package com.adel.assistant.ai.tools

import android.content.Context
import com.adel.assistant.data.TunnelReportStore
import java.util.Locale
import kotlin.math.abs

/**
 * ابزار خواندن داده‌های واقعی نقاط تونل برای Agent.
 * AI فقط درخواست را انتخاب می‌کند؛ داده از TunnelReportStore خوانده می‌شود.
 */
object SurveyTools {
    const val FIND_BY_CHAINAGE = "find_point_by_chainage"
    const val FIND_BY_NAME = "find_point_by_name"
    const val FIND_BY_RANGE = "find_points_by_range"
    const val SEARCH = "search_survey_data"

    data class Result(val success: Boolean, val message: String)

    fun findByChainage(context: Context, chainage: Double): Result {
        val points = TunnelReportStore.allPoints(context).sortedBy { it.km }
        if (points.isEmpty()) return Result(false, "هیچ نقطه‌ای در اطلاعات تونل ثبت نشده است.")

        val exact = points.firstOrNull { abs(it.km - chainage) < 0.0001 }
        if (exact != null) {
            return Result(true, formatPoint("نقطه دقیق در کیلومتراژ %.3f", exact))
        }

        val nearest = points.minByOrNull { abs(it.km - chainage) }!!
        val interpolated = TunnelReportStore.findByKm(context, chainage)
        val delta = abs(nearest.km - chainage)
        val text = buildString {
            appendLine("برای کیلومتراژ ${fmt(chainage)} نقطه ثبت‌شده دقیق پیدا نشد.")
            appendLine("نزدیک‌ترین نقطه: ${nearest.pointNo} — کیلومتر ${fmt(nearest.km)} — فاصله ${fmt(delta)} کیلومتر")
            appendLine("مختصات نقطه نزدیک: X=${fmt(nearest.x)}  Y=${fmt(nearest.y)}  Z=${fmt(nearest.z)}")
            interpolated?.let {
                appendLine("مختصات درون‌یابی‌شده در کیلومتر ${fmt(chainage)}: X=${fmt(it.x)}  Y=${fmt(it.y)}  Z=${fmt(it.z)}")
            }
            append("نوع: ${nearest.type.ifBlank { "بدون توضیح" }}")
        }
        return Result(true, text)
    }

    fun findByName(context: Context, pointNo: String): Result {
        val q = pointNo.trim()
        if (q.isBlank()) return Result(false, "شماره نقطه را مشخص کن.")
        val exact = TunnelReportStore.findByPointNo(context, q)
        if (exact != null) return Result(true, formatPoint("اطلاعات نقطه ${exact.pointNo}", exact))
        val matches = TunnelReportStore.allPoints(context)
            .filter { it.pointNo.contains(q, ignoreCase = true) }
            .take(10)
        if (matches.isEmpty()) return Result(false, "نقطه‌ای با شماره «$q» پیدا نشد.")
        return Result(true, buildString {
            appendLine("چند نقطه مطابق «$q» پیدا شد:")
            matches.forEachIndexed { i, p -> appendLine("${i + 1}. ${shortPoint(p)}") }
        }.trimEnd())
    }

    fun findByRange(context: Context, from: Double, to: Double): Result {
        val low = minOf(from, to)
        val high = maxOf(from, to)
        val points = TunnelReportStore.allPoints(context)
            .filter { it.km in low..high }
            .sortedBy { it.km }
        if (points.isEmpty()) return Result(false, "در بازه ${fmt(low)} تا ${fmt(high)} نقطه‌ای پیدا نشد.")
        return Result(true, buildString {
            appendLine("نقاط کیلومتراژ ${fmt(low)} تا ${fmt(high)} — ${points.size} نقطه")
            points.take(30).forEachIndexed { i, p -> appendLine("${i + 1}. ${shortPoint(p)}") }
            if (points.size > 30) appendLine("… ${points.size - 30} نقطه دیگر")
        }.trimEnd())
    }

    fun search(context: Context, keyword: String): Result {
        val q = keyword.trim()
        if (q.isBlank()) return Result(false, "عبارت جستجو را مشخص کن.")
        val points = TunnelReportStore.searchByKeyword(context, q).take(20)
        if (points.isEmpty()) return Result(false, "برای «$q» نتیجه‌ای پیدا نشد.")
        return Result(true, buildString {
            appendLine("نتایج جستجوی «$q» — ${points.size} مورد")
            points.forEachIndexed { i, p -> appendLine("${i + 1}. ${shortPoint(p)}") }
        }.trimEnd())
    }

    private fun formatPoint(prefix: String, p: TunnelReportStore.TunnelPoint): String = buildString {
        appendLine(String.format(Locale.US, prefix, p.km))
        appendLine("شماره: ${p.pointNo}")
        appendLine("مختصات: X=${fmt(p.x)}  Y=${fmt(p.y)}  Z=${fmt(p.z)}")
        append("نوع: ${p.type.ifBlank { "بدون توضیح" }}")
    }

    private fun shortPoint(p: TunnelReportStore.TunnelPoint): String =
        "${p.pointNo} — کیلومتر ${fmt(p.km)} — X=${fmt(p.x)} Y=${fmt(p.y)} Z=${fmt(p.z)} — ${p.type.ifBlank { "بدون توضیح" }}"

    private fun fmt(v: Double): String = String.format(Locale.US, "%.3f", v)
}
