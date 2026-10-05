package com.adel.assistant.data

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Data used by the longitudinal profile tool. */
data class ProfileSurfaceSlot(val name: String, val points: List<VolPoint>)
data class ProfileSurface(
    val name: String,
    val points: List<VolPoint>,
    val tinPoints: List<VolPoint>,
    val triangles: List<VolTriangle>
)
data class AlignmentVertex(val x: Double, val y: Double)
data class AlignmentStation(val chainage: Double, val x: Double, val y: Double)
data class ProfileSampleRow(
    val chainage: Double,
    val x: Double,
    val y: Double,
    val elevations: Map<String, Double?>
)
data class ProfileResult2(
    val stations: List<AlignmentStation>,
    val rows: List<ProfileSampleRow>,
    val length: Double,
    val minElevation: Double,
    val maxElevation: Double,
    val warnings: List<String>
)

data class ProfileScale(
    val horizontal: Double,
    val vertical: Double
) {
    val verticalExaggeration: Double
        get() = if (vertical > 0.0) horizontal / vertical else 0.0
}

object ProfileEngine {
    fun buildSurface(name: String, points: List<VolPoint>): ProfileSurface? {
        val clean = VolumeEngine.dedupe(points, 0.01).first
        if (clean.size < 3) return null
        val tris = VolumeEngine.buildTin(clean)
        if (tris.isEmpty()) return null
        return ProfileSurface(name.ifBlank { "سطح" }, clean, clean, tris)
    }

    fun polylineLength(vertices: List<AlignmentVertex>): Double {
        var s = 0.0
        for (i in 0 until vertices.size - 1) {
            s += hypot(vertices[i + 1].x - vertices[i].x, vertices[i + 1].y - vertices[i].y)
        }
        return s
    }

