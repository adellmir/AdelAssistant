package com.adel.assistant.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class InvoiceLine(
    val service: String,
    val amount: Double,
    val note: String
)

data class InvoiceData(
    val employer: String,
    val letterNo: String,
    val lines: List<InvoiceLine>,
    val received: Double,
    val cardNo: String,
    val iban: String,
    val dateLabel: String
) {
    val total: Double get() = lines.sumOf { it.amount }
    val remaining: Double get() = (total - received).coerceAtLeast(0.0)
    val employerTitle: String
        get() = if (employer.isBlank()) "کارفرمای محترم"
        else "کارفرمای محترم $employer"
}

object InvoiceExport {

    fun exportXlsx(context: Context, data: InvoiceData): Uri? {
        val bytes = buildFromTemplate(context, data)
        val name = "invoice_${sanitize(data.letterNo.ifBlank { "draft" })}.xlsx"
        return FileExport.exportBytesToDocuments(
            context, name, bytes,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }

    fun exportPdfAndShare(context: Context, data: InvoiceData): Uri? {
        // PDF هم‌قالب با اکسل فاکتور
        val bytes = buildPdfLikeExcel(data)
        val name = "invoice_${sanitize(data.letterNo.ifBlank { "draft" })}.pdf"
        val uri = FileExport.exportBytesToDocuments(context, name, bytes, "application/pdf")
        try {
            val cache = File(context.cacheDir, name)
            cache.writeBytes(bytes)
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cache)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "اشتراک فاکتور"))
        } catch (_: Exception) {
        }
        return uri
    }

    private fun sanitize(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|]"""), "_").replace(" ", "_")

    private fun buildFromTemplate(context: Context, data: InvoiceData): ByteArray {
        val updates = linkedMapOf<String, Pair<String, Boolean>>()
        updates["C7"] = data.letterNo to true
        updates["E7"] = data.employerTitle to true

        val maxRows = 13
        data.lines.take(maxRows).forEachIndexed { idx, line ->
            val row = 10 + idx
            updates["G$row"] = line.service to true
            updates["D$row"] = formatAmount(line.amount) to true
            updates["C$row"] = line.note to true
        }
        for (row in (10 + data.lines.size).coerceAtMost(23)..22) {
            updates["G$row"] = "" to true
            updates["D$row"] = "" to true
            updates["C$row"] = "" to true
        }

        updates["D24"] = formatAmount(data.total) to true
        updates["D25"] = formatAmount(data.received) to true
        updates["D26"] = formatAmount(data.remaining) to true
        updates["F25"] = data.cardNo to true
        updates["F26"] = data.iban to true

        val templateBytes = context.assets.open("invoice_template.xlsx").use { it.readBytes() }
        return rewriteXlsx(templateBytes, updates)
    }

    private fun formatAmount(v: Double): String {
        val longVal = kotlin.math.abs(v - v.toLong().toDouble()) < 1e-9
        val raw = if (longVal) v.toLong().toString() else String.format(java.util.Locale.US, "%.0f", v)
        val neg = raw.startsWith("-")
        val digits = raw.removePrefix("-")
        val sb = StringBuilder()
        var i = digits.length
        while (i > 0) {
            val start = (i - 3).coerceAtLeast(0)
            if (sb.isNotEmpty()) sb.insert(0, "/")
            sb.insert(0, digits.substring(start, i))
            i = start
        }
        return if (neg) "-$sb" else sb.toString()
    }

    /** بازنویسی امن قالب — مثل گزارش روزانه با ZipFile */
    private fun rewriteXlsx(templateBytes: ByteArray, updates: Map<String, Pair<String, Boolean>>): ByteArray {
        val tmp = File.createTempFile("invoice_tpl_", ".xlsx")
        try {
            tmp.writeBytes(templateBytes)
            val outBuffer = ByteArrayOutputStream()
            ZipFile(tmp).use { zf ->
                ZipOutputStream(outBuffer).use { zos ->
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        val data = zf.getInputStream(entry).readBytes()
                        zos.putNextEntry(ZipEntry(name))
                        if (name == "xl/worksheets/sheet1.xml") {
                            val xml = String(data, Charsets.UTF_8)
                            zos.write(applyCellUpdates(xml, updates).toByteArray(Charsets.UTF_8))
                        } else {
                            zos.write(data)
                        }
                        zos.closeEntry()
                    }
                }
            }
            return outBuffer.toByteArray()
        } finally {
            tmp.delete()
        }
    }

    private fun applyCellUpdates(xml: String, updates: Map<String, Pair<String, Boolean>>): String {
        var result = xml
        updates.forEach { (ref, pair) ->
            val (value, isText) = pair
            // هم سلول‌های معمولی و هم self-closing
            val patterns = listOf(
                Regex("""<c r="$ref"([^>]*)>.*?</c>""", setOf(RegexOption.DOT_MATCHES_ALL)),
                Regex("""<c r="$ref"([^>/]*)/>""")
            )
            var match: MatchResult? = null
            for (p in patterns) {
                match = p.find(result)
                if (match != null) break
            }
            if (match == null) return@forEach
            val attrs = match.groupValues.getOrNull(1) ?: ""
            val onlyStyle = Regex("""s="(\d+)"""").find(attrs)?.value?.let { " $it" } ?: ""
            val escaped = value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            val newCell = if (isText) {
                """<c r="$ref"$onlyStyle t="inlineStr"><is><t xml:space="preserve">$escaped</t></is></c>"""
            } else {
                """<c r="$ref"$onlyStyle><v>$value</v></c>"""
            }
            result = result.replaceRange(match.range, newCell)
        }
        return result
    }

    /** PDF شبیه چیدمان فایل اکسل فاکتور */
    private fun buildPdfLikeExcel(data: InvoiceData): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val c: Canvas = page.canvas

        val paintTitle = Paint().apply {
            textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
            color = 0xFF000000.toInt(); isAntiAlias = true
        }
        val paintSub = Paint().apply {
            textSize = 11f; textAlign = Paint.Align.CENTER
            color = 0xFF333333.toInt(); isAntiAlias = true
        }
        val paintRight = Paint().apply {
            textSize = 12f; textAlign = Paint.Align.RIGHT
            color = 0xFF000000.toInt(); isAntiAlias = true
        }
        val paintRightBold = Paint().apply {
            textSize = 12f; textAlign = Paint.Align.RIGHT; isFakeBoldText = true
            color = 0xFF000000.toInt(); isAntiAlias = true
        }
        val paintLeft = Paint().apply {
            textSize = 11f; textAlign = Paint.Align.LEFT
            color = 0xFF000000.toInt(); isAntiAlias = true
        }
        val paintCenter = Paint().apply {
            textSize = 11f; textAlign = Paint.Align.CENTER
            color = 0xFF000000.toInt(); isAntiAlias = true
        }
        val line = Paint().apply {
            color = 0xFF444444.toInt(); strokeWidth = 1.2f; style = Paint.Style.STROKE
        }
        val headerBg = Paint().apply {
            color = 0xFFE8E8E8.toInt(); style = Paint.Style.FILL
        }

        val margin = 40f
        val right = pageWidth - margin
        val left = margin
        var y = 50f

        // سربرگ مثل اکسل
        c.drawText("مهندس سید عادل پورمیر", pageWidth / 2f, y, paintTitle); y += 22
        c.drawText("نقشه بردار  تونل - راه - ساختمان - اراضی - سازه - UTM", pageWidth / 2f, y, paintSub); y += 20
        c.drawText("کارکرد نقشه برداری", pageWidth / 2f, y, paintTitle); y += 28

        c.drawText(data.employerTitle, right, y, paintRightBold)
        c.drawText("شماره: ${data.letterNo}", left, y, paintLeft); y += 16
        c.drawText("تاریخ: ${data.dateLabel}", left, y, paintLeft); y += 20

        // جدول: شرح خدمات | مبلغ | توضیحات  (RTL visual)
        val col1 = right          // شرح
        val col2 = right - 220f   // مبلغ
        val col3 = left + 10f     // توضیحات شروع از چپ
        val rowH = 28f

        // هدر جدول
        c.drawRect(RectF(left, y, right, y + rowH), headerBg)
        c.drawRect(RectF(left, y, right, y + rowH), line)
        val hy = y + 18f
        c.drawText("شرح خدمات", col1 - 8f, hy, paintRightBold)
        c.drawText("مبلغ (ریال)", col2, hy, paintRightBold)
        c.drawText("توضیحات", col3 + 80f, hy, paintCenter)
        y += rowH

        data.lines.forEach { lineItem ->
            c.drawRect(RectF(left, y, right, y + rowH), line)
            val ty = y + 18f
            // truncate long text
            fun short(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1) + "…"
            c.drawText(short(lineItem.service, 28), col1 - 8f, ty, paintRight)
            c.drawText(formatAmount(lineItem.amount), col2, ty, paintRight)
            c.drawText(short(lineItem.note, 18), col3 + 4f, ty, paintLeft)
            y += rowH
        }

        // حداقل چند ردیف خالی مثل قالب
        val emptyRows = (6 - data.lines.size).coerceAtLeast(0)
        repeat(emptyRows) {
            c.drawRect(RectF(left, y, right, y + rowH), line)
            y += rowH
        }

        y += 12
        c.drawLine(left, y, right, y, line); y += 20
        c.drawText("جمع کل: ${formatAmount(data.total)}", right, y, paintRightBold); y += 18
        c.drawText("دریافتی: ${formatAmount(data.received)}", right, y, paintRight); y += 18
        c.drawText("مانده پرداختی: ${formatAmount(data.remaining)}", right, y, paintRightBold); y += 24

        c.drawText("شماره کارت جهت واریز: ${data.cardNo}", right, y, paintRight); y += 16
        c.drawText("شبا: ${data.iban}", right, y, paintRight); y += 28

        c.drawText("E-MAIL: pourmir.surveyor@yahoo.com", pageWidth / 2f, y, paintSub); y += 16
        c.drawText("لطفا پس از انجام پرداخت اطلاع رسانی بفرمایید                با سپاس", pageWidth / 2f, y, paintSub)

        doc.finishPage(page)
        val bos = ByteArrayOutputStream()
        doc.writeTo(bos)
        doc.close()
        return bos.toByteArray()
    }
}
