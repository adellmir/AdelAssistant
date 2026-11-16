package com.adel.assistant.data

/**
 * هدف جهت‌یابی که از نقاط تونل / نقشه تونل به برنامه مکان پاس داده می‌شود.
 */
object PendingNavigate {
    var targetLabel: String = ""
    var lat: Double? = null
    var lon: Double? = null
    var easting: Double? = null
    var northing: Double? = null
    var zone: Int = UtmGeo.DEFAULT_ZONE
    var active: Boolean = false

    fun setLatLon(label: String, la: Double, lo: Double) {
        targetLabel = label
        lat = la; lon = lo
        easting = null; northing = null
        active = true
    }

    fun setUtm(label: String, e: Double, n: Double, z: Int = UtmGeo.DEFAULT_ZONE) {
        targetLabel = label
        easting = e; northing = n; zone = z
        try {
            val ll = UtmGeo.toLatLon(e, n, z)
            lat = ll.first; lon = ll.second
        } catch (_: Exception) {
            lat = null; lon = null
        }
        active = true
    }

    fun clear() {
        active = false
        targetLabel = ""
        lat = null; lon = null
        easting = null; northing = null
    }
}
