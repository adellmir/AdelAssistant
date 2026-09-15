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

data class LetterData(
    val letterNo: String,
    val dateLabel: String,
    val employer: String,
    val body: String
) {
    val employerTitle: String
        get() = when {
            employer.isBlank() -> "کارفرمای محترم"
            employer.startsWith("کارفرمای محترم") -> employer
            else -> "کارفرمای محترم $employer"
        }
}

/**
 * صدور نامه روی قالب letter_template.xlsx
 * C7 شماره | C8 تاریخ | E7 کارفرما | C11 متن
 */
object LetterExport {

    fun exportXlsx(context: Context, data: LetterData): Uri? {
        val template = loadTemplate(context) ?: return null
        val updates = mapOf(
            "C7" to (data.letterNo.ifBlank { "—" } to true),
            "C8" to (data.dateLabel.ifBlank { "—" } to true),
            "E7" to (data.employerTitle to true),
            "C11" to (data.body to true)
        )
        val out = rewriteXlsx(template, updates)
        if (out.size < template.size / 2) return null
        val name = "letter_${data.letterNo.ifBlank { System.currentTimeMillis().toString() }}.xlsx"
            .replace(" ", "_")
        return FileExport.exportBytesToDocuments(
            context, name, out,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }

    fun exportPdfAndShare(context: Context, data: LetterData): Uri? {
        val bytes = buildPdf(data)
        val name = "letter_${data.letterNo.ifBlank { "draft" }}.pdf".replace(" ", "_")
        val uri = FileExport.exportBytesToDocuments(context, name, bytes, "application/pdf")
        try {
            val cache = File(context.cacheDir, name)
            cache.writeBytes(bytes)
            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cache)
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "اشتراک نامه"
                )
            )
        } catch (_: Exception) {
        }
        return uri
    }

    private fun loadTemplate(context: Context): ByteArray? {
        val names = listOf("letter_template.xlsx", "LETER.xlsx", "letter.xlsx")
        for (n in names) {
            try {
                val b = context.assets.open(n).use { it.readBytes() }
                if (b.size > 2000) return b
            } catch (_: Exception) {
            }
        }
        val local = File(context.filesDir, "letter_template.xlsx")
        if (local.exists() && local.length() > 2000) return local.readBytes()
        return null
    }

    private fun rewriteXlsx(
        templateBytes: ByteArray,
        updates: Map<String, Pair<String, Boolean>>
    ): ByteArray {
        val outBuffer = ByteArrayOutputStream()
        ZipOutputStream(outBuffer).use { zos ->
            ZipInputStream(templateBytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val bytes = zis.readBytes()
                    val name = entry.name
                    zos.putNextEntry(ZipEntry(name))
                    if (name == "xl/worksheets/sheet1.xml") {
                        zos.write(applyCellUpdates(bytes.toString(Charsets.UTF_8), updates).toByteArray(Charsets.UTF_8))
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
                """<c r="$ref"$styleAttr t="inlineStr"><is><t xml:space="preserve">$escaped</t></is></c>"""
            } else {
                """<c r="$ref"$styleAttr><v>$value</v></c>"""
            }
            result = result.replaceRange(match.range, newCell)
        }
        return result
    }

    private fun buildPdf(data: LetterData): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
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
        var y = 48f
        val right = pageWidth - 40f
        fun line(text: String, paint: Paint) {
            c.drawText(text, right, y, paint)
            y += paint.textSize + 8f
        }
        line("مهندس سید عادل پورمیر", title)
        line("نقشه بردار تونل - راه - ساختمان - اراضی - سازه - UTM", small)
        line("گزارش نقشه برداری", title)
        y += 12f
        line("شماره: ${data.letterNo}", body)
        line("تاریخ: ${data.dateLabel}", body)
        line(data.employerTitle, body)
        y += 16f
        // body multi-line (simple wrap by characters for RTL)
        val maxChars = 55
        val paragraphs = data.body.replace("\r", "").split('\n')
        paragraphs.forEach { para ->
            if (para.isBlank()) {
                y += 10f
                return@forEach
            }
            var rest = para
            while (rest.isNotEmpty()) {
                val take = rest.take(maxChars)
                val cut = if (rest.length > maxChars) {
                    val sp = take.lastIndexOf(' ')
                    if (sp > 20) take.substring(0, sp) else take
                } else take
                line(cut, body)
                rest = rest.drop(cut.length).trimStart()
                if (y > pageHeight - 60) return@forEach
            }
        }
        y = pageHeight - 40f
        line("E-MAIL: pourmir.surveyor@yahoo.com", small)
        doc.finishPage(page)
        val bos = ByteArrayOutputStream()
        doc.writeTo(bos)
        doc.close()
        return bos.toByteArray()
    }
}
