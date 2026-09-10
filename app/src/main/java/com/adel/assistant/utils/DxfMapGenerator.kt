package com.adel.assistant.utils

import com.adel.assistant.data.CodeCategory
import com.adel.assistant.data.CodeSetting
import com.adel.assistant.data.DxfColors
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.data.isEndOfLine
import java.util.Locale

/**
 * تولید DXF سازگار با AutoCAD
 * - جداول LTYPE / LAYER / STYLE / APPID
 * - بخش BLOCKS
 * - پایان خط CRLF
 * - خطوط با LINE به‌جای LWPOLYLINE
 */
object DxfMapGenerator {

    private const val CRLF = "\r\n"

    fun generate(
        points: List<SurveyPoint>,
        settings: Map<String, CodeSetting>
    ): String {
        val sb = StringBuilder()

        // HEADER
        pair(sb, 0, "SECTION")
        pair(sb, 2, "HEADER")
        pair(sb, 9, "\$ACADVER")
        pair(sb, 1, "AC1014")
        pair(sb, 9, "\$INSUNITS")
        pair(sb, 70, "6")
        pair(sb, 0, "ENDSEC")

        // TABLES
        pair(sb, 0, "SECTION")
        pair(sb, 2, "TABLES")

        // LTYPE
        pair(sb, 0, "TABLE")
        pair(sb, 2, "LTYPE")
        pair(sb, 70, "1")
        pair(sb, 0, "LTYPE")
        pair(sb, 2, "CONTINUOUS")
        pair(sb, 70, "0")
        pair(sb, 3, "Solid line")
        pair(sb, 72, "65")
        pair(sb, 73, "0")
        pair(sb, 40, "0.0")
        pair(sb, 0, "ENDTAB")

        // LAYER
        pair(sb, 0, "TABLE")
        pair(sb, 2, "LAYER")
        pair(sb, 70, "256")
        writeLayer(sb, "0", 7)

        val usedLayers = linkedMapOf<String, Int>()
        settings.values
            .filter { it.category != CodeCategory.IGNORE }
            .forEach { s ->
                val layer = sanitizeLayer(
                    s.layerName.ifBlank {
                        if (s.category == CodeCategory.LINE) "L-${s.code}" else "P-${s.code}"
                    }
                )
                val color = DxfColors.aci.getOrElse(s.colorIndex) { 7 }
                usedLayers.putIfAbsent(layer, color)
            }
        usedLayers.forEach { (name, color) -> writeLayer(sb, name, color) }
        pair(sb, 0, "ENDTAB")

        // STYLE
        pair(sb, 0, "TABLE")
        pair(sb, 2, "STYLE")
        pair(sb, 70, "1")
        pair(sb, 0, "STYLE")
        pair(sb, 2, "STANDARD")
        pair(sb, 70, "0")
        pair(sb, 40, "0.0")
        pair(sb, 41, "1.0")
        pair(sb, 50, "0.0")
        pair(sb, 71, "0")
        pair(sb, 42, "1.0")
        pair(sb, 3, "txt")
        pair(sb, 4, "")
        pair(sb, 0, "ENDTAB")

        // APPID
        pair(sb, 0, "TABLE")
        pair(sb, 2, "APPID")
        pair(sb, 70, "1")
        pair(sb, 0, "APPID")
        pair(sb, 2, "ACAD")
        pair(sb, 70, "0")
        pair(sb, 0, "ENDTAB")

        pair(sb, 0, "ENDSEC")

        // BLOCKS
        pair(sb, 0, "SECTION")
        pair(sb, 2, "BLOCKS")
        pair(sb, 0, "ENDSEC")

        // ENTITIES
        pair(sb, 0, "SECTION")
        pair(sb, 2, "ENTITIES")

        val byCode = points.groupBy { it.code }
        byCode.forEach { (code, codePoints) ->
            val setting = settings[code] ?: return@forEach
            if (setting.category == CodeCategory.IGNORE) return@forEach

            val layer = sanitizeLayer(
                setting.layerName.ifBlank {
                    if (setting.category == CodeCategory.LINE) "L-$code" else "P-$code"
                }
            )
            val color = DxfColors.aci.getOrElse(setting.colorIndex) { 7 }

            when (setting.category) {
                CodeCategory.LINE -> writePolylinesAsLines(sb, codePoints, layer, color, setting.closeOnE)
                CodeCategory.POINT -> {
                    codePoints.forEach { p ->
                        writePointSymbol(sb, p, layer, color)
                        writePointLabel(sb, p, layer, color, setting)
                    }
                }
                else -> {}
            }
        }

        pair(sb, 0, "ENDSEC")
        pair(sb, 0, "EOF")
        return sb.toString()
    }

