package com.adel.assistant.data

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class VolPoint(
    val id: String,
    val x: Double,
    val y: Double,
    val z: Double
)

data class VolTriangle(
    val a: Int,
    val b: Int,
    val c: Int
)

data class VolumeResult(
    val method: String,
    val existingCount: Int,
    val designCount: Int,
    val cutM3: Double,
    val fillM3: Double,
    val netM3: Double,
    val areaM2: Double,
    val minDz: Double,
    val maxDz: Double,
    val avgDz: Double,
    val cellOrTriCount: Int,
    val gridSize: Double?,
    val warnings: List<String>
) {
    val adjustedCut: Double get() = cutM3
    val adjustedFill: Double get() = fillM3
}

/**
 * محاسبه احجام خاکبرداری/خاکریزی
 * - Grid: نمونه‌برداری روی شبکه مشترک + حجم سلول‌به‌سلول
 * - TIN: مثلث‌بندی Delaunay ساده + حجم مثلث‌به‌مثلث با میانگین ΔZ
 */
object VolumeEngine {

    private const val EPS = 1e-12

    fun dedupe(points: List<VolPoint>, tol: Double = 0.01): Pair<List<VolPoint>, Int> {
        if (points.isEmpty()) return emptyList<VolPoint>() to 0
        val sorted = points.sortedWith(compareBy({ it.x }, { it.y }, { it.z }))
        val out = mutableListOf<VolPoint>()
        var removed = 0
        for (p in sorted) {
            val last = out.lastOrNull()
            if (last != null &&
                abs(last.x - p.x) <= tol &&
                abs(last.y - p.y) <= tol
            ) {
                removed++
                // نگه داشتن میانگین Z
                out[out.lastIndex] = last.copy(z = (last.z + p.z) / 2.0)
            } else {
                out.add(p)
            }
        }
        return out to removed
    }

    fun bounds(points: List<VolPoint>): DoubleArray {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return doubleArrayOf(minX, minY, maxX, maxY)
    }

