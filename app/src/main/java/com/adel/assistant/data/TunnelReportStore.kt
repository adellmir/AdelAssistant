package com.adel.assistant.data

import android.content.Context
import kotlin.math.abs

/**
 * lengthCm: طول واردشده به سانتی‌متر (همان عددی که کاربر تایپ می‌کند)
 * km: کیلومتراژ نهاییِ محاسبه‌شده = کیلومتراژ نقطه‌ی مرجع (از tunnel_points) + جهت×(lengthCm/100)
 * dailyProgress: قدرمطلق تفاضل این km با آخرین km ثبت‌شده‌ی قبلیِ همان شفت-سمت
 */
data class ReportEntry(
    val year: String, val month: String, val day: String,
    val shaft: String, val side: String, val pointNo: String, val lengthCm: Double,
    val deviation: String = "", val collapse: String = "",
    val km: Double = 0.0, val dailyProgress: Double = 0.0
) {
    val key: String get() = "$shaft-$side"
    val dateSortKey: String get() = "%s%02d%02d".format(year, month.toIntOrNullFa() ?: 0, day.toIntOrNullFa() ?: 0)
}

data class ShaftEntry(val name: String, val fixedKm: Double, val type: String)

object TunnelReportStore {
    private const val REPORT_CSV = "survey_tunnel_report"
    private const val POINTS_TXT = "tunnel_points"

    private val DIRECTION = mapOf(
        "1-start" to -1, "1-2" to 1,
        "2-1" to -1, "2-3" to 1,
        "3-2" to -1, "3-4" to 1,
        "4-3" to 1, "4-end" to 1
    )

    fun direction(shaft: String, side: String): Int = DIRECTION["$shaft-$side"] ?: 1

    // ترتیب فایل: روز,ماه,سال,شفت,سمت,شماره_نقطه,طول_سانتیمتر,انحراف,ریزش,کیلومتراژ,پیشرفت
    fun saveEntry(context: Context, e: ReportEntry) {
        CsvStore.appendRow(context, REPORT_CSV, listOf(
            e.day, e.month, e.year, e.shaft, e.side, e.pointNo, e.lengthCm.toString(),
            e.deviation, e.collapse, e.km.toString(), e.dailyProgress.toString()
        ))
    }

    fun allEntries(context: Context): List<ReportEntry> {
        return CsvStore.readAll(context, REPORT_CSV).mapNotNull { row ->
            if (row.size < 7) return@mapNotNull null
            try {
                ReportEntry(
                    year = row[2], month = row[1], day = row[0],
                    shaft = row[3], side = row[4], pointNo = row[5],
                    lengthCm = row[6].toEnglishDigits().toDouble(),
                    deviation = row.getOrElse(7) { "" }, collapse = row.getOrElse(8) { "" },
                    km = row.getOrElse(9) { "0" }.toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    dailyProgress = row.getOrElse(10) { "0" }.toEnglishDigits().toDoubleOrNull() ?: 0.0
                )
            } catch (e: Exception) { null }
        }
    }

    fun entriesForDate(context: Context, year: String, month: String, day: String): List<ReportEntry> {
        val key = "%s%02d%02d".format(year, month.toIntOrNullFa() ?: 0, day.toIntOrNullFa() ?: 0)
        return allEntries(context).filter { it.dateSortKey == key }
    }

    fun replaceEntriesForDate(context: Context, year: String, month: String, day: String, newEntries: List<ReportEntry>) {
        val dateKey = "%s%02d%02d".format(year, month.toIntOrNullFa() ?: 0, day.toIntOrNullFa() ?: 0)
        val kept = allEntries(context).filter { it.dateSortKey != dateKey }
        val all = kept + newEntries
        val rows = all.map { listOf(it.day, it.month, it.year, it.shaft, it.side, it.pointNo,
            it.lengthCm.toString(), it.deviation, it.collapse, it.km.toString(), it.dailyProgress.toString()) }
        CsvStore.overwriteAll(context, REPORT_CSV, rows)
    }

    fun shaftFixedKm(context: Context, shaft: String): Double? = allShafts(context).firstOrNull { it.name == shaft }?.fixedKm

    fun allShafts(context: Context): List<ShaftEntry> {
        return allPoints(context).mapNotNull { p ->
            val t = p.type.trim().lowercase()
            when {
                t.startsWith("sh") && t.drop(2).toIntOrNullFa() != null -> ShaftEntry(t.drop(2), p.km, "شفت")
                t == "start" || t == "end" -> ShaftEntry(t, p.km, "دهانه")
                else -> null
            }
        }.distinctBy { it.name }
    }