    fun stations(vertices: List<AlignmentVertex>, step: Double, startChainage: Double = 0.0): List<AlignmentStation> {
        if (vertices.size < 2) return emptyList()
        val cleanStep = step.coerceAtLeast(0.01)
        val out = mutableListOf<AlignmentStation>()
        var accumulated = 0.0
        var next = 0.0
        for (seg in 0 until vertices.size - 1) {
            val a = vertices[seg]
            val b = vertices[seg + 1]
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len < 1e-9) continue
            while (next <= accumulated + len + 1e-9) {
                val d = (next - accumulated).coerceIn(0.0, len)
                val t = d / len
                out += AlignmentStation(
                    startChainage + next,
                    a.x + (b.x - a.x) * t,
                    a.y + (b.y - a.y) * t
                )
                next += cleanStep
            }
            accumulated += len
        }
        if (out.isEmpty() || abs(out.last().chainage - (startChainage + accumulated)) > 1e-7) {
            val b = vertices.last()
            out += AlignmentStation(startChainage + accumulated, b.x, b.y)
        }
        return out.distinctBy { (it.chainage * 1_000_000.0).toLong() }
    }

    fun sample(
        vertices: List<AlignmentVertex>,
        surfaces: List<ProfileSurface>,
        step: Double,
        startChainage: Double = 0.0
    ): ProfileResult2 {
        val ss = stations(vertices, step, startChainage)
        if (ss.isEmpty()) {
            return ProfileResult2(emptyList(), emptyList(), 0.0, 0.0, 0.0, listOf("الایمنت حداقل ۲ رأس لازم دارد"))
        }
        val rows = ss.map { st ->
            val elevations = surfaces.associate { s ->
                s.name to VolumeEngine.tinZAt(st.x, st.y, s.tinPoints, s.triangles)
            }
            ProfileSampleRow(st.chainage, st.x, st.y, elevations)
        }
        val z = rows.flatMap { it.elevations.values.filterNotNull() }
        val warnings = mutableListOf<String>()
        surfaces.forEach { s ->
            val count = rows.count { it.elevations[s.name] != null }
            if (count < rows.size) warnings += "سطح «${s.name}» در $count از ${rows.size} ایستگاه ارتفاع دارد."
        }
        if (z.isEmpty()) warnings += "هیچ ایستگاهی داخل TIN سطح‌ها قرار نگرفت."
        return ProfileResult2(
            ss, rows, polylineLength(vertices), z.minOrNull() ?: 0.0,
            z.maxOrNull() ?: 0.0, warnings
        )
    }

    fun stationLabel(ch: Double): String {
        val m = max(0.0, ch)
        val major = (m / 1000.0).toInt()
        val rest = m - major * 1000.0
        return String.format(java.util.Locale.US, "%d+%06.2f", major, rest)
    }

    fun csv(result: ProfileResult2, surfaces: List<ProfileSurface>): String = buildString {
        append("Station,X,Y")
        surfaces.forEach { append(",\"${it.name.replace("\"", "'")}\"") }
        append("\n")
        result.rows.forEach { r ->
            append("${stationLabel(r.chainage)},${r.x},${r.y}")
            surfaces.forEach { s -> append(",${r.elevations[s.name] ?: ""}") }
            append("\n")
        }
    }

    /**
     * DXF پروفیل + خط الایمنت با کیلومتربندی عمود بر مسیر.
     * @param alignment رأس‌های الایمنت در مختصات واقعی (اختیاری)
     * @param stationInterval فاصله کیلومتربندی روی الایمنت (متر)
     * @param startChainage کیلومتر شروع
     * @param stationTextSize ارتفاع متن کیلومتر روی نقشه
     * @param profileTextSize ارتفاع متن روی شیت پروفیل
     */
    fun toDxfModel(
        result: ProfileResult2,
        surfaces: List<ProfileSurface>,
        scale: ProfileScale,
        layerName: String = "PROFILE",
        alignment: List<AlignmentVertex> = emptyList(),
        stationInterval: Double = 10.0,
        startChainage: Double = 0.0,
        stationTextSize: Double = 1.0,
        profileTextSize: Double = 1.5,
        titleTextSize: Double = 2.0,
        levelTextSize: Double = 1.5,
        chainageFrom: Double? = null,
        chainageTo: Double? = null
    ): DxfModel {
        val h = scale.horizontal.coerceAtLeast(1.0)
        val v = scale.vertical.coerceAtLeast(1.0)
        val lines = mutableListOf<DxfLine>()
        val texts = mutableListOf<DxfText>()
        val layers = linkedMapOf<String, DxfLayerInfo>()
        fun layer(name: String, color: Int) { layers.putIfAbsent(name, DxfLayerInfo(name, color)) }

        layer("ALIGN", 1)
        layer("STATION", 7)
        layer("PROFILE", 3)
        layer("PROFILE2", 5)
        layer("GRID", 8)
        layer("LABEL", 7)

        // ---- 1) خط الایمنت + کیلومتربندی عمود بر مسیر (مختصات واقعی) ----
        if (alignment.size >= 2) {
            for (i in 0 until alignment.size - 1) {
                val a = alignment[i]
                val b = alignment[i + 1]
                lines += DxfLine(a.x, a.y, b.x, b.y, "ALIGN", 1)
            }
            val step = stationInterval.coerceAtLeast(0.01)
            val stations = stations(alignment, step, startChainage)
            stations.forEach { st ->
                // جهت مماس
                val idx = findSegmentIndex(alignment, st.x, st.y)
                val (dx, dy) = segmentDirection(alignment, idx)
                val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-9)
                val ux = dx / len
                val uy = dy / len
                // عمود
                val px = -uy
                val py = ux
                val tick = 1.5
                lines += DxfLine(
                    st.x - px * tick, st.y - py * tick,
                    st.x + px * tick, st.y + py * tick,
                    "STATION", 7
                )
                val ang = Math.toDegrees(kotlin.math.atan2(uy, ux))
                // متن عمود بر مسیر: زاویه مماس + 90
                val textAng = ang + 90.0
                val off = 2.0 + stationTextSize
                texts += DxfText(
                    st.x + px * off,
                    st.y + py * off,
                    stationTextSize.coerceAtLeast(0.2),
                    stationLabel(st.chainage),
                    "STATION",
                    7,
                    textAng
                )
            }
        }

        // ---- 2) شیت پروفیل (همه سطوح) ----
        if (result.rows.isNotEmpty()) {
            val rowsAll = result.rows
            val rows = when {
                chainageFrom != null && chainageTo != null -> {
                    val a = minOf(chainageFrom, chainageTo)
                    val b = maxOf(chainageFrom, chainageTo)
                    rowsAll.filter { it.chainage + 1e-9 >= a && it.chainage - 1e-9 <= b }
                }
                chainageFrom != null -> rowsAll.filter { it.chainage + 1e-9 >= chainageFrom }
                chainageTo != null -> rowsAll.filter { it.chainage - 1e-9 <= chainageTo }
                else -> rowsAll
            }
            if (rows.isEmpty()) {
                // محدوده خالی — فقط الایمنت (اگر بود) برمی‌گردد
            } else {
            val x0 = rows.first().chainage / h
            val x1 = rows.last().chainage / h
            val zMin = floorTo(result.minElevation, 2.0)
            val zMax = ceilTo(result.maxElevation, 2.0)
            val y0 = zMin / v
            val y1 = zMax / v
            val stationStep = if (rows.size > 1) rows[1].chainage - rows[0].chainage else 10.0
            val xStep = max(0.001, stationStep / h)
            val zStepMeters = max(0.1, stationStep * 2.0)
            val pText = profileTextSize.coerceAtLeast(0.3)

            var gx = floorTo(rows.first().chainage, max(0.01, stationStep))
            while (gx <= rows.last().chainage + 1e-9) {
                val xx = gx / h
                lines += DxfLine(xx, y0, xx, y1, "GRID", 8)
                texts += DxfText(xx, y0 - 2.5 / v, pText / v * h * 0.15, stationLabel(gx), "LABEL", 7)
                gx += xStep * h
            }
            var gz = zMin
            var guard = 0
            while (gz <= zMax + 1e-9 && guard < 80) {
                guard++
                val yy = gz / v
                lines += DxfLine(x0, yy, x1, yy, "GRID", 8)
                texts += DxfText(x0 - 8.0 / h, yy, levelTextSize.coerceAtLeast(0.3), String.format(java.util.Locale.US, "%.2f", gz), "LABEL", 7)
                gz += zStepMeters
            }

            // هر دو (همه) خطوط پروفیل سطوح
            surfaces.forEachIndexed { surfaceIndex, s ->
                val color = if (surfaceIndex == 0) 3 else 1 + (surfaceIndex % 6)
                val sLayer = if (surfaceIndex == 0) "PROFILE" else "PROFILE2"
                layer(sLayer, color)
                var previous: Pair<Double, Double>? = null
                rows.forEach { row ->
                    val z = row.elevations[s.name] ?: run { previous = null; return@forEach }
                    val pt = row.chainage / h to z / v
                    previous?.let { lines += DxfLine(it.first, it.second, pt.first, pt.second, sLayer, color) }
                    previous = pt
                }
                val lastZ = rows.asReversed().firstNotNullOfOrNull { it.elevations[s.name] }
                if (lastZ != null) {
                    texts += DxfText(x1, lastZ / v + 3.0 / v, pText, s.name, "LABEL", color)
                }
            }
            texts += DxfText(x0, y1 + 4.0 / v, titleTextSize.coerceAtLeast(0.3), "PROFILE", "LABEL", 7)
            texts += DxfText(
                x0, y1 + 1.0 / v, pText * 0.8,
                "H=1:${h.toInt()}  V=1:${v.toInt()}",
                "LABEL", 7
            )
            } // end rows not empty filtered
        }

        val model = DxfModel(lines, emptyList(), texts, layers, 0.0, 0.0, 1.0, 1.0)
        return model.recalculatedBounds()
    }

    private fun findSegmentIndex(vertices: List<AlignmentVertex>, x: Double, y: Double): Int {
        var best = 0
        var bestD = Double.MAX_VALUE
        for (i in 0 until vertices.size - 1) {
            val a = vertices[i]
            val b = vertices[i + 1]
            val mx = (a.x + b.x) / 2
            val my = (a.y + b.y) / 2
            val d = (mx - x) * (mx - x) + (my - y) * (my - y)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun segmentDirection(vertices: List<AlignmentVertex>, index: Int): Pair<Double, Double> {
        val i = index.coerceIn(0, (vertices.size - 2).coerceAtLeast(0))
        val a = vertices[i]
        val b = vertices.getOrElse(i + 1) { a }
        return (b.x - a.x) to (b.y - a.y)
    }

    private fun floorTo(v: Double, step: Double): Double = kotlin.math.floor(v / step) * step
    private fun ceilTo(v: Double, step: Double): Double = kotlin.math.ceil(v / step) * step
}
