package com.adel.assistant.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.*

/**
 * نقطهٔ مکان — مختصات جغرافیایی + UTM محاسبه‌شده.
 */
data class LocationPoint(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val alt: Double = 0.0,
    val accuracy: Float = -1f,
    val code: String = "",
    val zone: Int = UtmGeo.DEFAULT_ZONE,
    val easting: Double = 0.0,
    val northing: Double = 0.0,
    val timeMs: Long = System.currentTimeMillis(),
    val note: String = ""
) {
    fun withUtm(zoneOverride: Int? = null): LocationPoint {
        val z = zoneOverride ?: UtmGeo.zoneFromLon(lon)
        val (e, n) = UtmGeo.fromLatLon(lat, lon, z)
        return copy(zone = z, easting = e, northing = n)
    }

    fun toSurveyPoint(): SurveyPoint =
        SurveyPoint(name.ifBlank { id }, easting, northing, alt, code)
}

object LocationStore {
    private const val FILE = "location_session.json"

    private fun file(context: Context): File {
        val dir = File(context.filesDir, "data")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE)
    }

    fun load(context: Context): List<LocationPoint> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                LocationPoint(
                    id = o.optString("id", System.nanoTime().toString()),
                    name = o.optString("name", ""),
                    lat = o.optDouble("lat", 0.0),
                    lon = o.optDouble("lon", 0.0),
                    alt = o.optDouble("alt", 0.0),
                    accuracy = o.optDouble("accuracy", -1.0).toFloat(),
                    code = o.optString("code", ""),
                    zone = o.optInt("zone", UtmGeo.DEFAULT_ZONE),
                    easting = o.optDouble("easting", 0.0),
                    northing = o.optDouble("northing", 0.0),
                    timeMs = o.optLong("timeMs", 0L),
                    note = o.optString("note", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, points: List<LocationPoint>) {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("lat", p.lat)
                    .put("lon", p.lon)
                    .put("alt", p.alt)
                    .put("accuracy", p.accuracy.toDouble())
                    .put("code", p.code)
                    .put("zone", p.zone)
                    .put("easting", p.easting)
                    .put("northing", p.northing)
                    .put("timeMs", p.timeMs)
                    .put("note", p.note)
            )
        }
        file(context).writeText(arr.toString())
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** فاصله افقی (متر) و آزیموت از شمال (درجه ۰–۳۶۰) بین دو نقطه جغرافیایی */
    fun distanceAndAzimuth(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Pair<Double, Double> {
        val r = 6_378_137.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(p1) * cos(p2) * sin(dLon / 2).pow(2)
        val dist = 2 * r * asin(min(1.0, sqrt(a)))
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        var az = Math.toDegrees(atan2(y, x))
        if (az < 0) az += 360.0
        return dist to az
    }

    /** آفست از lat/lon با فاصله (متر) و آزیموت (درجه از شمال) */
    fun offsetLatLon(lat: Double, lon: Double, distanceM: Double, azimuthDeg: Double): Pair<Double, Double> {
        val r = 6_378_137.0
        val brng = Math.toRadians(azimuthDeg)
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val ang = distanceM / r
        val lat2 = asin(sin(lat1) * cos(ang) + cos(lat1) * sin(ang) * cos(brng))
        val lon2 = lon1 + atan2(
            sin(brng) * sin(ang) * cos(lat1),
            cos(ang) - sin(lat1) * sin(lat2)
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }

    fun pathLength(points: List<LocationPoint>): Double {
        if (points.size < 2) return 0.0
        var s = 0.0
        for (i in 0 until points.size - 1) {
            s += distanceAndAzimuth(points[i].lat, points[i].lon, points[i + 1].lat, points[i + 1].lon).first
        }
        return s
    }
}
