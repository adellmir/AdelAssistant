package com.adel.assistant.data

import kotlin.math.*

enum class CadTool {
    None,
    Select,
    MeasureDist,
    MeasureAngle,
    MeasureArea,
    MeasureObject,
    Offset,
    Trim,
    DrawLine,
    DrawPoly,
    DrawCircle,
    DrawArc,
    DrawText,
    Move,
    Copy,
    Rotate,
    Scale
}

data class OsnapFlags(
    val end: Boolean = true,
    val mid: Boolean = true,
    val center: Boolean = true,
    val intersection: Boolean = true,
    val perpendicular: Boolean = false,
    val node: Boolean = true,
    val nearest: Boolean = true,
    val quadrant: Boolean = false
)

sealed class CadEntity {
    data class Line(val index: Int, val line: DxfLine, val drawingId: Int) : CadEntity()
    data class Circle(val index: Int, val circle: DxfCircle, val drawingId: Int) : CadEntity()
    data class Text(val index: Int, val text: DxfText, val drawingId: Int) : CadEntity()
}

/**
 * شبکه فضایی — حلقه‌ها محدود می‌شوند تا روی نقشه‌های UTM کرش نکند.
 */
class SpatialGrid(private val cell: Double) {
    private val map = HashMap<Long, MutableList<Int>>()
    private val maxCellsPerAxis = 64

    private fun key(ix: Int, iy: Int): Long =
        (ix.toLong() shl 32) xor (iy.toLong() and 0xffffffffL)

    fun clear() = map.clear()

    fun add(id: Int, minX: Double, minY: Double, maxX: Double, maxY: Double) {
        val c = cell.coerceIn(0.5, 1e6)
        var x0 = floor(minX / c).toInt()
        var x1 = floor(maxX / c).toInt()
        var y0 = floor(minY / c).toInt()
        var y1 = floor(maxY / c).toInt()
        // اگر باکس خیلی بزرگ است فقط مرکز را ایندکس کن
        if (x1 - x0 > maxCellsPerAxis || y1 - y0 > maxCellsPerAxis) {
            val cx = floor(((minX + maxX) / 2.0) / c).toInt()
            val cy = floor(((minY + maxY) / 2.0) / c).toInt()
            map.getOrPut(key(cx, cy)) { mutableListOf() }.add(id)
            return
        }
        for (ix in x0..x1) for (iy in y0..y1) {
            map.getOrPut(key(ix, iy)) { mutableListOf() }.add(id)
        }
    }

    fun near(x: Double, y: Double, r: Double): Set<Int> {
        val c = cell.coerceIn(0.5, 1e6)
        val out = HashSet<Int>()
        val rr = r.coerceIn(c, c * maxCellsPerAxis)
        var x0 = floor((x - rr) / c).toInt()
        var x1 = floor((x + rr) / c).toInt()
        var y0 = floor((y - rr) / c).toInt()
        var y1 = floor((y + rr) / c).toInt()
        if (x1 - x0 > maxCellsPerAxis) {
            val mid = (x0 + x1) / 2
            x0 = mid - maxCellsPerAxis / 2
            x1 = mid + maxCellsPerAxis / 2
        }
        if (y1 - y0 > maxCellsPerAxis) {
            val mid = (y0 + y1) / 2
            y0 = mid - maxCellsPerAxis / 2
            y1 = mid + maxCellsPerAxis / 2
        }
        for (ix in x0..x1) for (iy in y0..y1) {
            map[key(ix, iy)]?.let { out.addAll(it) }
        }
        return out
    }
}

object CadEngine {

    fun hypot(dx: Double, dy: Double) = sqrt(dx * dx + dy * dy)

