package com.adel.assistant.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adel.assistant.data.*
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.*

private data class ViewerDrawing(val id: Int, val name: String, val model: DxfModel, var visible: Boolean = true)
private data class PickedMapPoint(val id: Int, val x: Double, val y: Double, val z: Double = 0.0, val snapped: Boolean = false, val snapLabel: String = "")
private data class SnapResult(val x: Double, val y: Double, val label: String)
private data class TileBmp(val image: ImageBitmap, val latNorth: Double, val latSouth: Double, val lonWest: Double, val lonEast: Double)
private enum class BaseMap(val title: String, val url: String) {
    NONE("بدون پس‌زمینه", ""),
    SATELLITE("ماهواره‌ای", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%d/%d/%d"),
    STREET("خیابانی", "https://tile.openstreetmap.org/%d/%d/%d.png"),
    TOPO("توپوگرافی", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/%d/%d/%d")
}
private enum class MeasureMode { NONE, DISTANCE, AREA }

@Composable
fun DxfPreviewScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var drawings by remember { mutableStateOf<List<ViewerDrawing>>(emptyList()) }
    var nextDrawingId by remember { mutableStateOf(1) }
    var pickedPoints by remember { mutableStateOf<List<PickedMapPoint>>(emptyList()) }
    var nextPointId by remember { mutableStateOf(1) }
    var selectedPointIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var message by remember { mutableStateOf("یک یا چند فایل DXF/KML/KMZ باز کن") }
    var zoneText by remember { mutableStateOf("40") }
    var baseMap by remember { mutableStateOf(BaseMap.NONE) }
    var showBaseMapDialog by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showDrawings by remember { mutableStateOf(false) }
    var showPoints by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showCoordinateSearch by remember { mutableStateOf(false) }
    var showChainageSearch by remember { mutableStateOf(false) }
    var showMeasureDialog by remember { mutableStateOf(false) }
    var measureMode by remember { mutableStateOf(MeasureMode.NONE) }
    var measureA by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var measureB by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var areaPoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var distanceMsg by remember { mutableStateOf<String?>(null) }
    var myLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var tiles by remember { mutableStateOf<List<TileBmp>>(emptyList()) }
    var fitTrigger by remember { mutableStateOf(0) }
    var pickMode by remember { mutableStateOf(false) }
    var editPointId by remember { mutableStateOf<Int?>(null) }
    var snapEnabled by remember { mutableStateOf(true) }
    var offlineOnly by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var coordinateText by remember { mutableStateOf("") }
    var coordinateType by remember { mutableStateOf("UTM") }
    var chainageText by remember { mutableStateOf("") }
    var offlineDownloadTrigger by remember { mutableStateOf(0) }

    val zone = zoneText.toIntOrNull()?.coerceIn(1, 60) ?: 40
    val activeDrawings = drawings.filter { it.visible }
    val allModels = activeDrawings.map { it.model }

    fun worldToScreen(x: Double, y: Double) = Offset((x * scale + offset.x).toFloat(), (-y * scale + offset.y).toFloat())
    fun screenToWorld(sx: Float, sy: Float): Pair<Double, Double> = ((sx - offset.x) / scale).toDouble() to (-((sy - offset.y) / scale)).toDouble()
    fun centerWorld(x: Double, y: Double) {
        if (canvasSize.x <= 0f || canvasSize.y <= 0f) return
        offset = Offset(canvasSize.x / 2f - (x * scale).toFloat(), canvasSize.y / 2f + (y * scale).toFloat())
    }

    fun fitAll(w: Float = canvasSize.x, h: Float = canvasSize.y) {
        if (w <= 0f || h <= 0f || allModels.isEmpty()) return
        var minX = Double.POSITIVE_INFINITY; var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        allModels.forEach { m -> minX = min(minX, m.minX); minY = min(minY, m.minY); maxX = max(maxX, m.maxX); maxY = max(maxY, m.maxY) }
        val sx = w * .90f / (maxX - minX).coerceAtLeast(1.0).toFloat()
        val sy = h * .90f / (maxY - minY).coerceAtLeast(1.0).toFloat()
        scale = min(sx, sy).coerceIn(0.000001f, 5000f)
        offset = Offset(w / 2f - ((minX + maxX) / 2.0 * scale).toFloat(), h / 2f + ((minY + maxY) / 2.0 * scale).toFloat())
    }

    fun nearestSnap(p: Pair<Double, Double>): SnapResult? {
        if (!snapEnabled || allModels.isEmpty()) return null
        val thresholdWorld = (36f / scale.coerceAtLeast(0.000001f)).toDouble()
        var best: SnapResult? = null; var bestD = thresholdWorld
        fun consider(x: Double, y: Double, label: String) {
            val d = hypot(x - p.first, y - p.second)
            if (d <= bestD) { bestD = d; best = SnapResult(x, y, label) }
        }
        activeDrawings.forEach { d ->
            d.model.lines.forEach { ln ->
                consider(ln.x1, ln.y1, "انتهای خط")
                consider(ln.x2, ln.y2, "انتهای خط")
                val vx = ln.x2 - ln.x1; val vy = ln.y2 - ln.y1; val len2 = vx * vx + vy * vy
                if (len2 > 1e-12) {
                    val t = ((p.first - ln.x1) * vx + (p.second - ln.y1) * vy) / len2
                    if (t in 0.0..1.0) consider(ln.x1 + t * vx, ln.y1 + t * vy, "روی خط")
                    consider((ln.x1 + ln.x2) / 2.0, (ln.y1 + ln.y2) / 2.0, "وسط خط")
                }
            }
            d.model.circles.forEach { c -> consider(c.x, c.y, "مرکز دایره") }
        }
        return best
    }

    fun selectPointAt(sx: Float, sy: Float) {
        val raw = screenToWorld(sx, sy)
        val snap = nearestSnap(raw)
        val p = snap?.let { it.x to it.y } ?: raw
        val editing = editPointId
        if (editing != null) {
            pickedPoints = pickedPoints.map { if (it.id == editing) it.copy(x = p.first, y = p.second, snapped = snap != null, snapLabel = snap?.label ?: "") else it }
            editPointId = null
            pickMode = false
            message = "نقطه ویرایش شد"
        } else {
            val np = PickedMapPoint(nextPointId++, p.first, p.second, 0.0, snap != null, snap?.label ?: "")
            pickedPoints = pickedPoints + np
            selectedPointIds = selectedPointIds + np.id
            pickMode = false
            message = if (snap != null) "نقطه ${np.id} با Snap روی ${snap.label} ثبت شد" else "نقطه ${np.id} ثبت شد"
        }
    }

    fun exportPicked(ext: String) {
        val selected = pickedPoints.filter { it.id in selectedPointIds }.ifEmpty { pickedPoints }
        if (selected.isEmpty()) { message = "نقطه‌ای برای خروجی وجود ندارد"; return }
        val survey = selected.map { SurveyPoint(it.id.toString(), it.x, it.y, it.z, it.snapLabel) }
        try {
            val text = if (ext == "kml") UtmGeo.toKml(survey, "selected_points", zone) else PointConverter.write(survey, ext)
            val mime = if (ext == "dxf") "application/dxf" else if (ext == "kml") "application/vnd.google-earth.kml+xml" else "text/plain"
            FileExport.exportTextToDocuments(context, "selected_points.$ext", text, mime)
            message = "خروجی $ext ساخته شد"
        } catch (e: Exception) { message = "خطا در خروجی: ${e.message ?: "نامشخص"}" }
    }

    fun openNeshan() {
        val p = pickedPoints.firstOrNull { it.id in selectedPointIds } ?: pickedPoints.lastOrNull()
        if (p == null) { message = "ابتدا یک نقطه انتخاب کن"; return }
        val (lat, lon) = UtmGeo.toLatLon(p.x, p.y, zone)
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UtmGeo.neshanIntentUri(lat, lon)))) }
        catch (_: Exception) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://nshn.ir/?lat=${"%.6f".format(Locale.US, lat)}&lng=${"%.6f".format(Locale.US, lon)}"))) }
    }

    fun readGps() {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (provider in lm.getProviders(true)) {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            if (best == null) message = "موقعیت GPS دریافت نشد" else {
                val eN = UtmGeo.fromLatLon(best.latitude, best.longitude, zone)
                myLoc = eN.first to eN.second
                centerWorld(eN.first, eN.second)
                message = "نقشه روی موقعیت من مرکز شد"
            }
        } catch (_: SecurityException) { message = "دسترسی موقعیت داده نشده" }
    }

    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted; if (granted) readGps() }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        try {
            val added = mutableListOf<ViewerDrawing>()
            uris.forEach { uri ->
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@forEach
                val rawName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "drawing.dxf" } ?: "drawing.dxf"
                val lower = rawName.lowercase(); val text = bytes.toString(Charsets.UTF_8)
                val isKml = lower.endsWith(".kml") || lower.endsWith(".kmz") || (text.trimStart().startsWith("<?xml") && text.contains("<kml", true))
                val model = if (isKml) KmlParser.toDxfModel(KmlParser.parseBytes(bytes, rawName, zone)) else DxfParser.parse(text)
                if (!model.isEmpty) added += ViewerDrawing(nextDrawingId + added.size, rawName, model)
            }
            if (added.isEmpty()) message = "موجودیتی قابل نمایش پیدا نشد" else { drawings = drawings + added; nextDrawingId += added.size; fitTrigger++; message = "${drawings.size + added.size} نقشه باز است" }
        } catch (e: Exception) { message = "خطا در خواندن فایل: ${e.message ?: "نامشخص"}" }
    }

    LaunchedEffect(baseMap, scale, offset, canvasSize, zone, drawings, offlineOnly, offlineDownloadTrigger) {
        if (baseMap == BaseMap.NONE || canvasSize.x <= 0f) { tiles = emptyList(); return@LaunchedEffect }
        val corners = listOf(screenToWorld(0f, 0f), screenToWorld(canvasSize.x, 0f), screenToWorld(0f, canvasSize.y), screenToWorld(canvasSize.x, canvasSize.y))
        val ll = corners.map { UtmGeo.toLatLon(it.first, it.second, zone) }
        val minLat = ll.minOf { it.first }; val maxLat = ll.maxOf { it.first }; val minLon = ll.minOf { it.second }; val maxLon = ll.maxOf { it.second }
        val z = estimateZoom(minLat, maxLat, minLon, maxLon, canvasSize.x)
        tiles = withContext(Dispatchers.IO) { loadMapTilesCached(context, baseMap, minLat, maxLat, minLon, maxLon, z, offlineOnly) }
    }
    LaunchedEffect(fitTrigger, canvasSize) { if (allModels.isNotEmpty() && canvasSize.x > 0f) fitAll() }

    Box(Modifier.fillMaxSize().background(Background)) {
        Canvas(
            modifier = Modifier.fillMaxSize().padding(bottom = 76.dp)
                .pointerInput(Unit) { detectTransformGestures { centroid, pan, zoom, _ ->
                    val old = scale; val ns = (scale * zoom).coerceIn(0.000001f, 5000f); val factor = ns / old
                    offset = Offset(centroid.x - (centroid.x - offset.x) * factor + pan.x, centroid.y - (centroid.y - offset.y) * factor + pan.y); scale = ns
                } }
                .pointerInput(pickMode, measureMode, scale, offset) { detectTapGestures(
                    onDoubleTap = { tap -> val ns = (scale * 1.7f).coerceAtMost(5000f); offset = Offset(tap.x - (tap.x - offset.x) * ns / scale, tap.y - (tap.y - offset.y) * ns / scale); scale = ns },
                    onTap = { tap ->
                        when {
                            pickMode -> selectPointAt(tap.x, tap.y)
                            measureMode == MeasureMode.DISTANCE -> {
                                val p = screenToWorld(tap.x, tap.y)
                                if (measureA == null || measureB != null) { measureA = p; measureB = null; distanceMsg = "نقطه دوم را لمس کن" }
                                else { measureB = p; val a = measureA!!; val de = p.first - a.first; val dn = p.second - a.second; val d = hypot(de, dn); val az = (Math.toDegrees(atan2(de, dn)) + 360.0) % 360.0; distanceMsg = "فاصله ${f3(d)} m | ΔE=${f3(de)} | ΔN=${f3(dn)} | آزیموت=${f2(az)}°"; measureMode = MeasureMode.NONE }
                            }
                            measureMode == MeasureMode.AREA -> { areaPoints = areaPoints + screenToWorld(tap.x, tap.y); distanceMsg = "${areaPoints.size + 1} رأس ثبت شد؛ برای پایان «پایان مساحت» را بزن" }
                        }
                    }
                ) }
        ) {
            canvasSize = Offset(size.width, size.height)
            tiles.forEach { t ->
                val (e0, n0) = UtmGeo.fromLatLon(t.latNorth, t.lonWest, zone); val (e1, n1) = UtmGeo.fromLatLon(t.latSouth, t.lonEast, zone)
                val tl = worldToScreen(e0, n0); val br = worldToScreen(e1, n1); val w = (br.x - tl.x).roundToInt(); val h = (br.y - tl.y).roundToInt()
                if (w > 1 && h > 1) drawImage(t.image, dstOffset = androidx.compose.ui.unit.IntOffset(tl.x.roundToInt(), tl.y.roundToInt()), dstSize = androidx.compose.ui.unit.IntSize(w, h))
            }
            activeDrawings.forEach { drawing ->
                val m = drawing.model
                m.lines.forEach { ln ->
                    val layer = m.layers[ln.layer]; if (layer?.visible == false) return@forEach
                    val c = layer?.displayColor ?: DxfParser.aciToColor(if (ln.color in 1..255) ln.color else layer?.colorAci ?: 7)
                    drawLine(c, worldToScreen(ln.x1, ln.y1), worldToScreen(ln.x2, ln.y2), 2.2f)
                }
                m.circles.forEach { c ->
                    val layer = m.layers[c.layer]; if (layer?.visible == false) return@forEach
                    val col = layer?.displayColor ?: DxfParser.aciToColor(if (c.color in 1..255) c.color else layer?.colorAci ?: 7); val p = worldToScreen(c.x, c.y); val r = (c.r * scale).toFloat().coerceAtLeast(3f)
                    drawCircle(col, r, p, style = Stroke(2f)); drawLine(col, Offset(p.x-r,p.y), Offset(p.x+r,p.y), 1.8f); drawLine(col, Offset(p.x,p.y-r), Offset(p.x,p.y+r), 1.8f)
                }
                m.texts.forEach { t ->
                    val layer = m.layers[t.layer]; if (layer?.visible == false) return@forEach
                    val col = layer?.displayColor ?: DxfParser.aciToColor(if (t.color in 1..255) t.color else layer?.colorAci ?: 7); val p = worldToScreen(t.x, t.y)
                    val paint = android.graphics.Paint().apply { this.color = col.toArgb(); textSize = (t.height * scale).toFloat().coerceIn(12f, 48f); isAntiAlias = true }
                    drawContext.canvas.nativeCanvas.drawText(t.text, p.x, p.y, paint)
                }
            }
            pickedPoints.forEach { p ->
                val s = worldToScreen(p.x, p.y); drawCircle(if (p.id in selectedPointIds) Color(0xFFFFC107) else Color.White, 9f, s); drawCircle(Color.Black, 4f, s)
            }
            areaPoints.forEachIndexed { i, p -> val s = worldToScreen(p.first, p.second); drawCircle(Color(0xFF00E5FF), 6f, s); if (i > 0) drawLine(Color(0xFF00E5FF), worldToScreen(areaPoints[i-1].first, areaPoints[i-1].second), s, 3f) }
            if (areaPoints.size > 2 && measureMode == MeasureMode.AREA) drawLine(Color(0xFF00E5FF), worldToScreen(areaPoints.last().first, areaPoints.last().second), worldToScreen(areaPoints.first().first, areaPoints.first().second), 2f)
            measureA?.let { a -> val pa = worldToScreen(a.first, a.second); drawCircle(Color.Yellow, 7f, pa); measureB?.let { b -> val pb = worldToScreen(b.first, b.second); drawCircle(Color.Yellow, 7f, pb); drawLine(Color.Yellow, pa, pb, 3f) } }
            myLoc?.let { p -> val s = worldToScreen(p.first, p.second); drawCircle(Color(0xFF2196F3), 13f, s); drawCircle(Color.White, 5f, s) }
        }

        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 12.dp, end = 12.dp)) { Text(message, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) }
        distanceMsg?.let { Surface(shape = RoundedCornerShape(16.dp), color = color, modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp)) { Text(it, color = Color.White, modifier = Modifier.padding(10.dp)) } }
        if (pickMode) Surface(shape = RoundedCornerShape(20.dp), color = Color(0xCC111111), modifier = Modifier.align(Alignment.Center)) { Text("روی نقطه/خط لمس کن؛ Snap: ${if (snapEnabled) "روشن" else "خاموش"}", color = Color.White, modifier = Modifier.padding(12.dp)) }

        NavigationBar(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            NavigationBarItem(false, { openFile.launch(arrayOf("*/*")) }, { Icon(Icons.Filled.FolderOpen, null) }, label = { Text("فایل") })
            NavigationBarItem(showDrawings, { showDrawings = true }, { Icon(Icons.Filled.Map, null) }, label = { Text("نقشه‌ها") })
            NavigationBarItem(showLayers, { showLayers = true }, { Icon(Icons.Filled.Layers, null) }, label = { Text("لایه‌ها") }, enabled = drawings.isNotEmpty())
            NavigationBarItem(false, { showBaseMapDialog = true }, { Icon(Icons.Filled.LayersClear, null) }, label = { Text("پس‌زمینه") })
            NavigationBarItem(false, { showTools = true }, { Icon(Icons.Filled.MoreHoriz, null) }, label = { Text("ابزار") })
            NavigationBarItem(pickMode, { pickMode = true; editPointId = null }, { Icon(Icons.Filled.AddLocationAlt, null) }, label = { Text("مختصات") })
        }

        Row(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallFloatingActionButton(onClick = { if (hasPermission) readGps() else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }, containerColor = color) { Icon(Icons.Filled.MyLocation, "موقعیت من", tint = Color.White) }
            SmallFloatingActionButton(onClick = { fitAll() }, containerColor = color) { Icon(Icons.Filled.ZoomOutMap, "Fit", tint = Color.White) }
        }
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) { Icon(Icons.Filled.ArrowBack, "بازگشت", tint = Color.White) }
    }

    if (showBaseMapDialog) AlertDialog(onDismissRequest = { showBaseMapDialog = false }, title = { Text("پس‌زمینه نقشه") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BaseMap.values().forEach { b -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(b == baseMap, { baseMap = b }); Text(b.title) } }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(TextSecondary.copy(alpha = 0.25f))); Text("زون UTM", style = MaterialTheme.typography.labelLarge); OutlinedTextField(zoneText, { zoneText = it.filter(Char::isDigit).take(2) }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(offlineOnly, { offlineOnly = it }); Text("فقط از کش آفلاین") }
            Text("حالت ترافیک به سرویس و کلید API ارائه‌دهنده ترافیک نیاز دارد و عمداً داده جعلی نمایش داده نمی‌شود.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }, confirmButton = { TextButton({ showBaseMapDialog = false }) { Text("بستن") } })

    if (showDrawings) AlertDialog(onDismissRequest = { showDrawings = false }, title = { Text("نقشه‌های باز (${drawings.size})") }, text = {
        LazyColumn { itemsIndexed(drawings, key = { _, d -> d.id }) { i, d -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(d.visible, { v -> drawings = drawings.mapIndexed { j, x -> if (i == j) x.copy(visible = v) else x } }); Column(Modifier.weight(1f)) { Text(d.name); Text("${d.model.lines.size} خط | ${d.model.circles.size} دایره | ${d.model.texts.size} متن", style = MaterialTheme.typography.bodySmall) }; IconButton({ drawings = drawings.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Filled.Close, "بستن") } } } }
    }, confirmButton = { TextButton({ showDrawings = false }) { Text("بستن") } })

    if (showLayers) AlertDialog(onDismissRequest = { showLayers = false }, title = { Text("لایه‌ها") }, text = {
        LazyColumn { drawings.filter { it.visible }.forEach { d -> item { Text(d.name, color = color, modifier = Modifier.padding(top = 6.dp)) }; itemsIndexed(d.model.layers.values.sortedBy { it.name }, key = { _, l -> "${d.id}:${l.name}" }) { _, l -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(l.visible, { l.visible = it; drawings = drawings.toList() }); Text(l.name, Modifier.weight(1f)); LayerColorButton(l.displayColor ?: DxfParser.aciToColor(l.colorAci)) { l.displayColor = it; drawings = drawings.toList() } } } } }
    }, confirmButton = { TextButton({ showLayers = false }) { Text("بستن") } })

    if (showPoints) AlertDialog(onDismissRequest = { showPoints = false }, title = { Text("مختصات (${pickedPoints.size})") }, text = {
        Column { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ pickMode = true; showPoints = false }) { Text("+ نقطه") }; TextButton({ snapEnabled = !snapEnabled }) { Text("Snap ${if (snapEnabled) "روشن" else "خاموش"}") } }
            LazyColumn { itemsIndexed(pickedPoints, key = { _, p -> p.id }) { i, p -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(p.id in selectedPointIds, { selectedPointIds = if (it) selectedPointIds + p.id else selectedPointIds - p.id }); Column(Modifier.weight(1f)) { Text("P${p.id}  E=${f3(p.x)}  N=${f3(p.y)}"); if (p.snapped) Text(p.snapLabel, style = MaterialTheme.typography.bodySmall, color = color) }; IconButton({ val c = "P${p.id}\t${f3(p.x)}\t${f3(p.y)}"; FileExport.exportTextToDocuments(context, "point_${p.id}.txt", c); message = "نقطه کپی/خروجی شد" }) { Icon(Icons.Filled.ContentCopy, null) }; IconButton({ editPointId = p.id; pickMode = true; showPoints = false }) { Icon(Icons.Filled.Edit, null) }; IconButton({ pickedPoints = pickedPoints.filterIndexed { j, _ -> j != i }; selectedPointIds = selectedPointIds - p.id }) { Icon(Icons.Filled.Delete, null) } } } }
        }
    }, confirmButton = { TextButton({ showPoints = false }) { Text("بستن") } })

    if (showTools) AlertDialog(onDismissRequest = { showTools = false }, title = { Text("ابزارهای نقشه") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { TextButton({ showTools = false; showSearch = true }) { Text("جستجوی نقطه/متن") } }
            item { TextButton({ showTools = false; showCoordinateSearch = true }) { Text("رفتن به مختصات UTM / Lat-Lon") } }
            item { TextButton({ showTools = false; showChainageSearch = true }) { Text("رفتن به چینیج") } }
            item { TextButton({ showTools = false; showMeasureDialog = true }) { Text("اندازه‌گیری فاصله / مساحت") } }
            item { TextButton({ showTools = false; showPoints = true }) { Text("ثبت و مدیریت نقاط") } }
            item { TextButton({ openNeshan(); showTools = false }) { Text("نمایش نقطه انتخابی در نشان") } }
            item { TextButton({ exportPicked("dxf"); showTools = false }) { Text("خروجی DXF") } }
            item { TextButton({ exportPicked("kml"); showTools = false }) { Text("خروجی KML") } }
            item { TextButton({ exportPicked("csv"); showTools = false }) { Text("خروجی CSV") } }
            item { TextButton({ exportPicked("txt"); showTools = false }) { Text("خروجی TXT") } }
            item { TextButton({ offlineOnly = false; offlineDownloadTrigger++; message = "ذخیره محدوده فعلی در کش آفلاین شروع شد"; showTools = false }) { Text("دانلود/ذخیره محدوده فعلی برای آفلاین") } }
        }
    }, confirmButton = { TextButton({ showTools = false }) { Text("بستن") } })

    if (showSearch) AlertDialog(onDismissRequest = { showSearch = false }, title = { Text("جستجو") }, text = { OutlinedTextField(searchText, { searchText = it }, label = { Text("شماره نقطه، کد یا متن") }, singleLine = true) }, confirmButton = { TextButton({
        val q = searchText.trim(); val p = pickedPoints.firstOrNull { "P${it.id}".contains(q, true) || it.snapLabel.contains(q, true) }
        val t = activeDrawings.flatMap { it.model.texts }.firstOrNull { it.text.contains(q, true) }
        when { p != null -> { centerWorld(p.x, p.y); message = "روی P${p.id} رفت" }; t != null -> { centerWorld(t.x, t.y); message = "روی متن ${t.text} رفت" }; else -> message = "موردی پیدا نشد" }; showSearch = false
    }) { Text("جستجو") } }, dismissButton = { TextButton({ showSearch = false }) { Text("لغو") } })

    if (showCoordinateSearch) AlertDialog(onDismissRequest = { showCoordinateSearch = false }, title = { Text("رفتن به مختصات") }, text = {
        Column { Row { RadioButton(coordinateType == "UTM", { coordinateType = "UTM" }); Text("UTM", Modifier.padding(top = 12.dp)); Spacer(Modifier.width(12.dp)); RadioButton(coordinateType == "LL", { coordinateType = "LL" }); Text("Lat/Lon", Modifier.padding(top = 12.dp)) }; OutlinedTextField(coordinateText, { coordinateText = it }, label = { Text(if (coordinateType == "UTM") "E,N" else "Lat,Lon") }, singleLine = true) }
    }, confirmButton = { TextButton({
        val a = coordinateText.replace("،", ",").split(",", " ").filter { it.isNotBlank() }.mapNotNull { it.toDoubleOrNull() }
        if (a.size >= 2) { val p = if (coordinateType == "UTM") a[0] to a[1] else UtmGeo.fromLatLon(a[0], a[1], zone); centerWorld(p.first, p.second); message = "روی مختصات مرکز شد" } else message = "مختصات نامعتبر"; showCoordinateSearch = false
    }) { Text("برو") } }, dismissButton = { TextButton({ showCoordinateSearch = false }) { Text("لغو") } })

    if (showChainageSearch) AlertDialog(onDismissRequest = { showChainageSearch = false }, title = { Text("رفتن به چینیج") }, text = { OutlinedTextField(chainageText, { chainageText = it }, label = { Text("مثلاً 280+00 یا 280") }, singleLine = true) }, confirmButton = { TextButton({
        val q = chainageText.trim(); val t = activeDrawings.flatMap { it.model.texts }.firstOrNull { it.text.replace(" ", "").contains(q.replace(" ", ""), true) }
        if (t != null) { centerWorld(t.x, t.y); message = "روی چینیج ${t.text} رفت" } else message = "چینیج موردنظر در متن نقشه پیدا نشد"; showChainageSearch = false
    }) { Text("برو") } }, dismissButton = { TextButton({ showChainageSearch = false }) { Text("لغو") } })

    if (showMeasureDialog) AlertDialog(onDismissRequest = { showMeasureDialog = false }, title = { Text("اندازه‌گیری") }, text = { Column { TextButton({ measureMode = MeasureMode.DISTANCE; measureA = null; measureB = null; areaPoints = emptyList(); distanceMsg = "نقطه اول را لمس کن"; showMeasureDialog = false }) { Text("فاصله، ΔE، ΔN و آزیموت") }; TextButton({ measureMode = MeasureMode.AREA; areaPoints = emptyList(); distanceMsg = "رأس‌های مساحت را لمس کن"; showMeasureDialog = false }) { Text("مساحت پلیگون") } } }, confirmButton = { TextButton({ showMeasureDialog = false }) { Text("بستن") } })

    if (measureMode == MeasureMode.AREA) {
        Surface(shape = RoundedCornerShape(16.dp), color = color, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 84.dp).wrapContentHeight().align(Alignment.TopCenter)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("رأس‌ها: ${areaPoints.size}", color = Color.White); TextButton({
                if (areaPoints.size >= 3) { val area = polygonArea(areaPoints); distanceMsg = "مساحت = ${f3(area)} مترمربع"; measureMode = MeasureMode.NONE; areaPoints = emptyList() } else distanceMsg = "حداقل ۳ رأس لازم است"
            }) { Text("پایان مساحت", color = Color.White) } }
        }
    }
}

