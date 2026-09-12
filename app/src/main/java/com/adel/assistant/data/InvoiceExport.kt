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
import java.util.zip.CRC32
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
        val bytes = try {
            buildFromTemplate(context, data)
        } catch (e: Exception) {
            buildMinimalXlsx(data)
        }
        // validate zip has sheet
        if (bytes.size < 100) return null
        val name = "invoice_${data.letterNo.ifBlank { System.currentTimeMillis().toString() }}.xlsx"
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
        // آدرس سلول‌ها طبق مشخصات کاربر
        val updates = linkedMapOf<String, String>()
        updates["C7"] = "شماره فاکتور ${data.letterNo}"
        updates["E7"] = data.employerTitle

        val maxRows = 13
        data.lines.take(maxRows).forEachIndexed { idx, line ->
            val row = 10 + idx
            updates["E$row"] = line.service
            updates["D$row"] = formatAmount(line.amount)
            updates["C$row"] = line.note
        }
        for (row in (10 + data.lines.size.coerceAtMost(maxRows))..22) {
            updates["E$row"] = ""
            updates["D$row"] = ""
            updates["C$row"] = ""
        }
        updates["C24"] = formatAmount(data.total)
        updates["C25"] = formatAmount(data.received)
        updates["C26"] = formatAmount(data.remaining)

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
            if (sb.isNotEmpty()) sb.insert(0, ",")
            sb.insert(0, digits.substring(start, i))
            i = start
        }
        return if (neg) "-$sb" else sb.toString()
    }

    private fun rewriteXlsx(templateBytes: ByteArray, updates: Map<String, String>): ByteArray {
        val outBuffer = ByteArrayOutputStream()
        ZipOutputStream(outBuffer).use { zos ->
            ZipInputStream(templateBytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val bytes = zis.readBytes()
                    val name = entry.name
                    val data = if (name == "xl/worksheets/sheet1.xml" || name.endsWith("sheet1.xml")) {
                        applyCellUpdates(bytes.toString(Charsets.UTF_8), updates).toByteArray(Charsets.UTF_8)
                    } else {
                        bytes
                    }
                    val ze = ZipEntry(name)
                    // STORED can break some readers if CRC wrong; use DEFLATED default
                    zos.putNextEntry(ze)
                    zos.write(data)
                    zos.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return outBuffer.toByteArray()
    }

    private fun applyCellUpdates(xml: String, updates: Map<String, String>): String {
        var result = xml
        updates.forEach { (ref, value) ->
            val escaped = value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
            val newCell = """<c r="$ref" t="inlineStr"><is><t xml:space="preserve">$escaped</t></is></c>"""
            val cellRegex = Regex(
                """<c r="$ref"[^>]*/>|<c r="$ref"[^>]*>.*?</c>""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            val match = cellRegex.find(result)
            if (match != null) {
                result = result.replaceRange(match.range, newCell)
            } else {
                // درج در ردیف: مثلاً C7 → row 7
                val rowNum = Regex("""(\d+)$""").find(ref)?.groupValues?.get(1) ?: return@forEach
                val rowOpen = Regex("""<row[^>]*r="$rowNum"[^>]*>""")
                val m = rowOpen.find(result)
                if (m != null) {
                    val insertAt = m.range.last + 1
                    result = result.substring(0, insertAt) + newCell + result.substring(insertAt)
                }
            }
        }
        return result
    }

    /** خروجی حداقلی معتبر اگر قالب خراب باشد */
    private fun buildMinimalXlsx(data: InvoiceData): ByteArray {
        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            fun row(r: Int, cells: List<Pair<String, String>>) {
                append("""<row r="$r">""")
                cells.forEach { (ref, v) ->
                    val e = v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                    append("""<c r="$ref" t="inlineStr"><is><t>$e</t></is></c>""")
                }
                append("</row>")
            }
            row(1, listOf("A1" to "فاکتور نقشه برداری"))
            row(7, listOf("C7" to "شماره فاکتور ${data.letterNo}", "E7" to data.employerTitle))
            data.lines.forEachIndexed { i, line ->
                val r = 10 + i
                row(r, listOf(
                    "C$r" to line.note,
                    "D$r" to formatAmount(line.amount),
                    "E$r" to line.service
                ))
            }
            row(24, listOf("C24" to formatAmount(data.total)))
            row(25, listOf("C25" to formatAmount(data.received)))
            row(26, listOf("C26" to formatAmount(data.remaining)))
            append("</sheetData></worksheet>")
        }
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
        val wb = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Invoice" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            fun put(name: String, text: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rels)
            put("xl/workbook.xml", wb)
            put("xl/_rels/workbook.xml.rels", wbRels)
            put("xl/worksheets/sheet1.xml", sheet)
        }
        return out.toByteArray()
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
        var y = 40f
        val right = pageWidth - 40f
        fun t(text: String, paint: Paint) {
            c.drawText(text, right, y, paint)
        }
        t("مهندس سید عادل پورمیر", title); y += 22
        t("نقشه بردار  تونل - راه - ساختمان", small); y += 18
        t("کارکرد نقشه برداری", title); y += 28
        t(data.employerTitle, body); y += 18
        t("شماره فاکتور ${data.letterNo}    تاریخ: ${data.dateLabel}", body); y += 24
        data.lines.forEach { line ->
            t("${line.service}  |  ${formatAmount(line.amount)}  |  ${line.note}", body)
            y += 18
            if (y > pageHeight - 80) return@forEach
        }
        y += 10
        t("جمع کل: ${formatAmount(data.total)}", title); y += 18
        t("دریافتی: ${formatAmount(data.received)}", body); y += 18
        t("مانده پرداختی: ${formatAmount(data.remaining)}", title); y += 22
        t("کارت: ${data.cardNo}", small); y += 14
        t("شبا: ${data.iban}", small)
        doc.finishPage(page)
        val bos = ByteArrayOutputStream()
        doc.writeTo(bos)
        doc.close()
        return bos.toByteArray()
    }
}
