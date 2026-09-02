package com.adel.assistant.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs

private data class RowSpec(
    val row: Int, val shaft: String, val side: String,
    val targetI: Double, val jFixed: Double? = null, val jRefRow: Int? = null
)

object XlsxReportWriter {

    private val rowSpecs = listOf(
        RowSpec(4, "1", "start", 362.1, jFixed = 227.0),
        RowSpec(12, "1", "2", 362.1, jRefRow = 20),
        RowSpec(20, "2", "1", 586.65),
        RowSpec(28, "2", "3", 586.65, jRefRow = 36),
        RowSpec(36, "3", "2", 909.8),
        RowSpec(44, "3", "4", 909.8, jRefRow = 52),
        RowSpec(52, "4", "3", 1200.0),
        RowSpec(60, "4", "end", 1200.0, jFixed = 1257.7)
    )

    fun generate(context: Context, year: String, month: String, day: String, weekday: String?): android.net.Uri? {
        val m = month.toIntOrNullFa() ?: 0
        val d = day.toIntOrNullFa() ?: 0
        val todayKey = "%s%02d%02d".format(year, m, d)
        val prevKey = CalendarStore.previousDateKey(year, month, day)

        // منبع واحد محاسبات: TunnelReportStore (همان موتوری که گزارش روزانه استفاده می‌کند)
        val gValues = mutableMapOf<Int, Double>()
        val fValues = mutableMapOf<Int, Double>()
        rowSpecs.forEach { spec ->
            fValues[spec.row] = TunnelReportStore.kmOnOrBefore(context, spec.shaft, spec.side, prevKey)
            gValues[spec.row] = TunnelReportStore.kmOnOrBefore(context, spec.shaft, spec.side, todayKey)
        }

        val cellUpdates = mutableMapOf<String, Pair<String, Boolean>>()

        cellUpdates["Q1"] = formatEn("%02d/%02d", m, d) to true
        cellUpdates["R1"] = "$year/" to true
        if (weekday != null) cellUpdates["Q2"] = weekday to true

        var sumI = 0.0
        rowSpecs.forEach { spec ->
            val f = fValues[spec.row]!!
            val g = gValues[spec.row]!!
            val h = abs(g - f)
            val i = abs(spec.targetI - g)
            sumI += i
            cellUpdates["F${spec.row}"] = formatEn("%.4f", f) to false
            cellUpdates["G${spec.row}"] = formatEn("%.4f", g) to false
            cellUpdates["H${spec.row}"] = formatEn("%.4f", h) to false
            cellUpdates["I${spec.row}"] = formatEn("%.4f", i) to false
            val jTarget = spec.jFixed ?: spec.jRefRow?.let { gValues[it] }
            if (jTarget != null) {
                cellUpdates["J${spec.row}"] = formatEn("%.4f", abs(g - jTarget)) to false
            }
        }
        // E69 = مجموع ستون I (مانده‌ها) — طبق فرمول واقعی قالب. H69 در قالب خالی است و دست نمی‌خورد.
        cellUpdates["E69"] = formatEn("%.4f", sumI) to false

        val templateBytes = context.assets.open("report_template.xlsx").use { it.readBytes() }
        val outputBytes = rewriteXlsx(templateBytes, cellUpdates)

        val fileName = formatEn("b%02d%02d.xlsx", m, d)
        return saveToDocuments(context, fileName, outputBytes)
    }

    private fun rewriteXlsx(templateBytes: ByteArray, updates: Map<String, Pair<String, Boolean>>): ByteArray {
        val outBuffer = ByteArrayOutputStream()
        ZipOutputStream(outBuffer).use { zos ->
            ZipInputStream(templateBytes.inputStream()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val bytes = zis.readBytes()
                    val name = entry.name
                    zos.putNextEntry(ZipEntry(name))
                    if (name == "xl/worksheets/sheet1.xml") {
                        val xml = bytes.toString(Charsets.UTF_8)
                        zos.write(applyCellUpdates(xml, updates).toByteArray(Charsets.UTF_8))
                    } else {
                        zos.write(bytes)
                    }
                    zos.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return outBuffer.toByteArray()
    }

    private fun applyCellUpdates(xml: String, updates: Map<String, Pair<String, Boolean>>): String {
        var result = xml
        updates.forEach { (ref, pair) ->
            val (value, isText) = pair
            val cellRegex = Regex("<c r=\"$ref\"([^>]*)>.*?</c>", RegexOption.DOT_MATCHES_ALL)
            val match = cellRegex.find(result)
            val styleAttr = match?.groupValues?.get(1)?.let { attrs ->
                Regex("s=\"(\\d+)\"").find(attrs)?.value?.let { " $it" }
            } ?: ""
            val newCell = if (isText) {
                val escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                "<c r=\"$ref\"$styleAttr t=\"inlineStr\"><is><t>$escaped</t></is></c>"
            } else {
                "<c r=\"$ref\"$styleAttr><v>$value</v></c>"
            }
            result = if (match != null) result.replaceRange(match.range, newCell) else result
        }
        return result
    }

    private fun saveToDocuments(context: Context, fileName: String, bytes: ByteArray): android.net.Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/AdelAssistant")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
            uri?.let { resolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
            return uri
        } else {
            val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AdelAssistant")
            if (!dir.exists()) dir.mkdirs()
            val f = java.io.File(dir, fileName)
            f.writeBytes(bytes)
            return android.net.Uri.fromFile(f)
        }
    }
}
