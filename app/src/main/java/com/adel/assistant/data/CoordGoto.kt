package com.adel.assistant.data

/**
 * پارس مختصات یک‌خطی: "e n" یا "e,n" یا lat,lon
 * تشخیص UTM (عدد صحیح ≥۴ رقم) در برابر Geo (عدد صحیح ≤۳ رقم).
 */
object CoordGoto {

    data class Parsed(
        val isUtm: Boolean,
        val a: Double, // easting یا lat
        val b: Double, // northing یا lon
        val zone: Int? = null
    )

    fun parse(line: String): Parsed? {
        val t = line.toEnglishDigits().trim()
        if (t.isBlank()) return null
        val parts = t.split(Regex("[,;\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val a = parts[0].replace(',', '.').toDoubleOrNull() ?: return null
        val b = parts[1].replace(',', '.').toDoubleOrNull() ?: return null
        val intDigitsA = integerDigitCount(a)
        val intDigitsB = integerDigitCount(b)
        // UTM: معمولاً هر دو ≥ ۴ رقم صحیح (E ~ 200000–800000، N ~ 3e6)
        val isUtm = intDigitsA >= 4 || intDigitsB >= 4
        return Parsed(isUtm, a, b)
    }

    private fun integerDigitCount(v: Double): Int {
        val abs = kotlin.math.abs(v)
        if (abs < 1) return 0
        return abs.toLong().toString().length
    }

    /** تبدیل به UTM easting/northing با زون */
    fun toUtm(p: Parsed, zone: Int = UtmGeo.DEFAULT_ZONE): Triple<Double, Double, Int> {
        return if (p.isUtm) {
            Triple(p.a, p.b, zone)
        } else {
            // a=lat b=lon  یا  a=lon b=lat — اگر |a|≤90 و |b|≤180 فرض lat,lon
            val lat: Double
            val lon: Double
            if (kotlin.math.abs(p.a) <= 90 && kotlin.math.abs(p.b) <= 180) {
                lat = p.a; lon = p.b
            } else {
                lon = p.a; lat = p.b
            }
            val z = if (zone in 1..60) zone else UtmGeo.zoneFromLon(lon)
            val (e, n) = UtmGeo.fromLatLon(lat, lon, z)
            Triple(e, n, z)
        }
    }
}

/** صفحه‌نقشه این مختصات را مصرف می‌کند و مرکز می‌کند */
object PendingMapGoto {
    var easting by androidx.compose.runtime.mutableStateOf<Double?>(null)
        private set
    var northing by androidx.compose.runtime.mutableStateOf<Double?>(null)
        private set
    var zone by androidx.compose.runtime.mutableStateOf(UtmGeo.DEFAULT_ZONE)
        private set

    fun set(e: Double, n: Double, z: Int = UtmGeo.DEFAULT_ZONE) {
        easting = e
        northing = n
        zone = z.coerceIn(1, 60)
    }

    fun consume(): Triple<Double, Double, Int>? {
        val e = easting ?: return null
        val n = northing ?: return null
        val z = zone
        easting = null
        northing = null
        return Triple(e, n, z)
    }
}
