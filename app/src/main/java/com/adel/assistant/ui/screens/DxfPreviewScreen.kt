package com.adel.assistant.ui.screens

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.ui.text.font.FontWeight
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
    var baseMap by remember { mutableStateOf(BaseMapType.NONE) }
    var showSatelliteDialog by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showDrawings by remember { mutableStateOf(false) }
    var measureMode by remember { mutableStateOf(false) }
    var coordinateMode by remember { mutableStateOf(false) }
    var selectedCoordinate by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showBaseMapMenu by remember { mutableStateOf(false) }
    var measureA by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var measureB by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var distanceMsg by remember { mutableStateOf<String?>(null) }
    var myLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var tiles by remember { mutableStateOf<List<TileBmp>>(emptyList()) }
    var fitTrigger by remember { mutableStateOf(0) }

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
    fun readGps() {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (provider in lm.getProviders(true)) {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            if (best == null) message = "موقعیت GPS دریافت نشد"
            else {
                val (e, n) = UtmGeo.fromLatLon(best.latitude, best.longitude, zone)
                myLoc = e to n
                message = "موقعیت فعلی روی نقشه"
            }
        } catch (_: SecurityException) { message = "دسترسی موقعیت داده نشده" }
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

    LaunchedEffect(baseMap, scale, offset, canvasSize, zone, drawings) {
        if (baseMap == BaseMapType.NONE || allModels.isEmpty() || canvasSize.x <= 0f) {
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
        tiles = withContext(Dispatchers.IO) { loadBaseTilesCached(baseMap, minLat, maxLat, minLon, maxLon, z) }
    }

    LaunchedEffect(fitTrigger, canvasSize) {
        if (allModels.isNotEmpty() && canvasSize.x > 0f) fitAll(canvasSize.x, canvasSize.y)
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (baseMap == BaseMapType.SATELLITE) Color(0xFF111111) else Color(0xFF202124)
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
                    .pointerInput(measureMode, coordinateMode, scale, offset) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                val factor = 1.7f
                                val ns = (scale * factor).coerceAtMost(5000f)
                                offset = Offset(tap.x - (tap.x - offset.x) * ns / scale, tap.y - (tap.y - offset.y) * ns / scale)
                                scale = ns
                            },
                            onTap = { tap ->
                                val p = screenToWorld(tap.x, tap.y)
                                if (coordinateMode) {
                                    selectedCoordinate = p
                                    message = "مختصات نقطه انتخاب شد"
                                    return@detectTapGestures
                                }
                                if (!measureMode) return@detectTapGestures
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

                if (baseMap != BaseMapType.NONE) {
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

        selectedCoordinate?.let { p ->
            val coordText = "X = ${"%.3f".format(java.util.Locale.US, p.first)}\nY = ${"%.3f".format(java.util.Locale.US, p.second)}"
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = if (distanceMsg != null) 92.dp else 50.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                    Text(coordText, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("مختصات", coordText.replace("\n", " ")))
                        message = "مختصات کپی شد"
                    }) { Icon(Icons.Filled.ContentCopy, "کپی مختصات") }
                }
            }
        }

        NavigationBar(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            NavigationBarItem(selected = false, onClick = { openFile.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Filled.FolderOpen, null) }, label = { Text("فایل") })
            NavigationBarItem(selected = showDrawings, onClick = { showDrawings = true },
                icon = { Icon(Icons.Filled.Map, null) }, label = { Text("نقشه‌ها") })
            NavigationBarItem(selected = showLayers, onClick = { showLayers = true },
                icon = { Icon(Icons.Filled.Layers, null) }, label = { Text("لایه‌ها") }, enabled = drawings.isNotEmpty())
            NavigationBarItem(selected = baseMap != BaseMapType.NONE, onClick = { showBaseMapMenu = true },
                icon = { Icon(Icons.Filled.Layers, null) }, label = { Text("پس‌زمینه") })
            NavigationBarItem(selected = coordinateMode, onClick = {
                coordinateMode = !coordinateMode
                if (coordinateMode) { measureMode = false; message = "حالت مختصات: روی یک نقطه از نقشه لمس کن" }
                else message = "حالت مختصات خاموش شد"
            }, icon = { Icon(Icons.Filled.LocationOn, null) }, label = { Text("مختصات") })
            NavigationBarItem(selected = false, onClick = { fitAll(canvasSize.x, canvasSize.y) },
                icon = { Icon(Icons.Filled.ZoomOutMap, null) }, label = { Text("Fit") })
            NavigationBarItem(selected = measureMode, onClick = {
                measureMode = !measureMode
                measureA = null; measureB = null
                distanceMsg = if (measureMode) "حالت اندازه‌گیری: نقطه اول را لمس کن" else null
            }, icon = { Icon(Icons.Filled.Straighten, null) }, label = { Text("اندازه") })
        }

        if (showBaseMapMenu) {
            AlertDialog(
                onDismissRequest = { showBaseMapMenu = false },
                title = { Text("نوع نقشه") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BaseMapChoice("بدون پس‌زمینه", baseMap == BaseMapType.NONE) { baseMap = BaseMapType.NONE; showBaseMapMenu = false; tiles = emptyList() }
                        BaseMapChoice("تصاویر ماهواره‌ای", baseMap == BaseMapType.SATELLITE) { showSatelliteDialog = true; showBaseMapMenu = false }
                        BaseMapChoice("نقشه خیابان‌ها", baseMap == BaseMapType.STREET) { baseMap = BaseMapType.STREET; showBaseMapMenu = false }
                        BaseMapChoice("نقشه توپوگرافی", baseMap == BaseMapType.TOPO) { baseMap = BaseMapType.TOPO; showBaseMapMenu = false }
                    }
                },
                confirmButton = { TextButton(onClick = { showBaseMapMenu = false }) { Text("بستن") } }
            )
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

    if (showSatelliteDialog) {
        AlertDialog(
            onDismissRequest = { showSatelliteDialog = false },
            title = { Text("پس‌زمینه ماهواره‌ای") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("زون UTM نقشه را وارد کن. موقعیت و بزرگنمایی فعلی حفظ می‌شود.")
                    OutlinedTextField(
                        value = zoneText,
                        onValueChange = { zoneText = it.filter(Char::isDigit).take(2) },
                        label = { Text("UTM Zone") },
                        singleLine = true
                    )
                }
            },
            confirmButton = { TextButton(onClick = { baseMap = BaseMapType.SATELLITE; showSatelliteDialog = false }) { Text("نمایش") } },
            dismissButton = { TextButton(onClick = { showSatelliteDialog = false }) { Text("لغو") } }
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

private enum class BaseMapType { NONE, SATELLITE, STREET, TOPO }

@Composable
private fun BaseMapChoice(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, modifier = Modifier.weight(1f))
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

private data class TileBmp(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val latNorth: Double, val latSouth: Double,
    val lonWest: Double, val lonEast: Double
)

private object SatelliteTileCache {
    private const val MAX = 96
    private val cache = object : LinkedHashMap<String, TileBmp>(MAX, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TileBmp>?): Boolean = size > MAX
    }
    fun get(key: String) = synchronized(cache) { cache[key] }
    fun put(key: String, value: TileBmp) = synchronized(cache) { cache[key] = value }
}

