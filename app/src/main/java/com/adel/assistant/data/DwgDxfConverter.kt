package com.adel.assistant.data

/**
 * مبدل DWG / DXF → DXF R12 (AC1009)
 *
 * محدودیت صادقانه:
 * - فایل DXF (متنی) کامل خوانده و به R12 بازنویسی می‌شود.
 * - بعضی فایل‌های .dwg در واقع DXF هستند یا DXF جاسازی‌شده دارند — استخراج می‌شوند.
 * - DWG باینری جدید Autodesk بدون SDK رسمی (ODA/AutoCAD) قابل تبدیل کامل نیست.
 */
object DwgDxfConverter {

    data class Result(
        val ok: Boolean,
        val dxf: String,
        val message: String,
        val lines: Int = 0,
        val circles: Int = 0,
        val texts: Int = 0,
        val points: Int = 0,
        val format: String = ""
    )

    fun convert(bytes: ByteArray, fileName: String = ""): Result {
        val name = fileName.lowercase()
        val ascii = bytesToAscii(bytes)
        val header = bytes.take(16).toByteArray().toString(Charsets.ISO_8859_1)

        val looksDxf = isAsciiDxf(ascii)
        val embedded = if (!looksDxf) extractEmbeddedDxf(ascii) else null
        val sourceText = when {
            looksDxf -> ascii
            embedded != null -> embedded
            else -> null
        }

        if (sourceText != null) {
            val model = parseDrawing(sourceText)
            if (model.isEmpty && model.points.isEmpty()) {
                // فایل DXF خالی یا فقط لایه‌ها — همان متن را نرمال کن
                val dxf = if (looksLikeR12(sourceText)) sourceText else toR12(model)
                return Result(
                    ok = dxf.contains("ENTITIES"),
                    dxf = if (dxf.contains("ENTITIES")) dxf else toR12(model),
                    message = "DXF خوانده شد اما موجودیت هندسی پیدا نشد",
                    format = "DXF"
                )
            }
            val dxf = toR12(model)
            return Result(
                ok = true,
                dxf = dxf,
                message = "تبدیل شد: ${model.lines.size} خط، ${model.circles.size} دایره، " +
                    "${model.texts.size} متن، ${model.points.size} نقطه",
                lines = model.lines.size,
                circles = model.circles.size,
                texts = model.texts.size,
                points = model.points.size,
                format = if (looksDxf) "DXF" else "DWG→DXF"
            )
        }

        val ver = dwgVersion(header)
        return Result(
            ok = false,
            dxf = "",
            message = if (ver != null)
                "این فایل DWG باینری ($ver) است. مبدل داخلی موجودیت‌های فشردهٔ نسخه‌های جدید را کامل باز نمی‌کند. " +
                    "در اتوکد: Save As → DXF (R12/2000) و همان فایل را اینجا باز کن."
            else
                "فرمت فایل شناسایی نشد. فایل DXF متنی یا DWG با DXF جاسازی‌شده بده.",
            format = ver ?: name.substringAfterLast('.', "unknown")
        )
    }

    private data class Pt(val x: Double, val y: Double, val z: Double, val layer: String)
    private data class Drawing(
        val lines: List<DxfLine>,
        val circles: List<DxfCircle>,
        val texts: List<DxfText>,
        val points: List<Pt>
    ) {
        val isEmpty: Boolean get() = lines.isEmpty() && circles.isEmpty() && texts.isEmpty()
    }

    private fun parseDrawing(content: String): Drawing {
        val base = DxfParser.parse(content)
        val extraPts = mutableListOf<Pt>()
        val extraLines = mutableListOf<DxfLine>()

        val pairs = mutableListOf<Pair<Int, String>>()
        val raw = content.replace("\r\n", "\n").replace("\r", "\n").lines()
        var i = 0
        while (i + 1 < raw.size) {
            val code = raw[i].trim().toIntOrNull()
            val value = raw[i + 1]
            if (code != null) pairs.add(code to value)
            i += 2
        }
        var idx = 0
        var inEntities = false
        fun next(): Pair<Int, String>? = if (idx < pairs.size) pairs[idx++] else null
        fun peek(): Pair<Int, String>? = if (idx < pairs.size) pairs[idx] else null
        while (idx < pairs.size) {
            val (c, v) = next() ?: break
            if (c == 0 && v.trim() == "SECTION") {
                val n = next()
                inEntities = n?.first == 2 && n.second.trim() == "ENTITIES"
                continue
            }
            if (c == 0 && v.trim() == "ENDSEC") { inEntities = false; continue }
            if (!inEntities || c != 0) continue
            when (v.trim()) {
                "POINT" -> {
                    var x = 0.0; var y = 0.0; var z = 0.0; var layer = "0"
                    while (true) {
                        val p = peek() ?: break
                        if (p.first == 0) break
                        val (gc, gv) = next()!!
                        when (gc) {
                            8 -> layer = gv.trim().ifBlank { "0" }
                            10 -> x = gv.trim().toDoubleOrNull() ?: 0.0
                            20 -> y = gv.trim().toDoubleOrNull() ?: 0.0
                            30 -> z = gv.trim().toDoubleOrNull() ?: 0.0
                        }
                    }
                    extraPts += Pt(x, y, z, layer)
                }
                "LWPOLYLINE", "POLYLINE" -> {
                    val verts = mutableListOf<Pair<Double, Double>>()
                    var layer = "0"
                    var closed = false
                    while (true) {
                        val p = peek() ?: break
                        if (p.first == 0) {
                            if (p.second.trim() == "VERTEX") {
                                next()
                                var vx = 0.0; var vy = 0.0
                                while (true) {
                                    val q = peek() ?: break
                                    if (q.first == 0) break
                                    val (gc, gv) = next()!!
                                    when (gc) {
                                        10 -> vx = gv.trim().toDoubleOrNull() ?: vx
                                        20 -> vy = gv.trim().toDoubleOrNull() ?: vy
                                    }
                                }
                                verts += vx to vy
                                continue
                            }
                            if (p.second.trim() == "SEQEND") { next(); break }
                            break
                        }
                        val (gc, gv) = next()!!
                        when (gc) {
                            8 -> layer = gv.trim().ifBlank { "0" }
                            70 -> closed = ((gv.trim().toIntOrNull() ?: 0) and 1) != 0
                            10 -> {
                                val x = gv.trim().toDoubleOrNull() ?: 0.0
                                var y = 0.0
                                val n = peek()
                                if (n?.first == 20) {
                                    y = next()!!.second.trim().toDoubleOrNull() ?: 0.0
                                }
                                verts += x to y
                            }
                        }
                    }
                    if (verts.size >= 2) {
                        for (k in 0 until verts.size - 1) {
                            extraLines += DxfLine(
                                verts[k].first, verts[k].second,
                                verts[k + 1].first, verts[k + 1].second,
                                layer, 256
                            )
                        }
                        if (closed) {
                            extraLines += DxfLine(
                                verts.last().first, verts.last().second,
                                verts.first().first, verts.first().second,
                                layer, 256
                            )
                        }
                    }
                }
            }
        }
        return Drawing(
            lines = base.lines + extraLines,
            circles = base.circles,
            texts = base.texts,
            points = extraPts
        )
    }