@Composable
private fun LayerColorButton(current: Color, onColor: (Color) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { IconButton({ open = true }) { Surface(color = current, shape = RoundedCornerShape(50), modifier = Modifier.size(22.dp)) {} }; DropdownMenu(open, { open = false }) { listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.White, Color.Gray).forEach { c -> DropdownMenuItem({ Text("●", color = c) }, { onColor(c); open = false }) } } }
}

private object SatelliteTileCache {
    private const val MAX = 128
    private val cache = object : LinkedHashMap<String, TileBmp>(MAX, .75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, TileBmp>?): Boolean = size > MAX }
    fun get(k: String) = synchronized(cache) { cache[k] }
    fun put(k: String, v: TileBmp) = synchronized(cache) { cache[k] = v }
}

private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
private fun f3(v: Double) = String.format(Locale.US, "%.3f", v)
private fun polygonArea(points: List<Pair<Double, Double>>): Double { var s = 0.0; for (i in points.indices) { val a = points[i]; val b = points[(i + 1) % points.size]; s += a.first * b.second - b.first * a.second }; return abs(s) / 2.0 }
private fun estimateZoom(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, screenW: Float): Int { val d = (maxLon - minLon).absoluteValue.coerceAtLeast(1e-7); return (ln((360.0 * screenW / 256.0) / d) / ln(2.0)).roundToInt().coerceIn(10, 19) }
private fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> { val n = 1 shl zoom; val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1); val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511)); val y = ((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toInt().coerceIn(0, n - 1); return x to y }
private fun tileToLatLon(x: Int, y: Int, zoom: Int): Pair<Double, Double> { val n = 1 shl zoom; val lon = x.toDouble() / n * 360 - 180; val lat = Math.toDegrees(atan(sinh(PI * (1 - 2.0 * y / n)))); return lat to lon }

private fun loadMapTilesCached(context: Context, map: BaseMap, minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, zoom: Int, offlineOnly: Boolean): List<TileBmp> {
    val (ax, ay) = latLonToTile(minLat, minLon, zoom); val (bx, by) = latLonToTile(maxLat, maxLon, zoom); val minX = min(ax,bx); val maxX = max(ax,bx); val minY = min(ay,by); val maxY = max(ay,by)
    val out = mutableListOf<TileBmp>(); val dir = File(context.cacheDir, "maptiles/${map.name}/$zoom"); if (!dir.exists()) dir.mkdirs(); var count = 0
    outer@ for (x in minX..maxX) for (y in minY..maxY) {
        if (count >= 36) break@outer
        val key = "${map.name}/$zoom/$x/$y"; SatelliteTileCache.get(key)?.let { out += it; count++; continue }
        val file = File(dir, "${x}_$y.png")
        val bmp = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else if (!offlineOnly) try {
            val u = URL(String.format(Locale.US, map.url, zoom, y, x)); val c = u.openConnection() as HttpURLConnection; c.connectTimeout = 4000; c.readTimeout = 6000; c.inputStream.use { BitmapFactory.decodeStream(it) }.also { c.disconnect() }
        } catch (_: Exception) { null } else null
        if (bmp != null) {
            if (!file.exists()) try { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (_: Exception) {}
            val (latN, lonW) = tileToLatLon(x, y, zoom); val (latS, lonE) = tileToLatLon(x + 1, y + 1, zoom); val t = TileBmp(bmp.asImageBitmap(), latN, latS, lonW, lonE); SatelliteTileCache.put(key, t); out += t; count++
        }
    }
    return out
}
