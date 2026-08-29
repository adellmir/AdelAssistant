package com.adel.assistant.data

import android.content.Context
import kotlin.math.abs

/** یک ورودی خام گزارش روزانه: تاریخ + شفت + سمت + شماره‌نقطه + طول */
data class ReportEntry(
    val year: String,
    val month: String,
    val day: String,
    val shaft: String,
    val side: String,
    val pointNo: String,
    val length: Double
) {
    val key: String get() = "$shaft-$side"
    val dateSortKey: String get() = "%s%02d%02d".format(year, month.toIntOrNull() ?: 0, day.toIntOrNull() ?: 0)
}

/** یک ردیف در جدول شفت‌ها/دهانه‌ها */
data class ShaftEntry(val name: String, val fixedKm: Double, val type: String)

object TunnelReportStore {
    private const val REPORT_CSV = "survey_tunnel_report"
    private const val SHAFTS_CSV = "tunnel_shafts"
    private const val POINTS_TXT = "tunnel_points"

    fun saveEntry(context: Context, e: ReportEntry) {
        CsvStore.appendRow(
            context, REPORT_CSV,
            listOf(e.year, e.month, e.day, e.shaft, e.side, e.pointNo, e.length.toString())
        )
    }

    fun allEntries(context: Context): List<ReportEntry> {
        return CsvStore.readAll(context, REPORT_CSV).mapNotNull { row ->
            if (row.size < 7) return@mapNotNull null
            try {
                ReportEntry(row[0], row[1], row[2], row[3], row[4], row[5], row[6].toDouble())
            } catch (e: Exception) { null }
        }
    }

    /** آخرین کیلومتراژ ثبت‌شده برای یک شفت-سمت، در یا قبل از یک تاریخ مشخص (خالی یعنی بدون محدودیت) */
    fun lastKmBefore(context: Context, shaft: String, side: String, onOrBeforeDateKey: String? = null): Double? {
        val key = "$shaft-$side"
        val entries = allEntries(context)
            .filter { it.key == key }
            .filter { onOrBeforeDateKey == null || it.dateSortKey <= onOrBeforeDateKey }
            .sortedByDescending { it.dateSortKey }
        return entries.firstOrNull()?.let { calcKmFromEntry(context, it) }
    }

    /** کیلومتراژ محاسبه‌شده از یک ورودی (کیلومتر قبلی + طول، به‌همان جهت) */
    private fun calcKmFromEntry(context: Context, e: ReportEntry): Double {
        // کیلومتر جدید = کیلومتر قبلیِ همان شفت-سمت (پیش از این ثبت) + طول
        val prev = allEntries(context)
            .filter { it.key == e.key && it.dateSortKey < e.dateSortKey }
            .sortedByDescending { it.dateSortKey }
            .firstOrNull()
        val prevKm = prev?.let { calcKmFromEntry(context, it) } ?: shaftFixedKm(context, e.shaft, e.side) ?: 0.0
        return prevKm + e.length
    }

    fun shaftFixedKm(context: Context, shaft: String, side: String): Double? {
        return allShafts(context).firstOrNull { it.name == shaft }?.fixedKm
    }

    fun allShafts(context: Context): List<ShaftEntry> {
        return CsvStore.readAll(context, SHAFTS_CSV).mapNotNull { row ->
            if (row.size < 3) return@mapNotNull null
            try { ShaftEntry(row[0], row[1].toDouble(), row[2]) } catch (e: Exception) { null }
        }
    }

    fun saveShaft(context: Context, s: ShaftEntry) {
        CsvStore.appendRow(context, SHAFTS_CSV, listOf(s.name, s.fixedKm.toString(), s.type))
    }

    /** لیست همه‌ی شفت-سمت‌های شناخته‌شده (از جدول شفت‌ها) */
    fun allShaftSideKeys(context: Context): List<Pair<String, String>> {
        return allShafts(context).flatMap { s -> listOf(s.name to "0", s.name to "1") }
    }

    // ---- نقاط تونل ----
    data class TunnelPoint(
        val pointNo: String, val x: Double, val y: Double, val z: Double,
        val km: Double, val elevDiff: String, val slope: String, val type: String
    )

    fun allPoints(context: Context): List<TunnelPoint> {
        return CsvStore.readAll(context, POINTS_TXT).drop(0).mapNotNull { row ->
            if (row.size < 8) return@mapNotNull null
            try {
                TunnelPoint(row[0], row[1].toDouble(), row[2].toDouble(), row[3].toDouble(),
                    row[4].toDouble(), row[5], row[6], row[7])
            } catch (e: Exception) { null }
        }
    }

    fun savePoint(context: Context, p: TunnelPoint) {
        CsvStore.appendRow(context, POINTS_TXT, listOf(
            p.pointNo, p.x.toString(), p.y.toString(), p.z.toString(),
            p.km.toString(), p.elevDiff, p.slope, p.type
        ))
    }

    /** درون‌یابی خطی X,Y از روی کیلومتراژ؛ نزدیک‌ترین نقطه برای بقیه‌ی فیلدها */
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
        // نزدیک‌ترین برای فیلدهای غیرپیوسته
        val nearest = if (abs(km - before.km) <= abs(after.km - km)) before else after
        return TunnelPoint(nearest.pointNo, x, y, nearest.z, km, nearest.elevDiff, nearest.slope, nearest.type)
    }

    fun nearestPointsByKeyword(context: Context, keyword: String): List<TunnelPoint> {
        return allPoints(context).filter { it.type.contains(keyword) || it.pointNo.contains(keyword) }
    }
}
