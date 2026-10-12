package com.adel.assistant.data

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.roundToInt

/** واحدهای نقشه‌برداری: DMS، Bearing، تبدیل مساحت */
object SurveyUnits {

    fun dms(deg: Double): String {
        val sign = if (deg < 0) "-" else ""
        val a = kotlin.math.abs(deg)
        val t = (a * 3600.0).roundToInt()
        val d = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return String.format(Locale.US, "%s%d°%02d'%02d\"", sign, d, m, s)
    }

    /** آزیموت ۰–۳۶۰ از شمال (dE شرق، dN شمال) */
    fun azimuthDeg(dE: Double, dN: Double): Double {
        val az = Math.toDegrees(atan2(dE, dN))
        return ((az % 360) + 360) % 360
    }

    fun bearing(dE: Double, dN: Double): String {
        val az = azimuthDeg(dE, dN)
        return when {
            az <= 90 -> "N ${dms(az)} E"
            az <= 180 -> "S ${dms(180 - az)} E"
            az <= 270 -> "S ${dms(az - 180)} W"
            else -> "N ${dms(360 - az)} W"
        }
    }

    fun formatAngle(deg: Double, surveyor: Boolean, dE: Double = 0.0, dN: Double = 0.0): String {
        return if (surveyor) bearing(dE, dN) else dms(deg)
    }

    fun formatArea(m2: Double): String {
        val ha = m2 / 10000.0
        return String.format(
            Locale.US,
            "%.3f m²  |  %.4f ha  |  %.2f ft²",
            m2, ha, m2 * 10.76391041671
        )
    }

    fun sideArea(perimeterM: Double, heightM: Double): Double = perimeterM * heightM
}
