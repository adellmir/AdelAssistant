package com.adel.assistant.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * مسیر درخواستی از ویجت / اعلان + تاریخ انتخاب‌شده برای تقویم.
 */
object DeepLinkHolder {
    var pendingRoute by mutableStateOf<String?>(null)
        private set

    /** روز/ماه/سال جلالی از ویجت تقویم؛ بعد از مصرف null می‌شود */
    var pendingCalYear by mutableStateOf<Int?>(null)
        private set
    var pendingCalMonth by mutableStateOf<Int?>(null)
        private set
    var pendingCalDay by mutableStateOf<Int?>(null)
        private set

    fun setRoute(route: String?) {
        pendingRoute = route
    }

    fun setCalendarTarget(year: Int?, month: Int?, day: Int?) {
        pendingCalYear = year
        pendingCalMonth = month
        pendingCalDay = day
    }

    fun consumeCalendarTarget(): Triple<Int, Int, Int>? {
        val y = pendingCalYear
        val m = pendingCalMonth
        val d = pendingCalDay
        pendingCalYear = null
        pendingCalMonth = null
        pendingCalDay = null
        return if (y != null && m != null && d != null) Triple(y, m, d) else null
    }
}
