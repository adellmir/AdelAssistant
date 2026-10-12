package com.adel.assistant.data

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class DxfLine(
    val x1: Double, val y1: Double,
    val x2: Double, val y2: Double,
    val layer: String, val color: Int,
    val z1: Double = 0.0, val z2: Double = 0.0
)

data class DxfCircle(
    val x: Double, val y: Double, val r: Double,
    val layer: String, val color: Int,
    val z: Double = 0.0
)

data class DxfText(
    val x: Double, val y: Double, val height: Double,
    val text: String, val layer: String, val color: Int,
    val z: Double = 0.0, val rotation: Double = 0.0
)

/** نقطهٔ POINT در DXF (با ارتفاع Z) */
data class DxfPoint(
    val x: Double, val y: Double, val z: Double = 0.0,
    val layer: String = "0", val color: Int = 7
)

data class DxfLayerInfo(
    val name: String,
    var colorAci: Int,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var displayColor: Color? = null
)

data class DxfModel(
    val lines: List<DxfLine>,
    val circles: List<DxfCircle>,
    val texts: List<DxfText>,
    val layers: MutableMap<String, DxfLayerInfo>,
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
    /** نقاط POINT با Z — پیش‌فرض خالی برای سازگاری فراخوانی‌های قبلی */
    val points: List<DxfPoint> = emptyList()
) {
    val isEmpty: Boolean
        get() = lines.isEmpty() && circles.isEmpty() && texts.isEmpty() && points.isEmpty()

    fun width() = (maxX - minX).coerceAtLeast(1.0)
    fun height() = (maxY - minY).coerceAtLeast(1.0)

    fun recalculatedBounds(): DxfModel {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        fun pt(x: Double, y: Double) {
            if (x < minX) minX = x; if (y < minY) minY = y
            if (x > maxX) maxX = x; if (y > maxY) maxY = y
        }
        lines.forEach { pt(it.x1, it.y1); pt(it.x2, it.y2) }
        circles.forEach {
            pt(it.x - it.r, it.y - it.r); pt(it.x + it.r, it.y + it.r)
        }
        texts.forEach { pt(it.x, it.y) }
        points.forEach { pt(it.x, it.y) }
        if (minX == Double.POSITIVE_INFINITY) {
            minX = 0.0; minY = 0.0; maxX = 1.0; maxY = 1.0
        }
        return copy(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
    }

    fun toDxfText(): String = buildString {
        append("0\nSECTION\n2\nHEADER\n0\nENDSEC\n")
        append("0\nSECTION\n2\nENTITIES\n")
        lines.forEach { l ->
            append("0\nLINE\n8\n${l.layer}\n10\n${l.x1}\n20\n${l.y1}\n30\n${l.z1}\n11\n${l.x2}\n21\n${l.y2}\n31\n${l.z2}\n")
        }
        circles.forEach { c ->
            append("0\nCIRCLE\n8\n${c.layer}\n10\n${c.x}\n20\n${c.y}\n30\n${c.z}\n40\n${c.r}\n")
        }
        points.forEach { p ->
            append("0\nPOINT\n8\n${p.layer}\n10\n${p.x}\n20\n${p.y}\n30\n${p.z}\n")
        }
        texts.forEach { tx ->
            append("0\nTEXT\n8\n${tx.layer}\n10\n${tx.x}\n20\n${tx.y}\n30\n${tx.z}\n40\n${tx.height}\n1\n${tx.text}\n")
        }
        append("0\nENDSEC\n0\nEOF\n")
    }
}

object DxfParser {

    fun cleanMText(s: String): String = s
        .replace("\\P", "\n")
        .replace(Regex("\\\\[fFHhCcTtQqWwAa][^;]*;"), "")
        .replace(Regex("\\\\[LlOoKk]"), "")
        .replace("{", "").replace("}", "")
        .replace("%%d", "°", ignoreCase = true)
        .replace("%%p", "±", ignoreCase = true)
        .replace("%%c", "Ø", ignoreCase = true)

    fun bulgeArc(
        x1: Double, y1: Double, x2: Double, y2: Double,
        b: Double, stepDeg: Double = 5.0
    ): List<Pair<Double, Double>> {
        if (abs(b) < 1e-9) return listOf(x1 to y1, x2 to y2)
        val theta = 4.0 * atan(b)
        val chord = hypot(x2 - x1, y2 - y1)
        if (chord < 1e-12) return listOf(x1 to y1, x2 to y2)
        val r = chord / (2.0 * sin(abs(theta) / 2.0))
        val ux = -(y2 - y1) / chord
        val uy = (x2 - x1) / chord
        val sign = if (b >= 0) 1.0 else -1.0
        val distCenter = r * cos(theta / 2.0)
        val cx2 = (x1 + x2) / 2.0 + ux * distCenter * sign
        val cy2 = (y1 + y2) / 2.0 + uy * distCenter * sign
        val a1 = atan2(y1 - cy2, x1 - cx2)
        val n = max(2, ceil(Math.toDegrees(abs(theta)) / stepDeg).toInt())
        return (0..n).map { i ->
            val a = a1 + theta * i / n
            (cx2 + r * cos(a)) to (cy2 + r * sin(a))
        }
    }

    fun sampleArc(
        cx: Double, cy: Double, r: Double,
        startDeg: Double, endDeg: Double,
        stepDeg: Double = 5.0
    ): List<Pair<Double, Double>> {
        var a0 = startDeg
        var a1 = endDeg
        while (a1 < a0) a1 += 360.0
        val sweep = a1 - a0
        val n = max(2, ceil(sweep / stepDeg).toInt())
        return (0..n).map { i ->
            val a = Math.toRadians(a0 + sweep * i / n)
            (cx + r * cos(a)) to (cy + r * sin(a))
        }
    }

    fun parse(content: String): DxfModel {
        val raw = content.replace("\r\n", "\n").replace('\r', '\n')
        val fileLines = raw.split('\n')
        val pairs = ArrayList<Pair<Int, String>>(fileLines.size / 2 + 8)
        var i = 0
        while (i + 1 < fileLines.size) {
            val code = fileLines[i].trim().toIntOrNull()
            val value = fileLines[i + 1]
            if (code != null) pairs.add(code to value)
            i += 2
        }

        val outLines = ArrayList<DxfLine>()
        val outCircles = ArrayList<DxfCircle>()
        val outTexts = ArrayList<DxfText>()
        val outPoints = ArrayList<DxfPoint>()
        val layerTable = linkedMapOf<String, DxfLayerInfo>()

        fun ensureLayer(name: String, color: Int = 7) {
            layerTable.putIfAbsent(name, DxfLayerInfo(name, if (color != 256) color else 7))
        }

        fun addSegs(pts: List<Pair<Double, Double>>, layer: String, color: Int, closed: Boolean = false) {
            if (pts.size < 2) return
            for (k in 0 until pts.size - 1) {
                val (ax, ay) = pts[k]; val (bx, by) = pts[k + 1]
                outLines += DxfLine(ax, ay, bx, by, layer, color)
            }
            if (closed && pts.size >= 2) {
                val (ax, ay) = pts.last(); val (bx, by) = pts.first()
                outLines += DxfLine(ax, ay, bx, by, layer, color)
            }
            ensureLayer(layer, color)
        }

        var idx = 0
        while (idx < pairs.size) {
            val (gc, gv) = pairs[idx]
            if (gc == 0) {
                val ent = gv.trim().uppercase()
                idx++
                when (ent) {
                    "LINE" -> {
                        var x1 = 0.0; var y1 = 0.0; var z1 = 0.0
                        var x2 = 0.0; var y2 = 0.0; var z2 = 0.0
                        var layer = "0"; var color = 256
                        while (idx < pairs.size && pairs[idx].first != 0) {
                            val (c, v) = pairs[idx]; idx++
                            when (c) {
                                8 -> layer = v.trim()
                                62 -> color = v.trim().toIntOrNull() ?: 256
                                10 -> x1 = v.trim().toDoubleOrNull() ?: 0.0
                                20 -> y1 = v.trim().toDoubleOrNull() ?: 0.0
                                30 -> z1 = v.trim().toDoubleOrNull() ?: 0.0
                                11 -> x2 = v.trim().toDoubleOrNull() ?: 0.0
                                21 -> y2 = v.trim().toDoubleOrNull() ?: 0.0
                                31 -> z2 = v.trim().toDoubleOrNull() ?: 0.0
                            }
                        }
                        outLines += DxfLine(x1, y1, x2, y2, layer, color, z1, z2)
                        ensureLayer(layer, color)
                    }
                    "CIRCLE" -> {
                        var x = 0.0; var y = 0.0; var z = 0.0; var r = 0.0
                        var layer = "0"; var color = 256
                        while (idx < pairs.size && pairs[idx].first != 0) {
                            val (c, v) = pairs[idx]; idx++
                            when (c) {
                                8 -> layer = v.trim()
                                62 -> color = v.trim().toIntOrNull() ?: 256
                                10 -> x = v.trim().toDoubleOrNull() ?: 0.0
                                20 -> y = v.trim().toDoubleOrNull() ?: 0.0
                                30 -> z = v.trim().toDoubleOrNull() ?: 0.0
                                40 -> r = v.trim().toDoubleOrNull() ?: 0.0
                            }
                        }
                        if (r > 0) {
                            outCircles += DxfCircle(x, y, r, layer, color, z)
                            ensureLayer(layer, color)
                        }
                    }
                    "ARC" -> {
                        var x = 0.0; var y = 0.0; var r = 0.0
                        var a0 = 0.0; var a1 = 0.0
                        var layer = "0"; var color = 256
                        while (idx < pairs.size && pairs[idx].first != 0) {
                            val (c, v) = pairs[idx]; idx++
                            when (c) {
                                8 -> layer = v.trim()
                                62 -> color = v.trim().toIntOrNull() ?: 256
                                10 -> x = v.trim().toDoubleOrNull() ?: 0.0
                                20 -> y = v.trim().toDoubleOrNull() ?: 0.0
                                40 -> r = v.trim().toDoubleOrNull() ?: 0.0
                                50 -> a0 = v.trim().toDoubleOrNull() ?: 0.0
                                51 -> a1 = v.trim().toDoubleOrNull() ?: 0.0
                            }
                        }
                        if (r > 0) addSegs(sampleArc(x, y, r, a0, a1), layer, color, false)
                    }
                    "POINT" -> {
                        var x = 0.0; var y = 0.0; var z = 0.0
                        var layer = "0"; var color = 256
                        while (idx < pairs.size && pairs[idx].first != 0) {
                            val (c, v) = pairs[idx]; idx++
                            when (c) {
                                8 -> layer = v.trim()
                                62 -> color = v.trim().toIntOrNull() ?: 256
                                10 -> x = v.trim().toDoubleOrNull() ?: 0.0
                                20 -> y = v.trim().toDoubleOrNull() ?: 0.0
                                30 -> z = v.trim().toDoubleOrNull() ?: 0.0
                            }
                        }
                        outPoints += DxfPoint(x, y, z, layer, color)
                        outCircles += DxfCircle(x, y, 0.12, layer, color, z)
                        ensureLayer(layer, color)
                    }
                    "LWPOLYLINE" -> {
                        var layer = "0"; var color = 256
                        var closed = false
                        data class V(var x: Double = 0.0, var y: Double = 0.0, var bulge: Double = 0.0)
                        val verts = ArrayList<V>()
                        var cur: V? = null
                        while (idx < pairs.size && pairs[idx].first != 0) {
                            val (c, v) = pairs[idx]; idx++
                            when (c) {
                                8 -> layer = v.trim()
                                62 -> color = v.trim().toIntOrNull() ?: 256
                                70 -> closed = ((v.trim().toIntOrNull() ?: 0) and 1) != 0
                                10 -> {
                                    cur = V(x = v.trim().toDoubleOrNull() ?: 0.0)
                                    verts += cur!!
                                }
                                20 -> cur?.y = v.trim().toDoubleOrNull() ?: 0.0
                                42 -> cur?.bulge = v.trim().toDoubleOrNull() ?: 0.0
                            }
                        }
                        if (verts.size >= 2) {
                            val allPts = ArrayList<Pair<Double, Double>>()
                            val n = verts.size
                            val segCount = if (closed) n else n - 1
                            for (s in 0 until segCount) {
                                val a = verts[s]
                                val b = verts[(s + 1) % n]
                                val arcPts = bulgeArc(a.x, a.y, b.x, b.y, a.bulge)
                                if (allPts.isEmpty()) allPts += arcPts
                                else allPts += arcPts.drop(1)
                            }
                            addSegs(allPts, layer, color, false)
                        }
                    }
                    "POLYLINE" -> {
                        var layer = "0"; var color = 256
                        var closed = false
                        while (idx < pairs.size && pairs[idx].first != 0) {
                            val (c, v) = pairs[idx]; idx++
                            when (c) {
                                8 -> layer = v.trim()
                                62 -> color = v.trim().toIntOrNull() ?: 256
                                70 -> closed = ((v.trim().toIntOrNull() ?: 0) and 1) != 0
                            }
                        }
                        val verts = ArrayList<Pair<Double, Double>>()
                        while (idx < pairs.size) {
                            if (pairs[idx].first == 0) {
                                val name = pairs[idx].second.trim().uppercase()
                                if (name == "SEQEND") { idx++; break }
                                if (name != "VERTEX") break
                                idx++
                                var vx = 0.0; var vy = 0.0
                                while (idx < pairs.size && pairs[idx].first != 0) {
                                    val (c, v) = pairs[idx]; idx++
                                    when (c) {
                                        10 -> vx = v.trim().toDoubleOrNull() ?: 0.0
                                        20 -> vy = v.trim().toDoubleOrNull() ?: 0.0
                                    }
                                }
                                verts += vx to vy
                            } else idx++
                        }
                        addSegs(verts, layer, color, closed)
                    }
                    "TEXT", "MTEXT" -> {
                        var x = 0.0; var y = 0.0; var z = 0.0
                        var h = 0.25; var text = ""; var layer = "0"; var color = 256
                        var rot = 0.0
                        while (idx < pairs.size && pairs[idx].first != 0) {
                            val (c, v) = pairs[idx]; idx++
                            when (c) {
                                8 -> layer = v.trim()
                                62 -> color = v.trim().toIntOrNull() ?: 256
                                10 -> x = v.trim().toDoubleOrNull() ?: 0.0
                                20 -> y = v.trim().toDoubleOrNull() ?: 0.0
                                30 -> z = v.trim().toDoubleOrNull() ?: 0.0
                                40 -> h = v.trim().toDoubleOrNull() ?: 0.25
                                50 -> rot = v.trim().toDoubleOrNull() ?: 0.0
                                1, 3 -> text = if (text.isEmpty()) v else text + v
                            }
                        }
                        text = if (ent == "MTEXT") cleanMText(text) else text
                        if (text.isNotBlank()) {
                            outTexts += DxfText(x, y, h, text, layer, color, z, rot)
                            ensureLayer(layer, color)
                        }
                    }
                    else -> {
                        while (idx < pairs.size && pairs[idx].first != 0) idx++
                    }
                }
            } else {
                idx++
            }
        }

        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        fun extend(x: Double, y: Double) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        outLines.forEach { extend(it.x1, it.y1); extend(it.x2, it.y2) }
        outCircles.forEach {
            extend(it.x - it.r, it.y - it.r)
            extend(it.x + it.r, it.y + it.r)
        }
        outTexts.forEach { extend(it.x, it.y) }
        outPoints.forEach { extend(it.x, it.y) }
        if (minX == Double.POSITIVE_INFINITY) {
            minX = 0.0; minY = 0.0; maxX = 1.0; maxY = 1.0
        }
        val pad = ((maxX - minX).coerceAtLeast(maxY - minY)) * 0.05 + 1.0
        return DxfModel(
            outLines, outCircles, outTexts, layerTable,
            minX - pad, minY - pad, maxX + pad, maxY + pad,
            outPoints
        )
    }

    fun horizontalDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    fun aciToColor(aci: Int): Color {
        val c = if (aci < 0) -aci else aci
        return when (c) {
            0, 250, 251, 252 -> Color.Black
            1, 10 -> Color(0xFFE53935)
            2, 50 -> Color(0xFFFDD835)
            3, 60, 80 -> Color(0xFF43A047)
            4, 140 -> Color(0xFF00ACC1)
            5, 150, 160 -> Color(0xFF1E88E5)
            6, 210 -> Color(0xFFD81B60)
            7, 255 -> Color(0xFFE0E0E0)
            8, 9 -> Color(0xFF9E9E9E)
            30, 40 -> Color(0xFFFB8C00)
            200 -> Color(0xFF8E24AA)
            else -> Color(0xFF90A4AE)
        }
    }
}
