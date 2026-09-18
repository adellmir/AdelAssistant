package com.adel.assistant.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** نقطه برای الاین چندنقطه‌ای */
data class AlignPoint(
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double = 0.0
)

data class AlignPair(
    val source: AlignPoint,
    val target: AlignPoint
)

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

    fun parsePoints(text: String): List<AlignPoint> {
        val out = mutableListOf<AlignPoint>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val p = line.split(Regex("[,;\\t]+")).map { it.trim() }.filter { it.isNotEmpty() }
            if (p.size < 3) return@forEach
            fun d(i: Int) = p.getOrNull(i)?.replace(',', '.')?.toDoubleOrNull()
            try {
                when {
                    p.size >= 4 && d(1) != null && d(2) != null -> {
                        val name = p[0]
                        val x = d(1)!!; val y = d(2)!!
                        val z = d(3) ?: 0.0
                        out += AlignPoint(name, x, y, z)
                    }
                    d(0) != null && d(1) != null -> {
                        val x = d(0)!!; val y = d(1)!!
                        val z = d(2) ?: 0.0
                        val name = p.getOrNull(3) ?: (out.size + 1).toString()
                        out += AlignPoint(name, x, y, z)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

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
        var sumRr = 0.0
        var sum_xX = 0.0; var sum_yY = 0.0; var sum_xY = 0.0; var sum_yX = 0.0
        pairs.forEach { p ->
            val xp = p.source.x - cx; val yp = p.source.y - cy
            val Xp = p.target.x - cX; val Yp = p.target.y - cY
            sumRr += xp * xp + yp * yp
            sum_xX += xp * Xp; sum_yY += yp * Yp
            sum_xY += xp * Yp; sum_yX += yp * Xp
        }
        if (sumRr < 1e-18) throw IllegalArgumentException("نقاط مشترک روی هم افتاده‌اند")
        var a = (sum_xX + sum_yY) / sumRr
        var b = (sum_xY - sum_yX) / sumRr
        if (!useScale) {
            val norm = sqrt(a * a + b * b)
            if (norm < 1e-18) throw IllegalArgumentException("دوران قابل محاسبه نیست")
            a /= norm; b /= norm
        }
        val tx = cX - a * cx + b * cy
        val ty = cY - b * cx - a * cy
        var sse = 0.0
        pairs.forEach { p ->
            val (X, Y) = Pair(tx + a * p.source.x - b * p.source.y, ty + b * p.source.x + a * p.source.y)
            val dx = X - p.target.x; val dy = Y - p.target.y
            sse += dx * dx + dy * dy
        }
        val rms = sqrt(sse / n)
        return AlignParams(tx, ty, a, b, useScale, rms, n)
    }

    data class SimpleResult(
        val points: List<GsiPoint>,
        val residualB: Triple<Double, Double, Double>,
        val scale: Double,
        val angleDeg: Double,
        val message: String
    )

    /**
     * الاین ساده: مبنا A→A' (XYZ)، دوران AB→A'B'، مقیاس و میانگین اختیاری.
     * ارتفاع اول با مبنا مچ می‌شود؛ مقیاس/میانگین فقط در صورت فعال بودن روی Z هم اثر دارد.
     */
    fun alignSimple(
        points: List<GsiPoint>,
        baseName: String,
        dirName: String,
        targetBase: Triple<Double, Double, Double>,
        targetDir: Triple<Double, Double, Double>,
        useScale: Boolean,
        useAverage: Boolean
    ): SimpleResult {
        fun match(p: GsiPoint, name: String) =
            p.name.equals(name, true) || p.code.equals(name, true)

        val a = points.find { match(it, baseName) }
            ?: return SimpleResult(points, Triple(0.0, 0.0, 0.0), 1.0, 0.0, "نقطه مبنا «$baseName» پیدا نشد")
        val b = points.find { match(it, dirName) }
            ?: return SimpleResult(points, Triple(0.0, 0.0, 0.0), 1.0, 0.0, "نقطه جهت «$dirName» پیدا نشد")

        val ax = a.e; val ay = a.n; val az = a.z
        val bx = b.e; val by = b.n; val bz = b.z
        val apx = targetBase.first; val apy = targetBase.second; val apz = targetBase.third
        val bpx = targetDir.first; val bpy = targetDir.second; val bpz = targetDir.third

        val vsx = bx - ax; val vsy = by - ay
        val vtx = bpx - apx; val vty = bpy - apy
        val lenS = hypot(vsx, vsy)
        val lenT = hypot(vtx, vty)
        if (lenS < 1e-9) {
            return SimpleResult(points, Triple(0.0, 0.0, 0.0), 1.0, 0.0, "فاصله افقی مبنا تا جهت نزدیک صفر است")
        }
        val scale = if (useScale) lenT / lenS else 1.0
        val theta = atan2(vty, vtx) - atan2(vsy, vsx)
        val cosT = cos(theta); val sinT = sin(theta)

        fun mapOne(p: GsiPoint): GsiPoint {
            val dx = p.e - ax; val dy = p.n - ay; val dz = p.z - az
            val sx = dx * scale; val sy = dy * scale
            val rx = sx * cosT - sy * sinT
            val ry = sx * sinT + sy * cosT
            val rz = if (useScale) dz * scale else dz
            return p.copy(e = apx + rx, n = apy + ry, z = apz + rz)
        }

        var out = points.map { mapOne(it) }
        val bOut = out.find { it.id == b.id } ?: out.find { match(it, dirName) }
        var resX = if (bOut != null) bpx - bOut.e else 0.0
        var resY = if (bOut != null) bpy - bOut.n else 0.0
        var resZ = if (bOut != null) bpz - bOut.z else 0.0

        if (useAverage && bOut != null) {
            out = out.map {
                it.copy(e = it.e + resX / 2, n = it.n + resY / 2, z = it.z + resZ / 2)
            }
            val b2 = out.find { it.id == b.id } ?: out.find { match(it, dirName) }
            if (b2 != null) {
                resX = bpx - b2.e; resY = bpy - b2.n; resZ = bpz - b2.z
            }
        }

        val msg = buildString {
            append("الاین: مبنا=$baseName جهت=$dirName")
            append(formatEn(" | s=%.6f θ=%.4f°", scale, Math.toDegrees(theta)))
            if (useAverage) append(" | میانگین")
            append(formatEn(" | resB ΔE=%.4f ΔN=%.4f ΔZ=%.4f", resX, resY, resZ))
        }
        return SimpleResult(out, Triple(resX, resY, resZ), scale, Math.toDegrees(theta), msg)
    }
}
