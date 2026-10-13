package com.adel.assistant.data

import kotlin.math.*

enum class CadTool {
    None,
    Select,
    MeasureDist,
    MeasureAngle,
    MeasureArea,
    MeasureObject,
    DrawLine,
    DrawPoly,
    DrawCircle,
    DrawArc,
    DrawText,
    DrawGrid,
    Move,
    Copy,
    Rotate,
    Scale,
    Offset,
    Trim,
    Extend,
    Fillet,
    Mirror
}

/**
 * ساخت خطوط و متون گرید روی لایه GRID.
 * محورها موازی X/Y و روی مختصات رُند (مضرب فاصله).
 * متن مختصات در دو سر هر خط.
 */
object GridBuilder {
    fun build(
        xA: Double, yA: Double, xB: Double, yB: Double,
        step: Double, textHeight: Double,
        layer: String = "GRID", color: Int = 8
    ): Pair<List<DxfLine>, List<DxfText>> {
        val st = step.coerceAtLeast(1e-6)
        val minX = minOf(xA, xB)
        val maxX = maxOf(xA, xB)
        val minY = minOf(yA, yB)
        val maxY = maxOf(yA, yB)
        // رُند کردن محدوده به مضرب step
        fun ceilStep(v: Double) = kotlin.math.ceil(v / st - 1e-12) * st
        fun floorStep(v: Double) = kotlin.math.floor(v / st + 1e-12) * st
        val x0 = ceilStep(minX)
        val x1 = floorStep(maxX)
        val y0 = ceilStep(minY)
        val y1 = floorStep(maxY)
        val lines = mutableListOf<DxfLine>()
        val texts = mutableListOf<DxfText>()
        fun fmt(v: Double): String {
            val r = kotlin.math.round(v * 1000.0) / 1000.0
            return if (kotlin.math.abs(r - r.toLong()) < 1e-9) r.toLong().toString()
            else String.format(java.util.Locale.US, "%.3f", r)
        }
        // خطوط عمودی (ثابت X) — برچسب X در پایین و بالا
        if (x0 <= x1) {
            var x = x0
            var guard = 0
            while (x <= x1 + 1e-9 && guard < 5000) {
                lines.add(DxfLine(x, minY, x, maxY, layer, color))
                texts.add(DxfText(x, minY - textHeight * 1.2, textHeight, fmt(x), layer, color))
                texts.add(DxfText(x, maxY + textHeight * 0.4, textHeight, fmt(x), layer, color))
                x += st
                guard++
            }
        }
        // خطوط افقی (ثابت Y) — برچسب Y در چپ و راست
        if (y0 <= y1) {
            var y = y0
            var guard = 0
            while (y <= y1 + 1e-9 && guard < 5000) {
                lines.add(DxfLine(minX, y, maxX, y, layer, color))
                texts.add(DxfText(minX - textHeight * 0.2, y, textHeight, fmt(y), layer, color))
                texts.add(DxfText(maxX + textHeight * 0.4, y, textHeight, fmt(y), layer, color))
                y += st
                guard++
            }
        }
        return lines to texts
    }
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

/** شبکه فضایی محدود برای اسنپ روی نقشه‌های بزرگ UTM */
class SpatialGrid(private val cell: Double) {
    private val map = HashMap<Long, MutableList<Int>>()
    private val maxCellsPerAxis = 64
    private fun key(ix: Int, iy: Int): Long = (ix.toLong() shl 32) xor (iy.toLong() and 0xffffffffL)
    fun add(id: Int, minX: Double, minY: Double, maxX: Double, maxY: Double) {
        val c = cell.coerceIn(0.5, 1e6)
        var x0 = floor(minX / c).toInt(); var x1 = floor(maxX / c).toInt()
        var y0 = floor(minY / c).toInt(); var y1 = floor(maxY / c).toInt()
        if (x1 - x0 > maxCellsPerAxis || y1 - y0 > maxCellsPerAxis) {
            val cx = floor(((minX + maxX) / 2.0) / c).toInt()
            val cy = floor(((minY + maxY) / 2.0) / c).toInt()
            map.getOrPut(key(cx, cy)) { mutableListOf() }.add(id)
            return
        }
        for (ix in x0..x1) for (iy in y0..y1) map.getOrPut(key(ix, iy)) { mutableListOf() }.add(id)
    }
    fun near(x: Double, y: Double, r: Double): Set<Int> {
        val c = cell.coerceIn(0.5, 1e6)
        val out = HashSet<Int>()
        val rr = r.coerceIn(c, c * maxCellsPerAxis)
        var x0 = floor((x - rr) / c).toInt(); var x1 = floor((x + rr) / c).toInt()
        var y0 = floor((y - rr) / c).toInt(); var y1 = floor((y + rr) / c).toInt()
        if (x1 - x0 > maxCellsPerAxis) { val m = (x0 + x1) / 2; x0 = m - maxCellsPerAxis / 2; x1 = m + maxCellsPerAxis / 2 }
        if (y1 - y0 > maxCellsPerAxis) { val m = (y0 + y1) / 2; y0 = m - maxCellsPerAxis / 2; y1 = m + maxCellsPerAxis / 2 }
        for (ix in x0..x1) for (iy in y0..y1) map[key(ix, iy)]?.let { out.addAll(it) }
        return out
    }
}

object CadEngine {
    fun hypot(dx: Double, dy: Double) = sqrt(dx * dx + dy * dy)

