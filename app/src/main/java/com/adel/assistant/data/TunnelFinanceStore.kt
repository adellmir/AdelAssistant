package com.adel.assistant.data

import android.content.Context
import kotlin.math.round

/**
 * یک جدول واحد مطابق Tunel-financial.csv
 * هر سطر = یک ماه کاری
 */
data class TunnelMonthRow(
    val dateCode: Int,          // A
    val days: Double,           // B
    val unitPrice: Double,      // C
    val lunchCount: Double = 0.0, // F
    val lunchDeduction: Double = 0.0, // G
    val overtimeAdd: Double = 0.0,    // H
    val timesheetDeduction: Double = 0.0, // I
    val cameraDeduction: Double = 0.0,    // J
    val cameraTimeDeduction: Double = 0.0, // K
    val dayOp: Double = 0.0,    // N روز ع
    val dayLeave: Double = 0.0, // O روز م
    val surveyorPay: Double = 0.0, // P
    val receiveDate: String = "",  // R YYYYMMDD
    val receiveAmount: Double? = null, // S null = خالی
    val note: String = ""       // T
) {
    val year: Int
    val month: Int
    init {
        val (y, m) = parseDateCode(dateCode)
        year = y
        month = m
    }

    // محاسبات
    val totalAmount: Double get() = days * unitPrice                          // D
    val retention: Double get() = round(totalAmount * 0.10)                  // E
    val totalDeductions: Double get() =                                      // L
        retention + lunchDeduction + timesheetDeduction + cameraDeduction + cameraTimeDeduction
    val payable: Double get() = totalAmount - totalDeductions + overtimeAdd  // M
    val income: Double get() = payable + retention - surveyorPay             // Q

    fun withRecalculated(): TunnelMonthRow = this // computed on the fly

    companion object {
        fun parseDateCode(code: Int): Pair<Int, Int> {
            return if (code < 10000) {
                // 4 digit: 9802 -> 1398, 02
                val y = 1300 + code / 100
                val m = code % 100
                y to m
            } else {
                // 5 digit: 40504 -> 1405, 04
                val y = 1000 + code / 100
                val m = code % 100
                y to m
            }
        }

        fun makeDateCode(year: Int, month: Int): Int {
            return if (year < 1400) {
                (year - 1300) * 100 + month
            } else {
                (year - 1000) * 100 + month
            }
        }
    }
}

data class TunnelFinanceSummary(
    val sumPayable: Double,           // جمع صورت وضعیت‌ها M
    val sumReceived: Double,          // جمع همه دریافتی‌ها S
    val blockedRetention: Double,     // حسن‌انجام بلوکه‌شده از آخرین اردیبهشت
    val remaining: Double             // مانده = صورت‌وضعیت − بلوکه − دریافتی
)

object TunnelFinanceStore {
    private const val CSV = "tunnel_financial"

    val CSV_HEADER = listOf(
        "تاریخ", "روز", "مبلغ واحد", "مبلغ کل", "حسن انجام",
        "نهاری", "کسر نهاری", "اضافه تایم شیت", "کسر تایم شیت",
        "کسر دوربین", "کسر تایم دوربین", "کل کسورات", "مبلغ صورت پرداختی",
        "روز ع", "روز م", "نقشه بردار", "درامد",
        "تاریخ دریافت", "مبلغ دریافت", "توضیحات"
    )

    fun all(context: Context): List<TunnelMonthRow> {
        val lines = CsvStore.readAll(context, CSV)
        if (lines.isEmpty()) return emptyList()
        val start = if (lines.firstOrNull()?.firstOrNull()?.contains("تاریخ") == true) 1 else 0
        return lines.drop(start).mapNotNull { parseRow(it) }
            .sortedBy { it.dateCode }
    }

    private fun parseRow(r: List<String>): TunnelMonthRow? {
        if (r.isEmpty()) return null
        val code = r.getOrNull(0)?.toEnglishDigits()?.trim()?.toDoubleOrNull()?.toInt() ?: return null
        fun d(i: Int): Double = r.getOrNull(i)?.toEnglishDigits()?.trim()?.toDoubleOrNull() ?: 0.0
        fun s(i: Int): String = r.getOrNull(i)?.trim().orEmpty()
        val recvRaw = s(18).toEnglishDigits().trim()
        val recvAmt = if (recvRaw.isBlank()) null else recvRaw.toDoubleOrNull()
        return TunnelMonthRow(
            dateCode = code,
            days = d(1),
            unitPrice = d(2),
            lunchCount = d(5),
            lunchDeduction = d(6),
            overtimeAdd = d(7),
            timesheetDeduction = d(8),
            cameraDeduction = d(9),
            cameraTimeDeduction = d(10),
            dayOp = d(13),
            dayLeave = d(14),
            surveyorPay = d(15),
            receiveDate = s(17).toEnglishDigits(),
            receiveAmount = recvAmt,
            note = s(19)
        )
    }

