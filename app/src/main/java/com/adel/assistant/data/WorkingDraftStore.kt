package com.adel.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * حافظهٔ موقت کارهای باز (گزارش روزانه و …)
 * تا ثبت/ذخیره یا صرف‌نظر — حتی با خروج موقت از اپ و چرخش صفحه.
 */
object WorkingDraftStore {
    private fun file(context: Context, name: String) =
        File(File(context.filesDir, "drafts").also { it.mkdirs() }, name)

    // —— گزارش روزانه ——
    data class DailyRow(
        val shaft: String, val side: String, val pointNo: String, val lengthCm: Double,
        val km: Double, val dailyProgress: Double, val shaftProgress: Double, val remaining: Double,
        val deviation: String = "", val collapse: String = ""
    )

    data class DailyDraft(
        val year: String, val month: String, val day: String,
        val rows: List<DailyRow>
    )

    fun saveDaily(context: Context, draft: DailyDraft) {
        val o = JSONObject()
        o.put("year", draft.year)
        o.put("month", draft.month)
        o.put("day", draft.day)
        val arr = JSONArray()
        draft.rows.forEach { r ->
            arr.put(
                JSONObject()
                    .put("shaft", r.shaft).put("side", r.side).put("pointNo", r.pointNo)
                    .put("lengthCm", r.lengthCm).put("km", r.km)
                    .put("dailyProgress", r.dailyProgress).put("shaftProgress", r.shaftProgress)
                    .put("remaining", r.remaining)
                    .put("deviation", r.deviation).put("collapse", r.collapse)
            )
        }
        o.put("rows", arr)
        file(context, "daily_report_draft.json").writeText(o.toString())
    }

    fun loadDaily(context: Context): DailyDraft? {
        val f = file(context, "daily_report_draft.json")
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val arr = o.optJSONArray("rows") ?: JSONArray()
            val rows = (0 until arr.length()).mapNotNull { i ->
                val r = arr.optJSONObject(i) ?: return@mapNotNull null
                DailyRow(
                    r.optString("shaft"), r.optString("side"), r.optString("pointNo"),
                    r.optDouble("lengthCm"), r.optDouble("km"),
                    r.optDouble("dailyProgress"), r.optDouble("shaftProgress"), r.optDouble("remaining"),
                    r.optString("deviation"), r.optString("collapse")
                )
            }
            DailyDraft(o.optString("year"), o.optString("month"), o.optString("day"), rows)
        } catch (_: Exception) {
            null
        }
    }

    fun clearDaily(context: Context) {
        file(context, "daily_report_draft.json").delete()
    }
}
