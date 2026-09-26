package com.adel.assistant.data

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** هسته پروفیل طولی: سطح‌ها از TIN واقعی نمونه‌برداری می‌شوند. */
data class ProfileSurface(
    val id: String,
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

object ProfileEngine {
    fun buildSurface(name: String, points: List<VolPoint>): ProfileSurface? {
        val clean = VolumeEngine.dedupe(points, 0.01).first
        if (clean.size < 3) return null
        val tris = VolumeEngine.buildTin(clean)
        if (tris.isEmpty()) return null
        return ProfileSurface(name, name, clean, clean, tris)
    }

    fun polylineLength(vertices: List<AlignmentVertex>): Double {
        var s = 0.0
        for (i in 0 until vertices.size - 1) s += hypot(vertices[i + 1].x - vertices[i].x, vertices[i + 1].y - vertices[i].y)
        return s
    }

    fun stations(vertices: List<AlignmentVertex>, step: Double, startChainage: Double = 0.0): List<AlignmentStation> {
        if (vertices.size < 2) return emptyList()
        val cleanStep = step.coerceAtLeast(0.01)
        val out = mutableListOf<AlignmentStation>()
        var accumulated = 0.0
        var next = 0.0
        for (seg in 0 until vertices.size - 1) {
            val a = vertices[seg]; val b = vertices[seg + 1]
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len < 1e-9) continue
            while (next <= accumulated + len + 1e-9) {
                val d = (next - accumulated).coerceIn(0.0, len)
                val t = d / len
                out += AlignmentStation(startChainage + next, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                next += cleanStep
                if (next > accumulated + len + 1e-9) break
            }
            accumulated += len
        }
        val total = accumulated
        if (out.isEmpty() || abs(out.last().chainage - (startChainage + total)) > 1e-7) {
            val b = vertices.last(); out += AlignmentStation(startChainage + total, b.x, b.y)
        }
        return out.distinctBy { round(it.chainage * 1000000.0) }
    }

    fun sample(vertices: List<AlignmentVertex>, surfaces: List<ProfileSurface>, step: Double, startChainage: Double = 0.0): ProfileResult2 {
        val ss = stations(vertices, step, startChainage)
        if (ss.isEmpty()) return ProfileResult2(emptyList(), emptyList(), 0.0, 0.0, 0.0, listOf("Alignment حداقل ۲ رأس لازم دارد"))
        val rows = ss.map { st ->
            val elevations = surfaces.associate { s -> s.name to VolumeEngine.tinZAt(st.x, st.y, s.tinPoints, s.triangles) }
            ProfileSampleRow(st.chainage, st.x, st.y, elevations)
        }
        val z = rows.flatMap { it.elevations.values.filterNotNull() }
        val warnings = mutableListOf<String>()
        surfaces.forEach { s ->
            val count = rows.count { it.elevations[s.name] != null }
            if (count < rows.size) warnings += "سطح «${s.name}» در $count از ${rows.size} ایستگاه ارتفاع دارد. خارج از محدوده TIN باطل است."
        }
        if (z.isEmpty()) warnings += "هیچ ایستگاهی داخل TIN سطح‌ها قرار نگرفت."
        return ProfileResult2(ss, rows, polylineLength(vertices), z.minOrNull() ?: 0.0, z.maxOrNull() ?: 0.0, warnings)
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
}
