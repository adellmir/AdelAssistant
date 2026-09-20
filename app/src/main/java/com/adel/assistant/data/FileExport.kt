package com.adel.assistant.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object FileExport {

    const val ROOT = "AdelAssistant"

    fun exportTextToDocuments(
        context: Context,
        fileName: String,
        text: String,
        mimeType: String = "text/plain"
    ): Uri? {
        val safeName = normalizeName(fileName, mimeType)
        val mime = resolveMime(safeName, mimeType)
        return exportBytesToDocuments(context, safeName, text.toByteArray(Charsets.UTF_8), mime)
    }

    fun exportBytesToDocuments(
        context: Context,
        fileName: String,
        bytes: ByteArray,
        mimeType: String
    ): Uri? {
        val safeName = normalizeName(fileName, mimeType)
        val mime = resolveMime(safeName, mimeType)
        val rel = relativePathFor(safeName, mime)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                    arrayOf(safeName),
                    null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        resolver.delete(Uri.withAppendedPath(collection, id.toString()), null, null)
                    }
                }
                val uri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    rel.removePrefix(Environment.DIRECTORY_DOCUMENTS + "/")
                )
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, safeName)
                f.writeBytes(bytes)
                Uri.fromFile(f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun readAsCsvText(context: Context, csvName: String): String {
        val f = CsvStore.getFile(context, csvName)
        return if (f.exists()) f.readText() else ""
    }

    /**
     * Documents/AdelAssistant/<زیرپوشه>
     * dxf, txt, backup, tunel-report, letter, invoice, gsi, kml, pdf
     */
    fun relativePathFor(fileName: String, mimeType: String = ""): String {
        val sub = subfolderFor(fileName, mimeType)
        val base = Environment.DIRECTORY_DOCUMENTS + "/" + ROOT
        return if (sub.isBlank()) base else "$base/$sub"
    }

    fun subfolderFor(fileName: String, mimeType: String = ""): String {
        val n = fileName.lowercase().substringAfterLast('/').substringAfterLast(':')
        val mime = mimeType.lowercase()
        return when {
            // گزارش روزانه تونل
            n.contains("survey_tunnel_report") ||
                n.contains("tunel-report") ||
                n.contains("tunnel_report") ||
                n.contains("tunnel-report") ||
                n.contains("daily") && n.endsWith(".xlsx") ||
                Regex("""^b\d{4,6}\.xlsx$""").matches(n) ||
                (n.startsWith("b") && n.endsWith(".xlsx") && n.length in 8..16) ||
                (n.startsWith("a") && n.endsWith(".xlsx") && n.length in 8..16) -> "tunel-report"
            // فاکتور
            n.startsWith("invoice") || n.contains("invoice") || n.contains("فاکتور") || n.contains("factor") -> "invoice"
            // نامه
            n.startsWith("letter") || n.contains("letter") || n.contains("leter") || n.contains("نامه") -> "letter"
            n.contains("backup") || (n.endsWith(".zip") && n.contains("backup")) || n.contains("پشتیبان") -> "backup"
            n.endsWith(".dxf") || mime.contains("dxf") -> "dxf"
            n.endsWith(".gsi") || mime.contains("gsi") -> "gsi"
            n.endsWith(".kml") || n.endsWith(".kmz") || mime.contains("google-earth") || mime.contains("kml") -> "kml"
            n.endsWith(".pdf") || mime.contains("pdf") -> "pdf"
            n.endsWith(".txt") || n.endsWith(".csv") || n.endsWith(".dat") || n.endsWith(".xyz") -> "txt"
            // xlsx عمومی بدون تشخیص → ریشه نه؛ اگر mime spreadsheet و نام مبهم
            n.endsWith(".xlsx") && mime.contains("sheet") -> "txt"
            else -> ""
        }
    }


    private fun normalizeName(fileName: String, mimeType: String): String {
        var n = fileName.trim()
        n = n.replace(Regex("""\.csv\.txt$""", RegexOption.IGNORE_CASE), ".csv")
        n = n.replace(Regex("""\.txt\.csv$""", RegexOption.IGNORE_CASE), ".csv")
        if (mimeType.contains("csv", true) || n.lowercase().endsWith(".csv")) {
            if (!n.lowercase().endsWith(".csv")) n = "$n.csv"
        }
        if (mimeType.contains("dxf", true) || n.lowercase().endsWith(".dxf")) {
            if (!n.lowercase().endsWith(".dxf")) n = "$n.dxf"
        }
        return n
    }

    private fun resolveMime(fileName: String, mimeType: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".csv") -> "text/csv"
            lower.endsWith(".dxf") -> "application/dxf"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".txt") -> "text/plain"
            mimeType.contains("csv") -> "text/csv"
            else -> mimeType
        }
    }
}
