package com.adel.assistant.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
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

/** یک پاره از خط تراز */
data class ContourSeg(
    val x1: Double, val y1: Double, val z: Double,
    val x2: Double, val y2: Double
)

data class ContourSet(
    val surfaceName: String,
    val interval: Double,
    val segments: List<ContourSeg>,
    /** نقاط برچسب تراز (وسط هر سگمنت مهم) */
    val labels: List<Triple<Double, Double, Double>> // x,y,elevation
)

data class VolumeResult(
    val method: String,
    val existingCount: Int,
    val designCount: Int,
    val existingName: String = "موجود",
    val designName: String = "طراحی",
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
    /** نتیجه عملیات خاکی */
    val earthworkLabel: String
        get() = when {
            cutM3 > fillM3 + 1e-6 -> "خاکبرداری"
            fillM3 > cutM3 + 1e-6 -> "خاکریزی"
            else -> "متعادل"
        }
}

/**
 * محاسبه احجام + خطوط تراز + خروجی DXF لایه‌بندی‌شده
 */
object VolumeEngine {

    private const val EPS = 1e-12
    private const val CRLF = "\r\n"

    fun dedupe(points: List<VolPoint>, tol: Double = 0.01): Pair<List<VolPoint>, Int> {
        if (points.isEmpty()) return emptyList<VolPoint>() to 0
        val sorted = points.sortedWith(compareBy({ it.x }, { it.y }, { it.z }))
        val out = mutableListOf<VolPoint>()
        var removed = 0
        for (p in sorted) {
            val last = out.lastOrNull()
            if (last != null && abs(last.x - p.x) <= tol && abs(last.y - p.y) <= tol) {
                removed++
                out[out.lastIndex] = last.copy(z = (last.z + p.z) / 2.0)
            } else out.add(p)
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

    fun convexHull(points: List<VolPoint>): List<VolPoint> {
        if (points.size <= 2) return points
        val pts = points.sortedWith(compareBy({ it.x }, { it.y }))
        fun cross(o: VolPoint, a: VolPoint, b: VolPoint) =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = mutableListOf<VolPoint>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0)
                lower.removeAt(lower.lastIndex)
            lower.add(p)
        }
        val upper = mutableListOf<VolPoint>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0)
                upper.removeAt(upper.lastIndex)
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
            val xi = poly[i].x; val yi = poly[i].y
            val xj = poly[j].x; val yj = poly[j].y
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { abs(it) > EPS } ?: EPS) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    fun interpolateIdw(x: Double, y: Double, pts: List<VolPoint>, k: Int = 6, power: Double = 2.0): Double? {
        if (pts.isEmpty()) return null
        val nearest = pts.map { p ->
            val d2 = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y)
            p to d2
        }.sortedBy { it.second }.take(k)
        val exact = nearest.firstOrNull { it.second < 1e-16 }
        if (exact != null) return exact.first.z
        var num = 0.0; var den = 0.0
        for ((p, d2) in nearest) {
            val d = sqrt(d2).coerceAtLeast(1e-9)
            val w = 1.0 / Math.pow(d, power)
            num += w * p.z; den += w
        }
        return if (den > 0) num / den else null
    }

    fun computeGrid(
        existing: List<VolPoint>,
        design: List<VolPoint>,
        gridSize: Double,
        boundary: List<VolPoint>? = null,
        cutFactor: Double = 1.0,
        fillFactor: Double = 1.0,
        existingName: String = "موجود",
        designName: String = "طراحی"
    ): VolumeResult {
        val warnings = mutableListOf<String>()
        val (ex, remEx) = dedupe(existing)
        val (de, remDe) = dedupe(design)
        if (remEx > 0) warnings.add("$remEx نقطه تکراری «$existingName» ادغام شد")
        if (remDe > 0) warnings.add("$remDe نقطه تکراری «$designName» ادغام شد")
        if (ex.size < 3 || de.size < 3) {
            warnings.add("هر سطح حداقل ۳ نقطه نیاز دارد")
            return VolumeResult("Grid", ex.size, de.size, existingName, designName, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, gridSize, warnings)
        }
        val all = ex + de
        val b = bounds(all)
        val hull = boundary ?: convexHull(all)
        val gs = gridSize.coerceAtLeast(0.1)
        var cut = 0.0; var fill = 0.0; var area = 0.0; var sumDz = 0.0
        var minDz = Double.POSITIVE_INFINITY; var maxDz = Double.NEGATIVE_INFINITY; var n = 0
        val cellArea = gs * gs
        var x = b[0]
        while (x < b[2] - 1e-9) {
            var y = b[1]
            while (y < b[3] - 1e-9) {
                val cx = x + gs / 2.0; val cy = y + gs / 2.0
                if (pointInPolygon(cx, cy, hull)) {
                    val ze = interpolateIdw(cx, cy, ex) ?: continue
                    val zd = interpolateIdw(cx, cy, de) ?: continue
                    val dz = ze - zd
                    if (dz > 0) cut += cellArea * dz else if (dz < 0) fill += cellArea * abs(dz)
                    area += cellArea; sumDz += dz
                    if (dz < minDz) minDz = dz; if (dz > maxDz) maxDz = dz
                    n++
                }
                y += gs
            }
            x += gs
        }
        if (n == 0) warnings.add("محدوده مشترک خالی است")
        if (n in 1..20) warnings.add("تعداد سلول کم — Grid Size را کوچک‌تر کنید")
        val cutAdj = cut * cutFactor; val fillAdj = fill * fillFactor
        return VolumeResult(
            "Grid", ex.size, de.size, existingName, designName,
            cutAdj, fillAdj, cutAdj - fillAdj, area,
            if (n > 0) minDz else 0.0, if (n > 0) maxDz else 0.0,
            if (n > 0) sumDz / n else 0.0, n, gs, warnings
        )
    }

    // ---------- TIN ----------
    private data class Tri(var a: Int, var b: Int, var c: Int)

    fun buildTin(points: List<VolPoint>): List<VolTriangle> {
        if (points.size < 3) return emptyList()
        val n = points.size
        val b = bounds(points)
        val dx = (b[2] - b[0]).coerceAtLeast(1.0)
        val dy = (b[3] - b[1]).coerceAtLeast(1.0)
        val dmax = max(dx, dy) * 10.0
        val midX = (b[0] + b[2]) / 2.0; val midY = (b[1] + b[3]) / 2.0
        val pts = points + listOf(
            VolPoint("_st0", midX - 2 * dmax, midY - dmax, 0.0),
            VolPoint("_st1", midX, midY + 2 * dmax, 0.0),
            VolPoint("_st2", midX + 2 * dmax, midY - dmax, 0.0)
        )
        val stA = n; val stB = n + 1; val stC = n + 2
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
            val px = pts[i].x; val py = pts[i].y
            val bad = mutableListOf<Tri>()
            for (t in tris) {
                val cc = circumcircle(t)
                if ((px - cc[0]) * (px - cc[0]) + (py - cc[1]) * (py - cc[1]) <= cc[2] + 1e-12) bad.add(t)
            }
            data class Edge(val u: Int, val v: Int)
            fun norm(u: Int, v: Int) = if (u < v) Edge(u, v) else Edge(v, u)
            val edgeCount = mutableMapOf<Edge, Int>()
            for (t in bad) {
                listOf(norm(t.a, t.b), norm(t.b, t.c), norm(t.c, t.a)).forEach { e ->
                    edgeCount[e] = (edgeCount[e] ?: 0) + 1
                }
            }
            tris.removeAll(bad.toSet())
            for ((e, cnt) in edgeCount) if (cnt == 1) tris.add(Tri(e.u, e.v, i))
        }
        return tris.filter { it.a < n && it.b < n && it.c < n }.map { VolTriangle(it.a, it.b, it.c) }
    }

    fun triangleArea2d(p: VolPoint, q: VolPoint, r: VolPoint): Double =
        abs((q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)) / 2.0

    fun tinZAt(x: Double, y: Double, pts: List<VolPoint>, tris: List<VolTriangle>): Double? {
        for (t in tris) {
            val a = pts[t.a]; val b = pts[t.b]; val c = pts[t.c]
            val area = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
            if (abs(area) < 1e-18) continue
            val w0 = ((b.x - x) * (c.y - y) - (b.y - y) * (c.x - x)) / area
            val w1 = ((c.x - x) * (a.y - y) - (c.y - y) * (a.x - x)) / area
            val w2 = 1.0 - w0 - w1
            if (w0 >= -1e-8 && w1 >= -1e-8 && w2 >= -1e-8)
                return w0 * a.z + w1 * b.z + w2 * c.z
        }
        return null
    }

    fun computeTin(
        existing: List<VolPoint>,
        design: List<VolPoint>,
        boundary: List<VolPoint>? = null,
        cutFactor: Double = 1.0,
        fillFactor: Double = 1.0,
        existingName: String = "موجود",
        designName: String = "طراحی"
    ): VolumeResult {
        val warnings = mutableListOf<String>()
        val (ex, remEx) = dedupe(existing)
        val (de, remDe) = dedupe(design)
        if (remEx > 0) warnings.add("$remEx نقطه تکراری «$existingName» ادغام شد")
        if (remDe > 0) warnings.add("$remDe نقطه تکراری «$designName» ادغام شد")
        if (ex.size < 3 || de.size < 3) {
            warnings.add("برای TIN حداقل ۳ نقطه در هر سطح لازم است")
            return VolumeResult("TIN", ex.size, de.size, existingName, designName, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, null, warnings)
        }
        val trisEx = buildTin(ex)
        val trisDe = buildTin(de)
        if (trisEx.isEmpty()) {
            warnings.add("ساخت TIN سطح «$existingName» ناموفق")
            return VolumeResult("TIN", ex.size, de.size, existingName, designName, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, null, warnings)
        }
        val hull = boundary ?: convexHull(ex + de)
        var cut = 0.0; var fill = 0.0; var area = 0.0; var sumDz = 0.0
        var minDz = Double.POSITIVE_INFINITY; var maxDz = Double.NEGATIVE_INFINITY; var used = 0
        fun designZ(x: Double, y: Double) = tinZAt(x, y, de, trisDe) ?: interpolateIdw(x, y, de)
        for (t in trisEx) {
            val p1 = ex[t.a]; val p2 = ex[t.b]; val p3 = ex[t.c]
            val cx = (p1.x + p2.x + p3.x) / 3.0; val cy = (p1.y + p2.y + p3.y) / 3.0
            if (!pointInPolygon(cx, cy, hull)) continue
            val z1d = designZ(p1.x, p1.y) ?: continue
            val z2d = designZ(p2.x, p2.y) ?: continue
            val z3d = designZ(p3.x, p3.y) ?: continue
            val dz1 = p1.z - z1d; val dz2 = p2.z - z2d; val dz3 = p3.z - z3d
            val a = triangleArea2d(p1, p2, p3)
            if (a < 1e-12) continue
            val hasPos = listOf(dz1, dz2, dz3).any { it > 1e-9 }
            val hasNeg = listOf(dz1, dz2, dz3).any { it < -1e-9 }
            if (!hasPos || !hasNeg) {
                val avg = (dz1 + dz2 + dz3) / 3.0
                val v = a * avg
                if (v > 0) cut += v else fill += abs(v)
                area += a; sumDz += avg
                minDz = min(minDz, minOf(dz1, dz2, dz3)); maxDz = max(maxDz, maxOf(dz1, dz2, dz3)); used++
            } else {
                for (dz in listOf(dz1, dz2, dz3)) {
                    val v = (a / 3.0) * dz
                    if (v > 0) cut += v else fill += abs(v)
                }
                area += a; sumDz += (dz1 + dz2 + dz3) / 3.0
                minDz = min(minDz, minOf(dz1, dz2, dz3)); maxDz = max(maxDz, maxOf(dz1, dz2, dz3)); used++
            }
        }
        if (used == 0) warnings.add("هیچ مثلث مشترکی محاسبه نشد")
        val cutAdj = cut * cutFactor; val fillAdj = fill * fillFactor
        return VolumeResult(
            "TIN", ex.size, de.size, existingName, designName,
            cutAdj, fillAdj, cutAdj - fillAdj, area,
            if (used > 0) minDz else 0.0, if (used > 0) maxDz else 0.0,
            if (used > 0) sumDz / used else 0.0, used, null, warnings
        )
    }

    // ---------- Contours ----------

    /**
     * خطوط تراز از TIN سطح.
     * برای هر ارتفاع تراز، روی یال‌های مثلث نقاط تقاطع گرفته و سگمنت می‌سازد.
     */
    fun buildContours(
        surfaceName: String,
        points: List<VolPoint>,
        interval: Double,
        maxLevels: Int = 80
    ): ContourSet {
        val (pts, _) = dedupe(points)
        if (pts.size < 3 || interval <= 0) {
            return ContourSet(surfaceName, interval, emptyList(), emptyList())
        }
        val tris = buildTin(pts)
        if (tris.isEmpty()) return ContourSet(surfaceName, interval, emptyList(), emptyList())

        var zMin = Double.POSITIVE_INFINITY
        var zMax = Double.NEGATIVE_INFINITY
        for (p in pts) {
            if (p.z < zMin) zMin = p.z
            if (p.z > zMax) zMax = p.z
        }
        val iv = interval.coerceAtLeast(0.01)
        var level = floor(zMin / iv) * iv
        if (level < zMin - 1e-9) level += iv

        val segs = mutableListOf<ContourSeg>()
        val labels = mutableListOf<Triple<Double, Double, Double>>()
        var levelCount = 0

        fun edgeHit(a: VolPoint, b: VolPoint, z: Double): Pair<Double, Double>? {
            val za = a.z; val zb = b.z
            if ((za < z && zb < z) || (za > z && zb > z)) return null
            if (abs(za - zb) < 1e-12) return null
            val t = (z - za) / (zb - za)
            if (t < -1e-9 || t > 1.0 + 1e-9) return null
            val tt = t.coerceIn(0.0, 1.0)
            return a.x + tt * (b.x - a.x) to a.y + tt * (b.y - a.y)
        }

        while (level <= zMax + 1e-9 && levelCount < maxLevels) {
            var labeledThis = false
            for (t in tris) {
                val p1 = pts[t.a]; val p2 = pts[t.b]; val p3 = pts[t.c]
                val hits = mutableListOf<Pair<Double, Double>>()
                edgeHit(p1, p2, level)?.let { hits.add(it) }
                edgeHit(p2, p3, level)?.let { hits.add(it) }
                edgeHit(p3, p1, level)?.let { hits.add(it) }
                // دو نقطه متمایز → یک سگمنت
                if (hits.size >= 2) {
                    // حذف تقریباً تکراری
                    val unique = mutableListOf<Pair<Double, Double>>()
                    for (h in hits) {
                        if (unique.none { abs(it.first - h.first) < 1e-6 && abs(it.second - h.second) < 1e-6 })
                            unique.add(h)
                    }
                    if (unique.size >= 2) {
                        val a = unique[0]; val b = unique[1]
                        segs.add(ContourSeg(a.first, a.second, level, b.first, b.second))
                        if (!labeledThis) {
                            labels.add(Triple((a.first + b.first) / 2.0, (a.second + b.second) / 2.0, level))
                            labeledThis = true
                        }
                    }
                }
            }
            level += iv
            levelCount++
        }
        return ContourSet(surfaceName, iv, segs, labels)
    }

    // ---------- DXF export ----------

    private fun pair(sb: StringBuilder, code: Int, value: String) {
        sb.append(code).append(CRLF)
        sb.append(value).append(CRLF)
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.4f", v)

    fun sanitizeLayer(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[^A-Za-z0-9_\\-\\u0600-\\u06FF]"), "_")
            .take(31)
        return if (cleaned.isBlank()) "LAYER0" else cleaned
    }

    /**
     * DXF R12 با لایه‌ها:
     * {نام}-POINTS ، {نام}-CONTOUR ، {نام}-LABEL
     * سایز متن تراز = ۰٫۰۵ m (۵ سانتی‌متر)
     */
    fun exportDxf(
        surfaces: List<Pair<String, List<VolPoint>>>,
        contours: List<ContourSet>,
        textSizeM: Double = 0.05
    ): String {
        val sb = StringBuilder()
        pair(sb, 0, "SECTION"); pair(sb, 2, "HEADER")
        pair(sb, 9, "\$ACADVER"); pair(sb, 1, "AC1009")
        pair(sb, 9, "\$INSUNITS"); pair(sb, 70, "6")
        pair(sb, 0, "ENDSEC")

        val layers = linkedMapOf<String, Int>()
        layers["0"] = 7
        val colors = intArrayOf(1, 3, 5, 4, 6, 2, 30, 140)
        surfaces.forEachIndexed { i, (name, _) ->
            val base = sanitizeLayer(name)
            layers["$base-POINTS"] = colors[i % colors.size]
            layers["$base-CONTOUR"] = colors[(i + 1) % colors.size]
            layers["$base-LABEL"] = 7
        }
        pair(sb, 0, "SECTION"); pair(sb, 2, "TABLES")
        pair(sb, 0, "TABLE"); pair(sb, 2, "LTYPE"); pair(sb, 70, "1")
        pair(sb, 0, "LTYPE"); pair(sb, 2, "CONTINUOUS"); pair(sb, 70, "0")
        pair(sb, 3, "Solid line"); pair(sb, 72, "65"); pair(sb, 73, "0"); pair(sb, 40, "0.0")
        pair(sb, 0, "ENDTAB")
        pair(sb, 0, "TABLE"); pair(sb, 2, "LAYER"); pair(sb, 70, layers.size.toString())
        layers.forEach { (name, color) ->
            pair(sb, 0, "LAYER"); pair(sb, 2, name); pair(sb, 70, "0")
            pair(sb, 62, color.coerceIn(1, 255).toString()); pair(sb, 6, "CONTINUOUS")
        }
        pair(sb, 0, "ENDTAB"); pair(sb, 0, "ENDSEC")

        pair(sb, 0, "SECTION"); pair(sb, 2, "ENTITIES")

        // نقاط هر سطح
        surfaces.forEach { (name, pts) ->
            val layer = "${sanitizeLayer(name)}-POINTS"
            val (clean, _) = dedupe(pts)
            for (p in clean) {
                // ضربدر کوچک
                val s = 0.15
                pair(sb, 0, "LINE"); pair(sb, 8, layer)
                pair(sb, 10, fmt(p.x - s)); pair(sb, 20, fmt(p.y - s)); pair(sb, 30, fmt(p.z))
                pair(sb, 11, fmt(p.x + s)); pair(sb, 21, fmt(p.y + s)); pair(sb, 31, fmt(p.z))
                pair(sb, 0, "LINE"); pair(sb, 8, layer)
                pair(sb, 10, fmt(p.x - s)); pair(sb, 20, fmt(p.y + s)); pair(sb, 30, fmt(p.z))
                pair(sb, 11, fmt(p.x + s)); pair(sb, 21, fmt(p.y - s)); pair(sb, 31, fmt(p.z))
                // شماره نقطه
                pair(sb, 0, "TEXT"); pair(sb, 8, layer)
                pair(sb, 10, fmt(p.x + 0.2)); pair(sb, 20, fmt(p.y + 0.2)); pair(sb, 30, fmt(p.z))
                pair(sb, 40, fmt(textSizeM)); pair(sb, 1, p.id.ifBlank { "P" }); pair(sb, 50, "0")
            }
        }

        // خطوط تراز + برچسب ارتفاع
        for (cs in contours) {
            val base = sanitizeLayer(cs.surfaceName)
            val layerC = "$base-CONTOUR"
            val layerL = "$base-LABEL"
            for (seg in cs.segments) {
                pair(sb, 0, "LINE"); pair(sb, 8, layerC)
                pair(sb, 10, fmt(seg.x1)); pair(sb, 20, fmt(seg.y1)); pair(sb, 30, fmt(seg.z))
                pair(sb, 11, fmt(seg.x2)); pair(sb, 21, fmt(seg.y2)); pair(sb, 31, fmt(seg.z))
            }
            for ((lx, ly, elev) in cs.labels) {
                pair(sb, 0, "TEXT"); pair(sb, 8, layerL)
                pair(sb, 10, fmt(lx)); pair(sb, 20, fmt(ly)); pair(sb, 30, fmt(elev))
                pair(sb, 40, fmt(textSizeM)) // ۵ سانتی‌متر
                pair(sb, 1, String.format(Locale.US, "%.2f", elev))
                pair(sb, 50, "0")
            }
        }

        pair(sb, 0, "ENDSEC"); pair(sb, 0, "EOF")
        return sb.toString()
    }

    fun reportText(name: String, result: VolumeResult, cutFactor: Double, fillFactor: Double): String {
        val sb = StringBuilder()
        sb.appendLine("گزارش تحلیل سطوح")
        sb.appendLine("نام: $name")
        sb.appendLine("سطح اصلی: ${result.existingName}")
        sb.appendLine("سطح دوم: ${result.designName}")
        sb.appendLine("روش: ${result.method}")
        sb.appendLine("نقاط سطح اصلی: ${result.existingCount}")
        sb.appendLine("نقاط سطح دوم: ${result.designCount}")
        sb.appendLine("مساحت منطقه مشترک (m²): ${fmt3(result.areaM2)}")
        sb.appendLine("حجم خاکبرداری Cut (m³): ${fmt3(result.cutM3)}")
        sb.appendLine("حجم خاکریزی Fill (m³): ${fmt3(result.fillM3)}")
        sb.appendLine("خالص Net (m³): ${fmt3(result.netM3)}")
        sb.appendLine("نتیجه احجام خاکی: ${result.earthworkLabel}")
        sb.appendLine("ضریب Cut: $cutFactor | ضریب Fill: $fillFactor")
        sb.appendLine("حداقل ΔZ: ${fmt3(result.minDz)} | حداکثر: ${fmt3(result.maxDz)} | میانگین: ${fmt3(result.avgDz)}")
        if (result.warnings.isNotEmpty()) {
            sb.appendLine("هشدارها:")
            result.warnings.forEach { sb.appendLine(" - $it") }
        }
        return sb.toString()
    }

    private fun fmt3(v: Double) = String.format(Locale.US, "%.3f", v)

    fun fromSurvey(points: List<SurveyPoint>): List<VolPoint> =
        points.map { VolPoint(it.id.ifBlank { "P" }, it.x, it.y, it.z) }
}
