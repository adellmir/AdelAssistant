package com.adel.assistant.utils

import com.adel.assistant.data.CodeCategory
import com.adel.assistant.data.CodeSetting
import com.adel.assistant.data.DxfColors
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.data.isEndOfLine
import java.util.Locale

/**
 * تولید DXF سازگار با AutoCAD:
 * - جداول LTYPE / LAYER / STYLE / APPID
 * - بخش BLOCKS
 * - خط پایان CRLF
 * - به‌جای LWPOLYLINE از LINE (سازگاری بیشتر)
 * - نام لایه فقط حروف امن
 */
object DxfMapGenerator {

    private const val CRLF = "\r\n"

    fun generate(
        points: List<SurveyPoint>,
        settings: Map<String, CodeSetting>
    ): String {
        val sb = StringBuilder()
        fun a(code: Any, value: Any) {
            sb.append(code).append(CRLF)
            sb.append(value).append(CRLF)
        }

        // HEADER
        a(0, "SECTION")
        a(2, "HEADER")
        a(9, "\$ACADVER")
        a(1, "AC1014")
        a(9, "\$INSUNITS")
        a(70, 6)
        a(0, "ENDSEC")

        // TABLES
        a(0, "SECTION")
        a(2, "TABLES")

        // LTYPE
        a(0, "TABLE")
        a(2, "LTYPE")
        a(70, 1)
        a(0, "LTYPE")
        a(2, "CONTINUOUS")
        a(70, 0)
        a(3, "Solid line")
        a(72, 65)
        a(73, 0)
        a(40, 0.0)
        a(0, "ENDTAB")

        // LAYER
        a(0, "TABLE")
        a(2, "LAYER")
        a(70, 256)
        writeLayer(a, "0", 7)

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
        usedLayers.forEach { (name, color) -> writeLayer(a, name, color) }
        a(0, "ENDTAB")

        // STYLE
        a(0, "TABLE")
        a(2, "STYLE")
        a(70, 1)
        a(0, "STYLE")
        a(2, "STANDARD")
        a(70, 0)
        a(40, 0.0)
        a(41, 1.0)
        a(50, 0.0)
        a(71, 0)
        a(42, 1.0)
        a(3, "txt")
        a(4, "")
        a(0, "ENDTAB")

        // APPID
        a(0, "TABLE")
        a(2, "APPID")
        a(70, 1)
        a(0, "APPID")
        a(2, "ACAD")
        a(70, 0)
        a(0, "ENDTAB")

        a(0, "ENDSEC")

        // BLOCKS
        a(0, "SECTION")
        a(2, "BLOCKS")
        a(0, "ENDSEC")

        // ENTITIES
        a(0, "SECTION")
        a(2, "ENTITIES")

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
                CodeCategory.LINE -> writePolylinesAsLines(a, codePoints, layer, color, setting.closeOnE)
                CodeCategory.POINT -> {
                    codePoints.forEach { p ->
                        writePointSymbol(a, p, layer, color)
                        writePointLabel(a, p, layer, color, setting)
                    }
                }
                else -> {}
            }
        }

        a(0, "ENDSEC")
        a(0, "EOF")
        return sb.toString()
    }

    private fun sanitizeLayer(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(31)
        return if (cleaned.isBlank()) "LAYER0" else cleaned
    }

    private fun writeLayer(a: (Any, Any) -> Unit, name: String, color: Int) {
        a(0, "LAYER")
        a(2, name)
        a(70, 0)
        a(62, color)
        a(6, "CONTINUOUS")
    }

    private fun writePolylinesAsLines(
        a: (Any, Any) -> Unit,
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
                    writeLine(a, current[i], current[i + 1], layer, color)
                }
            } else if (current.size == 1) {
                writePointSymbol(a, current[0], layer, color)
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
        a: (Any, Any) -> Unit,
        p1: SurveyPoint,
        p2: SurveyPoint,
        layer: String,
        color: Int
    ) {
        a(0, "LINE")
        a(8, layer)
        a(62, color)
        a(10, fmt(p1.x))
        a(20, fmt(p1.y))
        a(30, fmt(p1.z))
        a(11, fmt(p2.x))
        a(21, fmt(p2.y))
        a(31, fmt(p2.z))
    }

    private fun writePointSymbol(a: (Any, Any) -> Unit, p: SurveyPoint, layer: String, color: Int) {
        val size = 0.15
        a(0, "CIRCLE")
        a(8, layer)
        a(62, color)
        a(10, fmt(p.x))
        a(20, fmt(p.y))
        a(30, fmt(p.z))
        a(40, fmt(size))

        val d = size * 1.4
        a(0, "LINE")
        a(8, layer)
        a(62, color)
        a(10, fmt(p.x - d))
        a(20, fmt(p.y - d))
        a(30, fmt(p.z))
        a(11, fmt(p.x + d))
        a(21, fmt(p.y + d))
        a(31, fmt(p.z))

        a(0, "LINE")
        a(8, layer)
        a(62, color)
        a(10, fmt(p.x - d))
        a(20, fmt(p.y + d))
        a(30, fmt(p.z))
        a(11, fmt(p.x + d))
        a(21, fmt(p.y - d))
        a(31, fmt(p.z))
    }

    private fun writePointLabel(
        a: (Any, Any) -> Unit,
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

        // در DXF کاراکتر | گاهی مشکل‌ساز است
        val text = parts.joinToString(" - ")
            .replace("\r", " ")
            .replace("\n", " ")

        a(0, "TEXT")
        a(8, layer)
        a(62, color)
        a(10, fmt(p.x + 0.3))
        a(20, fmt(p.y + 0.3))
        a(30, fmt(p.z))
        a(40, fmt(setting.textSize.toDouble().coerceAtLeast(0.1)))
        a(1, text)
        a(50, 0)
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.4f", v)
}
