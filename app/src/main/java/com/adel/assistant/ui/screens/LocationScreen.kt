package com.adel.assistant.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.ToolbarIcon
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import com.adel.assistant.ui.theme.TextMuted
import com.adel.assistant.ui.theme.Background
import java.io.File
import java.io.FileWriter

private data class GpsPoint(val name: String, val lat: Double, val lon: Double, val alt: Double)

@Composable
fun LocationScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)
    var points by remember { mutableStateOf(listOf<GpsPoint>()) }
    var pointName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    fun captureLocation() {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.getProviders(true)
            var best: Location? = null
            for (provider in providers) {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            if (best != null) {
                val name = if (pointName.isBlank()) "P${points.size + 1}" else pointName
                points = points + GpsPoint(name, best.latitude, best.longitude, best.altitude)
                pointName = ""
                status = "نقطه ثبت شد (دقت تقریبی: ${best.accuracy.toInt()} متر)"
            } else {
                status = "هنوز موقعیتی از GPS دریافت نشده — کمی صبر کن و دوباره امتحان کن (بهتره فضای باز باشی)"
            }
        } catch (e: SecurityException) {
            status = "دسترسی موقعیت داده نشده"
        }
    }

    fun exportTxt(): File {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "points.txt")
        FileWriter(f).use { w ->
            points.forEach { p -> w.write("${p.name}\t${p.lat}\t${p.lon}\t${p.alt}\n") }
        }
        return f
    }

    fun exportKml(): File {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, "points.kml")
        FileWriter(f).use { w ->
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n")
            points.forEach { p ->
                w.write("<Placemark><name>${p.name}</name><Point><coordinates>${p.lon},${p.lat},${p.alt}</coordinates></Point></Placemark>\n")
            }
            w.write("</Document>\n</kml>")
        }
        return f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "مکان", color = color, onBack = onBack)
        Text(
            "دقت GPS گوشی معمولاً ۳ تا ۱۰ متر است — برای کاربردهای تقریبی مناسب است، نه برداشت دقیق مهندسی.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = pointName,
            onValueChange = { pointName = it },
            label = { Text("نام نقطه (اختیاری)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { captureLocation() },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ثبت نقطه از موقعیت فعلی") }
        if (status.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(points.size) { i ->
                val p = points[i]
                Text("${p.name}: ${p.lat}, ${p.lon} (ارتفاع ${p.alt.toInt()})", style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
            OutlinedButton(
                onClick = { status = "ذخیره شد: " + exportTxt().absolutePath },
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(4.dp)); Text("TXT") }
            OutlinedButton(
                onClick = { status = "ذخیره شد: " + exportKml().absolutePath },
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.Outlined.Map, null); Spacer(Modifier.width(4.dp)); Text("KML") }
        }
    }
}