    /** آخرین کیلومتراژ ثبت‌شده (ستون km) برای یک شفت-سمت، در/قبل از یک تاریخ؛ در نبود سابقه، کیلومتر ثابت شفت */
    fun kmOnOrBefore(context: Context, shaft: String, side: String, dateKey: String?): Double {
        val key = "$shaft-$side"
        val latest = allEntries(context)
            .filter { it.key == key && (dateKey == null || it.dateSortKey <= dateKey) }
            .maxByOrNull { it.dateSortKey }
        return latest?.km ?: shaftFixedKm(context, shaft) ?: 0.0
    }

    fun kmBefore(context: Context, shaft: String, side: String, dateKey: String): Double {
        val key = "$shaft-$side"
        val latest = allEntries(context)
            .filter { it.key == key && it.dateSortKey < dateKey }
            .maxByOrNull { it.dateSortKey }
        return latest?.km ?: shaftFixedKm(context, shaft) ?: 0.0
    }

    fun lastKmBefore(context: Context, shaft: String, side: String, onOrBeforeDateKey: String? = null): Double =
        kmOnOrBefore(context, shaft, side, onOrBeforeDateKey)

    fun currentKm(context: Context, shaft: String, side: String): Double = kmOnOrBefore(context, shaft, side, null)

    /** محاسبه‌ی کیلومتراژ و پیشرفت روزانه بر اساس نقطه‌ی مرجع (tunnel_points) + طول واردشده (سانتی‌متر) */
    fun computeKmAndProgress(context: Context, shaft: String, side: String, pointNo: String, lengthCm: Double, dateKeyForPrevLookup: String): Pair<Double, Double>? {
        val point = findByPointNo(context, pointNo) ?: return null
        val km = point.km + direction(shaft, side) * (lengthCm / 100.0)
        val prevKm = kmBefore(context, shaft, side, dateKeyForPrevLookup)
        val progress = abs(km - prevKm)
        return km to progress
    }

    // ---- نقاط تونل ----
    data class TunnelPoint(
        val pointNo: String, val x: Double, val y: Double, val z: Double,
        val km: Double, val elevDiff: String, val slope: String, val type: String
    )

    fun allPoints(context: Context): List<TunnelPoint> {
        return CsvStore.readAll(context, POINTS_TXT).mapNotNull { row ->
            if (row.size < 8) return@mapNotNull null
            try {
                TunnelPoint(row[0], row[1].toEnglishDigits().toDouble(), row[2].toEnglishDigits().toDouble(),
                    row[3].toEnglishDigits().toDouble(), row[4].toEnglishDigits().toDouble(), row[5], row[6], row[7])
            } catch (e: Exception) { null }
        }
    }

    fun savePoint(context: Context, p: TunnelPoint) {
        CsvStore.appendRow(context, POINTS_TXT, listOf(p.pointNo, p.x.toString(), p.y.toString(),
            p.z.toString(), p.km.toString(), p.elevDiff, p.slope, p.type))
    }

    fun replacePoint(context: Context, oldPointNo: String, updated: TunnelPoint) {
        val kept = allPoints(context).filter { it.pointNo != oldPointNo }
        val all = kept + updated
        val rows = all.map { listOf(it.pointNo, it.x.toString(), it.y.toString(), it.z.toString(),
            it.km.toString(), it.elevDiff, it.slope, it.type) }
        CsvStore.overwriteAll(context, POINTS_TXT, rows)
    }

    fun findByKm(context: Context, km: Double): TunnelPoint? {
        val points = allPoints(context).sortedBy { it.km }
        if (points.isEmpty()) return null
        val exact = points.firstOrNull { abs(it.km - km) < 0.0001 }
        if (exact != null) return exact
        val before = points.lastOrNull { it.km < km }
        val after = points.firstOrNull { it.km > km }
        if (before == null) return after
        if (after == null) return before
        val ratio = (km - before.km) / (after.km - before.km)
        val x = before.x + (after.x - before.x) * ratio
        val y = before.y + (after.y - before.y) * ratio
        val nearest = if (abs(km - before.km) <= abs(after.km - km)) before else after
        return TunnelPoint(nearest.pointNo, x, y, nearest.z, km, nearest.elevDiff, nearest.slope, nearest.type)
    }

    fun findByPointNo(context: Context, pointNo: String): TunnelPoint? =
        allPoints(context).firstOrNull { it.pointNo == pointNo }

    fun searchByKeyword(context: Context, keyword: String): List<TunnelPoint> =
        allPoints(context).filter { it.type.contains(keyword) || it.pointNo.contains(keyword) }

    fun nextSubPointNo(context: Context, baseNo: String): String {
        val existing = allPoints(context).map { it.pointNo }
        var n = 1
        while (existing.contains("$baseNo.$n")) n++
        return "$baseNo.$n"
    }
}
