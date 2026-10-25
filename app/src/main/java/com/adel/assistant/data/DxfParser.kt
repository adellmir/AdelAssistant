package com.adel.assistant.data

import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt

data class DxfLine(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val layer: String, val color: Int)
data class DxfCircle(val x: Double, val y: Double, val r: Double, val layer: String, val color: Int)
data class DxfText(val x: Double, val y: Double, val height: Double, val text: String, val layer: String, val color: Int, val rotation: Double = 0.0)
data class DxfLayerInfo(val name: String, var colorAci: Int, var visible: Boolean = true, var locked: Boolean = false, var displayColor: Color? = null)

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
        if (minX == Double.POSITIVE_INFINITY) {
            minX = 0.0; minY = 0.0; maxX = 1.0; maxY = 1.0
        }
        return copy(minX = minX, minY = minY, maxX = maxX, maxY = maxY)
    }

    fun toDxfText(): String = buildString {
        fun colorOf(entityColor: Int, layerName: String): Int {
            if (entityColor in 1..255) return entityColor
            val li = layers[layerName]
            var aci = li?.colorAci ?: 7
            li?.displayColor?.let { aci = DxfParser.colorToAci(it) }
            return aci.coerceIn(1, 255)
        }
        val layerNames = linkedSetOf<String>()
        layerNames.add("0")
        layers.keys.forEach { layerNames.add(it.ifBlank { "0" }) }
        lines.forEach { layerNames.add(it.layer.ifBlank { "0" }) }
        circles.forEach { layerNames.add(it.layer.ifBlank { "0" }) }
        texts.forEach { layerNames.add(it.layer.ifBlank { "0" }) }

        append("0\nSECTION\n2\nHEADER\n")
        append("9\n")
        append("$")
        append("INSUNITS\n70\n6\n")
        append("0\nENDSEC\n")
        append("0\nSECTION\n2\nTABLES\n")
        append("0\nTABLE\n2\nLAYER\n70\n${layerNames.size}\n")
        layerNames.forEach { name ->
            val li = layers[name]
            var aci = li?.colorAci ?: 7
            if (aci !in 1..255) aci = 7
            li?.displayColor?.let { aci = DxfParser.colorToAci(it) }
            val on = if (li?.visible == false) -aci else aci
            append("0\nLAYER\n2\n$name\n70\n0\n62\n$on\n6\nCONTINUOUS\n")
        }
        append("0\nENDTAB\n0\nENDSEC\n")
        append("0\nSECTION\n2\nENTITIES\n")
        lines.forEach { l ->
            val ly = l.layer.ifBlank { "0" }
            val col = colorOf(l.color, ly)
            append("0\nLINE\n8\n$ly\n62\n$col\n10\n${l.x1}\n20\n${l.y1}\n30\n0.0\n11\n${l.x2}\n21\n${l.y2}\n31\n0.0\n")
        }
        circles.forEach { c ->
            val ly = c.layer.ifBlank { "0" }
            val col = colorOf(c.color, ly)
            append("0\nCIRCLE\n8\n$ly\n62\n$col\n10\n${c.x}\n20\n${c.y}\n30\n0.0\n40\n${c.r}\n")
        }
        texts.forEach { tx ->
            val ly = tx.layer.ifBlank { "0" }
            val col = colorOf(tx.color, ly)
            append("0\nTEXT\n8\n$ly\n62\n$col\n10\n${tx.x}\n20\n${tx.y}\n30\n0.0\n40\n${tx.height}\n1\n${tx.text}\n50\n${tx.rotation}\n")
        }
        append("0\nENDSEC\n0\nEOF\n")
    }
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
                when (v.trim()) {
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
                        layerTable.putIfAbsent(layer, DxfLayerInfo(layer, if (color in 1..255) color else 7))
                    }
                    "CIRCLE" -> {
                        var x = 0.0; var y = 0.0; var r = 0.15
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
                                40 -> r = gv.trim().toDoubleOrNull() ?: 0.15
                            }
                        }
                        outCircles += DxfCircle(x, y, r, layer, color)
                        layerTable.putIfAbsent(layer, DxfLayerInfo(layer, if (color in 1..255) color else 7))
                    }
                    "TEXT", "MTEXT" -> {
                        var x = 0.0; var y = 0.0; var h = 0.25; var text = ""
                        var layer = "0"; var color = 250
                        while (true) {
                            val p = peek() ?: break
                            if (p.first == 0) break
                            val (gc, gv) = next()!!
                            when (gc) {
                                8 -> layer = gv.trim().ifBlank { "0" }
                                62 -> color = gv.trim().toIntOrNull() ?: 250
                                10 -> x = gv.trim().toDoubleOrNull() ?: 0.0
                                20 -> y = gv.trim().toDoubleOrNull() ?: 0.0
                                40 -> h = gv.trim().toDoubleOrNull() ?: 0.25
                                1 -> text = gv
                            }
                        }
                        if (text.isNotBlank()) {
                            outTexts += DxfText(x, y, h, text, layer, color)
                            layerTable.putIfAbsent(layer, DxfLayerInfo(layer, 7))
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
        // pad
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

    /** نزدیک‌ترین ACI استاندارد به رنگ Compose (برای ذخیره DXF) */
    fun colorToAci(color: Color): Int {
        val palette = listOf(
            1 to Color(0xFFE53935),
            2 to Color(0xFFFDD835),
            3 to Color(0xFF43A047),
            4 to Color(0xFF00ACC1),
            5 to Color(0xFF1E88E5),
            6 to Color(0xFFD81B60),
            7 to Color(0xFFE0E0E0),
            8 to Color(0xFF9E9E9E),
            30 to Color(0xFFFB8C00),
            200 to Color(0xFF8E24AA),
            0 to Color.Black
        )
        fun dist(a: Color, b: Color): Float {
            val dr = a.red - b.red
            val dg = a.green - b.green
            val db = a.blue - b.blue
            return dr * dr + dg * dg + db * db
        }
        // سفید خالص
        if (color.red > 0.9f && color.green > 0.9f && color.blue > 0.9f) return 7
        return palette.minBy { dist(color, it.second) }.first.let { if (it == 0) 7 else it }
    }
}
