package com.adel.assistant.data

import java.util.Locale

data class SurveyPoint(val id: String, val x: Double, val y: Double, val z: Double, val code: String = "")

object PointConverter {
    fun read(text: String, extension: String): List<SurveyPoint> = when (extension.lowercase()) {
        "gsi" -> parseGsi(text)
        "idx" -> parseIdx(text)
        "dxf" -> parseDxf(text)
        "kml" -> KmlParser.parseKmlText(text).points
        // DAT نقشه‌برداری: N Y X Z D
        "dat" -> parseDelimited(text, datOrder = true)
        else -> parseDelimited(text, datOrder = false)
    }

    /** خواندن از بایت (برای KMZ که باینری/زیپ است) */
    fun readBytes(bytes: ByteArray, fileName: String): List<SurveyPoint> {
        val lower = fileName.lowercase()
        val ext = extractExtension(lower)
        return when {
            ext == "kmz" || ext == "kml" || lower.endsWith(".kmz") || lower.endsWith(".kml") ->
                KmlParser.parseBytes(bytes, fileName).points
            ext == "gsi" || lower.endsWith(".gsi") -> parseGsi(bytes.toString(Charsets.UTF_8))
            ext == "dat" || lower.endsWith(".dat") ->
                parseDelimited(bytes.toString(Charsets.UTF_8), datOrder = true)
            else -> {
                val text = bytes.toString(Charsets.UTF_8)
                // اگر پسوند نامشخص است ولی ستون‌ها شبیه N,Y,X,Z هستند → DAT
                if (looksLikeDatOrder(text)) parseDelimited(text, datOrder = true)
                else read(text, ext.ifBlank { "txt" })
            }
        }
    }

    /** استخراج پسوند از نام یا URI (مثلاً content://.../Namaz%20Hashemie.dat) */
    private fun extractExtension(nameOrUri: String): String {
        val decoded = try {
            java.net.URLDecoder.decode(nameOrUri, "UTF-8")
        } catch (_: Exception) {
            nameOrUri
        }
        val base = decoded.substringAfterLast('/').substringAfterLast(':')
        val dot = base.lastIndexOf('.')
        if (dot < 0 || dot == base.lastIndex) return ""
        return base.substring(dot + 1).lowercase().takeWhile { it.isLetterOrDigit() }
    }

    /**
     * تشخیص خودکار ترتیب DAT (N Y X Z):
     * اگر در چند خط اول، ستون۲ ~ northing (معمولاً > 1e6) و ستون۳ ~ easting باشد.
     */
    private fun looksLikeDatOrder(text: String): Boolean {
        var checked = 0
        var datVotes = 0
        text.lineSequence().forEach { raw ->
            if (checked >= 8) return@forEach
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val toks = line.split(Regex("[,;\t ]+")).filter { it.isNotBlank() }
            if (toks.size < 4) return@forEach
            val c1 = toks[1].replace(',', '.').toDoubleOrNull() ?: return@forEach
            val c2 = toks[2].replace(',', '.').toDoubleOrNull() ?: return@forEach
            checked++
            // UTM zone 38-41 ایران: E ~ 2e5..8e5 ، N ~ 2.8e6..4.4e6
            if (c1 > 1_000_000 && c2 < c1 && c2 > 50_000) datVotes++
        }
        return checked > 0 && datVotes * 2 >= checked
    }

    fun write(points: List<SurveyPoint>, extension: String): String = when (extension.lowercase()) {
        "csv" -> points.joinToString("\n", "ID,X,Y,Z,CODE\n") { p -> "${p.id},${f(p.x)},${f(p.y)},${f(p.z)},${p.code}" }
        "txt" -> points.joinToString("\n") { p -> listOf(p.id, f(p.x), f(p.y), f(p.z), p.code).joinToString("\t") }
        "dat" -> points.joinToString("\n") { p -> listOf(p.id, f(p.y), f(p.x), f(p.z), p.code).joinToString("\t") }
        "dxf" -> dxf(points)
        "gsi" -> gsi(points)
        "idx" -> idx(points)
        "kml" -> UtmGeo.toKml(points)
        else -> throw IllegalArgumentException("فرمت خروجی پشتیبانی نمی‌شود")
    }

