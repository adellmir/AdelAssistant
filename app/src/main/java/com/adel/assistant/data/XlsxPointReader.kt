package com.adel.assistant.data

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * خواندن سادهٔ نقاط از XLSX (بدون Apache POI).
 * ستون‌ها: X,Y,Z,D  یا  ID,X,Y,Z,D  یا  X,Y,Z
 */
object XlsxPointReader {

    fun parse(bytes: ByteArray): List<GsiPoint> {
        val sheets = mutableMapOf<String, String>()
        var shared: List<String> = emptyList()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val data = zis.readBytes().toString(Charsets.UTF_8)
                when {
                    name == "xl/sharedStrings.xml" -> shared = parseShared(data)
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") ->
                        sheets[name] = data
                }
                entry = zis.nextEntry
            }
        }
        val sheetXml = sheets.entries.minByOrNull { it.key }?.value ?: return emptyList()
        val rows = extractRows(sheetXml, shared)
        return rowsToPoints(rows)
    }

    private fun parseShared(xml: String): List<String> {
        val out = mutableListOf<String>()
        val re = Regex("""<si[^>]*>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
        val tRe = Regex("""<t[^>]*>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
        re.findAll(xml).forEach { m ->
            val texts = tRe.findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
            out += texts.joinToString("")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        }
        return out
    }

    private fun extractRows(sheetXml: String, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val rowRe = Regex("""<row[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
        val cellRe = Regex("""<c([^>]*)>(.*?)</c>""", RegexOption.DOT_MATCHES_ALL)
        val vRe = Regex("""<v>(.*?)</v>""")
        val isRe = Regex("""<t[^>]*>(.*?)</t>""")
        rowRe.findAll(sheetXml).forEach { rowM ->
            val cells = mutableMapOf<Int, String>()
            cellRe.findAll(rowM.groupValues[1]).forEach { cM ->
                val attrs = cM.groupValues[1]
                val body = cM.groupValues[2]
                val ref = Regex("""r="([A-Z]+)(\d+)"""").find(attrs)?.groupValues?.get(1) ?: return@forEach
                val col = colIndex(ref)
                val isShared = attrs.contains("""t="s"""")
                val isInline = attrs.contains("""t="inlineStr"""")
                val value = when {
                    isInline -> isRe.find(body)?.groupValues?.get(1).orEmpty()
                    isShared -> {
                        val idx = vRe.find(body)?.groupValues?.get(1)?.toIntOrNull()
                        if (idx != null && idx in shared.indices) shared[idx] else ""
                    }
                    else -> vRe.find(body)?.groupValues?.get(1).orEmpty()
                }
                cells[col] = value
            }
            if (cells.isNotEmpty()) {
                val maxCol = cells.keys.maxOrNull() ?: 0
                rows += (0..maxCol).map { cells[it].orEmpty() }
            }
        }
        return rows
    }

    private fun colIndex(letters: String): Int {
        var n = 0
        for (ch in letters) n = n * 26 + (ch - 'A' + 1)
        return n - 1
    }

    private fun rowsToPoints(rows: List<List<String>>): List<GsiPoint> {
        val out = mutableListOf<GsiPoint>()
        rows.forEach { cols ->
            val a = cols.map { it.trim() }.filter { it.isNotEmpty() || cols.indexOf(it) < 4 }
            if (a.isEmpty()) return@forEach
            val low = a.joinToString(",").lowercase()
            if (low.contains("easting") || low.startsWith("x,y") || low.startsWith("id,")) return@forEach
            fun d(i: Int) = a.getOrNull(i)?.replace(',', '.')?.toDoubleOrNull()
            when {
                a.size >= 5 && d(1) != null && d(2) != null && d(3) != null ->
                    out += GsiPoint(name = a[0], e = d(1)!!, n = d(2)!!, z = d(3)!!, code = a.drop(4).joinToString(" "))
                a.size >= 4 && d(0) != null && d(1) != null && d(2) != null ->
                    out += GsiPoint(
                        name = (out.size + 1).toString(),
                        e = d(0)!!, n = d(1)!!, z = d(2)!!,
                        code = a.drop(3).joinToString(" ")
                    )
                a.size >= 3 && d(0) != null && d(1) != null ->
                    out += GsiPoint(
                        name = (out.size + 1).toString(),
                        e = d(0)!!, n = d(1)!!, z = d(2) ?: 0.0
                    )
            }
        }
        return out
    }
}
