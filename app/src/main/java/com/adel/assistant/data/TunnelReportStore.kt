package com.adel.assistant.data

import android.content.Context
import kotlin.math.abs

data class ReportEntry(
    val year: String, val month: String, val day: String,
    val shaft: String, val side: String, val pointNo: String, val lengthCm: Double,
    val deviation: String = "", val collapse: String = "",
    val km: Double = 0.0, val dailyProgress: Double = 0.0,
    val shaftProgress: Double = 0.0, val remaining: Double = 0.0,
    /** مختصات درون‌یابی‌شده روی محور تونل — ذخیره می‌شود، در UI گزارش نمایش داده نمی‌شود */
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0
) {
    val key: String get() = "$shaft-${normalizeSide(side)}"
    val dateSortKey: String get() = "%s%02d%02d".format(year, month.toIntOrNullFa() ?: 0, day.toIntOrNullFa() ?: 0)
    /** برچسب تاریخ به فرم YYMMDD مثل 050624 */
    val dateLabel: String
        get() {
            val y = (year.toIntOrNullFa() ?: 0) % 100
            val m = month.toIntOrNullFa() ?: 0
            val d = day.toIntOrNullFa() ?: 0
            return "%02d%02d%02d".format(y, m, d)
        }
    val hasCoords: Boolean get() = !(x == 0.0 && y == 0.0 && z == 0.0)
}

data class ShaftEntry(val name: String, val fixedKm: Double, val type: String)

/** سمت «۰» و «start» برای شفت۱ معادل هم هستند؛ این تابع همیشه یک برچسب یکسان برمی‌گرداند */
fun normalizeSide(side: String): String = if (side == "0") "start" else side

object TunnelReportStore {
    private const val REPORT_CSV = "survey_tunnel_report"
    private const val POINTS_TXT = "tunnel_points"

    // جهت‌ها: -1 یعنی کیلومتر با پیشروی کم می‌شود، +1 یعنی زیاد می‌شود
    // نکته: جهت «۲-۱» بر اساس آزمایش میدانی کاربر برعکسِ فرمول اصلی فایل اکسل تنظیم شده — نیاز به بازبینی مجدد دارد
    private val DIRECTION = mapOf(
        "1-start" to 1, "1-2" to 1,
        "2-1" to 1, "2-3" to 1,
        "3-2" to 1, "3-4" to 1,
        "4-3" to 1, "4-end" to 1
    )

    fun direction(shaft: String, side: String): Int = DIRECTION["$shaft-${normalizeSide(side)}"] ?: 1

    /** نگاشت ثابت مسیر: هر شفت به دو سمت — مرجع مشترک برای گزارش روزانه، اکسل، و وضعیت تونل */
    val TUNNEL_LAYOUT = listOf(
        "1" to "start", "1" to "2",
        "2" to "1", "2" to "3",
        "3" to "2", "3" to "4",
        "4" to "3", "4" to "end"
    )

    fun saveEntry(context: Context, e: ReportEntry) {
        CsvStore.appendRow(context, REPORT_CSV, entryToRow(e))
    }

    private fun entryToRow(e: ReportEntry): List<String> = listOf(
        e.day, e.month, e.year, e.shaft, e.side, e.pointNo, e.lengthCm.toString(),
        e.deviation, e.collapse, e.km.toString(), e.dailyProgress.toString(),
        e.shaftProgress.toString(), e.remaining.toString(),
        e.x.toString(), e.y.toString(), e.z.toString()
    )

    fun allEntries(context: Context): List<ReportEntry> {
        return CsvStore.readAll(context, REPORT_CSV).mapNotNull { row ->
            if (row.size < 7) return@mapNotNull null
            // رد کردن هدر در صورت وجود
            if (row[0].contains("روز") || row[0].contains("تاریخ")) return@mapNotNull null
            try {
                ReportEntry(
                    year = row[2], month = row[1], day = row[0],
                    shaft = row[3], side = row[4], pointNo = row[5],
                    lengthCm = row[6].toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    deviation = row.getOrElse(7) { "" }, collapse = row.getOrElse(8) { "" },
                    km = row.getOrElse(9) { "0" }.toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    dailyProgress = row.getOrElse(10) { "0" }.toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    shaftProgress = row.getOrElse(11) { "0" }.toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    remaining = row.getOrElse(12) { "0" }.toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    x = row.getOrElse(13) { "0" }.toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    y = row.getOrElse(14) { "0" }.toEnglishDigits().toDoubleOrNull() ?: 0.0,
                    z = row.getOrElse(15) { "0" }.toEnglishDigits().toDoubleOrNull() ?: 0.0
                )
            } catch (e: Exception) { null }
        }
    }

