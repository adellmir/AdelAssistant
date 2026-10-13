package com.adel.assistant.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocationPdfReport {

    fun write(
        context: Context,
        points: List<LocationPoint>,
        measures: List<Measure>,
        title: String = "گزارش میدانی AdelAssistant"
    ): File? {
        return try {
            val doc = PdfDocument()
            val paint = Paint().apply {
                textSize = 11f
                typeface = Typeface.MONOSPACE
                isAntiAlias = true
            }
            val titlePaint = Paint().apply {
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val pageWidth = 595 // A4-ish points
            val pageHeight = 842
            var pageNum = 1
            var y = 0f
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            var canvas = page.canvas

            fun newPage() {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 40f
            }

            fun line(s: String, bold: Boolean = false) {
                if (y > pageHeight - 40) newPage()
                if (bold) canvas.drawText(s, 40f, y, titlePaint) else canvas.drawText(s, 40f, y, paint)
                y += if (bold) 22f else 14f
            }

            y = 40f
            line(title, true)
            line(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            line("نقاط: ${points.size}   اندازه‌گیری: ${measures.size}")
            y += 8f
            line("--- نقاط ---", true)
            points.forEachIndexed { i, p ->
                line(
                    String.format(
                        Locale.US,
                        "%d. %s [%s]  %.7f,%.7f  Z=%.2f  ±%s  E=%.2f N=%.2f Z%d",
                        i + 1, p.name, p.code, p.lat, p.lon, p.alt,
                        if (p.accuracy > 0) String.format(Locale.US, "%.1fm", p.accuracy) else "?",
                        p.easting, p.northing, p.zone
                    )
                )
                if (p.photoPath.isNotBlank()) line("   عکس: ${p.photoPath}")
                if (p.note.isNotBlank()) line("   یادداشت: ${p.note}")
            }
            y += 8f
            line("--- اندازه‌گیری‌ها ---", true)
            measures.forEach { m ->
                val info = if (m.type == MeasureType.FIELD)
                    MeasureGeometry.fmtArea(m.areaM2)
                else MeasureGeometry.fmtDist(m.lengthM)
                line("${m.name} (${m.type.name})  $info  zone=${m.zone}")
            }
            doc.finishPage(page)

            val dir = File(context.getExternalFilesDir(null), "export").apply { mkdirs() }
            val f = File(dir, "report_${System.currentTimeMillis() / 1000}.pdf")
            FileOutputStream(f).use { doc.writeTo(it) }
            doc.close()
            f
        } catch (_: Exception) {
            null
        }
    }
}
