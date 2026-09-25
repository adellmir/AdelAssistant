package com.adel.assistant.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object MonitoringExport {

    fun export(
        context: Context,
        project: MonProject,
        epoch: MonEpoch,
        rows: List<MonAnalysisRow>,
        epochIndex: Int
    ): Uri? {
        val template = MonitoringStore.loadTemplateBytes(context, project) ?: return null
        val updates = linkedMapOf<String, Pair<String, Boolean>>()

        val employer = when {
            project.client.isBlank() -> "کارفرمای محترم"
            project.client.startsWith("کارفرمای محترم") -> project.client
            else -> "کارفرمای محترم ${project.client}"
        }
        updates["I7"] = employer to true
        updates["C7"] = project.reportNo.ifBlank { "—" } to true

        val ord = MonitoringAnalyzer.persianOrdinal(epochIndex.coerceAtLeast(1))
        val dateStr = listOf(epoch.year, epoch.month, epoch.day).filter { it.isNotBlank() }.joinToString("/")
        val body =
            "احتراما گزارش وضعیت مانیتورینگ دور برداشت $ord مورخ $dateStr با برداشت کارفرما به شرح ذیل می باشد"
        updates["D9"] = body to true

        rows.forEachIndexed { idx, r ->
            val row = 13 + idx
            updates["D$row"] = (if (r.isBm) "Bm" else "TP") to true
            updates["E$row"] = r.name to true
            r.baseX?.let { updates["F$row"] = fmt(it) to false }
            r.baseY?.let { updates["G$row"] = fmt(it) to false }
            r.baseZ?.let { updates["H$row"] = fmt(it) to false }
            r.epX?.let { updates["J$row"] = fmt(it) to false }
            r.epY?.let { updates["K$row"] = fmt(it) to false }
            r.epZ?.let { updates["L$row"] = fmt(it) to false }
            r.inOutMm?.let { updates["M$row"] = fmt1(it) to false }
            r.settleMm?.let { updates["N$row"] = fmt1(it) to false }
            r.d3dMm?.let { updates["O$row"] = fmt1(it) to false }
        }

        val out = rewriteXlsx(context, template, updates)
        if (out.isEmpty()) return null
        val name = "monitoring_${project.name}_${epoch.year}${epoch.month}${epoch.day}.xlsx"
            .replace(" ", "_")
        return FileExport.exportBytesToDocuments(
            context, name, out,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }

    fun shareUri(context: Context, uri: Uri) {
        try {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "اشتراک گزارش پایش"
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.4f", v)
    private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun rewriteXlsx(
        context: Context,
        templateBytes: ByteArray,
        updates: Map<String, Pair<String, Boolean>>
    ): ByteArray {
        val tmpIn = File(context.cacheDir, "mon_tpl_${System.currentTimeMillis()}.xlsx")
        return try {
            tmpIn.writeBytes(templateBytes)
            val outBuffer = ByteArrayOutputStream(templateBytes.size + 8192)
            ZipFile(tmpIn).use { zipFile ->
                ZipOutputStream(outBuffer).use { zos ->
                    val enumEntries = zipFile.entries()
                    while (enumEntries.hasMoreElements()) {
                        val entry = enumEntries.nextElement()
                        if (entry.isDirectory) continue
                        val name = entry.name
                        val raw = zipFile.getInputStream(entry).use { it.readBytes() }
                        val data = if (name == "xl/worksheets/sheet1.xml") {
                            applyCellUpdates(raw.toString(Charsets.UTF_8), updates)
                                .toByteArray(Charsets.UTF_8)
                        } else raw
                        val outEntry = ZipEntry(name)
                        outEntry.method = ZipEntry.DEFLATED
                        zos.putNextEntry(outEntry)
                        zos.write(data)
                        zos.closeEntry()
                    }
                }
            }
            outBuffer.toByteArray()
        } catch (_: Exception) {
            ByteArray(0)
        } finally {
            tmpIn.delete()
        }
    }

    private fun applyCellUpdates(xml: String, updates: Map<String, Pair<String, Boolean>>): String {
        var result = xml
        updates.forEach { (ref, pair) ->
            val (value, isText) = pair
            if (value.isEmpty()) return@forEach
            val selfClose = Regex("""<c r="$ref"([^>]*?)/>""")
            val fullCell = Regex("""<c r="$ref"([^>]*?)>.*?</c>""", RegexOption.DOT_MATCHES_ALL)
            val match = selfClose.find(result) ?: fullCell.find(result)
            val styleAttr = match?.groupValues?.getOrNull(1).orEmpty().let { attrs ->
                Regex("""\bs="\d+\"""").find(attrs)?.value?.let { " $it" }.orEmpty()
            }
            val escaped = value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            val newCell = if (isText) {
                """<c r="$ref"$styleAttr t="inlineStr"><is><t>$escaped</t></is></c>"""
            } else {
                """<c r="$ref"$styleAttr><v>$escaped</v></c>"""
            }
            result = when {
                selfClose.containsMatchIn(result) -> selfClose.replace(result, newCell)
                fullCell.containsMatchIn(result) -> fullCell.replace(result, newCell)
                else -> {
                    // inject before </sheetData>
                    val inj = newCell
                    if (result.contains("</sheetData>")) {
                        result.replace("</sheetData>", "$inj</sheetData>")
                    } else result
                }
            }
        }
        return result
    }
}
