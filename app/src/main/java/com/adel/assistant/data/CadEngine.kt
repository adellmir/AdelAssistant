package com.adel.assistant.data

import kotlin.math.*

enum class CadTool {
    None,
    Select,
    MeasureDist,
    MeasureAngle,
    MeasureArea,
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
    val perpendicular: Boolean = false
)

sealed class CadEntity {
    data class Line(val index: Int, val line: DxfLine, val drawingId: Int) : CadEntity()
    data class Circle(val index: Int, val circle: DxfCircle, val drawingId: Int) : CadEntity()
    data class Text(val index: Int, val text: DxfText, val drawingId: Int) : CadEntity()
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
        var bestD = maxDist
        var best: Pair<Double, Double>? = null
        fun consider(x: Double, y: Double) {
            val d = hypot(x - rawX, y - rawY)
            if (d < bestD) {
                bestD = d
                best = x to y
            }
        }
        models.forEach { (_, m) ->
            m.lines.forEach { l ->
                if (flags.end) {
                    consider(l.x1, l.y1)
                    consider(l.x2, l.y2)
                }
                if (flags.mid) consider((l.x1 + l.x2) / 2.0, (l.y1 + l.y2) / 2.0)
                if (flags.perpendicular && refForPerp != null) {
                    val (px, py) = projectPointOnSegment(refForPerp.first, refForPerp.second, l.x1, l.y1, l.x2, l.y2)
                    consider(px, py)
                }
            }
            if (flags.center) m.circles.forEach { consider(it.x, it.y) }
            if (flags.intersection) {
                val lines = m.lines
                for (i in lines.indices) for (j in i + 1 until lines.size) {
                    intersectSeg(lines[i], lines[j])?.let { consider(it.first, it.second) }
                }
            }
        }
        return best ?: (rawX to rawY)
    }

    fun projectPointOnSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Pair<Double, Double> {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-18) return x1 to y1
        var t = ((px - x1) * dx + (py - y1) * dy) / len2
        t = t.coerceIn(0.0, 1.0)
        return (x1 + t * dx) to (y1 + t * dy)
    }

    fun intersectSeg(a: DxfLine, b: DxfLine): Pair<Double, Double>? {
        val dax = a.x2 - a.x1
        val day = a.y2 - a.y1
        val dbx = b.x2 - b.x1
        val dby = b.y2 - b.y1
        val den = dax * dby - day * dbx
        if (abs(den) < 1e-12) return null
        val t = ((b.x1 - a.x1) * dby - (b.y1 - a.y1) * dbx) / den
        val u = ((b.x1 - a.x1) * day - (b.y1 - a.y1) * dax) / den
        if (t < 0 || t > 1 || u < 0 || u > 1) return null
        return (a.x1 + t * dax) to (a.y1 + t * day)
    }

    fun applyOrtho(from: Pair<Double, Double>, to: Pair<Double, Double>): Pair<Double, Double> {
        val dx = abs(to.first - from.first)
        val dy = abs(to.second - from.second)
        return if (dx >= dy) to.first to from.second else from.first to to.second
    }

    fun snapToGrid(x: Double, y: Double, step: Double): Pair<Double, Double> {
        if (step <= 0) return x to y
        return (round(x / step) * step) to (round(y / step) * step)
    }

    fun pickEntity(
        wx: Double,
        wy: Double,
        models: List<Pair<Int, DxfModel>>,
        maxDist: Double
    ): CadEntity? {
        var bestD = maxDist
        var best: CadEntity? = null
        models.forEach { (did, m) ->
            m.lines.forEachIndexed { i, l ->
                val d = distToSegment(wx, wy, l.x1, l.y1, l.x2, l.y2)
                if (d < bestD) {
                    bestD = d
                    best = CadEntity.Line(i, l, did)
                }
            }
            m.circles.forEachIndexed { i, c ->
                val d = abs(hypot(wx - c.x, wy - c.y) - c.r)
                if (d < bestD) {
                    bestD = d
                    best = CadEntity.Circle(i, c, did)
                }
            }
            m.texts.forEachIndexed { i, tx ->
                val d = hypot(wx - tx.x, wy - tx.y)
                if (d < bestD) {
                    bestD = d
                    best = CadEntity.Text(i, tx, did)
                }
            }
        }
        return best
    }

    fun distToSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val (qx, qy) = projectPointOnSegment(px, py, x1, y1, x2, y2)
        return hypot(px - qx, py - qy)
    }

    fun angleDeg(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>): Double {
        val v1x = a.first - b.first
        val v1y = a.second - b.second
        val v2x = c.first - b.first
        val v2y = c.second - b.second
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

    fun translateLine(l: DxfLine, dx: Double, dy: Double) =
        l.copy(x1 = l.x1 + dx, y1 = l.y1 + dy, x2 = l.x2 + dx, y2 = l.y2 + dy)

    fun translateCircle(c: DxfCircle, dx: Double, dy: Double) =
        c.copy(x = c.x + dx, y = c.y + dy)

    fun translateText(t: DxfText, dx: Double, dy: Double) =
        t.copy(x = t.x + dx, y = t.y + dy)
}