    fun entriesForDate(context: Context, year: String, month: String, day: String): List<ReportEntry> {
        val key = "%s%02d%02d".format(year, month.toIntOrNullFa() ?: 0, day.toIntOrNullFa() ?: 0)
        return allEntries(context).filter { it.dateSortKey == key }
    }

    /** پیدا کردن رکورد یک روز برای یک شفت-سمت مشخص، با در نظر گرفتن هم‌ارزی ۰/start */
    fun entryForDateAndKey(context: Context, year: String, month: String, day: String, shaft: String, side: String): ReportEntry? {
        return entriesForDate(context, year, month, day).firstOrNull { it.key == "$shaft-${normalizeSide(side)}" }
    }

    fun replaceEntriesForDate(context: Context, year: String, month: String, day: String, newEntries: List<ReportEntry>) {
        val dateKey = "%s%02d%02d".format(year, month.toIntOrNullFa() ?: 0, day.toIntOrNullFa() ?: 0)
        // مختصات را برای هر ردیف از روی کیلومتراژ درون‌یابی کن اگر خالی بود
        val filled = newEntries.map { e ->
            if (e.hasCoords) e else {
                val xyz = interpolateAtKm(context, e.km)
                if (xyz != null) e.copy(x = xyz.first, y = xyz.second, z = xyz.third) else e
            }
        }
        val kept = allEntries(context).filter { it.dateSortKey != dateKey }
        val all = kept + filled
        CsvStore.overwriteAll(context, REPORT_CSV, all.map { entryToRow(it) })
    }

