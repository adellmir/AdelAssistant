package com.adel.assistant.data

import android.content.Context
import android.net.Uri

/**
 * فضای یکپارچهٔ ورود/خروج نقطه.
 * همهٔ برنامه‌ها باید از اینجا بخوانند/بنویسند تا قانون DAT و فرمت‌ها یک‌جا باشد.
 *
 * ترتیب داخلی همیشه: X=Easting ، Y=Northing
 * فایل DAT: N Y X Z [کد]
 */
object PointImport {

    /** پسوندهای پشتیبانی‌شده برای انتخاب فایل */
    val MIME_TYPES: Array<String> = arrayOf(
        "*/*",
        "text/plain",
        "text/csv",
        "application/octet-stream",
        "application/vnd.google-earth.kml+xml",
        "application/vnd.google-earth.kmz"
    )

    val EXTENSIONS = listOf("txt", "dat", "gsi", "csv", "kml", "kmz", "dxf", "idx")

    fun readBytes(bytes: ByteArray, fileName: String): List<SurveyPoint> {
        if (bytes.isEmpty()) return emptyList()
        val name = fileName.ifBlank { "points.txt" }
        // ۱) مسیر اصلی — PointConverter (DAT/GSI/KML/TXT/…)
        try {
            val primary = PointConverter.readBytes(bytes, name)
            if (primary.isNotEmpty()) return primary
        } catch (_: Exception) {
        }
        val text = try {
            bytes.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            return emptyList()
        }
        val lower = name.lowercase()
        // ۲) مسیرهای پشتیبان GsiParser
        if (lower.endsWith(".gsi") || text.trimStart().startsWith("*11") || text.contains("81..")) {
            val g = GsiParser.parse(text)
            if (g.isNotEmpty()) return g.toSurveyPoints()
        }
        if (lower.endsWith(".dat")) {
            val d = GsiParser.parseDat(text)
            if (d.isNotEmpty()) return d.toSurveyPoints()
        }
        val txt = GsiParser.parseTxt(text)
        if (txt.isNotEmpty()) return txt.toSurveyPoints()
        val dat = GsiParser.parseDat(text)
        if (dat.isNotEmpty()) return dat.toSurveyPoints()
        return GsiParser.parse(text).toSurveyPoints()
    }

    fun readText(text: String, fileName: String = "points.txt"): List<SurveyPoint> =
        readBytes(text.toByteArray(Charsets.UTF_8), fileName)

    fun readUri(context: Context, uri: Uri): List<SurveyPoint> {
        val name = resolveDisplayName(context, uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return emptyList()
        return readBytes(bytes, name)
    }

    /** خواندن و تبدیل مستقیم به GsiPoint برای صفحه‌های قدیمی */
    fun readAsGsi(context: Context, uri: Uri): List<GsiPoint> =
        readUri(context, uri).toGsiPoints()

    fun readBytesAsGsi(bytes: ByteArray, fileName: String): List<GsiPoint> =
        readBytes(bytes, fileName).toGsiPoints()

    fun write(points: List<SurveyPoint>, extension: String): String =
        PointConverter.write(points, extension)

    fun writeGsiList(points: List<GsiPoint>, extension: String): String {
        val sp = points.toSurveyPoints()
        return when (extension.lowercase()) {
            "txt" -> GsiParser.toTxt(points)
            "dat" -> GsiParser.toDat(points)
            "gsi" -> GsiParser.toGsi(points)
            "csv" -> GsiParser.toCsv(points)
            "dxf" -> GsiParser.toDxf(points)
            "kml" -> GsiParser.toKml(points)
            else -> PointConverter.write(sp, extension)
        }
    }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val n = c.getString(idx)
                        if (!n.isNullOrBlank()) return n
                    }
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "points.txt"
    }
}

/** تبدیل‌های مشترک بین مدل‌های نقطه */
fun SurveyPoint.toGsiPoint(idSeed: Long = System.nanoTime()): GsiPoint =
    GsiPoint(id = idSeed, name = id, e = x, n = y, z = z, code = code)

fun GsiPoint.toSurveyPoint(): SurveyPoint =
    SurveyPoint(id = name, x = e, y = n, z = z, code = code)

fun List<SurveyPoint>.toGsiPoints(): List<GsiPoint> =
    mapIndexed { i, p -> p.toGsiPoint(System.nanoTime() + i) }

fun List<GsiPoint>.toSurveyPoints(): List<SurveyPoint> =
    map { it.toSurveyPoint() }

fun SurveyPoint.toMap2Point(idPrefix: String = "p"): Map2Point =
    Map2Point(
        id = "${idPrefix}_${id}_${System.nanoTime() % 100000}",
        name = id,
        x = x,
        y = y,
        z = z,
        code = code
    )
