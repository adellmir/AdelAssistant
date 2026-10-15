package com.adel.assistant.data

import android.content.Context

/**
 * فضای یکپارچهٔ کارهای نقشه‌ای.
 *
 * پیاده‌سازی فعلی روی [Map2Session] سوار است تا رفتار نقشه به‌هم نریزد.
 * صفحه‌های نقشه / نقشه تونل / پروفیل باید از اینجا به نشست وصل شوند
 * نه اینکه منطق جدا بسازند.
 */
object MapOps {

    /** آیا پروژهٔ ترسیمی باز و تغییرکرده است؟ */
    fun isDirty(): Boolean = Map2Session.dirty && Map2Session.openProject

    fun isOpen(): Boolean = Map2Session.openProject

    fun markDirty() = Map2Session.markDirty()

    fun save(context: Context) = Map2Session.save(context)

    fun discard(context: Context) = Map2Session.discardProject(context)

    fun load(context: Context) = Map2Session.load(context)

    /** ورود نقاط به نشست مشترک — بدون پاک کردن ترسیم‌های قبلی */
    fun importSurveyPoints(
        points: List<SurveyPoint>,
        categoryName: String? = null
    ): Map2PointCategory {
        val mapped = points.map { it.toMap2Point() }
        val cat = Map2Session.addCategory(
            name = categoryName ?: Map2Session.nextPgName(),
            points = mapped
        )
        Map2Session.seedToolsFromPoints()
        Map2Session.markDirty()
        return cat
    }

    fun importGsiPoints(points: List<GsiPoint>, categoryName: String? = null): Map2PointCategory =
        importSurveyPoints(points.toSurveyPoints(), categoryName)

    fun allSurveyPoints(): List<SurveyPoint> = Map2Session.toSurveyPoints()

    fun projectCenter(): Pair<Double, Double>? = Map2Session.projectBoundsCenter()

    fun upsertDxfPart(name: String, dxfText: String) =
        Map2Session.upsertProjectPart(name, dxfText)

    fun setLineColor(code: String, aci: Int) = Map2Session.setLineColor(code, aci)

    fun lineColor(code: String, fallback: Int = 7): Int {
        val key = codeBase(code).ifBlank { code }.lowercase()
        return Map2Session.lineColors[key] ?: fallback
    }

    /**
     * سیاست خروج — مطابق خواستهٔ کاربر:
     * اگر ترسیمی/تغییری نبود → بدون پرسش
     * اگر dirty → سه گزینه (در UI)
     */
    fun needsExitPrompt(): Boolean = isDirty()

    enum class ExitAction { KEEP, DISCARD, SAVE }
}