    fun snap(
        rawX: Double, rawY: Double,
        models: List<Pair<Int, DxfModel>>,
        flags: OsnapFlags, maxDist: Double,
        refForPerp: Pair<Double, Double>? = null
    ): Pair<Double, Double> {
        if (!rawX.isFinite() || !rawY.isFinite() || maxDist <= 0 || !maxDist.isFinite()) return rawX to rawY
        var bestD = maxDist
        var best: Pair<Double, Double>? = null
        fun consider(x: Double, y: Double) {
            if (!x.isFinite() || !y.isFinite()) return
            val d = hypot(x - rawX, y - rawY)
            if (d < bestD) { bestD = d; best = x to y }
        }
        try {
            models.forEach { (_, m) ->
                val lines = m.lines
                val useGrid = flags.intersection && lines.size > 80
                val grid = if (useGrid) {
                    SpatialGrid(maxDist.coerceIn(1.0, 50.0) * 4.0).also { g ->
                        lines.forEachIndexed { idx, l ->
                            g.add(idx, min(l.x1, l.x2), min(l.y1, l.y2), max(l.x1, l.x2), max(l.y1, l.y2))
                        }
                    }
                } else null
                val nearIdx = grid?.near(rawX, rawY, maxDist * 3.0)
                lines.forEach { l ->
                    if (flags.end) { consider(l.x1, l.y1); consider(l.x2, l.y2) }
                    if (flags.mid) consider((l.x1 + l.x2) / 2.0, (l.y1 + l.y2) / 2.0)
                    if (flags.nearest) {
                        val (px, py) = projectPointOnSegment(rawX, rawY, l.x1, l.y1, l.x2, l.y2)
                        consider(px, py)
                    }
                    if (flags.perpendicular && refForPerp != null) {
                        val (px, py) = projectPointOnSegment(refForPerp.first, refForPerp.second, l.x1, l.y1, l.x2, l.y2)
                        consider(px, py)
                    }
                }
                if (flags.center) m.circles.forEach { consider(it.x, it.y) }
                if (flags.quadrant) m.circles.forEach { c ->
                    if (c.r.isFinite() && c.r > 0) {
                        consider(c.x + c.r, c.y); consider(c.x - c.r, c.y)
                        consider(c.x, c.y + c.r); consider(c.x, c.y - c.r)
                    }
                }
                if (flags.node) {
                    m.circles.forEach { if (it.r <= 0.25) consider(it.x, it.y) }
                }
                if (flags.intersection) {
                    if (nearIdx != null && nearIdx.isNotEmpty()) {
                        val list = nearIdx.toList()
                        val lim = min(list.size, 120)
                        for (i in 0 until lim) for (j in i + 1 until lim) {
                            val ii = list[i]; val jj = list[j]
                            if (ii in lines.indices && jj in lines.indices)
                                intersectSeg(lines[ii], lines[jj])?.let { consider(it.first, it.second) }
                        }
                    } else if (lines.size <= 200) {
                        for (i in lines.indices) for (j in i + 1 until lines.size)
                            intersectSeg(lines[i], lines[j])?.let { consider(it.first, it.second) }
                    }
                }
            }
        } catch (_: Throwable) { return rawX to rawY }
        return best ?: (rawX to rawY)
    }

