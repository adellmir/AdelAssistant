package com.adel.assistant.data

import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * هواشناسی فعلی از OpenWeather.
 * کلید را در [API_KEY] بگذار (از openweathermap.org).
 */
object WeatherStore {

    /** ← کلید OpenWeather خودت را اینجا بگذار */
    const val API_KEY: String = "5c665d6072451330f43584e24c3218b0"

    data class Snapshot(
        val tempC: Double,
        /** OpenWeather weather[0].id */
        val conditionId: Int,
        val main: String,
        val description: String,
        val lat: Double,
        val lon: Double,
        val fetchedAtMs: Long = System.currentTimeMillis()
    )

    private val cache = AtomicReference<Snapshot?>(null)
    private const val CACHE_MS = 20 * 60 * 1000L

    fun cached(): Snapshot? {
        val c = cache.get() ?: return null
        if (System.currentTimeMillis() - c.fetchedAtMs > CACHE_MS) return null
        return c
    }

    fun lastKnownLatLon(context: Context): Pair<Double, Double>? {
        LiveLocationStore.ensure(context)
        LiveLocationStore.currentOrNull()?.let { return it.lat to it.lon }
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: android.location.Location? = null
            for (pr in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (!lm.isProviderEnabled(pr)) continue
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(pr) ?: continue
                val cur = best
                if (cur == null || loc.accuracy < cur.accuracy) best = loc
            }
            val loc = best ?: return null
            loc.latitude to loc.longitude
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetch(context: Context, force: Boolean = false): Snapshot? = withContext(Dispatchers.IO) {
        if (!force) cached()?.let { return@withContext it }
        if (API_KEY.isBlank() || API_KEY.startsWith("YOUR_")) return@withContext null
        val ll = lastKnownLatLon(context) ?: return@withContext null
        val (lat, lon) = ll
        val url = String.format(
            Locale.US,
            "https://api.openweathermap.org/data/2.5/weather?lat=%.5f&lon=%.5f&units=metric&lang=fa&appid=%s",
            lat, lon, API_KEY
        )
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: return@withContext null
            if (code !in 200..299) return@withContext null
            val root = JSONObject(body)
            val main = root.getJSONObject("main")
            val wArr = root.getJSONArray("weather")
            val w0 = wArr.getJSONObject(0)
            val snap = Snapshot(
                tempC = main.getDouble("temp"),
                conditionId = w0.getInt("id"),
                main = w0.optString("main", ""),
                description = w0.optString("description", ""),
                lat = lat,
                lon = lon
            )
            cache.set(snap)
            snap
        } catch (_: Exception) {
            null
        }
    }

    /** آیکون مینی‌مال بر اساس کد OpenWeather */
    fun iconKind(conditionId: Int): WeatherIconKind = when (conditionId) {
        in 200..299 -> WeatherIconKind.Thunder
        in 300..399 -> WeatherIconKind.Drizzle
        in 500..599 -> WeatherIconKind.Rain
        in 600..699 -> WeatherIconKind.Snow
        in 700..799 -> WeatherIconKind.Fog
        800 -> WeatherIconKind.Clear
        in 801..804 -> WeatherIconKind.Clouds
        else -> WeatherIconKind.Unknown
    }

    fun googleWeatherUrl(lat: Double, lon: Double): String =
        String.format(Locale.US, "https://www.google.com/search?q=weather+%.4f,%.4f", lat, lon)

    fun googleWeatherUrlFallback(): String = "https://www.google.com/search?q=weather"
}

enum class WeatherIconKind {
    Clear, Clouds, Rain, Drizzle, Snow, Thunder, Fog, Unknown
}
