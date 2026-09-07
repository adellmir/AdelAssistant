package com.adel.assistant.utils

import com.adel.assistant.data.CodeCategory
import com.adel.assistant.data.CodeSetting
import com.adel.assistant.data.DxfColors
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.data.isEndOfLine
import java.util.Locale

/**
 * تولید DXF پیشرفته: خط + نقطه + لایه + برچسب + قانون .E
 */
object DxfMapGenerator {

    fun generate(
        points: List<SurveyPoint>,
        settings: Map<String, CodeSetting>
    ): String {
        val sb = StringBuilder()

        // HEADER
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("HEADER")
        sb.appendLine("9")
        sb.appendLine("\$ACADVER")
        sb.appendLine("1")
        sb.appendLine("AC1015")
        sb.appendLine("9")
        sb.appendLine("\$INSUNITS")
        sb.appendLine("70")
        sb.appendLine("6")
        sb.appendLine("0")
        sb.appendLine("ENDSEC")

        // TABLES / LAYERS
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("TABLES")
        sb.appendLine("0")
        sb.appendLine("TABLE")
        sb.appendLine("2")
        sb.appendLine("LAYER")
        sb.appendLine("70")
        sb.appendLine("0")
        writeLayer(sb, "0", 7)

        val usedLayers = settings.values
            .filter { it.category != CodeCategory.IGNORE }
            .map { it.layerName.ifBlank { "L-${it.code}" } }
            .distinct()

        usedLayers.forEach { layerName ->
            val setting = settings.values.find { it.layerName == layerName }
            val color = setting?.let { DxfColors.aci.getOrElse(it.colorIndex) { 7 } } ?: 7
            writeLayer(sb, layerName, color)
        }

        sb.appendLine("0")
        sb.appendLine("ENDTAB")
        sb.appendLine("0")
        sb.appendLine("ENDSEC")

        // ENTITIES
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("ENTITIES")

        val byCode = points.groupBy { it.code }

        byCode.forEach { (code, codePoints) ->
            val setting = settings[code] ?: return@forEach
            if (setting.category == CodeCategory.IGNORE) return@forEach

            val layer = setting.layerName.ifBlank {
                if (setting.category == CodeCategory.LINE) "L-$code" else "P-$code"
            }
            val color = DxfColors.aci.getOrElse(setting.colorIndex) { 7 }

            when (setting.category) {
                CodeCategory.LINE -> writePolylines(sb, codePoints, layer, color, setting.closeOnE)
                CodeCategory.POINT -> {
                    codePoints.forEach { p ->
                        writePointSymbol(sb, p, layer, color)
                        writePointLabel(sb, p, layer, color, setting)
                    }
                }
                else -> {}
            }
        }

        sb.appendLine("0")
        sb.appendLine("ENDSEC")
        sb.appendLine("0")
        sb.appendLine("EOF")
        return sb.toString()
    }

    private fun writeLayer(sb: StringBuilder, name: String, color: Int) {
        sb.appendLine("0")
        sb.appendLine("LAYER")
        sb.appendLine("2")
        sb.appendLine(name)
        sb.appendLine("70")
        sb.appendLine("0")
        sb.appendLine("62")
        sb.appendLine(color.toString())
        sb.appendLine("6")
        sb.appendLine("CONTINUOUS")
    }

    private fun writePolylines(
        sb: StringBuilder,
        points: List<SurveyPoint>,
        layer: String,
        color: Int,
        closeOnE: Boolean
    ) {
        if (points.isEmpty()) return
        var current = mutableListOf<SurveyPoint>()

        fun flush() {
            if (current.size >= 2) writeLwPolyline(sb, current, layer, color)
            else if (current.size == 1) writePointSymbol(sb, current[0], layer, color)
            current = mutableListOf()
        }

        points.forEach { p ->
            current.add(p)
            if (closeOnE && p.isEndOfLine()) flush()
        }
        flush()
    }

    private fun writeLwPolyline(sb: StringBuilder, pts: List<SurveyPoint>, layer: String, color: Int) {
        sb.appendLine("0")
        sb.appendLine("LWPOLYLINE")
        sb.appendLine("8")
        sb.appendLine(layer)
        sb.appendLine("62")
        sb.appendLine(color.toString())
        sb.appendLine("90")
        sb.appendLine(pts.size.toString())
        sb.appendLine("70")
        sb.appendLine("0")
        pts.forEach { p ->
            sb.appendLine("10")
            sb.appendLine(fmt(p.x))
            sb.appendLine("20")
            sb.appendLine(fmt(p.y))
        }
    }

    private fun writePointSymbol(sb: StringBuilder, p: SurveyPoint, layer: String, color: Int) {
        val size = 0.15
        // دایره
        sb.appendLine("0")
        sb.appendLine("CIRCLE")
        sb.appendLine("8")
        sb.appendLine(layer)
        sb.appendLine("62")
        sb.appendLine(color.toString())
        sb.appendLine("10")
        sb.appendLine(fmt(p.x))
        sb.appendLine("20")
        sb.appendLine(fmt(p.y))
        sb.appendLine("30")
        sb.appendLine(fmt(p.z))
        sb.appendLine("40")
        sb.appendLine(fmt(size))

        val d = size * 1.4
        // ضربدر
        sb.appendLine("0")
        sb.appendLine("LINE")
        sb.appendLine("8")
        sb.appendLine(layer)
        sb.appendLine("62")
        sb.appendLine(color.toString())
        sb.appendLine("10")
        sb.appendLine(fmt(p.x - d))
        sb.appendLine("20")
        sb.appendLine(fmt(p.y - d))
        sb.appendLine("11")
        sb.appendLine(fmt(p.x + d))
        sb.appendLine("21")
        sb.appendLine(fmt(p.y + d))

        sb.appendLine("0")
        sb.appendLine("LINE")
        sb.appendLine("8")
        sb.appendLine(layer)
        sb.appendLine("62")
        sb.appendLine(color.toString())
        sb.appendLine("10")
        sb.appendLine(fmt(p.x - d))
        sb.appendLine("20")
        sb.appendLine(fmt(p.y + d))
        sb.appendLine("11")
        sb.appendLine(fmt(p.x + d))
        sb.appendLine("21")
        sb.appendLine(fmt(p.y - d))
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

        val text = parts.joinToString(" | ")
        val offset = 0.3

        sb.appendLine("0")
        sb.appendLine("TEXT")
        sb.appendLine("8")
        sb.appendLine(layer)
        sb.appendLine("62")
        sb.appendLine(color.toString())
        sb.appendLine("10")
        sb.appendLine(fmt(p.x + offset))
        sb.appendLine("20")
        sb.appendLine(fmt(p.y + offset))
        sb.appendLine("30")
        sb.appendLine(fmt(p.z))
        sb.appendLine("40")
        sb.appendLine(fmt(setting.textSize.toDouble()))
        sb.appendLine("1")
        sb.appendLine(text)
        sb.appendLine("50")
        sb.appendLine("0")
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.4f", v)
}