    fun describeEntity(entity: CadEntity): String = when (entity) {
        is CadEntity.Line -> {
            val l = entity.line
            val dE = l.x2 - l.x1; val dN = l.y2 - l.y1
            val len = hypot(dE, dN)
            val az = SurveyUnits.azimuthDeg(dE, dN)
            val brg = SurveyUnits.bearing(dE, dN)
            "خط | طول: ${"%.3f".format(len)} m | $brg | Az ${"%.2f".format(az)}° | ΔE ${"%.3f".format(dE)} ΔN ${"%.3f".format(dN)} | لایه ${l.layer}"
        }
        is CadEntity.Circle -> {
            val c = entity.circle
            val peri = 2 * PI * c.r; val area = PI * c.r * c.r
            "دایره | مرکز E=${"%.3f".format(c.x)} N=${"%.3f".format(c.y)} | R ${"%.3f".format(c.r)} | D ${"%.3f".format(c.r * 2)} | محیط ${"%.3f".format(peri)} | ${SurveyUnits.formatArea(area)} | لایه ${c.layer}"
        }
        is CadEntity.Text -> "متن | «${entity.text.text}» | E=${"%.3f".format(entity.text.x)} N=${"%.3f".format(entity.text.y)} | لایه ${entity.text.layer}"
    }

    fun projectPointOnSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Pair<Double, Double> {
        val dx = x2 - x1; val dy = y2 - y1; val len2 = dx * dx + dy * dy
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

    fun intersectInfinite(a: DxfLine, b: DxfLine): Pair<Double, Double>? {
        val x1 = a.x1; val y1 = a.y1; val x2 = a.x2; val y2 = a.y2
        val x3 = b.x1; val y3 = b.y1; val x4 = b.x2; val y4 = b.y2
        val den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(den) < 1e-12) return null
        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
        return (x1 + t * (x2 - x1)) to (y1 + t * (y2 - y1))
    }

    fun applyOrtho(from: Pair<Double, Double>, to: Pair<Double, Double>): Pair<Double, Double> {
        val dx = abs(to.first - from.first); val dy = abs(to.second - from.second)
        return if (dx >= dy) (to.first to from.second) else (from.first to to.second)
    }

    fun offsetLine(l: DxfLine, dist: Double, sidePoint: Pair<Double, Double>): DxfLine {
        val dx = l.x2 - l.x1; val dy = l.y2 - l.y1; val len = hypot(dx, dy)
        if (len < 1e-12 || !dist.isFinite()) return l
        var nx = -dy / len; var ny = dx / len
        val mx = (l.x1 + l.x2) / 2.0; val my = (l.y1 + l.y2) / 2.0
        if (nx * (sidePoint.first - mx) + ny * (sidePoint.second - my) < 0) { nx = -nx; ny = -ny }
        return l.copy(x1 = l.x1 + nx * dist, y1 = l.y1 + ny * dist, x2 = l.x2 + nx * dist, y2 = l.y2 + ny * dist)
    }

    fun offsetLineMultiple(l: DxfLine, dist: Double, count: Int, sidePoint: Pair<Double, Double>): List<DxfLine> =
        (1..count.coerceIn(1, 50)).map { k -> offsetLine(l, dist * k, sidePoint) }

    fun trimLineToPoint(l: DxfLine, pick: Pair<Double, Double>, cut: DxfLine): DxfLine? {
        val hit = intersectSeg(l, cut) ?: return null
        val d1 = hypot(pick.first - l.x1, pick.second - l.y1)
        val d2 = hypot(pick.first - l.x2, pick.second - l.y2)
        return if (d1 <= d2) l.copy(x1 = hit.first, y1 = hit.second) else l.copy(x2 = hit.first, y2 = hit.second)
    }

