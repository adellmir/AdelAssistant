package com.adel.assistant.data

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * خواندن KML / KMZ → نقاط Survey یا مدل DXF برای نمایش نقشه.
 * مختصات KML به صورت lon,lat[,alt] هستند و به UTM تبدیل می‌شوند.
 */
object KmlParser {

    data class Result(
        val points: List<SurveyPoint>,
        val lines: List<List<SurveyPoint>>, // مسیرها / LineString
        val zone: Int
    )

    fun parseBytes(bytes: ByteArray, fileName: String, zone: Int = UtmGeo.DEFAULT_ZONE): Result {
        val lower = fileName.lowercase()
        val kmlText = when {
            lower.endsWith(".kmz") -> extractKmlFromKmz(bytes)
            else -> bytes.toString(Charsets.UTF_8)
        }
        return parseKmlText(kmlText, zone)
    }

    fun parseKmlText(text: String, zone: Int = UtmGeo.DEFAULT_ZONE): Result {
        val points = mutableListOf<SurveyPoint>()
        val paths = mutableListOf<List<SurveyPoint>>()
        var counter = 1

        // هر Placemark جدا
        val placemarkRegex = Regex(
            "<Placemark\\b[^>]*>(.*?)</Placemark>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val nameRegex = Regex("<name\\b[^>]*>(.*?)</name>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val coordBlockRegex = Regex(
            "<coordinates\\b[^>]*>(.*?)</coordinates>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        fun parseCoordList(raw: String): List<Triple<Double, Double, Double>> {
            // lon,lat[,alt] جدا شده با فاصله یا خط جدید
            return raw.trim().split(Regex("\\s+")).mapNotNull { token ->
                val parts = token.split(",")
                if (parts.size < 2) return@mapNotNull null
                val lon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val alt = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                Triple(lon, lat, alt)
            }
        }

        val marks = placemarkRegex.findAll(text).toList()
        if (marks.isEmpty()) {
            // بدون Placemark — فقط coordinates
            coordBlockRegex.findAll(text).forEach { m ->
                val coords = parseCoordList(m.groupValues[1])
                if (coords.size == 1) {
                    val (lon, lat, alt) = coords[0]
                    val (e, n) = UtmGeo.fromLatLon(lat, lon, zone)
                    points += SurveyPoint("P${counter++}", e, n, alt, "kml")
                } else if (coords.size > 1) {
                    val pathPts = coords.map { (lon, lat, alt) ->
                        val (e, n) = UtmGeo.fromLatLon(lat, lon, zone)
                        SurveyPoint("P${counter++}", e, n, alt, "path")
                    }
                    paths += pathPts
                    points += pathPts
                }
            }
        } else {
            marks.forEach { pm ->
                val body = pm.groupValues[1]
                val name = nameRegex.find(body)?.groupValues?.get(1)
                    ?.replace(Regex("<[^>]+>"), "")
                    ?.trim()
                    ?.ifBlank { null }
                    ?: "P${counter}"
                val coordBlocks = coordBlockRegex.findAll(body).toList()
                coordBlocks.forEach { cb ->
                    val coords = parseCoordList(cb.groupValues[1])
                    when {
                        coords.isEmpty() -> Unit
                        coords.size == 1 -> {
                            val (lon, lat, alt) = coords[0]
                            val (e, n) = UtmGeo.fromLatLon(lat, lon, zone)
                            points += SurveyPoint(name, e, n, alt, "kml")
                            counter++
                        }
                        else -> {
                            val pathPts = coords.mapIndexed { i, (lon, lat, alt) ->
                                val (e, n) = UtmGeo.fromLatLon(lat, lon, zone)
                                SurveyPoint(if (i == 0) name else "${name}_${i + 1}", e, n, alt, "path")
                            }
                            paths += pathPts
                            points += pathPts
                            counter += pathPts.size
                        }
                    }
                }
            }
        }

        return Result(points.distinctBy { "${it.x},${it.y},${it.z}" }, paths, zone)
    }

    /** تبدیل نتیجه KML به DxfModel برای نمایش در نقشه */
    fun toDxfModel(result: Result): DxfModel {
        val layerPts = "KML_POINTS"
        val layerPath = "KML_PATH"
        val layers = linkedMapOf(
            layerPts to DxfLayerInfo(layerPts, 5),
            layerPath to DxfLayerInfo(layerPath, 1)
        )
        val circles = result.points
            .filter { it.code != "path" || result.lines.none { path -> path.any { p -> p.x == it.x && p.y == it.y } } }
            .map { DxfCircle(it.x, it.y, 0.5, layerPts, 5) }
            .ifEmpty {
                // همه نقاط به‌صورت دایره
                result.points.map { DxfCircle(it.x, it.y, 0.5, layerPts, 5) }
            }
        val texts = result.points.map { p ->
            DxfText(p.x, p.y, 1.5, p.id, layerPts, 7)
        }
        val lines = mutableListOf<DxfLine>()
        result.lines.forEach { path ->
            for (i in 0 until path.size - 1) {
                val a = path[i]; val b = path[i + 1]
                lines += DxfLine(a.x, a.y, b.x, b.y, layerPath, 1)
            }
        }
        // اگر فقط نقطه داریم و دایره خالی شد
        val circlesFinal = if (circles.isEmpty() && result.points.isNotEmpty()) {
            result.points.map { DxfCircle(it.x, it.y, 0.5, layerPts, 5) }
        } else circles

        val allX = result.points.map { it.x }
        val allY = result.points.map { it.y }
        if (allX.isEmpty()) {
            return DxfModel(emptyList(), emptyList(), emptyList(), layers, 0.0, 0.0, 1.0, 1.0)
        }
        val pad = 10.0
        return DxfModel(
            lines = lines,
            circles = circlesFinal,
            texts = texts,
            layers = layers,
            minX = allX.minOrNull()!! - pad,
            minY = allY.minOrNull()!! - pad,
            maxX = allX.maxOrNull()!! + pad,
            maxY = allY.maxOrNull()!! + pad
        )
    }

    private fun extractKmlFromKmz(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            var fallback: String? = null
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory && name.endsWith(".kml")) {
                    val text = zis.readBytes().toString(Charsets.UTF_8)
                    if (name.endsWith("doc.kml") || name == "doc.kml") return text
                    if (fallback == null) fallback = text
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            return fallback ?: throw IllegalArgumentException("داخل KMZ فایل KML پیدا نشد")
        }
    }
}
