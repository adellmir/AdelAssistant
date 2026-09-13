package com.adel.assistant.data

/**
 * پیش‌بینی شماره نقطه گزارش روزانه بر اساس آخرین ثبت و جهت شفت.
 *
 * قاعده:
 *  - به سمت بیشتر → last + 1
 *  - به سمت کمتر  → last - 1
 *
 * در DailyReportScreen هر جا که برای جهت «کمتر» هنوز +1 می‌زند،
 * باید از این تابع استفاده شود (یا همان منطق جایگزین شود).
 */
object ShaftPointSuggest {

    fun next(lastPoint: Int, towardMore: Boolean): Int {
        return if (towardMore) lastPoint + 1 else lastPoint - 1
    }

    /**
     * تشخیص جهت از متن UI (بیشتر / کمتر)
     */
    fun nextFromLabel(lastPoint: Int, directionLabel: String): Int {
        val d = directionLabel.trim()
        val more = d.contains("بیش") || d.contains("more", ignoreCase = true) ||
            d == "+" || d == "1" || d == "بیشتر"
        val less = d.contains("کم") || d.contains("less", ignoreCase = true) ||
            d == "-" || d == "0" || d == "کمتر"
        return when {
            more && !less -> lastPoint + 1
            less -> lastPoint - 1
            else -> lastPoint + 1 // پیش‌فرض: بیشتر
        }
    }
}
