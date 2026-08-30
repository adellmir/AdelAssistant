package com.adel.assistant.data

import android.content.Context
import kotlin.math.abs

data class ReportEntry(
    val year: String, val month: String, val day: String,
    val shaft: String, val side: String, val pointNo: String, val length: Double
) {
    val key: String get() = "$shaft-$side"
    val dateSortKey: String get() = "%s%02d%02d".format(year, month.toIntOrNull() ?: 0, day.toIntOrNull() ?: 0)
}

data class ShaftEntry(val name: String, val fixedKm: Double, val type: String)

object TunnelReportStore {
    private const val REPORT_CSV = "survey_tunnel_report"
    private const val SHAFTS_CSV = "tunnel_shafts"
    private const val POINTS_TXT = "tunnel_points"

    fun saveEntry(context: Context, e: ReportEntry) {
        CsvStore.appendRow(context, REPORT_CSV, listOf(e.year, e.month, e.day, e.shaft, e.side, e.pointNo, e.length.toString()))
    }

    fun allEntries(context: Context): List<ReportEntry> {
        return CsvStore.readAll(context, REPORT_CSV).mapNotNull { row ->
            if (row.size < 7) return@mapNotNull null
            try { ReportEntry(row[0], row[1], row[2], row[3], row[4], row[5], row[6].toDouble()) }
            catch (e: Exception) { null }
        }
    }

    /** رکوردهای ثبت‌شده برای یک تاریخ مشخص */
    fun entriesForDate(context: Context, year: String, month: String, day: String): List<ReportEntry> {
        val key = "%s%02d%02d".format(year, month.toIntOrNull() ?: 0, day.toIntOrNull() ?: 0)
        return allEntries(context).filter { it.dateSortKey == key }
    }

    /** جایگزینی رکوردهای یک روز خاص با مجموعه‌ی جدید (برای ویرایش روزهای گذشته) */
    fun replaceEntriesForDate(context: Context, year: String, month: String, day: String, newEntries: List<ReportEntry>) {
        val dateKey = "%s%02d%02d".format(year, month.toIntOrNull() ?: 0, day.toIntOrNull() ?: 0)
        val kept = allEntries(context).filter { it.dateSortKey != dateKey }
        val all = kept + newEntries
        val rows = all.map { listOf(it.year, it.month, it.day, it.shaft, it.side, it.pointNo, it.length.toString()) }
        CsvStore.overwriteAll(context, REPORT_CSV, rows)
    }

    fun lastKmBefore(context: Context, shaft: String, side: String, onOrBeforeDateKey: String? = null): Double? {
        val key = "$shaft-$side"
        val entries = allEntries(context).filter { it.key == key }
            .filter { onOrBeforeDateKey == null || it.dateSortKey <= onOrBeforeDateKey }
            .sortedByDescending { it.dateSortKey }
        return entries.firstOrNull()?.let { calcKmFromEntry(context, it) }
    }

    private fun calcKmFromEntry(context: Context, e: ReportEntry): Double {
        val prev = allEntries(context).filter { it.key == e.key && it.dateSortKey < e.dateSortKey }
            .sortedByDescending { it.dateSortKey }.firstOrNull()
        val prevKm = prev?.let { calcKmFromEntry(context, it) } ?: shaftFixedKm(context, e.shaft) ?: 0.0
        return prevKm + e.length
    }

    fun shaftFixedKm(context: Context, shaft: String): Double? {
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

    /** آخرین کیلومتر فعلیِ هر شفت-سمت (بدون محدودیت تاریخ) */
    fun currentKm(context: Context, shaft: String, side: String): Double {
        return lastKmBefore(context, shaft, side) ?: shaftFixedKm(context, shaft) ?: 0.0
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
                TunnelPoint(row[0], row[1].toDouble(), row[2].toDouble(), row[3].toDouble(),
                    row[4].toDouble(), row[5], row[6], row[7])
            } catch (e: Exception) { null }
        }
    }

    fun savePoint(context: Context, p: TunnelPoint) {
        CsvStore.appendRow(context, POINTS_TXT, listOf(p.pointNo, p.x.toString(), p.y.toString(),
            p.z.toString(), p.km.toString(), p.elevDiff, p.slope, p.type))
    }

    /** جایگزینی کامل نقطه‌ای موجود (برای ویرایش) */
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

    /** پیدا کردن نزدیک‌ترین X,Y (برای جستجوی معکوس مختصات→کیلومتراژ) */
    fun nearestByXY(context: Context, x: Double, y: Double): TunnelPoint? {
        return allPoints(context).minByOrNull {
            val dx = it.x - x; val dy = it.y - y
            dx * dx + dy * dy
        }
    }

    /** شماره‌ی نقطه‌ی جدید نزدیک یک نقطه‌ی موجود (۳۱۲ -> ۳۱۲.۱ -> ۳۱۲.۲ ...) */
    fun nextSubPointNo(context: Context, baseNo: String): String {
        val existing = allPoints(context).map { it.pointNo }
        var n = 1
        while (existing.contains("$baseNo.$n")) n++
        return "$baseNo.$n"
    }
}
