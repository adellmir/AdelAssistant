package com.adel.assistant.data

/**
 * یک نقطه برداشت
 * @param n شماره نقطه
 * @param x مختصات X (شرقی)
 * @param y مختصات Y (شمالی)
 * @param z ارتفاع
 * @param code کد / خانواده (d)
 * @param rawLine خط خام برای دیباگ
 */
data class SurveyPoint(
    val n: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val code: String,
    val rawLine: String = ""
) {
    /** آیا این نقطه پایان خط است؟ (شامل .E یا ختم به .E) */
    val isEndOfLine: Boolean
        get() = code.contains(".E", ignoreCase = true) || code.endsWith("E", ignoreCase = true) && code.contains(".")
}

/**
 * نوع دسته‌بندی یک کد
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
    var colorIndex: Int = 0,          // ایندکس از لیست ۱۵ رنگ
    var layerName: String = "",       // نام لایه در DXF
    var closeOnE: Boolean = false,    // بستن ترسیم بعد از .E (فقط برای LINE)
    // تنظیمات برچسب (فقط برای POINT)
    var showNumber: Boolean = true,
    var showXY: Boolean = false,
    var showZ: Boolean = false,
    var showCode: Boolean = true,
    var textSize: Float = 2.5f
)

/**
 * ۱۵ رنگ ثابت استاندارد (شبیه AutoCAD)
 * ایندکس ۰ = مشکی
 */
object DxfColors {
    val names = listOf(
        "مشکی", "قرمز", "زرد", "سبز", "فیروزه‌ای",
        "آبی", "صورتی", "سفید", "خاکستری", "قهوه‌ای",
        "نارنجی", "بنفش", "سبز روشن", "آبی روشن", "قرمز تیره"
    )

    // رنگ‌های ACI تقریبی برای DXF (1-based در AutoCAD، اینجا 0-based)
    val aci = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 30, 30, 200, 3, 150, 10)

    // رنگ‌های Compose برای نمایش در UI
    val compose = listOf(
        androidx.compose.ui.graphics.Color(0xFF000000), // مشکی
        androidx.compose.ui.graphics.Color(0xFFE53935), // قرمز
        androidx.compose.ui.graphics.Color(0xFFFDD835), // زرد
        androidx.compose.ui.graphics.Color(0xFF43A047), // سبز
        androidx.compose.ui.graphics.Color(0xFF00ACC1), // فیروزه‌ای
        androidx.compose.ui.graphics.Color(0xFF1E88E5), // آبی
        androidx.compose.ui.graphics.Color(0xFFD81B60), // صورتی
        androidx.compose.ui.graphics.Color(0xFFEEEEEE), // سفید
        androidx.compose.ui.graphics.Color(0xFF757575), // خاکستری
        androidx.compose.ui.graphics.Color(0xFF6D4C41), // قهوه‌ای
        androidx.compose.ui.graphics.Color(0xFFFB8C00), // نارنجی
        androidx.compose.ui.graphics.Color(0xFF8E24AA), // بنفش
        androidx.compose.ui.graphics.Color(0xFF66BB6A), // سبز روشن
        androidx.compose.ui.graphics.Color(0xFF4FC3F7), // آبی روشن
        androidx.compose.ui.graphics.Color(0xFFC62828)  // قرمز تیره
    )
}

/**
 * پیش‌فرض‌های دسته‌بندی و لایه
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

    /** کدهایی که پیش‌فرض تیک بستن بعد از .E دارند */
    private val defaultCloseOnE = setOf("ple", "r")

    fun createDefaultSetting(code: String): CodeSetting {
        val lower = code.lowercase().trim()
        // حذف پسوند .E برای تشخیص دسته اصلی
        val base = lower.replace(Regex("\\.e$", RegexOption.IGNORE_CASE), "").trim()

        val category = when {
            lineCodes.contains(base) || lineCodes.contains(lower) -> CodeCategory.LINE
            pointCodes.contains(base) || pointCodes.contains(lower) -> CodeCategory.POINT
            ignoreCodes.contains(base) || ignoreCodes.contains(lower) -> CodeCategory.IGNORE
            else -> CodeCategory.POINT // پیش‌فرض امن
        }

        val layer = layerMap[base] ?: layerMap[lower] ?: when (category) {
            CodeCategory.LINE -> "L-$base"
            CodeCategory.POINT -> "P-$base"
            CodeCategory.IGNORE -> "ignore"
        }

        val closeOnE = defaultCloseOnE.contains(base) || defaultCloseOnE.contains(lower)

        // رنگ پیش‌فرض بر اساس دسته
        val colorIndex = when (category) {
            CodeCategory.LINE -> 1 // قرمز
            CodeCategory.POINT -> 5 // آبی
            CodeCategory.IGNORE -> 8 // خاکستری
        }

        return CodeSetting(
            code = code, // کد اصلی را حفظ می‌کنیم (case اصلی فایل)
            category = category,
            colorIndex = colorIndex,
            layerName = layer,
            closeOnE = closeOnE,
            showNumber = true,
            showXY = false,
            showZ = false,
            showCode = true,
            textSize = 2.5f
        )
    }
}
