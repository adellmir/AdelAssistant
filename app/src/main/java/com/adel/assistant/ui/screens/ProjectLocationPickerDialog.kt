package com.adel.assistant.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.adel.assistant.data.NeshanLinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * انتخاب موقعیت پروژه.
 * - نگه‌داشتن ۱.۵ثانیه روی نقشه → پیشنهاد ثبت
 * - چسباندن لینک نشان → دکمه ثبت
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
    val scope = rememberCoroutineScope()
    var satellite by remember { mutableStateOf(false) }
    var pendingLat by remember { mutableStateOf<Double?>(if (initialLat != 0.0) initialLat else null) }
    var pendingLon by remember { mutableStateOf<Double?>(if (initialLon != 0.0) initialLon else null) }
    var showConfirm by remember { mutableStateOf(false) }
    var isUpdate by remember { mutableStateOf(initialLat != 0.0 || initialLon != 0.0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember {
        mutableStateOf("۱.۵ثانیه روی نقشه نگه دارید یا لینک نشان را بچسبانید")
    }
    var linkText by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }

    val startLat = if (initialLat != 0.0) initialLat else 36.2970
    val startLon = if (initialLon != 0.0) initialLon else 59.6062

    fun html(sat: Boolean, lat: Double, lon: Double, markLat: Double, markLon: Double): String {
        val layerJs = if (sat) {
            "L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',{maxZoom:19}).addTo(map);"
        } else {
            "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19}).addTo(map);"
        }
        val hasMark = markLat != 0.0 || markLon != 0.0
        val markJs = if (hasMark) {
            """
            var icon = L.divIcon({className:'x-icon',html:'<div style="color:#e53935;font-size:28px;font-weight:bold;text-align:center;">✕</div>',iconSize:[28,28],iconAnchor:[14,14]});
            var marker = L.marker([$markLat,$markLon],{icon:icon}).addTo(map);
            """
        } else "var marker = null;"
        return """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
html,body,#map{margin:0;padding:0;height:100%;width:100%;touch-action:pan-x pan-y;}
.x-icon{background:transparent!important;border:none!important;}
</style></head><body><div id="map"></div>
<script>
var map = L.map('map',{zoomControl:true, tap:true}).setView([$lat,$lon],15);
$layerJs
$markJs
var holdTimer=null;
function clearHold(){ if(holdTimer){ clearTimeout(holdTimer); holdTimer=null; } }
function startHold(latlng){
  clearHold();
  holdTimer=setTimeout(function(){
    if(latlng && window.Android && Android.onLongPress){
      Android.onLongPress(latlng.lat, latlng.lng);
    }
  },1500);
}
map.on('contextmenu', function(e){
  if(window.Android && Android.onLongPress) Android.onLongPress(e.latlng.lat, e.latlng.lng);
});
map.on('mousedown', function(e){ startHold(e.latlng); });
map.on('mouseup mouseout', clearHold);
map.on('touchstart', function(e){
  if(e.latlng) startHold(e.latlng);
  else if(e.originalEvent && e.originalEvent.touches && e.originalEvent.touches.length===1){
    var t=e.originalEvent.touches[0];
    var p=map.mouseEventToLatLng({clientX:t.clientX,clientY:t.clientY});
    startHold(p);
  }
});
map.on('touchend touchcancel touchmove dragstart move', clearHold);
function setCross(lat,lon){
  if(marker) map.removeLayer(marker);
  var icon=L.divIcon({className:'x-icon',html:'<div style="color:#e53935;font-size:28px;font-weight:bold;text-align:center;">✕</div>',iconSize:[28,28],iconAnchor:[14,14]});
  marker=L.marker([lat,lon],{icon:icon}).addTo(map);
  map.panTo([lat,lon]);
}
function goTo(lat,lon){ map.setView([lat,lon],16); }
function getCenter(cb){
  var c=map.getCenter();
  if(window.Android && Android.onCenter) Android.onCenter(c.lat,c.lng);
}
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
            webView?.evaluateJavascript("goTo($la,$lo);", null)
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
        if (granted) locateGps() else status = "مجوز موقعیت رد شد"
    }

    fun applyCoords(la: Double, lo: Double, fromLink: Boolean = false) {
        pendingLat = la
        pendingLon = lo
        isUpdate = initialLat != 0.0 || initialLon != 0.0
        webView?.evaluateJavascript("setCross($la,$lo); goTo($la,$lo);", null)
        status = String.format(java.util.Locale.US, "انتخاب شد: %.5f , %.5f", la, lo)
        showConfirm = true
    }

    fun resolveLinkAndRegister() {
        if (linkText.isBlank()) {
            status = "لینک نشان را وارد کنید"
            return
        }
        resolving = true
        status = "خواندن لینک…"
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                try { NeshanLinkResolver.resolve(linkText) } catch (_: Exception) { null }
            }
            resolving = false
            if (res == null) {
                status = "مختصات از لینک خوانده نشد"
            } else {
                applyCoords(res.lat, res.lon, fromLink = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت موقعیت پروژه") },
        text = {
            Column(Modifier.fillMaxWidth().height(460.dp)) {
                Text(status, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    label = { Text("لینک نشان") },
                    placeholder = { Text("https://nshn.ir/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !resolving
                )
                Spacer(Modifier.height(4.dp))
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
                        val ml = pendingLat ?: startLat
                        val mo = pendingLon ?: startLon
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
                    TextButton(onClick = {
                        // ثبت مرکز فعلی نقشه (اگر نگه‌داشتن کار نکرد)
                        webView?.evaluateJavascript("getCenter();", null)
                    }) { Text("مرکز نقشه") }
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
                                    Handler(Looper.getMainLooper()).post {
                                        applyCoords(lat, lon)
                                    }
                                }
                                @JavascriptInterface
                                fun onCenter(lat: Double, lon: Double) {
                                    Handler(Looper.getMainLooper()).post {
                                        applyCoords(lat, lon)
                                    }
                                }
                            }, "Android")
                            val markL = if (initialLat != 0.0) initialLat else 0.0
                            val markO = if (initialLon != 0.0) initialLon else 0.0
                            loadDataWithBaseURL(
                                "https://localhost/",
                                html(false, startLat, startLon, markL, markO),
                                "text/html", "UTF-8", null
                            )
                            webView = this
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (linkText.isNotBlank() && (pendingLat == null || pendingLon == null)) {
                        resolveLinkAndRegister()
                    } else if (pendingLat != null && pendingLon != null) {
                        onConfirm(pendingLat!!, pendingLon!!)
                        onDismiss()
                    } else if (linkText.isNotBlank()) {
                        resolveLinkAndRegister()
                    } else {
                        status = "نقطه را با نگه‌داشتن یا لینک انتخاب کنید"
                    }
                },
                enabled = !resolving
            ) {
                Text(if (resolving) "صبر…" else "ثبت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )

    if (showConfirm && pendingLat != null && pendingLon != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(if (isUpdate) "تغییر محل ثبت؟" else "ثبت این موقعیت؟") },
            text = {
                Text(
                    String.format(
                        java.util.Locale.US,
                        "%.6f , %.6f",
                        pendingLat, pendingLon
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(pendingLat!!, pendingLon!!)
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
