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

    // averaging
    var averaging by remember { mutableStateOf(false) }
    var avgSamples by remember { mutableStateOf(listOf<Location>()) }
    var avgSeconds by remember { mutableStateOf(10) }

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

    fun updateLiveFrom(loc: Location) {
        liveLat = loc.latitude
        liveLon = loc.longitude
        liveAlt = if (loc.hasAltitude()) loc.altitude else 0.0
        liveAcc = if (loc.hasAccuracy()) loc.accuracy else -1f
        liveProvider = loc.provider ?: ""
        if (autoZone) {
            zoneText = UtmGeo.zoneFromLon(loc.longitude).toString()
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
            timeMs = System.currentTimeMillis()
        )
    }

    fun captureOnce() {
        if (!ensurePermission()) return
        val loc = readBestLocation()
        if (loc == null) {
            status = "موقعیت در دسترس نیست — GPS را روشن کنید"
            return
        }
        updateLiveFrom(loc)
        val p = makePoint(
            loc.latitude, loc.longitude,
            if (loc.hasAltitude()) loc.altitude else 0.0,
            if (loc.hasAccuracy()) loc.accuracy else -1f,
            pointName, pointCode
        )
        persist(points + p)
        status = "ثبت شد: ${p.name}  دقت≈${if (p.accuracy > 0) "%.1f m".format(p.accuracy) else "؟"}"
        pointName = ""
    }

    fun startAverage() {
        if (!ensurePermission()) return
        averaging = true
        avgSamples = emptyList()
        status = "میانگین‌گیری ${avgSeconds} ثانیه…"
        Handler(Looper.getMainLooper()).postDelayed({
            averaging = false
            val samples = avgSamples
            if (samples.isEmpty()) {
                status = "نمونه‌ای دریافت نشد"
                return@postDelayed
            }
            val lat = samples.map { it.latitude }.average()
            val lon = samples.map { it.longitude }.average()
            val alt = samples.filter { it.hasAltitude() }.map { it.altitude }.let { if (it.isEmpty()) 0.0 else it.average() }
            val acc = samples.filter { it.hasAccuracy() }.map { it.accuracy }.minOrNull() ?: -1f
            val p = makePoint(lat, lon, alt, acc, pointName.ifBlank { "AVG${points.size + 1}" }, pointCode)
            persist(points + p)
            status = "میانگین ${samples.size} نمونه ثبت شد — ${p.name}"
            pointName = ""
            avgSamples = emptyList()
        }, avgSeconds * 1000L)
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
