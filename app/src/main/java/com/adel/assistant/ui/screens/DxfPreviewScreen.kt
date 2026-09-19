package com.adel.assistant.ui.screens

import com.adel.assistant.data.FileExport
import com.adel.assistant.data.GsiPoint
import com.adel.assistant.data.GsiParser

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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
    var message by remember { mutableStateOf("") }
    var zoneText by remember { mutableStateOf("40") }
    var baseMap by remember { mutableStateOf(BaseMap.NONE) }
    var emptyMapColor by remember { mutableStateOf(Color(0xFF202124)) }
    var showEmptyColorPalette by remember { mutableStateOf(false) }
    var showBaseMapDialog by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showDrawings by remember { mutableStateOf(false) }
    var measureMode by remember { mutableStateOf(false) }
    var showMapAlignDialog by remember { mutableStateOf(false) }
    var mapAlignUseScale by remember { mutableStateOf(true) }
    var mapAlignUseAverage by remember { mutableStateOf(false) }
    // هر ردیف: مبدا (name,e,n,z) + مقصد (name,e,n,z)
    var mapAlignRows by remember {
        mutableStateOf(
            listOf(
                listOf("", "", "", "", "", "", "", ""),
                listOf("", "", "", "", "", "", "", "")
            )
        )
    }
    var mapAlignManual by remember { mutableStateOf(true) }
    var mapAlignLoadTarget by remember { mutableStateOf("src") } // src | dst

    var measureA by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var measureB by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var measureDragTarget by remember { mutableStateOf<String?>(null) } // "A" | "B"
    var distanceMsg by remember { mutableStateOf<String?>(null) }
    var showExportPickedDialog by remember { mutableStateOf(false) }
    // انتخاب از نقشه برای الاین: rowIndex, side "src"|"dst"
    var mapAlignPick by remember { mutableStateOf<Pair<Int, String>?>(null) }

    var myLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var tiles by remember { mutableStateOf<List<TileBmp>>(emptyList()) }
    var fitTrigger by remember { mutableStateOf(0) }
    var mapInitialized by remember { mutableStateOf(false) }
    var pickCoordinateMode by remember { mutableStateOf(false) }
    var pickedPoints by remember { mutableStateOf<List<PickedPoint>>(emptyList()) }
    var nextPointId by remember { mutableStateOf(1) }
    var showPointsDialog by remember { mutableStateOf(false) }
    var editingPointId by remember { mutableStateOf<Int?>(null) }
    var editTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
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


    val mapAlignFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val pts = GsiParser.parseTxt(text).ifEmpty {
                GsiParser.parseDat(text).ifEmpty { GsiParser.parse(text) }
            }
            if (pts.isEmpty()) {
                message = "فایل نقطه خالی بود"
                return@rememberLauncherForActivityResult
            }
            // پر کردن ردیف‌ها از فایل — مبدا یا مقصد
            val rows = mapAlignRows.toMutableList()
            while (rows.size < pts.size) {
                rows += listOf("", "", "", "", "", "", "", "")
            }
            pts.forEachIndexed { i, p ->
                val r = rows[i].toMutableList()
                if (mapAlignLoadTarget == "src") {
                    r[0] = p.name
                    r[1] = String.format(java.util.Locale.US, "%.3f", p.e)
                    r[2] = String.format(java.util.Locale.US, "%.3f", p.n)
                    r[3] = String.format(java.util.Locale.US, "%.3f", p.z)
                } else {
                    r[4] = p.name
                    r[5] = String.format(java.util.Locale.US, "%.3f", p.e)
                    r[6] = String.format(java.util.Locale.US, "%.3f", p.n)
                    r[7] = String.format(java.util.Locale.US, "%.3f", p.z)
                }
                rows[i] = r
            }
            mapAlignRows = rows
            message = "بارگذاری ${pts.size} نقطه در " + if (mapAlignLoadTarget == "src") "مبدا" else "مقصد"
            showMapAlignDialog = true
        } catch (e: Exception) {
            message = "خطا خواندن فایل: ${e.message}"
        }
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

    fun snapWorld(rawX: Double, rawY: Double): Pair<Double, Double> {
        val maxW = (28f / scale.coerceAtLeast(1e-6f)).toDouble()
        var bestD = maxW
        var best: Pair<Double, Double>? = null
        fun consider(x: Double, y: Double) {
            val d = kotlin.math.hypot(x - rawX, y - rawY)
            if (d < bestD) { bestD = d; best = x to y }
        }
        allModels.forEach { m ->
            m.lines.forEach { consider(it.x1, it.y1); consider(it.x2, it.y2) }
            m.circles.forEach { consider(it.x, it.y) }
            m.texts.forEach { consider(it.x, it.y) }
        }
        return best ?: (rawX to rawY)
    }

    fun updateMeasureDistance() {
        val a = measureA; val b = measureB
        if (a != null && b != null) {
            val d = DxfParser.horizontalDistance(a.first, a.second, b.first, b.second)
            distanceMsg = "فاصله افقی: ${"%.3f".format(java.util.Locale.US, d)} متر — لمس نزدیک نقطه = کشیدن"
        }
    }

    // فقط وقتی پس‌زمینه نقشه روشن است؛ debounce تا زوم لگ ندهد
    val tileScaleKey = ((kotlin.math.ln(scale.toDouble().coerceAtLeast(1e-9)) / kotlin.math.ln(1.15)).toInt())
    val tileOffsetKey = Offset((offset.x / 48f).toInt() * 48f, (offset.y / 48f).toInt() * 48f)
    LaunchedEffect(baseMap, tileScaleKey, tileOffsetKey, canvasSize, zone) {
        if (baseMap == BaseMap.NONE || canvasSize.x <= 0f) {
            tiles = emptyList()
            return@LaunchedEffect
        }
        delay(220)
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
            color = if (baseMap == BaseMap.SATELLITE) Color(0xFF111111) else if (baseMap == BaseMap.NONE) emptyMapColor else Color(0xFF202124)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 76.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            // Keep the original smooth map gesture. While editing a point,
                            // the map must stay still so the point can move independently.
                            if (editingPointId != null || pickCoordinateMode || measureDragTarget != null || mapAlignPick != null) return@detectTransformGestures
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
                    .pointerInput(editingPointId, scale) {
                        val id = editingPointId ?: return@pointerInput
                        detectDragGestures(
                            onDrag = { change: PointerInputChange, dragAmount: Offset ->
                                change.consume()
                                val s = scale.toDouble().coerceAtLeast(1e-9)
                                val dxv = dragAmount.x.toDouble() / s
                                val dyv = (-dragAmount.y).toDouble() / s
                                pickedPoints = pickedPoints.map { p ->
                                    if (p.id != id) p
                                    else {
                                        val e = p.easting + dxv
                                        val n = p.northing + dyv
                                        val ll = UtmGeo.toLatLon(e, n, zone)
                                        p.copy(easting = e, northing = n, lat = ll.first, lon = ll.second)
                                    }
                                }
                            },
                            onDragEnd = {
                                message = "موقعیت نقطه تغییر کرد؛ برای پایان ویرایش دکمه تأیید را بزن"
                            }
                        )
                    }
                    .pointerInput(measureMode, scale, offset, pickCoordinateMode, editingPointId) {
                        if (editingPointId == null) {
                            detectTapGestures(
                                onDoubleTap = { tap ->
                                    if (pickCoordinateMode) return@detectTapGestures
                                    val factor = 1.7f
                                    val ns = (scale * factor).coerceAtMost(5000f)
                                    offset = Offset(tap.x - (tap.x - offset.x) * ns / scale, tap.y - (tap.y - offset.y) * ns / scale)
                                    scale = ns
                                },
                                onTap = { tap ->
                                    if (pickCoordinateMode) {
                                        val raw = screenToWorld(tap.x, tap.y)
                                        val p = snapWorld(raw.first, raw.second)
                                        val (lat, lon) = UtmGeo.toLatLon(p.first, p.second, zone)
                                        pickedPoints = pickedPoints + PickedPoint(nextPointId, p.first, p.second, lat, lon)
                                        nextPointId++
                                        pickCoordinateMode = false
                                        showPointsDialog = true
                                        message = "نقطه ثبت شد؛ برای ثبت نقطه بعدی + را بزن"
                                        return@detectTapGestures
                                    }
                                    if (!measureMode && mapAlignPick == null) return@detectTapGestures
                                    val raw = screenToWorld(tap.x, tap.y)
                                    val p = snapWorld(raw.first, raw.second)
                                    // الاین از روی نقشه
                                    mapAlignPick?.let { (rowIdx, side) ->
                                        val e = String.format(java.util.Locale.US, "%.3f", p.first)
                                        val n = String.format(java.util.Locale.US, "%.3f", p.second)
                                        val rows = mapAlignRows.toMutableList()
                                        if (rowIdx in rows.indices) {
                                            val r = rows[rowIdx].toMutableList()
                                            if (side == "src") {
                                                r[1] = e; r[2] = n
                                                if (r[0].isBlank()) r[0] = "S${rowIdx + 1}"
                                            } else {
                                                r[5] = e; r[6] = n
                                                if (r[4].isBlank()) r[4] = "T${rowIdx + 1}"
                                            }
                                            rows[rowIdx] = r
                                            mapAlignRows = rows
                                        }
                                        mapAlignPick = null
                                        showMapAlignDialog = true
                                        message = "مختصات از نقشه ثبت شد"
                                        return@detectTapGestures
                                    }
                                    if (!measureMode) return@detectTapGestures
                                    // اگر هر دو نقطه هست و نزدیک یکی لمس شد → انتخاب برای کشیدن
                                    if (measureA != null && measureB != null) {
                                        val pa = worldToScreen(measureA!!.first, measureA!!.second)
                                        val pb = worldToScreen(measureB!!.first, measureB!!.second)
                                        val da = kotlin.math.hypot(tap.x - pa.x, tap.y - pa.y)
                                        val db = kotlin.math.hypot(tap.x - pb.x, tap.y - pb.y)
                                        if (da < 36f || db < 36f) {
                                            measureDragTarget = if (da <= db) "A" else "B"
                                            distanceMsg = "نقطه ${measureDragTarget} را بکش؛ برای نقطه جدید دوباره اندازه را بزن"
                                            return@detectTapGestures
                                        }
                                    }
                                    if (measureA == null || (measureB != null && measureDragTarget == null)) {
                                        measureA = p; measureB = null; measureDragTarget = null
                                        distanceMsg = "نقطه اول (حساس به عارضه)؛ نقطه دوم را لمس کن"
                                    } else if (measureB == null) {
                                        measureB = p
                                        updateMeasureDistance()
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(measureMode, measureDragTarget, scale, offset) {
                        if (!measureMode || measureDragTarget == null) return@pointerInput
                        detectDragGestures(
                            onDrag = { change, _ ->
                                change.consume()
                                val raw = screenToWorld(change.position.x, change.position.y)
                                val p = snapWorld(raw.first, raw.second)
                                when (measureDragTarget) {
                                    "A" -> measureA = p
                                    "B" -> measureB = p
                                }
                                updateMeasureDistance()
                            },
                            onDragEnd = {
                                measureDragTarget = null
                                message = "نقطه اندازه جابجا شد"
                            }
                        )
                    }

            ) {
                canvasSize = Offset(size.width, size.height)

                if (baseMap != BaseMap.NONE) {
                    tiles.forEach { tile ->
                        // چهار گوشهٔ کاشی را به صفحه می‌بریم تا در UTM هم تراز بماند
                        val (eNW, nNW) = UtmGeo.fromLatLon(tile.latNorth, tile.lonWest, zone)
                        val (eNE, nNE) = UtmGeo.fromLatLon(tile.latNorth, tile.lonEast, zone)
                        val (eSW, nSW) = UtmGeo.fromLatLon(tile.latSouth, tile.lonWest, zone)
                        val (eSE, nSE) = UtmGeo.fromLatLon(tile.latSouth, tile.lonEast, zone)
                        val cNW = worldToScreen(eNW, nNW)
                        val cNE = worldToScreen(eNE, nNE)
                        val cSW = worldToScreen(eSW, nSW)
                        val cSE = worldToScreen(eSE, nSE)
                        // min/max برای پوشش کامل + ۱ پیکسل همپوشانی تا خط سیاه بین کاشی‌ها نماند
                        val left = minOf(cNW.x, cNE.x, cSW.x, cSE.x)
                        val right = maxOf(cNW.x, cNE.x, cSW.x, cSE.x)
                        val top = minOf(cNW.y, cNE.y, cSW.y, cSE.y)
                        val bottom = maxOf(cNW.y, cNE.y, cSW.y, cSE.y)
                        val dstLeft = kotlin.math.floor(left.toDouble()).toInt() - 1
                        val dstTop = kotlin.math.floor(top.toDouble()).toInt() - 1
                        val dstRight = kotlin.math.ceil(right.toDouble()).toInt() + 1
                        val dstBottom = kotlin.math.ceil(bottom.toDouble()).toInt() + 1
                        val w = (dstRight - dstLeft).coerceAtLeast(2)
                        val h = (dstBottom - dstTop).coerceAtLeast(2)
                        drawImage(
                            image = tile.image,
                            dstOffset = androidx.compose.ui.unit.IntOffset(dstLeft, dstTop),
                            dstSize = androidx.compose.ui.unit.IntSize(w, h),
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
                        )
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
                // Saved coordinate markers. The active pick/edit marker is deliberately
                // large so it remains visible under a finger.
                pickedPoints.forEach { p ->
                    val s = worldToScreen(p.easting, p.northing)
                    val active = p.id == editingPointId
                    val r = if (active) 22f else 17f
                    drawCircle(Color(0xFFFFC107), r, s, style = Stroke(width = if (active) 4f else 3f))
                    drawLine(Color(0xFFFFC107), Offset(s.x - r, s.y), Offset(s.x + r, s.y), if (active) 3f else 2f)
                    drawLine(Color(0xFFFFC107), Offset(s.x, s.y - r), Offset(s.x, s.y + r), if (active) 3f else 2f)
                }
                editTarget?.let { target ->
                    val s = worldToScreen(target.first, target.second)
                    drawCircle(Color.White, 24f, s, style = Stroke(width = 3f))
                    drawLine(Color.White, Offset(s.x - 28f, s.y), Offset(s.x + 28f, s.y), 2f)
                    drawLine(Color.White, Offset(s.x, s.y - 28f), Offset(s.x, s.y + 28f), 2f)
                }
                if (pickCoordinateMode) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawCircle(Color(0xFFFFC107), 28f, Offset(cx, cy), style = Stroke(width = 5f))
                    drawLine(Color(0xFFFFC107), Offset(cx - 34f, cy), Offset(cx + 34f, cy), 4f)
                    drawLine(Color(0xFFFFC107), Offset(cx, cy - 34f), Offset(cx, cy + 34f), 4f)
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

        if (editingPointId != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.78f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp)
                ) {
                    Text("ویرایش نقطه ${editingPointId}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        editingPointId = null
                        editTarget = null
                        showPointsDialog = true
                        message = "ویرایش ثبت شد"
                    }) { Text("تأیید", color = Color.White) }
                    TextButton(onClick = {
                        editingPointId = null
                        editTarget = null
                        showPointsDialog = true
                    }) { Text("لغو", color = Color.White) }
                }
            }
        }

        
        // منوی شیشه‌ای کرکره‌ای — گوشه بالا راست
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .zIndex(20f)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x66FFFFFF),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = { menuOpen = !menuOpen }, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        if (menuOpen) Icons.Filled.Close else Icons.Filled.Menu,
                        contentDescription = "منو",
                        tint = Color.White
                    )
                }
            }
            AnimatedVisibility(
                visible = menuOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xAA1B1B1B),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.padding(top = 6.dp).width(52.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        IconButton(onClick = { openFile.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.FolderOpen, null, tint = Color.White)
                        }
                        IconButton(onClick = { showDrawings = true }) {
                            Icon(Icons.Filled.Map, null, tint = Color.White)
                        }
                        IconButton(onClick = { showLayers = true }) {
                            Icon(Icons.Filled.Layers, null, tint = Color.White)
                        }
                        IconButton(onClick = { showBaseMapDialog = true }) {
                            Icon(Icons.Filled.Public, null, tint = Color.White)
                        }
                        IconButton(onClick = {
                            pickCoordinateMode = true
                            showPointsDialog = true
                            message = "نقطه را روی نقشه انتخاب کن"
                        }) {
                            Icon(Icons.Filled.MyLocation, null, tint = Color.White)
                        }
                        IconButton(onClick = { fitAll(canvasSize.x, canvasSize.y) }) {
                            Icon(Icons.Filled.ZoomOutMap, null, tint = Color.White)
                        }
                        IconButton(onClick = {
                            if (drawings.isEmpty()) message = "اول یک نقشه DXF/KML باز کن"
                            else {
                                showMapAlignDialog = true
                                mapAlignManual = true
                            }
                        }) {
                            Icon(
                                Icons.Filled.OpenWith,
                                contentDescription = "الاین نقشه",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = {
                            measureMode = !measureMode
                            measureDragTarget = null
                            if (measureMode) {
                                if (measureA == null) distanceMsg = "اندازه‌گذاری: نقطه اول را لمس کن (حساس به عارضه)"
                                else updateMeasureDistance()
                            } else {
                                distanceMsg = null
                            }
                        }) {
                            Icon(Icons.Filled.Straighten, null, tint = if (measureMode) Color(0xFF81C995) else Color.White)
                        }
                    }
                }
            }
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
                    Text("نوع پس‌زمینه را انتخاب کن")
                    BaseMap.values().forEach { item ->
                        OutlinedButton(
                            onClick = {
                                baseMap = item
                                showBaseMapDialog = false
                                if (item == BaseMap.NONE) showEmptyColorPalette = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(item.icon, null)
                            Spacer(Modifier.width(8.dp))
                            Text(item.title)
                        }
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

    if (showEmptyColorPalette) {
        AlertDialog(
            onDismissRequest = { showEmptyColorPalette = false },
            title = { Text("رنگ پس‌زمینه خالی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("یکی از ۱۰ طیف را انتخاب کن")
                    emptyBackgroundPalette.forEach { c ->
                        OutlinedButton(
                            onClick = { emptyMapColor = c; showEmptyColorPalette = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(Modifier.size(26.dp).background(c, RoundedCornerShape(6.dp)))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEmptyColorPalette = false }) { Text("بستن") } }
        )
    }

    if (showPointsDialog) {
        AlertDialog(
            onDismissRequest = { showPointsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("مختصات (${pickedPoints.size})", Modifier.weight(1f))
                    IconButton(onClick = {
                        if (pickedPoints.isEmpty()) message = "نقطه‌ای برای خروجی نیست"
                        else showExportPickedDialog = true
                    }) {
                        Icon(Icons.Filled.UploadFile, "خروجی")
                    }
                    IconButton(onClick = { pickCoordinateMode = true; showPointsDialog = false; message = "نقطه بعدی را روی نقشه انتخاب کن" }) {
                        Icon(Icons.Filled.Add, "نقطه جدید")
                    }
                }
            },
            text = {
                if (pickedPoints.isEmpty()) Text("هنوز نقطه‌ای ثبت نشده است.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(pickedPoints, key = { _, p -> p.id }) { _, p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = true, onCheckedChange = { })
                            Column(Modifier.weight(1f)) {
                                Text("${p.id}: ${formatEn("X=%.3f  Y=%.3f", p.easting, p.northing)}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UtmGeo.neshanIntentUri(p.lat, p.lon)))) } catch (_: Exception) {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(formatEn("https://nshn.ir/?lat=%.6f&lng=%.6f", p.lat, p.lon)))) } catch (_: Exception) {}
                                }
                            }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Navigation, "نمایش در نشان", modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(formatEn("X=%.3f  Y=%.3f", p.easting, p.northing))); message = "مختصات کپی شد" }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.ContentCopy, "کپی", modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = {
                                editingPointId = p.id
                                editTarget = p.easting to p.northing
                                showPointsDialog = false
                                message = "ویرایش نقطه ${p.id}: انگشت را هرجای صفحه بگذار و بکش؛ نقطه همان فاصله حرکت می‌کند"
                            }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Edit, "ویرایش", modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { pickedPoints = pickedPoints.filterNot { it.id == p.id } }, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.Delete, "حذف", modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPointsDialog = false }) { Text("بستن") }
            }
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

    if (showExportPickedDialog) {
        AlertDialog(
            onDismissRequest = { showExportPickedDialog = false },
            title = { Text("خروجی مختصات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pickedPoints.size} نقطه — فرمت را انتخاب کن")
                    listOf("txt", "gsi", "dxf", "kml").forEach { fmt ->
                        Button(
                            onClick = {
                                try {
                                    val gsiPts = pickedPoints.mapIndexed { i, p ->
                                        GsiPoint(
                                            id = p.id.toLong(),
                                            name = "P${p.id}",
                                            e = p.easting,
                                            n = p.northing,
                                            z = 0.0,
                                            code = ""
                                        )
                                    }
                                    val body = when (fmt) {
                                        "txt" -> GsiParser.toTxt(gsiPts)
                                        "gsi" -> GsiParser.toGsi(gsiPts)
                                        "dxf" -> GsiParser.toDxf(gsiPts)
                                        "kml" -> GsiParser.toKml(gsiPts, "picked")
                                        else -> GsiParser.toTxt(gsiPts)
                                    }
                                    val mime = when (fmt) {
                                        "dxf" -> "application/dxf"
                                        "kml" -> "application/vnd.google-earth.kml+xml"
                                        else -> "text/plain"
                                    }
                                    val name = "picked_coords.$fmt"
                                    val ok = FileExport.exportTextToDocuments(context, name, body, mime) != null
                                    message = if (ok) "ذخیره شد: Documents/AdelAssistant/…/$name" else "خطا در ذخیره"
                                } catch (e: Exception) {
                                    message = "خطا خروجی: ${e.message}"
                                }
                                showExportPickedDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = color)
                        ) { Text(fmt.uppercase()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExportPickedDialog = false }) { Text("بستن") } }
        )
    }

    if (showMapAlignDialog) {
        AlertDialog(
            onDismissRequest = { showMapAlignDialog = false },
            title = { Text("الاین نقشه") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("حداقل ۲ جفت مبدا → مقصد (پشت‌سرهم). شماره نقطه اختیاری است.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = {
                            mapAlignRows = mapAlignRows + listOf(listOf("", "", "", "", "", "", "", ""))
                        }) { Icon(Icons.Filled.Add, "جفت بیشتر") }
                        IconButton(onClick = {
                            mapAlignLoadTarget = "src"
                            mapAlignFilePicker.launch(arrayOf("*/*", "text/*"))
                        }) { Icon(Icons.Filled.FolderOpen, "فایل مبدا") }
                        IconButton(onClick = {
                            mapAlignLoadTarget = "dst"
                            mapAlignFilePicker.launch(arrayOf("*/*", "text/*"))
                        }) { Icon(Icons.Filled.UploadFile, "فایل مقصد") }
                        IconButton(onClick = { mapAlignManual = true }) {
                            Icon(Icons.Filled.Edit, "دستی", tint = if (mapAlignManual) color else LocalContentColor.current)
                        }
                    }
                    mapAlignRows.forEachIndexed { idx, row ->
                        Text("جفت ${idx + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        // مبدا
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("مبدا", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                mapAlignPick = idx to "src"
                                showMapAlignDialog = false
                                message = "نقطه مبدا جفت ${idx + 1} را روی نقشه لمس کن (حساس به عارضه)"
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.MyLocation, "از نقشه", modifier = Modifier.size(18.dp), tint = color)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(row[0], { v ->
                                val m = row.toMutableList(); m[0] = v; mapAlignRows = mapAlignRows.toMutableList().also { it[idx] = m }
                            }, label = { Text("N") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(row[1], { v ->
                                val m = row.toMutableList(); m[1] = v; mapAlignRows = mapAlignRows.toMutableList().also { it[idx] = m }
                            }, label = { Text("E") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(row[2], { v ->
                                val m = row.toMutableList(); m[2] = v; mapAlignRows = mapAlignRows.toMutableList().also { it[idx] = m }
                            }, label = { Text("N") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(row[3], { v ->
                                val m = row.toMutableList(); m[3] = v; mapAlignRows = mapAlignRows.toMutableList().also { it[idx] = m }
                            }, label = { Text("Z") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("مقصد", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                mapAlignPick = idx to "dst"
                                showMapAlignDialog = false
                                message = "نقطه مقصد جفت ${idx + 1} را روی نقشه لمس کن (حساس به عارضه)"
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.MyLocation, "از نقشه", modifier = Modifier.size(18.dp), tint = color)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(row[4], { v ->
                                val m = row.toMutableList(); m[4] = v; mapAlignRows = mapAlignRows.toMutableList().also { it[idx] = m }
                            }, label = { Text("N") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(row[5], { v ->
                                val m = row.toMutableList(); m[5] = v; mapAlignRows = mapAlignRows.toMutableList().also { it[idx] = m }
                            }, label = { Text("E") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(row[6], { v ->
                                val m = row.toMutableList(); m[6] = v; mapAlignRows = mapAlignRows.toMutableList().also { it[idx] = m }
                            }, label = { Text("N") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(row[7], { v ->
                                val m = row.toMutableList(); m[7] = v; mapAlignRows = mapAlignRows.toMutableList().also { it[idx] = m }
                            }, label = { Text("Z") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        if (mapAlignRows.size > 2) {
                            TextButton(onClick = {
                                mapAlignRows = mapAlignRows.filterIndexed { i, _ -> i != idx }
                            }) { Text("حذف جفت") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(mapAlignUseScale, { mapAlignUseScale = it })
                        Text("مقیاس")
                        Spacer(Modifier.width(12.dp))
                        Checkbox(mapAlignUseAverage, { mapAlignUseAverage = it })
                        Text("میانگین‌گیری")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        fun d(s: String) = s.replace(',', '.').toDoubleOrNull()
                        val pairs = mutableListOf<AlignPair>()
                        mapAlignRows.forEachIndexed { i, r ->
                            val se = d(r[1]); val sn = d(r[2]); val sz = d(r[3]) ?: 0.0
                            val de = d(r[5]); val dn = d(r[6]); val dz = d(r[7]) ?: 0.0
                            if (se == null || sn == null || de == null || dn == null) return@forEachIndexed
                            val snName = r[0].ifBlank { "S${i + 1}" }
                            val dnName = r[4].ifBlank { "T${i + 1}" }
                            pairs += AlignPair(
                                AlignPoint(snName, se, sn, sz),
                                AlignPoint(dnName, de, dn, dz)
                            )
                        }
                        if (pairs.size < 2) {
                            message = "حداقل ۲ جفت با مختصات کامل لازم است"
                            return@TextButton
                        }
                        val (params, msg) = AlignTransform.alignMapPairs(pairs, mapAlignUseScale, mapAlignUseAverage)
                        drawings = drawings.map { dr ->
                            dr.copy(model = AlignTransform.transformModel(dr.model, params))
                        }
                        message = msg
                        showMapAlignDialog = false
                        fitTrigger++
                    } catch (e: Exception) {
                        message = "خطا الاین: ${e.message}"
                    }
                }) { Text("اعمال") }
            },
            dismissButton = { TextButton(onClick = { showMapAlignDialog = false }) { Text("بستن") } }
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


private enum class BaseMap(val title: String, val service: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SATELLITE("ماهواره‌ای", "World_Imagery", Icons.Filled.Map),
    STREET("نقشه جاده‌ای / ترافیکی", "World_Street_Map", Icons.Filled.DirectionsCar),
    TOPO("توپوگرافی", "World_Topo_Map", Icons.Filled.Terrain),
    NONE("پس‌زمینه خالی", "", Icons.Filled.FormatColorFill)
}

private val emptyBackgroundPalette = listOf(
    Color(0xFF101820), Color(0xFF1B263B), Color(0xFF263238), Color(0xFF37474F), Color(0xFF455A64),
    Color(0xFF546E7A), Color(0xFFECEFF1), Color(0xFFD7CCC8), Color(0xFFE8F5E9), Color(0xFFFFF8E1)
)

private data class PickedPoint(
    val id: Int,
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
    return raw.roundToInt().coerceIn(12, 20)
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
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
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
    val minX = (min(aX, bX) - 1).coerceAtLeast(0)
    val maxX = max(aX, bX) + 1
    val minY = (min(aY, bY) - 1).coerceAtLeast(0)
    val maxY = max(aY, bY) + 1
    val coords = buildList {
        outer@ for (x in minX..maxX) for (y in minY..maxY) {
            if (size >= 80) break@outer
            add(x to y)
        }
    }
    val jobs = coords.map { (x, y) ->
        async(Dispatchers.IO) {
            val key = "${baseMap.name}/$zoom/$x/$y"
            // گوگل ماهواره کیفیت بهتر؛ اسری پشتیبان
            val primary = when (baseMap) {
                BaseMap.SATELLITE -> "https://mt1.google.com/vt/lyrs=s&x=$x&y=$y&z=$zoom"
                BaseMap.STREET -> "https://mt1.google.com/vt/lyrs=m&x=$x&y=$y&z=$zoom"
                BaseMap.TOPO -> "https://services.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/$zoom/$y/$x"
                else -> "https://mt1.google.com/vt/lyrs=s&x=$x&y=$y&z=$zoom"
            }
            val fallback = when (baseMap) {
                BaseMap.SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"
                BaseMap.STREET -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$zoom/$y/$x"
                else -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"
            }
            fetchTile(primary, fallback, x, y, zoom, key)
        }
    }
    jobs.awaitAll().filterNotNull()
}
