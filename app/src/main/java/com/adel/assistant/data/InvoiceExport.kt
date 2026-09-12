package com.adel.assistant.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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
        val name = "invoice_${data.letterNo.ifBlank { "draft" }}.xlsx".replace(" ", "_")
        return FileExport.exportBytesToDocuments(
            context, name, bytes,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }

    fun exportPdfAndShare(context: Context, data: InvoiceData): Uri? {
        val bytes = buildPdf(data)
        val name = "invoice_${data.letterNo.ifBlank { "draft" }}.pdf".replace(" ", "_")
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

    private fun buildFromTemplate(context: Context, data: InvoiceData): ByteArray {
        val updates = mutableMapOf<String, Pair<String, Boolean>>()
        // شماره نامه
        updates["C7"] = data.letterNo to true
        // کارفرمای محترم ...
        updates["E7"] = data.employerTitle to true

        val maxRows = 13 // rows 10..22
        data.lines.take(maxRows).forEachIndexed { idx, line ->
            val row = 10 + idx
            updates["G$row"] = line.service to true
            updates["D$row"] = formatAmount(line.amount) to true
            updates["C$row"] = line.note to true
        }
        // clear unused sample rows content
        for (row in (10 + data.lines.size).coerceAtMost(22)..22) {
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

    private fun rewriteXlsx(templateBytes: ByteArray, updates: Map<String, Pair<String, Boolean>>): ByteArray {
        val outBuffer = ByteArrayOutputStream()
        ZipOutputStream(outBuffer).use { zos ->
            ZipInputStream(templateBytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
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
            val cellRegex = Regex(
                """<c r="$ref"([^>/]*?)(?:/>|>.*?</c>)""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            val match = cellRegex.find(result)
            val styleAttr = match?.groupValues?.get(1)?.let { attrs ->
                Regex("""s="(\d+)"""").find(attrs)?.value?.let { " $it" } ?: attrs.trim().let {
                    if (it.isNotBlank() && !it.startsWith(" ")) " $it" else if (it.isNotBlank()) it else ""
                }
            } ?: ""
            // keep only style s="N"
            val onlyStyle = Regex("""s="(\d+)"""").find(styleAttr)?.value?.let { " $it" } ?: ""
            val escaped = value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            val newCell = if (isText) {
                """<c r="$ref"$onlyStyle t="inlineStr"><is><t>$escaped</t></is></c>"""
            } else {
                """<c r="$ref"$onlyStyle><v>$value</v></c>"""
            }
            result = if (match != null) {
                result.replaceRange(match.range, newCell)
            } else {
                result
            }
        }
        return result
    }

    private fun buildPdf(data: InvoiceData): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val c: Canvas = page.canvas
        val title = Paint().apply {
            textSize = 16f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT; color = 0xFF000000.toInt()
        }
        val body = Paint().apply {
            textSize = 12f; textAlign = Paint.Align.RIGHT; color = 0xFF222222.toInt()
        }
        val small = Paint().apply {
            textSize = 10f; textAlign = Paint.Align.RIGHT; color = 0xFF444444.toInt()
        }
        val linePaint = Paint().apply {
            color = 0xFF888888.toInt(); strokeWidth = 1f; style = Paint.Style.STROKE
        }
        var y = 40f
        val right = pageWidth - 40f
        fun t(text: String, paint: Paint) {
            c.drawText(text, right, y, paint)
        }
        t("مهندس سید عادل پورمیر", title); y += 22
        t("نقشه بردار  تونل - راه - ساختمان - اراضی - سازه - UTM", small); y += 18
        t("کارکرد نقشه برداری", title); y += 28
        t(data.employerTitle, body); y += 18
        t("شماره: ${data.letterNo}    تاریخ: ${data.dateLabel}", body); y += 24
        c.drawLine(40f, y, right, y, linePaint); y += 16
        t("شرح خدمات          مبلغ (ریال)          توضیحات", body); y += 6
        c.drawLine(40f, y, right, y, linePaint); y += 18
        data.lines.forEach { line ->
            t("${line.service}    ${formatMoney(line.amount)}    ${line.note}", body)
            y += 18
        }
        y += 10
        c.drawLine(40f, y, right, y, linePaint); y += 20
        t("جمع کل: ${formatMoney(data.total)}", title); y += 18
        t("دریافتی: ${formatMoney(data.received)}", body); y += 18
        t("مانده پرداختی: ${formatMoney(data.remaining)}", title); y += 24
        t("شماره کارت: ${data.cardNo}", body); y += 16
        t("شبا: ${data.iban}", body); y += 24
        t("E-MAIL: pourmir.surveyor@yahoo.com", small); y += 16
        t("لطفا پس از انجام پرداخت اطلاع رسانی بفرمایید — با سپاس", small)
        doc.finishPage(page)
        val bos = ByteArrayOutputStream()
        doc.writeTo(bos)
        doc.close()
        return bos.toByteArray()
    }
}
