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
     * مدل کامل ترسیم پروفیل.
     * @param textHeightMeters ارتفاع متن اعداد (پیش‌فرض ۰٫۴ = ۴۰ سانتی‌متر)
     * @param layerName نام لایه انگلیسی (پیش‌فرض PROFILE)
     */
    fun toDxfModel(
        result: ProfileResult2,
        surfaces: List<ProfileSurface>,
        scale: ProfileScale,
        layerName: String = "PROFILE",
        textHeightMeters: Double = 0.4
    ): DxfModel {
        val h = scale.horizontal.coerceAtLeast(1.0)
        val v = scale.vertical.coerceAtLeast(1.0)
        val th = textHeightMeters.coerceAtLeast(0.05)
        val lines = mutableListOf<DxfLine>()
        val texts = mutableListOf<DxfText>()
        val layers = linkedMapOf<String, DxfLayerInfo>()
        fun layer(name: String, color: Int) { layers.putIfAbsent(name, DxfLayerInfo(name, color)) }
        val gridLayer = "PROFILE_GRID"
        val axisLayer = "PROFILE_AXIS"
        val labelLayer = "PROFILE_LABEL"
        val surfaceLayer = layerName
        layer(layerName, 3)
        layer(gridLayer, 8)
        layer(axisLayer, 7)
        layer(labelLayer, 7)

        if (result.rows.isEmpty()) return DxfModel(emptyList(), emptyList(), emptyList(), layers, 0.0, 0.0, 1.0, 1.0)
        val x0 = result.rows.first().chainage / h
        val x1 = result.rows.last().chainage / h
        val zMin = floorTo(result.minElevation, 2.0)
        val zMax = ceilTo(result.maxElevation, 2.0)
        val y0 = zMin / v
        val y1 = zMax / v
        val stationStep = if (result.rows.size > 1) {
            (result.rows[1].chainage - result.rows[0].chainage).coerceAtLeast(0.01)
        } else 10.0
        val xStep = max(0.001, stationStep / h)
        val zStepMeters = max(0.1, stationStep * 2.0)

        // محورهای اصلی (ضخیم‌تر از گرید)
        lines += DxfLine(x0, y0, x1, y0, axisLayer, 7) // محور افقی (ایستگاه)
        lines += DxfLine(x0, y0, x0, y1, axisLayer, 7) // محور قائم (ارتفاع)

        // گرید عمودی + برچسب ایستگاه
        var gx = floorTo(result.rows.first().chainage, stationStep)
        var guard = 0
        while (gx <= result.rows.last().chainage + 1e-9 && guard < 5000) {
            val xx = gx / h
            lines += DxfLine(xx, y0, xx, y1, gridLayer, 8)
            texts += DxfText(xx, y0 - th * 1.5, th, stationLabel(gx), labelLayer, 7)
            gx += stationStep
            guard++
        }
        // گرید افقی + برچسب ارتفاع
        var gz = zMin
        guard = 0
        while (gz <= zMax + 1e-9 && guard < 5000) {
            val yy = gz / v
            lines += DxfLine(x0, yy, x1, yy, gridLayer, 8)
            texts += DxfText(x0 - th * 3.0, yy, th, String.format(java.util.Locale.US, "%.2f", gz), labelLayer, 7)
            gz += zStepMeters
            guard++
        }

        surfaces.forEachIndexed { surfaceIndex, s ->
            val color = if (surfaceIndex == 0) 3 else 1 + (surfaceIndex % 6)
            var previous: Pair<Double, Double>? = null
            result.rows.forEach { row ->
                val z = row.elevations[s.name] ?: run { previous = null; return@forEach }
                val p = row.chainage / h to z / v
                previous?.let { lines += DxfLine(it.first, it.second, p.first, p.second, surfaceLayer, color) }
                previous = p
            }
            val lastZ = result.rows.asReversed().firstNotNullOfOrNull { it.elevations[s.name] }
            if (lastZ != null) texts += DxfText(x1, lastZ / v + th * 1.2, th, s.name, labelLayer, color)
        }
        texts += DxfText(x0, y1 + th * 3.0, th * 1.2, "PROFILE", labelLayer, 7)
        texts += DxfText(
            x0, y1 + th * 1.2, th,
            "H=1:${h.toInt()}  V=1:${v.toInt()}  VE=${String.format(java.util.Locale.US, "%.2f", scale.verticalExaggeration)}",
            labelLayer, 7
        )
        val model = DxfModel(lines, emptyList(), texts, layers, min(x0, x1), y0 - th * 4.0, max(x0, x1), y1 + th * 6.0)
        return try {
            model.recalculatedBounds()
        } catch (_: Exception) {
            model
        }
    }

    private fun floorTo(v: Double, step: Double): Double = kotlin.math.floor(v / step) * step
    private fun ceilTo(v: Double, step: Double): Double = kotlin.math.ceil(v / step) * step
}
