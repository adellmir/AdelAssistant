package com.adel.assistant.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adel.assistant.data.*
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.function.Consumer
import kotlin.math.*

private data class ViewerDrawing(
    val id: Int,
    val name: String,
    val model: DxfModel,
    var visible: Boolean = true
)

@Composable
fun DxfPreviewScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var drawings by remember { mutableStateOf<List<ViewerDrawing>>(emptyList()) }
    var nextDrawingId by remember { mutableStateOf(1) }
    var message by remember { mutableStateOf("برای شروع یک یا چند فایل DXF/KML/KMZ انتخاب کن") }
    var zoneText by remember { mutableStateOf("40") }
    var baseMap by remember { mutableStateOf(BaseMap.SATELLITE) }
    var showBaseMapDialog by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showDrawings by remember { mutableStateOf(false) }
    var measureMode by remember { mutableStateOf(false) }
    var measureA by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var measureB by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var distanceMsg by remember { mutableStateOf<String?>(null) }
    var myLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var tiles by remember { mutableStateOf<List<TileBmp>>(emptyList()) }
    var fitTrigger by remember { mutableStateOf(0) }
    var mapInitialized by remember { mutableStateOf(false) }
    var pickCoordinateMode by remember { mutableStateOf(false) }
    var pickedPoint by remember { mutableStateOf<PickedPoint?>(null) }
    val clipboard = LocalClipboardManager.current

    val zone = zoneText.toIntOrNull()?.coerceIn(1, 60) ?: 40
    val activeDrawings = drawings.filter { it.visible }
    val allModels = activeDrawings.map { it.model }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        try {
            val added = mutableListOf<ViewerDrawing>()
            uris.forEach { uri ->
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@forEach
                val rawName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "drawing.dxf" } ?: "drawing.dxf"
                val lower = rawName.lowercase()
                val text = bytes.toString(Charsets.UTF_8)
                val isKml = lower.endsWith(".kml") || lower.endsWith(".kmz") ||
                    (text.trimStart().startsWith("<?xml") && text.contains("<kml", true))
                val model = if (isKml) KmlParser.toDxfModel(KmlParser.parseBytes(bytes, rawName, zone))
                            else DxfParser.parse(text)
                if (!model.isEmpty) {
                    added += ViewerDrawing(nextDrawingId + added.size, rawName, model)
                }
            }
            if (added.isEmpty()) message = "موجودیتی قابل نمایش پیدا نشد"
            else {
                drawings = drawings + added
                nextDrawingId += added.size
                measureA = null; measureB = null; distanceMsg = null
                fitTrigger++
                message = "${drawings.size} نقشه باز است | ${allModels.sumOf { it.lines.size }} خط | ${allModels.sumOf { it.circles.size }} نقطه"
            }
        } catch (e: Exception) {
            message = "خطا در خواندن فایل: ${e.message ?: "نامشخص"}"
        }
    }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    fun centerOnUtm(easting: Double, northing: Double, zoom: Int = 16) {
        if (canvasSize.x <= 0f || canvasSize.y <= 0f) return
        val latLon = UtmGeo.toLatLon(easting, northing, zone)
        val latRad = Math.toRadians(latLon.first.coerceIn(-85.0, 85.0))
        val metersPerPixel = (2.0 * Math.PI * 6378137.0 * cos(latRad)) / (256.0 * (1 shl zoom))
        scale = (1.0 / metersPerPixel).toFloat().coerceIn(0.000001f, 5000f)
        offset = Offset(canvasSize.x / 2f - (easting * scale).toFloat(), canvasSize.y / 2f + (northing * scale).toFloat())
    }

    fun centerOnLatLon(lat: Double, lon: Double, zoom: Int = 12) {
        val (e, n) = UtmGeo.fromLatLon(lat, lon, zone)
        centerOnUtm(e, n, zoom)
    }

    fun readGps() {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            fun applyLocation(loc: Location?) {
                if (loc == null) {
                    message = "موقعیت فعلی دریافت نشد؛ GPS و اینترنت را بررسی کن"
                    return
                }
                val (e, n) = UtmGeo.fromLatLon(loc.latitude, loc.longitude, zone)
                myLoc = e to n
                centerOnUtm(e, n, 17)
                if (baseMap == BaseMap.NONE) baseMap = BaseMap.STREET
                message = "مرکز نقشه روی موقعیت من قرار گرفت"
            }

            // اول موقعیت واقعی را درخواست می‌کنیم؛ اگر در دسترس نبود از آخرین موقعیت معتبر استفاده می‌کنیم.
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                lm.getCurrentLocation(
                    provider,
                    CancellationSignal(),
                    ContextCompat.getMainExecutor(context),
                    Consumer { current ->
                        if (current != null) applyLocation(current)
                        else {
                            var best: Location? = null
                            for (p in lm.getProviders(true)) {
                                val loc = lm.getLastKnownLocation(p) ?: continue
                                if (best == null || loc.accuracy < best!!.accuracy) best = loc
                            }
                            applyLocation(best)
                        }
                    }
                )
            } else {
                var best: Location? = null
                for (p in lm.getProviders(true)) {
                    val loc = lm.getLastKnownLocation(p) ?: continue
                    if (best == null || loc.accuracy < best!!.accuracy) best = loc
                }
                applyLocation(best)
            }
        } catch (_: SecurityException) {
            message = "دسترسی موقعیت داده نشده"
        } catch (e: Exception) {
            message = "خطا در دریافت موقعیت: ${e.message ?: "نامشخص"}"
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) readGps()
    }

    fun fitAll(w: Float, h: Float) {
        if (w <= 0f || h <= 0f || allModels.isEmpty()) return
        var minX = Double.POSITIVE_INFINITY; var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        allModels.forEach {
            minX = min(minX, it.minX); minY = min(minY, it.minY)
            maxX = max(maxX, it.maxX); maxY = max(maxY, it.maxY)
        }
        val sx = w * .90f / (maxX - minX).coerceAtLeast(1.0).toFloat()
        val sy = h * .90f / (maxY - minY).coerceAtLeast(1.0).toFloat()
        scale = min(sx, sy).coerceIn(0.000001f, 5000f)
        offset = Offset(w / 2f - ((minX + maxX) / 2.0 * scale).toFloat(),
                        h / 2f + ((minY + maxY) / 2.0 * scale).toFloat())
    }
    fun worldToScreen(x: Double, y: Double) = Offset((x * scale + offset.x).toFloat(), (-y * scale + offset.y).toFloat())
    fun screenToWorld(sx: Float, sy: Float): Pair<Double, Double> =
        ((sx - offset.x) / scale).toDouble() to (-((sy - offset.y) / scale)).toDouble()

    LaunchedEffect(baseMap, scale, offset, canvasSize, zone) {
        if (baseMap == BaseMap.NONE || canvasSize.x <= 0f) {
            tiles = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        val corners = listOf(
            screenToWorld(0f, 0f), screenToWorld(canvasSize.x, 0f),
            screenToWorld(0f, canvasSize.y), screenToWorld(canvasSize.x, canvasSize.y)
        )
        val latLon = corners.map { UtmGeo.toLatLon(it.first, it.second, zone) }
        val minLat = latLon.minOf { it.first }; val maxLat = latLon.maxOf { it.first }
        val minLon = latLon.minOf { it.second }; val maxLon = latLon.maxOf { it.second }
        val z = estimateZoom(minLat, maxLat, minLon, maxLon, canvasSize.x)
        tiles = withContext(Dispatchers.IO) { loadEsriTilesCached(minLat, maxLat, minLon, maxLon, z, baseMap) }
    }

    LaunchedEffect(fitTrigger, canvasSize) {
        if (canvasSize.x > 0f) {
            if (allModels.isNotEmpty()) fitAll(canvasSize.x, canvasSize.y)
            else if (!mapInitialized) {
                centerOnLatLon(35.70, 51.40, 12)
                mapInitialized = true
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (baseMap == BaseMap.SATELLITE) Color(0xFF111111) else Color(0xFF202124)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 76.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(0.000001f, 5000f)
                            if (newScale != oldScale) {
                                val factor = newScale / oldScale
                                offset = Offset(
                                    centroid.x - (centroid.x - offset.x) * factor + pan.x,
                                    centroid.y - (centroid.y - offset.y) * factor + pan.y
                                )
                                scale = newScale
                            } else offset += pan
                        }
                    }
                    .pointerInput(measureMode, scale, offset) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                val factor = 1.7f
                                val ns = (scale * factor).coerceAtMost(5000f)
                                offset = Offset(tap.x - (tap.x - offset.x) * ns / scale, tap.y - (tap.y - offset.y) * ns / scale)
                                scale = ns
                            },
                            onTap = { tap ->
                                if (pickCoordinateMode) {
                                    val p = screenToWorld(tap.x, tap.y)
                                    val (lat, lon) = UtmGeo.toLatLon(p.first, p.second, zone)
                                    pickedPoint = PickedPoint(p.first, p.second, lat, lon)
                                    pickCoordinateMode = false
                                    message = "نقطه انتخاب شد"
                                    return@detectTapGestures
                                }
                                if (!measureMode) return@detectTapGestures
                                val p = screenToWorld(tap.x, tap.y)
                                if (measureA == null || measureB != null) {
                                    measureA = p; measureB = null
                                    distanceMsg = "نقطه اول انتخاب شد؛ نقطه دوم را لمس کن"
                                } else {
                                    measureB = p
                                    val d = DxfParser.horizontalDistance(measureA!!.first, measureA!!.second, p.first, p.second)
                                    distanceMsg = "فاصله افقی: ${"%.3f".format(java.util.Locale.US, d)} متر"
                                    measureMode = false
                                }
                            }
                        )
                    }
            ) {
                canvasSize = Offset(size.width, size.height)

                if (baseMap != BaseMap.NONE) {
                    tiles.forEach { t ->
                        val (e0, n0) = UtmGeo.fromLatLon(t.latNorth, t.lonWest, zone)
                        val (e1, n1) = UtmGeo.fromLatLon(t.latSouth, t.lonEast, zone)
                        val tl = worldToScreen(e0, n0); val br = worldToScreen(e1, n1)
                        val w = (br.x - tl.x).roundToInt(); val h = (br.y - tl.y).roundToInt()
                        if (w > 1 && h > 1) drawImage(t.image,
                            dstOffset = androidx.compose.ui.unit.IntOffset(tl.x.roundToInt(), tl.y.roundToInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(w, h))
                    }
                }

                activeDrawings.forEach { drawing ->
                    val m = drawing.model
                    m.lines.forEach { ln ->
                        val layer = m.layers[ln.layer]
                        if (layer?.visible == false) return@forEach
                        val c = layer?.displayColor ?: DxfParser.aciToColor(if (ln.color in 1..255) ln.color else layer?.colorAci ?: 7)
                        drawLine(c, worldToScreen(ln.x1, ln.y1), worldToScreen(ln.x2, ln.y2), strokeWidth = 2.2f)
                    }
                    m.circles.forEach { c ->
                        val layer = m.layers[c.layer]
                        if (layer?.visible == false) return@forEach
                        val col = layer?.displayColor ?: DxfParser.aciToColor(if (c.color in 1..255) c.color else layer?.colorAci ?: 7)
                        val p = worldToScreen(c.x, c.y); val r = (c.r * scale).toFloat().coerceAtLeast(3f)
                        drawCircle(col, r, p, style = Stroke(width = 2f))
                        drawLine(col, Offset(p.x-r,p.y), Offset(p.x+r,p.y), 1.8f)
                        drawLine(col, Offset(p.x,p.y-r), Offset(p.x,p.y+r), 1.8f)
                    }
                    m.texts.forEach { t ->
                        val layer = m.layers[t.layer]
                        if (layer?.visible == false) return@forEach
                        val col = layer?.displayColor ?: DxfParser.aciToColor(if (t.color in 1..255) t.color else layer?.colorAci ?: 7)
                        val p = worldToScreen(t.x, t.y)
                        val paint = android.graphics.Paint().apply {
                            this.color = col.toArgb()
                            textSize = (t.height * scale).toFloat().coerceIn(12f, 48f)
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(t.text, p.x, p.y, paint)
                    }
                }

                measureA?.let { a ->
                    val pa = worldToScreen(a.first, a.second)
                    drawCircle(Color(0xFFFFEB3B), 7f, pa)
                    measureB?.let { b ->
                        val pb = worldToScreen(b.first, b.second)
                        drawCircle(Color(0xFFFFEB3B), 7f, pb)
                        drawLine(Color(0xFFFFEB3B), pa, pb, 3f)
                    }
                }
                pickedPoint?.let { p ->
                    val s = worldToScreen(p.easting, p.northing)
                    drawCircle(Color(0xFFFFC107), 14f, s, style = Stroke(width = 3f))
                    drawLine(Color(0xFFFFC107), Offset(s.x - 10f, s.y), Offset(s.x + 10f, s.y), 2f)
                    drawLine(Color(0xFFFFC107), Offset(s.x, s.y - 10f), Offset(s.x, s.y + 10f), 2f)
                }
                myLoc?.let { p ->
                    val s = worldToScreen(p.first, p.second)
                    drawCircle(Color(0xFF2196F3), 12f, s)
                    drawCircle(Color.White, 5f, s)
                }
            }
        }

        if (message.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 4.dp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 12.dp, end = 12.dp)
            ) {
                Text(message, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        distanceMsg?.let {
            Surface(shape = RoundedCornerShape(16.dp), color = color, modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp)) {
                Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }

        NavigationBar(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            NavigationBarItem(selected = false, onClick = { openFile.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Filled.FolderOpen, null) }, label = { Text("فایل") })
            NavigationBarItem(selected = showDrawings, onClick = { showDrawings = true },
                icon = { Icon(Icons.Filled.Map, null) }, label = { Text("نقشه‌ها") })
            NavigationBarItem(selected = showLayers, onClick = { showLayers = true },
                icon = { Icon(Icons.Filled.Layers, null) }, label = { Text("لایه‌ها") }, enabled = drawings.isNotEmpty())
            NavigationBarItem(selected = baseMap != BaseMap.NONE, onClick = { showBaseMapDialog = true },
                icon = { Icon(Icons.Filled.Map, null) }, label = { Text("پس‌زمینه") })
            NavigationBarItem(selected = pickCoordinateMode, onClick = {
                pickCoordinateMode = !pickCoordinateMode
                if (pickCoordinateMode) message = "روی نقشه روی نقطه موردنظر بزن"
            }, icon = { Icon(Icons.Filled.LocationOn, null) }, label = { Text("انتخاب نقطه") })
            NavigationBarItem(selected = false, onClick = { fitAll(canvasSize.x, canvasSize.y) },
                icon = { Icon(Icons.Filled.ZoomOutMap, null) }, label = { Text("Fit") })
            NavigationBarItem(selected = measureMode, onClick = {
                measureMode = !measureMode
                measureA = null; measureB = null
                distanceMsg = if (measureMode) "حالت اندازه‌گیری: نقطه اول را لمس کن" else null
            }, icon = { Icon(Icons.Filled.Straighten, null) }, label = { Text("اندازه") })
        }

        FloatingActionButton(
            onClick = {
                if (hasPermission) readGps() else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            containerColor = color,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 92.dp)
        ) { Icon(Icons.Filled.MyLocation, "موقعیت من", tint = Color.White) }

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.ArrowBack, "بازگشت", tint = Color.White)
        }
    }

    if (showBaseMapDialog) {
        AlertDialog(
            onDismissRequest = { showBaseMapDialog = false },
            title = { Text("پس‌زمینه نقشه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نوع پس‌زمینه را انتخاب کن. برای تصاویر ماهواره‌ای/خیابانی/توپوگرافی از سرویس ArcGIS استفاده می‌شود.")
                    BaseMap.values().filter { it != BaseMap.NONE }.forEach { item ->
                        OutlinedButton(onClick = { baseMap = item; showBaseMapDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.title)
                        }
                    }
                    OutlinedButton(onClick = { baseMap = BaseMap.NONE; showBaseMapDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("بدون پس‌زمینه")
                    }
                    OutlinedTextField(
                        value = zoneText,
                        onValueChange = { zoneText = it.filter(Char::isDigit).take(2) },
                        label = { Text("UTM Zone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showBaseMapDialog = false }) { Text("بستن") } }
        )
    }

    pickedPoint?.let { p ->
        val coordTxt = formatEn("X=%.3f  Y=%.3f", p.easting, p.northing)
        AlertDialog(
            onDismissRequest = { pickedPoint = null },
            title = { Text("مختصات نقطه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(coordTxt)
                    Text(formatEn("Lat=%.6f  Lon=%.6f", p.lat, p.lon), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(coordTxt)); message = "مختصات کپی شد" }, modifier = Modifier.fillMaxWidth()) {
                        Text("کپی مختصات")
                    }
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UtmGeo.neshanIntentUri(p.lat, p.lon))))
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(formatEn("https://nshn.ir/?lat=%.6f&lng=%.6f", p.lat, p.lon))))
                            } catch (_: Exception) {}
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("نمایش در نشان")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickedPoint = null }) { Text("بستن") } }
        )
    }

    if (showDrawings) {
        AlertDialog(
            onDismissRequest = { showDrawings = false },
            title = { Text("نقشه‌های باز (${drawings.size})") },
            text = {
                if (drawings.isEmpty()) Text("هنوز نقشه‌ای باز نشده است.")
                else LazyColumn {
                    itemsIndexed(drawings, key = { _, d -> d.id }) { index, drawing ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Checkbox(checked = drawing.visible, onCheckedChange = { v ->
                                drawings = drawings.mapIndexed { i, d -> if (i == index) d.copy(visible = v) else d }
                            })
                            Column(Modifier.weight(1f)) {
                                Text(drawing.name, color = TextPrimary)
                                Text("${drawing.model.lines.size} خط | ${drawing.model.circles.size} نقطه", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            IconButton(onClick = {
                                drawings = drawings.filterIndexed { i, _ -> i != index }
                                if (drawings.isEmpty()) { tiles = emptyList(); message = "نقشه‌ها بسته شدند" }
                            }) { Icon(Icons.Filled.Close, "بستن") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDrawings = false }) { Text("بستن") } }
        )
    }

    if (showLayers) {
        AlertDialog(
            onDismissRequest = { showLayers = false },
            title = { Text("مدیریت لایه‌ها") },
            text = {
                if (drawings.isEmpty()) Text("نقشه‌ای باز نشده است.")
                else LazyColumn {
                    drawings.filter { it.visible }.forEach { drawing ->
                        item {
                            Text("📁 ${drawing.name}", style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                        val layers = drawing.model.layers.values.sortedBy { it.name }
                        itemsIndexed(layers, key = { _, layer -> "${drawing.id}:${layer.name}" }) { _, layer ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Checkbox(checked = layer.visible, onCheckedChange = { v ->
                                    layer.visible = v
                                    drawings = drawings.toList()
                                })
                                Text(layer.name.ifBlank { "(بدون نام)" }, Modifier.weight(1f))
                                LayerColorButton(layer.displayColor ?: DxfParser.aciToColor(layer.colorAci)) { newColor ->
                                    layer.displayColor = newColor
                                    drawings = drawings.toList()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLayers = false }) { Text("بستن") } }
        )
    }
}

@Composable
private fun LayerColorButton(current: Color, onColor: (Color) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Surface(color = current, shape = RoundedCornerShape(50), modifier = Modifier.size(22.dp)) {}
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val palette = listOf(Color.Black, Color.Red, Color(0xFFFFC107), Color(0xFF43A047), Color.Cyan, Color.Blue, Color.Magenta, Color.White, Color.Gray)
            palette.forEach { c ->
                DropdownMenuItem(
                    text = { Text("●", color = c, style = MaterialTheme.typography.titleLarge) },
                    onClick = { onColor(c); open = false }
                )
            }
        }
    }
}

private enum class BaseMap(val title: String, val service: String) {
    SATELLITE("ماهواره‌ای", "World_Imagery"),
    STREET("خیابانی / ترافیکی", "World_Street_Map"),
    TOPO("توپوگرافی", "World_Topo_Map"),
    NONE("بدون پس‌زمینه", "")
}

private data class PickedPoint(
    val easting: Double,
    val northing: Double,
    val lat: Double,
    val lon: Double
)

private data class TileBmp(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val latNorth: Double, val latSouth: Double,
    val lonWest: Double, val lonEast: Double
)

private object SatelliteTileCache {
    private const val MAX = 120
    private val cache = object : LinkedHashMap<String, TileBmp>(MAX, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TileBmp>?): Boolean = size > MAX
    }
    fun get(key: String) = synchronized(cache) { cache[key] }
    fun put(key: String, value: TileBmp) = synchronized(cache) { cache[key] = value }
}

private fun estimateZoom(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, screenW: Float): Int {
    val lonDiff = (maxLon - minLon).absoluteValue.coerceAtLeast(1e-7)
    val raw = ln((360.0 * screenW / 256.0) / lonDiff) / ln(2.0)
    return raw.roundToInt().coerceIn(10, 19)
}

private fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
    val n = 1 shl zoom
    val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    return x to y
}

private fun tileToLatLon(x: Int, y: Int, zoom: Int): Pair<Double, Double> {
    val n = 1 shl zoom
    val lon = x.toDouble() / n * 360.0 - 180.0
    val lat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / n))))
    return lat to lon
}

