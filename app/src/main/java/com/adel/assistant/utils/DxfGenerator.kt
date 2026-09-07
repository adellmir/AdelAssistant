package com.adel.assistant.utils

import com.adel.assistant.data.CodeCategory
import com.adel.assistant.data.CodeSetting
import com.adel.assistant.data.DxfColors
import com.adel.assistant.data.SurveyPoint
import java.util.Locale

/**
 * تولید فایل DXF از نقاط و تنظیمات کدها
 */
object DxfGenerator {

    fun generate(
        points: List<SurveyPoint>,
        settings: Map<String, CodeSetting> // key = code (exact as in file)
    ): String {
        val sb = StringBuilder()

        // ---- HEADER ----
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("HEADER")
        sb.appendLine("9")
        sb.appendLine("\$ACADVER")
        sb.appendLine("1")
        sb.appendLine("AC1015") // AutoCAD 2000
        sb.appendLine("9")
        sb.appendLine("\$INSUNITS")
        sb.appendLine("70")
        sb.appendLine("6") // meters
        sb.appendLine("0")
        sb.appendLine("ENDSEC")

        // ---- TABLES (LAYERS) ----
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("TABLES")

        // Layer table
        sb.appendLine("0")
        sb.appendLine("TABLE")
        sb.appendLine("2")
        sb.appendLine("LAYER")
        sb.appendLine("70")
        sb.appendLine("0")

        // لایه ۰ پیش‌فرض
        writeLayer(sb, "0", 7)

        // لایه‌های مورد استفاده
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

        // ---- ENTITIES ----
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("ENTITIES")

        // گروه‌بندی نقاط بر اساس کد دقیق
        val byCode = points.groupBy { it.code }

        byCode.forEach { (code, codePoints) ->
            val setting = settings[code] ?: return@forEach
            if (setting.category == CodeCategory.IGNORE) return@forEach

            val layer = setting.layerName.ifBlank {
                if (setting.category == CodeCategory.LINE) "L-$code" else "P-$code"
            }
            val color = DxfColors.aci.getOrElse(setting.colorIndex) { 7 }

            when (setting.category) {
                CodeCategory.LINE -> {
                    writePolylines(sb, codePoints, layer, color, setting.closeOnE)
                }
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

    /**
     * ترسیم پلی‌لاین‌ها با رعایت قانون .E
     */
    private fun writePolylines(
        sb: StringBuilder,
        points: List<SurveyPoint>,
        layer: String,
        color: Int,
        closeOnE: Boolean
    ) {
        if (points.isEmpty()) return

        var currentSegment = mutableListOf<SurveyPoint>()

        fun flushSegment() {
            if (currentSegment.size >= 2) {
                writeLwPolyline(sb, currentSegment, layer, color)
            } else if (currentSegment.size == 1) {
                // نقطه تنها → به صورت نقطه بکش
                writePointSymbol(sb, currentSegment[0], layer, color)
            }
            currentSegment = mutableListOf()
        }

        points.forEach { p ->
            currentSegment.add(p)
            if (closeOnE && p.isEndOfLine) {
                flushSegment()
            }
        }
        flushSegment() // آخرین سگمنت
    }

    private fun writeLwPolyline(
        sb: StringBuilder,
        pts: List<SurveyPoint>,
        layer: String,
        color: Int
    ) {
        sb.appendLine("0")
        sb.appendLine("LWPOLYLINE")
        sb.appendLine("8")
        sb.appendLine(layer)
        sb.appendLine("62")
        sb.appendLine(color.toString())
        sb.appendLine("90")
        sb.appendLine(pts.size.toString())
        sb.appendLine("70")
        sb.appendLine("0") // open

        pts.forEach { p ->
            sb.appendLine("10")
            sb.appendLine(format(p.x))
            sb.appendLine("20")
            sb.appendLine(format(p.y))
            // Z برای LWPOLYLINE در elevation جداست، ولی برای سادگی اینجا نمی‌ذاریم
        }
    }

    /**
     * نماد نقطه: دایره کوچک + ضربدر
     */
    private fun writePointSymbol(
        sb: StringBuilder,
        p: SurveyPoint,
        layer: String,
        color: Int
    ) {
        val size = 0.15 // شعاع دایره

        // دایره
        sb.appendLine("0")
        sb.appendLine("CIRCLE")
        sb.appendLine("8")
        sb.appendLine(layer)
        sb.appendLine("62")
        sb.appendLine(color.toString())
        sb.appendLine("10")
        sb.appendLine(format(p.x))
        sb.appendLine("20")
        sb.appendLine(format(p.y))
        sb.appendLine("30")
        sb.appendLine(format(p.z))
        sb.appendLine("40")
        sb.appendLine(format(size))

        // ضربدر (دو خط)
        val d = size * 1.4
        // خط /
        sb.appendLine("0")
        sb.appendLine("LINE")
        sb.appendLine("8")
        sb.appendLine(layer)
        sb.appendLine("62")
        sb.appendLine(color.toString())
        sb.appendLine("10")
        sb.appendLine(format(p.x - d))
        sb.appendLine("20")
        sb.appendLine(format(p.y - d))
        sb.appendLine("11")
        sb.appendLine(format(p.x + d))
        sb.appendLine("21")
        sb.appendLine(format(p.y + d))

        // خط \
        sb.appendLine("0")
        sb.appendLine("LINE")
        sb.appendLine("8")
        sb.appendLine(layer)
        sb.appendLine("62")
        sb.appendLine(color.toString())
        sb.appendLine("10")
        sb.appendLine(format(p.x - d))
        sb.appendLine("20")
        sb.appendLine(format(p.y + d))
        sb.appendLine("11")
        sb.appendLine(format(p.x + d))
        sb.appendLine("21")
        sb.appendLine(format(p.y - d))
    }

    private fun writePointLabel(
        sb: StringBuilder,
        p: SurveyPoint,
        layer: String,
        color: Int,
        setting: CodeSetting
    ) {
        val parts = mutableListOf<String>()
        if (setting.showNumber) parts.add(p.n)
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
        sb.appendLine(format(p.x + offset))
        sb.appendLine("20")
        sb.appendLine(format(p.y + offset))
        sb.appendLine("30")
        sb.appendLine(format(p.z))
        sb.appendLine("40")
        sb.appendLine(format(setting.textSize.toDouble()))
        sb.appendLine("1")
        sb.appendLine(text)
        sb.appendLine("50")
        sb.appendLine("0")
    }

    private fun format(v: Double): String = String.format(Locale.US, "%.4f", v)
}
