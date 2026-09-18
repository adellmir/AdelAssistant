package com.adel.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.MapOverlayPoint
import com.adel.assistant.data.MapOverlayStore
import com.adel.assistant.data.ReportEntry
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.TunnelMapBgStore
import com.adel.assistant.data.DxfModel
import com.adel.assistant.data.UtmGeo
import com.adel.assistant.data.filterNumericInput
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * نقشه تونل:
 * پس‌زمینه ثابت محور تونل + نقاط گزارش + نقاط دستی/GPS
 * پس‌زمینه ماهواره / شهری اختیاری
 */
@Composable
fun TunnelExcavationMapScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    var tunnelPts by remember { mutableStateOf(TunnelReportStore.allPoints(context)) }
    var bgModel by remember { mutableStateOf<DxfModel?>(TunnelMapBgStore.parseModel(context)) }
    var showBg by remember { mutableStateOf(true) }
    var reportPts by remember { mutableStateOf(listOf<ReportEntry>()) }
    var overlays by remember { mutableStateOf(MapOverlayStore.all(context)) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var needFit by remember { mutableStateOf(true) }

    var baseMap by remember { mutableStateOf(TunnelBaseMap.NONE) }
    var showBaseDialog by remember { mutableStateOf(false) }
    var tiles by remember { mutableStateOf<List<TunnelTileBmp>>(emptyList()) }
    var zone by remember { mutableStateOf(40) }

    var showTunnel by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(true) }
    var showOverlay by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("نقشه تونل") }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var draftX by remember { mutableStateOf("") }
    var draftY by remember { mutableStateOf("") }
    var draftZ by remember { mutableStateOf("") }
    var draftD by remember { mutableStateOf("") }
    var draftKm by remember { mutableStateOf("") }
    var draftId by remember { mutableStateOf<String?>(null) }

    var hasGps by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    fun reload() {
        tunnelPts = TunnelReportStore.allPoints(context)
        reportPts = TunnelReportStore.allEntries(context).map { TunnelReportStore.ensureCoords(context, it) }
            .filter { it.x != 0.0 || it.y != 0.0 }
        overlays = MapOverlayStore.all(context)
        bgModel = TunnelMapBgStore.parseModel(context)
        needFit = true
        status = "تونل ${tunnelPts.size} | گزارش ${reportPts.size} | دستی ${overlays.size}"
    }

    LaunchedEffect(Unit) { reload() }

    fun worldBounds(): Pair<Offset, Offset>? {
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        tunnelPts.forEach { xs += it.x; ys += it.y }
        reportPts.forEach { xs += it.x; ys += it.y }
        overlays.forEach { xs += it.x; ys += it.y }
        bgModel?.lines?.forEach { ln ->
            xs += ln.x1; ys += ln.y1; xs += ln.x2; ys += ln.y2
        }
        if (xs.isEmpty()) return null
        return Offset(xs.min().toFloat(), ys.min().toFloat()) to
            Offset(xs.max().toFloat(), ys.max().toFloat())
    }

    fun fit() {
        val b = worldBounds() ?: return
        val (mn, mx) = b
        val w = (mx.x - mn.x).coerceAtLeast(1f)
        val h = (mx.y - mn.y).coerceAtLeast(1f)
        val cw = canvasSize.x.coerceAtLeast(1f)
        val ch = canvasSize.y.coerceAtLeast(1f)
        val sx = (cw * 0.88f) / w
        val sy = (ch * 0.88f) / h
        scale = min(sx, sy).coerceIn(0.000001f, 5000f)
        val cx = (mn.x + mx.x) / 2f
        val cy = (mn.y + mx.y) / 2f
        offset = Offset(cw / 2f - cx * scale, ch / 2f + cy * scale)
        needFit = false
    }

    fun worldToScreen(x: Double, y: Double): Offset =
        Offset((x * scale + offset.x).toFloat(), (-y * scale + offset.y).toFloat())

    fun screenToWorld(sx: Float, sy: Float): Pair<Double, Double> {
        val x = (sx - offset.x) / scale
        val y = -(sy - offset.y) / scale
        return x.toDouble() to y.toDouble()
    }

    LaunchedEffect(needFit, canvasSize, tunnelPts, reportPts, overlays) {
        if (needFit && canvasSize.x > 0f &&
            (tunnelPts.isNotEmpty() || reportPts.isNotEmpty() || overlays.isNotEmpty())
        ) {
            fit()
        }
    }

    // بارگذاری کاشی ماهواره/شهری
    LaunchedEffect(baseMap, scale, offset, canvasSize, zone) {
        if (baseMap == TunnelBaseMap.NONE || canvasSize.x <= 0f) {
            tiles = emptyList()
            return@LaunchedEffect
        }
        val (e0, n0) = screenToWorld(0f, canvasSize.y)
        val (e1, n1) = screenToWorld(canvasSize.x, 0f)
        val minE = min(e0, e1); val maxE = max(e0, e1)
        val minN = min(n0, n1); val maxN = max(n0, n1)
        val corners = listOf(
            UtmGeo.toLatLon(minE, minN, zone),
            UtmGeo.toLatLon(minE, maxN, zone),
            UtmGeo.toLatLon(maxE, minN, zone),
            UtmGeo.toLatLon(maxE, maxN, zone)
        )
        val minLat = corners.minOf { it.first }
        val maxLat = corners.maxOf { it.first }
        val minLon = corners.minOf { it.second }
        val maxLon = corners.maxOf { it.second }
        val z = estimateTunnelZoom(minLat, maxLat, minLon, maxLon, canvasSize.x)
        tiles = withContext(Dispatchers.IO) {
            loadTunnelTiles(minLat, maxLat, minLon, maxLon, z, baseMap)
        }
    }

    fun fillFromNearest(x: Double, y: Double) {
        val n = TunnelReportStore.findNearestByXy(context, x, y)
        if (n != null) {
            draftKm = formatEn("%.3f", n.km)
            val elev = n.elevDiff.toDoubleOrNullFa()
            draftZ = if (elev != null) formatEn("%.3f", elev) else "0"
        } else {
            draftKm = "0"; draftZ = "0"
        }
        draftX = formatEn("%.3f", x)
        draftY = formatEn("%.3f", y)
    }

    fun openAddAt(x: Double, y: Double, id: String? = null, d0: String = "") {
        draftId = id
        fillFromNearest(x, y)
        draftD = d0
        showAddDialog = true
    }

    fun saveDraft() {
        val x = draftX.toDoubleOrNullFa() ?: return
        val y = draftY.toDoubleOrNullFa() ?: return
        val z = draftZ.toDoubleOrNullFa() ?: 0.0
        val km = draftKm.toDoubleOrNullFa() ?: 0.0
        val d = draftD.trim()
        if (d.isBlank()) {
            status = "فیلد D (توضیح/نوع) الزامی است"; return
        }
        val id = draftId ?: MapOverlayStore.nextId(context)
        MapOverlayStore.upsert(context, MapOverlayPoint(id, x, y, z, d, km, "manual"))
        overlays = MapOverlayStore.all(context)
        selectedId = id
        showAddDialog = false
        status = "نقطه $id ذخیره شد"
    }

    fun exportCsv() {
        val pts = overlays
        if (pts.isEmpty()) { status = "نقطهٔ دستی برای CSV نیست"; return }
        val ok = FileExport.exportTextToDocuments(
            context, "tunnel_map_points.csv", MapOverlayStore.toCsvBody(pts), "text/csv"
        ) != null
        status = if (ok) "CSV ذخیره شد" else "خطا در CSV"
    }

    fun exportDxf() {
        val fromReport = reportPts.map {
            MapOverlayPoint(it.dateLabel, it.x, it.y, it.z, formatEn("%.3f", it.km), it.km, "report")
        }
        val all = overlays + fromReport
        if (all.isEmpty()) { status = "نقطه‌ای برای DXF نیست"; return }
        val ok = FileExport.exportTextToDocuments(
            context, "tunnel_map.dxf", MapOverlayStore.toDxf(all), "application/dxf"
        ) != null
        status = if (ok) "DXF ذخیره شد" else "خطا در DXF"
    }

    fun readGps() {
        try {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (pr in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (!lm.isProviderEnabled(pr)) continue
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(pr) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            if (best == null) { status = "موقعیت GPS در دسترس نیست"; return }
            val (e, n) = UtmGeo.fromLatLon(best.latitude, best.longitude, zone)
            openAddAt(e, n, d0 = "GPS")
            status = "GPS خوانده شد — توضیح (D) را وارد کن"
        } catch (_: SecurityException) {
            status = "مجوز موقعیت لازم است"
        } catch (e: Exception) {
            status = "خطا GPS: ${e.message}"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasGps = granted
        if (granted) readGps() else status = "مجوز موقعیت رد شد"
    }

    val mapUploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            TunnelMapBgStore.saveBytes(context, bytes)
            bgModel = TunnelMapBgStore.parseModel(context)
            needFit = true
            status = if (bgModel == null || bgModel!!.isEmpty) "فایل ذخیره شد ولی خط ترسیمی پیدا نشد"
            else "نقشه تونل بارگذاری شد (${bgModel!!.lines.size} خط) — در پشتیبان ZIP می‌آید"
        } catch (e: Exception) {
            status = "خطا در بارگذاری نقشه: ${e.message}"
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        ScreenTopBar(title = "نقشه تونل", color = color, onBack = onBack)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .background(
                    when (baseMap) {
                        TunnelBaseMap.SATELLITE -> Color(0xFF111111)
                        TunnelBaseMap.STREET -> Color(0xFF202124)
                        else -> Color(0xFF1A1C1E)
                    },
                    RoundedCornerShape(12.dp)
                )
        ) {
            // منوی شیشه‌ای کرکره‌ای — گوشه بالا راست
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .zIndex(10f)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x66FFFFFF),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(onClick = { menuOpen = !menuOpen }, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            if (menuOpen) Icons.Outlined.Close else Icons.Outlined.Menu,
                            if (menuOpen) "بستن منو" else "منو",
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
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(52.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            IconButton(onClick = { reload() }) { Icon(Icons.Outlined.Refresh, null, tint = Color.White) }
                            IconButton(onClick = { needFit = true; fit() }) { Icon(Icons.Outlined.ZoomOutMap, null, tint = Color.White) }
                            IconButton(onClick = {
                                if (!hasGps) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                else readGps()
                            }) { Icon(Icons.Outlined.MyLocation, null, tint = Color.White) }
                            IconButton(onClick = {
                                val (cx, cy) = if (canvasSize.x > 0) screenToWorld(canvasSize.x / 2, canvasSize.y / 2)
                                else 0.0 to 0.0
                                openAddAt(cx, cy)
                            }) { Icon(Icons.Outlined.AddLocationAlt, null, tint = Color.White) }
                            IconButton(onClick = { editMode = !editMode }) {
                                Icon(if (editMode) Icons.Outlined.Close else Icons.Outlined.Edit, null, tint = if (editMode) color else Color.White)
                            }
                            IconButton(onClick = { showBaseDialog = true }) { Icon(Icons.Outlined.Map, null, tint = Color.White) }
                            IconButton(onClick = {
                                mapUploadLauncher.launch(arrayOf("*/*", "application/dxf", "text/*", "application/octet-stream"))
                            }) { Icon(Icons.Outlined.Upload, null, tint = Color.White) }
                            IconButton(onClick = { showLayers = true }) { Icon(Icons.Outlined.Layers, null, tint = Color.White) }
                            IconButton(onClick = { showExport = true }) { Icon(Icons.Outlined.FileDownload, null, tint = Color.White) }
                        }
                    }
                }
            }
            // وضعیت
            Text(
                status,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color(0x66000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // زوم حول نقطهٔ تماس — بدون پرش
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(0.000001f, 5000f)
                            if (kotlin.math.abs(newScale - oldScale) > 1e-12f) {
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
                    .pointerInput(editMode, overlays, scale, offset) {
                        detectTapGestures(
                            onTap = { pos ->
                                var best: MapOverlayPoint? = null
                                var bestD = 40f
                                overlays.forEach { p ->
                                    val s = worldToScreen(p.x, p.y)
                                    val d = hypot(s.x - pos.x, s.y - pos.y)
                                    if (d < bestD) { bestD = d; best = p }
                                }
                                if (best != null) {
                                    selectedId = best!!.id
                                    if (editMode) {
                                        openAddAt(best!!.x, best!!.y, best!!.id, best!!.d)
                                        draftZ = formatEn("%.3f", best!!.z)
                                        draftKm = formatEn("%.3f", best!!.km)
                                    }
                                } else if (editMode) {
                                    val (wx, wy) = screenToWorld(pos.x, pos.y)
                                    openAddAt(wx, wy)
                                }
                            }
                        )
                    }
                    .pointerInput(editMode, selectedId, scale) {
                        if (!editMode || selectedId == null) return@pointerInput
                        detectDragGestures { change, drag ->
                            change.consume()
                            val id = selectedId ?: return@detectDragGestures
                            val cur = overlays.firstOrNull { it.id == id } ?: return@detectDragGestures
                            val scr = worldToScreen(cur.x, cur.y)
                            val (wx, wy) = screenToWorld(scr.x + drag.x, scr.y + drag.y)
                            val n = TunnelReportStore.findNearestByXy(context, wx, wy)
                            val km = n?.km ?: cur.km
                            val z = n?.elevDiff?.toDoubleOrNullFa() ?: cur.z
                            MapOverlayStore.upsert(context, cur.copy(x = wx, y = wy, km = km, z = z))
                            overlays = MapOverlayStore.all(context)
                        }
                    }
            ) {
                canvasSize = Offset(size.width, size.height)

                // کاشی‌های پس‌زمینه
                if (baseMap != TunnelBaseMap.NONE) {
                    tiles.forEach { tile ->
                        val (eNW, nNW) = UtmGeo.fromLatLon(tile.latNorth, tile.lonWest, zone)
                        val (eNE, nNE) = UtmGeo.fromLatLon(tile.latNorth, tile.lonEast, zone)
                        val (eSW, nSW) = UtmGeo.fromLatLon(tile.latSouth, tile.lonWest, zone)
                        val (eSE, nSE) = UtmGeo.fromLatLon(tile.latSouth, tile.lonEast, zone)
                        val cNW = worldToScreen(eNW, nNW)
                        val cNE = worldToScreen(eNE, nNE)
                        val cSW = worldToScreen(eSW, nSW)
                        val cSE = worldToScreen(eSE, nSE)
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
                            dstOffset = IntOffset(dstLeft, dstTop),
                            dstSize = IntSize(w, h),
                            filterQuality = FilterQuality.Low
                        )
                    }
                }

                // نقشه ثابت آپلود‌شده (DXF) — خط + دایره + متن
                if (showBg) {
                    bgModel?.lines?.forEach { ln ->
                        drawLine(
                            Color(0xFF90CAF9).copy(alpha = 0.85f),
                            worldToScreen(ln.x1, ln.y1),
                            worldToScreen(ln.x2, ln.y2),
                            strokeWidth = 2f
                        )
                    }
                    bgModel?.circles?.forEach { c ->
                        val center = worldToScreen(c.x, c.y)
                        val r = (c.r * scale).toFloat().coerceAtLeast(2f)
                        drawCircle(Color(0xFF90CAF9).copy(alpha = 0.7f), radius = r, center = center, style = Stroke(width = 2f))
                    }
                    bgModel?.texts?.forEach { tx ->
                        val pos = worldToScreen(tx.x, tx.y)
                        // ارتفاع متن DXF به پیکسل صفحه؛ حداقل خوانا
                        val hPx = (tx.height * scale).toFloat().coerceIn(14f, 48f)
                        val tp = Paint().apply {
                            this.color = android.graphics.Color.rgb(0xE3, 0xF2, 0xFD)
                            textSize = hPx
                            isAntiAlias = true
                            textAlign = Paint.Align.LEFT
                        }
                        drawContext.canvas.nativeCanvas.drawText(tx.text, pos.x, pos.y, tp)
                    }
                }

                if (showTunnel && tunnelPts.size >= 2) {
                    val sorted = tunnelPts.sortedBy { it.km }
                    for (i in 0 until sorted.lastIndex) {
                        val a = sorted[i]; val b = sorted[i + 1]
                        drawLine(
                            Color(0xFF8AB4F8),
                            worldToScreen(a.x, a.y),
                            worldToScreen(b.x, b.y),
                            strokeWidth = 3f
                        )
                    }
                }
                if (showTunnel) {
                    tunnelPts.forEach { p ->
                        drawCircle(Color(0xFF5F6368), radius = 4f, center = worldToScreen(p.x, p.y))
                    }
                }

                if (showReport) {
                    // ۷ سانتی‌متر در مختصات نقشه؛ حداقل خوانا روی صفحه
                    val textPx = (0.07f * scale).coerceIn(18f, 56f)
                    val textPaint = Paint().apply {
                        this.color = android.graphics.Color.rgb(0xE5, 0x39, 0x35) // قرمز
                        textSize = textPx
                        isAntiAlias = true
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.LEFT
                    }
                    reportPts.forEach { p ->
                        val c = worldToScreen(p.x, p.y)
                        val arm = max(8f, textPx * 0.35f)
                        val col = Color(0xFFE53935)
                        // ضربدر
                        drawLine(col, Offset(c.x - arm, c.y - arm), Offset(c.x + arm, c.y + arm), strokeWidth = 2.5f)
                        drawLine(col, Offset(c.x - arm, c.y + arm), Offset(c.x + arm, c.y - arm), strokeWidth = 2.5f)
                        // سه ردیف سمت راست نقطه: شماره / ارتفاع / کیلومتراژ
                        val lineH = textPx * 1.15f
                        val tx = c.x + arm + 6f
                        val ty = c.y
                        drawContext.canvas.nativeCanvas.apply {
                            drawText(p.dateLabel, tx, ty - lineH, textPaint)
                            drawText(formatEn("%.3f", p.z), tx, ty, textPaint)
                            drawText(formatEn("%.3f", p.km), tx, ty + lineH, textPaint)
                        }
                    }
                }

                if (showOverlay) {
                    overlays.forEach { p ->
                        val c = worldToScreen(p.x, p.y)
                        val col = if (p.id == selectedId) color else Color(0xFFFFB74D)
                        drawCircle(col, radius = 10f, center = c)
                        drawCircle(Color.White, radius = 4f, center = c)
                        if (p.id == selectedId) {
                            drawCircle(col.copy(alpha = 0.35f), radius = 18f, center = c, style = Stroke(width = 2f))
                        }
                    }
                }
            }
        }

        if (overlays.isNotEmpty()) {
            Text("نقاط دستی/GPS", color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 140.dp).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(overlays, key = { it.id }) { p ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (p.id == selectedId) color.copy(alpha = 0.2f) else SurfaceColor,
                        onClick = { selectedId = p.id },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${p.id} — ${p.d}", color = TextPrimary, fontSize = 13.sp)
                                Text(
                                    formatEn("X=%.2f Y=%.2f Z=%.2f KM=%.3f", p.x, p.y, p.z, p.km),
                                    color = TextSecondary, fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = {
                                openAddAt(p.x, p.y, p.id, p.d)
                                draftZ = formatEn("%.3f", p.z)
                                draftKm = formatEn("%.3f", p.km)
                            }) { Icon(Icons.Outlined.Edit, null, tint = color) }
                            IconButton(onClick = {
                                MapOverlayStore.delete(context, p.id)
                                overlays = MapOverlayStore.all(context)
                                if (selectedId == p.id) selectedId = null
                            }) { Icon(Icons.Outlined.Delete, null, tint = Color(0xFFCF6679)) }
                        }
                    }
                }
            }
        }
    }

    if (showBaseDialog) {
        AlertDialog(
            onDismissRequest = { showBaseDialog = false },
            title = { Text("پس‌زمینه نقشه") },
            text = {
                Column {
                    TunnelBaseMap.values().forEach { item ->
                        TextButton(onClick = {
                            baseMap = item
                            showBaseDialog = false
                        }) { Text(item.title) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBaseDialog = false }) { Text("بستن") }
            }
        )
    }

    if (showLayers) {
        AlertDialog(
            onDismissRequest = { showLayers = false },
            title = { Text("لایه‌ها") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showBg, { showBg = it }); Text("نقشه آپلود‌شده (DXF)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showTunnel, { showTunnel = it }); Text("محور و نقاط تونل (ثابت)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showReport, { showReport = it }); Text("نقاط حفاری گزارش")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showOverlay, { showOverlay = it }); Text("نقاط دستی / GPS")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLayers = false }) { Text("باشه") } }
        )
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("خروجی") },
            text = {
                Column {
                    Text("CSV: نقاط دستی X,Y,Z,D,KM", color = TextSecondary, fontSize = 12.sp)
                    Text("DXF: دستی + گزارش (ضربدر و سه متن)", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { exportCsv(); showExport = false }) { Text("CSV") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { exportDxf(); showExport = false }) { Text("DXF") }
                }
            },
            dismissButton = { TextButton(onClick = { showExport = false }) { Text("بستن") } }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (draftId != null) "ویرایش نقطه" else "نقطه جدید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(draftX, { draftX = filterNumericInput(it) }, label = { Text("X") }, keyboardOptions = numKb, singleLine = true)
                    OutlinedTextField(draftY, { draftY = filterNumericInput(it) }, label = { Text("Y") }, keyboardOptions = numKb, singleLine = true)
                    OutlinedTextField(draftZ, { draftZ = filterNumericInput(it) }, label = { Text("Z (اختلاف تراز)") }, keyboardOptions = numKb, singleLine = true)
                    OutlinedTextField(draftKm, { draftKm = filterNumericInput(it) }, label = { Text("KM") }, keyboardOptions = numKb, singleLine = true)
                    OutlinedTextField(draftD, { draftD = it }, label = { Text("D (توضیح — الزامی)") }, singleLine = true)
                    TextButton(onClick = {
                        val x = draftX.toDoubleOrNullFa() ?: return@TextButton
                        val y = draftY.toDoubleOrNullFa() ?: return@TextButton
                        fillFromNearest(x, y)
                    }) { Text("محاسبه Z و KM از نزدیک‌ترین نقطه تونل") }
                }
            },
            confirmButton = { TextButton(onClick = { saveDraft() }) { Text("ذخیره") } },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("انصراف") } }
        )
    }
}