    /** Convex hull (Andrew's monotone chain) — خروجی به‌صورت حلقه بسته نیست؛ فقط رأس‌ها */
    fun convexHull(points: List<VolPoint>): List<VolPoint> {
        if (points.size <= 2) return points
        val pts = points.sortedWith(compareBy({ it.x }, { it.y }))
        fun cross(o: VolPoint, a: VolPoint, b: VolPoint) =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = mutableListOf<VolPoint>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) {
                lower.removeAt(lower.lastIndex)
            }
            lower.add(p)
        }
        val upper = mutableListOf<VolPoint>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) {
                upper.removeAt(upper.lastIndex)
            }
            upper.add(p)
        }
        lower.removeAt(lower.lastIndex)
        upper.removeAt(upper.lastIndex)
        return lower + upper
    }

    fun pointInPolygon(x: Double, y: Double, poly: List<VolPoint>): Boolean {
        if (poly.size < 3) return true
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = poly[i].x
            val yi = poly[i].y
            val xj = poly[j].x
            val yj = poly[j].y
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { abs(it) > EPS } ?: EPS) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    /** درون‌یابی IDW از نزدیک‌ترین k نقطه */
    fun interpolateIdw(x: Double, y: Double, pts: List<VolPoint>, k: Int = 6, power: Double = 2.0): Double? {
        if (pts.isEmpty()) return null
        val nearest = pts.map { p ->
            val d2 = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y)
            p to d2
        }.sortedBy { it.second }.take(k)
        val exact = nearest.firstOrNull { it.second < 1e-16 }
        if (exact != null) return exact.first.z
        var num = 0.0
        var den = 0.0
        for ((p, d2) in nearest) {
            val d = sqrt(d2).coerceAtLeast(1e-9)
            val w = 1.0 / Math.pow(d, power)
            num += w * p.z
            den += w
        }
        return if (den > 0) num / den else null
    }

    /**
     * محاسبه Grid
     * در هر سلول: ΔZ = Z_existing - Z_design
     * ΔZ > 0 → Cut ، ΔZ < 0 → Fill
     * V_cell = cellArea * ΔZ
     */
    fun computeGrid(
        existing: List<VolPoint>,
        design: List<VolPoint>,
        gridSize: Double,
        boundary: List<VolPoint>? = null,
        cutFactor: Double = 1.0,
        fillFactor: Double = 1.0
    ): VolumeResult {
        val warnings = mutableListOf<String>()
        val (ex, remEx) = dedupe(existing)
        val (de, remDe) = dedupe(design)
        if (remEx > 0) warnings.add("$remEx نقطه تکراری سطح موجود ادغام شد")
        if (remDe > 0) warnings.add("$remDe نقطه تکراری سطح طراحی ادغام شد")
        if (ex.size < 3) warnings.add("سطح موجود نقاط کافی ندارد")
        if (de.size < 3) warnings.add("سطح طراحی نقاط کافی ندارد")
        if (ex.size < 3 || de.size < 3) {
            return VolumeResult("Grid", ex.size, de.size, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, gridSize, warnings)
        }

        val all = ex + de
        val b = bounds(all)
        val hull = boundary ?: convexHull(all)
        val gs = gridSize.coerceAtLeast(0.1)
        val x0 = b[0]
        val y0 = b[1]
        val x1 = b[2]
        val y1 = b[3]

        // Origin محلی برای پایداری عددی
        val ox = (x0 + x1) / 2.0
        val oy = (y0 + y1) / 2.0

        var cut = 0.0
        var fill = 0.0
        var area = 0.0
        var sumDz = 0.0
        var minDz = Double.POSITIVE_INFINITY
        var maxDz = Double.NEGATIVE_INFINITY
        var n = 0
        val cellArea = gs * gs

        var x = x0
        while (x < x1 - 1e-9) {
            var y = y0
            while (y < y1 - 1e-9) {
                val cx = x + gs / 2.0
                val cy = y + gs / 2.0
                if (pointInPolygon(cx, cy, hull)) {
                    val ze = interpolateIdw(cx, cy, ex) ?: continue
                    val zd = interpolateIdw(cx, cy, de) ?: continue
                    val dz = ze - zd
                    if (dz > 0) cut += cellArea * dz
                    else if (dz < 0) fill += cellArea * abs(dz)
                    area += cellArea
                    sumDz += dz
                    if (dz < minDz) minDz = dz
                    if (dz > maxDz) maxDz = dz
                    n++
                }
                y += gs
            }
            x += gs
        }

        if (n == 0) warnings.add("هیچ سلول مشترکی در محدوده محاسبه نشد")
        if (n in 1..20) warnings.add("تعداد سلول کم است — Grid Size را کوچک‌تر کنید")

        val avg = if (n > 0) sumDz / n else 0.0
        val cutAdj = cut * cutFactor
        val fillAdj = fill * fillFactor
        return VolumeResult(
            method = "Grid",
            existingCount = ex.size,
            designCount = de.size,
            cutM3 = cutAdj,
            fillM3 = fillAdj,
            netM3 = cutAdj - fillAdj,
            areaM2 = area,
            minDz = if (n > 0) minDz else 0.0,
            maxDz = if (n > 0) maxDz else 0.0,
            avgDz = avg,
            cellOrTriCount = n,
            gridSize = gs,
            warnings = warnings
        )
    }

    // ---------- TIN (Bowyer–Watson ساده) ----------

    private data class Tri(var a: Int, var b: Int, var c: Int)

    fun buildTin(points: List<VolPoint>): List<VolTriangle> {
        if (points.size < 3) return emptyList()
        val n = points.size
        val b = bounds(points)
        val dx = (b[2] - b[0]).coerceAtLeast(1.0)
        val dy = (b[3] - b[1]).coerceAtLeast(1.0)
        val dmax = max(dx, dy) * 10.0
        val midX = (b[0] + b[2]) / 2.0
        val midY = (b[1] + b[3]) / 2.0
        // super-triangle
        val p0 = VolPoint("_st0", midX - 2 * dmax, midY - dmax, 0.0)
        val p1 = VolPoint("_st1", midX, midY + 2 * dmax, 0.0)
        val p2 = VolPoint("_st2", midX + 2 * dmax, midY - dmax, 0.0)
        val pts = points + listOf(p0, p1, p2)
        val stA = n
        val stB = n + 1
        val stC = n + 2
        val tris = mutableListOf(Tri(stA, stB, stC))

        fun circumcircle(t: Tri): DoubleArray {
            val ax = pts[t.a].x; val ay = pts[t.a].y
            val bx = pts[t.b].x; val by = pts[t.b].y
            val cx = pts[t.c].x; val cy = pts[t.c].y
            val d = 2.0 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
            if (abs(d) < 1e-18) return doubleArrayOf(ax, ay, 1e18)
            val ux = ((ax * ax + ay * ay) * (by - cy) + (bx * bx + by * by) * (cy - ay) + (cx * cx + cy * cy) * (ay - by)) / d
            val uy = ((ax * ax + ay * ay) * (cx - bx) + (bx * bx + by * by) * (ax - cx) + (cx * cx + cy * cy) * (bx - ax)) / d
            val r2 = (ux - ax) * (ux - ax) + (uy - ay) * (uy - ay)
            return doubleArrayOf(ux, uy, r2)
        }

        for (i in 0 until n) {
            val px = pts[i].x
            val py = pts[i].y
            val bad = mutableListOf<Tri>()
            for (t in tris) {
                val cc = circumcircle(t)
                val d2 = (px - cc[0]) * (px - cc[0]) + (py - cc[1]) * (py - cc[1])
                if (d2 <= cc[2] + 1e-12) bad.add(t)
            }
            // edge count for boundary of polygonal hole
            data class Edge(val u: Int, val v: Int)
            fun norm(u: Int, v: Int) = if (u < v) Edge(u, v) else Edge(v, u)
            val edgeCount = mutableMapOf<Edge, Int>()
            for (t in bad) {
                listOf(norm(t.a, t.b), norm(t.b, t.c), norm(t.c, t.a)).forEach { e ->
                    edgeCount[e] = (edgeCount[e] ?: 0) + 1
                }
            }
            tris.removeAll(bad.toSet())
            for ((e, cnt) in edgeCount) {
                if (cnt == 1) {
                    tris.add(Tri(e.u, e.v, i))
                }
            }
        }

        return tris.filter { t ->
            t.a < n && t.b < n && t.c < n
        }.map { VolTriangle(it.a, it.b, it.c) }
    }

    fun triangleArea2d(p: VolPoint, q: VolPoint, r: VolPoint): Double {
        return abs((q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)) / 2.0
    }

    /** ارتفاع سطح TIN در (x,y) با barycentric — null اگر خارج باشد */
    fun tinZAt(x: Double, y: Double, pts: List<VolPoint>, tris: List<VolTriangle>): Double? {
        for (t in tris) {
            val a = pts[t.a]
            val b = pts[t.b]
            val c = pts[t.c]
            val area = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
            if (abs(area) < 1e-18) continue
            val w0 = ((b.x - x) * (c.y - y) - (b.y - y) * (c.x - x)) / area
            val w1 = ((c.x - x) * (a.y - y) - (c.y - y) * (a.x - x)) / area
            val w2 = 1.0 - w0 - w1
            if (w0 >= -1e-8 && w1 >= -1e-8 && w2 >= -1e-8) {
                return w0 * a.z + w1 * b.z + w2 * c.z
            }
        }
        return null
    }

    /**
     * TIN Difference تقریبی:
     * مثلث‌های سطح موجود را مبنا می‌گیرد؛ در هر رأس ΔZ با درون‌یابی سطح طراحی
     * و اگر علامت‌ها یکسان بود V = A*(dz1+dz2+dz3)/3
     * اگر مخلوط بود، به زیرمثلث‌های علامت‌دار تقسیم ساده می‌شود.
     */
    fun computeTin(
        existing: List<VolPoint>,
        design: List<VolPoint>,
        boundary: List<VolPoint>? = null,
        cutFactor: Double = 1.0,
        fillFactor: Double = 1.0
    ): VolumeResult {
        val warnings = mutableListOf<String>()
        val (ex, remEx) = dedupe(existing)
        val (de, remDe) = dedupe(design)
        if (remEx > 0) warnings.add("$remEx نقطه تکراری سطح موجود ادغام شد")
        if (remDe > 0) warnings.add("$remDe نقطه تکراری سطح طراحی ادغام شد")
        if (ex.size < 3 || de.size < 3) {
            warnings.add("برای TIN حداقل ۳ نقطه در هر سطح لازم است")
            return VolumeResult("TIN", ex.size, de.size, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, null, warnings)
        }
        if (ex.size > 2500 || de.size > 2500) {
            warnings.add("تعداد نقاط زیاد است — برای سرعت از Grid استفاده کنید")
        }

        val trisEx = buildTin(ex)
        val trisDe = buildTin(de)
        if (trisEx.isEmpty()) {
            warnings.add("ساخت TIN سطح موجود ناموفق بود")
            return VolumeResult("TIN", ex.size, de.size, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, null, warnings)
        }

        val hull = boundary ?: convexHull(ex + de)
        var cut = 0.0
        var fill = 0.0
        var area = 0.0
        var sumDz = 0.0
        var minDz = Double.POSITIVE_INFINITY
        var maxDz = Double.NEGATIVE_INFINITY
        var used = 0

        fun designZ(x: Double, y: Double): Double? {
            return tinZAt(x, y, de, trisDe) ?: interpolateIdw(x, y, de)
        }

        for (t in trisEx) {
            val p1 = ex[t.a]
            val p2 = ex[t.b]
            val p3 = ex[t.c]
            val cx = (p1.x + p2.x + p3.x) / 3.0
            val cy = (p1.y + p2.y + p3.y) / 3.0
            if (!pointInPolygon(cx, cy, hull)) continue
            val z1d = designZ(p1.x, p1.y) ?: continue
            val z2d = designZ(p2.x, p2.y) ?: continue
            val z3d = designZ(p3.x, p3.y) ?: continue
            val dz1 = p1.z - z1d
            val dz2 = p2.z - z2d
            val dz3 = p3.z - z3d
            val a = triangleArea2d(p1, p2, p3)
            if (a < 1e-12) continue

            val signs = listOf(dz1, dz2, dz3).map {
                when {
                    it > 1e-9 -> 1
                    it < -1e-9 -> -1
                    else -> 0
                }
            }
            val hasPos = signs.any { it > 0 }
            val hasNeg = signs.any { it < 0 }

            if (!hasPos || !hasNeg) {
                val avg = (dz1 + dz2 + dz3) / 3.0
                val v = a * avg
                if (v > 0) cut += v else fill += abs(v)
                area += a
                sumDz += avg
                minDz = min(minDz, minOf(dz1, dz2, dz3))
                maxDz = max(maxDz, maxOf(dz1, dz2, dz3))
                used++
            } else {
                // تقسیم ساده: سهم هر رأس با وزن مساحت یکسان تقریبی
                // دقیق‌تر: سه زیرمثلث به مرکز با ΔZ رأس
                val davg = (dz1 + dz2 + dz3) / 3.0
                val parts = listOf(dz1, dz2, dz3)
                for (dz in parts) {
                    val subA = a / 3.0
                    val v = subA * dz
                    if (v > 0) cut += v else fill += abs(v)
                }
                area += a
                sumDz += davg
                minDz = min(minDz, minOf(dz1, dz2, dz3))
                maxDz = max(maxDz, maxOf(dz1, dz2, dz3))
                used++
            }
        }

        if (used == 0) warnings.add("هیچ مثلث مشترکی محاسبه نشد")
        val avg = if (used > 0) sumDz / used else 0.0
        val cutAdj = cut * cutFactor
        val fillAdj = fill * fillFactor
        return VolumeResult(
            method = "TIN",
            existingCount = ex.size,
            designCount = de.size,
            cutM3 = cutAdj,
            fillM3 = fillAdj,
            netM3 = cutAdj - fillAdj,
            areaM2 = area,
            minDz = if (used > 0) minDz else 0.0,
            maxDz = if (used > 0) maxDz else 0.0,
            avgDz = avg,
            cellOrTriCount = used,
            gridSize = null,
            warnings = warnings
        )
    }

    fun reportText(
        name: String,
        result: VolumeResult,
        cutFactor: Double,
        fillFactor: Double
    ): String {
        val sb = StringBuilder()
        sb.appendLine("گزارش محاسبه احجام")
        sb.appendLine("نام: $name")
        sb.appendLine("روش: ${result.method}")
        sb.appendLine("نقاط موجود: ${result.existingCount}")
        sb.appendLine("نقاط طراحی: ${result.designCount}")
        sb.appendLine("مساحت محاسبه (m²): ${fmt(result.areaM2)}")
        if (result.gridSize != null) sb.appendLine("اندازه شبکه (m): ${fmt(result.gridSize)}")
        sb.appendLine("تعداد سلول/مثلث: ${result.cellOrTriCount}")
        sb.appendLine("Cut خام (m³): ${fmt(result.cutM3 / cutFactor.coerceAtLeast(1e-9))}")
        sb.appendLine("Fill خام (m³): ${fmt(result.fillM3 / fillFactor.coerceAtLeast(1e-9))}")
        sb.appendLine("Cut با ضریب (m³): ${fmt(result.cutM3)}")
        sb.appendLine("Fill با ضریب (m³): ${fmt(result.fillM3)}")
        sb.appendLine("Net (m³): ${fmt(result.netM3)}")
        sb.appendLine("ضریب Cut: $cutFactor")
        sb.appendLine("ضریب Fill: $fillFactor")
        sb.appendLine("حداقل ΔZ (m): ${fmt(result.minDz)}")
        sb.appendLine("حداکثر ΔZ (m): ${fmt(result.maxDz)}")
        sb.appendLine("میانگین ΔZ (m): ${fmt(result.avgDz)}")
        if (result.warnings.isNotEmpty()) {
            sb.appendLine("هشدارها:")
            result.warnings.forEach { sb.appendLine(" - $it") }
        }
        return sb.toString()
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.3f", v)

    fun fromSurvey(points: List<SurveyPoint>): List<VolPoint> =
        points.map { VolPoint(it.id.ifBlank { "P" }, it.x, it.y, it.z) }
}
