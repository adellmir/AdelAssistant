package com.adel.assistant.data

/**
 * تبدیل ارتفاع بیضوی (GPS/WGS84) ↔ ارتومتریک با جداسازی ژئوئید N.
 *
 * Z_orthometric ≈ Z_ellipsoid − N
 * N: undulation (متر). برای دقت بالا باید از مدل EGM/ژئوئید محلی بیاید؛
 * اینجا N توسط کاربر یا مقدار تقریبی منطقه‌ای وارد می‌شود.
 */
object GeoidHeight {
    /** میانگین تقریبی N برای بخش‌هایی از ایران (فقط تخمین — جایگزین مدل دقیق نیست) */
    const val IRAN_DEFAULT_N = 25.0

    fun ellipsoidToOrtho(zEllipsoid: Double, n: Double): Double = zEllipsoid - n
    fun orthoToEllipsoid(zOrtho: Double, n: Double): Double = zOrtho + n

    fun convertPoints(
        points: List<SurveyPoint>,
        n: Double,
        toOrtho: Boolean
    ): List<SurveyPoint> = points.map { p ->
        val z = if (toOrtho) ellipsoidToOrtho(p.z, n) else orthoToEllipsoid(p.z, n)
        p.copy(z = z)
    }

    fun convertVolPoints(
        points: List<VolPoint>,
        n: Double,
        toOrtho: Boolean
    ): List<VolPoint> = points.map { p ->
        val z = if (toOrtho) ellipsoidToOrtho(p.z, n) else orthoToEllipsoid(p.z, n)
        p.copy(z = z)
    }
}
