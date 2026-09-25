package com.adel.assistant.data

/**
 * پارسر GSI لیکا — هر دو مدل:
 *  - مدل مشاهدات (B0619): 21/22/31 + 81/82/83 + OCUPAR/RE
 *  - مدل مختصات (BAHAR): فقط 81/82/83
 * هنگام OCUPAR/RE همان خط و دو خط قبل حذف می‌شوند.
 */
data class GsiPoint(
    val id: Long = System.nanoTime(),
    var name: String,
    var e: Double,
    var n: Double,
    var z: Double,
    var code: String = ""
)

object GsiParser {

    private val wordRegex = Regex("""\*?(\d{2})([^\s+]*)([+-])([0-9A-Za-z.\-]{1,16})""")

    fun parse(text: String): List<GsiPoint> {
        val lines = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }

        val drop = BooleanArray(lines.size)
        for (i in lines.indices) {
            val upper = lines[i].uppercase()
            val isStation = upper.contains("OCUPAR") ||
                upper.contains("00000RE") ||
                upper.contains("+00000000000000RE") ||
                (upper.contains("*41") && (upper.contains("OCUPAR") || upper.contains("00RE")))
            if (isStation) {
                drop[i] = true
                if (i >= 1) drop[i - 1] = true
                if (i >= 2) drop[i - 2] = true
            }
        }

