package com.adel.assistant.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import kotlin.math.*

/** تبدیل ساده‌ی UTM (Zone 40N, WGS84) به Lat/Lon */
private fun utmToLatLon(x: Double, y: Double): Pair<Double, Double> {
    val a = 6378137.0
    val f = 1 / 298.257223563
    val k0 = 0.9996
    val e = sqrt(f * (2 - f))
    val e1sq = e * e / (1 - e * e)
    val m = y / k0
    val mu = m / (a * (1 - e * e / 4 - 3 * e.pow(4) / 64 - 5 * e.pow(6) / 256))
    val e1 = (1 - sqrt(1 - e * e)) / (1 + sqrt(1 - e * e))
    val j1 = 3 * e1 / 2 - 27 * e1.pow(3) / 32
    val j2 = 21 * e1.pow(2) / 16 - 55 * e1.pow(4) / 32
    val j3 = 151 * e1.pow(3) / 96
    val j4 = 1097 * e1.pow(4) / 512
    val fp = mu + j1 * sin(2 * mu) + j2 * sin(4 * mu) + j3 * sin(6 * mu) + j4 * sin(8 * mu)
    val c1 = e1sq * cos(fp).pow(2)
    val t1 = tan(fp).pow(2)
    val r1 = a * (1 - e * e) / (1 - e * e * sin(fp).pow(2)).pow(1.5)
    val n1 = a / sqrt(1 - e * e * sin(fp).pow(2))
    val d = (x - 500000.0) / (n1 * k0)
    val lat = fp - (n1 * tan(fp) / r1) * (d.pow(2) / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1.pow(2) - 9 * e1sq) * d.pow(4) / 24 +
            (61 + 90 * t1 + 298 * c1 + 45 * t1.pow(2) - 252 * e1sq - 3 * c1.pow(2)) * d.pow(6) / 720)
    val lon = (d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1.pow(2) + 8 * e1sq + 24 * t1.pow(2)) * d.pow(5) / 120) / cos(fp)
    val zoneCentralMeridian = 57.0 // Zone 40N
    return Pair(Math.toDegrees(lat), zoneCentralMeridian + Math.toDegrees(lon))
}

@Composable
fun ChainageScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var kmInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var myLocationResult by remember { mutableStateOf<String?>(null) }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted }

    fun calc() {
        val km = kmInput.toDoubleOrNull()
        if (km == null) { result = "کیلومتراژ نامعتبر است"; return }
        val p = TunnelReportStore.findByKm(context, km)
        if (p == null) { result = "داده‌ای برای این کیلومتراژ موجود نیست (فایل نقاط آپلود شده؟)"; return }
        val (lat, lon) = utmToLatLon(p.x, p.y)
        result = "X: %.3f   Y: %.3f\nZ: %.3f   اختلاف‌تراز: %s\nشیب: %s   نوع نقطه: %s\nLat/Lon: %.6f, %.6f\nhttps://maps.google.com/?q=%.6f,%.6f"
            .format(p.x, p.y, p.z, p.elevDiff, p.slope, p.type, lat, lon, lat, lon)
    }

    fun findMyLocation() {
        if (!hasPermission) { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION); return }
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (provider in lm.getProviders(true)) {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            if (best == null) { myLocationResult = "موقعیتی دریافت نشد، کمی صبر کن"; return }
            // این یک تبدیل ساده‌ی معکوس نیست — نزدیک‌ترین نقطه بر اساس فاصله‌ی تقریبی محاسبه می‌شود
            val points = TunnelReportStore.allPoints(context)
            if (points.isEmpty()) { myLocationResult = "فایل نقاط آپلود نشده"; return }
            // تقریب: نزدیک‌ترین نقطه بر اساس فاصله‌ی درجه‌ای (برای دقت بالاتر نیاز به تبدیل دقیق‌تر lat/lon->UTM است)
            myLocationResult = "دقت GPS ارتفاع/موقعیت تقریبی است (خطای ۱۰-۲۰ متر). این محاسبه در نسخه‌ی بعد کامل می‌شود."
        } catch (e: SecurityException) {
            myLocationResult = "دسترسی موقعیت داده نشده"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "کیلومتراژ/چینیج", color = color, onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = kmInput, onValueChange = { kmInput = it },
            label = { Text("کیلومتراژ") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { calc() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
            Text("محاسبه")
        }
        Spacer(modifier = Modifier.height(12.dp))
        result?.let {
            Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { findMyLocation() }, modifier = Modifier.fillMaxWidth()) {
            Text("موقعیت من (نزدیک‌ترین نقطه)")
        }
        myLocationResult?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        }
    }
}
