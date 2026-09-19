package com.adel.assistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * تبدیل آزمایشی DWG → DXF از طریق اینترنت.
 *
 * ترتیب:
 * 1) مبدل محلی (DXF متنی / DWG با DXF جاسازی‌شده)
 * 2) POST به بک‌اند AdelAssistant: /convert/dwg-to-dxf
 *
 * پاسخ‌های قابل قبول سرور:
 * - متن DXF خام (شروع با 0\nSECTION یا شامل ENTITIES)
 * - application/json با فیلد dxf یا text یا content
 * - JSON با download_url برای دریافت فایل
 */
object OnlineDwgConverter {

    private const val BACKEND = "https://adelassistant-backend.fastapicloud.dev"
    private const val PATH = "/convert/dwg-to-dxf"

    data class Result(
        val ok: Boolean,
        val dxf: String,
        val message: String,
        val source: String = ""
    )

    suspend fun convert(bytes: ByteArray, fileName: String): Result = withContext(Dispatchers.IO) {
        // ۱) تلاش محلی
        val local = DwgDxfConverter.convert(bytes, fileName)
        if (local.ok && local.dxf.contains("ENTITIES", ignoreCase = true)) {
            return@withContext Result(true, local.dxf, "محلی: ${local.message}", "local")
        }

        // ۲) آنلاین
        return@withContext try {
            postToBackend(bytes, fileName)
        } catch (e: Exception) {
            val tip = buildString {
                append(local.message)
                append("\n\nآنلاین ناموفق: ${e.message ?: e.javaClass.simpleName}")
                append("\nسرور باید endpoint داشته باشد: POST $BACKEND$PATH")
            }
            Result(false, "", tip, "error")
        }
    }

    private fun postToBackend(bytes: ByteArray, fileName: String): Result {
        val boundary = "----AdelBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val url = URL("$BACKEND$PATH")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 180000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json, application/dxf, text/plain, */*")
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                val safeName = fileName.ifBlank { "drawing.dwg" }.replace("\"", "")
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\n")
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                out.write(bytes)
                out.writeBytes("\r\n")
                // optional format
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"target\"\r\n\r\n")
                out.writeBytes("dxf\r\n")
                out.writeBytes("--$boundary--\r\n")
                out.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bodyBytes = stream?.readBytes() ?: ByteArray(0)
            val bodyText = runCatching { bodyBytes.toString(Charsets.UTF_8) }.getOrDefault("")

            if (code !in 200..299) {
                val detail = runCatching { JSONObject(bodyText).optString("detail") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                throw IllegalStateException(detail ?: "HTTP $code")
            }

            // DXF خام
            if (looksLikeDxf(bodyText)) {
                return Result(true, bodyText, "آنلاین: DXF دریافت شد (${bodyBytes.size} بایت)", "online")
            }

            // JSON
            if (bodyText.trimStart().startsWith("{")) {
                val json = JSONObject(bodyText)
                val dxfField = listOf("dxf", "text", "content", "data")
                    .mapNotNull { k -> json.optString(k).takeIf { it.isNotBlank() } }
                    .firstOrNull()
                if (dxfField != null && looksLikeDxf(dxfField)) {
                    return Result(true, dxfField, "آنلاین: DXF از JSON", "online-json")
                }
                val downloadUrl = listOf("download_url", "url", "file_url")
                    .mapNotNull { k -> json.optString(k).takeIf { it.isNotBlank() } }
                    .firstOrNull()
                if (downloadUrl != null) {
                    val downloaded = downloadUrl(downloadUrl)
                    if (looksLikeDxf(downloaded)) {
                        return Result(true, downloaded, "آنلاین: دانلود از لینک", "online-url")
                    }
                }
                throw IllegalStateException(json.optString("message").ifBlank {
                    "پاسخ JSON بدون DXF معتبر"
                })
            }

            // باینری DXF با encoding اشتباه — دوباره به‌صورت latin1
            val latin = bodyBytes.toString(Charsets.ISO_8859_1)
            if (looksLikeDxf(latin)) {
                return Result(true, latin, "آنلاین: DXF باینری/متنی", "online-bin")
            }

            throw IllegalStateException("پاسخ سرور DXF معتبر نبود")
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadUrl(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30000
            readTimeout = 180000
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun looksLikeDxf(text: String): Boolean {
        val t = text.trimStart()
        if (t.length < 20) return false
        return t.contains("ENTITIES", ignoreCase = true) ||
            (t.startsWith("0") && t.contains("SECTION", ignoreCase = true)) ||
            t.contains("\$ACADVER", ignoreCase = true)
    }
}
