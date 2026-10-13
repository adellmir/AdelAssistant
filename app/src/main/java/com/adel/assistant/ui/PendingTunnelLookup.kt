package com.adel.assistant.ui

/** پیش‌پر کردن برنامه نقاط تونل */
object PendingTunnelLookup {
    var pointNo: String? = null
    var km: String? = null

    fun openPoint(no: String) {
        pointNo = no
        km = null
    }

    fun openKm(k: Double) {
        km = String.format(java.util.Locale.US, "%.3f", k)
        pointNo = null
    }

    fun consumePointNo(): String? {
        val v = pointNo
        pointNo = null
        return v
    }

    fun consumeKm(): String? {
        val v = km
        km = null
        return v
    }
}

/** پیش‌پر کردن گزارش حفاری روزانه */
object PendingDailyReport {
    var year: String? = null
    var month: String? = null
    var day: String? = null

    fun open(y: Int, m: Int, d: Int) {
        year = y.toString()
        month = m.toString()
        day = d.toString()
    }

    fun consume(): Triple<String, String, String>? {
        val y = year ?: return null
        val m = month ?: return null
        val d = day ?: return null
        year = null; month = null; day = null
        return Triple(y, m, d)
    }
}
