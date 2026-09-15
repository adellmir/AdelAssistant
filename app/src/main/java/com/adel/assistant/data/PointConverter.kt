package com.adel.assistant.data

import java.util.Locale

data class SurveyPoint(val id: String, val x: Double, val y: Double, val z: Double, val code: String = "")

object PointConverter {
    fun read(text: String, extension: String): List<SurveyPoint> = when (extension.lowercase()) {
        "gsi" -> parseGsi(text)
        "idx" -> parseIdx(text)
        "dxf" -> parseDxf(text)
        "kml" -> KmlParser.parseKmlText(text).points
        else -> parseDelimited(text)
    }

    /** خواندن از بایت (برای KMZ که باینری/زیپ است) */
    fun readBytes(bytes: ByteArray, fileName: String): List<SurveyPoint> {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".kmz") || lower.endsWith(".kml") ->
                KmlParser.parseBytes(bytes, fileName).points
            lower.endsWith(".gsi") -> parseGsi(bytes.toString(Charsets.UTF_8))
            else -> read(bytes.toString(Charsets.UTF_8), lower.substringAfterLast('.', "txt"))
        }
    }

    fun write(points: List<SurveyPoint>, extension: String): String = when (extension.lowercase()) {
        "csv" -> points.joinToString("\n", "ID,X,Y,Z,CODE\n") { p -> "${p.id},${f(p.x)},${f(p.y)},${f(p.z)},${p.code}" }
        "txt", "dat" -> points.joinToString("\n") { p -> listOf(p.id, f(p.x), f(p.y), f(p.z), p.code).joinToString("\t") }
        "dxf" -> dxf(points)
        "gsi" -> gsi(points)
        "idx" -> idx(points)
        "kml" -> UtmGeo.toKml(points)
        else -> throw IllegalArgumentException("فرمت خروجی پشتیبانی نمی‌شود")
    }

    private fun parseDelimited(text: String): List<SurveyPoint> = text.lineSequence().mapNotNull { line ->
        val s = line.trim()
        if (s.isBlank() || s.startsWith("#") || s.lowercase().contains("id,x,y,z")) return@mapNotNull null
        val a = s.split(Regex("[,;\\t ]+")).filter { it.isNotBlank() }
        if (a.size < 4) return@mapNotNull null
        val x = a.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
        val y = a.getOrNull(2)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
        val z = a.getOrNull(3)?.replace(',', '.')?.toDoubleOrNull() ?: return@mapNotNull null
        SurveyPoint(a[0].trim('"'), x, y, z, a.drop(4).joinToString(" "))
    }.toList()

    private fun parseIdx(text: String): List<SurveyPoint> {
        val r = Regex("^\\s*\\d+\\s*,\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"([^\\\"]*)\\\"\\s*,\\s*([-+0-9.]+)\\s*,\\s*([-+0-9.]+)\\s*,\\s*([-+0-9.]+)")
        return text.lineSequence().mapNotNull { m -> r.find(m)?.let {
            SurveyPoint(it.groupValues[1], it.groupValues[3].toDouble(), it.groupValues[4].toDouble(), it.groupValues[5].toDouble(), it.groupValues[2])
        } }.toList()
    }

    /**
     * خواندن GSI:
     * - نام نقطه از فیلد دادهٔ word 11 (مثلاً S1) نه شمارندهٔ 110003
     * - کد از word 41 روی نقاط بعدی اعمال می‌شود
     * - OCUPAR و RE حذف؛ دو برداشت بعد از OCUPAR هم حذف
     * - نقاط با مختصات صفر حذف
     */
    private fun parseGsi(text: String): List<SurveyPoint> {
        val out = mutableListOf<SurveyPoint>()
        var currentCode = ""
        var skipPoints = 0

        fun decodeText(raw: String): String {
            val t = raw.trim()
            if (t.isEmpty()) return "0"
            return if (t.any { it.isLetter() }) t.trimStart('0').ifBlank { "0" }
            else t.trimStart('0').ifBlank { "0" }
        }

        fun coord(line: String, key: String): Double? {
            val m = Regex(key + """\.\.00\+([0-9]+)""").find(line) ?: return null
            return m.groupValues[1].toDouble() / 10000.0
        }

        fun wordData(line: String, wi: String): String? {
            val m = Regex("""\*?""" + wi + """[0-9.]*\+([^\s]+)""").find(line) ?: return null
            return decodeText(m.groupValues[1])
        }

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val upper = line.uppercase()

            val is41 = line.contains("*41") && !line.contains("*11")
            if (is41) {
                val codeVal = wordData(line, "41") ?: ""
                if (codeVal.equals("OCUPAR", true) || upper.contains("OCUPAR")) {
                    currentCode = ""
                    skipPoints = 0 // فقط OCUPAR/RE و مختصات صفر حذف می‌شوند
                    continue
                }
                if (codeVal.equals("RE", true) || Regex("""(^|[^A-Z0-9])RE([^A-Z0-9]|$)""").containsMatchIn(upper)) {
                    continue
                }
                if (codeVal.isNotBlank() && codeVal != "0") currentCode = codeVal
                continue
            }

            if (!line.contains("*11")) continue
            if (skipPoints > 0) { skipPoints--; continue }

            val idRaw = wordData(line, "11") ?: continue
            if (idRaw.equals("OCUPAR", true) || idRaw.equals("RE", true)) continue

            val x = coord(line, "81") ?: continue
            val y = coord(line, "82") ?: continue
            val z = coord(line, "83") ?: 0.0
            if (kotlin.math.abs(x) < 1e-9 && kotlin.math.abs(y) < 1e-9) continue

            val code42 = wordData(line, "42")?.takeIf { it.isNotBlank() && it != "0" }
            val code = code42 ?: currentCode
            out += SurveyPoint(idRaw, x, y, z, code)
        }
        return out
    }

    private fun parseDxf(text: String): List<SurveyPoint> {
        val lines = text.lines(); val out = mutableListOf<SurveyPoint>(); var i = 0; var no = 1
        while (i < lines.size - 1) {
            if (lines[i].trim() == "0" && lines[i + 1].trim().equals("POINT", true)) {
                var x: Double? = null; var y: Double? = null; var z: Double? = 0.0; var layer = ""
                i += 2
                while (i < lines.size - 1 && lines[i].trim() != "0") {
                    when (lines[i].trim()) { "10" -> x = lines[i+1].trim().toDoubleOrNull(); "20" -> y = lines[i+1].trim().toDoubleOrNull(); "30" -> z = lines[i+1].trim().toDoubleOrNull(); "8" -> layer = lines[i+1].trim() }
                    i += 2
                }
                if (x != null && y != null) out += SurveyPoint(no++.toString(), x, y, z ?: 0.0, layer)
            } else i++
        }
        return out
    }

    /** DXF R12 (AC1009) معتبر برای اتوکد */
    private fun dxf(points: List<SurveyPoint>) = buildString {
        fun layerOf(p: SurveyPoint) =
            p.code.ifBlank { "POINTS" }.replace(Regex("[^A-Za-z0-9_-]"), "_").take(31).ifBlank { "POINTS" }
        val layers = points.map { layerOf(it) }.distinct()
        append("0\r\nSECTION\r\n2\r\nHEADER\r\n9\r\n\$ACADVER\r\n1\r\nAC1009\r\n0\r\nENDSEC\r\n")
        append("0\r\nSECTION\r\n2\r\nTABLES\r\n")
        append("0\r\nTABLE\r\n2\r\nLTYPE\r\n70\r\n1\r\n")
        append("0\r\nLTYPE\r\n2\r\nCONTINUOUS\r\n70\r\n0\r\n3\r\nSolid line\r\n72\r\n65\r\n73\r\n0\r\n40\r\n0.0\r\n0\r\nENDTAB\r\n")
        append("0\r\nTABLE\r\n2\r\nLAYER\r\n70\r\n${layers.size}\r\n")
        layers.forEach { layer ->
            append("0\r\nLAYER\r\n2\r\n$layer\r\n70\r\n0\r\n62\r\n7\r\n6\r\nCONTINUOUS\r\n")
        }
        append("0\r\nENDTAB\r\n0\r\nENDSEC\r\n")
        append("0\r\nSECTION\r\n2\r\nENTITIES\r\n")
        points.forEach { p ->
            val layer = layerOf(p)
            val s = 0.10
            val gap = 0.15
            append("0\r\nLINE\r\n8\r\n$layer\r\n")
            append("10\r\n${f(p.x - s)}\r\n20\r\n${f(p.y - s)}\r\n30\r\n${f(p.z)}\r\n")
            append("11\r\n${f(p.x + s)}\r\n21\r\n${f(p.y + s)}\r\n31\r\n${f(p.z)}\r\n")
            append("0\r\nLINE\r\n8\r\n$layer\r\n")
            append("10\r\n${f(p.x - s)}\r\n20\r\n${f(p.y + s)}\r\n30\r\n${f(p.z)}\r\n")
            append("11\r\n${f(p.x + s)}\r\n21\r\n${f(p.y - s)}\r\n31\r\n${f(p.z)}\r\n")
            textEntity(layer, p.x + gap, p.y + 0.10, p.z, p.id, 0.10)
            textEntity(layer, p.x + gap, p.y, p.z, "Z=${f(p.z)}", 0.10)
            if (p.code.isNotBlank()) textEntity(layer, p.x + gap, p.y - 0.10, p.z, p.code, 0.10)
        }
        append("0\r\nENDSEC\r\n0\r\nEOF\r\n")
    }

    private fun StringBuilder.textEntity(layer: String, x: Double, y: Double, z: Double, value: String, height: Double) {
        append("0\r\nTEXT\r\n8\r\n$layer\r\n62\r\n7\r\n")
        append("10\r\n${f(x)}\r\n20\r\n${f(y)}\r\n30\r\n${f(z)}\r\n")
        append("40\r\n${f(height)}\r\n1\r\n${value.replace("\n", " ")}\r\n50\r\n0\r\n")
    }

    private fun gsi(points: List<SurveyPoint>) = points.joinToString("\n") { p ->
        "*11${p.id.padStart(4,'0')} 42....+000000000000${p.code} 81..00+${scaled(p.x)} 82..00+${scaled(p.y)} 83..00+${scaled(p.z)}"
    }

    private fun idx(points: List<SurveyPoint>) = buildString {
        append("HEADER\n  VERSION      1.31\n  SYSTEM       \"STS\"\n  SEPARATOR    ','\n  TERMINATOR   ';'\nEND HEADER\n\nDATABASE\n  POINTS (PointNo,PointID,Code,East,North,Elevation,CLASS)\n")
        points.forEachIndexed { i,p -> append("    ${i+1},  \"${p.id}\",  \"${p.code}\",    ${f(p.x)},  ${f(p.y)},  ${f(p.z)},  FIX;\n") }
        append("END DATABASE\n")
    }
    private fun scaled(v: Double) = String.format(Locale.US, "%014d", kotlin.math.round(v * 10000).toLong())
    private fun f(v: Double) = String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
}
