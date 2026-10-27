package com.adel.assistant.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * موقعیت زندهٔ سراسری — همهٔ برنامه‌ها بدون ورود اجباری به «مکان» می‌خوانند.
 * به‌روزرسانی پیش‌فرض هر ۵ ثانیه.
 */
object LiveLocationStore {
    private const val INTERVAL_MS = 5_000L
    private const val MIN_DISTANCE_M = 0f

    var lat by mutableDoubleStateOf(0.0)
        private set
    var lon by mutableDoubleStateOf(0.0)
        private set
    var alt by mutableDoubleStateOf(0.0)
        private set
    var accuracy by mutableFloatStateOf(-1f)
        private set
    var provider by mutableStateOf("")
        private set
    var updatedAtMs by mutableLongStateOf(0L)
        private set
    /** UTM zone از طول جغرافیایی */
    var zone by mutableStateOf(40)
        private set
    var easting by mutableDoubleStateOf(0.0)
        private set
    var northing by mutableDoubleStateOf(0.0)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var hasFix by mutableStateOf(false)
        private set

    private var lm: LocationManager? = null
    private var listener: LocationListener? = null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start(context: Context) {
        if (!hasPermission(context)) return
        if (isRunning && listener != null) {
            // فقط آخرین را دوباره بخوان
            readBestOnce(context)
            return
        }
        val manager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm = manager
        val locListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                applyFrom(location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        listener = locListener
        try {
            for (p in manager.getProviders(true)) {
                try {
                    manager.requestLocationUpdates(
                        p, INTERVAL_MS, MIN_DISTANCE_M, locListener, Looper.getMainLooper()
                    )
                } catch (_: Exception) { }
            }
            isRunning = true
            readBestOnce(context)
        } catch (_: SecurityException) {
            isRunning = false
        } catch (_: Exception) {
            isRunning = false
        }
    }

    fun stop() {
        val manager = lm ?: return
        val locListener = listener ?: return
        try {
            manager.removeUpdates(locListener)
        } catch (_: Exception) { }
        listener = null
        isRunning = false
    }

    /** یک‌بار بهترین lastKnown + در صورت نیاز start */
    fun ensure(context: Context) {
        if (!hasPermission(context)) return
        start(context)
    }

    fun readBestOnce(context: Context): Location? {
        if (!hasPermission(context)) return null
        return try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (pr in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                if (!manager.isProviderEnabled(pr) && pr != LocationManager.PASSIVE_PROVIDER) continue
                @Suppress("MissingPermission")
                val loc = manager.getLastKnownLocation(pr) ?: continue
                val cur = best
                if (cur == null || (loc.hasAccuracy() && loc.accuracy < cur.accuracy)) best = loc
            }
            best?.let { applyFrom(it) }
            best
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun applyFrom(loc: Location) {
        lat = loc.latitude
        lon = loc.longitude
        alt = if (loc.hasAltitude()) loc.altitude else alt
        accuracy = if (loc.hasAccuracy()) loc.accuracy else -1f
        provider = loc.provider ?: ""
        updatedAtMs = System.currentTimeMillis()
        hasFix = true
        val z = UtmGeo.zoneFromLon(loc.longitude)
        zone = z
        val (e, n) = UtmGeo.fromLatLon(loc.latitude, loc.longitude, z)
        easting = e
        northing = n
    }

    /** snapshot برای UI غیر Compose */
    data class Fix(
        val lat: Double,
        val lon: Double,
        val alt: Double,
        val accuracy: Float,
        val easting: Double,
        val northing: Double,
        val zone: Int,
        val updatedAtMs: Long
    )

    fun currentOrNull(): Fix? {
        if (!hasFix) return null
        return Fix(lat, lon, alt, accuracy, easting, northing, zone, updatedAtMs)
    }
}
