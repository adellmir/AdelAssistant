package com.adel.assistant.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.UtmGeo
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import kotlin.math.*

@Composable
fun ChainageScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)
    val clipboard = LocalClipboardManager.current
    var kmInput by remember { mutableStateOf("") }

    var coordText by remember { mutableStateOf<String?>(null) }
    var detailText by remember { mutableStateOf<String?>(null) }
    var mapsUrl by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var myLocationResult by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted }

    fun calc() {
        errorText = null; coordText = null; detailText = null; mapsUrl = null
        val km = kmInput.toDoubleOrNullFa()
        if (km == null) { errorText = "کیلومتراژ نامعتبر است"; return }
        val p = TunnelReportStore.findByKm(context, km)
        if (p == null) { errorText = "داده‌ای برای این کیلومتراژ موجود نیست (فایل نقاط آپلود شده؟)"; return }
        val (lat, lon) = UtmGeo.toLatLon(p.x, p.y, UtmGeo.DEFAULT_ZONE)
        coordText = formatEn("X: %.3f   Y: %.3f", p.x, p.y)
        detailText = formatEn(
            "Z: %.3f   اختلاف‌تراز: %s\nشیب: %s   نوع نقطه: %s\nLat/Lon: %.6f, %.6f  (زون %d)",
            p.z, p.elevDiff, p.slope, p.type, lat, lon, UtmGeo.DEFAULT_ZONE
        )
        mapsUrl = UtmGeo.googleMapsUrl(lat, lon)
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
            val points = TunnelReportStore.allPoints(context)
            if (points.isEmpty()) { myLocationResult = "فایل نقاط آپلود نشده"; return }
            val zone = UtmGeo.zoneFromLon(best.longitude)
            val (ex, ny) = UtmGeo.fromLatLon(best.latitude, best.longitude, zone)
            val nearest = points.minByOrNull { p ->
                val dx = p.x - ex; val dy = p.y - ny
                dx * dx + dy * dy
            }!!
            val dist = sqrt((nearest.x - ex).pow(2) + (nearest.y - ny).pow(2))
            myLocationResult = formatEn(
                "GPS: %.6f, %.6f (زون %d)\nUTM: E=%.3f  N=%.3f\nنزدیک‌ترین نقطه: %s  فاصله افقی ≈ %.1f m\n(دقت GPS گوشی معمولاً ۳–۱۵ متر)",
                best.latitude, best.longitude, zone, ex, ny, nearest.pointNo, dist
            )
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
            value = kmInput, onValueChange = { kmInput = com.adel.assistant.data.filterNumericInput(it) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            label = { Text("کیلومتراژ") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { calc() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
            Text("محاسبه")
        }
        Spacer(modifier = Modifier.height(12.dp))

        errorText?.let {
            Text(it, color = Color(0xFFC2685E), style = MaterialTheme.typography.bodySmall)
        }

        coordText?.let { txt ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SurfaceColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clipboard.setText(AnnotatedString(txt))
                    }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(txt, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("(ضربه بزن تا کپی بشه)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C8A6B))
                }
            }
        }
        detailText?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        mapsUrl?.let { url ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
            ) {
                Text(url, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = color)
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