package com.adel.assistant.data

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** تبدیل UTM (زون تقریبی ایران، نصف‌النهار مرکزی ۵۷°) به WGS84 */
object UtmGeo {
    fun toLatLon(easting: Double, northing: Double, zoneCentralMeridian: Double = 57.0): Pair<Double, Double> {
        val a = 6378137.0
        val f = 1 / 298.257223563
        val k0 = 0.9996
        val e = sqrt(f * (2 - f))
        val e1sq = e * e / (1 - e * e)
        val m = northing / k0
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
        val d = (easting - 500000.0) / (n1 * k0)
        val lat = fp - (n1 * tan(fp) / r1) * (
            d.pow(2) / 2 -
                (5 + 3 * t1 + 10 * c1 - 4 * c1.pow(2) - 9 * e1sq) * d.pow(4) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1.pow(2) - 252 * e1sq - 3 * c1.pow(2)) * d.pow(6) / 720
            )
        val lon = (
            d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
                (5 - 2 * c1 + 28 * t1 - 3 * c1.pow(2) + 8 * e1sq + 24 * t1.pow(2)) * d.pow(5) / 120
            ) / cos(fp)
        return Pair(Math.toDegrees(lat), zoneCentralMeridian + Math.toDegrees(lon))
    }

    fun googleMapsUrl(lat: Double, lon: Double): String =
        formatEn("https://maps.google.com/?q=%.6f,%.6f", lat, lon)

    fun neshanIntentUri(lat: Double, lon: Double): String {
        val latS = formatEn("%.6f", lat)
        val lonS = formatEn("%.6f", lon)
        return "intent://nshn.ir/?lat=$latS&lng=$lonS#Intent;scheme=http;package=org.rajman.neshan.traffic.tehran.navigator;S.browser_fallback_url=https://nshn.ir/?lat=$latS&lng=$lonS;end"
    }

    fun toKml(points: List<SurveyPoint>, documentName: String = "AdelAssistant"): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>$documentName</name>""").append('\n')
        points.forEach { p ->
            val (lat, lon) = toLatLon(p.x, p.y)
            val name = escapeXml(p.id.ifBlank { "point" })
            val desc = escapeXml(buildString {
                append("X=${p.x} Y=${p.y} Z=${p.z}")
                if (p.code.isNotBlank()) append(" CODE=${p.code}")
            })
            sb.append("<Placemark><name>$name</name><description>$desc</description>")
            sb.append("<Point><coordinates>")
            sb.append(formatEn("%.8f,%.8f,%.3f", lon, lat, p.z))
            sb.append("</coordinates></Point></Placemark>\n")
        }
        sb.append("</Document></kml>\n")
        return sb.toString()
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
