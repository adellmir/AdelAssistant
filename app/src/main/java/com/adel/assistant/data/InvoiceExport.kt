package com.adel.assistant.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayInputStream
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

    /**
     * خروجی XLSX از روی قالب assets
     * C7 شماره فاکتور | E7 کارفرمای محترم
     * E10+ شرح | D10+ مبلغ | C10+ توضیحات
     * C24 جمع | C25 دریافتی | C26 مانده
     */
    fun exportXlsx(context: Context, data: InvoiceData): Uri? {
        val bytes = try {
            buildFromTemplate(context, data)
        } catch (e: Exception) {
            // فقط اگر قالب نبود — فایل سادهٔ معتبر
            buildMinimalXlsx(data)
        }
        if (bytes.size < 200) return null
        // اعتبارسنجی حداقلی: باید Content_Types داشته باشد
        if (!zipHasEntry(bytes, "[Content_Types].xml")) {
            val fixed = ensureValidPackage(bytes, data)
            return save(context, data, fixed)
        }
        return save(context, data, bytes)
    }

    private fun save(context: Context, data: InvoiceData, bytes: ByteArray): Uri? {
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

    private fun loadTemplateBytes(context: Context): ByteArray {
        val names = listOf("invoice_template.xlsx", "1فاکتور.xlsx", "invoice.xlsx")
        for (n in names) {
            try {
                val b = context.assets.open(n).use { it.readBytes() }
                if (b.size > 500 && zipHasEntry(b, "[Content_Types].xml")) return b
            } catch (_: Exception) {
            }
        }
        val local = File(context.filesDir, "invoice_template.xlsx")
        if (local.exists()) {
            val b = local.readBytes()
            if (b.size > 500) return b
        }
        throw IllegalStateException("قالب فاکتور در assets پیدا نشد")
    }

    private fun buildFromTemplate(context: Context, data: InvoiceData): ByteArray {
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

        return rewriteXlsx(loadTemplateBytes(context), updates)
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

    /** خواندن همهٔ ورودی‌های ZIP، اصلاح شیت، نوشتن دوباره با ساختار کامل */
    private fun rewriteXlsx(templateBytes: ByteArray, updates: Map<String, String>): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(templateBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sheetKey = entries.keys.firstOrNull { name ->
            name == "xl/worksheets/sheet1.xml" ||
                name.endsWith("/sheet1.xml") ||
                (name.contains("worksheets/sheet") && name.endsWith(".xml") && !name.contains("_rels"))
        } ?: throw IllegalStateException("sheet1.xml در قالب پیدا نشد")

        val sheetXml = entries[sheetKey]!!.toString(Charsets.UTF_8)
        entries[sheetKey] = applyCellUpdates(sheetXml, updates).toByteArray(Charsets.UTF_8)

        // اگر Content_Types یا rels نبود، فایل قالب ناقص است
        if (!entries.containsKey("[Content_Types].xml")) {
            throw IllegalStateException("قالب فاقد [Content_Types].xml است")
        }
        if (!entries.keys.any { it == "_rels/.rels" || it.endsWith(".rels") }) {
            throw IllegalStateException("قالب فاقد فایل rels است")
        }

        return writeZip(entries)
    }

    private fun writeZip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.setLevel(6)
            for ((name, data) in entries) {
                val ze = ZipEntry(name)
                // برای فایل‌های کوچک XML فشرده؛ برای باینری هم DEFLATED مشکلی ندارد
                ze.method = ZipEntry.DEFLATED
                zos.putNextEntry(ze)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipHasEntry(bytes: ByteArray, entryName: String): Boolean {
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    if (e.name == entryName || e.name.endsWith(entryName)) return true
                    e = zis.nextEntry
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureValidPackage(bytes: ByteArray, data: InvoiceData): ByteArray {
        // اگر خروجی ناقص بود، حداقل فایل معتبر بساز
        return try {
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    if (!e.isDirectory) entries[e.name] = zis.readBytes()
                    e = zis.nextEntry
                }
            }
            if (!entries.containsKey("[Content_Types].xml")) {
                entries["[Content_Types].xml"] = DEFAULT_CONTENT_TYPES.toByteArray(Charsets.UTF_8)
            }
            if (!entries.containsKey("_rels/.rels")) {
                entries["_rels/.rels"] = DEFAULT_RELS.toByteArray(Charsets.UTF_8)
            }
            if (!entries.containsKey("xl/_rels/workbook.xml.rels")) {
                entries["xl/_rels/workbook.xml.rels"] = DEFAULT_WB_RELS.toByteArray(Charsets.UTF_8)
            }
            if (!entries.containsKey("xl/workbook.xml")) {
                entries["xl/workbook.xml"] = DEFAULT_WB.toByteArray(Charsets.UTF_8)
            }
            writeZip(entries)
        } catch (_: Exception) {
            buildMinimalXlsx(data)
        }
    }

    /**
     * مثل XlsxReportWriter: استایل سلول (s="…") حفظ می‌شود تا قالب پایه از بین نرود.
     * سلول‌های مبلغی (D10.. و C24/C25/C26) به‌صورت عددی نوشته می‌شوند.
     */
    private fun applyCellUpdates(xml: String, updates: Map<String, String>): String {
        var result = xml
        val numericRefs = updates.keys.filter { ref ->
            ref.startsWith("D") || ref == "C24" || ref == "C25" || ref == "C26"
        }.toSet()
        updates.forEach { (ref, value) ->
            val cellRegex = Regex(
                """<c r="$ref"(?:\s[^>/]*)?(?:/>|>.*?</c>)""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            val match = cellRegex.find(result)
            val styleAttr = match?.groupValues?.get(0)?.let { full ->
                Regex("""\bs="\d+\"""").find(full)?.value?.let { " $it" } ?: ""
            } ?: ""
            val newCell = if (ref in numericRefs) {
                // فقط رقم و کاما/نقطه — برای <v> کاما را حذف می‌کنیم
                val num = value.replace(",", "").replace(" ", "")
                """<c r="$ref"$styleAttr><v>$num</v></c>"""
            } else {
                val escaped = value
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                """<c r="$ref"$styleAttr t="inlineStr"><is><t xml:space="preserve">$escaped</t></is></c>"""
            }
            if (match != null) {
                result = result.replaceRange(match.range, newCell)
            } else {
                val rowNum = Regex("""(\d+)$""").find(ref)?.groupValues?.get(1) ?: return@forEach
                val rowOpen = Regex("""<row[^>]*\br="$rowNum"[^>]*>""")
                val m = rowOpen.find(result)
                if (m != null) {
                    val insertAt = m.range.last + 1
                    result = result.substring(0, insertAt) + newCell + result.substring(insertAt)
                } else {
                    val close = result.lastIndexOf("</sheetData>")
                    if (close >= 0) {
                        val rowXml = """<row r="$rowNum">$newCell</row>"""
                        result = result.substring(0, close) + rowXml + result.substring(close)
                    }
                }
            }
        }
        return result
    }

    private fun buildMinimalXlsx(data: InvoiceData): ByteArray {
        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            fun row(r: Int, cells: List<Pair<String, String>>) {
                append("""<row r="$r">""")
                cells.forEach { (ref, v) ->
                    val e = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">$e</t></is></c>""")
                }
                append("</row>")
            }
            row(1, listOf("A1" to "فاکتور نقشه برداری"))
            row(7, listOf("C7" to "شماره فاکتور ${data.letterNo}", "E7" to data.employerTitle))
            data.lines.forEachIndexed { i, line ->
                val r = 10 + i
                row(
                    r, listOf(
                        "C$r" to line.note,
                        "D$r" to formatAmount(line.amount),
                        "E$r" to line.service
                    )
                )
            }
            row(24, listOf("C24" to formatAmount(data.total)))
            row(25, listOf("C25" to formatAmount(data.received)))
            row(26, listOf("C26" to formatAmount(data.remaining)))
            append("</sheetData></worksheet>")
        }
        val entries = linkedMapOf(
            "[Content_Types].xml" to DEFAULT_CONTENT_TYPES.toByteArray(Charsets.UTF_8),
            "_rels/.rels" to DEFAULT_RELS.toByteArray(Charsets.UTF_8),
            "xl/workbook.xml" to DEFAULT_WB.toByteArray(Charsets.UTF_8),
            "xl/_rels/workbook.xml.rels" to DEFAULT_WB_RELS.toByteArray(Charsets.UTF_8),
            "xl/worksheets/sheet1.xml" to sheet.toByteArray(Charsets.UTF_8)
        )
        return writeZip(entries)
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

    private const val DEFAULT_CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val DEFAULT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val DEFAULT_WB = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Invoice" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private const val DEFAULT_WB_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
}
