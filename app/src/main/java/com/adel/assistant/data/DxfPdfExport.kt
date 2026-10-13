package com.adel.assistant.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.min

/**
 * خروجی PDF از DxfModel با مقیاس کاغذی اختیاری 1:N
 */
object DxfPdfExport {

    fun export(
        model: DxfModel,
        out: OutputStream,
        scaleN: Double? = null,
        pageW: Int = 842,
        pageH: Int = 595,
        margin: Float = 24f
    ) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = doc.startPage(pageInfo)
        val c = page.canvas
        val s = (if (scaleN != null && scaleN > 0)
            2834.64567 / scaleN
        else
            min(
                (pageW - 2 * margin) / model.width().toFloat(),
                (pageH - 2 * margin) / model.height().toFloat()
            )).toFloat()

        fun X(x: Double) = margin + ((x - model.minX) * s).toFloat()
        fun Y(y: Double) = pageH - margin - ((y - model.minY) * s).toFloat()

        val paint = Paint().apply {
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
            isAntiAlias = true
            color = 0xFF000000.toInt()
        }
        val textPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            color = 0xFF000000.toInt()
            typeface = Typeface.MONOSPACE
        }

        model.lines.forEach { l ->
            c.drawLine(X(l.x1), Y(l.y1), X(l.x2), Y(l.y2), paint)
        }
        model.circles.forEach { cir ->
            val rr = (cir.r * s).toFloat()
            c.drawCircle(X(cir.x), Y(cir.y), rr, paint)
        }
        model.texts.forEach { t ->
            textPaint.textSize = (t.height * s).toFloat().coerceIn(4f, 28f)
            c.drawText(t.text, X(t.x), Y(t.y), textPaint)
        }

        val frame = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = 0xFF000000.toInt()
        }
        c.drawRect(margin / 2, margin / 2, pageW - margin / 2f, pageH - margin / 2f, frame)
        val title = Paint().apply {
            textSize = 10f
            color = 0xFF333333.toInt()
            isAntiAlias = true
        }
        val scaleLabel = if (scaleN != null) "1:${scaleN.toInt()}" else "FIT"
        c.drawText("Adel Assistant  |  scale $scaleLabel", margin, pageH - 8f, title)

        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }
}