private enum class TunnelBaseMap(val title: String) {
    NONE("پس‌زمینه ساده"),
    SATELLITE("ماهواره‌ای"),
    STREET("نقشه شهری / جاده‌ای")
}

private data class TunnelTileBmp(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val latNorth: Double, val latSouth: Double,
    val lonWest: Double, val lonEast: Double
)

private object TunnelTileCache {
    private const val MAX = 100
    private val cache = object : LinkedHashMap<String, TunnelTileBmp>(MAX, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TunnelTileBmp>?): Boolean = size > MAX
    }
    fun get(key: String) = synchronized(cache) { cache[key] }
    fun put(key: String, value: TunnelTileBmp) = synchronized(cache) { cache[key] = value }
}

private fun estimateTunnelZoom(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, screenW: Float): Int {
    val lonDiff = (maxLon - minLon).absoluteValue.coerceAtLeast(1e-7)
    val raw = ln((360.0 * screenW / 256.0) / lonDiff) / ln(2.0)
    return raw.roundToInt().coerceIn(12, 20)
}

private fun tunnelLatLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
    val n = 1 shl zoom
    val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    return x to y
}

private fun tunnelTileToLatLon(x: Int, y: Int, zoom: Int): Pair<Double, Double> {
    val n = 1 shl zoom
    val lon = x.toDouble() / n * 360.0 - 180.0
    val lat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / n))))
    return lat to lon
}

