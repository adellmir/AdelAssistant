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

private val ROW_MAP = mapOf(
    4 to ("1" to "start"),
    12 to ("1" to "2"),
    20 to ("2" to "1"),
    28 to ("2" to "3"),
    36 to ("3" to "2"),
    44 to ("3" to "4"),
    52 to ("4" to "3"),
    60 to ("4" to "end")
)

object XlsxReportWriter {

    fun generate(context: Context, year: String, month: String, day: String, weekday: String?): android.net.Uri? {
        val m = month.toIntOrNullFa() ?: 0
        val d = day.toIntOrNullFa() ?: 0
        val todayKey = "%s%02d%02d".format(year, m, d)

        val cellUpdates = mutableMapOf<String, Pair<String, Boolean>>()
        cellUpdates["Q1"] = formatEn("%02d/%02d", m, d) to true
        cellUpdates["R1"] = "$year/" to true
        // روز هفته مستقیم در Q2 (بدون فرمول)
        val wd = weekday?.takeIf { it.isNotBlank() }
            ?: CalendarStore.weekdayNameJalali(year.toIntOrNullFa() ?: 0, m, d)
        cellUpdates["Q2"] = wd to true

        var sumI = 0.0
        ROW_MAP.forEach { (row, pair) ->
            val (shaft, side) = pair
            val todayEntry = TunnelReportStore.entryForDateAndKey(context, year, month, day, shaft, side)

            val f = TunnelReportStore.kmBefore(context, shaft, side, todayKey)
            val g: Double
            val h: Double
            val i: Double
            val j: Double
            if (todayEntry != null) {
                g = todayEntry.km
                h = todayEntry.dailyProgress
                i = todayEntry.shaftProgress
                j = todayEntry.remaining
            } else {
                g = f
                h = 0.0
                val fixedKm = TunnelReportStore.shaftFixedKm(context, shaft) ?: f
                i = kotlin.math.abs(g - fixedKm)
                j = 0.0
            }
            sumI += i

            cellUpdates["F$row"] = formatEn("%.4f", f) to false
            cellUpdates["G$row"] = formatEn("%.4f", g) to false
            cellUpdates["H$row"] = formatEn("%.4f", h) to false
            cellUpdates["I$row"] = formatEn("%.4f", i) to false
            if (todayEntry != null || row == 4 || row == 60) {
                cellUpdates["J$row"] = formatEn("%.4f", j) to false
            }
        }
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
        return FileExport.exportBytesToDocuments(
            context,
            fileName,
            bytes,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }
}