private suspend fun fetchTile(url: String, fallbackUrl: String, x: Int, y: Int, zoom: Int, key: String): TileBmp? = withContext(Dispatchers.IO) {
    SatelliteTileCache.get(key)?.let { return@withContext it }
    val urls = listOf(url, fallbackUrl)
    for (tileUrl in urls) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(tileUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 10000
                instanceFollowRedirects = true
                useCaches = true
                setRequestProperty("User-Agent", "AdelAssistant/1.0 (Android)")
                setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            }
            if (conn.responseCode !in 200..299) continue
            val tile = conn.inputStream.use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return@use null
                val (latN, lonW) = tileToLatLon(x, y, zoom)
                val (latS, lonE) = tileToLatLon(x + 1, y + 1, zoom)
                TileBmp(bmp.asImageBitmap(), latN, latS, lonW, lonE)
            } ?: continue
            SatelliteTileCache.put(key, tile)
            return@withContext tile
        } catch (_: Exception) {
            // Try the second ArcGIS endpoint.
        } finally {
            conn?.disconnect()
        }
    }
    null
}

private suspend fun loadEsriTilesCached(
    minLat: Double, maxLat: Double, minLon: Double, maxLon: Double,
    zoom: Int, baseMap: BaseMap
): List<TileBmp> = coroutineScope {
    if (baseMap == BaseMap.NONE) return@coroutineScope emptyList()
    val (aX, aY) = latLonToTile(minLat, minLon, zoom)
    val (bX, bY) = latLonToTile(maxLat, maxLon, zoom)
    val minX = min(aX, bX); val maxX = max(aX, bX)
    val minY = min(aY, bY); val maxY = max(aY, bY)
    val coords = buildList {
        outer@ for (x in minX..maxX) for (y in minY..maxY) {
            if (size >= 36) break@outer
            add(x to y)
        }
    }
    val jobs = coords.map { (x, y) ->
        async(Dispatchers.IO) {
            val key = "${baseMap.service}/$zoom/$x/$y"
            val url = "https://services.arcgisonline.com/ArcGIS/rest/services/${baseMap.service}/MapServer/tile/$zoom/$y/$x"
            val fallback = "https://server.arcgisonline.com/ArcGIS/rest/services/${baseMap.service}/MapServer/tile/$zoom/$y/$x"
            fetchTile(url, fallback, x, y, zoom, key)
        }
    }
    jobs.awaitAll().filterNotNull()
}
