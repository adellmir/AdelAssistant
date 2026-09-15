package com.adel.assistant.data

import android.content.Context
import kotlin.math.round

/**
 * یک جدول واحد مطابق tunnel_financial.csv
 * هر سطر = یک ماه کاری (+ اختیاری یک دریافتی در همان سطر)
 *
 * منطق محاسبات (مطابق دفتر اکسل):
 *  D مبلغ کل        = روز × مبلغ واحد
 *  E حسن انجام      = معمولاً ۱۰٪ مبلغ کل (قابل ذخیرهٔ دستی برای سوابق)
 *  L کل کسورات      = حسن + کسر نهاری + کسر تایم‌شیت + کسر دوربین + کسر تایم دوربین
 *  M صورت پرداختی   = مبلغ کل − کل کسورات + اضافه تایم‌شیت
 *  Q درآمد          = صورت پرداختی + حسن − نقشه بردار
 *
 * خلاصه:
 *  کل صورت          = Σ M
 *  کل حسن           = Σ E
 *  حسن بلوکه        = Σ E از آخرین اردیبهشت (ماه ۲) تا الان
 *  حسن آزاد         = کل حسن − حسن بلوکه
 *  جمع دریافتی      = Σ مبلغ دریافت
 *  مطالبات آزاد     = کل صورت + حسن آزاد − جمع دریافتی
 */
