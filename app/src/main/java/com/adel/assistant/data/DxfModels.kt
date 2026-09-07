package com.adel.assistant.data

import androidx.compose.ui.graphics.Color

/**
 * نوع دسته‌بندی یک کد برای ترسیم DXF
 */
enum class CodeCategory {
    IGNORE,   // نادیده
    LINE,     // ترسیم خطوط
    POINT     // ترسیم نقاط
}

/**
 * تنظیمات یک کد خاص
 */
data class CodeSetting(
    val code: String,
    var category: CodeCategory,
    var colorIndex: Int = 0,
    var layerName: String = "",
    var closeOnE: Boolean = false,
    var showNumber: Boolean = true,
    var showXY: Boolean = false,
    var showZ: Boolean = false,
    var showCode: Boolean = true,
    var textSize: Float = 2.5f
)

/**
 * ۱۵ رنگ ثابت استاندارد
 */
object DxfColors {
    val names = listOf(
        "مشکی", "قرمز", "زرد", "سبز", "فیروزه‌ای",
        "آبی", "صورتی", "سفید", "خاکستری", "قهوه‌ای",
        "نارنجی", "بنفش", "سبز روشن", "آبی روشن", "قرمز تیره"
    )

    val aci = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 30, 30, 200, 3, 150, 10)

    val compose = listOf(
        Color(0xFF000000),
        Color(0xFFE53935),
        Color(0xFFFDD835),
        Color(0xFF43A047),
        Color(0xFF00ACC1),
        Color(0xFF1E88E5),
        Color(0xFFD81B60),
        Color(0xFFEEEEEE),
        Color(0xFF757575),
        Color(0xFF6D4C41),
        Color(0xFFFB8C00),
        Color(0xFF8E24AA),
        Color(0xFF66BB6A),
        Color(0xFF4FC3F7),
        Color(0xFFC62828)
    )
}

/**
 * پیش‌فرض‌های دسته‌بندی و لایه (طبق توافق)
 */
object DefaultCodeRules {

    private val lineCodes = setOf(
        "1", "2", "3", "8", "10", "as", "j", "sako", "gr", "hr", "p", "ple", "r"
    )
    private val pointCodes = setOf("tp")
    private val ignoreCodes = setOf(
        "4", "5", "6", "7", "9", "ab", "mt", "tm", "tb"
    )
    private val layerMap = mapOf(
        "1" to "jadval",
        "2" to "baghche",
        "3" to "divar",
        "8" to "busst",
        "10" to "arax",
        "as" to "asphalt",
        "sako" to "sako",
        "gr" to "gurd",
        "hr" to "handrail",
        "r" to "ramp",
        "ple" to "pele",
        "p" to "piadero"
    )
    private val defaultCloseOnE = setOf("ple", "r")

    fun createDefaultSetting(code: String): CodeSetting {
        val lower = code.lowercase().trim()
        val base = lower.replace(Regex("\\.e$", RegexOption.IGNORE_CASE), "").trim()

        val category = when {
            lineCodes.contains(base) || lineCodes.contains(lower) -> CodeCategory.LINE
            pointCodes.contains(base) || pointCodes.contains(lower) -> CodeCategory.POINT
            ignoreCodes.contains(base) || ignoreCodes.contains(lower) -> CodeCategory.IGNORE
            else -> CodeCategory.POINT
        }

        val layer = layerMap[base] ?: layerMap[lower] ?: when (category) {
            CodeCategory.LINE -> "L-$base"
            CodeCategory.POINT -> "P-$base"
            CodeCategory.IGNORE -> "ignore"
        }

        val closeOnE = defaultCloseOnE.contains(base) || defaultCloseOnE.contains(lower)
        val colorIndex = when (category) {
            CodeCategory.LINE -> 1
            CodeCategory.POINT -> 5
            CodeCategory.IGNORE -> 8
        }

        return CodeSetting(
            code = code,
            category = category,
            colorIndex = colorIndex,
            layerName = layer,
            closeOnE = closeOnE
        )
    }
}

/** آیا نقطه پایان خط است؟ (کد شامل .E) */
fun SurveyPoint.isEndOfLine(): Boolean =
    code.contains(".E", ignoreCase = true) ||
    (code.endsWith("E", ignoreCase = true) && code.contains("."))
