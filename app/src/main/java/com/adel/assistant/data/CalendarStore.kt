package com.adel.assistant.data

import android.content.Context
import java.util.Calendar


object CalendarStore {
    private const val CSV = "calendar"

    /** اسم روز هفته برای یک روز/ماه شمسی، از فایل تقویم آپلودشده */
    fun weekdayFor(context: Context, day: String, month: String): String? {
        val d = day.toIntOrNullFa() ?: return null
        val m = month.toIntOrNullFa() ?: return null
        return CsvStore.readAll(context, CSV).firstOrNull { row ->
            row.size >= 3 && row[0].toIntOrNull() == d && row[1].toIntOrNull() == m
        }?.get(2)
    }

    /** تبدیل تقریبی میلادی به شمسی برای پیش‌فرض تاریخ امروز (الگوریتم استاندارد) */
    fun todayJalali(): Triple<Int, Int, Int> {
        val now = Calendar.getInstance()
        val gy = now.get(Calendar.YEAR)
        val gm = now.get(Calendar.MONTH) + 1
        val gd = now.get(Calendar.DAY_OF_MONTH)
        return gregorianToJalali(gy, gm, gd)
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gy2 = if (gm > 2) gy + 1 else gy
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
