package com.adel.assistant.ui

/** پیش‌پر کردن برنامه نقاط تونل */
object PendingTunnelLookup {
    var pointNo: String? = null
    var km: String? = null
    /** اگر true باشد فرم در حالت ویرایش باز می‌شود */
    var editMode: Boolean = false

    fun openPoint(no: String) {
        pointNo = no
        km = null
        editMode = false
    }

    /** باز کردن نقطه برای ویرایش در برنامه نقاط تونل */
    fun openPointForEdit(no: String) {
        pointNo = no
        km = null
        editMode = true
    }

    fun openKm(k: Double) {
        km = String.format(java.util.Locale.US, "%.3f", k)
        pointNo = null
        editMode = false
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

    fun consumeEditMode(): Boolean {
        val v = editMode
        editMode = false
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
