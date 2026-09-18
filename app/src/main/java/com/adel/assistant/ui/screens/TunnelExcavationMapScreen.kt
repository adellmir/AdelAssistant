package com.adel.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.MapOverlayPoint
import com.adel.assistant.data.MapOverlayStore
import com.adel.assistant.data.ReportEntry
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.UtmGeo
import com.adel.assistant.data.filterNumericInput
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * نقشه حفاری تونل:
 * - پس‌زمینه ثابت: محور و نقاط تونل از پایگاه (غیرقابل ویرایش)
 * - نقاط حفاری از گزارش روزانه (بروزرسانی خودکار)
 * - نقاط دستی/GPS قابل ویرایش و جابجایی
 * - خروجی DXF و CSV با فیلدهای X,Y,Z,D,KM
 */
@Composable
fun TunnelExcavationMapScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    var tunnelPts by remember { mutableStateOf(TunnelReportStore.allPoints(context)) }
    var reportPts by remember { mutableStateOf(listOf<ReportEntry>()) }
    var overlays by remember { mutableStateOf(MapOverlayStore.all(context)) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var fitted by remember { mutableStateOf(false) }

    var showTunnel by remember { mutableStateOf(true) }
    var showReport by remember { mutableStateOf(true) }
    var showOverlay by remember { mutableStateOf(true) }
    var showLayers by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("نقشه محور تونل + نقاط حفاری") }

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
        // همهٔ گزارش‌ها با مختصات
        reportPts = TunnelReportStore.allEntries(context).map { TunnelReportStore.ensureCoords(context, it) }
            .filter { it.x != 0.0 || it.y != 0.0 }
        overlays = MapOverlayStore.all(context)
        fitted = false
        status = "تونل ${tunnelPts.size} | گزارش ${reportPts.size} | دستی ${overlays.size}"
    }

    LaunchedEffect(Unit) { reload() }

    fun worldBounds(): Pair<Offset, Offset>? {
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        tunnelPts.forEach { xs += it.x; ys += it.y }
        reportPts.forEach { xs += it.x; ys += it.y }
        overlays.forEach { xs += it.x; ys += it.y }
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
        scale = min(sx, sy)
        // مرکز
        val cx = (mn.x + mx.x) / 2f
        val cy = (mn.y + mx.y) / 2f
        offset = Offset(cw / 2f - cx * scale, ch / 2f + cy * scale) // y معکوس صفحه
        fitted = true
    }

    fun worldToScreen(x: Double, y: Double): Offset =
        Offset(x.toFloat() * scale + offset.x, -y.toFloat() * scale + offset.y)

    fun screenToWorld(sx: Float, sy: Float): Pair<Double, Double> {
        val x = (sx - offset.x) / scale
        val y = -(sy - offset.y) / scale
        return x.toDouble() to y.toDouble()
    }

    fun fillFromNearest(x: Double, y: Double) {
        val n = TunnelReportStore.findNearestByXy(context, x, y)
        if (n != null) {
            draftKm = formatEn("%.3f", n.km)
            // z = اختلاف تراز کف خیابان از نزدیک‌ترین نقطه
            val elev = n.elevDiff.toDoubleOrNullFa()
            draftZ = if (elev != null) formatEn("%.3f", elev) else formatEn("%.3f", 0.0)
        } else {
            draftKm = "0"
            draftZ = "0"
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
            status = "فیلد D (توضیح/نوع) الزامی است"
            return
        }
        // بعد از ویرایش دستی XY دوباره نزدیک‌ترین را برای km/z پیشنهاد نکن مگر کاربر عوض کرده
        val id = draftId ?: MapOverlayStore.nextId(context)
        val p = MapOverlayPoint(id, x, y, z, d, km, if (draftId != null) "manual" else "manual")
        MapOverlayStore.upsert(context, p)
        overlays = MapOverlayStore.all(context)
        selectedId = id
        showAddDialog = false
        status = "نقطه $id ذخیره شد"
    }

    fun exportCsv() {
        val pts = overlays
        if (pts.isEmpty()) {
            status = "نقطهٔ دستی برای CSV نیست"; return
        }
        val body = MapOverlayStore.toCsvBody(pts)
        val ok = FileExport.exportTextToDocuments(context, "tunnel_map_points.csv", body, "text/csv") != null
        status = if (ok) "CSV ذخیره شد (Documents/AdelAssistant/txt)" else "خطا در CSV"
    }

    fun exportDxf() {
        // DXF گزارش: id=تاریخ(مثل 050627) ، z=ارتفاع ، km=کیلومتراژ — سه ردیف سمت چپ ضربدر
        val fromReport = reportPts.map {
            MapOverlayPoint(
                id = it.dateLabel,
                x = it.x, y = it.y, z = it.z,
                d = formatEn("%.3f", it.km),
                km = it.km,
                source = "report"
            )
        }
        val all = overlays + fromReport
        if (all.isEmpty()) {
            status = "نقطه‌ای برای DXF نیست"; return
        }
        val body = MapOverlayStore.toDxf(all)
        val ok = FileExport.exportTextToDocuments(context, "tunnel_map_excavation.dxf", body, "application/dxf") != null
        status = if (ok) "DXF ذخیره شد (Documents/AdelAssistant/dxf)" else "خطا در DXF"
    }

    fun readGps() {
        try {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            var best: Location? = null
            for (pr in providers) {
                if (!lm.isProviderEnabled(pr)) continue
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(pr) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            if (best == null) {
                status = "موقعیت GPS در دسترس نیست"; return
            }
            val zone = 40 // پیش‌فرض ایران مرکزی؛ در صورت نیاز قابل تغییر
            val (e, n) = UtmGeo.fromLatLon(best.latitude, best.longitude, zone)
            openAddAt(e, n, d0 = "GPS")
            status = "موقعیت GPS خوانده شد — توضیح (D) را وارد کن"
        } catch (e: SecurityException) {
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        ScreenTopBar(title = "نقشه حفاری تونل", color = color, onBack = onBack)

        // نوار ابزار
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { reload(); fit() }) {
                Icon(Icons.Outlined.Refresh, "بروزرسانی", tint = color)
            }
            IconButton(onClick = { fit() }) {
                Icon(Icons.Outlined.ZoomOutMap, "Fit", tint = color)
            }
            IconButton(onClick = {
                if (!hasGps) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                else readGps()
            }) {
                Icon(Icons.Outlined.MyLocation, "موقعیت من", tint = color)
            }
            IconButton(onClick = {
                val (cx, cy) = if (canvasSize.x > 0) screenToWorld(canvasSize.x / 2, canvasSize.y / 2)
                else 0.0 to 0.0
                openAddAt(cx, cy)
            }) {
                Icon(Icons.Outlined.AddLocationAlt, "نقطه دستی", tint = color)
            }
            IconButton(onClick = { editMode = !editMode }) {
                Icon(
                    if (editMode) Icons.Outlined.Close else Icons.Outlined.Edit,
                    "ویرایش",
                    tint = if (editMode) color else TextSecondary
                )
            }
            IconButton(onClick = { showLayers = true }) {
                Icon(Icons.Outlined.Layers, "لایه‌ها", tint = color)
            }
            IconButton(onClick = { showExport = true }) {
                Icon(Icons.Outlined.FileDownload, "خروجی", tint = color)
            }
        }

        Text(
            status,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // نقشه
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .background(Color(0xFF1A1C1E), RoundedCornerShape(12.dp))
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(editMode, selectedId, scale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.0001f, 500f)
                            offset += pan
                        }
                    }
                    .pointerInput(editMode, overlays, scale, offset) {
                        detectTapGestures(
                            onTap = { pos ->
                                // انتخاب نزدیک‌ترین نقطهٔ overlay
                                var best: MapOverlayPoint? = null
                                var bestD = 40f
                                overlays.forEach { p ->
                                    val s = worldToScreen(p.x, p.y)
                                    val d = hypot(s.x - pos.x, s.y - pos.y)
                                    if (d < bestD) {
                                        bestD = d; best = p
                                    }
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
                            val (wx, wy) = screenToWorld(
                                worldToScreen(cur.x, cur.y).x + drag.x,
                                worldToScreen(cur.x, cur.y).y + drag.y
                            )
                            val n = TunnelReportStore.findNearestByXy(context, wx, wy)
                            val km = n?.km ?: cur.km
                            val z = n?.elevDiff?.toDoubleOrNullFa() ?: cur.z
                            val updated = cur.copy(x = wx, y = wy, km = km, z = z)
                            MapOverlayStore.upsert(context, updated)
                            overlays = MapOverlayStore.all(context)
                        }
                    }
            ) {
                canvasSize = Offset(size.width, size.height)
                if (!fitted && canvasSize.x > 0 && (tunnelPts.isNotEmpty() || overlays.isNotEmpty() || reportPts.isNotEmpty())) {
                    fit()
                }

                // محور تونل (ثابت)
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
                        val c = worldToScreen(p.x, p.y)
                        drawCircle(Color(0xFF5F6368), radius = 4f, center = c)
                    }
                }

                // نقاط گزارش روزانه — ضربدر + (در زوم مناسب قابل تشخیص)
                if (showReport) {
                    reportPts.forEach { p ->
                        val c = worldToScreen(p.x, p.y)
                        val arm = 8f
                        val col = Color(0xFF81C995)
                        drawLine(col, Offset(c.x - arm, c.y - arm), Offset(c.x + arm, c.y + arm), strokeWidth = 2.5f)
                        drawLine(col, Offset(c.x - arm, c.y + arm), Offset(c.x + arm, c.y - arm), strokeWidth = 2.5f)
                    }
                }

                // نقاط دستی/GPS
                if (showOverlay) {
                    overlays.forEach { p ->
                        val c = worldToScreen(p.x, p.y)
                        val col = if (p.id == selectedId) color else Color(0xFFFFB74D)
                        drawCircle(col, radius = 10f, center = c)
                        drawCircle(Color.White, radius = 4f, center = c)
                        if (p.id == selectedId) {
                            drawCircle(
                                col.copy(alpha = 0.35f),
                                radius = 18f,
                                center = c,
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                }
            }
        }

        // لیست کوتاه نقاط دستی
        if (overlays.isNotEmpty()) {
            Text(
                "نقاط دستی/GPS (قابل ویرایش)",
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(overlays, key = { it.id }) { p ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (p.id == selectedId) color.copy(alpha = 0.2f) else SurfaceColor,
                        onClick = { selectedId = p.id },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${p.id} — ${p.d}", color = TextPrimary, fontSize = 13.sp)
                                Text(
                                    formatEn("X=%.2f Y=%.2f Z=%.2f KM=%.3f", p.x, p.y, p.z, p.km),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = {
                                openAddAt(p.x, p.y, p.id, p.d)
                                draftZ = formatEn("%.3f", p.z)
                                draftKm = formatEn("%.3f", p.km)
                            }) {
                                Icon(Icons.Outlined.Edit, null, tint = color)
                            }
                            IconButton(onClick = {
                                MapOverlayStore.delete(context, p.id)
                                overlays = MapOverlayStore.all(context)
                                if (selectedId == p.id) selectedId = null
                            }) {
                                Icon(Icons.Outlined.Delete, null, tint = Color(0xFFCF6679))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLayers) {
        AlertDialog(
            onDismissRequest = { showLayers = false },
            title = { Text("لایه‌ها") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showTunnel, { showTunnel = it })
                        Text("محور و نقاط تونل (ثابت)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showReport, { showReport = it })
                        Text("نقاط حفاری گزارش روزانه")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showOverlay, { showOverlay = it })
                        Text("نقاط دستی / GPS")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLayers = false }) { Text("باشه") }
            }
        )
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("خروجی") },
            text = {
                Column {
                    Text("CSV: فقط نقاط دستی/GPS با فیلد X,Y,Z,D,KM", color = TextSecondary, fontSize = 12.sp)
                    Text("DXF: نقاط دستی + نقاط گزارش روزانه", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { exportCsv(); showExport = false }) { Text("CSV") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { exportDxf(); showExport = false }) { Text("DXF") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExport = false }) { Text("بستن") }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (draftId != null) "ویرایش نقطه" else "نقطه جدید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        draftX, { draftX = filterNumericInput(it) },
                        label = { Text("X") }, keyboardOptions = numKb, singleLine = true
                    )
                    OutlinedTextField(
                        draftY, { draftY = filterNumericInput(it) },
                        label = { Text("Y") }, keyboardOptions = numKb, singleLine = true
                    )
                    OutlinedTextField(
                        draftZ, { draftZ = filterNumericInput(it) },
                        label = { Text("Z (اختلاف تراز کف خیابان)") }, keyboardOptions = numKb, singleLine = true
                    )
                    OutlinedTextField(
                        draftKm, { draftKm = filterNumericInput(it) },
                        label = { Text("KM") }, keyboardOptions = numKb, singleLine = true
                    )
                    OutlinedTextField(
                        draftD, { draftD = it },
                        label = { Text("D (توضیح / نوع — الزامی)") }, singleLine = true
                    )
                    TextButton(onClick = {
                        val x = draftX.toDoubleOrNullFa() ?: return@TextButton
                        val y = draftY.toDoubleOrNullFa() ?: return@TextButton
                        fillFromNearest(x, y)
                    }) { Text("محاسبه Z و KM از نزدیک‌ترین نقطه تونل") }
                }
            },
            confirmButton = {
                TextButton(onClick = { saveDraft() }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("انصراف") }
            }
        )
    }
}
