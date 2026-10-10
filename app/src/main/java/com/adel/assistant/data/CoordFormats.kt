package com.adel.assistant.data

import kotlin.math.abs
import kotlin.math.floor

/** فرمت‌های نمایشی مختصات جغرافیایی و UTM */
object CoordFormats {

    fun formatDecimal(v: Double, decimals: Int = 8): String =
        String.format(java.util.Locale.US, "%.${decimals}f", v)

    fun toDms(deg: Double, isLat: Boolean): String {
        val hemi = when {
            isLat && deg >= 0 -> "N"
            isLat && deg < 0 -> "S"
            !isLat && deg >= 0 -> "E"
            else -> "W"
        }
        val a = abs(deg)
        val d = floor(a).toInt()
        val mFull = (a - d) * 60.0
        val m = floor(mFull).toInt()
        val s = (mFull - m) * 60.0
        return String.format(java.util.Locale.US, "%d°%02d'%05.2f\"%s", d, m, s, hemi)
    }

    fun toDm(deg: Double, isLat: Boolean): String {
        val hemi = when {
            isLat && deg >= 0 -> "N"
            isLat && deg < 0 -> "S"
            !isLat && deg >= 0 -> "E"
            else -> "W"
        }
        val a = abs(deg)
        val d = floor(a).toInt()
        val m = (a - d) * 60.0
        return String.format(java.util.Locale.US, "%d°%07.4f'%s", d, m, hemi)
    }

    fun parseDegrees(input: String): Double? {
        val t = input.toEnglishDigits().trim().replace(',', '.')
        if (t.isBlank()) return null
        t.toDoubleOrNull()?.let { return it }
        val cleaned = t.replace("°", " ").replace("'", " ").replace("\"", " ")
            .replace(Regex("[NSEWnsew]"), " ")
            .trim()
        val parts = cleaned.split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] + parts[1] / 60.0
            3 -> parts[0] + parts[1] / 60.0 + parts[2] / 3600.0
            else -> null
        }
    }

    fun toMgrs(easting: Double, northing: Double, zone: Int, northern: Boolean = true): String {
        val z = zone.coerceIn(1, 60)
        val (lat, _) = UtmGeo.toLatLon(easting, northing, z, northern)
        val band = latBand(lat)
        val colLetters = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val rowLetters = "ABCDEFGHJKLMNPQRSTUV"
        val colSet = ((z - 1) % 3) * 8
        val e100 = floor(easting / 100_000.0).toInt().coerceIn(1, 8)
        val n100 = floor((northing % 2_000_000.0) / 100_000.0).toInt().coerceIn(0, 19)
        val col = colLetters[(colSet + e100 - 1) % 24]
        val rowOrigin = if ((z % 2) == 0) 5 else 0
        val row = rowLetters[(rowOrigin + n100) % 20]
        val e = floor(easting % 100_000.0).toInt().coerceIn(0, 99999)
        val n = floor(northing % 100_000.0).toInt().coerceIn(0, 99999)
        return String.format(java.util.Locale.US, "%d%c %c%c %05d %05d", z, band, col, row, e, n)
    }

    private fun latBand(lat: Double): Char {
        val bands = "CDEFGHJKLMNPQRSTUVWX"
        val idx = floor((lat + 80.0) / 8.0).toInt().coerceIn(0, bands.length - 1)
        return bands[idx]
    }
}
