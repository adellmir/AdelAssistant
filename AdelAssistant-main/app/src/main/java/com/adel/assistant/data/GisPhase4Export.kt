package com.adel.assistant.data

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * فاز ۴: GeoJSON، KMZ، پشتیبان JSON نقاط+اندازه‌گیری‌ها.
 */
object GisPhase4Export {

    fun pointsToGeoJson(points: List<LocationPoint>): String {
        val feats = points.joinToString(",\n") { p ->
            """
            {
              "type": "Feature",
              "geometry": { "type": "Point", "coordinates": [${fmt(p.lon)}, ${fmt(p.lat)}, ${fmt(p.alt)}] },
              "properties": {
                "name": ${jq(p.name)},
                "code": ${jq(p.code)},
                "easting": ${fmt(p.easting)},
                "northing": ${fmt(p.northing)},
                "zone": ${p.zone},
                "accuracy": ${p.accuracy},
                "fix": ${p.fix},
                "sats": ${p.sats},
                "hdop": ${p.hdop},
                "source": ${jq(if (p.isExternal) "external" else "phone")}
              }
            }
            """.trimIndent()
        }
        return """{"type":"FeatureCollection","features":[$feats]}"""
    }

    fun measuresToGeoJson(measures: List<Measure>): String {
        val feats = measures.mapNotNull { m ->
            val ring = m.rings.firstOrNull() ?: return@mapNotNull null
            if (ring.isEmpty()) return@mapNotNull null
            val coords = ring.map { (e, n) ->
                val (lat, lon) = UtmGeo.toLatLon(e, n, m.zone)
                "[${fmt(lon)}, ${fmt(lat)}]"
            }
            // بستن حلقه برای Polygon
            val closed = if (coords.first() != coords.last()) coords + coords.first() else coords
            val geom = when (m.type) {
                MeasureType.FIELD -> {
                    val holes = m.rings.drop(1).map { hole ->
                        val hc = hole.map { (e, n) ->
                            val (lat, lon) = UtmGeo.toLatLon(e, n, m.zone)
                            "[${fmt(lon)}, ${fmt(lat)}]"
                        }
                        val hc2 = if (hc.isNotEmpty() && hc.first() != hc.last()) hc + hc.first() else hc
                        "[${hc2.joinToString(",")}]"
                    }
                    val ringsStr = listOf("[${closed.joinToString(",")}]") + holes
                    """{ "type": "Polygon", "coordinates": [${ringsStr.joinToString(",")}] }"""
                }
                MeasureType.LINE -> """{ "type": "LineString", "coordinates": [${coords.joinToString(",")}] }"""
                MeasureType.POI -> {
                    val (lat, lon) = UtmGeo.toLatLon(ring[0].first, ring[0].second, m.zone)
                    """{ "type": "Point", "coordinates": [${fmt(lon)}, ${fmt(lat)}] }"""
                }
            }
            """
            {
              "type": "Feature",
              "geometry": $geom,
              "properties": {
                "name": ${jq(m.name)},
                "type": ${jq(m.type.name)},
                "area_m2": ${fmt(m.areaM2)},
                "length_m": ${fmt(m.lengthM)},
                "zone": ${m.zone},
                "note": ${jq(m.note)}
              }
            }
            """.trimIndent()
        }.joinToString(",\n")
        return """{"type":"FeatureCollection","features":[$feats]}"""
    }

    /** KML ساده از LocationPoint (lat/lon مستقیم) */
    fun pointsToKml(points: List<LocationPoint>, name: String = "AdelLocation"): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>${esc(name)}</name>""")
        points.forEach { p ->
            sb.appendLine("<Placemark><name>${esc(p.name)}</name>")
            sb.appendLine("<description>${esc("code=${p.code} z=${p.alt} acc=${p.accuracy}")}</description>")
            sb.appendLine(String.format(Locale.US, "<Point><coordinates>%.8f,%.8f,%.3f</coordinates></Point>", p.lon, p.lat, p.alt))
            sb.appendLine("</Placemark>")
        }
        measuresKmlFolder(sb)
        sb.appendLine("</Document></kml>")
        return sb.toString()
    }

    private var measuresForKml: List<Measure> = emptyList()
    fun setMeasuresForKml(m: List<Measure>) { measuresForKml = m }

    private fun measuresKmlFolder(sb: StringBuilder) {
        if (measuresForKml.isEmpty()) return
        sb.appendLine("<Folder><name>Measures</name>")
        measuresForKml.forEach { m ->
            val ring = m.rings.firstOrNull() ?: return@forEach
            if (ring.size < 2) return@forEach
            sb.appendLine("<Placemark><name>${esc(m.name)}</name>")
            when (m.type) {
                MeasureType.FIELD -> {
                    sb.appendLine("<Polygon><outerBoundaryIs><LinearRing><coordinates>")
                    val closed = if (ring.first() != ring.last()) ring + ring.first() else ring
                    closed.forEach { (e, n) ->
                        val (lat, lon) = UtmGeo.toLatLon(e, n, m.zone)
                        sb.append(String.format(Locale.US, " %.8f,%.8f,0", lon, lat))
                    }
                    sb.appendLine("</coordinates></LinearRing></outerBoundaryIs></Polygon>")
                }
                else -> {
                    sb.appendLine("<LineString><coordinates>")
                    ring.forEach { (e, n) ->
                        val (lat, lon) = UtmGeo.toLatLon(e, n, m.zone)
                        sb.append(String.format(Locale.US, " %.8f,%.8f,0", lon, lat))
                    }
                    sb.appendLine("</coordinates></LineString>")
                }
            }
            sb.appendLine("</Placemark>")
        }
        sb.appendLine("</Folder>")
    }

    fun writeKmzToDocuments(context: Context, fileName: String, kml: String): Uri? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "export").apply { mkdirs() }
            val f = File(dir, fileName)
            ZipOutputStream(BufferedOutputStream(FileOutputStream(f))).use { zos ->
                zos.putNextEntry(ZipEntry("doc.kml"))
                zos.write(kml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            // also try Documents via FileExport text path for path reporting
            android.net.Uri.fromFile(f)
        } catch (_: Exception) {
            null
        }
    }

    fun backupJson(points: List<LocationPoint>, measures: List<Measure>): String {
        // ساده: دو بخش
        return """{"points":${points.size},"measures":${measures.size},"geojson_points":${pointsToGeoJson(points)},"geojson_measures":${measuresToGeoJson(measures)}}"""
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.8f", v)
    private fun fmt(v: Float): String = String.format(Locale.US, "%.3f", v)
    private fun jq(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
