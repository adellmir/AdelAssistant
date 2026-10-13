package com.adel.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.adel.assistant.MainActivity
import com.adel.assistant.R
import com.adel.assistant.data.TrackFilter
import com.adel.assistant.data.UtmGeo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * فاز ۵: سرویس Foreground برای ثبت مسیر در پس‌زمینه.
 * نقاط در filesDir/data/track_live.json ذخیره می‌شوند.
 */
class TrackRecordService : Service(), LocationListener {

    private var lm: LocationManager? = null
    private val filter = TrackFilter()
    private val pts = mutableListOf<JSONObject>()
    private var zone = UtmGeo.DEFAULT_ZONE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotif(0))
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            lm?.getProviders(true)?.forEach { p ->
                lm?.requestLocationUpdates(p, 1000L, 0f, this, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            save()
            stopSelf()
            return START_NOT_STICKY
        }
        zone = intent?.getIntExtra(EXTRA_ZONE, UtmGeo.DEFAULT_ZONE) ?: UtmGeo.DEFAULT_ZONE
        return START_STICKY
    }

    override fun onDestroy() {
        try { lm?.removeUpdates(this) } catch (_: Exception) { }
        save()
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        val z = if (zone > 0) zone else UtmGeo.zoneFromLon(location.longitude)
        val (e, n) = UtmGeo.fromLatLon(location.latitude, location.longitude, z)
        val acc = if (location.hasAccuracy()) location.accuracy else 25f
        val sample = TrackFilter.Loc(e, n, acc, System.currentTimeMillis())
        if (!filter.accept(sample)) return
        pts.add(JSONObject().apply {
            put("e", e); put("n", n); put("lat", location.latitude); put("lon", location.longitude)
            put("acc", acc.toDouble()); put("t", System.currentTimeMillis()); put("zone", z)
        })
        save()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(pts.size))
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun save() {
        try {
            val dir = File(filesDir, "data").apply { mkdirs() }
            val arr = JSONArray()
            pts.forEach { arr.put(it) }
            File(dir, LIVE_FILE).writeText(arr.toString())
        } catch (_: Exception) { }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "ثبت مسیر GPS", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotif(count: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TrackRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("ثبت مسیر GPS")
            .setContentText("$count نقطه ثبت شد")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .addAction(0, "توقف", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL = "track_record"
        const val NOTIF_ID = 7101
        const val ACTION_STOP = "com.adel.assistant.STOP_TRACK"
        const val EXTRA_ZONE = "zone"
        const val LIVE_FILE = "track_live.json"
        const val ACTION_START = "com.adel.assistant.START_TRACK"

        fun start(ctx: Context, zone: Int) {
            val i = Intent(ctx, TrackRecordService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ZONE, zone)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, TrackRecordService::class.java).setAction(ACTION_STOP))
        }

        fun loadLive(ctx: Context): List<Triple<Double, Double, Int>> {
            val f = File(File(ctx.filesDir, "data"), LIVE_FILE)
            if (!f.exists()) return emptyList()
            return try {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    Triple(o.optDouble("e"), o.optDouble("n"), o.optInt("zone", UtmGeo.DEFAULT_ZONE))
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun clearLive(ctx: Context) {
            File(File(ctx.filesDir, "data"), LIVE_FILE).delete()
        }
    }
}
