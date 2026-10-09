package com.adel.assistant.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * انتخاب موقعیت پروژه روی نقشه خیابان/ماهواره (اینترنت).
 * نگه‌داشتن > ۱.۵ ثانیه = پیشنهاد ثبت؛ ضربدر روی نقطه.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ProjectLocationPickerDialog(
    initialLat: Double = 0.0,
    initialLon: Double = 0.0,
    onConfirm: (lat: Double, lon: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var satellite by remember { mutableStateOf(false) }
    var pendingLat by remember { mutableStateOf<Double?>(null) }
    var pendingLon by remember { mutableStateOf<Double?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var isUpdate by remember { mutableStateOf(initialLat != 0.0 || initialLon != 0.0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("نگه‌داشتن ۱.۵ثانیه روی نقشه برای ثبت") }

    val startLat = if (initialLat != 0.0) initialLat else 35.6892
    val startLon = if (initialLon != 0.0) initialLon else 51.3890

    fun html(sat: Boolean, lat: Double, lon: Double, markLat: Double, markLon: Double): String {
        val layerJs = if (sat) {
            """L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',{maxZoom:19,attribution:'Esri'}).addTo(map);"""
        } else {
            """L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'OSM'}).addTo(map);"""
        }
        val hasMark = markLat != 0.0 || markLon != 0.0
        val markJs = if (hasMark) {
            """
            var icon = L.divIcon({className:'x-icon', html:'<div style="color:#e53935;font-size:28px;font-weight:bold;line-height:28px;text-align:center;">✕</div>', iconSize:[28,28], iconAnchor:[14,14]});
            var marker = L.marker([$markLat, $markLon], {icon: icon}).addTo(map);
            """
        } else "var marker = null;"
        return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
html,body,#map{margin:0;padding:0;height:100%;width:100%;}
.x-icon{background:transparent !important;border:none !important;}
</style></head><body>
<div id="map"></div>
<script>
var map = L.map('map', {zoomControl:true}).setView([$lat, $lon], 15);
$layerJs
$markJs
var holdTimer = null;
var holdLatLng = null;
function clearHold(){ if(holdTimer){ clearTimeout(holdTimer); holdTimer=null; } }
map.on('mousedown touchstart', function(e){
  holdLatLng = e.latlng;
  clearHold();
  holdTimer = setTimeout(function(){
    if(holdLatLng){
      Android.onLongPress(holdLatLng.lat, holdLatLng.lng);
    }
  }, 1500);
});
map.on('mouseup mouseout touchend touchcancel touchmove mousemove dragstart', clearHold);
map.on('click', function(e){ /* short click ignored for register */ });
function setCross(lat, lon){
  if(marker) map.removeLayer(marker);
  var icon = L.divIcon({className:'x-icon', html:'<div style="color:#e53935;font-size:28px;font-weight:bold;line-height:28px;text-align:center;">✕</div>', iconSize:[28,28], iconAnchor:[14,14]});
  marker = L.marker([lat, lon], {icon: icon}).addTo(map);
  map.panTo([lat, lon]);
}
function goTo(lat, lon){ map.setView([lat, lon], 16); }
</script></body></html>
""".trimIndent()
    }

    fun locateGps() {
        try {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (pr in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (!lm.isProviderEnabled(pr)) continue
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(pr) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            if (best == null) {
                status = "موقعیت GPS در دسترس نیست"
                return
            }
            val la = best.latitude
            val lo = best.longitude
            webView?.evaluateJavascript("goTo($la, $lo);", null)
            status = "موقعیت GPS روی نقشه"
        } catch (_: SecurityException) {
            status = "مجوز موقعیت لازم است"
        } catch (e: Exception) {
            status = "خطا GPS: ${e.message}"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) locateGps()
        else status = "مجوز موقعیت رد شد"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت موقعیت پروژه") },
        text = {
            Column(Modifier.fillMaxWidth().height(420.dp)) {
                Text(status, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        val ok = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (ok) locateGps()
                        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }) { Icon(Icons.Outlined.MyLocation, "GPS") }
                    IconButton(onClick = {
                        satellite = !satellite
                        val ml = if (initialLat != 0.0) initialLat else startLat
                        val mo = if (initialLon != 0.0) initialLon else startLon
                        val markL = pendingLat ?: if (initialLat != 0.0) initialLat else 0.0
                        val markO = pendingLon ?: if (initialLon != 0.0) initialLon else 0.0
                        webView?.loadDataWithBaseURL(
                            "https://localhost/",
                            html(satellite, ml, mo, markL, markO),
                            "text/html", "UTF-8", null
                        )
                    }) {
                        Icon(
                            if (satellite) Icons.Outlined.Map else Icons.Outlined.Public,
                            if (satellite) "خیابان" else "ماهواره"
                        )
                    }
                    Text(
                        if (satellite) "ماهواره" else "خیابان",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(4.dp))
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onLongPress(lat: Double, lon: Double) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        pendingLat = lat
                                        pendingLon = lon
                                        isUpdate = initialLat != 0.0 || initialLon != 0.0
                                        showConfirm = true
                                    }
                                }
                            }, "Android")
                            loadDataWithBaseURL(
                                "https://localhost/",
                                html(false, startLat, startLon,
                                    if (initialLat != 0.0) initialLat else 0.0,
                                    if (initialLon != 0.0) initialLon else 0.0),
                                "text/html", "UTF-8", null
                            )
                            webView = this
                        }
                    },
                    update = { }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )

    if (showConfirm && pendingLat != null && pendingLon != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(if (isUpdate) "تغییر محل ثبت؟" else "ثبت موقعیت؟") },
            text = {
                Text(
                    if (isUpdate)
                        "موقعیت قبلی وجود دارد. آیا محل جدید جایگزین شود؟\n" +
                            String.format(java.util.Locale.US, "%.5f , %.5f", pendingLat, pendingLon)
                    else
                        String.format(java.util.Locale.US, "ثبت مختصات:\n%.5f , %.5f", pendingLat, pendingLon)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val la = pendingLat!!
                    val lo = pendingLon!!
                    webView?.evaluateJavascript("setCross($la, $lo);", null)
                    onConfirm(la, lo)
                    showConfirm = false
                    onDismiss()
                }) { Text(if (isUpdate) "بله، تغییر بده" else "ثبت") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("انصراف") }
            }
        )
    }
}
