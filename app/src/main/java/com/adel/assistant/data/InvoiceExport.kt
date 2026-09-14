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
            // فقط اگر قالب واقعاً نبود
            try {
                buildMinimalXlsx(data)
            } catch (_: Exception) {
                return null
            }
        }
        if (bytes.size < 500) return null
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
        // نگاشت مطابق قالب واقعی:
        // C7 شماره | E7 کارفرما (ادغام E7:G7)
        // ردیف ۱۰–۲۳: C توضیحات | D مبلغ | E شرح (ادغام E:G)
        // C24 جمع | C25 دریافتی | C26 مانده
        // F25 کارت | F26 شبا
        val updates = linkedMapOf<String, Pair<String, Boolean>>() // value to isText
        updates["C7"] = (data.letterNo.ifBlank { "—" }) to true
        updates["E7"] = data.employerTitle to true

        val maxRows = 14 // ردیف ۱۰ تا ۲۳
        data.lines.take(maxRows).forEachIndexed { idx, line ->
            val row = 10 + idx
            updates["C$row"] = line.note to true
            updates["D$row"] = formatAmountPlain(line.amount) to false
            updates["E$row"] = line.service to true
        }
        updates["C24"] = formatAmountPlain(data.total) to false
        updates["C25"] = formatAmountPlain(data.received) to false
        updates["C26"] = formatAmountPlain(data.remaining) to false
        if (data.cardNo.isNotBlank()) updates["F25"] = data.cardNo to true
        if (data.iban.isNotBlank()) updates["F26"] = data.iban to true

        return rewriteXlsxLikeReport(loadTemplateBytes(context), updates)
    }

    /** مبلغ بدون جداکننده برای سلول عددی */
    private fun formatAmountPlain(v: Double): String {
        val longVal = kotlin.math.abs(v - v.toLong().toDouble()) < 1e-9
        return if (longVal) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.0f", v)
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

    /**
     * همان روش موفق XlsxReportWriter:
     * همهٔ فایل‌های ZIP قالب (تصاویر، استایل، drawing) کپی می‌شوند
     * فقط sheet1.xml ویرایش می‌شود و ویژگی s="…" حفظ می‌گردد.
     */
    private fun rewriteXlsxLikeReport(
        templateBytes: ByteArray,
        updates: Map<String, Pair<String, Boolean>>
    ): ByteArray {
        val outBuffer = ByteArrayOutputStream()
        ZipOutputStream(outBuffer).use { zos ->
            ZipInputStream(ByteArrayInputStream(templateBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val bytes = zis.readBytes()
                    zos.putNextEntry(ZipEntry(name))
                    if (name == "xl/worksheets/sheet1.xml" ||
                        (name.contains("worksheets/sheet") && name.endsWith(".xml") && !name.contains("_rels"))
                    ) {
                        val xml = bytes.toString(Charsets.UTF_8)
                        zos.write(applyCellUpdatesReportStyle(xml, updates).toByteArray(Charsets.UTF_8))
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

    private fun writeZip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
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
     * استایل s="…" حفظ می‌شود.
     * مهم: سلول‌های خالی قالب به‌صورت <c r="D10" s="26"/> هستند؛
     * باید اول فرم self-closing مچ شود وگرنه DOT_MATCHES تا </c> بعدی می‌بلعد.
     */
    private fun applyCellUpdatesReportStyle(
        xml: String,
        updates: Map<String, Pair<String, Boolean>>
    ): String {
        var result = xml
        updates.forEach { (ref, pair) ->
            val (value, isText) = pair
            if (value.isEmpty()) return@forEach
            // اول self-closing، بعد سلول معمولی
            val selfClose = Regex("""<c r="$ref"([^>]*?)/>""")
            val fullCell = Regex("""<c r="$ref"([^>]*?)>.*?</c>""", RegexOption.DOT_MATCHES_ALL)
            val match = selfClose.find(result) ?: fullCell.find(result)
            val attrs = match?.groupValues?.getOrNull(1).orEmpty()
            val styleAttr = Regex("""\bs="\d+\"""").find(attrs)?.value?.let { " $it" } ?: ""
            val newCell = if (isText) {
                val escaped = value
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                """<c r="$ref"$styleAttr t="inlineStr"><is><t>$escaped</t></is></c>"""
            } else {
                val num = value.replace(",", "").replace("/", "").replace(" ", "")
                """<c r="$ref"$styleAttr><v>$num</v></c>"""
            }
            result = if (match != null) {
                result.replaceRange(match.range, newCell)
            } else {
                result
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