    /**
     * ارقام فارسی/عربی → انگلیسی
     */
    fun normalizeDigits(s: String): String {
        val fa = "۰۱۲۳۴۵۶۷۸۹"
        val ar = "٠١٢٣٤٥٦٧٨٩"
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val fi = fa.indexOf(ch)
            val ai = ar.indexOf(ch)
            when {
                fi >= 0 -> sb.append(fi)
                ai >= 0 -> sb.append(ai)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseNumberToken(s: String): Double? =
        s.trim().trim('"').replace(',', '.').toDoubleOrNull()

    /**
     * تجزیه یک خط نقطه.
     * جداکننده بین فیلدها: فاصله، تب، ; یا ,
     * اعشار داخل عدد: نقطه یا ویرگول (مثلاً 512345,67)
     * ترتیب عادی: N X Y Z [کد…]
     * datOrder=true: N Y X Z [کد…] → داخلی X=Easting ، Y=Northing
     */
    fun parseSurveyLine(raw: String, datOrder: Boolean = false): SurveyPoint? {
        var line = normalizeDigits(raw.trim()).removePrefix("\uFEFF")
        if (line.isBlank()) return null
        if (line.startsWith("#") || line.startsWith("//") || line.startsWith("*")) return null
        val low = line.lowercase()
        if (low.startsWith("id") && (low.contains("x") || low.contains("east") || low.contains("north"))) return null
        if (low.contains("pointno") || low.contains("header") || low == "end") return null

        // ۱) اولویت با جداکنندهٔ فاصله/تب/سمیکالن تا ویرگولِ اعشار حفظ شود
        var tokens = line.split(Regex("""[\t ;]+""")).map { it.trim().trim(',') }.filter { it.isNotEmpty() }

        // ۲) اگر فیلد کافی نبود، ویرگول را جداکننده بگیر
        if (tokens.size < 3) {
            tokens = line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }

        // ۳) هنوز کم است → استخراج همهٔ اعداد با regex
        if (tokens.size < 3) {
            val numRe = Regex("""[+-]?\d+(?:[.,]\d+)?""")
            val nums = numRe.findAll(line).mapNotNull { parseNumberToken(it.value) }.toList()
            if (nums.size < 3) return null
            val firstNum = numRe.find(line)
            val idPart = if (firstNum != null && firstNum.range.first > 0) {
                line.substring(0, firstNum.range.first).trim().trim(',', ' ', '\t')
            } else ""
            val hasId = idPart.isNotBlank() && parseNumberToken(idPart) == null
            return if (hasId && nums.size >= 3) {
                val x = if (datOrder) nums[1] else nums[0]
                val y = if (datOrder) nums[0] else nums[1]
                val z = nums[2]
                SurveyPoint(idPart.trim('"'), x, y, z, "")
            } else if (nums.size >= 3) {
                // ممکن است عدد اول شماره نقطه باشد
                val x = if (datOrder) nums[1] else nums[0]
                val y = if (datOrder) nums[0] else nums[1]
                val z = nums.getOrNull(2) ?: 0.0
                // اگر ۴ عدد و اول شبیه شماره نقطهٔ کوچک
                if (nums.size >= 4 && kotlin.math.abs(nums[0]) < 1e7 && kotlin.math.abs(nums[1]) > 1e4) {
                    val xx = if (datOrder) nums[2] else nums[1]
                    val yy = if (datOrder) nums[1] else nums[2]
                    val zz = nums[3]
                    SurveyPoint(nums[0].toLong().toString(), xx, yy, zz, "")
                } else {
                    SurveyPoint("P", x, y, z, "")
                }
            } else null
        }

        fun num(i: Int) = tokens.getOrNull(i)?.let { parseNumberToken(it) }

        // N X Y Z [code] یا N Y X Z [code]
        if (tokens.size >= 4 && num(1) != null && num(2) != null && num(3) != null) {
            val id = tokens[0].trim('"')
            val c1 = num(1)!!
            val c2 = num(2)!!
            val z = num(3)!!
            val x = if (datOrder) c2 else c1
            val y = if (datOrder) c1 else c2
            val code = tokens.drop(4).joinToString(" ").trim()
            return SurveyPoint(id, x, y, z, code)
        }

        // بدون نام: X Y Z یا Y X Z
        if (tokens.size >= 3 && num(0) != null && num(1) != null && num(2) != null) {
            // اگر ۴ مقدار عددی و اول کوچک → احتمالاً شماره نقطه
            if (tokens.size >= 4 && num(3) != null &&
                kotlin.math.abs(num(0)!!) < 1e7 && kotlin.math.abs(num(1)!!) > 1e4
            ) {
                val id = tokens[0]
                val c1 = num(1)!!
                val c2 = num(2)!!
                val z = num(3)!!
                val x = if (datOrder) c2 else c1
                val y = if (datOrder) c1 else c2
                val code = tokens.drop(4).joinToString(" ").trim()
                return SurveyPoint(id, x, y, z, code)
            }
            val c0 = num(0)!!
            val c1 = num(1)!!
            val c2 = num(2)!!
            val x = if (datOrder) c1 else c0
            val y = if (datOrder) c0 else c1
            val z = c2
            val code = tokens.drop(3).filter { parseNumberToken(it) == null }.joinToString(" ")
            return SurveyPoint("P", x, y, z, code)
        }

        // نام + X Y (بدون Z)
        if (tokens.size >= 3 && num(1) != null && num(2) != null && num(0) == null) {
            val c1 = num(1)!!
            val c2 = num(2)!!
            val x = if (datOrder) c2 else c1
            val y = if (datOrder) c1 else c2
            return SurveyPoint(tokens[0].trim('"'), x, y, 0.0, tokens.drop(3).joinToString(" "))
        }

        return null
    }

    private fun parseDelimited(text: String, datOrder: Boolean = false): List<SurveyPoint> {
        var autoId = 1
        return text.lineSequence().mapNotNull { raw ->
            val p = parseSurveyLine(raw, datOrder) ?: return@mapNotNull null
            if (p.id.isBlank() || p.id == "P") {
                SurveyPoint((autoId++).toString(), p.x, p.y, p.z, p.code)
            } else p
        }.toList()
    }


    private fun parseIdx(text: String): List<SurveyPoint> {
        val r = Regex("^\\s*\\d+\\s*,\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"([^\\\"]*)\\\"\\s*,\\s*([-+0-9.]+)\\s*,\\s*([-+0-9.]+)\\s*,\\s*([-+0-9.]+)")
        return text.lineSequence().mapNotNull { m -> r.find(m)?.let {
            SurveyPoint(it.groupValues[1], it.groupValues[3].toDouble(), it.groupValues[4].toDouble(), it.groupValues[5].toDouble(), it.groupValues[2])
        } }.toList()
    }

    /**
     * خواندن GSI:
     * - نام نقطه از فیلد دادهٔ word 11 (مثلاً S1) نه شمارندهٔ 110003
     * - کد از word 41 روی نقاط بعدی اعمال می‌شود
     * - OCUPAR و RE حذف؛ دو برداشت بعد از OCUPAR هم حذف
     * - نقاط با مختصات صفر حذف
     */
    private fun parseGsi(text: String): List<SurveyPoint> {
        val out = mutableListOf<SurveyPoint>()
        var currentCode = ""
        var skipPoints = 0

        fun decodeText(raw: String): String {
            val t = raw.trim()
            if (t.isEmpty()) return "0"
            return if (t.any { it.isLetter() }) t.trimStart('0').ifBlank { "0" }
            else t.trimStart('0').ifBlank { "0" }
        }

                fun coord(line: String, key: String): Double? {
            // فرمت‌های رایج: 81..00+ / 81..10+ / 81..10-
            val m = Regex(key + """\.\.(\d{2})([+-])([0-9]+)""").find(line) ?: return null
            val unit = m.groupValues[1]
            val sign = if (m.groupValues[2] == "-") -1.0 else 1.0
            val body = m.groupValues[3].toDoubleOrNull() ?: return null
            // ..10 معمولاً میلی‌متر (÷1000)؛ ..00 اغلب 0.1mm یا متر×10000
            val meters = when (unit) {
                "10" -> body / 1000.0
                "00" -> if (body >= 1e8) body / 1000.0 else body / 10000.0
                else -> body / 1000.0
            }
            return sign * meters
        }

        fun wordData(line: String, wi: String): String? {
            val m = Regex("""\*?""" + wi + """[0-9.]*\+([^\s]+)""").find(line) ?: return null
            return decodeText(m.groupValues[1])
        }

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val upper = line.uppercase()

            val is41 = line.contains("*41") && !line.contains("*11")
            if (is41) {
                val codeVal = wordData(line, "41") ?: ""
                if (codeVal.equals("OCUPAR", true) || upper.contains("OCUPAR")) {
                    currentCode = ""
                    skipPoints = 0 // فقط OCUPAR/RE و مختصات صفر حذف می‌شوند
                    continue
                }
                if (codeVal.equals("RE", true) || Regex("""(^|[^A-Z0-9])RE([^A-Z0-9]|$)""").containsMatchIn(upper)) {
                    continue
                }
                if (codeVal.isNotBlank() && codeVal != "0") currentCode = codeVal
                continue
            }

            if (!line.contains("*11")) continue
            if (skipPoints > 0) { skipPoints--; continue }

            val idRaw = wordData(line, "11") ?: continue
            if (idRaw.equals("OCUPAR", true) || idRaw.equals("RE", true)) continue

            val x = coord(line, "81") ?: continue
            val y = coord(line, "82") ?: continue
            val z = coord(line, "83") ?: 0.0
            if (kotlin.math.abs(x) < 1e-9 && kotlin.math.abs(y) < 1e-9) continue

            val code71 = wordData(line, "71")?.takeIf { it.isNotBlank() && it != "0" }
            val code42 = wordData(line, "42")?.takeIf { it.isNotBlank() && it != "0" }
            val code = code71 ?: code42 ?: currentCode
            out += SurveyPoint(idRaw, x, y, z, code)
        }
        return out
    }

    private fun parseDxf(text: String): List<SurveyPoint> {
        val lines = text.lines(); val out = mutableListOf<SurveyPoint>(); var i = 0; var no = 1
        while (i < lines.size - 1) {
            if (lines[i].trim() == "0" && lines[i + 1].trim().equals("POINT", true)) {
                var x: Double? = null; var y: Double? = null; var z: Double? = 0.0; var layer = ""
                i += 2
                while (i < lines.size - 1 && lines[i].trim() != "0") {
                    when (lines[i].trim()) { "10" -> x = lines[i+1].trim().toDoubleOrNull(); "20" -> y = lines[i+1].trim().toDoubleOrNull(); "30" -> z = lines[i+1].trim().toDoubleOrNull(); "8" -> layer = lines[i+1].trim() }
                    i += 2
                }
                if (x != null && y != null) out += SurveyPoint(no++.toString(), x, y, z ?: 0.0, layer)
            } else i++
        }
        return out
    }

    /** DXF R12 (AC1009) معتبر برای اتوکد */
    private fun dxf(points: List<SurveyPoint>) = buildString {
        fun layerOf(p: SurveyPoint) =
            p.code.ifBlank { "POINTS" }.replace(Regex("[^A-Za-z0-9_-]"), "_").take(31).ifBlank { "POINTS" }
        val layers = points.map { layerOf(it) }.distinct()
        append("0\r\nSECTION\r\n2\r\nHEADER\r\n9\r\n\$ACADVER\r\n1\r\nAC1009\r\n0\r\nENDSEC\r\n")
        append("0\r\nSECTION\r\n2\r\nTABLES\r\n")
        append("0\r\nTABLE\r\n2\r\nLTYPE\r\n70\r\n1\r\n")
        append("0\r\nLTYPE\r\n2\r\nCONTINUOUS\r\n70\r\n0\r\n3\r\nSolid line\r\n72\r\n65\r\n73\r\n0\r\n40\r\n0.0\r\n0\r\nENDTAB\r\n")
        append("0\r\nTABLE\r\n2\r\nLAYER\r\n70\r\n${layers.size}\r\n")
        layers.forEach { layer ->
            append("0\r\nLAYER\r\n2\r\n$layer\r\n70\r\n0\r\n62\r\n7\r\n6\r\nCONTINUOUS\r\n")
        }
        append("0\r\nENDTAB\r\n0\r\nENDSEC\r\n")
        append("0\r\nSECTION\r\n2\r\nENTITIES\r\n")
        points.forEach { p ->
            val layer = layerOf(p)
            val s = 0.10
            val gap = 0.15
            append("0\r\nLINE\r\n8\r\n$layer\r\n")
            append("10\r\n${f(p.x - s)}\r\n20\r\n${f(p.y - s)}\r\n30\r\n${f(p.z)}\r\n")
            append("11\r\n${f(p.x + s)}\r\n21\r\n${f(p.y + s)}\r\n31\r\n${f(p.z)}\r\n")
            append("0\r\nLINE\r\n8\r\n$layer\r\n")
            append("10\r\n${f(p.x - s)}\r\n20\r\n${f(p.y + s)}\r\n30\r\n${f(p.z)}\r\n")
            append("11\r\n${f(p.x + s)}\r\n21\r\n${f(p.y - s)}\r\n31\r\n${f(p.z)}\r\n")
            textEntity(layer, p.x + gap, p.y + 0.10, p.z, p.id, 0.10)
            textEntity(layer, p.x + gap, p.y, p.z, "Z=${f(p.z)}", 0.10)
            if (p.code.isNotBlank()) textEntity(layer, p.x + gap, p.y - 0.10, p.z, p.code, 0.10)
        }
        append("0\r\nENDSEC\r\n0\r\nEOF\r\n")
    }

    private fun StringBuilder.textEntity(layer: String, x: Double, y: Double, z: Double, value: String, height: Double) {
        append("0\r\nTEXT\r\n8\r\n$layer\r\n62\r\n7\r\n")
        append("10\r\n${f(x)}\r\n20\r\n${f(y)}\r\n30\r\n${f(z)}\r\n")
        append("40\r\n${f(height)}\r\n1\r\n${value.replace("\n", " ")}\r\n50\r\n0\r\n")
    }

    private fun gsi(points: List<SurveyPoint>): String {
        // خروجی مختصات مطلق شبیه BAHAR (قابل ورود به دوربین)
        return buildString {
            points.forEachIndexed { idx, p ->
                val serial = (idx + 1).toString().padStart(4, '0')
                val name = p.id.trim().ifBlank { "P${idx + 1}" }.take(16).padStart(16, '0')
                fun sc(v: Double): String {
                    val mm = kotlin.math.round(kotlin.math.abs(v) * 1000.0).toLong()
                    val body = mm.toString().padStart(16, '0')
                    return if (v < 0) "-$body" else "+$body"
                }
                append("*11")
                append(serial)
                append("+")
                append(name)
                val codeBody = p.code.trim().take(16).padStart(16, '0').ifBlank { "0000000000000000" }
                append(" 71....+")
                append(codeBody)
                append(" 81..10")
                append(sc(p.x))
                append(" 82..10")
                append(sc(p.y))
                append(" 83..10")
                append(sc(p.z))
                append("\r\n")
            }
        }
    }

    private fun idx(points: List<SurveyPoint>) = buildString {
        append("HEADER\n  VERSION      1.31\n  SYSTEM       \"STS\"\n  SEPARATOR    ','\n  TERMINATOR   ';'\nEND HEADER\n\nDATABASE\n  POINTS (PointNo,PointID,Code,East,North,Elevation,CLASS)\n")
        points.forEachIndexed { i,p -> append("    ${i+1},  \"${p.id}\",  \"${p.code}\",    ${f(p.x)},  ${f(p.y)},  ${f(p.z)},  FIX;\n") }
        append("END DATABASE\n")
    }
    private fun scaled(v: Double) = String.format(Locale.US, "%014d", kotlin.math.round(v * 10000).toLong())
    private fun f(v: Double) = String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
}
