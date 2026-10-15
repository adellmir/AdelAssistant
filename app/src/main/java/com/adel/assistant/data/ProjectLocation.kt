package com.adel.assistant.data

import android.content.Context

/**
 * فضای یکپارچهٔ موقعیت و مسیر پروژه.
 * قانون: اگر لینک نشان باشد همان استفاده می‌شود؛ تبدیل اجباری به مختصات نیست.
 * ثبت روی یک نام → همهٔ ردیف‌های هم‌نام.
 */
object ProjectLocation {

    fun openNavigation(context: Context, entry: ProjectEntry) {
        openNeshanNav(context, entry)
    }

    fun openNavigation(
        context: Context,
        lat: Double,
        lon: Double,
        neshanLink: String = ""
    ) {
        openNeshanNav(context, lat, lon, neshanLink)
    }

    fun hasLocation(entry: ProjectEntry): Boolean = entry.hasLocation

    fun updateForProjectName(
        context: Context,
        projectName: String,
        lat: Double,
        lon: Double,
        neshanLink: String = ""
    ) {
        ProjectStore.updateLocationByProjectName(context, projectName, lat, lon, neshanLink)
    }

    /** لینک خام نشان یا geo از lat/lon */
    fun resolveUrl(entry: ProjectEntry): String = entry.neshanUrl
}