data class TunnelMonthRow(
    val dateCode: Int,
    val days: Double,
    val unitPrice: Double,
    val lunchCount: Double = 0.0,
    val lunchDeduction: Double = 0.0,
    val overtimeAdd: Double = 0.0,
    val timesheetDeduction: Double = 0.0,
    val cameraDeduction: Double = 0.0,
    val cameraTimeDeduction: Double = 0.0,
    val dayOp: Double = 0.0,
    val dayLeave: Double = 0.0,
    val surveyorPay: Double = 0.0,
    val receiveDate: String = "",
    val receiveAmount: Double? = null,
    val note: String = "",
    /** اگر از CSV/اکسل آمده باشد، همان عدد ذخیره می‌شود؛ وگرنه محاسبه می‌شود */
    val totalAmount: Double = 0.0,
    val retention: Double = 0.0,
    val totalDeductions: Double = 0.0,
    val payable: Double = 0.0,
    val income: Double = 0.0
) {
    val year: Int
    val month: Int

    init {
        val (y, m) = parseDateCode(dateCode)
        year = y
        month = m
    }

    companion object {
        fun parseDateCode(code: Int): Pair<Int, Int> {
            return if (code < 10000) {
                val y = 1300 + code / 100
                val m = code % 100
                y to m
            } else {
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

        /**
         * ساخت سطر با محاسبهٔ خودکار (برای ثبت ماه جدید در UI)
         * @param retentionOverride اگر null باشد ۱۰٪ مبلغ کل
         */
        fun compute(
            dateCode: Int,
            days: Double,
            unitPrice: Double,
            lunchCount: Double = 0.0,
            lunchDeduction: Double = 0.0,
            overtimeAdd: Double = 0.0,
            timesheetDeduction: Double = 0.0,
            cameraDeduction: Double = 0.0,
            cameraTimeDeduction: Double = 0.0,
            dayOp: Double = 0.0,
            dayLeave: Double = 0.0,
            surveyorPay: Double = 0.0,
            receiveDate: String = "",
            receiveAmount: Double? = null,
            note: String = "",
            retentionOverride: Double? = null
        ): TunnelMonthRow {
            val total = days * unitPrice
            val hasan = retentionOverride ?: round(total * 0.10)
            val kasr = hasan + lunchDeduction + timesheetDeduction + cameraDeduction + cameraTimeDeduction
            val soorat = total - kasr + overtimeAdd
            val daramad = soorat + hasan - surveyorPay
            return TunnelMonthRow(
                dateCode = dateCode,
                days = days,
                unitPrice = unitPrice,
                lunchCount = lunchCount,
                lunchDeduction = lunchDeduction,
                overtimeAdd = overtimeAdd,
                timesheetDeduction = timesheetDeduction,
                cameraDeduction = cameraDeduction,
                cameraTimeDeduction = cameraTimeDeduction,
                dayOp = dayOp,
                dayLeave = dayLeave,
                surveyorPay = surveyorPay,
                receiveDate = receiveDate,
                receiveAmount = receiveAmount,
                note = note,
                totalAmount = total,
                retention = hasan,
                totalDeductions = kasr,
                payable = soorat,
                income = daramad
            )
        }
    }

    /** پس از ویرایش فیلدهای ورودی، محاسبات را تازه می‌کند (حسن دستی حفظ می‌شود اگر keepRetention) */
    fun recalculate(keepRetention: Boolean = true): TunnelMonthRow =
        compute(
            dateCode = dateCode,
            days = days,
            unitPrice = unitPrice,
            lunchCount = lunchCount,
            lunchDeduction = lunchDeduction,
            overtimeAdd = overtimeAdd,
            timesheetDeduction = timesheetDeduction,
            cameraDeduction = cameraDeduction,
            cameraTimeDeduction = cameraTimeDeduction,
            dayOp = dayOp,
            dayLeave = dayLeave,
            surveyorPay = surveyorPay,
            receiveDate = receiveDate,
            receiveAmount = receiveAmount,
            note = note,
            retentionOverride = if (keepRetention) retention else null
        )
}

data class TunnelFinanceSummary(
    val sumPayable: Double,          // کل صورت وضعیت‌ها (Σ M)
    val sumRetention: Double,        // کل حسن انجام (Σ E)
    val sumHasanAzad: Double,        // حسن آزاد = کل حسن − بلوکه
    val sumReceived: Double,         // جمع دریافتی‌ها
    val blockedRetention: Double,    // حسن بلوکه از آخرین اردیبهشت
    val remaining: Double            // مطالبات آزاد = صورت + حسن آزاد − دریافتی
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

        val days = d(1)
        val unit = d(2)
        // مقادیر محاسبه‌شده از CSV (اگر خالی بود از نو حساب می‌شود)
        val totalCsv = d(3)
        val hasanCsv = d(4)
        val kasrCsv = d(11)
        val payableCsv = d(12)
        val incomeCsv = d(16)

        val base = TunnelMonthRow.compute(
            dateCode = code,
            days = days,
            unitPrice = unit,
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
            note = s(19),
            retentionOverride = if (hasanCsv != 0.0 || totalCsv != 0.0) hasanCsv else null
        )
        // اگر CSV عدد صریح داشت، همان را نگه دار (سوابق اکسل)
        return base.copy(
            totalAmount = if (totalCsv != 0.0) totalCsv else base.totalAmount,
            retention = if (hasanCsv != 0.0 || totalCsv != 0.0) hasanCsv else base.retention,
            totalDeductions = if (kasrCsv != 0.0) kasrCsv else base.totalDeductions,
            payable = if (payableCsv != 0.0 || totalCsv != 0.0) payableCsv else base.payable,
            income = if (incomeCsv != 0.0 || totalCsv != 0.0) incomeCsv else base.income
        )
    }

    private fun toCsvRow(row: TunnelMonthRow): List<String> {
        fun n(v: Double): String =
            if (kotlin.math.abs(v - v.toLong().toDouble()) < 1e-9) v.toLong().toString()
            else v.toString()
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

    fun firstEmptyReceiveIndex(context: Context): TunnelMonthRow? {
        return all(context).firstOrNull { it.receiveAmount == null }
    }

    fun addReceipt(context: Context, amount: Double, receiveDate: String, note: String = ""): Boolean {
        val list = all(context).toMutableList()
        val idx = list.indexOfFirst { it.receiveAmount == null }
        if (idx < 0) return false
        list[idx] = list[idx].copy(
            receiveAmount = amount,
            receiveDate = receiveDate,
            note = if (note.isNotBlank()) note else list[idx].note
        )
        writeAll(context, list)
        return true
    }

    fun updateReceipt(context: Context, dateCode: Int, amount: Double, receiveDate: String, note: String) {
        val list = all(context).map {
            if (it.dateCode == dateCode)
                it.copy(receiveAmount = amount, receiveDate = receiveDate, note = note)
            else it
        }
        writeAll(context, list)
    }

    fun clearReceipt(context: Context, dateCode: Int) {
        val list = all(context).map {
            if (it.dateCode == dateCode) it.copy(receiveAmount = null, receiveDate = "") else it
        }
        writeAll(context, list)
    }

    /**
     * حسن بلوکه = جمع حسن از آخرین اردیبهشت (ماه ۲) تا امروز.
     * مطالبات آزاد = کل صورت + (کل حسن − حسن بلوکه) − جمع دریافتی
     */
    fun summary(context: Context): TunnelFinanceSummary {
        val rows = all(context)
        val sumPayable = rows.sumOf { it.payable }
        val sumRetention = rows.sumOf { it.retention }
        val sumReceived = rows.mapNotNull { it.receiveAmount }.sum()
        val lastOrdibehesht = rows.filter { it.month == 2 }.maxByOrNull { it.dateCode }
        val blocked = if (lastOrdibehesht != null) {
            rows.filter { it.dateCode >= lastOrdibehesht.dateCode }.sumOf { it.retention }
        } else {
            0.0
        }
        val hasanAzad = (sumRetention - blocked).coerceAtLeast(0.0)
        val remaining = sumPayable + hasanAzad - sumReceived
        return TunnelFinanceSummary(
            sumPayable = sumPayable,
            sumRetention = sumRetention,
            sumHasanAzad = hasanAzad,
            sumReceived = sumReceived,
            blockedRetention = blocked,
            remaining = remaining
        )
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
