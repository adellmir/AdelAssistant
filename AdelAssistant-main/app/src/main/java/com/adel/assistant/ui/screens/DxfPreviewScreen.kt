package com.adel.assistant.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.ui.platform.LocalDensity
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


private data class PickedMapPoint(
    val id: Int,
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    val snapped: Boolean = false,
    val snapLabel: String = ""
)

private data class SnapCandidate(val x: Double, val y: Double, val label: String)

@Composable
fun DxfPreviewScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var drawings by remember { mutableStateOf<List<ViewerDrawing>>(emptyList()) }
    var nextDrawingId by remember { mutableStateOf(1) }
    var message by remember { mutableStateOf("برای شروع یک یا چند فایل DXF/KML/KMZ انتخاب کن") }
    var zoneText by remember { mutableStateOf("40") }
    var satellite by remember { mutableStateOf(false) }
    var showSatelliteDialog by remember { mutableStateOf(false) }
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
    var pickMode by remember { mutableStateOf(false) }
    var editingPointId by remember { mutableStateOf<Int?>(null) }
    var activePick by remember { mutableStateOf<Offset?>(null) }
    var pickedPoints by remember { mutableStateOf<List<PickedMapPoint>>(emptyList()) }
    var nextPointId by remember { mutableStateOf(1) }
    var showPointDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showKmlZoneDialog by remember { mutableStateOf(false) }
    var selectedPointIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

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

    fun snapThresholdPx(): Float = 44.8f * density.density

    fun pointToSegment(px: Double, py: Double, line: DxfLine): Pair<Double, Double> {
        val dx = line.x2 - line.x1
        val dy = line.y2 - line.y1
        val len2 = dx * dx + dy * dy
        if (len2 <= 1e-12) return line.x1 to line.y1
        val t = (((px - line.x1) * dx + (py - line.y1) * dy) / len2).coerceIn(0.0, 1.0)
        return (line.x1 + t * dx) to (line.y1 + t * dy)
    }

    fun lineIntersection(a: DxfLine, b: DxfLine): Pair<Double, Double>? {
        val rX = a.x2 - a.x1; val rY = a.y2 - a.y1
        val sX = b.x2 - b.x1; val sY = b.y2 - b.y1
        val den = rX * sY - rY * sX
        if (abs(den) < 1e-12) return null
        val qpx = b.x1 - a.x1; val qpy = b.y1 - a.y1
        val t = (qpx * sY - qpy * sX) / den
        val u = (qpx * rY - qpy * rX) / den
        return if (t in 0.0..1.0 && u in 0.0..1.0) (a.x1 + t * rX) to (a.y1 + t * rY) else null
    }

    fun nearestSnap(world: Pair<Double, Double>): SnapCandidate? {
        if (allModels.isEmpty()) return null
        val thresholdWorld = snapThresholdPx() / scale.toDouble()
        val px = world.first; val py = world.second
        var best: SnapCandidate? = null
        var bestD2 = thresholdWorld * thresholdWorld
        fun consider(x: Double, y: Double, label: String) {
            val dx = x - px; val dy = y - py; val d2 = dx * dx + dy * dy
            if (d2 <= bestD2) { bestD2 = d2; best = SnapCandidate(x, y, label) }
        }
        activeDrawings.forEach { drawing ->
            drawing.model.lines.forEach { l ->
                consider(l.x1, l.y1, "ابتدای خط")
                consider(l.x2, l.y2, "انتهای خط")
                consider((l.x1 + l.x2) / 2.0, (l.y1 + l.y2) / 2.0, "وسط خط")
                val q = pointToSegment(px, py, l)
                consider(q.first, q.second, "روی خط")
            }
            drawing.model.circles.forEach { c -> consider(c.x, c.y, "مرکز دایره") }
        }
        val lines = activeDrawings.flatMap { it.model.lines }
        if (lines.size <= 1800) {
            for (i in 0 until lines.size) for (j in i + 1 until lines.size) {
                val a = lines[i]; val b = lines[j]
                val minAx = min(a.x1, a.x2) - thresholdWorld; val maxAx = max(a.x1, a.x2) + thresholdWorld
                val minAy = min(a.y1, a.y2) - thresholdWorld; val maxAy = max(a.y1, a.y2) + thresholdWorld
                val minBx = min(b.x1, b.x2) - thresholdWorld; val maxBx = max(b.x1, b.x2) + thresholdWorld
                val minBy = min(b.y1, b.y2) - thresholdWorld; val maxBy = max(b.y1, b.y2) + thresholdWorld
                if (maxAx < minBx || maxBx < minAx || maxAy < minBy || maxBy < minAy) continue
                lineIntersection(a, b)?.let { consider(it.first, it.second, "تقاطع خطوط") }
            }
        }
        return best
    }

    fun makePoint(world: Pair<Double, Double>): PickedMapPoint {
        val snap = nearestSnap(world)
        val p = snap ?: SnapCandidate(world.first, world.second, "")
        return PickedMapPoint(
            id = editingPointId ?: nextPointId++,
            x = p.x, y = p.y, snapped = snap != null, snapLabel = p.label
        )
    }

    fun commitPicked(world: Pair<Double, Double>) {
        val point = makePoint(world)
        val editing = editingPointId
        if (editing != null) {
            pickedPoints = pickedPoints.map { if (it.id == editing) point.copy(id = editing) else it }
            selectedPointIds = selectedPointIds + editing
            editingPointId = null
        } else {
            pickedPoints = pickedPoints + point
            selectedPointIds = selectedPointIds + point.id
        }
        activePick = worldToScreen(point.x, point.y)
        pickMode = false
        showPointDialog = true
    }

    fun selectedPoints(): List<PickedMapPoint> = pickedPoints.filter { selectedPointIds.contains(it.id) }

    fun pointAsSurvey(p: PickedMapPoint, index: Int): SurveyPoint =
        SurveyPoint(p.id.toString(), p.x, p.y, p.z, "P$index")

    fun exportPoints(extension: String) {
        val points = selectedPoints().ifEmpty { pickedPoints }
        if (points.isEmpty()) { message = "هنوز نقطه‌ای انتخاب نشده است"; return }
        if (extension == "kml") { pendingExport = extension; showKmlZoneDialog = true; return }
        try {
            val survey = points.mapIndexed { i, p -> pointAsSurvey(p, i + 1) }
            val content = if (extension == "kml") UtmGeo.toKml(survey, "selected_points", zone) else PointConverter.write(survey, extension)
            val mime = when (extension) { "dxf" -> "application/dxf" else -> "text/plain" }
            val uri = FileExport.exportTextToDocuments(context, "selected_points.$extension", content, mime)
            message = if (uri != null) "خروجی $extension در Documents/AdelAssistant ذخیره شد" else "ذخیره خروجی ناموفق بود"
            showExportMenu = false
        } catch (e: Exception) { message = "خطا در خروجی: ${e.message ?: "نامشخص"}" }
    }

    fun openSelectedInNeshan() {
        val p = selectedPoints().firstOrNull() ?: pickedPoints.lastOrNull() ?: return
        val (lat, lon) = UtmGeo.toLatLon(p.x, p.y, zone)
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UtmGeo.neshanIntentUri(lat, lon)))) }
        catch (_: Exception) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://nshn.ir/?lat=$lat&lng=$lon"))) }
        showExportMenu = false
    }

    LaunchedEffect(satellite, scale, offset, canvasSize, zone, drawings) {
        if (!satellite || allModels.isEmpty() || canvasSize.x <= 0f) {
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
        tiles = withContext(Dispatchers.IO) { loadEsriTilesCached(minLat, maxLat, minLon, maxLon, z) }
    }

    LaunchedEffect(fitTrigger, canvasSize) {
        if (allModels.isNotEmpty() && canvasSize.x > 0f) fitAll(canvasSize.x, canvasSize.y)
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (satellite) Color(0xFF111111) else Color(0xFF202124)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 76.dp)
                    .pointerInput(pickMode, editingPointId, scale, offset, drawings) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (pickMode) {
                                // In pick/edit mode the marker follows the finger without
                                // replacing the smooth map gesture detector.
                                activePick = centroid
                                if (editingPointId != null && pan != Offset.Zero) {
                                    val id = editingPointId!!
                                    pickedPoints = pickedPoints.map { point ->
                                        if (point.id == id) {
                                            val dx = pan.x.toDouble() / scale.toDouble()
                                            val dy = -pan.y.toDouble() / scale.toDouble()
                                            point.copy(x = point.x + dx, y = point.y + dy, snapped = false, snapLabel = "")
                                        } else point
                                    }
                                }
                            } else {
                                val oldScale = scale
                                val newScale = (scale * zoom).coerceIn(0.000001f, 5000f)
                                if (newScale != oldScale) {
                                    val factor = newScale / oldScale
                                    offset = Offset(
                                        centroid.x - (centroid.x - offset.x) * factor + pan.x,
                                        centroid.y - (centroid.y - offset.y) * factor + pan.y
                                    )
                                    scale = newScale
                                } else {
                                    offset += pan
                                }
                            }
                        }
                    }
                    .pointerInput(pickMode, editingPointId, scale, offset) {
                        if (pickMode && editingPointId == null) {
                            detectTapGestures(
                                onTap = { tap -> commitPicked(screenToWorld(tap.x, tap.y)) }
                            )
                        }
                    }
                    .pointerInput(measureMode, scale, offset, pickMode) {
                        if (!pickMode) {
                            detectTapGestures(
                                onDoubleTap = { tap ->
                                    val factor = 1.7f
                                    val ns = (scale * factor).coerceAtMost(5000f)
                                    offset = Offset(tap.x - (tap.x - offset.x) * ns / scale, tap.y - (tap.y - offset.y) * ns / scale)
                                    scale = ns
                                },
                                onTap = { tap ->
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
                    }
            ) {
                canvasSize = Offset(size.width, size.height)

                if (satellite) {
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


                pickedPoints.forEach { p ->
                    val s = worldToScreen(p.x, p.y)
                    val selected = selectedPointIds.contains(p.id)
                    drawCircle(if (selected) color else Color.White, 9f, s, style = Stroke(width = 2.5f))
                    drawLine(if (selected) color else Color.White, Offset(s.x - 7f, s.y), Offset(s.x + 7f, s.y), 2f)
                    drawLine(if (selected) color else Color.White, Offset(s.x, s.y - 7f), Offset(s.x, s.y + 7f), 2f)
                }
                activePick?.let { s ->
                    drawLine(Color(0xFFFFC107), Offset(s.x - 14f, s.y), Offset(s.x + 14f, s.y), 3f)
                    drawLine(Color(0xFFFFC107), Offset(s.x, s.y - 14f), Offset(s.x, s.y + 14f), 3f)
                    drawCircle(Color(0xFFFFC107), 4f, s)
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

        NavigationBar(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            NavigationBarItem(selected = false, onClick = { openFile.launch(arrayOf("*/*")); pickMode = false },
                icon = { Icon(Icons.Filled.FolderOpen, null) }, label = { Text("فایل") })
            NavigationBarItem(selected = showDrawings, onClick = { showDrawings = true },
                icon = { Icon(Icons.Filled.Map, null) }, label = { Text("نقشه‌ها") })
            NavigationBarItem(selected = showLayers, onClick = { showLayers = true },
                icon = { Icon(Icons.Filled.Layers, null) }, label = { Text("لایه‌ها") }, enabled = drawings.isNotEmpty())
            NavigationBarItem(selected = satellite, onClick = { if (satellite) satellite = false else showSatelliteDialog = true },
                icon = { Icon(Icons.Filled.Satellite, null) }, label = { Text("ماهواره") })
            NavigationBarItem(selected = pickMode, onClick = {
                measureMode = false; distanceMsg = null; pickMode = true; activePick = null
                message = "انگشت را روی نقشه بگذار و نقطه را بکش؛ با برداشتن انگشت ثبت می‌شود"
            }, icon = { Icon(Icons.Filled.AddLocationAlt, null) }, label = { Text("مختصات") })
        }

        FloatingActionButton(
            onClick = {
                if (hasPermission) readGps() else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            containerColor = color,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 92.dp)
        ) { Icon(Icons.Filled.MyLocation, "موقعیت من", tint = Color.White) }

        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(onClick = { showExportMenu = true }) {
                Icon(Icons.Filled.Add, "افزودن/خروجی", tint = Color.White)
            }
            DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                DropdownMenuItem(text = { Text("نمایش در نشان") }, onClick = { openSelectedInNeshan() }, enabled = pickedPoints.isNotEmpty())
                DropdownMenuItem(text = { Text("حذف انتخاب‌شده‌ها") }, onClick = {
                    pickedPoints = pickedPoints.filterNot { selectedPointIds.contains(it.id) }
                    selectedPointIds = emptySet(); showExportMenu = false
                    message = "نقاط انتخاب‌شده حذف شدند"
                }, enabled = selectedPointIds.isNotEmpty())
                HorizontalDivider()
                DropdownMenuItem(text = { Text("خروجی DXF") }, onClick = { exportPoints("dxf") }, enabled = pickedPoints.isNotEmpty())
                DropdownMenuItem(text = { Text("خروجی KML") }, onClick = { exportPoints("kml") }, enabled = pickedPoints.isNotEmpty())
                DropdownMenuItem(text = { Text("خروجی TXT") }, onClick = { exportPoints("txt") }, enabled = pickedPoints.isNotEmpty())
            }
        }

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.ArrowBack, "بازگشت", tint = Color.White)
        }
    }


    if (showPointDialog) {
        AlertDialog(
            onDismissRequest = { showPointDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("مختصات (${pickedPoints.size})", modifier = Modifier.weight(1f))
                    IconButton(onClick = { showPointDialog = false; editingPointId = null; pickMode = true; activePick = null }) {
                        Icon(Icons.Filled.Add, "افزودن نقطه")
                    }
                }
            },
            text = {
                if (pickedPoints.isEmpty()) Text("نقطه‌ای انتخاب نشده")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(pickedPoints, key = { _, p -> p.id }) { index, point ->
                        val checked = selectedPointIds.contains(point.id)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = checked, onCheckedChange = { v ->
                                selectedPointIds = if (v) selectedPointIds + point.id else selectedPointIds - point.id
                            })
                            Column(Modifier.weight(1f)) {
                                Text("${index + 1}. E=${"%.3f".format(java.util.Locale.US, point.x)}")
                                Text("N=${"%.3f".format(java.util.Locale.US, point.y)}", style = MaterialTheme.typography.bodySmall)
                                if (point.snapped) Text(point.snapLabel, style = MaterialTheme.typography.labelSmall, color = color)
                            }
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = "E=${"%.3f".format(java.util.Locale.US, point.x)}\nN=${"%.3f".format(java.util.Locale.US, point.y)}\nZ=${"%.3f".format(java.util.Locale.US, point.z)}"
                                cm.setPrimaryClip(ClipData.newPlainText("مختصات", text))
                                message = "مختصات نقطه ${index + 1} کپی شد"
                            }) { Icon(Icons.Filled.ContentCopy, "کپی") }
                            IconButton(onClick = {
                                editingPointId = point.id; showPointDialog = false; pickMode = true; activePick = worldToScreen(point.x, point.y)
                                message = "ویرایش نقطه ${index + 1}: آن را روی نقشه جابه‌جا کن"
                            }) { Icon(Icons.Filled.Edit, "ویرایش") }
                            IconButton(onClick = {
                                pickedPoints = pickedPoints.filterNot { it.id == point.id }
                                selectedPointIds = selectedPointIds - point.id
                            }) { Icon(Icons.Filled.Delete, "پاک کردن") }
                        }
                        if (index < pickedPoints.lastIndex) HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPointDialog = false; pickMode = true; activePick = null }) { Text("+") }
            },
            dismissButton = { TextButton(onClick = { showPointDialog = false }) { Text("بستن") } }
        )
    }

    if (showKmlZoneDialog) {
        AlertDialog(
            onDismissRequest = { showKmlZoneDialog = false; pendingExport = null },
            title = { Text("زون UTM برای KML") },
            text = { OutlinedTextField(value = zoneText, onValueChange = { zoneText = it.filter(Char::isDigit).take(2) }, label = { Text("UTM Zone") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val ext = pendingExport ?: return@TextButton
                    try {
                        val points = selectedPoints().ifEmpty { pickedPoints }
                        val survey = points.mapIndexed { i, p -> pointAsSurvey(p, i + 1) }
                        val content = UtmGeo.toKml(survey, "selected_points", zone)
                        val uri = FileExport.exportTextToDocuments(context, "selected_points.kml", content, "application/vnd.google-earth.kml+xml")
                        message = if (uri != null) "خروجی KML ذخیره شد" else "ذخیره KML ناموفق بود"
                    } catch (e: Exception) { message = "خطا در خروجی KML: ${e.message ?: "نامشخص"}" }
                    pendingExport = null; showKmlZoneDialog = false; showExportMenu = false
                }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { showKmlZoneDialog = false; pendingExport = null }) { Text("لغو") } }
        )
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
            confirmButton = { TextButton(onClick = { satellite = true; showSatelliteDialog = false }) { Text("نمایش") } },
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

private fun loadEsriTilesCached(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, zoom: Int): List<TileBmp> {
    val (aX, aY) = latLonToTile(minLat, minLon, zoom)
    val (bX, bY) = latLonToTile(maxLat, maxLon, zoom)
    val minX = min(aX, bX); val maxX = max(aX, bX)
    val minY = min(aY, bY); val maxY = max(aY, bY)
    val result = mutableListOf<TileBmp>()
    var count = 0
    for (x in minX..maxX) for (y in minY..maxY) {
        if (count >= 24) break
        val key = "$zoom/$x/$y"
        SatelliteTileCache.get(key)?.let { result += it; count++; return@let }
        try {
            val conn = URL("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x").openConnection() as HttpURLConnection
            conn.connectTimeout = 3500; conn.readTimeout = 5000
            conn.inputStream.use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return@use
                val (latN, lonW) = tileToLatLon(x, y, zoom)
                val (latS, lonE) = tileToLatLon(x + 1, y + 1, zoom)
                val tile = TileBmp(bmp.asImageBitmap(), latN, latS, lonW, lonE)
                SatelliteTileCache.put(key, tile)
                result += tile; count++
            }
            conn.disconnect()
        } catch (_: Exception) {}
    }
    return result
}
