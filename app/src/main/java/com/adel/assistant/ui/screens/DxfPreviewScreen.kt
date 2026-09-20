package com.adel.assistant.ui.screens

import com.adel.assistant.data.FileExport
import com.adel.assistant.data.PendingMapOpen
import com.adel.assistant.ui.PointsSpreadsheet
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
    var menuGroup by remember { mutableStateOf<Int?>(null) } // 0..8
    var showOsnapPanel by remember { mutableStateOf(false) }
    var showMyLocPanel by remember { mutableStateOf(false) }
    var pathPts by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var pathMode by remember { mutableStateOf(false) } // مسافت چندنقطه‌ای
    var pathEdit by remember { mutableStateOf(false) }
    var pathFinished by remember { mutableStateOf(false) }
    var areaPts by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var areaMode by remember { mutableStateOf(false) }
    var areaEdit by remember { mutableStateOf(false) }
    var areaFinished by remember { mutableStateOf(false) }
    var linePickIds by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) } // drawingId, lineIndex
    var linePickMode by remember { mutableStateOf(false) }
    var showCoordSub by remember { mutableStateOf(false) }
    var showAreaSub by remember { mutableStateOf(false) }
    var showSaveAndView by remember { mutableStateOf(false) }

    var showLayers by remember { mutableStateOf(false) }
    var showDrawings by remember { mutableStateOf(false) }
    var measureMode by remember { mutableStateOf(false) }

    // —— CAD حرفه‌ای (منوی کرکره‌ای، نه آیکن زیاد) ——
    var showCadPanel by remember { mutableStateOf(false) }
    var showSaveDxfDialog by remember { mutableStateOf(false) }
    var saveDxfName by remember { mutableStateOf("map_edit") }
    var cadTool by remember { mutableStateOf(CadTool.None) }
    var orthoOn by remember { mutableStateOf(false) }
    var gridOn by remember { mutableStateOf(false) }
    var gridStep by remember { mutableStateOf(1.0) }
    var osnap by remember { mutableStateOf(OsnapFlags()) }
    var undoStack by remember { mutableStateOf<List<List<ViewerDrawing>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<ViewerDrawing>>>(emptyList()) }
    var selected by remember { mutableStateOf<CadEntity?>(null) }
    var showProps by remember { mutableStateOf(false) }
    var draftPts by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var statusXY by remember { mutableStateOf("X: —  Y: —") }
    var zoomPrev by remember { mutableStateOf<List<Pair<Float, Offset>>>(emptyList()) }
    var zoomWindowFirst by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var zoomWindowMode by remember { mutableStateOf(false) }
    var layerFilter by remember { mutableStateOf("") }
    var drawLayer by remember { mutableStateOf("CAD") }
    var textDraft by remember { mutableStateOf("متن") }
    var showTextInput by remember { mutableStateOf(false) }

    var showMapAlignDialog by remember { mutableStateOf(false) }
    var alignTargetIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var alignDragKey by remember { mutableStateOf<Pair<Int, String>?>(null) } // row, src|dst

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

    // اندازه‌گذاری و مختصات‌گذاری (زیر مجموعه مختصات)
    var showCoordMenu by remember { mutableStateOf(false) }
    var annotMode by remember { mutableStateOf("") } // dim | coord_beside | coord_table | ""
    var annotTextSize by remember { mutableStateOf("0.5") }
    var annotPointNo by remember { mutableStateOf("1") }
    var lastAnnotPointNo by remember { mutableStateOf(0) }
    var showAnnotSizeDialog by remember { mutableStateOf(false) }
    var showAnnotPointNoDialog by remember { mutableStateOf(false) }
    var pendingAnnotWorld by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var dimDraft by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var tablePoints by remember { mutableStateOf<List<Triple<String, Double, Double>>>(emptyList()) }
    var tablePlaceMode by remember { mutableStateOf(false) }

    var pickedPoints by remember { mutableStateOf<List<PickedPoint>>(emptyList()) }
    var nextPointId by remember { mutableStateOf(1) }
    var showPointsDialog by remember { mutableStateOf(false) }
    var editingPointId by remember { mutableStateOf<Int?>(null) }
    var editTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val clipboard = LocalClipboardManager.current

    val zone = zoneText.toIntOrNull()?.coerceIn(1, 60) ?: 40
    val activeDrawings = drawings.filter { it.visible }
    val allModels = activeDrawings.map { it.model }

    LaunchedEffect(Unit) {
        PendingMapOpen.consume()?.let { (text, name) ->
            try {
                val model = DxfParser.parse(text)
                if (!model.isEmpty) {
                    drawings = drawings + ViewerDrawing(nextDrawingId, name, model)
                    nextDrawingId++
                    fitTrigger++
                    message = "از خروجی: $name بارگذاری شد"
                } else {
                    message = "DXF خالی یا نامعتبر بود"
                }
            } catch (e: Exception) {
                message = "خطا باز کردن DXF: ${e.message}"
            }
        }
    }

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


    val mapAlignFilePicker = rememberLauncherForActivityResult(com.adel.assistant.data.AdelDocuments.OpenDocumentContract()) { uri ->
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
            if (alignTargetIds.isEmpty()) alignTargetIds = drawings.filter { it.visible }.map { it.id }.toSet()
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

    fun pickedAsGsi(): List<GsiPoint> = pickedPoints.map { p ->
        GsiPoint(id = p.id.toLong(), name = "P${p.id}", e = p.easting, n = p.northing, z = 0.0, code = "")
    }
    fun applyGsiToPicked(list: List<GsiPoint>) {
        val byId = list.associateBy { it.id }
        pickedPoints = pickedPoints.map { p ->
            val g = byId[p.id.toLong()] ?: return@map p
            val ll = UtmGeo.toLatLon(g.e, g.n, zone)
            p.copy(easting = g.e, northing = g.n, lat = ll.first, lon = ll.second)
        }
    }

    fun snapWorld(rawX: Double, rawY: Double, ref: Pair<Double, Double>? = null): Pair<Double, Double> {
        var x = rawX; var y = rawY
        if (gridOn) {
            val g = CadEngine.snapToGrid(x, y, gridStep)
            x = g.first; y = g.second
        }
        val maxW = (28f / scale.coerceAtLeast(1e-6f)).toDouble()
        val models = activeDrawings.map { it.id to it.model }
        return CadEngine.snap(x, y, models, osnap, maxW, ref)
    }

    
    fun pushUndo() {
        undoStack = (undoStack + listOf(drawings.map { it.copy(model = it.model.copy(
            lines = it.model.lines.toList(),
            circles = it.model.circles.toList(),
            texts = it.model.texts.toList(),
            layers = it.model.layers.toMutableMap()
        )) })).takeLast(25)
        redoStack = emptyList()
    }
    fun doUndo() {
        if (undoStack.isEmpty()) return
        redoStack = redoStack + listOf(drawings)
        drawings = undoStack.last()
        undoStack = undoStack.dropLast(1)
        message = "Undo"
    }
    fun doRedo() {
        if (redoStack.isEmpty()) return
        undoStack = undoStack + listOf(drawings)
        drawings = redoStack.last()
        redoStack = redoStack.dropLast(1)
        message = "Redo"
    }
    fun ensureCadDrawing(): Int {
        val existing = drawings.find { it.name == "_CAD" }
        if (existing != null) return existing.id
        val empty = DxfModel(emptyList(), emptyList(), emptyList(), linkedMapOf(
            "CAD" to com.adel.assistant.data.DxfLayerInfo("CAD", 1, true)
        ), 0.0, 0.0, 1.0, 1.0)
        val id = nextDrawingId
        drawings = drawings + ViewerDrawing(id, "_CAD", empty)
        nextDrawingId++
        return id
    }
    fun mutateCad(block: (DxfModel) -> DxfModel) {
        pushUndo()
        val id = ensureCadDrawing()
        drawings = drawings.map {
            if (it.id == id) {
                val nm = block(it.model).recalculatedBounds()
                it.copy(model = nm)
            } else it
        }
    }
    fun deleteSelected() {
        val sel = selected ?: return
        pushUndo()
        drawings = drawings.map { dr ->
            val matchId = when (sel) {
                is CadEntity.Line -> sel.drawingId
                is CadEntity.Circle -> sel.drawingId
                is CadEntity.Text -> sel.drawingId
            }
            if (dr.id != matchId) return@map dr
            // لایه قفل؟
            val locked = dr.model.layers.values.any { it.locked }
            val m = when (sel) {
                is CadEntity.Line -> dr.model.copy(lines = dr.model.lines.filterIndexed { i, _ -> i != sel.index })
                is CadEntity.Circle -> dr.model.copy(circles = dr.model.circles.filterIndexed { i, _ -> i != sel.index })
                is CadEntity.Text -> dr.model.copy(texts = dr.model.texts.filterIndexed { i, _ -> i != sel.index })
            }.recalculatedBounds()
            dr.copy(model = m)
        }
        selected = null
        showProps = false
        message = "شیء حذف شد — ذخیره را فراموش نکن"
    }

    fun processCadPoint(raw: Pair<Double, Double>) {
        val ref = draftPts.lastOrNull()
        var p = snapWorld(raw.first, raw.second, ref)
        if (orthoOn && ref != null && cadTool in listOf(CadTool.DrawLine, CadTool.DrawPoly, CadTool.Move, CadTool.Copy)) {
            p = CadEngine.applyOrtho(ref, p)
        }
        when (cadTool) {
            CadTool.Select -> {
                val maxW = (24f / scale.coerceAtLeast(1e-6f)).toDouble()
                selected = CadEngine.pickEntity(p.first, p.second, activeDrawings.map { it.id to it.model }, maxW)
                showProps = selected != null
                message = if (selected != null) "شیء انتخاب شد" else "چیزی انتخاب نشد"
            }
            CadTool.MeasureDist -> {
                if (draftPts.isEmpty()) {
                    draftPts = listOf(p)
                    distanceMsg = "نقطه دوم فاصله"
                } else {
                    val a = draftPts[0]
                    val d = CadEngine.hypot(p.first - a.first, p.second - a.second)
                    distanceMsg = "فاصله: ${"%.3f".format(java.util.Locale.US, d)} m"
                    draftPts = emptyList()
                }
            }
            CadTool.MeasureAngle -> {
                draftPts = draftPts + p
                if (draftPts.size >= 3) {
                    val ang = CadEngine.angleDeg(draftPts[0], draftPts[1], draftPts[2])
                    distanceMsg = "زاویه: ${"%.2f".format(java.util.Locale.US, ang)}°"
                    draftPts = emptyList()
                } else distanceMsg = "نقطه ${draftPts.size + 1} از ۳ (رأس وسط)"
            }
            CadTool.MeasureArea -> {
                draftPts = draftPts + p
                if (draftPts.size >= 3) {
                    val ar = CadEngine.polygonArea(draftPts)
                    distanceMsg = "مساحت موقت: ${"%.3f".format(java.util.Locale.US, ar)} m² — دوباره بزن برای بستن"
                } else distanceMsg = "رأس ${draftPts.size} — حداقل ۳"
            }
            CadTool.DrawLine -> {
                if (draftPts.isEmpty()) {
                    draftPts = listOf(p)
                    message = "نقطه دوم خط"
                } else {
                    val a = draftPts[0]
                    mutateCad { m ->
                        m.copy(lines = m.lines + DxfLine(a.first, a.second, p.first, p.second, drawLayer, 1))
                    }
                    draftPts = emptyList()
                    message = "خط ترسیم شد"
                }
            }
            CadTool.DrawPoly -> {
                if (draftPts.isNotEmpty()) {
                    val a = draftPts.last()
                    mutateCad { m ->
                        m.copy(lines = m.lines + DxfLine(a.first, a.second, p.first, p.second, drawLayer, 3))
                    }
                }
                draftPts = draftPts + p
                message = "پلی‌لاین: ${draftPts.size} رأس — ابزار را ببند برای پایان"
            }
            CadTool.DrawCircle -> {
                if (draftPts.isEmpty()) {
                    draftPts = listOf(p)
                    message = "نقطه روی محیط دایره"
                } else {
                    val c = draftPts[0]
                    val r = CadEngine.hypot(p.first - c.first, p.second - c.second)
                    mutateCad { m ->
                        m.copy(circles = m.circles + DxfCircle(c.first, c.second, r, drawLayer, 1))
                    }
                    draftPts = emptyList()
                    message = "دایره ترسیم شد"
                }
            }
            CadTool.DrawArc -> {
                // تقریبی: سه نقطه → وتر + کمان ساده با دایره
                draftPts = draftPts + p
                if (draftPts.size >= 3) {
                    val (a, b, c) = Triple(draftPts[0], draftPts[1], draftPts[2])
                    // دایره از ۳ نقطه ساده نیست؛ دو پاره خط
                    mutateCad { m ->
                        m.copy(lines = m.lines + listOf(
                            DxfLine(a.first, a.second, b.first, b.second, drawLayer, 2),
                            DxfLine(b.first, b.second, c.first, c.second, drawLayer, 2)
                        ))
                    }
                    draftPts = emptyList()
                    message = "قوس تقریبی (دو پاره)"
                } else message = "نقطه ${draftPts.size} از ۳ قوس"
            }
            CadTool.DrawText -> {
                draftPts = listOf(p)
                showTextInput = true
            }
            CadTool.Move, CadTool.Copy -> {
                if (selected == null) {
                    val maxW = (24f / scale.coerceAtLeast(1e-6f)).toDouble()
                    selected = CadEngine.pickEntity(p.first, p.second, activeDrawings.map { it.id to it.model }, maxW)
                    draftPts = if (selected != null) listOf(p) else emptyList()
                    message = if (selected != null) "مقصد را لمس کن" else "اول شیء را انتخاب کن"
                } else if (draftPts.size == 1) {
                    val a = draftPts[0]
                    val dx = p.first - a.first
                    val dy = p.second - a.second
                    pushUndo()
                    val sel = selected!!
                    drawings = drawings.map { dr ->
                        if (dr.id != when (sel) {
                            is CadEntity.Line -> sel.drawingId
                            is CadEntity.Circle -> sel.drawingId
                            is CadEntity.Text -> sel.drawingId
                        }) return@map dr
                        val m = dr.model
                        val nm = when (sel) {
                            is CadEntity.Line -> {
                                val moved = CadEngine.translateLine(sel.line, dx, dy)
                                if (cadTool == CadTool.Copy)
                                    m.copy(lines = m.lines + moved)
                                else
                                    m.copy(lines = m.lines.mapIndexed { i, l -> if (i == sel.index) moved else l })
                            }
                            is CadEntity.Circle -> {
                                val moved = CadEngine.translateCircle(sel.circle, dx, dy)
                                if (cadTool == CadTool.Copy)
                                    m.copy(circles = m.circles + moved)
                                else
                                    m.copy(circles = m.circles.mapIndexed { i, c -> if (i == sel.index) moved else c })
                            }
                            is CadEntity.Text -> {
                                val moved = CadEngine.translateText(sel.text, dx, dy)
                                if (cadTool == CadTool.Copy)
                                    m.copy(texts = m.texts + moved)
                                else
                                    m.copy(texts = m.texts.mapIndexed { i, tx -> if (i == sel.index) moved else tx })
                            }
                        }.recalculatedBounds()
                        dr.copy(model = nm)
                    }
                    draftPts = emptyList()
                    if (cadTool == CadTool.Move) selected = null
                    message = if (cadTool == CadTool.Copy) "کپی شد" else "جابجا شد"
                }
            }
            CadTool.Rotate, CadTool.Scale -> {
                message = "چرخش/مقیاس: شیء را Select کن سپس ابزار را از پنل CAD بزن (نسخه ساده: Move/Copy فعال)"
            }
            else -> {}
        }
    }

    
    fun textH(): Double = annotTextSize.replace(',', '.').toDoubleOrNull()?.coerceIn(0.01, 50.0) ?: 0.5

    fun placeDimension(a: Pair<Double, Double>, b: Pair<Double, Double>) {
        val d = CadEngine.hypot(b.first - a.first, b.second - a.second)
        val mx = (a.first + b.first) / 2.0
        val my = (a.second + b.second) / 2.0
        val label = String.format(java.util.Locale.US, "%.3f", d)
        val h = textH()
        mutateCad { m ->
            m.copy(
                lines = m.lines + DxfLine(a.first, a.second, b.first, b.second, "DIM", 1),
                texts = m.texts + DxfText(mx, my + h * 0.3, h, label, "DIM", 1)
            )
        }
        message = "اندازه $label ثبت شد"
    }

    fun placeCoordBeside(wx: Double, wy: Double, no: String) {
        val h = textH()
        val line1 = no
        val line2 = String.format(java.util.Locale.US, "X=%.3f Y=%.3f", wx, wy)
        mutateCad { m ->
            m.copy(
                circles = m.circles + DxfCircle(wx, wy, h * 0.15, "COORD", 1),
                texts = m.texts + listOf(
                    DxfText(wx + h * 0.4, wy + h * 1.2, h, line1, "COORD", 1),
                    DxfText(wx + h * 0.4, wy + h * 0.2, h, line2, "COORD", 1)
                )
            )
        }
        // also track as picked for export
        val idNum = no.filter { it.isDigit() }.toIntOrNull() ?: (lastAnnotPointNo + 1)
        lastAnnotPointNo = idNum
        annotPointNo = (idNum + 1).toString()
        val ll = UtmGeo.toLatLon(wx, wy, zone)
        pickedPoints = pickedPoints + PickedPoint(nextPointId, wx, wy, ll.first, ll.second)
        nextPointId++
        message = "مختصات نقطه $no کنار نقطه ثبت شد"
    }

    fun placeCoordTable(origin: Pair<Double, Double>) {
        if (tablePoints.isEmpty()) {
            message = "جدولی خالی است"
            return
        }
        val h = textH()
        val rowH = h * 1.6
        val col1 = h * 8
        val col2 = h * 22
        val ox = origin.first
        val oy = origin.second
        mutateCad { m ->
            val newLines = m.lines.toMutableList()
            val newTexts = m.texts.toMutableList()
            // header
            newTexts += DxfText(ox + h * 0.3, oy - h * 0.3, h, "N", "COORD_TBL", 1)
            newTexts += DxfText(ox + col1 + h * 0.3, oy - h * 0.3, h, "X,Y", "COORD_TBL", 1)
            val n = tablePoints.size + 1
            // outer box
            val totalH = rowH * n
            val totalW = col1 + col2
            newLines += DxfLine(ox, oy, ox + totalW, oy, "COORD_TBL", 1)
            newLines += DxfLine(ox, oy - totalH, ox + totalW, oy - totalH, "COORD_TBL", 1)
            newLines += DxfLine(ox, oy, ox, oy - totalH, "COORD_TBL", 1)
            newLines += DxfLine(ox + totalW, oy, ox + totalW, oy - totalH, "COORD_TBL", 1)
            newLines += DxfLine(ox + col1, oy, ox + col1, oy - totalH, "COORD_TBL", 1)
            for (i in 1 until n) {
                val y = oy - rowH * i
                newLines += DxfLine(ox, y, ox + totalW, y, "COORD_TBL", 1)
            }
            tablePoints.forEachIndexed { idx, tp ->
                val y = oy - rowH * (idx + 1) - h * 0.2
                val xy = String.format(java.util.Locale.US, "%.3f, %.3f", tp.second, tp.third)
                newTexts += DxfText(ox + h * 0.3, y, h, tp.first, "COORD_TBL", 1)
                newTexts += DxfText(ox + col1 + h * 0.3, y, h, xy, "COORD_TBL", 1)
                // mark point on map
                newLines // keep
            }
            // markers at points
            val markers = tablePoints.map { DxfCircle(it.second, it.third, h * 0.12, "COORD", 1) }
            m.copy(lines = newLines, texts = newTexts, circles = m.circles + markers)
        }
        // export list
        tablePoints.forEach { tp ->
            val ll = UtmGeo.toLatLon(tp.second, tp.third, zone)
            pickedPoints = pickedPoints + PickedPoint(nextPointId, tp.second, tp.third, ll.first, ll.second)
            nextPointId++
        }
        tablePoints = emptyList()
        tablePlaceMode = false
        annotMode = ""
        message = "جدول مختصات ترسیم شد"
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
                            val sw = screenToWorld(centroid.x, centroid.y)
                            statusXY = "X: ${"%.3f".format(java.util.Locale.US, sw.first)}  Y: ${"%.3f".format(java.util.Locale.US, sw.second)}"
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
                                    if (!measureMode && mapAlignPick == null && cadTool == CadTool.None && !zoomWindowMode && annotMode.isEmpty() && !pathMode && !areaMode && !linePickMode) {
                                            if (menuOpen) { menuOpen = false; menuGroup = null }
                                            return@detectTapGestures
                                        }
                                    val raw = screenToWorld(tap.x, tap.y)
                                    val p = snapWorld(raw.first, raw.second)
                                    // الاین از روی نقشه
                                    mapAlignPick?.let { (rowIdx, side) ->
                                        val e = String.format(java.util.Locale.US, "%.4f", p.first)
                                        val n = String.format(java.util.Locale.US, "%.4f", p.second)
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
                                        if (alignTargetIds.isEmpty()) alignTargetIds = drawings.filter { it.visible }.map { it.id }.toSet()
                                showMapAlignDialog = true
                                        message = "مختصات از نقشه ثبت شد"
                                        return@detectTapGestures
                                    }
                                    
                                    // اندازه‌گذاری / مختصات‌گذاری
                                    if (annotMode == "dim") {
                                        dimDraft = dimDraft + p
                                        if (dimDraft.size >= 2) {
                                            showAnnotSizeDialog = true
                                            pendingAnnotWorld = null
                                            message = "سایز نوشته اندازه را وارد کن"
                                        } else {
                                            message = "نقطه دوم اندازه"
                                        }
                                        return@detectTapGestures
                                    }
                                    if (annotMode == "coord_beside") {
                                        pendingAnnotWorld = p
                                        annotPointNo = (lastAnnotPointNo + 1).toString()
                                        showAnnotPointNoDialog = true
                                        return@detectTapGestures
                                    }
                                    if (annotMode == "coord_table") {
                                        if (tablePlaceMode) {
                                            placeCoordTable(p)
                                            return@detectTapGestures
                                        }
                                        pendingAnnotWorld = p
                                        annotPointNo = (lastAnnotPointNo + 1).toString()
                                        showAnnotPointNoDialog = true
                                        return@detectTapGestures
                                    }

                                    
                                    // جابجایی مارکر الاین با فاصله انگشت
                                    run {
                                        val hitR = 40f
                                        mapAlignRows.forEachIndexed { idx, row ->
                                            fun tryHit(ei: Int, ni: Int, side: String): Boolean {
                                                val e = row.getOrNull(ei)?.replace(',', '.')?.toDoubleOrNull() ?: return false
                                                val n = row.getOrNull(ni)?.replace(',', '.')?.toDoubleOrNull() ?: return false
                                                val s = worldToScreen(e, n)
                                                if (kotlin.math.hypot(tap.x - s.x, tap.y - s.y) < hitR) {
                                                    alignDragKey = idx to side
                                                    message = "مارکر را با فاصله انگشت جابجا کن"
                                                    return true
                                                }
                                                return false
                                            }
                                            if (tryHit(1, 2, "src") || tryHit(5, 6, "dst")) return@detectTapGestures
                                        }
                                    }

                                    
                                    if (pathMode && !pathFinished) {
                                        val p = snapWorld(raw.first, raw.second)
                                        pathPts = pathPts + p
                                        message = "مسافت: ${pathPts.size} نقطه"
                                        return@detectTapGestures
                                    }
                                    if (areaMode && !areaFinished) {
                                        val p = snapWorld(raw.first, raw.second)
                                        areaPts = areaPts + p
                                        message = "مساحت: ${areaPts.size} رأس"
                                        return@detectTapGestures
                                    }
                                    if (linePickMode) {
                                        val maxW = (24f / scale.coerceAtLeast(1e-6f)).toDouble()
                                        val hit = CadEngine.pickEntity(raw.first, raw.second, activeDrawings.map { it.id to it.model }, maxW)
                                        if (hit is CadEntity.Line) {
                                            val key = hit.drawingId to hit.index
                                            linePickIds = if (key in linePickIds) linePickIds - key else linePickIds + key
                                            message = "خطوط: ${linePickIds.size}"
                                        }
                                        return@detectTapGestures
                                    }
if (zoomWindowMode) {
                                        if (zoomWindowFirst == null) {
                                            zoomWindowFirst = p
                                            message = "گوشه دوم پنجره زوم"
                                        } else {
                                            val a = zoomWindowFirst!!
                                            zoomPrev = (zoomPrev + listOf(scale to offset)).takeLast(10)
                                            // fit to window a..p
                                            val minX = minOf(a.first, p.first)
                                            val maxX = maxOf(a.first, p.first)
                                            val minY = minOf(a.second, p.second)
                                            val maxY = maxOf(a.second, p.second)
                                            val w = canvasSize.x; val h = canvasSize.y
                                            if (w > 0 && maxX > minX && maxY > minY) {
                                                val sx = w * 0.9f / (maxX - minX).toFloat()
                                                val sy = h * 0.9f / (maxY - minY).toFloat()
                                                scale = minOf(sx, sy).coerceIn(0.000001f, 5000f)
                                                offset = Offset(
                                                    w / 2f - ((minX + maxX) / 2.0 * scale).toFloat(),
                                                    h / 2f + ((minY + maxY) / 2.0 * scale).toFloat()
                                                )
                                            }
                                            zoomWindowFirst = null
                                            zoomWindowMode = false
                                            message = "Zoom Window"
                                        }
                                        return@detectTapGestures
                                    }
                                    if (cadTool != CadTool.None) {
                                        processCadPoint(p)
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
                    .pointerInput(alignDragKey, scale, offset) {
                        if (alignDragKey == null) return@pointerInput
                        detectDragGestures(
                            onDrag = { change, _ ->
                                change.consume()
                                val key = alignDragKey ?: return@detectDragGestures
                                val raw = screenToWorld(change.position.x, change.position.y)
                                // فاصله: نقطه زیر انگشت نیست — همان مختصات انگشت به عنوان هدف
                                val p = snapWorld(raw.first, raw.second)
                                val e = String.format(java.util.Locale.US, "%.4f", p.first)
                                val n = String.format(java.util.Locale.US, "%.4f", p.second)
                                val rows = mapAlignRows.toMutableList()
                                if (key.first in rows.indices) {
                                    val r = rows[key.first].toMutableList()
                                    if (key.second == "src") { r[1] = e; r[2] = n } else { r[5] = e; r[6] = n }
                                    rows[key.first] = r
                                    mapAlignRows = rows
                                }
                            },
                            onDragEnd = {
                                alignDragKey = null
                                message = "مارکر جابجا شد"
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


                // شبکه
                if (gridOn && scale > 0.01f) {
                    val step = gridStep
                    val (w0x, w0y) = screenToWorld(0f, 0f)
                    val (w1x, w1y) = screenToWorld(canvasSize.x, canvasSize.y)
                    val gx0 = kotlin.math.floor(minOf(w0x, w1x) / step) * step
                    val gx1 = kotlin.math.ceil(maxOf(w0x, w1x) / step) * step
                    val gy0 = kotlin.math.floor(minOf(w0y, w1y) / step) * step
                    val gy1 = kotlin.math.ceil(maxOf(w0y, w1y) / step) * step
                    var x = gx0
                    var n = 0
                    while (x <= gx1 && n < 80) {
                        val a = worldToScreen(x, gy0); val b = worldToScreen(x, gy1)
                        drawLine(Color(0x33FFFFFF), a, b, 1f)
                        x += step; n++
                    }
                    var y = gy0; n = 0
                    while (y <= gy1 && n < 80) {
                        val a = worldToScreen(gx0, y); val b = worldToScreen(gx1, y)
                        drawLine(Color(0x33FFFFFF), a, b, 1f)
                        y += step; n++
                    }
                }
                dimDraft.forEachIndexed { i, pt ->
                    val s = worldToScreen(pt.first, pt.second)
                    drawCircle(Color(0xFF69F0AE), 7f, s)
                    if (i > 0) {
                        val prev = worldToScreen(dimDraft[i-1].first, dimDraft[i-1].second)
                        drawLine(Color(0xFF69F0AE), prev, s, 2f)
                    }
                }
                
                
                // مسیر مسافت
                if (pathPts.isNotEmpty()) {
                    for (i in 1 until pathPts.size) {
                        val a = worldToScreen(pathPts[i-1].first, pathPts[i-1].second)
                        val b = worldToScreen(pathPts[i].first, pathPts[i].second)
                        drawLine(Color(0xFF4FC3F7), a, b, 3f)
                    }
                    pathPts.forEach { pt ->
                        drawCircle(Color(0xFF4FC3F7), 6f, worldToScreen(pt.first, pt.second))
                    }
                    if (pathFinished && pathPts.size >= 2) {
                        var sum = 0.0
                        for (i in 1 until pathPts.size) {
                            val dx = pathPts[i].first - pathPts[i-1].first
                            val dy = pathPts[i].second - pathPts[i-1].second
                            sum += kotlin.math.hypot(dx, dy)
                        }
                        val mid = pathPts[pathPts.size / 2]
                        val s = worldToScreen(mid.first, mid.second)
                        drawContext.canvas.nativeCanvas.drawText(
                            String.format(java.util.Locale.US, "%.2f m", sum),
                            s.x, s.y - 12f,
                            android.graphics.Paint().apply {
                                this.color = android.graphics.Color.CYAN
                                textSize = 32f
                                isAntiAlias = true
                            }
                        )
                    }
                }
                if (areaPts.size >= 2) {
                    for (i in 1 until areaPts.size) {
                        val a = worldToScreen(areaPts[i-1].first, areaPts[i-1].second)
                        val b = worldToScreen(areaPts[i].first, areaPts[i].second)
                        drawLine(Color(0xFFCE93D8), a, b, 3f)
                    }
                    if (areaPts.size >= 3) {
                        val a0 = worldToScreen(areaPts[0].first, areaPts[0].second)
                        val al = worldToScreen(areaPts.last().first, areaPts.last().second)
                        drawLine(Color(0xFFCE93D8), al, a0, 2f)
                    }
                    if (areaFinished && areaPts.size >= 3) {
                        var a = 0.0
                        val n = areaPts.size
                        for (i in 0 until n) {
                            val j = (i + 1) % n
                            a += areaPts[i].first * areaPts[j].second - areaPts[j].first * areaPts[i].second
                        }
                        a = kotlin.math.abs(a) / 2.0
                        val cx = areaPts.map { it.first }.average()
                        val cy = areaPts.map { it.second }.average()
                        val s = worldToScreen(cx, cy)
                        drawContext.canvas.nativeCanvas.drawText(
                            String.format(java.util.Locale.US, "%.2f m²", a),
                            s.x, s.y,
                            android.graphics.Paint().apply {
                                this.color = android.graphics.Color.rgb(206, 147, 216)
                                textSize = 34f
                                isAntiAlias = true
                            }
                        )
                    }
                }

                // مارکرهای الاین نقشه
                mapAlignRows.forEachIndexed { idx, row ->
                    fun pt(ei: Int, ni: Int): Pair<Double, Double>? {
                        val e = row.getOrNull(ei)?.replace(',', '.')?.toDoubleOrNull() ?: return null
                        val n = row.getOrNull(ni)?.replace(',', '.')?.toDoubleOrNull() ?: return null
                        return e to n
                    }
                    pt(1, 2)?.let { (e, n) ->
                        val s = worldToScreen(e, n)
                        drawCircle(Color(0xFF4FC3F7), 10f, s, style = Stroke(3f))
                        drawContext.canvas.nativeCanvas.drawText("S${idx+1}", s.x + 12f, s.y, android.graphics.Paint().apply {
                            this.color = android.graphics.Color.CYAN; textSize = 28f; isAntiAlias = true
                        })
                    }
                    pt(5, 6)?.let { (e, n) ->
                        val s = worldToScreen(e, n)
                        drawCircle(Color(0xFFFF8A65), 10f, s, style = Stroke(3f))
                        drawContext.canvas.nativeCanvas.drawText("T${idx+1}", s.x + 12f, s.y, android.graphics.Paint().apply {
                            this.color = android.graphics.Color.rgb(255, 138, 101); textSize = 28f; isAntiAlias = true
                        })
                    }
                }
// پیش‌نمایش ترسیم
                draftPts.forEachIndexed { i, pt ->
                    val s = worldToScreen(pt.first, pt.second)
                    drawCircle(Color(0xFF00E5FF), 6f, s)
                    if (i > 0) {
                        val prev = worldToScreen(draftPts[i-1].first, draftPts[i-1].second)
                        drawLine(Color(0xFF00E5FF), prev, s, 2f)
                    }
                }
                // انتخاب
                selected?.let { sel ->
                    when (sel) {
                        is CadEntity.Line -> {
                            val a = worldToScreen(sel.line.x1, sel.line.y1)
                            val b = worldToScreen(sel.line.x2, sel.line.y2)
                            drawLine(Color(0xFFFF9800), a, b, 5f)
                        }
                        is CadEntity.Circle -> {
                            val c = worldToScreen(sel.circle.x, sel.circle.y)
                            drawCircle(Color(0xFFFF9800), (sel.circle.r * scale).toFloat().coerceIn(4f, 500f), c, style = Stroke(3f))
                        }
                        is CadEntity.Text -> {
                            val s = worldToScreen(sel.text.x, sel.text.y)
                            drawCircle(Color(0xFFFF9800), 12f, s, style = Stroke(3f))
                        }
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

        

        // ——— منوی شیشه‌ای ۹ ستونه + زیرمنوی عمود ———
        val glass = Color(0x66FFFFFF)
        val glassDark = Color(0xCC1A1F18)
        fun closeMenus() { menuOpen = false; menuGroup = null; showOsnapPanel = false; showCoordSub = false; showAreaSub = false }
        fun exitAllTools() {
            cadTool = CadTool.None
            measureMode = false
            pathMode = false; pathEdit = false; pathFinished = false
            areaMode = false; areaEdit = false; areaFinished = false
            linePickMode = false
            zoomWindowMode = false
            annotMode = ""
            mapAlignPick = null
            closeMenus()
            message = ""
        }
        val anyTool = cadTool != CadTool.None || measureMode || pathMode || areaMode || linePickMode ||
            zoomWindowMode || annotMode.isNotEmpty() || mapAlignPick != null

        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .zIndex(20f),
            horizontalAlignment = Alignment.End
        ) {
            // دکمه اصلی منو
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glass,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = {
                    if (anyTool) exitAllTools()
                    else { menuOpen = !menuOpen; if (!menuOpen) menuGroup = null }
                }, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        if (anyTool || menuOpen) Icons.Filled.Close else Icons.Filled.Menu,
                        contentDescription = "منو",
                        tint = Color.White
                    )
                }
            }

            if (menuOpen) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    // زیرمنوی افقی (عمود بر ستون اصلی) سمت چپ آیکن‌ها
                    if (menuGroup != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = glassDark,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                Modifier.padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                when (menuGroup) {
                                    0 -> { // فایل
                                        IconButton(onClick = { openFile.launch(arrayOf("*/*", "application/dxf", "text/*")); closeMenus() }) {
                                            Icon(Icons.Filled.FolderOpen, "ورود DXF", tint = Color.White)
                                        }
                                        IconButton(onClick = { showLayers = true; closeMenus() }) {
                                            Icon(Icons.Filled.Layers, "لایه‌ها", tint = Color.White)
                                        }
                                        IconButton(onClick = { showBaseMapDialog = true; closeMenus() }) {
                                            Icon(Icons.Filled.Public, "پس‌زمینه", tint = Color.White)
                                        }
                                        IconButton(onClick = { showSaveDxfDialog = true; closeMenus() }) {
                                            Icon(Icons.Filled.Save, "ذخیره", tint = Color.White)
                                        }
                                    }
                                    1 -> { // مشاهده
                                        IconButton(onClick = { fitTrigger++; closeMenus() }) {
                                            Icon(Icons.Filled.ZoomOutMap, "فیت", tint = Color.White)
                                        }
                                        IconButton(onClick = {
                                            zoomWindowMode = true; zoomWindowFirst = null
                                            message = "زوم پنجره: دو گوشه"; closeMenus()
                                        }) {
                                            Icon(Icons.Filled.Crop, "زوم پنجره", tint = Color.White)
                                        }
                                        IconButton(onClick = {
                                            gridOn = !gridOn; message = if (gridOn) "گرید روشن" else "گرید خاموش"; closeMenus()
                                        }) {
                                            Icon(Icons.Filled.GridOn, "گرید", tint = if (gridOn) Color(0xFF81C995) else Color.White)
                                        }
                                        IconButton(onClick = {
                                            if (hasPermission) { readGps(); showMyLocPanel = true }
                                            else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                            closeMenus()
                                        }) {
                                            Icon(Icons.Filled.MyLocation, "موقعیت من", tint = Color.White)
                                        }
                                    }
                                    2 -> { // اندازه
                                        IconButton(onClick = {
                                            measureMode = true; measureA = null; measureB = null
                                            pathMode = false; areaMode = false; linePickMode = false
                                            message = "اندازه طول: دو نقطه"; closeMenus()
                                        }) {
                                            Icon(Icons.Filled.Straighten, "طول", tint = Color.White)
                                        }
                                        IconButton(onClick = {
                                            pathMode = true; pathPts = emptyList(); pathFinished = false; pathEdit = false
                                            measureMode = false; areaMode = false; linePickMode = false
                                            message = "مسافت: نقاط را پشت‌سرهم لمس کن"; closeMenus()
                                        }) {
                                            Icon(Icons.Filled.MoreHoriz, "مسافت", tint = Color.White)
                                        }
                                        IconButton(onClick = {
                                            linePickMode = true; linePickIds = emptyList()
                                            measureMode = false; pathMode = false; areaMode = false
                                            message = "خطوط را لمس کن"; closeMenus()
                                        }) {
                                            Icon(Icons.Filled.Remove, "طول خط", tint = Color.White)
                                        }
                                        IconButton(onClick = { showAreaSub = true }) {
                                            Icon(Icons.Filled.CropSquare, "مساحت", tint = Color.White)
                                        }
                                        IconButton(onClick = { showCoordSub = true }) {
                                            Icon(Icons.Filled.Place, "مختصات", tint = Color.White)
                                        }
                                    }
                                    3 -> { // ترسیم
                                        listOf(
                                            CadTool.DrawLine to Icons.Filled.TrendingFlat,
                                            CadTool.DrawCircle to Icons.Filled.RadioButtonUnchecked,
                                            CadTool.DrawArc to Icons.Filled.Architecture,
                                            CadTool.DrawText to Icons.Filled.Title
                                        ).forEach { (tool, ic) ->
                                            IconButton(onClick = {
                                                cadTool = tool; draftPts = emptyList(); closeMenus()
                                                message = tool.name
                                            }) { Icon(ic, null, tint = Color.White) }
                                        }
                                    }
                                    4 -> { // ویرایش
                                        listOf(
                                            CadTool.Select to Icons.Filled.NearMe,
                                            CadTool.Move to Icons.Filled.OpenWith,
                                            CadTool.Copy to Icons.Filled.ContentCopy
                                        ).forEach { (tool, ic) ->
                                            IconButton(onClick = {
                                                cadTool = tool; draftPts = emptyList(); closeMenus()
                                            }) { Icon(ic, null, tint = Color.White) }
                                        }
                                        IconButton(onClick = {
                                            if (selected != null) deleteSelected() else {
                                                cadTool = CadTool.Select; message = "اول انتخاب کن"
                                            }
                                            closeMenus()
                                        }) { Icon(Icons.Filled.Delete, "حذف", tint = Color(0xFFE57373)) }
                                    }
                                    5 -> { // الاین
                                        IconButton(onClick = {
                                            if (alignTargetIds.isEmpty())
                                                alignTargetIds = drawings.filter { it.visible }.map { it.id }.toSet()
                                            showMapAlignDialog = true; closeMenus()
                                        }) { Icon(Icons.Filled.Transform, "الاین", tint = Color.White) }
                                        IconButton(onClick = { showDrawings = true; closeMenus() }) {
                                            Icon(Icons.Filled.Map, "انتخاب DXF", tint = Color.White)
                                        }
                                    }
                                    6 -> {
                                        IconButton(onClick = { doUndo(); closeMenus() }) {
                                            Icon(Icons.Filled.Undo, "عقب", tint = Color.White)
                                        }
                                    }
                                    7 -> {
                                        IconButton(onClick = { doRedo(); closeMenus() }) {
                                            Icon(Icons.Filled.Redo, "جلو", tint = Color.White)
                                        }
                                    }
                                    8 -> {
                                        IconButton(onClick = { showOsnapPanel = true }) {
                                            Icon(Icons.Filled.MyLocation, "OSNAP", tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                        // زیر-زیرمنو مختصات / مساحت
                        if (showCoordSub && menuGroup == 2) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = glassDark,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Column(Modifier.padding(6.dp)) {
                                    TextButton(onClick = {
                                        showCoordMenu = true; showCoordSub = false; closeMenus()
                                    }) { Text("مختصات کنار نقطه / جدول", color = Color.White, fontSize = 11.sp) }
                                    TextButton(onClick = {
                                        showExportPickedDialog = true; showCoordSub = false; closeMenus()
                                    }) { Text("خروجی مختصات", color = Color.White, fontSize = 11.sp) }
                                }
                            }
                        }
                        if (showAreaSub && menuGroup == 2) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = glassDark,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Column(Modifier.padding(6.dp)) {
                                    TextButton(onClick = {
                                        // انتخاب پلی‌گون بسته از مدل — ساده: حالت دستی
                                        areaMode = true; areaPts = emptyList(); areaFinished = false; areaEdit = false
                                        showAreaSub = false; closeMenus()
                                        message = "مساحت دستی: رئوس را لمس کن"
                                    }) { Text("انتخاب دستی", color = Color.White, fontSize = 11.sp) }
                                    TextButton(onClick = {
                                        areaMode = true; areaPts = emptyList(); areaFinished = false
                                        showAreaSub = false; closeMenus()
                                        message = "پلی‌گون: رئوس پشت‌سرهم"
                                    }) { Text("پلی‌گون بسته", color = Color.White, fontSize = 11.sp) }
                                }
                            }
                        }
                        if (showOsnapPanel && menuGroup == 8) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = glassDark,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    listOf(
                                        "نقطه" to osnap.end,
                                        "وسط" to osnap.mid,
                                        "تقاطع" to osnap.intersection
                                    ).forEachIndexed { i, (lab, on) ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(on, {
                                                osnap = when (i) {
                                                    0 -> osnap.copy(end = it)
                                                    1 -> osnap.copy(mid = it)
                                                    else -> osnap.copy(intersection = it)
                                                }
                                            })
                                            Text(lab, color = Color.White, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // ستون اصلی ۹ آیکن
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = glassDark,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(4.dp)) {
                            val mainIcons = listOf(
                                Triple(Icons.Filled.Folder, 0, "فایل"),
                                Triple(Icons.Filled.Map, 1, "نمایش"),
                                Triple(Icons.Filled.Straighten, 2, "اندازه"),
                                Triple(Icons.Filled.Create, 3, "ترسیم"),
                                Triple(Icons.Filled.Build, 4, "ویرایش"),
                                Triple(Icons.Filled.Transform, 5, "الاین"),
                                Triple(Icons.Filled.Undo, 6, "عقب"),
                                Triple(Icons.Filled.Redo, 7, "جلو"),
                                Triple(Icons.Filled.MyLocation, 8, "گیر")
                            )
                            mainIcons.forEach { (ic, g, label) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(48.dp).padding(vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            menuGroup = if (menuGroup == g) null else g
                                            showCoordSub = false; showAreaSub = false; showOsnapPanel = false
                                            if (g == 6) { doUndo() }
                                            if (g == 7) { doRedo() }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(ic, null, tint = if (menuGroup == g) Color(0xFF81C995) else Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    Text(
                                        label,
                                        color = if (menuGroup == g) Color(0xFF81C995) else Color.White.copy(alpha = 0.85f),
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
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

        // نوار مختصات و ابزار فعال
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
            color = Color(0xCC1A1F18),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    statusXY + (if (cadTool != CadTool.None) "  |  ${cadTool.name}" else "") +
                        (if (orthoOn) "  ORTHO" else "") + (if (gridOn) "  GRID" else ""),
                    color = Color(0xFFE0E0E0),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                if (pathMode && !pathFinished) {
                    TextButton(onClick = {
                        pathFinished = true
                        message = "مسافت تمام — ویرایش فعال"
                        pathEdit = true
                    }) { Text("اتمام", fontSize = 11.sp) }
                }
                if (pathFinished) {
                    TextButton(onClick = {
                        pathPts = emptyList(); pathMode = false; pathFinished = false; pathEdit = false
                    }) { Text("پاک مسافت", fontSize = 11.sp) }
                }
                if (areaMode && !areaFinished) {
                    TextButton(onClick = {
                        areaFinished = true; areaEdit = true
                        message = "مساحت تمام"
                    }) { Text("اتمام مساحت", fontSize = 11.sp) }
                }
                if (areaFinished) {
                    TextButton(onClick = {
                        areaPts = emptyList(); areaMode = false; areaFinished = false; areaEdit = false
                    }) { Text("پاک مساحت", fontSize = 11.sp) }
                }
                if (linePickMode) {
                    val sum = linePickIds.sumOf { (did, li) ->
                        val dr = drawings.find { it.id == did } ?: return@sumOf 0.0
                        val line = dr.model.lines.getOrNull(li) ?: return@sumOf 0.0
                        kotlin.math.hypot(line.x2 - line.x1, line.y2 - line.y1)
                    }
                    Text(String.format(java.util.Locale.US, "جمع: %.2f m", sum), color = Color.White, fontSize = 11.sp)
                    TextButton(onClick = { linePickIds = emptyList(); linePickMode = false }) { Text("پاک", fontSize = 11.sp) }
                }
                if (undoStack.isNotEmpty()) {
                    TextButton(onClick = { doUndo() }) { Text("Undo", fontSize = 11.sp) }
                }
            }
        }

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.ArrowBack, "بازگشت", tint = Color.White)
        }
    }


    if (showMyLocPanel) {
        AlertDialog(
            onDismissRequest = { showMyLocPanel = false },
            title = { Text("موقعیت من") },
            text = { Text(message.ifBlank { "موقعیت روی نقشه اعمال شد" }) },
            confirmButton = { TextButton(onClick = { showMyLocPanel = false }) { Text("بستن") } }
        )
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
                if (pickedPoints.isEmpty()) {
                    Text("هنوز نقطه‌ای ثبت نشده است.")
                } else {
                    PointsSpreadsheet(
                        points = pickedAsGsi(),
                        color = color,
                        showCheckbox = false,
                        onChange = { g ->
                            val ll = UtmGeo.toLatLon(g.e, g.n, zone)
                            pickedPoints = pickedPoints.map {
                                if (it.id.toLong() == g.id)
                                    it.copy(easting = g.e, northing = g.n, lat = ll.first, lon = ll.second)
                                else it
                            }
                        },
                        onDelete = { g ->
                            pickedPoints = pickedPoints.filterNot { it.id.toLong() == g.id }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    )
                    Text("سلول را بزن و ویرایش کن؛ ✕ = حذف. برای جابجایی روی نقشه از آیکن ویرایش قبلی استفاده کن.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
                    item {
                            OutlinedTextField(layerFilter, { layerFilter = it }, label = { Text("فیلتر نام لایه") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        drawings.filter { it.visible }.forEach { drawing ->
                        item {
                            Text("📁 ${drawing.name}", style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        }
                        val layers = drawing.model.layers.values.sortedBy { it.name }
                        itemsIndexed(layers, key = { _, layer -> "${drawing.id}:${layer.name}" }) { _, layer ->
                            if (layerFilter.isBlank() || layer.name.contains(layerFilter, true)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Checkbox(checked = layer.visible, onCheckedChange = { v ->
                                    layer.visible = v
                                    drawings = drawings.toList()
                                })
                                Text(layer.name.ifBlank { "(بدون نام)" }, Modifier.weight(1f), fontSize = 12.sp)
                                Checkbox(checked = layer.locked, onCheckedChange = { v ->
                                    layer.locked = v
                                    drawings = drawings.toList()
                                })
                                Text("قفل", fontSize = 10.sp)
                                LayerColorButton(layer.displayColor ?: DxfParser.aciToColor(layer.colorAci)) { newColor ->
                                    layer.displayColor = newColor
                                    drawings = drawings.toList()
                                }
                            }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLayers = false }) { Text("بستن") } }
        )
    }



    if (showCoordMenu) {
        AlertDialog(
            onDismissRequest = { showCoordMenu = false },
            title = { Text("مختصات و اندازه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سایز پیش‌فرض نوشته (متر نقشه):")
                    OutlinedTextField(annotTextSize, { annotTextSize = it }, label = { Text("سایز") }, singleLine = true)
                    Button(onClick = {
                        annotMode = "dim"; dimDraft = emptyList(); showCoordMenu = false
                        message = "دو سر خط را برای اندازه‌گذاری لمس کن"
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = color)) {
                        Text("اندازه‌گذاری خط")
                    }
                    Button(onClick = {
                        annotMode = "coord_beside"; showCoordMenu = false
                        message = "نقطه را لمس کن — شماره و سایز پرسیده می‌شود"
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = color)) {
                        Text("مختصات کنار نقطه")
                    }
                    Button(onClick = {
                        annotMode = "coord_table"; tablePoints = emptyList(); tablePlaceMode = false
                        showCoordMenu = false
                        message = "نقاط جدول را یکی‌یکی لمس کن؛ در پایان «جابجایی جدول» را از منوی مختصات بزن"
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = color)) {
                        Text("جدول مختصات")
                    }
                    if (annotMode == "coord_table" && tablePoints.isNotEmpty()) {
                        Button(onClick = {
                            tablePlaceMode = true; showCoordMenu = false
                            message = "جای گوشه بالای جدول را روی نقشه لمس کن"
                        }, modifier = Modifier.fillMaxWidth()) { Text("قرار دادن جدول (${tablePoints.size} نقطه)") }
                    }
                    Button(onClick = {
                        showPointsDialog = true; showCoordMenu = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("لیست / خروجی مختصات") }
                    TextButton(onClick = {
                        annotMode = ""; dimDraft = emptyList(); tablePlaceMode = false
                        showCoordMenu = false; message = "ابزار مختصات خاموش"
                    }) { Text("خاموش کردن ابزار") }
                }
            },
            confirmButton = { TextButton(onClick = { showCoordMenu = false }) { Text("بستن") } }
        )
    }

    if (showAnnotSizeDialog) {
        AlertDialog(
            onDismissRequest = { showAnnotSizeDialog = false; dimDraft = emptyList() },
            title = { Text("سایز نوشته") },
            text = {
                OutlinedTextField(annotTextSize, { annotTextSize = it }, label = { Text("ارتفاع متن") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dimDraft.size >= 2) {
                        placeDimension(dimDraft[0], dimDraft[1])
                    }
                    dimDraft = emptyList()
                    showAnnotSizeDialog = false
                    // keep annotMode dim for more
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { showAnnotSizeDialog = false; dimDraft = emptyList() }) { Text("لغو") } }
        )
    }

    if (showAnnotPointNoDialog && pendingAnnotWorld != null) {
        AlertDialog(
            onDismissRequest = { showAnnotPointNoDialog = false; pendingAnnotWorld = null },
            title = { Text("شماره نقطه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(annotPointNo, { annotPointNo = it }, label = { Text("شماره (پیش‌فرض +۱)") }, singleLine = true)
                    OutlinedTextField(annotTextSize, { annotTextSize = it }, label = { Text("سایز نوشته") }, singleLine = true)
                    if (annotMode == "coord_table") {
                        Text("بعد از چند نقطه، از منوی مختصات «قرار دادن جدول» را بزن")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pt = pendingAnnotWorld!!
                    val no = annotPointNo.ifBlank { (lastAnnotPointNo + 1).toString() }
                    if (annotMode == "coord_beside") {
                        placeCoordBeside(pt.first, pt.second, no)
                    } else if (annotMode == "coord_table") {
                        lastAnnotPointNo = no.filter { it.isDigit() }.toIntOrNull() ?: (lastAnnotPointNo + 1)
                        annotPointNo = (lastAnnotPointNo + 1).toString()
                        tablePoints = tablePoints + Triple(no, pt.first, pt.second)
                        message = "نقطه $no به جدول اضافه شد (${tablePoints.size}) — نقطه بعدی یا قرار دادن جدول"
                    }
                    showAnnotPointNoDialog = false
                    pendingAnnotWorld = null
                }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { showAnnotPointNoDialog = false; pendingAnnotWorld = null }) { Text("لغو") } }
        )
    }


    if (showSaveDxfDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDxfDialog = false },
            title = { Text("ذخیره DXF") },
            text = {
                OutlinedTextField(
                    saveDxfName,
                    { saveDxfName = it },
                    label = { Text("نام فایل (بدون پسوند)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        try {
                            val name = saveDxfName.trim().ifBlank { "map_edit" }.let {
                                if (it.lowercase().endsWith(".dxf")) it else "$it.dxf"
                            }
                            val body = drawings.filter { it.visible }.joinToString("") { it.model.toDxfText() }
                            FileExport.exportTextToDocuments(context, name, body, "application/dxf")
                            message = "ذخیره شد: $name"
                        } catch (e: Exception) {
                            message = "خطا ذخیره: ${e.message}"
                        }
                        showSaveDxfDialog = false
                    }) { Text("ذخیره") }
                    TextButton(onClick = {
                        try {
                            val name = saveDxfName.trim().ifBlank { "map_edit" }.let {
                                if (it.lowercase().endsWith(".dxf")) it else "$it.dxf"
                            }
                            val body = drawings.filter { it.visible }.joinToString("") { it.model.toDxfText() }
                            FileExport.exportTextToDocuments(context, name, body, "application/dxf")
                            PendingMapOpen.setDxf(body, name)
                            message = "ذخیره و آماده نمایش: $name"
                        } catch (e: Exception) {
                            message = "خطا: ${e.message}"
                        }
                        showSaveDxfDialog = false
                    }) { Text("ذخیره + نمایش") }
                }
            },
            dismissButton = { TextButton(onClick = { showSaveDxfDialog = false }) { Text("لغو") } }
        )
    }

    if (showCadPanel) {
        AlertDialog(
            onDismissRequest = { showCadPanel = false },
            title = { Text("ابزار CAD") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ترسیم", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(CadTool.DrawLine to "خط", CadTool.DrawPoly to "پلی‌لاین", CadTool.DrawCircle to "دایره", CadTool.DrawText to "متن").forEach { (tool, label) ->
                            FilterChip(selected = cadTool == tool, onClick = { cadTool = tool; draftPts = emptyList(); showCadPanel = false; message = label }, label = { Text(label, fontSize = 11.sp) })
                        }
                    }
                    Text("اندازه", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(CadTool.MeasureDist to "فاصله", CadTool.MeasureAngle to "زاویه", CadTool.MeasureArea to "مساحت").forEach { (tool, label) ->
                            FilterChip(selected = cadTool == tool, onClick = { cadTool = tool; draftPts = emptyList(); measureMode = false; showCadPanel = false }, label = { Text(label, fontSize = 11.sp) })
                        }
                    }
                    Text("ویرایش", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(CadTool.Select to "انتخاب", CadTool.Move to "جابجایی", CadTool.Copy to "کپی").forEach { (tool, label) ->
                            FilterChip(selected = cadTool == tool, onClick = { cadTool = tool; draftPts = emptyList(); showCadPanel = false }, label = { Text(label, fontSize = 11.sp) })
                        }
                        FilterChip(selected = false, onClick = {
                            if (selected != null) deleteSelected() else { cadTool = CadTool.Select; message = "اول شیء را انتخاب کن" }
                            showCadPanel = false
                        }, label = { Text("حذف شیء", fontSize = 11.sp) })
                    }
                    Text("نمایش", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = false, onClick = { fitTrigger++; showCadPanel = false }, label = { Text("Fit", fontSize = 11.sp) })
                        FilterChip(selected = zoomWindowMode, onClick = { zoomWindowMode = true; zoomWindowFirst = null; showCadPanel = false; message = "Zoom Window: دو گوشه" }, label = { Text("پنجره", fontSize = 11.sp) })
                        FilterChip(selected = false, onClick = {
                            if (zoomPrev.isNotEmpty()) {
                                val (s, o) = zoomPrev.last()
                                zoomPrev = zoomPrev.dropLast(1)
                                scale = s; offset = o
                            }
                            showCadPanel = false
                        }, label = { Text("قبلی", fontSize = 11.sp) })
                    }
                    Text("قیدها", fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(orthoOn, { orthoOn = it }); Text("Ortho", fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Checkbox(gridOn, { gridOn = it }); Text("Grid", fontSize = 12.sp)
                    }
                    if (gridOn) {
                        OutlinedTextField(gridStep.toString(), { v -> v.replace(',','.').toDoubleOrNull()?.let { if (it > 0) gridStep = it } }, label = { Text("گام شبکه") }, singleLine = true)
                    }
                    Text("Osnap", fontWeight = FontWeight.Bold)
                    Row {
                        Checkbox(osnap.end, { osnap = osnap.copy(end = it) }); Text("End", fontSize = 11.sp)
                        Checkbox(osnap.mid, { osnap = osnap.copy(mid = it) }); Text("Mid", fontSize = 11.sp)
                        Checkbox(osnap.center, { osnap = osnap.copy(center = it) }); Text("Cen", fontSize = 11.sp)
                    }
                    Row {
                        Checkbox(osnap.intersection, { osnap = osnap.copy(intersection = it) }); Text("Int", fontSize = 11.sp)
                        Checkbox(osnap.perpendicular, { osnap = osnap.copy(perpendicular = it) }); Text("Perp", fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { doUndo() }) { Text("Undo") }
                        TextButton(onClick = { doRedo() }) { Text("Redo") }
                        TextButton(onClick = {
                            cadTool = CadTool.None; draftPts = emptyList(); zoomWindowMode = false; message = "ابزار خاموش"
                            showCadPanel = false
                        }) { Text("خاموش") }
                    }
                    Button(
                        onClick = {
                            showCadPanel = false
                            showSaveDxfDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("ذخیره DXF ویرایش‌شده") }
                }
            },
            confirmButton = { TextButton(onClick = { showCadPanel = false }) { Text("بستن") } }
        )
    }

    if (showTextInput && draftPts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showTextInput = false; draftPts = emptyList() },
            title = { Text("متن") },
            text = { OutlinedTextField(textDraft, { textDraft = it }, label = { Text("محتوا") }) },
            confirmButton = {
                TextButton(onClick = {
                    val pt = draftPts[0]
                    mutateCad { m ->
                        m.copy(texts = m.texts + DxfText(pt.first, pt.second, 0.5, textDraft, drawLayer, 7))
                    }
                    showTextInput = false
                    draftPts = emptyList()
                    message = "متن اضافه شد"
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { showTextInput = false; draftPts = emptyList() }) { Text("لغو") } }
        )
    }

    if (showProps && selected != null) {
        AlertDialog(
            onDismissRequest = { showProps = false },
            title = { Text("خصوصیات") },
            text = {
                val s = selected!!
                Column {
                    when (s) {
                        is CadEntity.Line -> {
                            Text("نوع: خط")
                            Text("لایه: ${s.line.layer}")
                            Text("از: ${"%.3f".format(s.line.x1)}, ${"%.3f".format(s.line.y1)}")
                            Text("تا: ${"%.3f".format(s.line.x2)}, ${"%.3f".format(s.line.y2)}")
                            val len = CadEngine.hypot(s.line.x2 - s.line.x1, s.line.y2 - s.line.y1)
                            Text("طول: ${"%.3f".format(len)} m")
                        }
                        is CadEntity.Circle -> {
                            Text("نوع: دایره")
                            Text("لایه: ${s.circle.layer}")
                            Text("مرکز: ${"%.3f".format(s.circle.x)}, ${"%.3f".format(s.circle.y)}")
                            Text("شعاع: ${"%.3f".format(s.circle.r)}")
                        }
                        is CadEntity.Text -> {
                            Text("نوع: متن")
                            Text("لایه: ${s.text.layer}")
                            Text(s.text.text)
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { deleteSelected() }) { Text("حذف شیء", color = Color(0xFFE57373)) }
                    TextButton(onClick = { showProps = false }) { Text("بستن") }
                }
            }
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
                    Text("نقشه(های) هدف برای جابجایی:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    drawings.forEach { dr ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = dr.id in alignTargetIds,
                                onCheckedChange = { on ->
                                    alignTargetIds = if (on) alignTargetIds + dr.id else alignTargetIds - dr.id
                                }
                            )
                            Text(dr.name, fontSize = 12.sp)
                        }
                    }
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
                            }, label = { Text("نام") }, modifier = Modifier.weight(1f), singleLine = true)
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
                            }, label = { Text("نام") }, modifier = Modifier.weight(1f), singleLine = true)
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
                            if (dr.id in alignTargetIds) dr.copy(model = AlignTransform.transformModel(dr.model, params))
                            else dr
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