private suspend fun fetchTunnelTile(
    primary: String, fallback: String, x: Int, y: Int, zoom: Int, key: String
): TunnelTileBmp? = withContext(Dispatchers.IO) {
    TunnelTileCache.get(key)?.let { return@withContext it }
    for (url in listOf(primary, fallback)) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "AdelAssistant/1.0")
            }
            if (conn.responseCode !in 200..299) continue
            val tile = conn.inputStream.use { input ->
                val bmp = BitmapFactory.decodeStream(input) ?: return@use null
                val (latN, lonW) = tunnelTileToLatLon(x, y, zoom)
                val (latS, lonE) = tunnelTileToLatLon(x + 1, y + 1, zoom)
                TunnelTileBmp(bmp.asImageBitmap(), latN, latS, lonW, lonE)
            } ?: continue
            TunnelTileCache.put(key, tile)
            return@withContext tile
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }
    }
    null
}

private suspend fun loadTunnelTiles(
    minLat: Double, maxLat: Double, minLon: Double, maxLon: Double,
    zoom: Int, baseMap: TunnelBaseMap
): List<TunnelTileBmp> = coroutineScope {
    if (baseMap == TunnelBaseMap.NONE) return@coroutineScope emptyList()
    val (aX, aY) = tunnelLatLonToTile(minLat, minLon, zoom)
    val (bX, bY) = tunnelLatLonToTile(maxLat, maxLon, zoom)
    val minX = (min(aX, bX) - 1).coerceAtLeast(0)
    val maxX = max(aX, bX) + 1
    val minY = (min(aY, bY) - 1).coerceAtLeast(0)
    val maxY = max(aY, bY) + 1
    val coords = buildList {
        outer@ for (x in minX..maxX) for (y in minY..maxY) {
            if (size >= 64) break@outer
            add(x to y)
        }
    }
    coords.map { (x, y) ->
        async(Dispatchers.IO) {
            val key = "${baseMap.name}/$zoom/$x/$y"
            val primary = when (baseMap) {
                TunnelBaseMap.SATELLITE -> "https://mt1.google.com/vt/lyrs=s&x=$x&y=$y&z=$zoom"
                TunnelBaseMap.STREET -> "https://mt1.google.com/vt/lyrs=m&x=$x&y=$y&z=$zoom"
                else -> "https://mt1.google.com/vt/lyrs=s&x=$x&y=$y&z=$zoom"
            }
            val fallback = when (baseMap) {
                TunnelBaseMap.SATELLITE ->
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x"
                else ->
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$zoom/$y/$x"
            }
            fetchTunnelTile(primary, fallback, x, y, zoom, key)
        }
    }.awaitAll().filterNotNull()
}
