package com.adel.assistant.data

import java.util.Locale

/**
 * خروجی GIS: KML و GPX از نقاط UTM (E,N,Z).
 */
object GeoExport {

    fun toKml(
        points: List<SurveyPoint>,
        documentName: String = "AdelAssistant",
        zone: Int = UtmGeo.DEFAULT_ZONE,
        lines: List<List<SurveyPoint>> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        sb.appendLine("<Document>")
        sb.appendLine("<name>${esc(documentName)}</name>")
        points.forEach { p ->
            val (lat, lon) = UtmGeo.toLatLon(p.x, p.y, zone)
            val name = esc("${p.id} ${p.code}".trim())
            sb.appendLine("<Placemark>")
            sb.appendLine("<name>$name</name>")
            sb.appendLine("<description>${esc("Z=${p.z}")}</description>")
            sb.appendLine("<Point><coordinates>$lon,$lat,${p.z}</coordinates></Point>")
            sb.appendLine("</Placemark>")
        }
        lines.forEachIndexed { i, line ->
            if (line.size < 2) return@forEachIndexed
            sb.appendLine("<Placemark>")
            sb.appendLine("<name>${esc(line.firstOrNull()?.code?.ifBlank { "Line${i + 1}" } ?: "Line${i + 1}")}</name>")
            sb.appendLine("<LineString><coordinates>")
            line.forEach { p ->
                val (lat, lon) = UtmGeo.toLatLon(p.x, p.y, zone)
                sb.append(" $lon,$lat,${p.z}")
            }
            sb.appendLine()
            sb.appendLine("</coordinates></LineString>")
            sb.appendLine("</Placemark>")
        }
        sb.appendLine("</Document></kml>")
        return sb.toString()
    }

    fun toGpx(
        points: List<SurveyPoint>,
        documentName: String = "AdelAssistant",
        zone: Int = UtmGeo.DEFAULT_ZONE,
        tracks: List<List<SurveyPoint>> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="AdelAssistant" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("<metadata><name>${esc(documentName)}</name></metadata>")
        points.forEach { p ->
            val (lat, lon) = UtmGeo.toLatLon(p.x, p.y, zone)
            val name = esc(p.id.ifBlank { "P" })
            sb.appendLine(
                String.format(
                    Locale.US,
                    """<wpt lat="%.8f" lon="%.8f"><ele>%.3f</ele><name>%s</name><cmt>%s</cmt></wpt>""",
                    lat, lon, p.z, name, esc(p.code)
                )
            )
        }
        tracks.forEachIndexed { i, track ->
            if (track.size < 2) return@forEachIndexed
            val tName = esc(track.firstOrNull()?.code?.ifBlank { "Track${i + 1}" } ?: "Track${i + 1}")
            sb.appendLine("<trk><name>$tName</name><trkseg>")
            track.forEach { p ->
                val (lat, lon) = UtmGeo.toLatLon(p.x, p.y, zone)
                sb.appendLine(
                    String.format(
                        Locale.US,
                        """<trkpt lat="%.8f" lon="%.8f"><ele>%.3f</ele></trkpt>""",
                        lat, lon, p.z
                    )
                )
            }
            sb.appendLine("</trkseg></trk>")
        }
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    fun surveyFromGsi(pts: List<GsiPoint>): List<SurveyPoint> =
        pts.map { SurveyPoint(it.name.ifBlank { it.id.toString() }, it.e, it.n, it.z, it.code) }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
