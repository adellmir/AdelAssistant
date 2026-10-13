package com.adel.assistant.ui

/**
 * فیلتر موقت برای باز کردن کارفرمایان و پروژه‌ها با پیش‌فرض جستجو/تب.
 * tab: 0 = پروژه‌ها، 1 = کارفرمایان
 */
object PendingClientsNav {
    var tab: Int? = null
    var searchProject: String? = null
    var searchEmployer: String? = null

    fun consumeTab(): Int? {
        val t = tab
        tab = null
        return t
    }

    fun consumeProjectSearch(): String? {
        val s = searchProject
        searchProject = null
        return s
    }

    fun consumeEmployerSearch(): String? {
        val s = searchEmployer
        searchEmployer = null
        return s
    }

    fun openProject(name: String) {
        tab = 0
        searchProject = name
        searchEmployer = null
    }

    fun openEmployer(name: String) {
        tab = 1
        searchEmployer = name
        searchProject = null
    }
}
