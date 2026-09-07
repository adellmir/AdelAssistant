package com.adel.assistant.utils

import com.adel.assistant.data.SurveyPoint
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * پارس کردن فایل نقاط
 * - .dat  → n y x z d
 * - بقیه → N x y z d
 */
object PointFileParser {

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

                // جدا کردن با فاصله یا تب یا کاما
                val parts = raw.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
                if (parts.size < 5) {
                    errors.add("خط $lineNumber: تعداد ستون کمتر از ۵ ($raw)")
                    continue
                }

                try {
                    val point = if (isDat) {
                        // n y x z d
                        SurveyPoint(
                            n = parts[0],
                            y = parts[1].toDouble(),
                            x = parts[2].toDouble(),
                            z = parts[3].toDouble(),
                            code = parts[4],
                            rawLine = raw
                        )
                    } else {
                        // N x y z d
                        SurveyPoint(
                            n = parts[0],
                            x = parts[1].toDouble(),
                            y = parts[2].toDouble(),
                            z = parts[3].toDouble(),
                            code = parts[4],
                            rawLine = raw
                        )
                    }
                    points.add(point)
                } catch (e: NumberFormatException) {
                    errors.add("خط $lineNumber: عدد نامعتبر ($raw)")
                }
            }
        }

        return ParseResult(points, errors, isDat)
    }

    data class ParseResult(
        val points: List<SurveyPoint>,
        val errors: List<String>,
        val isDatFormat: Boolean
    )
}