    fun snap(
        rawX: Double,
        rawY: Double,
        models: List<Pair<Int, DxfModel>>,
        flags: OsnapFlags,
        maxDist: Double,
        refForPerp: Pair<Double, Double>? = null
    ): Pair<Double, Double> {
        if (!rawX.isFinite() || !rawY.isFinite() || maxDist <= 0 || !maxDist.isFinite()) {
            return rawX to rawY
        }
        var bestD = maxDist
        var best: Pair<Double, Double>? = null
        fun consider(x: Double, y: Double) {
            if (!x.isFinite() || !y.isFinite()) return
            val d = hypot(x - rawX, y - rawY)
            if (d < bestD) {
                bestD = d
                best = x to y
            }
        }

        try {
            models.forEach { (_, m) ->
                val lines = m.lines
                val useGrid = (flags.intersection) && lines.size > 80
                val grid = if (useGrid) {
                    SpatialGrid(maxDist.coerceIn(1.0, 50.0) * 4.0).also { g ->
                        lines.forEachIndexed { idx, l ->
                            g.add(
                                idx,
                                min(l.x1, l.x2), min(l.y1, l.y2),
                                max(l.x1, l.x2), max(l.y1, l.y2)
                            )
                        }
                    }
                } else null
                val nearIdx = grid?.near(rawX, rawY, maxDist * 3.0)

                lines.forEach { l ->
                    if (flags.end) {
                        consider(l.x1, l.y1)
                        consider(l.x2, l.y2)
                    }
                    if (flags.mid) consider((l.x1 + l.x2) / 2.0, (l.y1 + l.y2) / 2.0)
                    if (flags.nearest) {
                        val (px, py) = projectPointOnSegment(rawX, rawY, l.x1, l.y1, l.x2, l.y2)
                        consider(px, py)
                    }
                    if (flags.perpendicular && refForPerp != null) {
                        val (px, py) = projectPointOnSegment(
                            refForPerp.first, refForPerp.second, l.x1, l.y1, l.x2, l.y2
                        )
                        consider(px, py)
                    }
                }

                if (flags.center) m.circles.forEach { consider(it.x, it.y) }

                if (flags.quadrant) {
                    m.circles.forEach { c ->
                        if (c.r.isFinite() && c.r > 0) {
                            consider(c.x + c.r, c.y)
                            consider(c.x - c.r, c.y)
                            consider(c.x, c.y + c.r)
                            consider(c.x, c.y - c.r)
                        }
                    }
                }

                if (flags.node) {
                    try {
                        m.points.forEach { consider(it.x, it.y) }
                    } catch (_: Throwable) { }
                    m.circles.forEach { if (it.r <= 0.25) consider(it.x, it.y) }
                }

                if (flags.intersection) {
                    if (nearIdx != null && nearIdx.isNotEmpty()) {
                        val list = nearIdx.toList()
                        val lim = min(list.size, 120)
                        for (i in 0 until lim) for (j in i + 1 until lim) {
                            val ii = list[i]; val jj = list[j]
                            if (ii in lines.indices && jj in lines.indices) {
                                intersectSeg(lines[ii], lines[jj])?.let { consider(it.first, it.second) }
                            }
                        }
                    } else if (lines.size <= 200) {
                        for (i in lines.indices) for (j in i + 1 until lines.size) {
                            intersectSeg(lines[i], lines[j])?.let { consider(it.first, it.second) }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            return rawX to rawY
        }
        return best ?: (rawX to rawY)
    }

    /** توضیح اندازه‌گیری برای شیء انتخاب‌شده */
    fun describeEntity(entity: CadEntity): String {
        return when (entity) {
            is CadEntity.Line -> {
                val l = entity.line
                val dE = l.x2 - l.x1
                val dN = l.y2 - l.y1
                val len = hypot(dE, dN)
                val az = SurveyUnits.azimuthDeg(dE, dN)
                val brg = SurveyUnits.bearing(dE, dN)
                val dZ = try { l.z2 - l.z1 } catch (_: Throwable) { 0.0 }
                val slope = if (len > 1e-9) (dZ / len) * 100.0 else 0.0
                buildString {
                    append("خط | طول: ${"%.3f".format(len)} m")
                    append(" | $brg")
                    append(" | Az ${"%.2f".format(az)}°")
                    append(" | ΔE ${"%.3f".format(dE)} ΔN ${"%.3f".format(dN)}")
                    if (abs(dZ) > 1e-6) append(" | ΔZ ${"%.3f".format(dZ)} شیب ${"%.2f".format(slope)}%")
                    append(" | لایه ${l.layer}")
                }
            }
            is CadEntity.Circle -> {
                val c = entity.circle
                val r = c.r
                val diam = r * 2
                val peri = 2 * PI * r
                val area = PI * r * r
                buildString {
                    append("دایره | مرکز E=${"%.3f".format(c.x)} N=${"%.3f".format(c.y)}")
                    append(" | R ${"%.3f".format(r)} m | D ${"%.3f".format(diam)} m")
                    append(" | محیط ${"%.3f".format(peri)} m")
                    append(" | ${SurveyUnits.formatArea(area)}")
                    append(" | لایه ${c.layer}")
                }
            }
            is CadEntity.Text -> {
                val tx = entity.text
                "متن | «${tx.text}» | E=${"%.3f".format(tx.x)} N=${"%.3f".format(tx.y)} | H=${"%.2f".format(tx.height)} | لایه ${tx.layer}"
            }
        }
    }

    fun projectPointOnSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Pair<Double, Double> {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-18) return x1 to y1
        val t = (((px - x1) * dx + (py - y1) * dy) / len2).coerceIn(0.0, 1.0)
        return (x1 + t * dx) to (y1 + t * dy)
    }

    fun intersectSeg(a: DxfLine, b: DxfLine): Pair<Double, Double>? {
        val x1 = a.x1; val y1 = a.y1; val x2 = a.x2; val y2 = a.y2
        val x3 = b.x1; val y3 = b.y1; val x4 = b.x2; val y4 = b.y2
        val den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(den) < 1e-12) return null
        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
        val u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / den
        if (t < 0 || t > 1 || u < 0 || u > 1) return null
        return (x1 + t * (x2 - x1)) to (y1 + t * (y2 - y1))
    }

    fun pickEntity(
        x: Double,
        y: Double,
        models: List<Pair<Int, DxfModel>>,
        maxDist: Double
    ): CadEntity? {
        var bestD = maxDist
        var best: CadEntity? = null
        models.forEach { (did, m) ->
            m.lines.forEachIndexed { i, l ->
                val (px, py) = projectPointOnSegment(x, y, l.x1, l.y1, l.x2, l.y2)
                val d = hypot(px - x, py - y)
                if (d < bestD) {
                    bestD = d
                    best = CadEntity.Line(i, l, did)
                }
            }
            m.circles.forEachIndexed { i, c ->
                val d = abs(hypot(c.x - x, c.y - y) - c.r)
                if (d < bestD) {
                    bestD = d
                    best = CadEntity.Circle(i, c, did)
                }
            }
            m.texts.forEachIndexed { i, tx ->
                val d = hypot(tx.x - x, tx.y - y)
                if (d < bestD) {
                    bestD = d
                    best = CadEntity.Text(i, tx, did)
                }
            }
        }
        return best
    }

    fun angleDeg(a: Pair<Double, Double>, vertex: Pair<Double, Double>, c: Pair<Double, Double>): Double {
        val v1x = a.first - vertex.first
        val v1y = a.second - vertex.second
        val v2x = c.first - vertex.first
        val v2y = c.second - vertex.second
        val n1 = hypot(v1x, v1y)
        val n2 = hypot(v2x, v2y)
        if (n1 < 1e-12 || n2 < 1e-12) return 0.0
        val cos = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    fun polygonArea(pts: List<Pair<Double, Double>>): Double {
        if (pts.size < 3) return 0.0
        var s = 0.0
        for (i in pts.indices) {
            val j = (i + 1) % pts.size
            s += pts[i].first * pts[j].second - pts[j].first * pts[i].second
        }
        return abs(s) / 2.0
    }

    fun applyOrtho(from: Pair<Double, Double>, to: Pair<Double, Double>): Pair<Double, Double> {
        val dx = abs(to.first - from.first)
        val dy = abs(to.second - from.second)
        return if (dx >= dy) (to.first to from.second) else (from.first to to.second)
    }

    fun distToSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val (qx, qy) = projectPointOnSegment(px, py, x1, y1, x2, y2)
        return hypot(px - qx, py - qy)
    }

    fun snapToGrid(x: Double, y: Double, step: Double): Pair<Double, Double> {
        if (step <= 0) return x to y
        return (round(x / step) * step) to (round(y / step) * step)
    }

    /** خط موازی به فاصله dist؛ سمت با sidePoint مشخص می‌شود */
    fun offsetLine(l: DxfLine, dist: Double, sidePoint: Pair<Double, Double>): DxfLine {
        val dx = l.x2 - l.x1
        val dy = l.y2 - l.y1
        val len = hypot(dx, dy)
        if (len < 1e-12 || !dist.isFinite()) return l
        var nx = -dy / len
        var ny = dx / len
        val mx = (l.x1 + l.x2) / 2.0
        val my = (l.y1 + l.y2) / 2.0
        val sx = sidePoint.first - mx
        val sy = sidePoint.second - my
        if (nx * sx + ny * sy < 0) {
            nx = -nx
            ny = -ny
        }
        val d = dist
        return l.copy(
            x1 = l.x1 + nx * d,
            y1 = l.y1 + ny * d,
            x2 = l.x2 + nx * d,
            y2 = l.y2 + ny * d
        )
    }

    /** کوتاه کردن خط تا نزدیک‌ترین تقاطع با cutting (ساده) */
    fun trimLineToPoint(l: DxfLine, pick: Pair<Double, Double>, cut: DxfLine): DxfLine? {
        val hit = intersectSeg(l, cut) ?: return null
        val d1 = hypot(pick.first - l.x1, pick.second - l.y1)
        val d2 = hypot(pick.first - l.x2, pick.second - l.y2)
        return if (d1 <= d2) {
            l.copy(x1 = hit.first, y1 = hit.second)
        } else {
            l.copy(x2 = hit.first, y2 = hit.second)
        }
    }

    fun translateLine(l: DxfLine, dx: Double, dy: Double) =
        l.copy(x1 = l.x1 + dx, y1 = l.y1 + dy, x2 = l.x2 + dx, y2 = l.y2 + dy)

    fun translateCircle(c: DxfCircle, dx: Double, dy: Double) =
        c.copy(x = c.x + dx, y = c.y + dy)

    fun translateText(t: DxfText, dx: Double, dy: Double) =
        t.copy(x = t.x + dx, y = t.y + dy)
}
