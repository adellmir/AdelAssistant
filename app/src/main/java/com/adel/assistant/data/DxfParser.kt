package com.adel.assistant.data

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class DxfLine(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val layer: String, val color: Int)
data class DxfCircle(val x: Double, val y: Double, val r: Double, val layer: String, val color: Int)
data class DxfText(val x: Double, val y: Double, val height: Double, val text: String, val layer: String, val color: Int)
data class DxfLayerInfo(val name: String, var colorAci: Int, var visible: Boolean = true, var displayColor: Color? = null)

data class DxfModel(
    val lines: List<DxfLine>,
    val circles: List<DxfCircle>,
    val texts: List<DxfText>,
    val layers: MutableMap<String, DxfLayerInfo>,
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    val isEmpty: Boolean get() = lines.isEmpty() && circles.isEmpty() && texts.isEmpty()
    fun width() = (maxX - minX).coerceAtLeast(1.0)
    fun height() = (maxY - minY).coerceAtLeast(1.0)
}

object DxfParser {
    fun parse(content: String): DxfModel {
        val pairs = mutableListOf<Pair<Int, String>>()
        val linesIn = content.replace("\r\n", "\n").replace("\r", "\n").lines()
        var i = 0
        while (i + 1 < linesIn.size) {
            val code = linesIn[i].trim().toIntOrNull()
            val value = linesIn[i + 1]
            if (code != null) pairs.add(code to value)
            i += 2
        }

        val layerTable = linkedMapOf<String, DxfLayerInfo>()
        val outLines = mutableListOf<DxfLine>()
        val outCircles = mutableListOf<DxfCircle>()
        val outTexts = mutableListOf<DxfText>()

        var inEntities = false
        var inTables = false
        var inLayerTable = false
        var idx = 0

        fun next(): Pair<Int, String>? = if (idx < pairs.size) pairs[idx++] else null
        fun peek(): Pair<Int, String>? = if (idx < pairs.size) pairs[idx] else null

        fun addArcSegments(
            cx: Double, cy: Double, r: Double,
            startDeg: Double, endDeg: Double,
            layer: String, color: Int
        ) {
            if (r <= 1e-9) return
            var a0 = startDeg
            var a1 = endDeg
            // DXF: counter-clockwise from start to end
            while (a1 <= a0) a1 += 360.0
            val sweep = a1 - a0
            val steps = max(8, (abs(sweep) / 6.0).toInt().coerceAtMost(180))
            var prevX = cx + r * cos(Math.toRadians(a0))
            var prevY = cy + r * sin(Math.toRadians(a0))
            for (s in 1..steps) {
                val ang = a0 + sweep * s / steps
                val x = cx + r * cos(Math.toRadians(ang))
                val y = cy + r * sin(Math.toRadians(ang))
                outLines += DxfLine(prevX, prevY, x, y, layer, color)
                prevX = x; prevY = y
            }
            layerTable.putIfAbsent(layer, DxfLayerInfo(layer, 7))
        }

        /** bulge: tan(included/4); positive = CCW */
        fun addBulgeSegment(
            x1: Double, y1: Double, x2: Double, y2: Double, bulge: Double,
            layer: String, color: Int
        ) {
            if (abs(bulge) < 1e-12) {
                outLines += DxfLine(x1, y1, x2, y2, layer, color)
                return
            }
            val dx = x2 - x1
            val dy = y2 - y1
            val chord = sqrt(dx * dx + dy * dy)
            if (chord < 1e-12) return
            val included = 4.0 * atan(bulge) // radians, signed
            val sHalf = sin(included / 2.0)
            if (abs(sHalf) < 1e-12) {
                outLines += DxfLine(x1, y1, x2, y2, layer, color)
                return
            }
            val r = abs((chord / 2.0) / sHalf)
            val midX = (x1 + x2) / 2.0
            val midY = (y1 + y2) / 2.0
            val ux = dx / chord
            val uy = dy / chord
            val px = -uy // left normal
            val py = ux
            val sagOffset = (chord / 2.0) * (1 - bulge * bulge) / (2 * bulge)
            val cx = midX - sagOffset * px
            val cy = midY - sagOffset * py
            var aStart = Math.toDegrees(kotlin.math.atan2(y1 - cy, x1 - cx))
            var aEnd = Math.toDegrees(kotlin.math.atan2(y2 - cy, x2 - cx))
            if (bulge >= 0) {
                while (aEnd <= aStart) aEnd += 360.0
                addArcSegments(cx, cy, r, aStart, aEnd, layer, color)
            } else {
                while (aStart <= aEnd) aStart += 360.0
                // CW: from aStart down toward aEnd
                val sweep = aEnd - aStart // negative
                val steps = max(8, (abs(sweep) / 6.0).toInt().coerceAtMost(180))
                var prevX = x1
                var prevY = y1
                for (sStep in 1..steps) {
                    val ang = aStart + sweep * sStep / steps
                    val x = cx + r * cos(Math.toRadians(ang))
                    val y = cy + r * sin(Math.toRadians(ang))
                    outLines += DxfLine(prevX, prevY, x, y, layer, color)
                    prevX = x
                    prevY = y
                }
                layerTable.putIfAbsent(layer, DxfLayerInfo(layer, 7))
            }
        }

while (idx < pairs.size) {
            val (c, v) = next() ?: break
            if (c == 0 && v.trim() == "SECTION") {
                val n = next()
                if (n?.first == 2) {
                    when (n.second.trim()) {
                        "ENTITIES" -> { inEntities = true; inTables = false }
                        "TABLES" -> { inTables = true; inEntities = false }
                        else -> { inEntities = false; inTables = false }
                    }
                }
                continue
            }
            if (c == 0 && v.trim() == "ENDSEC") {
                inEntities = false; inTables = false; inLayerTable = false
                continue
            }
            if (inTables && c == 0 && v.trim() == "TABLE") {
                val n = next()
                inLayerTable = n?.first == 2 && n.second.trim() == "LAYER"
                continue
            }
            if (inTables && c == 0 && v.trim() == "ENDTAB") {
                inLayerTable = false
                continue
            }
            if (inLayerTable && c == 0 && v.trim() == "LAYER") {
                var name = "0"
                var col = 7
                while (true) {
                    val p = peek() ?: break
                    if (p.first == 0) break
                    val (gc, gv) = next()!!
                    when (gc) {
                        2 -> name = gv.trim().ifBlank { "0" }
                        62 -> col = gv.trim().toIntOrNull()?.let { kotlin.math.abs(it) } ?: 7
                    }
                }
                layerTable.putIfAbsent(name, DxfLayerInfo(name, col))
                continue
            }
            if (inEntities && c == 0) {
                when (v.trim().uppercase()) {
                    "LINE" -> {
                        var x1 = 0.0; var y1 = 0.0; var x2 = 0.0; var y2 = 0.0
                        var layer = "0"; var color = 256
                        while (true) {
                            val p = peek() ?: break
                            if (p.first == 0) break
                            val (gc, gv) = next()!!
                            when (gc) {
                                8 -> layer = gv.trim().ifBlank { "0" }
                                62 -> color = gv.trim().toIntOrNull() ?: 256
                                10 -> x1 = gv.trim().toDoubleOrNull() ?: 0.0
                                20 -> y1 = gv.trim().toDoubleOrNull() ?: 0.0
                                11 -> x2 = gv.trim().toDoubleOrNull() ?: 0.0
                                21 -> y2 = gv.trim().toDoubleOrNull() ?: 0.0
                            }
                        }
                        outLines += DxfLine(x1, y1, x2, y2, layer, color)
                        layerTable.putIfAbsent(layer, DxfLayerInfo(layer, 7))
                    }
                    "CIRCLE" -> {
                        var x = 0.0; var y = 0.0; var r = 0.0
                        var layer = "0"; var color = 256
                        while (true) {
                            val p = peek() ?: break
                            if (p.first == 0) break
                            val (gc, gv) = next()!!
                            when (gc) {
                                8 -> layer = gv.trim().ifBlank { "0" }
                                62 -> color = gv.trim().toIntOrNull() ?: 256
                                10 -> x = gv.trim().toDoubleOrNull() ?: 0.0
                                20 -> y = gv.trim().toDoubleOrNull() ?: 0.0
                                40 -> r = gv.trim().toDoubleOrNull() ?: 0.0
                            }
                        }
                        if (r > 0) {
                            outCircles += DxfCircle(x, y, r, layer, color)
                            layerTable.putIfAbsent(layer, DxfLayerInfo(layer, 7))
                        }
                    }
                    "ARC" -> {
                        var x = 0.0; var y = 0.0; var r = 0.0
                        var a0 = 0.0; var a1 = 0.0
                        var layer = "0"; var color = 256
                        while (true) {
                            val p = peek() ?: break
                            if (p.first == 0) break
                            val (gc, gv) = next()!!
                            when (gc) {
                                8 -> layer = gv.trim().ifBlank { "0" }
                                62 -> color = gv.trim().toIntOrNull() ?: 256
                                10 -> x = gv.trim().toDoubleOrNull() ?: 0.0
                                20 -> y = gv.trim().toDoubleOrNull() ?: 0.0
                                40 -> r = gv.trim().toDoubleOrNull() ?: 0.0
                                50 -> a0 = gv.trim().toDoubleOrNull() ?: 0.0
                                51 -> a1 = gv.trim().toDoubleOrNull() ?: 0.0
                            }
                        }
                        if (r > 0) addArcSegments(x, y, r, a0, a1, layer, color)
                    }
                    "LWPOLYLINE" -> {
                        var layer = "0"; var color = 256
                        var closed = false
                        data class Vtx(var x: Double = 0.0, var y: Double = 0.0, var bulge: Double = 0.0)
                        val verts = mutableListOf<Vtx>()
                        var cur: Vtx? = null
                        while (true) {
                            val p = peek() ?: break
                            if (p.first == 0) break
                            val (gc, gv) = next()!!
                            when (gc) {
                                8 -> layer = gv.trim().ifBlank { "0" }
                                62 -> color = gv.trim().toIntOrNull() ?: 256
                                70 -> closed = ((gv.trim().toIntOrNull() ?: 0) and 1) != 0
                                10 -> {
                                    cur = Vtx(x = gv.trim().toDoubleOrNull() ?: 0.0)
                                    verts += cur!!
                                }
                                20 -> cur?.y = gv.trim().toDoubleOrNull() ?: 0.0
                                42 -> cur?.bulge = gv.trim().toDoubleOrNull() ?: 0.0
                            }
                        }
                        if (verts.size >= 2) {
                            for (vi in 0 until verts.size - 1) {
                                val a = verts[vi]; val b = verts[vi + 1]
                                addBulgeSegment(a.x, a.y, b.x, b.y, a.bulge, layer, color)
                            }
                            if (closed) {
                                val a = verts.last(); val b = verts.first()
                                addBulgeSegment(a.x, a.y, b.x, b.y, a.bulge, layer, color)
                            }
                        }
                    }
                    "POLYLINE" -> {
                        // legacy: collect VERTEX until SEQEND — simplified as straight segments
                        var layer = "0"; var color = 256
                        var closed = false
                        while (true) {
                            val p = peek() ?: break
                            if (p.first == 0) break
                            val (gc, gv) = next()!!
                            when (gc) {
                                8 -> layer = gv.trim().ifBlank { "0" }
                                62 -> color = gv.trim().toIntOrNull() ?: 256
                                70 -> closed = ((gv.trim().toIntOrNull() ?: 0) and 1) != 0
                            }
                        }
                        val verts = mutableListOf<Pair<Double, Double>>()
                        while (true) {
                            val p = peek() ?: break
                            if (p.first == 0 && p.second.trim().uppercase() == "SEQEND") {
                                next(); break
                            }
                            if (p.first == 0 && p.second.trim().uppercase() == "VERTEX") {
                                next()
                                var x = 0.0; var y = 0.0
                                while (true) {
                                    val q = peek() ?: break
                                    if (q.first == 0) break
                                    val (gc, gv) = next()!!
                                    when (gc) {
                                        10 -> x = gv.trim().toDoubleOrNull() ?: 0.0
                                        20 -> y = gv.trim().toDoubleOrNull() ?: 0.0
                                    }
                                }
                                verts += x to y
                            } else if (p.first == 0) break
                            else next()
                        }
                        for (vi in 0 until verts.size - 1) {
                            val a = verts[vi]; val b = verts[vi + 1]
                            outLines += DxfLine(a.first, a.second, b.first, b.second, layer, color)
                        }
                        if (closed && verts.size >= 2) {
                            val a = verts.last(); val b = verts.first()
                            outLines += DxfLine(a.first, a.second, b.first, b.second, layer, color)
                        }
                        if (verts.isNotEmpty()) layerTable.putIfAbsent(layer, DxfLayerInfo(layer, 7))
                    }
                    "TEXT", "MTEXT" -> {
                        var x = 0.0; var y = 0.0; var h = 0.25; var text = ""
                        var layer = "0"; var color = 256
                        while (true) {
                            val p = peek() ?: break
                            if (p.first == 0) break
                            val (gc, gv) = next()!!
                            when (gc) {
                                8 -> layer = gv.trim().ifBlank { "0" }
                                62 -> color = gv.trim().toIntOrNull() ?: 256
                                10 -> x = gv.trim().toDoubleOrNull() ?: 0.0
                                20 -> y = gv.trim().toDoubleOrNull() ?: 0.0
                                40 -> h = gv.trim().toDoubleOrNull() ?: 0.25
                                1 -> text = gv
                                3 -> if (text.isBlank()) text = gv else text += gv
                            }
                        }
                        if (text.isNotBlank()) {
                            outTexts += DxfText(x, y, h, text, layer, color)
                            layerTable.putIfAbsent(layer, DxfLayerInfo(layer, 7))
                        }
                    }
                    else -> {
                        // skip unknown entity body
                        while (true) {
                            val p = peek() ?: break
                            if (p.first == 0) break
                            next()
                        }
                    }
                }
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
        if (minX == Double.POSITIVE_INFINITY) {
            minX = 0.0; minY = 0.0; maxX = 1.0; maxY = 1.0
        }
        val pad = ((maxX - minX).coerceAtLeast(maxY - minY)) * 0.05 + 1.0
        return DxfModel(outLines, outCircles, outTexts, layerTable, minX - pad, minY - pad, maxX + pad, maxY + pad)
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
