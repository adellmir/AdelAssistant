package com.adel.assistant.data

import java.util.Locale

data class SurveyPoint(val id: String, val x: Double, val y: Double, val z: Double, val code: String = "")

object PointConverter {
    fun read(text: String, extension: String): List<SurveyPoint> = when (extension.lowercase()) {
        "gsi" -> parseGsi(text)
        "idx" -> parseIdx(text)
        "dxf" -> parseDxf(text)
        else -> parseDelimited(text)
    }

    fun write(points: List<SurveyPoint>, extension: String): String = when (extension.lowercase()) {
        "csv" -> points.joinToString("\n", "ID,X,Y,Z,CODE\n") { p -> "${p.id},${f(p.x)},${f(p.y)},${f(p.z)},${p.code}" }
        "txt", "dat" -> points.joinToString("\n") { p -> listOf(p.id, f(p.x), f(p.y), f(p.z), p.code).joinToString("\t") }
        "dxf" -> dxf(points)
        "gsi" -> gsi(points)
        "idx" -> idx(points)
        else -> throw IllegalArgumentException("فرمت خروجی پشتیبانی نمی‌شود")
    }

    private fun parseDelimited(text: String): List<SurveyPoint> = text.lineSequence().mapNotNull { line ->
        val s = line.trim()
        if (s.isBlank() || s.startsWith("#") || s.lowercase().contains("id,x,y,z")) return@mapNotNull null
        val a = s.split(Regex("[,;\\t ]+")).filter { it.isNotBlank() }
        if (a.size < 4) return@mapNotNull null
        val n = a.map { it.replace(',', '.').toDoubleOrNull() }
        // ID X Y Z [CODE]
        val x = n.getOrNull(1) ?: return@mapNotNull null
        val y = n.getOrNull(2) ?: return@mapNotNull null
        val z = n.getOrNull(3) ?: return@mapNotNull null
        SurveyPoint(a[0].trim('"'), x, y, z, a.drop(4).joinToString(" "))
    }.toList()

    private fun parseIdx(text: String): List<SurveyPoint> {
        val r = Regex("^\\s*\\d+\\s*,\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"([^\\\"]*)\\\"\\s*,\\s*([-+0-9.]+)\\s*,\\s*([-+0-9.]+)\\s*,\\s*([-+0-9.]+)")
        return text.lineSequence().mapNotNull { m -> r.find(m)?.let { SurveyPoint(it.groupValues[1], it.groupValues[3].toDouble(), it.groupValues[4].toDouble(), it.groupValues[5].toDouble(), it.groupValues[2]) } }.toList()
    }

    private fun parseGsi(text: String): List<SurveyPoint> = text.lineSequence().mapNotNull { line ->
        // Ignore station/setup and backsight records such as OCUPAR and RE.
        if (line.contains("OCUPAR", true) || Regex("\\bRE\\b").containsMatchIn(line)) return@mapNotNull null
        val point = Regex("\\*11(\\d{4,})").find(line)?.groupValues?.get(1)?.trimStart('0') ?: return@mapNotNull null
        fun coord(key: String): Double? {
            val raw = Regex("$key\\.\\.00\\+([0-9]+)").find(line)?.groupValues?.get(1) ?: return null
            return raw.toDouble() / 10000.0
        }
        val x = coord("81") ?: return@mapNotNull null
        val y = coord("82") ?: return@mapNotNull null
        val z = coord("83") ?: return@mapNotNull null
        val code = Regex("42(?:\\.\\.\\.\\.|\\d{4})\\+0+([^\\s]+)").find(line)?.groupValues?.getOrNull(1)?.trimEnd('0') ?: ""
        SurveyPoint(if (point.isBlank()) "0" else point, x, y, z, code)
    }.toList()

    private fun parseDxf(text: String): List<SurveyPoint> {
        val lines = text.lines()
        val out = mutableListOf<SurveyPoint>()
        var i = 0; var no = 1
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

    private fun dxf(points: List<SurveyPoint>) = buildString {
        append("0\nSECTION\n2\nENTITIES\n")
        points.forEach { p -> append("0\nPOINT\n8\n${p.code.ifBlank { "POINTS" }}\n10\n${f(p.x)}\n20\n${f(p.y)}\n30\n${f(p.z)}\n") }
        append("0\nENDSEC\n0\nEOF\n")
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
