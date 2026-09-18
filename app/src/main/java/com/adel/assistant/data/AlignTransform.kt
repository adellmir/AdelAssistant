package com.adel.assistant.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * الاین ساده نقشه‌برداری:
 * - انتقال روی نقطه مبنا A → A' (XYZ همیشه)
 * - دوران افقی حول A' تا AB روی A'B'
 * - مقیاس اختیاری (طول افقی AB؛ در صورت فعال بودن روی ΔZ هم)
 * - میانگین‌گیری اختیاری: نصف residual نقطهٔ جهت روی کل نقاط
 */
object AlignTransform {

    data class Result(
        val points: List<GsiPoint>,
        val residualB: Triple<Double, Double, Double>,
        val scale: Double,
        val angleDeg: Double,
        val message: String
    )

    /**
     * @param baseName نام نقطه مبنا در لیست (مثل B1)
     * @param dirName نام نقطه جهت (مثل B2)
     * @param targetBase مختصات هدف مبنا (E,N,Z)
     * @param targetDir مختصات هدف جهت
     * @param useScale مقیاس طول
     * @param useAverage نصف residual جهت
     */
    fun align(
        points: List<GsiPoint>,
        baseName: String,
        dirName: String,
        targetBase: Triple<Double, Double, Double>,
        targetDir: Triple<Double, Double, Double>,
        useScale: Boolean,
        useAverage: Boolean
    ): Result {
        val a = points.find { it.name.equals(baseName, true) || it.code.equals(baseName, true) }
            ?: return Result(points, Triple(0.0, 0.0, 0.0), 1.0, 0.0, "نقطه مبنا «$baseName» پیدا نشد")
        val b = points.find { it.name.equals(dirName, true) || it.code.equals(dirName, true) }
            ?: return Result(points, Triple(0.0, 0.0, 0.0), 1.0, 0.0, "نقطه جهت «$dirName» پیدا نشد")

        val ax = a.e; val ay = a.n; val az = a.z
        val bx = b.e; val by = b.n; val bz = b.z
        val apx = targetBase.first; val apy = targetBase.second; val apz = targetBase.third
        val bpx = targetDir.first; val bpy = targetDir.second; val bpz = targetDir.third

        val vsx = bx - ax; val vsy = by - ay; val vsz = bz - az
        val vtx = bpx - apx; val vty = bpy - apy; val vtz = bpz - apz
        val lenS = hypot(vsx, vsy)
        val lenT = hypot(vtx, vty)
        if (lenS < 1e-9) {
            return Result(points, Triple(0.0, 0.0, 0.0), 1.0, 0.0, "فاصله افقی مبنا تا جهت نزدیک صفر است")
        }
        val scale = if (useScale) lenT / lenS else 1.0
        val angS = atan2(vsy, vsx)
        val angT = atan2(vty, vtx)
        val theta = angT - angS
        val cosT = cos(theta)
        val sinT = sin(theta)

        fun mapOne(p: GsiPoint): GsiPoint {
            val dx = p.e - ax
            val dy = p.n - ay
            val dz = p.z - az
            val sx = dx * scale
            val sy = dy * scale
            val rx = sx * cosT - sy * sinT
            val ry = sx * sinT + sy * cosT
            // ارتفاع: اول مچ با مبنا (انتقال az→apz)؛ مقیاس ارتفاع فقط اگر useScale
            val rz = if (useScale) dz * scale else dz
            return p.copy(
                e = apx + rx,
                n = apy + ry,
                z = apz + rz
            )
        }

        var out = points.map { mapOne(it) }

        // residual نقطه جهت پس از تبدیل
        val bOut = out.find { it.id == b.id } ?: out.find {
            it.name.equals(dirName, true) || it.code.equals(dirName, true)
        }
        val resX = if (bOut != null) bpx - bOut.e else 0.0
        val resY = if (bOut != null) bpy - bOut.n else 0.0
        val resZ = if (bOut != null) bpz - bOut.z else 0.0

        if (useAverage && bOut != null) {
            val hx = resX / 2.0
            val hy = resY / 2.0
            val hz = resZ / 2.0
            out = out.map { it.copy(e = it.e + hx, n = it.n + hy, z = it.z + hz) }
        }

        val finalB = out.find { it.id == b.id }
        val finalRes = if (finalB != null)
            Triple(bpx - finalB.e, bpy - finalB.n, bpz - finalB.z)
        else Triple(resX, resY, resZ)

        val msg = buildString {
            append("الاین شد: مبنا=$baseName → هدف، جهت=$dirName")
            append(formatEn(" | مقیاس=%.6f", scale))
            append(formatEn(" | زاویه=%.4f°", Math.toDegrees(theta)))
            if (useAverage) append(" | میانگین‌گیری")
            append(formatEn(" | residual B: ΔE=%.4f ΔN=%.4f ΔZ=%.4f", finalRes.first, finalRes.second, finalRes.third))
        }
        return Result(out, finalRes, scale, Math.toDegrees(theta), msg)
    }
}