        val points = mutableListOf<GsiPoint>()
        for (i in lines.indices) {
            if (drop[i]) continue
            val words = parseWords(lines[i])
            val w11 = words["11"] ?: continue
            val w81 = words["81"] ?: continue
            val w82 = words["82"] ?: continue
            val w83 = words["83"] ?: continue

            val name = cleanName(w11)
            if (name.isBlank()) continue
            val nameUp = name.uppercase()
            if (nameUp == "OCUPAR" || nameUp == "RE") continue

            val code = words["71"]?.let { cleanName(it) }
                ?.takeIf { it.isNotBlank() && it != "0" } ?: ""

            points.add(
                GsiPoint(
                    name = name,
                    e = parseCoord(w81),
                    n = parseCoord(w82),
                    z = parseCoord(w83),
                    code = code
                )
            )
        }
        return points
    }

    fun parseTxt(text: String): List<GsiPoint> {
        val out = mutableListOf<GsiPoint>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val p = line.split(Regex("""[\s,;\t]+""")).filter { it.isNotEmpty() }
            if (p.size < 3) return@forEach
            try {
                when {
                    p.size >= 4 && p[1].toDoubleOrNull() != null && p[0].toDoubleOrNull() == null -> {
                        out.add(GsiPoint(name = p[0], e = p[1].toDouble(), n = p[2].toDouble(), z = p[3].toDouble()))
                    }
                    p.size >= 4 && p[0].toDoubleOrNull() != null -> {
                        out.add(GsiPoint(name = p[3], e = p[0].toDouble(), n = p[1].toDouble(), z = p[2].toDouble()))
                    }
                    p.size == 3 && p[0].toDoubleOrNull() != null -> {
                        out.add(
                            GsiPoint(
                                name = (out.size + 1).toString(),
                                e = p[0].toDouble(),
                                n = p[1].toDouble(),
                                z = p[2].toDouble()
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    private fun parseWords(line: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        wordRegex.findAll(line).forEach { m ->
            map[m.groupValues[1]] = m.groupValues[3] + m.groupValues[4]
        }
        return map
    }

    private fun cleanName(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("+") || s.startsWith("-")) s = s.substring(1)
        s = s.trimStart('0')
        if (s.isEmpty()) s = "0"
        return s.trim()
    }

    private fun parseCoord(raw: String): Double {
        var s = raw.trim()
        val neg = s.startsWith("-")
        if (s.startsWith("+") || s.startsWith("-")) s = s.substring(1)
        val digits = s.filter { it.isDigit() }
        if (digits.isEmpty()) return 0.0
        val v = digits.toLongOrNull() ?: return 0.0
        val meters = v / 1000.0
        return if (neg) -meters else meters
    }

    fun toTxt(points: List<GsiPoint>): String = buildString {
        points.forEach { p ->
            appendLine("${p.name}\t${fmt(p.e)}\t${fmt(p.n)}\t${fmt(p.z)}")
        }
    }

    fun toGsi(points: List<GsiPoint>): String = buildString {
        // فرمت مختصات مطلق شبیه BAHAR — قابل ورود به دوربین (بدون 21/22/31)
        points.forEachIndexed { idx, p ->
            val serial = (idx + 1).toString().padStart(4, '0')
            val codeWord = if (p.code.isNotBlank()) encodeName16(p.code) else "0000000000000000"
            fun signedCoord(v: Double): String {
                val sign = if (v < 0) "-" else "+"
                return ".." + "10" + sign + encodeCoord16(kotlin.math.abs(v))
            }
            append("*11")
            append(serial)
            append("+")
            append(encodeName16(p.name))
            append(" 71....+")
            append(codeWord)
            append(" 81")
            append(signedCoord(p.e))
            append(" 82")
            append(signedCoord(p.n))
            append(" 83")
            append(signedCoord(p.z))
            append("\r\n")
        }
    }

    fun toKml(points: List<GsiPoint>, name: String = "GSI Export"): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>$name</name>""")
        points.forEach { p ->
            appendLine("""<Placemark><name>${esc(p.name)}</name>""")
            appendLine("""<Point><coordinates>${fmt(p.e)},${fmt(p.n)},${fmt(p.z)}</coordinates></Point>""")
            appendLine("""</Placemark>""")
        }
        appendLine("""</Document></kml>""")
    }

    /** DXF R12 (AC1009) — سازگار با AutoCAD */
    fun toDxf(points: List<GsiPoint>): String = buildString {
        append("0\r\nSECTION\r\n2\r\nHEADER\r\n")
        append("9\r\n\$ACADVER\r\n1\r\nAC1009\r\n")
        append("0\r\nENDSEC\r\n")
        append("0\r\nSECTION\r\n2\r\nTABLES\r\n")
        append("0\r\nTABLE\r\n2\r\nLTYPE\r\n70\r\n1\r\n")
        append("0\r\nLTYPE\r\n2\r\nCONTINUOUS\r\n70\r\n0\r\n3\r\nSolid line\r\n72\r\n65\r\n73\r\n0\r\n40\r\n0.0\r\n")
        append("0\r\nENDTAB\r\n")
        append("0\r\nTABLE\r\n2\r\nLAYER\r\n70\r\n1\r\n")
        append("0\r\nLAYER\r\n2\r\nPOINTS\r\n70\r\n0\r\n62\r\n7\r\n6\r\nCONTINUOUS\r\n")
        append("0\r\nENDTAB\r\n")
        append("0\r\nENDSEC\r\n")
        append("0\r\nSECTION\r\n2\r\nENTITIES\r\n")
        points.forEach { p ->
            // صلیب به‌جای POINT (استایل POINT در اتوکد متغیر است)
            val s = 0.15
            append("0\r\nLINE\r\n8\r\nPOINTS\r\n")
            append("10\r\n${fmt(p.e - s)}\r\n20\r\n${fmt(p.n - s)}\r\n30\r\n${fmt(p.z)}\r\n")
            append("11\r\n${fmt(p.e + s)}\r\n21\r\n${fmt(p.n + s)}\r\n31\r\n${fmt(p.z)}\r\n")
            append("0\r\nLINE\r\n8\r\nPOINTS\r\n")
            append("10\r\n${fmt(p.e - s)}\r\n20\r\n${fmt(p.n + s)}\r\n30\r\n${fmt(p.z)}\r\n")
            append("11\r\n${fmt(p.e + s)}\r\n21\r\n${fmt(p.n - s)}\r\n31\r\n${fmt(p.z)}\r\n")
            append("0\r\nTEXT\r\n8\r\nPOINTS\r\n62\r\n7\r\n")
            append("10\r\n${fmt(p.e + 0.3)}\r\n20\r\n${fmt(p.n + 0.3)}\r\n30\r\n${fmt(p.z)}\r\n")
            append("40\r\n0.5\r\n1\r\n${p.name.replace("\n", " ")}\r\n50\r\n0\r\n")
        }
        append("0\r\nENDSEC\r\n0\r\nEOF\r\n")
    }

    private fun encodeName16(name: String): String = name.trim().take(16).padStart(16, '0')

    private fun encodeCoord16(meters: Double): String {
        val mm = kotlin.math.round(meters * 1000.0).toLong()
        return kotlin.math.abs(mm).toString().padStart(16, '0')
    }

    private fun fmt(v: Double): String =
        String.format(java.util.Locale.US, "%.3f", v)

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
