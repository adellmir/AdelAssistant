package com.adel.assistant.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** نقطه برای الاین */
data class AlignPoint(
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double = 0.0
)

data class AlignPair(
    val source: AlignPoint, // سری دوم (برداشت)
    val target: AlignPoint  // سری اول (مرجع)
)

/**
 * پارامترهای تبدیل ۲بعدی:
 * X = Tx + a*x - b*y
 * Y = Ty + b*x + a*y
 * a = s·cosθ ، b = s·sinθ
 */
data class AlignParams(
    val tx: Double,
    val ty: Double,
    val a: Double,
    val b: Double,
    val useScale: Boolean,
    val residualRms: Double,
    val pairCount: Int
) {
    val scale: Double get() = sqrt(a * a + b * b)
    val rotationDeg: Double get() = Math.toDegrees(atan2(b, a))

    fun transform(x: Double, y: Double): Pair<Double, Double> {
        val X = tx + a * x - b * y
        val Y = ty + b * x + a * y
        return X to Y
    }

    fun transform(p: AlignPoint): AlignPoint {
        val (X, Y) = transform(p.x, p.y)
        return p.copy(x = X, y = Y)
    }
}

object AlignTransform {

    /**
     * محاسبه تبدیل از جفت نقاط مشترک.
     * @param useScale اگر false فقط انتقال+دوران (مقیاس=۱)
     */
    fun compute(pairs: List<AlignPair>, useScale: Boolean): AlignParams {
        require(pairs.size >= 2) { "حداقل ۲ نقطهٔ مشترک لازم است" }

        val n = pairs.size
        var sx = 0.0; var sy = 0.0; var sX = 0.0; var sY = 0.0
        pairs.forEach { p ->
            sx += p.source.x; sy += p.source.y
            sX += p.target.x; sY += p.target.y
        }
        val cx = sx / n; val cy = sy / n
        val cX = sX / n; val cY = sY / n

        var sumRr = 0.0 // Σ(x'²+y'²)
        var sum_xX = 0.0
        var sum_yY = 0.0
        var sum_xY = 0.0
        var sum_yX = 0.0
        pairs.forEach { p ->
            val xp = p.source.x - cx
            val yp = p.source.y - cy
            val Xp = p.target.x - cX
            val Yp = p.target.y - cY
            sumRr += xp * xp + yp * yp
            sum_xX += xp * Xp
            sum_yY += yp * Yp
            sum_xY += xp * Yp
            sum_yX += yp * Xp
        }
        if (sumRr < 1e-18) throw IllegalArgumentException("نقاط مشترک روی هم افتاده‌اند")

        var a = (sum_xX + sum_yY) / sumRr
        var b = (sum_xY - sum_yX) / sumRr

        if (!useScale) {
            val norm = sqrt(a * a + b * b)
            if (norm < 1e-18) throw IllegalArgumentException("دوران قابل محاسبه نیست")
            a /= norm
            b /= norm
        }

        val tx = cX - a * cx + b * cy
        val ty = cY - b * cx - a * cy

        // RMSE باقیمانده روی نقاط کنترل
        var sumSq = 0.0
        pairs.forEach { p ->
            val X = tx + a * p.source.x - b * p.source.y
            val Y = ty + b * p.source.x + a * p.source.y
            val dx = X - p.target.x
            val dy = Y - p.target.y
            sumSq += dx * dx + dy * dy
        }
        val rms = sqrt(sumSq / n)

        return AlignParams(tx, ty, a, b, useScale, rms, n)
    }

    /** جفت‌کردن خودکار بر اساس نام (بدون حساسیت به حروف) */
    fun matchByName(source: List<AlignPoint>, target: List<AlignPoint>): List<AlignPair> {
        val targetMap = target.associateBy { normalizeName(it.name) }
        return source.mapNotNull { s ->
            val t = targetMap[normalizeName(s.name)] ?: return@mapNotNull null
            AlignPair(source = s, target = t)
        }
    }

    fun normalizeName(s: String): String =
        s.trim().replace('ي', 'ی').replace('ك', 'ک').lowercase()

    fun parsePoints(text: String): List<AlignPoint> {
        return text.lineSequence().mapNotNull { raw ->
            val line = raw.trim().replace("\uFEFF", "")
            if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@mapNotNull null
            if (line.lowercase().contains("id") && line.lowercase().contains("x")) return@mapNotNull null
            val p = line.split(Regex("""[\s,;\t]+""")).filter { it.isNotEmpty() }
            fun num(i: Int) = p.getOrNull(i)?.replace(',', '.')?.toDoubleOrNull()
            when {
                // name X Y [Z]
                p.size >= 3 && num(1) != null && num(2) != null && p[0].toDoubleOrNull() == null ->
                    AlignPoint(p[0], num(1)!!, num(2)!!, num(3) ?: 0.0)
                // X Y Z name
                p.size >= 4 && num(0) != null && num(1) != null && num(2) != null ->
                    AlignPoint(p[3], num(0)!!, num(1)!!, num(2)!!)
                // X Y
                p.size >= 2 && num(0) != null && num(1) != null ->
                    AlignPoint((0).toString(), num(0)!!, num(1)!!, 0.0)
                else -> null
            }
        }.mapIndexed { i, pt ->
            if (pt.name.isBlank() || pt.name == "0") pt.copy(name = (i + 1).toString()) else pt
        }.toList()
    }

    fun toTxt(points: List<AlignPoint>): String =
        points.joinToString("\n") { p ->
            listOf(p.name, fmt(p.x), fmt(p.y), fmt(p.z)).joinToString("\t")
        }

    private fun fmt(v: Double): String =
        String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
}