private fun estimateZoom(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, screenW: Float): Int {
    val lonDiff = (maxLon - minLon).absoluteValue.coerceAtLeast(1e-7)
    val raw = ln((360.0 * screenW / 256.0) / lonDiff) / ln(2.0)
    return raw.roundToInt().coerceIn(14, 19)
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

private suspend fun loadBaseTilesCached(
    type: BaseMapType,
    minLat: Double,
    maxLat: Double,
    minLon: Double,
    maxLon: Double,
    zoom: Int
): List<TileBmp> = coroutineScope {
    val (aX, aY) = latLonToTile(minLat, minLon, zoom)
    val (bX, bY) = latLonToTile(maxLat, maxLon, zoom)
    val minX = min(aX, bX)
    val maxX = max(aX, bX)
    val minY = min(aY, bY)
    val maxY = max(aY, bY)

    val keys = mutableListOf<Triple<Int, Int, Int>>()
    outer@ for (x in minX..maxX) {
        for (y in minY..maxY) {
            keys += Triple(x, y, zoom)
            if (keys.size >= 30) break@outer
        }
    }

    val deferred = keys.map { (x, y, z) ->
        async(Dispatchers.IO) {
            val key = "${type.name}/$z/$x/$y"
            SatelliteTileCache.get(key)?.let { return@async it }

            val service = when (type) {
                BaseMapType.SATELLITE -> "World_Imagery"
                BaseMapType.STREET -> "World_Street_Map"
                BaseMapType.TOPO -> "World_Topo_Map"
                BaseMapType.NONE -> return@async null
            }

            try {
                val url = "https://server.arcgisonline.com/ArcGIS/rest/services/$service/MapServer/tile/$z/$y/$x"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 2200
                conn.readTimeout = 3500
                try {
                    conn.inputStream.use { input ->
                        val bmp = BitmapFactory.decodeStream(input) ?: return@async null
                        val (latN, lonW) = tileToLatLon(x, y, z)
                        val (latS, lonE) = tileToLatLon(x + 1, y + 1, z)
                        val tile = TileBmp(bmp.asImageBitmap(), latN, latS, lonW, lonE)
                        SatelliteTileCache.put(key, tile)
                        tile
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    deferred.awaitAll().filterNotNull()
}
