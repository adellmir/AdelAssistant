package com.adel.assistant.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

data class InvoiceLine(
    val service: String,
    val amount: Double, // ریال / واحد نمایش
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
        val bytes = buildXlsx(data)
        val name = "invoice_${data.letterNo.ifBlank { "draft" }}.xlsx"
            .replace(" ", "_")
        return FileExport.exportBytesToDocuments(
            context, name, bytes,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }

    fun exportPdfAndShare(context: Context, data: InvoiceData): Uri? {
        val bytes = buildPdf(data)
        val name = "invoice_${data.letterNo.ifBlank { "draft" }}.pdf".replace(" ", "_")
        val uri = FileExport.exportBytesToDocuments(context, name, bytes, "application/pdf")
        // share from cache copy
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
        } catch (_: Exception) {}
        return uri
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun buildXlsx(data: InvoiceData): ByteArray {
        val rows = mutableListOf<String>()
        var r = 1
        fun addRow(cells: List<Pair<String, Boolean>>) {
            val sb = StringBuilder("""<row r="$r">""")
            cells.forEachIndexed { idx, (v, isText) ->
                val col = ('A'.code + idx).toChar()
                val ref = "$col$r"
                if (isText) {
                    sb.append("""<c r="$ref" t="inlineStr"><is><t>${esc(v)}</t></is></c>""")
                } else {
                    sb.append("""<c r="$ref"><v>$v</v></c>""")
                }
            }
            sb.append("</row>")
            rows += sb.toString()
            r++
        }

        addRow(listOf("مهندس سید عادل پورمیر" to true))
        addRow(listOf("نقشه بردار  تونل - راه - ساختمان - اراضی - سازه - UTM" to true))
        addRow(listOf("کارکرد نقشه برداری" to true))
        addRow(listOf("شماره: ${data.letterNo}" to true, data.dateLabel to true, data.employerTitle to true))
        addRow(listOf("شرح خدمات" to true, "مبلغ ( ریال )" to true, "توضیحات" to true))
        data.lines.forEach { line ->
            addRow(listOf(
                line.service to true,
                formatPlain(line.amount) to false,
                line.note to true
            ))
        }
        // spacer rows to mimic template
        repeat(maxOf(0, 8 - data.lines.size)) { addRow(listOf("" to true, "" to true, "" to true)) }
        addRow(listOf("جمع کل" to true, formatPlain(data.total) to false, "شماره کارت جهت واریز" to true))
        addRow(listOf("دریافتی" to true, formatPlain(data.received) to false, data.cardNo to true))
        addRow(listOf("مانده پرداختی" to true, formatPlain(data.remaining) to false, data.iban to true))
        addRow(listOf("E-MAIL: pourmir.surveyor@yahoo.com" to true))
        addRow(listOf("لطفا پس از انجام پرداخت اطلاع رسانی بفرمایید                با سپاس" to true))

        val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetData>
${rows.joinToString("\n")}
</sheetData>
</worksheet>"""

        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="فاکتور" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            fun put(path: String, body: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(body.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rels)
            put("xl/workbook.xml", workbook)
            put("xl/_rels/workbook.xml.rels", wbRels)
            put("xl/worksheets/sheet1.xml", sheet)
        }
        return bos.toByteArray()
    }

    private fun formatPlain(v: Double): String {
        return if (kotlin.math.abs(v - v.toLong().toDouble()) < 1e-9) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.0f", v)
    }

    private fun buildPdf(data: InvoiceData): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 595 // A4
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
        fun t(text: String, paint: Paint, yy: Float = y) {
            c.drawText(text, right, yy, paint)
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
            val amt = formatMoney(line.amount)
            t("${line.service}    $amt    ${line.note}", body)
            y += 18
            if (y > pageHeight - 120) return@forEach
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
