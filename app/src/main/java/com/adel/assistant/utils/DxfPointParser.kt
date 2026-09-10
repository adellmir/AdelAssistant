package com.adel.assistant.utils

import com.adel.assistant.data.PointConverter
import com.adel.assistant.data.SurveyPoint
import java.io.InputStream

/** پارس فایل نقاط برای DXF — GSI با همان موتور مبدل */
object DxfPointParser {
    data class ParseResult(val points: List<SurveyPoint>, val errors: List<String>)

    fun parse(inputStream: InputStream, fileName: String): ParseResult {
        val text = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val lower = fileName.lowercase()
        if (lower.endsWith(".gsi")) {
            val pts = PointConverter.read(text, "gsi")
            return ParseResult(pts, if (pts.isEmpty()) listOf("نقطه GSI پیدا نشد") else emptyList())
        }
        return parseDelimited(text, lower.endsWith(".dat"))
    }

    private fun parseDelimited(text: String, isDat: Boolean): ParseResult {
        val points = mutableListOf<SurveyPoint>()
        val errors = mutableListOf<String>()
        var lineNumber = 0
        for (raw0 in text.lineSequence()) {
            lineNumber++
            val raw = raw0.trim()
            if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("//")) continue
            if (raw.lowercase().contains("id") && raw.lowercase().contains("x")) continue
            val parts = raw.split(Regex("[\\s,;\\t]+")).filter { it.isNotBlank() }
            if (parts.size < 4) {
                errors.add("خط $lineNumber: ستون ناکافی")
                continue
            }
            try {
                val point = if (isDat && parts.size >= 5) {
                    SurveyPoint(parts[0], parts[2].replace(',', '.').toDouble(), parts[1].replace(',', '.').toDouble(), parts[3].replace(',', '.').toDouble(), parts[4])
                } else if (parts.size >= 5) {
                    SurveyPoint(parts[0], parts[1].replace(',', '.').toDouble(), parts[2].replace(',', '.').toDouble(), parts[3].replace(',', '.').toDouble(), parts[4])
                } else {
                    SurveyPoint(parts[0], parts[1].replace(',', '.').toDouble(), parts[2].replace(',', '.').toDouble(), parts[3].replace(',', '.').toDouble(), "")
                }
                points += point
            } catch (e: Exception) {
                errors.add("خط $lineNumber: ${e.message}")
            }
        }
        return ParseResult(points, errors)
    }
}
