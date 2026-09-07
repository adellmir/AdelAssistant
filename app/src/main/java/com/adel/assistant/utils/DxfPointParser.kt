package com.adel.assistant.utils

import com.adel.assistant.data.SurveyPoint
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * پارس فایل نقاط مخصوص قابلیت DXF پیشرفته
 * - .dat  → n y x z d
 * - بقیه → N x y z d  (یا فرمت‌های رایج)
 */
object DxfPointParser {

    fun parse(inputStream: InputStream, fileName: String): ParseResult {
        val isDat = fileName.lowercase().endsWith(".dat")
        val points = mutableListOf<SurveyPoint>()
        val errors = mutableListOf<String>()
        var lineNumber = 0

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                lineNumber++
                val raw = line!!.trim()
                if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("//")) continue
                if (raw.lowercase().contains("id") && raw.lowercase().contains("x")) continue

                val parts = raw.split(Regex("[\\s,;\\t]+")).filter { it.isNotBlank() }
                if (parts.size < 4) {
                    errors.add("خط $lineNumber: ستون ناکافی")
                    continue
                }

                try {
                    val point = if (isDat && parts.size >= 5) {
                        // n y x z d
                        SurveyPoint(
                            id = parts[0],
                            y = parts[1].replace(',', '.').toDouble(),
                            x = parts[2].replace(',', '.').toDouble(),
                            z = parts[3].replace(',', '.').toDouble(),
                            code = parts[4]
                        )
                    } else if (parts.size >= 5) {
                        // N x y z d
                        SurveyPoint(
                            id = parts[0],
                            x = parts[1].replace(',', '.').toDouble(),
                            y = parts[2].replace(',', '.').toDouble(),
                            z = parts[3].replace(',', '.').toDouble(),
                            code = parts[4]
                        )
                    } else {
                        // حداقل ۴ ستون: id x y z
                        SurveyPoint(
                            id = parts[0],
                            x = parts[1].replace(',', '.').toDouble(),
                            y = parts[2].replace(',', '.').toDouble(),
                            z = parts[3].replace(',', '.').toDouble(),
                            code = parts.getOrElse(4) { "" }
                        )
                    }
                    points.add(point)
                } catch (e: NumberFormatException) {
                    errors.add("خط $lineNumber: عدد نامعتبر")
                }
            }
        }
        return ParseResult(points, errors)
    }

    data class ParseResult(
        val points: List<SurveyPoint>,
        val errors: List<String>
    )
}
