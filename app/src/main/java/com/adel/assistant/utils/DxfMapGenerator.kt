package com.adel.assistant.utils

import com.adel.assistant.data.codeBase
import com.adel.assistant.data.CodeCategory
import com.adel.assistant.data.CodeSetting
import com.adel.assistant.data.DxfColors
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.data.isEndOfLine
import java.util.Locale

/**
 * تولید DXF معتبر برای AutoCAD / Civil / اپ‌های موبایل.
 *
 * فرمت: AutoCAD R12 (AC1009) — بدون نیاز به handle و AcDbSymbolTable
 * که در AC1014 ناقص باعث خطای:
 * "Class separator for class AcDbSymbolTable expected" می‌شد.
 */
object DxfMapGenerator {

    private const val CRLF = "\r\n"

    fun generate(
        points: List<SurveyPoint>,
        settings: Map<String, CodeSetting>
    ): String {
        val sb = StringBuilder()

        // ---------- HEADER ----------
        pair(sb, 0, "SECTION")
        pair(sb, 2, "HEADER")
        pair(sb, 9, "\$ACADVER")
        pair(sb, 1, "AC1009")
        pair(sb, 9, "\$INSUNITS")
        pair(sb, 70, "6") // meters
        pair(sb, 0, "ENDSEC")

        // ---------- TABLES ----------
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
        val usedLayers = linkedMapOf<String, Int>()
        usedLayers["0"] = 7
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
        pair(sb, 0, "TABLE")
        pair(sb, 2, "LAYER")
        pair(sb, 70, usedLayers.size.toString())
        usedLayers.forEach { (name, color) -> writeLayer(sb, name, color) }
        pair(sb, 0, "ENDTAB")

        pair(sb, 0, "ENDSEC")

        // ---------- ENTITIES ----------
        pair(sb, 0, "SECTION")
        pair(sb, 2, "ENTITIES")

        writeAllLinesInOrder(sb, points, settings)

        points.forEach { p ->
            val setting = resolveSetting(settings, p.code) ?: return@forEach
            if (setting.category != CodeCategory.POINT) return@forEach
            val layer = sanitizeLayer(setting.layerName.ifBlank { "P-${p.code}" })
            val color = DxfColors.aci.getOrElse(setting.colorIndex) { 7 }
            writePointSymbol(sb, p, layer, color)
            writePointLabel(sb, p, layer, color, setting)
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
        pair(sb, 62, color.coerceIn(1, 255).toString())
        pair(sb, 6, "CONTINUOUS")
    }

    private fun resolveSetting(settings: Map<String, CodeSetting>, code: String): CodeSetting? {
        settings[code]?.let { return it }
        val base = codeBase(code)
        if (base.isNotBlank()) {
            settings[base]?.let { return it }
            settings[base.lowercase()]?.let { return it }
        }
        settings[code.lowercase()]?.let { return it }
        // هر کلیدی که پایهٔ یکسان دارد (مثلاً 1 و 1.e و e.1)
        return settings.entries.firstOrNull {
            codeBase(it.key).equals(base, true) || it.key.equals(code, true)
        }?.value
    }

    private fun writeAllLinesInOrder(
        sb: StringBuilder,
        points: List<SurveyPoint>,
        settings: Map<String, CodeSetting>
    ) {
        var current = mutableListOf<SurveyPoint>()
        var currentLayer = "0"
        var currentColor = 7
        var closeOnE = false

        fun flush() {
            if (current.size >= 2) {
                for (i in 0 until current.size - 1) {
                    writeLine(sb, current[i], current[i + 1], currentLayer, currentColor)
                }
            } else if (current.size == 1) {
                writePointSymbol(sb, current[0], currentLayer, currentColor)
            }
            current = mutableListOf()
        }

        points.forEach { p ->
            val setting = resolveSetting(settings, p.code)
            if (setting == null || setting.category != CodeCategory.LINE) {
                flush()
                return@forEach
            }
            val layer = sanitizeLayer(
                setting.layerName.ifBlank { "L-${p.code.substringBefore(".")}" }
            )
            val color = DxfColors.aci.getOrElse(setting.colorIndex) { 7 }
            if (current.isNotEmpty() && layer != currentLayer) flush()
            currentLayer = layer
            currentColor = color
            closeOnE = setting.closeOnE
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
        pair(sb, 62, "7")
        pair(sb, 10, fmt(p.x + 0.3))
        pair(sb, 20, fmt(p.y + 0.3))
        pair(sb, 30, fmt(p.z))
        pair(sb, 40, fmt(setting.textSize.toDouble().coerceAtLeast(0.1)))
        pair(sb, 1, text)
        pair(sb, 50, "0")
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.4f", v)
}