    private fun toCsvRow(row: TunnelMonthRow): List<String> {
        fun n(v: Double): String =
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
        return listOf(
            row.dateCode.toString(),
            n(row.days),
            n(row.unitPrice),
            n(row.totalAmount),
            n(row.retention),
            n(row.lunchCount),
            n(row.lunchDeduction),
            n(row.overtimeAdd),
            n(row.timesheetDeduction),
            n(row.cameraDeduction),
            n(row.cameraTimeDeduction),
            n(row.totalDeductions),
            n(row.payable),
            n(row.dayOp),
            n(row.dayLeave),
            n(row.surveyorPay),
            n(row.income),
            row.receiveDate,
            row.receiveAmount?.let { n(it) } ?: "",
            row.note
        )
    }

    private fun writeAll(context: Context, rows: List<TunnelMonthRow>) {
        val data = listOf(CSV_HEADER) + rows.sortedBy { it.dateCode }.map { toCsvRow(it) }
        CsvStore.overwriteAll(context, CSV, data)
    }

    fun saveMonth(context: Context, row: TunnelMonthRow) {
        val list = all(context).filterNot { it.dateCode == row.dateCode } + row
        writeAll(context, list)
    }

    fun deleteMonth(context: Context, dateCode: Int) {
        writeAll(context, all(context).filterNot { it.dateCode == dateCode })
    }

    fun lastUnitPrice(context: Context): Double {
        return all(context).lastOrNull { it.unitPrice > 0 }?.unitPrice ?: 0.0
    }

    /** اولین سطر با مبلغ دریافت خالی — برای ثبت دریافتی */
    fun firstEmptyReceiveIndex(context: Context): TunnelMonthRow? {
        return all(context).firstOrNull { it.receiveAmount == null }
    }

    fun addReceipt(context: Context, amount: Double, receiveDate: String, note: String = ""): Boolean {
        val list = all(context).toMutableList()
        val idx = list.indexOfFirst { it.receiveAmount == null }
        if (idx < 0) return false
        list[idx] = list[idx].copy(receiveAmount = amount, receiveDate = receiveDate, note = if (note.isNotBlank()) note else list[idx].note)
        writeAll(context, list)
        return true
    }

    fun clearReceipt(context: Context, dateCode: Int) {
        val list = all(context).map {
            if (it.dateCode == dateCode) it.copy(receiveAmount = null, receiveDate = "") else it
        }
        writeAll(context, list)
    }

    /**
     * حسن‌انجام بلوکه = جمع E از آخرین ماه اردیبهشت (ماه ۲) تا الان.
     * اگر اردیبهشت در سوابق نباشد، صفر.
     * مانده دریافتی = جمع صورت‌وضعیت − حسن‌انجام بلوکه − جمع دریافتی‌ها
     */
    fun summary(context: Context): TunnelFinanceSummary {
        val rows = all(context)
        val sumM = rows.sumOf { it.payable }
        val sumS = rows.mapNotNull { it.receiveAmount }.sum()
        val lastOrdibehesht = rows.lastOrNull { it.month == 2 }
        val blocked = if (lastOrdibehesht != null) {
            rows.filter { it.dateCode >= lastOrdibehesht.dateCode }.sumOf { it.retention }
        } else {
            0.0
        }
        val remaining = sumM - blocked - sumS
        return TunnelFinanceSummary(sumM, sumS, blocked, remaining)
    }

    fun exportCsvText(context: Context): String {
        val rows = all(context)
        val lines = mutableListOf(CSV_HEADER.joinToString(","))
        rows.forEach { lines.add(toCsvRow(it).joinToString(",")) }
        return lines.joinToString("\n") + "\n"
    }

    fun importCsvText(context: Context, text: String): Int {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return 0
        val parsed = mutableListOf<TunnelMonthRow>()
        for (line in lines) {
            val cols = line.split(",").map { it.trim() }
            if (cols.firstOrNull()?.contains("تاریخ") == true) continue
            parseRow(cols)?.let { parsed.add(it) }
        }
        writeAll(context, parsed)
        return parsed.size
    }
}
