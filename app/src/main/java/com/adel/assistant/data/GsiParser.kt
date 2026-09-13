package com.adel.assistant.data

/**
 * پارسر GSI لیکا — هر دو مدل:
 *  - مدل ۱ (مثل B0619): زاویه/فاصله + مختصات + OCUPAR/RE
 *  - مدل ۲ (مثل BAHAR): فقط مختصات 81/82/83
 *
 * خروجی سازگار با دوربین: فقط مدل ۲ (مختصات).
 */
data class GsiPoint(
    val index: Int,
    val name: String,
    val e: Double,
    val n: Double,
    val z: Double,
    val code: String = ""
)

object GsiParser {

    private val wordRegex = Regex("""\*?(\d{2})([^\s+]*)([+-])([0-9A-Za-z.\-]{1,16})""")

    fun parse(text: String): List<GsiPoint> {
        val lines = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // علامت‌گذاری خطوطی که باید حذف شوند: OCUPAR، RE و دو خط قبل از هر کدام
        val drop = BooleanArray(lines.size)
        for (i in lines.indices) {
            val upper = lines[i].uppercase()
            val isStation = upper.contains("OCUPAR") ||
                upper.contains("00000RE") ||
                upper.contains("+00000000000000RE") ||
                (upper.contains("*41") && (upper.contains("OCUPAR") || upper.endsWith("RE") || upper.contains(" RE")))
            if (isStation) {
                drop[i] = true
                if (i >= 1) drop[i - 1] = true
                if (i >= 2) drop[i - 2] = true
            }
        }

        val points = mutableListOf<GsiPoint>()
        var seq = 0
        for (i in lines.indices) {
            if (drop[i]) continue
            val line = lines[i]
            val words = parseWords(line)
            if (words.isEmpty()) continue

            val w11 = words["11"] ?: continue
            // فقط خطوط دارای مختصات
            val w81 = words["81"] ?: continue
            val w82 = words["82"] ?: continue
            val w83 = words["83"] ?: continue

            val name = cleanName(w11)
            if (name.isBlank()) continue
            // رد کردن خود OCUPAR/RE اگر به عنوان نام آمده
            val nameUp = name.uppercase()
            if (nameUp == "OCUPAR" || nameUp == "RE") continue

            val e = parseCoord(w81)
            val n = parseCoord(w82)
            val z = parseCoord(w83)
            val code = words["71"]?.let { cleanName(it) }?.takeIf { it.isNotBlank() && it != "0" } ?: ""

            seq++
            points.add(
                GsiPoint(
                    index = seq,
                    name = name,
                    e = e,
                    n = n,
                    z = z,
                    code = code
                )
            )
        }
        return points
    }

    private fun parseWords(line: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        wordRegex.findAll(line).forEach { m ->
            val wi = m.groupValues[1]
            val sign = m.groupValues[3]
            val data = m.groupValues[4]
            map[wi] = sign + data
        }
        return map
    }

    /** نام نقطه از فیلد دادهٔ کلمه ۱۱ — صفرهای چپ را حذف، حروف را نگه می‌دارد */
    private fun cleanName(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("+") || s.startsWith("-")) s = s.substring(1)
        // حذف صفرهای پیش‌رو ولی نه کامل خالی
        s = s.trimStart('0')
        if (s.isEmpty()) s = "0"
        return s.trim()
    }

    /**
     * مختصات لیکا معمولاً به‌صورت عدد صحیح با ۳ رقم اعشار ضمنی (میلی‌متر) ذخیره می‌شود.
     * مثال: 733568997 → 733568.997
     */
    private fun parseCoord(raw: String): Double {
        var s = raw.trim()
        val neg = s.startsWith("-")
        if (s.startsWith("+") || s.startsWith("-")) s = s.substring(1)
        // فقط رقم
        val digits = s.filter { it.isDigit() }
        if (digits.isEmpty()) return 0.0
        val v = digits.toLongOrNull() ?: return 0.0
        val meters = v / 1000.0
        return if (neg) -meters else meters
    }

    // ---------- خروجی ----------

    /** خروجی متنی N E Z یا نام E N Z */
    fun toTxt(points: List<GsiPoint>, includeCode: Boolean = false): String {
        return buildString {
            points.forEach { p ->
                if (includeCode && p.code.isNotBlank()) {
                    appendLine("${p.name}\t${fmt(p.e)}\t${fmt(p.n)}\t${fmt(p.z)}\t${p.code}")
                } else {
                    appendLine("${p.name}\t${fmt(p.e)}\t${fmt(p.n)}\t${fmt(p.z)}")
                }
            }
        }
    }

    /**
     * خروجی GSI مدل ۲ (فقط مختصات) — قابل بارگذاری روی دوربین.
     * فرمت مشابه BAHAR0604.gsi
     */
    fun toGsiModel2(points: List<GsiPoint>): String {
        return buildString {
            points.forEachIndexed { idx, p ->
                val serial = (idx + 1).toString().padStart(4, '0')
                val nameField = encodeName16(p.name)
                val eField = encodeCoord16(p.e)
                val nField = encodeCoord16(p.n)
                val zField = encodeCoord16(p.z)
                // *11ssss+name 71....+0 81..10+E 82..10+N 83..10+Z
                append("*11")
                append(serial)
                append("+")
                append(nameField)
                append(" 71....+0000000000000000")
                append(" 81..10+")
                append(eField)
                append(" 82..10+")
                append(nField)
                append(" 83..10+")
                append(zField)
                append(" \r\n")
            }
        }
    }

    private fun encodeName16(name: String): String {
        val n = name.trim().take(16)
        return n.padStart(16, '0')
    }

    private fun encodeCoord16(meters: Double): String {
        val mm = kotlin.math.round(meters * 1000.0).toLong()
        val abs = kotlin.math.abs(mm)
        return abs.toString().padStart(16, '0')
    }

    private fun fmt(v: Double): String {
        return String.format(java.util.Locale.US, "%.3f", v)
    }
}
