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

/**
 * صدور فاکتور XLSX — همان روش موفق [XlsxReportWriter]:
 * کل ZIP قالب (استایل، تصویر، drawing) کپی می‌شود و فقط sheet1 پر می‌شود.
 * دیگر به فایل خام (minimal) سقوط نمی‌کند.
 */
object InvoiceExport {

    fun exportXlsx(context: Context, data: InvoiceData): Uri? {
        val templateBytes = loadTemplateBytes(context) ?: return null
        val updates = buildUpdates(data)
        val outputBytes = rewriteXlsx(templateBytes, updates)
        // خروجی باید نزدیک به اندازه قالب باشد (نه فایل ۱–۲ کیلوبایتی خام)
        if (outputBytes.size < templateBytes.size / 2) return null
        val name = "invoice_${data.letterNo.ifBlank { System.currentTimeMillis().toString() }}.xlsx"
            .replace(" ", "_")
        return FileExport.exportBytesToDocuments(
            context,
            name,
            outputBytes,
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

    private fun loadTemplateBytes(context: Context): ByteArray? {
        val names = listOf("invoice_template.xlsx", "1فاکتور.xlsx", "invoice.xlsx")
        for (n in names) {
            try {
                val b = context.assets.open(n).use { it.readBytes() }
                if (b.size > 5000) return b
            } catch (_: Exception) {
            }
        }
        val local = File(context.filesDir, "invoice_template.xlsx")
        if (local.exists() && local.length() > 5000) return local.readBytes()
        return null
    }

    /**
     * نگاشت سلول‌ها مطابق قالب invoice_template.xlsx:
     * C7 شماره | E7 کارفرما
     * ردیف ۱۰+ : C توضیح | D مبلغ | E شرح
     * C24 جمع | C25 دریافتی | C26 مانده
     * F25 کارت | F26 شبا
     */
    private fun buildUpdates(data: InvoiceData): Map<String, Pair<String, Boolean>> {
        val updates = linkedMapOf<String, Pair<String, Boolean>>()
        updates["C7"] = data.letterNo.ifBlank { "—" } to true
        updates["E7"] = data.employerTitle to true
        data.lines.take(14).forEachIndexed { idx, line ->
            val row = 10 + idx
            if (line.note.isNotBlank()) updates["C$row"] = line.note to true
            updates["D$row"] = plainAmount(line.amount) to false
            if (line.service.isNotBlank()) updates["E$row"] = line.service to true
        }
        updates["C24"] = plainAmount(data.total) to false
        updates["C25"] = plainAmount(data.received) to false
        updates["C26"] = plainAmount(data.remaining) to false
        if (data.cardNo.isNotBlank()) updates["F25"] = data.cardNo to true
        if (data.iban.isNotBlank()) updates["F26"] = data.iban to true
        return updates
    }

    private fun plainAmount(v: Double): String {
        val longVal = kotlin.math.abs(v - v.toLong().toDouble()) < 1e-9
        return if (longVal) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.0f", v)
    }

    private fun formatAmount(v: Double): String {
        val raw = plainAmount(v)
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

    /** کپی ۱:۱ منطق XlsxReportWriter.rewriteXlsx */
    private fun rewriteXlsx(
        templateBytes: ByteArray,
        updates: Map<String, Pair<String, Boolean>>
    ): ByteArray {
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

    /**
     * مثل گزارش روزانه استایل s را نگه می‌دارد.
     * سلول‌های خالی قالب self-closing هستند: <c r="D10" s="26"/>
     */
    private fun applyCellUpdates(
        xml: String,
        updates: Map<String, Pair<String, Boolean>>
    ): String {
        var result = xml
        updates.forEach { (ref, pair) ->
            val (value, isText) = pair
            if (value.isEmpty()) return@forEach
            val selfClose = Regex("""<c r="$ref"([^>]*?)/>""")
            val fullCell = Regex("""<c r="$ref"([^>]*?)>.*?</c>""", RegexOption.DOT_MATCHES_ALL)
            val match = selfClose.find(result) ?: fullCell.find(result) ?: return@forEach
            val attrs = match.groupValues.getOrNull(1).orEmpty()
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
            result = result.replaceRange(match.range, newCell)
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