    /**
     * درون‌یابی خطی X,Y,Z روی کیلومتراژ بین دو نقطهٔ متوالی محور تونل (فاصلهٔ حدود ۱٫۲ m).
     */
    fun interpolateAtKm(context: Context, km: Double): Triple<Double, Double, Double>? {
        val points = allPoints(context).sortedBy { it.km }
        if (points.isEmpty()) return null
        if (km <= points.first().km) {
            val p = points.first(); return Triple(p.x, p.y, p.z)
        }
        if (km >= points.last().km) {
            val p = points.last(); return Triple(p.x, p.y, p.z)
        }
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            if (km >= a.km && km <= b.km) {
                val den = b.km - a.km
                val t = if (abs(den) < 1e-12) 0.0 else (km - a.km) / den
                return Triple(
                    a.x + (b.x - a.x) * t,
                    a.y + (b.y - a.y) * t,
                    a.z + (b.z - a.z) * t
                )
            }
        }
        return null
    }

    /** اگر مختصات ذخیره نشده باشد از روی km حساب می‌کند */
    fun ensureCoords(context: Context, e: ReportEntry): ReportEntry {
        if (e.hasCoords) return e
        val xyz = interpolateAtKm(context, e.km) ?: return e
        return e.copy(x = xyz.first, y = xyz.second, z = xyz.third)
    }

    /** فیلتر بازهٔ تاریخ (از–تا) بر اساس dateSortKey YYYYMMDD */
    fun entriesInRange(
        context: Context,
        fromYear: String, fromMonth: String, fromDay: String,
        toYear: String, toMonth: String, toDay: String
    ): List<ReportEntry> {
        val from = "%s%02d%02d".format(fromYear, fromMonth.toIntOrNullFa() ?: 0, fromDay.toIntOrNullFa() ?: 0)
        val to = "%s%02d%02d".format(toYear, toMonth.toIntOrNullFa() ?: 0, toDay.toIntOrNullFa() ?: 0)
        return allEntries(context)
            .filter { it.dateSortKey >= from && it.dateSortKey <= to }
            .map { ensureCoords(context, it) }
            .sortedByDescending { it.dateSortKey }
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

    fun kmOnOrBefore(context: Context, shaft: String, side: String, dateKey: String?): Double {
        val key = "$shaft-${normalizeSide(side)}"
        val latest = allEntries(context).filter { it.key == key && (dateKey == null || it.dateSortKey <= dateKey) }
            .maxByOrNull { it.dateSortKey }
        return latest?.km ?: shaftFixedKm(context, shaft) ?: 0.0
    }

    fun kmBefore(context: Context, shaft: String, side: String, dateKey: String): Double {
        val key = "$shaft-${normalizeSide(side)}"
        val latest = allEntries(context).filter { it.key == key && it.dateSortKey < dateKey }.maxByOrNull { it.dateSortKey }
        return latest?.km ?: shaftFixedKm(context, shaft) ?: 0.0
    }

    fun lastKmBefore(context: Context, shaft: String, side: String, onOrBeforeDateKey: String? = null): Double =
        kmOnOrBefore(context, shaft, side, onOrBeforeDateKey)

    fun currentKm(context: Context, shaft: String, side: String): Double = kmOnOrBefore(context, shaft, side, null)

    private fun oppositeKm(context: Context, shaft: String, side: String, asOfDateKey: String?): Double {
        val s = normalizeSide(side)
        return if (s == "start" || s == "end") {
            shaftFixedKm(context, s) ?: 0.0
        } else {
            kmOnOrBefore(context, s, shaft, asOfDateKey)
        }
    }


    fun lastPointNoFor(context: Context, shaft: String, side: String): Int? {
        val key = "$shaft-${normalizeSide(side)}"
        val latest = allEntries(context)
            .filter { it.key == key }
            .maxByOrNull { it.dateSortKey }
        return latest?.pointNo?.toIntOrNull()
    }

    /**
     * پیش‌بینی شماره نقطه بعدی بر اساس آخرین ثبت همان شفت-سمت.
     * سمت «کمتر» (به‌سمت start یا شفت با شماره کوچک‌تر): last - 1
     * سمت «بیشتر» (به‌سمت end یا شفت با شماره بزرگ‌تر): last + 1
     *
     * نمونه‌ها طبق TUNNEL_LAYOUT:
     * 1→start ، 2→1 ، 3→2 ، 4→3  → کمتر → -1
     * 1→2 ، 2→3 ، 3→4 ، 4→end   → بیشتر → +1
     */
    fun suggestedNextPointNo(context: Context, shaft: String, side: String): Int? {
        val last = lastPointNoFor(context, shaft, side) ?: return null
        return if (isTowardLessSide(shaft, side)) (last - 1).coerceAtLeast(0) else last + 1
    }

    /** آیا سمت انتخاب‌شده به‌سمت کمتر (start / شفت پایین‌تر) است؟ */
    fun isTowardLessSide(shaft: String, side: String): Boolean {
        val s = normalizeSide(side.trim())
        if (s.equals("start", true) || s == "0") return true
        if (s.equals("end", true)) return false
        val sideNum = s.toIntOrNullFa()
        val shaftNum = shaft.trim().toIntOrNullFa()
        if (sideNum != null && shaftNum != null) {
            // سمت عددی کوچک‌تر از شفت = حرکت به‌سمت کمتر
            return sideNum < shaftNum
        }
        // متن‌های فارسی/انگلیسی
        val lower = s.lowercase()
        return lower.contains("کم") || lower.contains("less") || lower == "l" || lower.contains("left")
    }

    fun computeEntryValues(context: Context, shaft: String, side: String, pointNo: String, lengthCm: Double, dateKeyToday: String): ReportEntryValues? {
        val point = findByPointNo(context, pointNo) ?: return null
        val km = point.km + direction(shaft, side) * (lengthCm / 100.0)
        val prevKm = kmBefore(context, shaft, side, dateKeyToday)
        val dailyProgress = abs(km - prevKm)
        val fixedKm = shaftFixedKm(context, shaft) ?: km
        val shaftProgress = abs(km - fixedKm)
        val remaining = abs(km - oppositeKm(context, shaft, side, dateKeyToday))
        return ReportEntryValues(km, dailyProgress, shaftProgress, remaining)
    }

    data class ReportEntryValues(val km: Double, val dailyProgress: Double, val shaftProgress: Double, val remaining: Double)

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
        val z = before.z + (after.z - before.z) * ratio
        val nearest = if (abs(km - before.km) <= abs(after.km - km)) before else after
        return TunnelPoint(nearest.pointNo, x, y, z, km, nearest.elevDiff, nearest.slope, nearest.type)
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