    private fun toR12(m: Drawing): String = buildString {
        fun w(code: Int, value: Any) {
            append(code).append("\r\n").append(value).append("\r\n")
        }
        w(0, "SECTION"); w(2, "HEADER")
        w(9, "\$ACADVER"); w(1, "AC1009")
        w(0, "ENDSEC")
        w(0, "SECTION"); w(2, "TABLES")
        w(0, "TABLE"); w(2, "LAYER"); w(70, 1)
        w(0, "LAYER"); w(2, "0"); w(70, 0); w(62, 7); w(6, "CONTINUOUS")
        w(0, "ENDTAB")
        w(0, "ENDSEC")
        w(0, "SECTION"); w(2, "ENTITIES")
        m.lines.forEach { ln ->
            w(0, "LINE"); w(8, ln.layer.ifBlank { "0" })
            w(10, fmt(ln.x1)); w(20, fmt(ln.y1)); w(30, "0.0")
            w(11, fmt(ln.x2)); w(21, fmt(ln.y2)); w(31, "0.0")
        }
        m.circles.forEach { c ->
            w(0, "CIRCLE"); w(8, c.layer.ifBlank { "0" })
            w(10, fmt(c.x)); w(20, fmt(c.y)); w(30, "0.0"); w(40, fmt(c.r))
        }
        m.texts.forEach { t ->
            w(0, "TEXT"); w(8, t.layer.ifBlank { "0" })
            w(10, fmt(t.x)); w(20, fmt(t.y)); w(30, "0.0")
            w(40, fmt(if (t.height > 0) t.height else 0.25))
            w(1, t.text.replace("\r", " ").replace("\n", " "))
        }
        m.points.forEach { p ->
            w(0, "POINT"); w(8, p.layer.ifBlank { "0" })
            w(10, fmt(p.x)); w(20, fmt(p.y)); w(30, fmt(p.z))
        }
        w(0, "ENDSEC")
        w(0, "EOF")
    }

    private fun fmt(v: Double): String = formatEn("%.6f", v)

    private fun bytesToAscii(bytes: ByteArray): String {
        // DXF معمولاً ASCII/ANSI است؛ UTF-8 خرابش نکند
        val utf = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
        if (utf != null && isAsciiDxf(utf)) return utf
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun isAsciiDxf(text: String): Boolean {
        val t = text.trimStart()
        if (t.startsWith("0") && (t.contains("SECTION") || t.contains("ENTITIES") || t.contains("EOF"))) {
            return true
        }
        return t.contains("\nSECTION") && t.contains("ENTITIES")
    }

    private fun looksLikeR12(text: String): Boolean =
        text.contains("AC1009") && text.contains("EOF")

    private fun extractEmbeddedDxf(text: String): String? {
        val idx = text.indexOf("0\nSECTION")
            .let { if (it >= 0) it else text.indexOf("0\r\nSECTION") }
            .let { if (it >= 0) it else text.indexOf("  0\nSECTION") }
        if (idx < 0) return null
        val slice = text.substring(idx)
        if (!slice.contains("ENTITIES")) return null
        val end = slice.lastIndexOf("EOF").let { if (it >= 0) it + 3 else slice.length }
        return slice.substring(0, end)
    }

    private fun dwgVersion(header: String): String? {
        val h = header
        return when {
            h.startsWith("AC1032") -> "2018+"
            h.startsWith("AC1027") -> "2013"
            h.startsWith("AC1024") -> "2010"
            h.startsWith("AC1021") -> "2007"
            h.startsWith("AC1018") -> "2004"
            h.startsWith("AC1015") -> "2000"
            h.startsWith("AC1014") -> "R14"
            h.startsWith("AC1012") -> "R13"
            h.startsWith("AC1009") -> "R12"
            h.startsWith("AC") -> h.take(6)
            else -> null
        }
    }
}
