package com.adel.assistant.data

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import kotlin.math.abs

/**
 * فاز ۱ GPS: پارس NMEA (GGA) و اتصال بلوتوث SPP به گیرنده خارجی.
 */
data class NmeaFix(
    val lat: Double,
    val lon: Double,
    val altMsl: Double,
    val geoidSep: Double,
    /** 0=none 1=GPS 2=DGPS 4=RTK Fixed 5=RTK Float */
    val fixQuality: Int,
    val sats: Int,
    val hdop: Double,
    val timeMs: Long = System.currentTimeMillis()
) {
    val ellipsoidalAlt: Double get() = altMsl + geoidSep

    val fixLabel: String
        get() = when (fixQuality) {
            0 -> "بدون Fix"
            1 -> "GPS"
            2 -> "DGPS"
            3 -> "PPS"
            4 -> "RTK Fixed"
            5 -> "RTK Float"
            6 -> "Estimated"
            else -> "Fix=$fixQuality"
        }

    /** دقت تقریبی از HDOP (متر) — در نبود GST */
    val approxAccuracyM: Float
        get() = when {
            hdop <= 0 -> -1f
            fixQuality == 4 -> (hdop * 0.02).toFloat().coerceAtLeast(0.01f)
            fixQuality == 5 -> (hdop * 0.15).toFloat().coerceAtLeast(0.05f)
            fixQuality == 2 -> (hdop * 0.8).toFloat()
            else -> (hdop * 3.0).toFloat()
        }
}

object NmeaParser {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun sppUuid(): UUID = SPP

    fun checksumOk(s: String): Boolean {
        val i = s.indexOf('*')
        if (!s.startsWith("$") || i < 0 || i + 3 > s.length) return false
        var x = 0
        for (k in 1 until i) x = x xor s[k].code
        val hex = s.substring(i + 1, (i + 3).coerceAtMost(s.length))
        return x == hex.toIntOrNull(16)
    }

    /** ddmm.mmmm یا dddmm.mmmm → درجه اعشاری */
    fun dmToDeg(v: String, hemi: String): Double? {
        val d = v.toDoubleOrNull() ?: return null
        val deg = (d / 100.0).toInt()
        val r = deg + (d - deg * 100.0) / 60.0
        return if (hemi.equals("S", true) || hemi.equals("W", true)) -r else r
    }

    fun parseGga(line: String): NmeaFix? {
        val s = line.trim()
        if (!s.startsWith("$")) return null
        if (!checksumOk(s)) return null
        val body = s.substringBefore('*')
        val f = body.split(',')
        if (f.size < 12) return null
        val talker = f[0]
        if (!talker.endsWith("GGA")) return null
        val fix = f[6].toIntOrNull() ?: 0
        if (fix == 0) return null
        val lat = dmToDeg(f[2], f[3]) ?: return null
        val lon = dmToDeg(f[4], f[5]) ?: return null
        val sats = f[7].toIntOrNull() ?: 0
        val hdop = f[8].toDoubleOrNull() ?: 99.0
        val alt = f[9].toDoubleOrNull() ?: 0.0
        val geoid = f.getOrNull(11)?.toDoubleOrNull() ?: 0.0
        return NmeaFix(lat, lon, alt, geoid, fix, sats, hdop)
    }

    /**
     * اتصال SPP و خواندن خط‌به‌خط NMEA تا [onLine] false برگرداند یا قطع شود.
     */
    suspend fun readNmeaStream(
        device: BluetoothDevice,
        onLine: (String) -> Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var sock: BluetoothSocket? = null
        try {
            sock = try {
                device.createRfcommSocketToServiceRecord(SPP)
            } catch (_: Exception) {
                @Suppress("DEPRECATION")
                device.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            }
            val s = sock ?: return@withContext Result.failure(IllegalStateException("socket null"))
            s.connect()
            BufferedReader(InputStreamReader(s.inputStream)).use { reader ->
                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine() ?: break
                    if (!onLine(line.trim())) break
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { sock?.close() } catch (_: Exception) { }
        }
    }

    fun qualityColorArgb(accM: Float, fix: Int, hdop: Double): Long {
        // سبز / زرد / قرمز
        val good = when {
            fix == 4 -> true
            accM in 0f..3f && hdop in 0.0..1.5 -> true
            else -> false
        }
        val mid = when {
            fix == 5 || fix == 2 -> true
            accM in 0f..10f && hdop in 0.0..3.0 -> true
            else -> false
        }
        return when {
            good -> 0xFF43A047
            mid -> 0xFFFDD835
            else -> 0xFFE53935
        }
    }
}

/**
 * میانگین وزن‌دار با حذف پرت (فاصله > ۲× میانه از مرکز).
 */
object GpsAverager {
    data class Sample(val lat: Double, val lon: Double, val alt: Double, val acc: Float)

    data class Result(
        val lat: Double,
        val lon: Double,
        val alt: Double,
        val accuracy: Float,
        val count: Int,
        val stdDevM: Double
    )

    fun weighted(samples: List<Sample>): Result? {
        if (samples.isEmpty()) return null
        // مرکز اولیه ساده
        var cLat = samples.map { it.lat }.average()
        var cLon = samples.map { it.lon }.average()
        val dists = samples.map { haversineM(cLat, cLon, it.lat, it.lon) }.sorted()
        val median = dists[dists.size / 2]
        val threshold = (2.0 * median).coerceAtLeast(1.5)
        val filtered = samples.filter { haversineM(cLat, cLon, it.lat, it.lon) <= threshold }
            .ifEmpty { samples }

        val weights = filtered.map { s ->
            val a = if (s.acc > 0f) s.acc.toDouble() else 25.0
            1.0 / (a * a)
        }
        val sumW = weights.sum().coerceAtLeast(1e-12)
        val lat = filtered.indices.sumOf { i -> filtered[i].lat * weights[i] } / sumW
        val lon = filtered.indices.sumOf { i -> filtered[i].lon * weights[i] } / sumW
        val altSamples = filtered.filter { abs(it.alt) > 1e-9 || true }
        val alt = altSamples.indices.sumOf { i -> filtered[i].alt * weights[i] } / sumW
        val residuals = filtered.map { haversineM(lat, lon, it.lat, it.lon) }
        val std = if (residuals.size > 1) {
            val mean = residuals.average()
            kotlin.math.sqrt(residuals.map { (it - mean) * (it - mean) }.average())
        } else 0.0
        val acc = filtered.map { it.acc }.filter { it > 0 }.minOrNull()
            ?: std.toFloat().coerceAtLeast(0.5f)
        return Result(lat, lon, alt, acc, filtered.size, std)
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_378_137.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(p1) * kotlin.math.cos(p2) * kotlin.math.sin(dLon / 2).let { it * it }
        return 2 * r * kotlin.math.asin(kotlin.math.min(1.0, kotlin.math.sqrt(a)))
    }
}
