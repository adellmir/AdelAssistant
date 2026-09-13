package com.adel.assistant.data

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * تبدیل UTM ↔ Lat/Lon (WGS84).
 *
 * باگ قبلی: پیش‌فرض ۵۷ به‌عنوان «شماره زون» تفسیر می‌شد → نصف‌النهار ۱۵۹° → طول ≈۱۶۱°.
 * حالا فقط شماره زون (۱..۶۰) پذیرفته می‌شود؛ پیش‌فرض زون ۴۰ ایران.
 */
object UtmGeo {

    const val DEFAULT_ZONE = 40

    fun zoneToCentralMeridian(zone: Int): Double =
        (zone.coerceIn(1, 60) - 1) * 6.0 - 180.0 + 3.0

    fun zoneFromLon(lonDeg: Double): Int =
        (((lonDeg + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)

    /**
     * UTM → Lat/Lon
     * @param zone شماره زون UTM (۱..۶۰) — نه نصف‌النهار مرکزی
     */
    fun toLatLon(
        easting: Double,
        northing: Double,
        zone: Int = DEFAULT_ZONE,
        northernHemisphere: Boolean = true
    ): Pair<Double, Double> {
        val lon0Deg = zoneToCentralMeridian(zone)
        val a = 6378137.0
        val f = 1.0 / 298.257223563
        val k0 = 0.9996
        val e = sqrt(f * (2 - f))
        val e1sq = e * e / (1 - e * e)
        var y = northing
        if (!northernHemisphere) y -= 10_000_000.0
        val m = y / k0
        val mu = m / (a * (1 - e * e / 4 - 3 * e.pow(4) / 64 - 5 * e.pow(6) / 256))
        val e1 = (1 - sqrt(1 - e * e)) / (1 + sqrt(1 - e * e))
        val j1 = 3 * e1 / 2 - 27 * e1.pow(3) / 32
        val j2 = 21 * e1.pow(2) / 16 - 55 * e1.pow(4) / 32
        val j3 = 151 * e1.pow(3) / 96
        val j4 = 1097 * e1.pow(4) / 512
        val fp = mu + j1 * sin(2 * mu) + j2 * sin(4 * mu) + j3 * sin(6 * mu) + j4 * sin(8 * mu)
        val c1 = e1sq * cos(fp).pow(2)
        val t1 = tan(fp).pow(2)
        val r1 = a * (1 - e * e) / (1 - e * e * sin(fp).pow(2)).pow(1.5)
        val n1 = a / sqrt(1 - e * e * sin(fp).pow(2))
        val d = (easting - 500_000.0) / (n1 * k0)
        val lat = fp - (n1 * tan(fp) / r1) * (
            d.pow(2) / 2 -
                (5 + 3 * t1 + 10 * c1 - 4 * c1.pow(2) - 9 * e1sq) * d.pow(4) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1.pow(2) - 252 * e1sq - 3 * c1.pow(2)) * d.pow(6) / 720
            )
        // حتماً c1² — نه n1² (باگ قبلی در ChainageScreen)
        val lon = (
            d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
                (5 - 2 * c1 + 28 * t1 - 3 * c1.pow(2) + 8 * e1sq + 24 * t1.pow(2)) * d.pow(5) / 120
            ) / cos(fp)
        return Math.toDegrees(lat) to (lon0Deg + Math.toDegrees(lon))
    }

    fun fromLatLon(latDeg: Double, lonDeg: Double, zone: Int = DEFAULT_ZONE): Pair<Double, Double> {
        val z = zone.coerceIn(1, 60)
        val lat = Math.toRadians(latDeg)
        val lon = Math.toRadians(lonDeg)
        val lon0 = Math.toRadians(zoneToCentralMeridian(z))
        val a = 6378137.0
        val f = 1.0 / 298.257223563
        val k0 = 0.9996
        val e2 = f * (2 - f)
        val ep2 = e2 / (1 - e2)
        val n = a / sqrt(1 - e2 * sin(lat).pow(2))
        val t = tan(lat).pow(2)
        val c = ep2 * cos(lat).pow(2)
        val aa = cos(lat) * (lon - lon0)
        val m = a * (
            (1 - e2 / 4 - 3 * e2.pow(2) / 64 - 5 * e2.pow(3) / 256) * lat -
                (3 * e2 / 8 + 3 * e2.pow(2) / 32 + 45 * e2.pow(3) / 1024) * sin(2 * lat) +
                (15 * e2.pow(2) / 256 + 45 * e2.pow(3) / 1024) * sin(4 * lat) -
                (35 * e2.pow(3) / 3072) * sin(6 * lat)
            )
        val easting = k0 * n * (
            aa + (1 - t + c) * aa.pow(3) / 6 +
                (5 - 18 * t + t.pow(2) + 72 * c - 58 * ep2) * aa.pow(5) / 120
            ) + 500_000.0
        val northing = k0 * (
            m + n * tan(lat) * (
                aa.pow(2) / 2 +
                    (5 - t + 9 * c + 4 * c.pow(2)) * aa.pow(4) / 24 +
                    (61 - 58 * t + t.pow(2) + 600 * c - 330 * ep2) * aa.pow(6) / 720
                )
            )
        return easting to northing
    }

    fun googleMapsUrl(lat: Double, lon: Double): String =
        "https://www.google.com/maps/search/?api=1&query=$lat,$lon"

    fun neshanIntentUri(lat: Double, lon: Double): String {
        val fallback = "https://nshn.ir/?lat=$lat&lng=$lon"
        return "intent://nshn.ir/?lat=$lat&lng=$lon#Intent;scheme=http;package=org.rajman.neshan.traffic.tehran.navigator;S.browser_fallback_url=$fallback;end"
    }

    fun toKml(points: List<SurveyPoint>, documentName: String = "AdelAssistant", zone: Int = DEFAULT_ZONE): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        sb.appendLine("<Document>")
        sb.appendLine("<name>${escapeXml(documentName)}</name>")
        points.forEach { p ->
            val (lat, lon) = toLatLon(p.x, p.y, zone)
            val name = escapeXml("${p.id} ${p.code}".trim())
            sb.appendLine("<Placemark>")
            sb.appendLine("<name>$name</name>")
            sb.appendLine("<description>${escapeXml("Z=${p.z}")}</description>")
            sb.appendLine("<Point><coordinates>$lon,$lat,${p.z}</coordinates></Point>")
            sb.appendLine("</Placemark>")
        }
        sb.appendLine("</Document></kml>")
        return sb.toString()
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
