package com.adel.assistant.data

import android.content.Context
import java.util.Calendar

object CalendarStore {
    private const val CSV = "calendar"

    fun weekdayFor(context: Context, day: String, month: String): String? {
        val d = day.toIntOrNullFa() ?: return null
        val m = month.toIntOrNullFa() ?: return null
        return CsvStore.readAll(context, CSV).firstOrNull { row ->
            row.size >= 3 && row[0].toIntOrNullFa() == d && row[1].toIntOrNullFa() == m
        }?.get(2)
    }

    /** نام روز هفته برای تاریخ شمسی — مستقل از CSV */
    fun weekdayNameJalali(jy: Int, jm: Int, jd: Int): String {
        if (jy <= 0 || jm !in 1..12 || jd <= 0) return ""
        val (gy, gm, gd) = jalaliToGregorian(jy, jm, jd)
        val cal = Calendar.getInstance()
        cal.set(gy, gm - 1, gd)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یکشنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنجشنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> ""
        }
    }

    fun todayJalali(): Triple<Int, Int, Int> {
        val now = Calendar.getInstance()
        return gregorianToJalali(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
    }

    private fun isLeapJalali(year: Int): Boolean {
        val breaks = setOf(1, 5, 9, 13, 17, 22, 26, 30)
        var y = year % 33
        if (y < 0) y += 33
        return breaks.contains(y)
    }

    fun jalaliMonthLength(year: Int, month: Int): Int = when {
        month in 1..6 -> 31
        month in 7..11 -> 30
        else -> if (isLeapJalali(year)) 30 else 29
    }

    /** تاریخ شمسی روز قبل را به‌درستی برمی‌گرداند (عبور صحیح از مرز ماه/سال) */
    fun previousJalaliDay(year: Int, month: Int, day: Int): Triple<Int, Int, Int> {
        if (day > 1) return Triple(year, month, day - 1)
        val prevMonth = if (month > 1) month - 1 else 12
        val prevYear = if (month > 1) year else year - 1
        return Triple(prevYear, prevMonth, jalaliMonthLength(prevYear, prevMonth))
    }

    /** کلید مرتب‌سازی تاریخِ روز قبل، برای استفاده مستقیم در جستجوی رکوردها */
    fun previousDateKey(year: String, month: String, day: String): String {
        val y = year.toIntOrNullFa() ?: 0
        val m = month.toIntOrNullFa() ?: 0
        val d = day.toIntOrNullFa() ?: 0
        val (py, pm, pd) = previousJalaliDay(y, m, d)
        return "%d%02d%02d".format(py, pm, pd)
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
                ((gy2 + 399) / 400) + gd
        for (i in 0 until gm - 1) days += gDaysInMonth[i]
        if (gm > 2 && (gy % 4 == 0 && (gy % 100 != 0 || gy % 400 == 0))) days += 1
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return Triple(jy, jm, jd)
    }
}
