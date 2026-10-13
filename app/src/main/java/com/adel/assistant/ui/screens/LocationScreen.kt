package com.adel.assistant.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.LocationPoint
import com.adel.assistant.data.LocationStore
import com.adel.assistant.data.PointConverter
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.data.UtmGeo
import com.adel.assistant.data.CoordGoto
import com.adel.assistant.data.PendingMapGoto
import com.adel.assistant.data.DeepLinkHolder
import com.adel.assistant.navigation.Routes
import com.adel.assistant.data.GeoidHeight
import com.adel.assistant.data.CompassMath
import com.adel.assistant.data.rememberCompassHeading
import com.adel.assistant.ui.CompassDial
import com.adel.assistant.data.CoordFormats
import com.adel.assistant.data.toEnglishDigits
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.adel.assistant.data.GpsAverager
import com.adel.assistant.data.NmeaFix
import com.adel.assistant.data.NmeaParser
import com.adel.assistant.data.Measure
import com.adel.assistant.data.MeasureStore
import com.adel.assistant.data.MeasureType
import com.adel.assistant.data.MeasureGeometry
import com.adel.assistant.data.TrackFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    var points by remember { mutableStateOf(LocationStore.load(context)) }
    var pointName by remember { mutableStateOf("") }
    var pointCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var gotoCoordLine by remember { mutableStateOf("") }
    var gotoZoneText by remember { mutableStateOf("40") }
    var geoidNText by remember { mutableStateOf(GeoidHeight.IRAN_DEFAULT_N.toString()) }
    val compassHeading = rememberCompassHeading()
    var zoneText by remember { mutableStateOf(UtmGeo.DEFAULT_ZONE.toString()) }
    var autoZone by remember { mutableStateOf(true) }
    var northernHemi by remember { mutableStateOf(true) }
    var fmtPreview by remember { mutableStateOf("") }

    // live GPS
    var liveLat by remember { mutableStateOf<Double?>(null) }
    var liveLon by remember { mutableStateOf<Double?>(null) }
    var liveAlt by remember { mutableStateOf(0.0) }
    var liveAcc by remember { mutableStateOf(-1f) }
    var liveProvider by remember { mutableStateOf("") }
    var liveFix by remember { mutableStateOf(-1) }
    var liveSats by remember { mutableStateOf(0) }
    var liveHdop by remember { mutableStateOf(-1.0) }
    var liveIsExternal by remember { mutableStateOf(false) }
    var liveIsMock by remember { mutableStateOf(false) }
    var lastGpsMs by remember { mutableStateOf(0L) }
    var gpsLost by remember { mutableStateOf(false) }
    /** آستانه دقت قابل قبول (متر) — بدتر از این هشدار/تأیید */
    var accThreshold by remember { mutableStateOf(10f) }
    var showAccWarn by remember { mutableStateOf(false) }
    var pendingCapture by remember { mutableStateOf(false) } // true=once false=avg handled separately
    var showExtGps by remember { mutableStateOf(false) }
    var extConnecting by remember { mutableStateOf(false) }
    var extDeviceName by remember { mutableStateOf<String?>(null) }
    var extJob by remember { mutableStateOf<Job?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // averaging
    var averaging by remember { mutableStateOf(false) }
    var avgSamples by remember { mutableStateOf(listOf<Location>()) }
    var avgSeconds by remember { mutableStateOf(10) }

    // —— فاز ۲: ثبت مسیر / محدوده ——
    var trackMode by remember { mutableStateOf(false) } // false=مسیر true=محدوده
    var tracking by remember { mutableStateOf(false) }
    var trackPaused by remember { mutableStateOf(false) }
    var trackPts by remember { mutableStateOf(listOf<TrackFilter.Loc>()) }
    var trackFilter by remember { mutableStateOf(TrackFilter()) }
    var trackName by remember { mutableStateOf("") }
    var measures by remember { mutableStateOf(MeasureStore.load(context)) }

    // manual convert
    var manLat by remember { mutableStateOf("") }
    var manLon by remember { mutableStateOf("") }
    var manE by remember { mutableStateOf("") }
    var manN by remember { mutableStateOf("") }
    var manZ by remember { mutableStateOf("0") }

    // tools: distance / offset
    var selA by remember { mutableStateOf<String?>(null) }
    var selB by remember { mutableStateOf<String?>(null) }
    var offsetDist by remember { mutableStateOf("10") }
    var offsetAz by remember { mutableStateOf("0") }
    // —— فاز ۳: Stakeout / شبکه ——
    var stakeoutTargetId by remember { mutableStateOf<String?>(null) }
    var stakeoutNearAlerted by remember { mutableStateOf(false) }
    var gridRows by remember { mutableStateOf("3") }
    var gridCols by remember { mutableStateOf("3") }
    var gridStep by remember { mutableStateOf("10") }
    var gridAz by remember { mutableStateOf("0") }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    fun zone(): Int = zoneText.toIntOrNull() ?: UtmGeo.DEFAULT_ZONE

    fun finishTrack(auto: Boolean = false) {
        if (trackPts.size < 2) {
            status = "نقاط مسیر کافی نیست"
            tracking = false
            return
        }
        val ring = trackPts.map { it.e to it.n }
        val type = if (trackMode) MeasureType.FIELD else MeasureType.LINE
        val z = zone()
        val m = Measure(
            id = System.nanoTime().toString(),
            type = type,
            name = trackName.ifBlank { if (trackMode) "محدوده${measures.size + 1}" else "مسیر${measures.size + 1}" },
            zone = z,
            rings = listOf(ring),
            source = if (liveIsExternal) "gps-ext" else "gps",
        )
        val list = measures + m
        measures = list
        MeasureStore.save(context, list)
        tracking = false
        trackPaused = false
        val info = if (type == MeasureType.FIELD)
            "مساحت ${MeasureGeometry.fmtArea(m.areaM2)} · محیط ${MeasureGeometry.fmtDist(m.lengthM)}"
        else
            "طول ${MeasureGeometry.fmtDist(m.lengthM)}"
        status = (if (auto) "بستن خودکار — " else "") + "ذخیره شد: ${m.name} · $info · ${trackPts.size} رأس"
        trackPts = emptyList()
        trackName = ""
    }


    fun startTrack(asField: Boolean) {
        if (!liveIsExternal && !ensurePermission()) return
        trackMode = asField
        tracking = true
        trackPaused = false
        trackPts = emptyList()
        trackFilter = TrackFilter()
        status = if (asField) "ثبت محدوده — راه بروید؛ نزدیک نقطه اول = بستن خودکار" else "ثبت مسیر — راه بروید"
    }

    fun appendTrackFromLocation(loc: Location) {
        val z = if (autoZone) UtmGeo.zoneFromLon(loc.longitude) else zone()
        val (e, n) = UtmGeo.fromLatLon(loc.latitude, loc.longitude, z)
        val acc = if (loc.hasAccuracy()) loc.accuracy else 25f
        val sample = TrackFilter.Loc(e, n, acc, System.currentTimeMillis())
        if (!trackFilter.accept(sample)) return
        val next = trackPts + sample
        trackPts = next
        if (trackMode && trackFilter.shouldAutoClose(trackPts.dropLast(1), sample, 3.0)) {
            finishTrack(auto = true)
        }
    }



    fun stakeoutMetrics(): Triple<Double, Double, Double>? {
        val id = stakeoutTargetId ?: selA ?: return null
        val tgt = points.find { it.id == id } ?: return null
        val la = liveLat ?: return null
        val lo = liveLon ?: return null
        val z = if (autoZone) UtmGeo.zoneFromLon(lo) else zone()
        val (e, n) = UtmGeo.fromLatLon(la, lo, z)
        val dE = tgt.easting - e
        val dN = tgt.northing - n
        val dist = kotlin.math.hypot(dE, dN)
        // هشدار نزدیکی < 2 متر
        if (dist < 2.0 && !stakeoutNearAlerted) {
            stakeoutNearAlerted = true
            status = "نزدیک هدف: ${tgt.name} · ${formatEn("%.2f", dist)} m"
            try {
                val v = if (Build.VERSION.SDK_INT >= 31) {
                    (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    v?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(200)
                }
            } catch (_: Exception) { }
        } else if (dist >= 2.0) {
            stakeoutNearAlerted = false
        }
        return Triple(dist, dE, dN)
    }

    fun generateGrid() {
        val baseId = selA
        val base = points.find { it.id == baseId }
        if (base == null) {
            status = "نقطه مبنای شبکه (A) را انتخاب کنید"
            return
        }
        val rows = gridRows.toIntOrNull() ?: 3
        val cols = gridCols.toIntOrNull() ?: 3
        val step = gridStep.toDoubleOrNull() ?: 10.0
        val az = gridAz.toDoubleOrNull() ?: 0.0
        if (rows < 1 || cols < 1 || rows * cols > 200) {
            status = "ابعاد شبکه نامعتبر (حداکثر ۲۰۰ نقطه)"
            return
        }
        val rad = Math.toRadians(az)
        val cosA = kotlin.math.cos(rad)
        val sinA = kotlin.math.sin(rad)
        val newPts = mutableListOf<LocationPoint>()
        var idx = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (r == 0 && c == 0) continue // خود مبنا
                val de = c * step
                val dn = r * step
                // چرخش آزیموت از شمال
                val e = base.easting + de * cosA - dn * sinA
                val n = base.northing + de * sinA + dn * cosA
                val (lat, lon) = UtmGeo.toLatLon(e, n, base.zone)
                idx++
                newPts += LocationPoint(
                    id = System.nanoTime().toString() + "_$idx",
                    name = "G${r + 1}-${c + 1}",
                    lat = lat,
                    lon = lon,
                    alt = base.alt,
                    accuracy = -1f,
                    code = "GRID",
                    zone = base.zone,
                    easting = e,
                    northing = n,
                    timeMs = System.currentTimeMillis(),
                    note = "شبکه از ${base.name}"
                )
            }
        }
        persist(points + newPts)
        status = "شبکه ${rows}×${cols} ساخته شد · ${newPts.size} نقطه جدید"
    }

    fun persist(list: List<LocationPoint>) {
        points = list
        LocationStore.save(context, list)
    }

    fun copyText(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        status = "$label کپی شد"
    }

    fun ensurePermission(): Boolean {
        if (hasPermission) return true
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        return false
    }

    fun readBestLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (provider in lm.getProviders(true)) {
            val loc = try {
                lm.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } ?: continue
            if (best == null || (loc.hasAccuracy() && loc.accuracy < (best.accuracy))) best = loc
        }
        return best
    }

    fun updateLiveFrom(loc: Location, fromExternal: Boolean = false) {
        // وقتی گیرنده خارجی وصل است، GPS داخلی را نادیده بگیر
        if (liveIsExternal && !fromExternal) return
        liveLat = loc.latitude
        liveLon = loc.longitude
        liveAlt = if (loc.hasAltitude()) loc.altitude else 0.0
        liveAcc = if (loc.hasAccuracy()) loc.accuracy else -1f
        liveProvider = loc.provider ?: ""
        liveIsMock = if (Build.VERSION.SDK_INT >= 31) loc.isMock else false
        if (!fromExternal) {
            liveFix = -1
            liveSats = 0
            liveHdop = -1.0
            liveIsExternal = false
        }
        lastGpsMs = System.currentTimeMillis()
        gpsLost = false
        if (autoZone) {
            zoneText = UtmGeo.zoneFromLon(loc.longitude).toString()
        }
    }

    fun updateLiveFromNmea(fix: NmeaFix) {
        liveLat = fix.lat
        liveLon = fix.lon
        liveAlt = fix.ellipsoidalAlt
        liveAcc = fix.approxAccuracyM
        liveProvider = "NMEA"
        liveFix = fix.fixQuality
        liveSats = fix.sats
        liveHdop = fix.hdop
        liveIsExternal = true
        liveIsMock = false
        lastGpsMs = System.currentTimeMillis()
        gpsLost = false
        if (autoZone) zoneText = UtmGeo.zoneFromLon(fix.lon).toString()
        if (averaging) {
            val loc = Location("nmea").apply {
                latitude = fix.lat
                longitude = fix.lon
                altitude = fix.ellipsoidalAlt
                accuracy = fix.approxAccuracyM
            }
            avgSamples = avgSamples + loc
        }
        if (tracking && !trackPaused) {
            val loc = Location("nmea").apply {
                latitude = fix.lat
                longitude = fix.lon
                altitude = fix.ellipsoidalAlt
                accuracy = fix.approxAccuracyM
            }
            appendTrackFromLocation(loc)
        }
    }


    // روشن ماندن صفحه حین ثبت مسیر
    DisposableEffect(tracking) {
        val act = context as? android.app.Activity
        if (tracking && act != null) {
            act.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // هشدار قطع سیگنال بیش از ۵ ثانیه
    LaunchedEffect(hasPermission, liveIsExternal) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (lastGpsMs > 0 && System.currentTimeMillis() - lastGpsMs > 5000) {
                if (!gpsLost) {
                    gpsLost = true
                    status = "سیگنال GPS قطع شد"
                    try {
                        val v = if (Build.VERSION.SDK_INT >= 31) {
                            (context.getSystemService(VibratorManager::class.java))?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        }
                        if (Build.VERSION.SDK_INT >= 26) {
                            v?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            v?.vibrate(120)
                        }
                    } catch (_: Exception) { }
                }
            }
        }
    }

    // continuous updates while screen visible
    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                updateLiveFrom(location)
                if (averaging) {
                    avgSamples = avgSamples + location
                }
                if (tracking && !trackPaused) {
                    appendTrackFromLocation(location)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            for (p in lm.getProviders(true)) {
                lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
            }
            readBestLocation()?.let { updateLiveFrom(it) }
        } catch (_: SecurityException) {
        }
        onDispose {
            try {
                lm.removeUpdates(listener)
            } catch (_: Exception) {
            }
        }
    }

    fun makePoint(lat: Double, lon: Double, alt: Double, acc: Float, name: String, code: String): LocationPoint {
        val z = if (autoZone) UtmGeo.zoneFromLon(lon) else zone()
        val (e, n) = UtmGeo.fromLatLon(lat, lon, z)
        return LocationPoint(
            id = System.nanoTime().toString(),
            name = name.ifBlank { "P${points.size + 1}" },
            lat = lat,
            lon = lon,
            alt = alt,
            accuracy = acc,
            code = code,
            zone = z,
            easting = e,
            northing = n,
            timeMs = System.currentTimeMillis(),
            fix = liveFix,
            sats = liveSats,
            hdop = liveHdop,
            isExternal = liveIsExternal,
            isMock = liveIsMock
        )
    }

    fun accQualityColor(): Color {
        val acc = liveAcc
        val argb = NmeaParser.qualityColorArgb(acc, liveFix, if (liveHdop > 0) liveHdop else 99.0)
        return Color(argb)
    }

    fun fixLabel(): String = when (liveFix) {
        0 -> "بدون Fix"
        1 -> "GPS"
        2 -> "DGPS"
        4 -> "RTK Fixed"
        5 -> "RTK Float"
        -1 -> if (liveIsMock) "Mock" else if (liveIsExternal) "خارجی" else "گوشی"
        else -> "Fix=$liveFix"
    }

    fun doCaptureOnce() {
        val lat = liveLat
        val lon = liveLon
        if (lat == null || lon == null) {
            if (!ensurePermission()) return
            val loc = readBestLocation()
            if (loc == null) {
                status = "موقعیت در دسترس نیست — GPS را روشن کنید"
                return
            }
            updateLiveFrom(loc)
        }
        val la = liveLat ?: return
        val lo = liveLon ?: return
        val p = makePoint(la, lo, liveAlt, liveAcc, pointName, pointCode)
        persist(points + p)
        status = "ثبت شد: ${p.name}  دقت≈${if (p.accuracy > 0) "%.1f m".format(Locale.US, p.accuracy) else "؟"}  ${fixLabel()}"
        pointName = ""
        showAccWarn = false
    }

    fun captureOnce() {
        if (!liveIsExternal && !ensurePermission()) return
        if (liveLat == null || liveLon == null) {
            val loc = readBestLocation()
            if (loc == null) {
                status = "موقعیت در دسترس نیست — GPS را روشن کنید"
                return
            }
            updateLiveFrom(loc)
        }
        if (liveAcc > 0 && liveAcc > accThreshold) {
            pendingCapture = true
            showAccWarn = true
            status = "دقت ${String.format(Locale.US, "%.1f", liveAcc)} m بدتر از آستانه ${String.format(Locale.US, "%.0f", accThreshold)} m"
            return
        }
        doCaptureOnce()
    }

    fun startAverage() {
        if (!liveIsExternal && !ensurePermission()) return
        averaging = true
        avgSamples = emptyList()
        status = "میانگین وزن‌دار ${avgSeconds} ثانیه…"
        Handler(Looper.getMainLooper()).postDelayed({
            averaging = false
            val samples = avgSamples
            if (samples.isEmpty()) {
                status = "نمونه‌ای دریافت نشد"
                return@postDelayed
            }
            val avgIn = samples.map {
                GpsAverager.Sample(
                    it.latitude, it.longitude,
                    if (it.hasAltitude()) it.altitude else 0.0,
                    if (it.hasAccuracy()) it.accuracy else 25f
                )
            }
            val res = GpsAverager.weighted(avgIn)
            if (res == null) {
                status = "محاسبه میانگین ناموفق"
                return@postDelayed
            }
            val p = makePoint(
                res.lat, res.lon, res.alt, res.accuracy,
                pointName.ifBlank { "AVG${points.size + 1}" }, pointCode
            )
            persist(points + p)
            status = "میانگین وزن‌دار ${res.count}/${samples.size} نمونه · σ≈${"%.2f".format(Locale.US, res.stdDevM)} m — ${p.name}"
            pointName = ""
            avgSamples = emptyList()
        }, avgSeconds * 1000L)
    }

    fun connectExternalGps(device: BluetoothDevice) {
        extJob?.cancel()
        extConnecting = true
        status = "اتصال به ${device.name ?: device.address}…"
        extJob = scope.launch {
            val result = NmeaParser.readNmeaStream(device) { line ->
                val gga = NmeaParser.parseGga(line)
                if (gga != null) {
                    // به‌روز روی UI thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        updateLiveFromNmea(gga)
                        extDeviceName = device.name ?: device.address
                        extConnecting = false
                    }
                }
                true // ادامه خواندن
            }
            withContext(Dispatchers.Main) {
                extConnecting = false
                if (result.isFailure) {
                    liveIsExternal = false
                    extDeviceName = null
                    status = "قطع GPS خارجی: ${result.exceptionOrNull()?.message ?: "خطا"}"
                } else {
                    liveIsExternal = false
                    extDeviceName = null
                    status = "GPS خارجی قطع شد"
                }
            }
        }
    }

    fun disconnectExternal() {
        extJob?.cancel()
        extJob = null
        liveIsExternal = false
        extDeviceName = null
        status = "GPS خارجی قطع شد — برگشت به گوشی"
    }

    fun updateFmtPreview(lat: Double, lon: Double, e: Double, n: Double, z: Int) {
        fmtPreview = buildString {
            appendLine("اعشاری: ${CoordFormats.formatDecimal(lat, 8)} , ${CoordFormats.formatDecimal(lon, 8)}")
            appendLine("DM: ${CoordFormats.toDm(lat, true)}  ${CoordFormats.toDm(lon, false)}")
            appendLine("DMS: ${CoordFormats.toDms(lat, true)}  ${CoordFormats.toDms(lon, false)}")
            append("MGRS: ${CoordFormats.toMgrs(e, n, z, northernHemi)}")
        }
    }

    fun convertGeoToUtm() {
        val lat = CoordFormats.parseDegrees(manLat) ?: manLat.toDoubleOrNullFa()
            ?: run { status = "عرض نامعتبر"; return }
        val lon = CoordFormats.parseDegrees(manLon) ?: manLon.toDoubleOrNullFa()
            ?: run { status = "طول نامعتبر"; return }
        val z = if (autoZone) UtmGeo.zoneFromLon(lon) else zone()
        zoneText = z.toString()
        northernHemi = lat >= 0
        val (e, n) = UtmGeo.fromLatLon(lat, lon, z)
        manLat = formatEn("%.8f", lat)
        manLon = formatEn("%.8f", lon)
        manE = formatEn("%.3f", e)
        manN = formatEn("%.3f", n)
        updateFmtPreview(lat, lon, e, n, z)
        status = "تبدیل شد → Zone $z"
    }

    fun convertUtmToGeo() {
        val e = manE.toDoubleOrNullFa() ?: run { status = "Easting نامعتبر"; return }
        val n = manN.toDoubleOrNullFa() ?: run { status = "Northing نامعتبر"; return }
        val z = zone()
        val (lat, lon) = UtmGeo.toLatLon(e, n, z, northernHemi)
        manLat = formatEn("%.8f", lat)
        manLon = formatEn("%.8f", lon)
        updateFmtPreview(lat, lon, e, n, z)
        status = "تبدیل شد ← Zone $z ${if (northernHemi) "N" else "S"}"
    }

    fun addManualPoint() {
        val lat = manLat.toDoubleOrNullFa()
        val lon = manLon.toDoubleOrNullFa()
        if (lat != null && lon != null) {
            val alt = manZ.toDoubleOrNullFa() ?: 0.0
            val p = makePoint(lat, lon, alt, -1f, pointName, pointCode)
            persist(points + p)
            status = "نقطه دستی اضافه شد: ${p.name}"
            pointName = ""
            return
        }
        val e = manE.toDoubleOrNullFa()
        val n = manN.toDoubleOrNullFa()
        if (e != null && n != null) {
            val z = zone()
            val (lat2, lon2) = UtmGeo.toLatLon(e, n, z)
            val alt = manZ.toDoubleOrNullFa() ?: 0.0
            val p = makePoint(lat2, lon2, alt, -1f, pointName, pointCode).copy(
                zone = z, easting = e, northing = n
            )
            persist(points + p)
            status = "نقطه UTM اضافه شد: ${p.name}"
            pointName = ""
        } else {
            status = "مختصات دستی کامل نیست"
        }
    }


    val batchOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "points.txt"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@rememberLauncherForActivityResult
            val pts = PointConverter.readBytes(bytes, name)
            if (pts.isEmpty()) {
                status = "نقطه‌ای از فایل خوانده نشد"
                return@rememberLauncherForActivityResult
            }
            val z = zone()
            val added = pts.map { sp ->
                val isGeo = kotlin.math.abs(sp.x) <= 180.0 && kotlin.math.abs(sp.y) <= 90.0
                if (isGeo) {
                    val lo = sp.x
                    val la = sp.y
                    val useZ = if (autoZone) UtmGeo.zoneFromLon(lo) else z
                    val (ee, nn) = UtmGeo.fromLatLon(la, lo, useZ)
                    makePoint(la, lo, sp.z, -1f, sp.id.ifBlank { "P" }, sp.code).copy(
                        zone = useZ, easting = ee, northing = nn
                    )
                } else {
                    val (la, lo) = UtmGeo.toLatLon(sp.x, sp.y, z, northernHemi)
                    makePoint(la, lo, sp.z, -1f, sp.id.ifBlank { "P" }, sp.code).copy(
                        zone = z, easting = sp.x, northing = sp.y
                    )
                }
            }
            persist(points + added)
            status = "${added.size} نقطه از فایل اضافه و تبدیل شد"
        } catch (ex: Exception) {
            status = "خطای فایل: ${ex.message}"
        }
    }


    fun export(ext: String) {
        if (points.isEmpty()) {
            status = "نقطه‌ای برای خروجی نیست"
            return
        }
        val survey = points.map { it.toSurveyPoint() }
        val text = when (ext) {
            "kml" -> UtmGeo.toKml(survey, "Location", points.first().zone)
            "txt", "dat", "gsi", "csv" -> PointConverter.write(survey, ext)
            else -> points.joinToString("\n") { p ->
                listOf(p.name, formatEn("%.8f", p.lat), formatEn("%.8f", p.lon), formatEn("%.3f", p.alt),
                    formatEn("%.3f", p.easting), formatEn("%.3f", p.northing), p.zone.toString(), p.code
                ).joinToString("\t")
            }
        }
        val name = "location_${System.currentTimeMillis() / 1000}.$ext"
        val uri = FileExport.exportTextToDocuments(context, name, text)
        status = if (uri != null) "خروجی: $name" else "خطا در ذخیره خروجی"
    }

    fun openMaps(p: LocationPoint) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UtmGeo.googleMapsUrl(p.lat, p.lon))))
        } catch (_: Exception) {
            status = "نقشه باز نشد"
        }
    }

    fun openNeshan(p: LocationPoint) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UtmGeo.neshanIntentUri(p.lat, p.lon))))
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(formatEn("https://nshn.ir/?lat=%.6f&lng=%.6f", p.lat, p.lon)))
                )
            } catch (_: Exception) {
                status = "نشان باز نشد"
            }
        }
    }

    fun sharePoint(p: LocationPoint) {
        val body = buildString {
            appendLine(p.name)
            appendLine(formatEn("Lat: %.8f  Lon: %.8f", p.lat, p.lon))
            appendLine(formatEn("UTM %d: E=%.3f  N=%.3f  Z=%.3f", p.zone, p.easting, p.northing, p.alt))
            if (p.code.isNotBlank()) appendLine("کد: ${p.code}")
            appendLine(UtmGeo.googleMapsUrl(p.lat, p.lon))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک موقعیت"))
    }

    fun applyOffset() {
        val dist = offsetDist.toDoubleOrNullFa() ?: run { status = "فاصله نامعتبر"; return }
        val az = offsetAz.toDoubleOrNullFa() ?: run { status = "آزیموت نامعتبر"; return }
        val base = points.find { it.id == selA }
            ?: liveLat?.let { la -> liveLon?.let { lo -> makePoint(la, lo, liveAlt, liveAcc, "BASE", "") } }
        if (base == null) {
            status = "نقطه مبنا انتخاب نشده / GPS نیست"
            return
        }
        val (lat2, lon2) = LocationStore.offsetLatLon(base.lat, base.lon, dist, az)
        val p = makePoint(lat2, lon2, base.alt, -1f, pointName.ifBlank { "OFF${points.size + 1}" }, pointCode)
        persist(points + p)
        status = "آفست ثبت شد: ${p.name}  (${formatEn("%.2f", dist)} m @ ${formatEn("%.1f", az)}°)"
        pointName = ""
    }

    val distInfo: String = run {
        val a = points.find { it.id == selA }
        val b = points.find { it.id == selB }
        if (a != null && b != null) {
            val (d, az) = LocationStore.distanceAndAzimuth(a.lat, a.lon, b.lat, b.lon)
            "فاصله: ${formatEn("%.3f", d)} m   آزیموت: ${formatEn("%.2f", az)}°"
        } else ""
    }

    val pathLen = LocationStore.pathLength(points)
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 14.dp)
    ) {
        ScreenTopBar(title = "مکان", color = color, onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "دقت GPS گوشی معمولاً ۳–۱۵ متر است. برای کنترل دقیق از دوربین نقشه‌برداری استفاده کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 11.sp
            )

            // —— موقعیت زنده ——
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("موقعیت زنده", fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (liveLat != null && liveLon != null) {
                        val z = if (autoZone) UtmGeo.zoneFromLon(liveLon!!) else zone()
                        val (e, n) = UtmGeo.fromLatLon(liveLat!!, liveLon!!, z)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatEn("Lat %.7f", liveLat!!), color = TextPrimary, fontSize = 12.sp,
                                modifier = Modifier.clickable { copyText("Lat", formatEn("%.8f", liveLat!!)) })
                            Text(formatEn("Lon %.7f", liveLon!!), color = TextPrimary, fontSize = 12.sp,
                                modifier = Modifier.clickable { copyText("Lon", formatEn("%.8f", liveLon!!)) })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatEn("E %.3f", e), color = TextSecondary, fontSize = 12.sp,
                                modifier = Modifier.clickable { copyText("E", formatEn("%.3f", e)) })
                            Text(formatEn("N %.3f", n), color = TextSecondary, fontSize = 12.sp,
                                modifier = Modifier.clickable { copyText("N", formatEn("%.3f", n)) })
                            Text("Z$z", color = TextSecondary, fontSize = 12.sp)
                        }
                        val accColor = when {
                            liveAcc < 0 -> TextSecondary
                            liveAcc <= 5f -> Color(0xFF2E7D32)
                            liveAcc <= 15f -> Color(0xFFF9A825)
                            else -> Color(0xFFC62828)
                        }
                        Text(
                            buildString {
                                append(if (liveAcc >= 0) formatEn("دقت ≈ %.1f m", liveAcc) else "دقت نامشخص")
                                append("  |  ارتفاع ${formatEn("%.1f", liveAlt)} m")
                                if (liveProvider.isNotBlank()) append("  |  $liveProvider")
                            },
                            color = accColor,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            if (hasPermission) "در انتظار سیگنال GPS…" else "مجوز موقعیت لازم است",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // —— نوار کیفیت GPS (فاز ۱) ——
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .background(accQualityColor(), shape = RoundedCornerShape(7.dp))
                        )
                        Text(
                            when {
                                gpsLost -> "قطع سیگنال"
                                liveLat == null -> "در انتظار GPS…"
                                else -> "دقت ${if (liveAcc > 0) String.format(Locale.US, "%.1f m", liveAcc) else "؟"}"
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (gpsLost) Color(0xFFE53935) else TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(fixLabel(), color = TextSecondary, fontSize = 12.sp)
                        if (liveSats > 0) Text("${liveSats} ماهواره", color = TextSecondary, fontSize = 11.sp)
                        if (liveHdop > 0) Text("HDOP ${String.format(Locale.US, "%.1f", liveHdop)}", color = TextSecondary, fontSize = 11.sp)
                    }
                    if (liveIsExternal) {
                        Text("منبع: GPS خارجی (${extDeviceName ?: "NMEA"})", color = Color(0xFF43A047), fontSize = 11.sp)
                    } else if (liveIsMock) {
                        Text("منبع: Mock Location", color = Color(0xFFFB8C00), fontSize = 11.sp)
                    } else if (liveProvider.isNotBlank()) {
                        Text("منبع: $liveProvider", color = TextSecondary, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("آستانه دقت:", fontSize = 11.sp, color = TextSecondary)
                        listOf(3f, 5f, 10f, 20f).forEach { th ->
                            FilterChip(
                                selected = accThreshold == th,
                                onClick = { accThreshold = th },
                                label = { Text("${th.toInt()}m", fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showExtGps = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (liveIsExternal) "تعویض GPS خارجی" else "GPS خارجی (NMEA)")
                        }
                        if (liveIsExternal) {
                            OutlinedButton(onClick = { disconnectExternal() }) {
                                Text("قطع خارجی")
                            }
                        }
                    }
                }
            }

            // —— ثبت ——
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ثبت نقطه", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            pointName, { pointName = it },
                            label = { Text("نام") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            pointCode, { pointCode = it },
                            label = { Text("کد") },
                            modifier = Modifier.weight(0.7f),
                            singleLine = true
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(autoZone, { autoZone = it })
                            Text("Zone خودکار", fontSize = 12.sp, color = TextSecondary)
                        }
                        OutlinedTextField(
                            zoneText,
                            { zoneText = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("Zone") },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            enabled = !autoZone,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Button(
                        onClick = { captureOnce() },
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !averaging
                    ) { Text("ثبت از موقعیت فعلی") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(5, 10, 20, 30).forEach { s ->
                            FilterChip(
                                selected = avgSeconds == s,
                                onClick = { avgSeconds = s },
                                label = { Text("${s}ث") },
                                enabled = !averaging
                            )
                        }
                        Button(
                            onClick = { startAverage() },
                            enabled = !averaging,
                            colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.85f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (averaging) "…${avgSamples.size}" else "میانگین‌گیری")
                        }
                    }
                }
            }


            // —— فاز ۲: ثبت مسیر / محدوده با راه رفتن ——
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ثبت مسیر / محدوده (فاز ۲)", fontWeight = FontWeight.Bold, color = TextPrimary)
                    OutlinedTextField(
                        trackName, { trackName = it },
                        label = { Text("نام (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !tracking
                    )
                    if (!tracking) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { startTrack(asField = false) },
                                colors = ButtonDefaults.buttonColors(containerColor = color),
                                modifier = Modifier.weight(1f)
                            ) { Text("شروع مسیر") }
                            Button(
                                onClick = { startTrack(asField = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.9f)),
                                modifier = Modifier.weight(1f)
                            ) { Text("شروع محدوده") }
                        }
                    } else {
                        Text(
                            "${if (trackMode) "محدوده" else "مسیر"} · ${trackPts.size} نقطه · ${MeasureGeometry.fmtDist(MeasureGeometry.pathLength(trackPts.map { it.e to it.n }))}",
                            color = color, fontWeight = FontWeight.Medium, fontSize = 13.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { trackPaused = !trackPaused }) {
                                Text(if (trackPaused) "ادامه" else "مکث")
                            }
                            Button(
                                onClick = { finishTrack() },
                                colors = ButtonDefaults.buttonColors(containerColor = color),
                                modifier = Modifier.weight(1f)
                            ) { Text(if (trackMode) "بستن و ذخیره" else "پایان مسیر") }
                            OutlinedButton(onClick = {
                                tracking = false; trackPts = emptyList(); trackPaused = false
                                status = "ثبت مسیر لغو شد"
                            }) { Text("لغو") }
                        }
                    }
                    if (measures.isNotEmpty()) {
                        Text("ذخیره‌شده‌ها (${measures.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextSecondary)
                        measures.takeLast(8).reversed().forEach { m ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${m.name} · ${m.type.name} · " +
                                        if (m.type == MeasureType.FIELD) MeasureGeometry.fmtArea(m.areaM2)
                                        else MeasureGeometry.fmtDist(m.lengthM),
                                    Modifier.weight(1f),
                                    fontSize = 12.sp, color = TextPrimary
                                )
                                IconButton(onClick = {
                                    measures = measures.filter { it.id != m.id }
                                    MeasureStore.save(context, measures)
                                    status = "حذف شد: ${m.name}"
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Delete, null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }


            // —— برو به روی نقشه ——
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("برو به روی نقشه", fontWeight = FontWeight.Bold, color = TextPrimary)
                    OutlinedTextField(
                        gotoCoordLine, { gotoCoordLine = it },
                        label = { Text("مختصات (یک خط)") },
                        placeholder = { Text("E N   یا   Lat Lon") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val parsed = CoordGoto.parse(gotoCoordLine)
                    if (parsed?.isUtm == true) {
                        OutlinedTextField(
                            gotoZoneText, { gotoZoneText = it },
                            label = { Text("زون UTM") },
                            singleLine = true,
                            modifier = Modifier.width(120.dp),
                            keyboardOptions = numKb
                        )
                    }
                    Button(
                        onClick = {
                            val pr = CoordGoto.parse(gotoCoordLine)
                            if (pr == null) {
                                status = "مختصات را با فاصله یا ویرگول وارد کنید"
                                return@Button
                            }
                            val z = gotoZoneText.toIntOrNull() ?: UtmGeo.DEFAULT_ZONE
                            val (e, n, zz) = CoordGoto.toUtm(pr, z)
                            PendingMapGoto.set(e, n, zz)
                            DeepLinkHolder.setRoute(Routes.TOOL_MAP2)
                            status = "در حال باز کردن نقشه…"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("برو به نقشه") }
                    Text("تشخیص خودکار: UTM (رقم≥۴) یا جغرافیایی (رقم≤۳)", fontSize = 10.sp, color = TextSecondary)
                }
            }

            // —— تبدیل دستی ——
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("تبدیل مختصات / ورود دستی", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = northernHemi, onClick = { northernHemi = true }, label = { Text("شمال N") })
                        FilterChip(selected = !northernHemi, onClick = { northernHemi = false }, label = { Text("جنوب S") })
                        Text("نیمکره برای UTM→Geo", fontSize = 11.sp, color = TextSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            manLat, { manLat = it }, label = { Text("Lat") },
                            modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb,
                            supportingText = { Text("اعشاری یا درجه دقیقه ثانیه", fontSize = 10.sp) }
                        )
                        OutlinedTextField(manLon, { manLon = it }, label = { Text("Lon") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(manE, { manE = it }, label = { Text("Easting") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                        OutlinedTextField(manN, { manN = it }, label = { Text("Northing") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                        OutlinedTextField(manZ, { manZ = it }, label = { Text("Z") }, modifier = Modifier.width(72.dp), singleLine = true, keyboardOptions = numKb)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { convertGeoToUtm() }, modifier = Modifier.weight(1f)) { Text("Geo→UTM") }
                        OutlinedButton(onClick = { convertUtmToGeo() }, modifier = Modifier.weight(1f)) { Text("UTM→Geo") }
                        Button(
                            onClick = { addManualPoint() },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier.weight(1f)
                        ) { Text("افزودن") }
                    }
                    if (fmtPreview.isNotBlank()) {
                        Text(fmtPreview, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(fmtPreview))
                            status = "فرمت‌ها کپی شد"
                        }) { Text("کپی فرمت‌ها") }
                    }
                    if (liveLat != null && liveLon != null) {
                        TextButton(onClick = {
                            manLat = formatEn("%.8f", liveLat!!)
                            manLon = formatEn("%.8f", liveLon!!)
                            convertGeoToUtm()
                        }) { Text("پر کردن از GPS زنده") }
                    }
                    OutlinedButton(
                        onClick = { batchOpen.launch(arrayOf("*/*", "text/*", "application/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ورود فایل نقاط (Batch تبدیل)") }
                }
            }

            // —— ابزار فاصله / آفست / قطب‌نما ——
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("فاصله / آزیموت / قطب‌نما", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("دو نقطه را از لیست انتخاب کنید (A سپس B) — یا هدف‌گیری با GPS", fontSize = 11.sp, color = TextSecondary)

                    // فاز ۳: Stakeout زنده
                    val so = stakeoutMetrics()
                    if (so != null && (stakeoutTargetId != null || selA != null)) {
                        val (dist, dE, dN) = so
                        val tgtName = points.find { it.id == (stakeoutTargetId ?: selA) }?.name ?: "?"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (dist < 2.0) Color(0xFF2E7D32).copy(alpha = 0.25f)
                            else if (dist < 10.0) Color(0xFFF9A825).copy(alpha = 0.2f)
                            else Color(0xFF1565C0).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Stakeout → $tgtName", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                Text(
                                    "فاصله: ${formatEn("%.2f", dist)} m",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (dist < 2.0) Color(0xFF2E7D32) else TextPrimary,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "ΔE ${formatEn("%+.2f", dE)}   ΔN ${formatEn("%+.2f", dN)}",
                                    color = TextSecondary, fontSize = 13.sp
                                )
                                Text(
                                    "شرق ${if (dE >= 0) "برو" else "برگرد"} ${formatEn("%.2f", kotlin.math.abs(dE))} m · شمال ${if (dN >= 0) "برو" else "برگرد"} ${formatEn("%.2f", kotlin.math.abs(dN))} m",
                                    color = TextSecondary, fontSize = 11.sp
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("هدف Stakeout:", fontSize = 11.sp, color = TextSecondary)
                        OutlinedButton(
                            onClick = {
                                stakeoutTargetId = selA
                                stakeoutNearAlerted = false
                                status = if (selA != null) "هدف Stakeout تنظیم شد" else "اول نقطه A را انتخاب کنید"
                            },
                            enabled = selA != null
                        ) { Text("A → هدف") }
                        if (stakeoutTargetId != null) {
                            OutlinedButton(onClick = { stakeoutTargetId = null }) { Text("لغو هدف") }
                        }
                    }

                    // فاز ۳: شبکه نقاط
                    Text("شبکه نقاط از مبنای A", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(gridRows, { gridRows = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("ردیف") }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(gridCols, { gridCols = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("ستون") }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(gridStep, { gridStep = it },
                            label = { Text("گام m") }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(gridAz, { gridAz = it },
                            label = { Text("آزیموت") }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Button(
                        onClick = { generateGrid() },
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selA != null
                    ) { Text("تولید شبکه نقاط") }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val hdg = compassHeading.value.toDouble()
                        val targetPt = points.find { it.id == selA }
                        val targetBearing: Float? = if (targetPt != null && liveLat != null && liveLon != null) {
                            LocationStore.distanceAndAzimuth(liveLat!!, liveLon!!, targetPt.lat, targetPt.lon).second.toFloat()
                        } else null
                        CompassDial(
                            headingDeg = compassHeading.value,
                            targetBearingDeg = targetBearing,
                            size = 88.dp,
                            accent = color
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "جهت فعلی: ${formatEn("%.0f", hdg)}°",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            if (targetPt != null && liveLat != null && liveLon != null) {
                                val (d, az) = LocationStore.distanceAndAzimuth(liveLat!!, liveLon!!, targetPt.lat, targetPt.lon)
                                val turn = CompassMath.turnToTarget(hdg, az)
                                val turnTxt = when {
                                    kotlin.math.abs(turn) < 5 -> "رو به هدف"
                                    turn > 0 -> formatEn("%.0f° به راست", turn)
                                    else -> formatEn("%.0f° به چپ", -turn)
                                }
                                Text("هدف: ${targetPt.name}", color = color, fontSize = 13.sp)
                                Text(
                                    "فاصله ${formatEn("%.1f", d)} m | آزیموت ${formatEn("%.0f", az)}°",
                                    fontSize = 12.sp, color = TextSecondary
                                )
                                Text(turnTxt, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
                            } else {
                                Text("نقطه A را انتخاب کنید و GPS روشن باشد تا هدف‌گیری فعال شود", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                    if (distInfo.isNotBlank()) {
                        Text(distInfo, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    if (points.size >= 2) {
                        Text("طول مسیر: ${formatEn("%.3f", pathLen)} m", fontSize = 12.sp, color = TextSecondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            offsetDist, { offsetDist = it },
                            label = { Text("فاصله m") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = numKb
                        )
                        OutlinedTextField(
                            offsetAz, { offsetAz = it },
                            label = { Text("آزیموت °") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = numKb
                        )
                        Button(
                            onClick = { applyOffset() },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) { Text("آفست") }
                    }
                    Text("مبنا: نقطه A انتخاب‌شده یا GPS زنده", fontSize = 10.sp, color = TextSecondary)
                }
            }

            // —— نقشه ساده ——
            if (points.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("نقشهٔ نقاط (${points.size})", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        PointsSketch(points, color, Modifier.fillMaxWidth().height(180.dp))
                    }
                }
            }

            // —— لیست نقاط ——
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("نقاط ثبت‌شده (${points.size})", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                        if (points.isNotEmpty()) {
                            TextButton(onClick = {
                                persist(emptyList())
                                LocationStore.clear(context)
                                selA = null; selB = null
                                status = "همه نقاط پاک شد"
                            }) { Text("پاک‌سازی", color = Color(0xFFC62828)) }
                        }
                    }
                    if (points.isEmpty()) {
                        Text("هنوز نقطه‌ای ثبت نشده", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        points.asReversed().forEach { p ->
                            PointRow(
                                p = p,
                                color = color,
                                selectedA = selA == p.id,
                                selectedB = selB == p.id,
                                timeLabel = if (p.timeMs > 0) timeFmt.format(Date(p.timeMs)) else "",
                                onSelect = {
                                    when {
                                        selA == null || (selA != null && selB != null) -> {
                                            selA = p.id; selB = null
                                        }
                                        selA == p.id -> selA = null
                                        else -> selB = p.id
                                    }
                                },
                                onCopy = {
                                    copyText(
                                        p.name,
                                        formatEn("%.8f,%.8f | E%.3f N%.3f Z%d", p.lat, p.lon, p.easting, p.northing, p.zone)
                                    )
                                },
                                onMaps = { openMaps(p) },
                                onNeshan = { openNeshan(p) },
                                onShare = { sharePoint(p) },
                                onDelete = {
                                    persist(points.filter { it.id != p.id })
                                    if (selA == p.id) selA = null
                                    if (selB == p.id) selB = null
                                }
                            )
                            HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                        }
                    }
                }
            }

            // —— ژئوئید / ارتفاع ——
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ارتفاع ژئوئید (N)", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Z_ortho = Z_ellip − N  |  برای دقت بالا N را از مدل محلی وارد کنید", fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        geoidNText, { geoidNText = it },
                        label = { Text("N (متر)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = numKb
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            val n = geoidNText.toDoubleOrNullFa() ?: GeoidHeight.IRAN_DEFAULT_N
                            if (points.isEmpty()) { status = "نقطه‌ای نیست"; return@OutlinedButton }
                            persist(points.map { pt ->
                                pt.copy(alt = GeoidHeight.ellipsoidToOrtho(pt.alt, n))
                            })
                            status = "ارتفاع همه نقاط → ارتومتریک (N=$n)"
                        }, modifier = Modifier.weight(1f)) { Text("بیضوی→ارتو") }
                        OutlinedButton(onClick = {
                            val n = geoidNText.toDoubleOrNullFa() ?: GeoidHeight.IRAN_DEFAULT_N
                            if (points.isEmpty()) { status = "نقطه‌ای نیست"; return@OutlinedButton }
                            persist(points.map { pt ->
                                pt.copy(alt = GeoidHeight.orthoToEllipsoid(pt.alt, n))
                            })
                            status = "ارتفاع همه نقاط → بیضوی (N=$n)"
                        }, modifier = Modifier.weight(1f)) { Text("ارتو→بیضوی") }
                    }
                }
            }

            // —— خروجی ——
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("خروجی فایل", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("txt", "dat", "gsi", "csv", "kml").forEach { ext ->
                            OutlinedButton(onClick = { export(ext) }) { Text(ext.uppercase()) }
                        }
                    }
                    Text("ذخیره در Documents/AdelAssistant", fontSize = 10.sp, color = TextSecondary)
                }
            }

            if (status.isNotBlank()) {
                Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
        }
    }


    if (showAccWarn) {
            AlertDialog(
                onDismissRequest = { showAccWarn = false },
                title = { Text("دقت نامناسب") },
                text = {
                    Text(
                        "دقت فعلی ${String.format(Locale.US, "%.1f", liveAcc)} متر است و از آستانه ${String.format(Locale.US, "%.0f", accThreshold)} متر بدتر است. باز هم ثبت شود؟"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showAccWarn = false
                        doCaptureOnce()
                    }) { Text("ثبت با همین دقت") }
                },
                dismissButton = {
                    TextButton(onClick = { showAccWarn = false }) { Text("انصراف") }
                }
            )
        }
    
        if (showExtGps) {
            val bonded = remember {
                try {
                    val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    mgr.adapter?.bondedDevices?.toList().orEmpty()
                } catch (_: SecurityException) {
                    emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            AlertDialog(
                onDismissRequest = { showExtGps = false },
                title = { Text("گیرنده GPS بلوتوث (NMEA)") },
                text = {
                    Column {
                        if (extConnecting) Text("در حال اتصال…", color = TextSecondary)
                        if (bonded.isEmpty()) {
                            Text("دستگاه جفت‌شده‌ای نیست. اول گیرنده را در تنظیمات بلوتوث Pair کنید.", fontSize = 13.sp)
                        } else {
                            bonded.forEach { dev ->
                                Text(
                                    dev.name ?: dev.address,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showExtGps = false
                                            connectExternalGps(dev)
                                        }
                                        .padding(vertical = 10.dp),
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                        Text(
                            "جملات GGA خوانده می‌شود. Fix: GPS / DGPS / RTK Float / RTK Fixed",
                            fontSize = 11.sp, color = TextSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExtGps = false }) { Text("بستن") }
                }
            )
        }
}


@Composable
private fun PointRow(
    p: LocationPoint,
    color: Color,
    selectedA: Boolean,
    selectedB: Boolean,
    timeLabel: String,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onMaps: () -> Unit,
    onNeshan: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val bg = when {
        selectedA -> color.copy(alpha = 0.15f)
        selectedB -> color.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable { onSelect() }
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    buildString {
                        append(p.name)
                        if (p.code.isNotBlank()) append(" [${p.code}]")
                        if (selectedA) append("  ·A")
                        if (selectedB) append("  ·B")
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
                Text(
                    formatEn("E %.3f  N %.3f  Z%d", p.easting, p.northing, p.zone),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    formatEn("%.7f , %.7f   h=%.1f", p.lat, p.lon, p.alt) +
                        (if (p.accuracy > 0) formatEn("  ±%.1fm", p.accuracy) else "") +
                        (when (p.fix) {
                            4 -> "  RTK"
                            5 -> "  Float"
                            2 -> "  DGPS"
                            1 -> "  GPS"
                            else -> ""
                        }) +
                        (if (p.isExternal) "  EXT" else "") +
                        (if (timeLabel.isNotBlank()) "  $timeLabel" else ""),
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ContentCopy, "کپی", tint = color, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onMaps, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Map, "گوگل‌مپ", tint = color, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onNeshan, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Navigation, "نشان", tint = color, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Share, "اشتراک", tint = color, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "حذف", tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PointsSketch(points: List<LocationPoint>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFF1A1D21), RoundedCornerShape(8.dp))) {
        if (points.isEmpty()) return@Canvas
        val xs = points.map { it.easting }
        val ys = points.map { it.northing }
        val minX = xs.minOrNull()!!
        val maxX = xs.maxOrNull()!!
        val minY = ys.minOrNull()!!
        val maxY = ys.maxOrNull()!!
        val dx = (maxX - minX).coerceAtLeast(1.0)
        val dy = (maxY - minY).coerceAtLeast(1.0)
        val pad = 24f
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        fun sx(e: Double) = pad + ((e - minX) / dx * w).toFloat()
        fun sy(n: Double) = pad + ((maxY - n) / dy * h).toFloat() // north up
        // path
        for (i in 0 until points.size - 1) {
            drawLine(
                color.copy(alpha = 0.5f),
                Offset(sx(points[i].easting), sy(points[i].northing)),
                Offset(sx(points[i + 1].easting), sy(points[i + 1].northing)),
                strokeWidth = 2f
            )
        }
        points.forEachIndexed { i, p ->
            val c = Offset(sx(p.easting), sy(p.northing))
            drawCircle(color, radius = 6f, center = c)
            drawCircle(Color.White, radius = 2.5f, center = c)
        }
    }
}