    fun extendLineTo(l: DxfLine, pick: Pair<Double, Double>, boundary: DxfLine): DxfLine? {
        val hit = intersectInfinite(l, boundary) ?: return null
        val d1 = hypot(pick.first - l.x1, pick.second - l.y1)
        val d2 = hypot(pick.first - l.x2, pick.second - l.y2)
        return if (d1 <= d2) l.copy(x1 = hit.first, y1 = hit.second) else l.copy(x2 = hit.first, y2 = hit.second)
    }

    fun mirrorLine(l: DxfLine, axis: DxfLine): DxfLine {
        fun mir(x: Double, y: Double): Pair<Double, Double> {
            val dx = axis.x2 - axis.x1; val dy = axis.y2 - axis.y1; val len2 = dx * dx + dy * dy
            if (len2 < 1e-18) return x to y
            val t = ((x - axis.x1) * dx + (y - axis.y1) * dy) / len2
            val qx = axis.x1 + t * dx; val qy = axis.y1 + t * dy
            return (2 * qx - x) to (2 * qy - y)
        }
        val (x1, y1) = mir(l.x1, l.y1); val (x2, y2) = mir(l.x2, l.y2)
        return l.copy(x1 = x1, y1 = y1, x2 = x2, y2 = y2)
    }

    fun mirrorCircle(c: DxfCircle, axis: DxfLine): DxfCircle {
        val dx = axis.x2 - axis.x1; val dy = axis.y2 - axis.y1; val len2 = dx * dx + dy * dy
        if (len2 < 1e-18) return c
        val t = ((c.x - axis.x1) * dx + (c.y - axis.y1) * dy) / len2
        val qx = axis.x1 + t * dx; val qy = axis.y1 + t * dy
        return c.copy(x = 2 * qx - c.x, y = 2 * qy - c.y)
    }

    fun snapToGrid(x: Double, y: Double, step: Double): Pair<Double, Double> {
        if (step <= 0) return x to y
        return (round(x / step) * step) to (round(y / step) * step)
    }

    fun pickEntity(wx: Double, wy: Double, models: List<Pair<Int, DxfModel>>, maxDist: Double): CadEntity? {
        var bestD = maxDist; var best: CadEntity? = null
        models.forEach { (did, m) ->
            m.lines.forEachIndexed { i, l ->
                val d = distToSegment(wx, wy, l.x1, l.y1, l.x2, l.y2)
                if (d < bestD) { bestD = d; best = CadEntity.Line(i, l, did) }
            }
            m.circles.forEachIndexed { i, c ->
                val d = abs(hypot(wx - c.x, wy - c.y) - c.r)
                if (d < bestD) { bestD = d; best = CadEntity.Circle(i, c, did) }
            }
            m.texts.forEachIndexed { i, tx ->
                val d = hypot(wx - tx.x, wy - tx.y)
                if (d < bestD) { bestD = d; best = CadEntity.Text(i, tx, did) }
            }
        }
        return best
    }

    fun distToSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val (qx, qy) = projectPointOnSegment(px, py, x1, y1, x2, y2)
        return hypot(px - qx, py - qy)
    }

    fun angleDeg(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>): Double {
        val v1x = a.first - b.first; val v1y = a.second - b.second
        val v2x = c.first - b.first; val v2y = c.second - b.second
        val n1 = hypot(v1x, v1y); val n2 = hypot(v2x, v2y)
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

    fun translateLine(l: DxfLine, dx: Double, dy: Double) =
        l.copy(x1 = l.x1 + dx, y1 = l.y1 + dy, x2 = l.x2 + dx, y2 = l.y2 + dy)
    fun translateCircle(c: DxfCircle, dx: Double, dy: Double) = c.copy(x = c.x + dx, y = c.y + dy)
    fun translateText(t: DxfText, dx: Double, dy: Double) = t.copy(x = t.x + dx, y = t.y + dy)
}