    private fun pair(sb: StringBuilder, code: Int, value: String) {
        sb.append(code).append(CRLF)
        sb.append(value).append(CRLF)
    }

    private fun sanitizeLayer(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(31)
        return if (cleaned.isBlank()) "LAYER0" else cleaned
    }

    private fun writeLayer(sb: StringBuilder, name: String, color: Int) {
        pair(sb, 0, "LAYER")
        pair(sb, 2, name)
        pair(sb, 70, "0")
        pair(sb, 62, color.toString())
        pair(sb, 6, "CONTINUOUS")
    }

    private fun writePolylinesAsLines(
        sb: StringBuilder,
        points: List<SurveyPoint>,
        layer: String,
        color: Int,
        closeOnE: Boolean
    ) {
        if (points.isEmpty()) return
        var current = mutableListOf<SurveyPoint>()

        fun flush() {
            if (current.size >= 2) {
                for (i in 0 until current.size - 1) {
                    writeLine(sb, current[i], current[i + 1], layer, color)
                }
            } else if (current.size == 1) {
                writePointSymbol(sb, current[0], layer, color)
            }
            current = mutableListOf()
        }

        points.forEach { p ->
            current.add(p)
            if (closeOnE && p.isEndOfLine()) flush()
        }
        flush()
    }

    private fun writeLine(
        sb: StringBuilder,
        p1: SurveyPoint,
        p2: SurveyPoint,
        layer: String,
        color: Int
    ) {
        pair(sb, 0, "LINE")
        pair(sb, 8, layer)
        pair(sb, 62, color.toString())
        pair(sb, 10, fmt(p1.x))
        pair(sb, 20, fmt(p1.y))
        pair(sb, 30, fmt(p1.z))
        pair(sb, 11, fmt(p2.x))
        pair(sb, 21, fmt(p2.y))
        pair(sb, 31, fmt(p2.z))
    }

    private fun writePointSymbol(sb: StringBuilder, p: SurveyPoint, layer: String, color: Int) {
        val size = 0.15
        pair(sb, 0, "CIRCLE")
        pair(sb, 8, layer)
        pair(sb, 62, color.toString())
        pair(sb, 10, fmt(p.x))
        pair(sb, 20, fmt(p.y))
        pair(sb, 30, fmt(p.z))
        pair(sb, 40, fmt(size))

        val d = size * 1.4
        pair(sb, 0, "LINE")
        pair(sb, 8, layer)
        pair(sb, 62, color.toString())
        pair(sb, 10, fmt(p.x - d))
        pair(sb, 20, fmt(p.y - d))
        pair(sb, 30, fmt(p.z))
        pair(sb, 11, fmt(p.x + d))
        pair(sb, 21, fmt(p.y + d))
        pair(sb, 31, fmt(p.z))

        pair(sb, 0, "LINE")
        pair(sb, 8, layer)
        pair(sb, 62, color.toString())
        pair(sb, 10, fmt(p.x - d))
        pair(sb, 20, fmt(p.y + d))
        pair(sb, 30, fmt(p.z))
        pair(sb, 11, fmt(p.x + d))
        pair(sb, 21, fmt(p.y - d))
        pair(sb, 31, fmt(p.z))
    }

    private fun writePointLabel(
        sb: StringBuilder,
        p: SurveyPoint,
        layer: String,
        color: Int,
        setting: CodeSetting
    ) {
        val parts = mutableListOf<String>()
        if (setting.showNumber) parts.add(p.id)
        if (setting.showXY) parts.add(String.format(Locale.US, "%.3f,%.3f", p.x, p.y))
        if (setting.showZ) parts.add(String.format(Locale.US, "Z:%.2f", p.z))
        if (setting.showCode) parts.add(p.code)
        if (parts.isEmpty()) return

        val text = parts.joinToString(" - ")
            .replace("\r", " ")
            .replace("\n", " ")

        pair(sb, 0, "TEXT")
        pair(sb, 8, layer)
        pair(sb, 62, "250")
        pair(sb, 10, fmt(p.x + 0.3))
        pair(sb, 20, fmt(p.y + 0.3))
        pair(sb, 30, fmt(p.z))
        pair(sb, 40, fmt(setting.textSize.toDouble().coerceAtLeast(0.1)))
        pair(sb, 1, text)
        pair(sb, 50, "0")
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.4f", v)
}
