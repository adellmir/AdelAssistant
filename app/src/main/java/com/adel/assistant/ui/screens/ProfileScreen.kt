package com.adel.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.adel.assistant.data.*
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(color: Color, onBack: () -> Unit, onOpenMap: (String) -> Unit) {
    val context = LocalContext.current
    val surfaces = ProfileSession.surfaces
    var intervalText by remember { mutableStateOf("10") }
    var startText by remember { mutableStateOf("0") }
    var horizontalScaleText by remember { mutableStateOf("100") }
    var verticalScaleText by remember { mutableStateOf("100") }
    var result by remember { mutableStateOf<ProfileResult2?>(null) }
    var alignment by remember { mutableStateOf<List<AlignmentVertex>>(emptyList()) }
    var showScaleDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var drawingZoom by remember { mutableStateOf(1f) }
    var drawingPan by remember { mutableStateOf(Offset.Zero) }
    var showTable by remember { mutableStateOf(false) }
    var showDxfSettings by remember { mutableStateOf(false) }
    var dxfStartChainage by remember { mutableStateOf("0") }
    var dxfStationTextSize by remember { mutableStateOf("1.0") }
    var dxfProfileTextSize by remember { mutableStateOf("1.5") }

    val pickSurface = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val loaded = mutableListOf<ProfileSurfaceSlot>()
        uris.forEach { uri ->
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@forEach
                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank { "سطح ${surfaces.size + loaded.size + 1}" }
                    ?: "سطح ${surfaces.size + loaded.size + 1}"
                val pts = PointConverter.readBytes(bytes, uri.lastPathSegment ?: "points.txt")
                    .map { VolPoint(it.id, it.x, it.y, it.z, it.code) }
                if (pts.size >= 3) loaded += ProfileSurfaceSlot(name, pts)
            } catch (_: Exception) { }
        }
        if (loaded.isNotEmpty()) {
            val merged = surfaces + loaded
            ProfileSession.updateSurfaces(merged)
            message = "${loaded.size} سطح اضافه شد (جمع: ${merged.size})"
            if (alignment.size >= 2) {
                val step = intervalText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.01) ?: 10.0
                val start = startText.replace(',', '.').toDoubleOrNull() ?: 0.0
                val ss = merged.mapNotNull { ProfileEngine.buildSurface(it.name, it.points) }
                if (ss.isNotEmpty()) {
                    result = ProfileEngine.sample(alignment, ss, step, start)
                    message = "${loaded.size} سطح اضافه شد — پروفیل با ${ss.size} سطح به‌روز شد"
                }
            }
        } else message = "سطح معتبر پیدا نشد"
    }

    LaunchedEffect(ProfileSession.alignmentResult) {
        if (ProfileSession.alignmentResult.size >= 2) {
            val verts = ProfileSession.alignmentResult
            alignment = verts
            val step = intervalText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.01) ?: 10.0
            val start = startText.replace(',', '.').toDoubleOrNull() ?: 0.0
            val ss = surfaces.mapNotNull { ProfileEngine.buildSurface(it.name, it.points) }
            if (ss.isNotEmpty()) {
                result = withContext(Dispatchers.Default) { ProfileEngine.sample(verts, ss, step, start) }
                message = "الایمنت دریافت شد؛ پروفیل محاسبه شد"
            } else message = "ابتدا حداقل یک سطح وارد کن"
            ProfileSession.clearAlignmentResult()
        }
    }

    fun recalc() {
        val verts = alignment
        if (verts.size >= 2 && surfaces.isNotEmpty()) {
            val step = intervalText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.01) ?: 10.0
            val start = startText.replace(',', '.').toDoubleOrNull() ?: 0.0
            val ss = surfaces.mapNotNull { ProfileEngine.buildSurface(it.name, it.points) }
            result = ProfileEngine.sample(verts, ss, step, start)
        }
    }

    val hScale = horizontalScaleText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0
    val vScale = verticalScaleText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0
    val scale = ProfileScale(hScale, vScale)

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("پروفیل طولی") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "بازگشت") } },
                actions = {
                    IconButton(onClick = { showScaleDialog = true }) { Icon(Icons.Filled.Straighten, "مقیاس") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تنظیمات پروفیل", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Button(onClick = { pickSurface.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("ورود سطوح")
                        }
                    }
                    surfaces.forEachIndexed { i, s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Layers, null, tint = color)
                            Spacer(Modifier.width(6.dp))
                            Text("${s.name} — ${s.points.size} نقطه", color = TextPrimary, modifier = Modifier.weight(1f))
                            IconButton(onClick = { ProfileSession.updateSurfaces(surfaces.filterIndexed { idx, _ -> idx != i }) }) { Icon(Icons.Filled.Delete, "حذف") }
                        }
                    }
                    if (surfaces.isEmpty()) Text("هنوز سطحی وارد نشده است.", color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(intervalText, { intervalText = it; recalc() }, label = { Text("فاصله ایستگاه (m)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(startText, { startText = it; recalc() }, label = { Text("کیلومتر شروع") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Text("مقیاس ترسیم: طولی 1:${hScale.toInt()}  |  عرضی 1:${vScale.toInt()}  |  ضریب ترسیم عرضی = ${"%.2f".format(scale.verticalExaggeration)}", color = TextSecondary, fontSize = 12.sp)
                }
            }

            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الایمنت", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            if (surfaces.isEmpty()) message = "ابتدا سطح وارد کن"
                            else { ProfileSession.beginAlignment(surfaces); onOpenMap("alignment") }
                        }) {
                            Icon(Icons.Filled.Route, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("رسم الایمنت")
                        }
                    }
                    if (alignment.size >= 2) {
                        Text("${alignment.size} رأس — طول ${"%.2f".format(ProfileEngine.polylineLength(alignment))} متر", color = TextSecondary)
                    } else Text("الایمنت را از روی خطوط نقشه انتخاب کن.", color = TextSecondary)
                }
            }

            result?.let { r ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("نمایش پروفیل", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${r.rows.size} ایستگاه", color = TextSecondary, fontSize = 12.sp)
                        }
                        ProfileChart(r, surfaces.map { it.name }, drawingZoom, drawingPan) { z, p ->
                            drawingZoom = z; drawingPan = p
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { showTable = !showTable }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.TableChart, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("جدول ایستگاه‌ها")
                            }
                            OutlinedButton(onClick = {
                                val ss = surfaces.mapNotNull { ProfileEngine.buildSurface(it.name, it.points) }
                                val csv = ProfileEngine.csv(r, ss)
                                FileExport.exportTextToDocuments(context, "profile.csv", csv, "text/csv")
                                message = "CSV ذخیره شد"
                            }, modifier = Modifier.weight(1f)) { Text("خروجی CSV") }
                            Button(onClick = { showDxfSettings = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Draw, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("ترسیم DXF")
                            }
                        }
                    }
                }
                if (showTable) ProfileTable(r, surfaces.mapNotNull { ProfileEngine.buildSurface(it.name, it.points) })
                if (r.warnings.isNotEmpty()) {
                    Card { Column(Modifier.padding(10.dp)) { r.warnings.forEach { Text("⚠ $it", color = Color(0xFFFFB74D), fontSize = 12.sp) } } }
                }
            }

            if (message.isNotBlank()) Text(message, color = color, fontSize = 12.sp)
        }
    }

    if (showScaleDialog) {
        AlertDialog(
            onDismissRequest = { showScaleDialog = false },
            title = { Text("مقیاس پروفیل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(horizontalScaleText, { horizontalScaleText = it }, label = { Text("مقیاس طولی 1:") }, singleLine = true)
                    OutlinedTextField(verticalScaleText, { verticalScaleText = it }, label = { Text("مقیاس عرضی 1:") }, singleLine = true)
                    val hh = horizontalScaleText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(1.0) ?: 1000.0
                    val vv = verticalScaleText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0
                    Text("ضریب ترسیم پروفیل (بزرگنمایی عمودی): ${"%.2f".format(hh / vv)}", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = { TextButton(onClick = { showScaleDialog = false }) { Text("تأیید") } }
        )
    }

    if (showDxfSettings) {
        val currentResult = result
        AlertDialog(
            onDismissRequest = { showDxfSettings = false },
            title = { Text("تنظیمات ترسیم DXF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dxfStartChainage,
                        onValueChange = { dxfStartChainage = it },
                        label = { Text("کیلومتر شروع الایمنت") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dxfStationTextSize,
                        onValueChange = { dxfStationTextSize = it },
                        label = { Text("سایز متن کیلومترها") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dxfProfileTextSize,
                        onValueChange = { dxfProfileTextSize = it },
                        label = { Text("سایز متن پروفیل طولی") },
                        singleLine = true
                    )
                    Text(
                        "خط الایمنت + کیلومتربندی عمود + همه سطوح پروفیل",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val rNow = currentResult ?: return@TextButton
                    val ss = surfaces.mapNotNull { ProfileEngine.buildSurface(it.name, it.points) }
                    val startCh = dxfStartChainage.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val stSize = dxfStationTextSize.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.2) ?: 1.0
                    val prSize = dxfProfileTextSize.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.2) ?: 1.5
                    val step = intervalText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.01) ?: 10.0
                    val model = ProfileEngine.toDxfModel(
                        result = rNow,
                        surfaces = ss,
                        scale = scale,
                        layerName = "PROFILE",
                        alignment = alignment,
                        stationInterval = step,
                        startChainage = startCh,
                        stationTextSize = stSize,
                        profileTextSize = prSize
                    )
                    ProfileSession.beginPlacement(model.toDxfText(), "profile.dxf")
                    showDxfSettings = false
                    onOpenMap("placement")
                }) { Text("ترسیم") }
            },
            dismissButton = {
                TextButton(onClick = { showDxfSettings = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun ProfileChart(
    result: ProfileResult2,
    surfaceNames: List<String>,
    zoom: Float,
    pan: Offset,
    onTransform: (Float, Offset) -> Unit
) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    // zoom/pan را در کلید pointerInput می‌گذاریم تا gesture با مقدار به‌روز کار کند (جلوگیری از کرش/حالت کهنه)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(Color(0xFF111315), RoundedCornerShape(10.dp))
            .pointerInput(zoom, pan) {
                detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                    val safeZoom = zoom.coerceIn(0.4f, 12f).let { if (it.isFinite()) it else 1f }
                    val gz = if (gestureZoom.isFinite() && gestureZoom > 0f) gestureZoom else 1f
                    val nz = (safeZoom * gz).coerceIn(0.4f, 12f)
                    val factor = if (safeZoom > 1e-4f) nz / safeZoom else 1f
                    val npx = centroid.x - (centroid.x - pan.x) * factor + gesturePan.x
                    val npy = centroid.y - (centroid.y - pan.y) * factor + gesturePan.y
                    onTransform(
                        nz,
                        Offset(
                            if (npx.isFinite()) npx.coerceIn(-5000f, 5000f) else 0f,
                            if (npy.isFinite()) npy.coerceIn(-5000f, 5000f) else 0f
                        )
                    )
                }
            }
    ) {
        if (result.rows.size < 2) return@Canvas
        val left = 62f; val right = 14f; val top = 22f; val bottom = 46f
        val plotW = (size.width - left - right).coerceAtLeast(20f)
        val plotH = (size.height - top - bottom).coerceAtLeast(20f)
        val minX = result.rows.first().chainage
        val maxX = result.rows.last().chainage.coerceAtLeast(minX + 1.0)
        val minZ = floor(result.minElevation / 2.0) * 2.0
        val maxZ = ceil(result.maxElevation / 2.0) * 2.0
        val zRange = (maxZ - minZ).coerceAtLeast(1.0)
        fun sx(x: Double) = left + ((x - minX) / (maxX - minX)).toFloat() * plotW
        fun sy(z: Double) = top + ((maxZ - z) / zRange).toFloat() * plotH
        val zSafe = zoom.coerceIn(0.4f, 12f).let { if (it.isFinite()) it else 1f }
        val panSafe = Offset(
            if (pan.x.isFinite()) pan.x else 0f,
            if (pan.y.isFinite()) pan.y else 0f
        )
        fun tx(p: Offset) = Offset(
            left + (p.x - left) * zSafe + panSafe.x,
            top + (p.y - top) * zSafe + panSafe.y
        )

        // station grid — محدود به تعداد معقول
        val maxStations = 200
        result.rows.take(maxStations).forEach { row ->
            val x = tx(Offset(sx(row.chainage), top)).x
            if (!x.isFinite()) return@forEach
            drawLine(Color(0x3344FFFFFF), Offset(x, top), Offset(x, top + plotH), 1f)
            if (result.rows.size <= 80) {
                drawText(
                    textMeasurer,
                    ProfileEngine.stationLabel(row.chainage),
                    topLeft = Offset(x - 22f, size.height - 34f),
                    style = androidx.compose.ui.text.TextStyle(color = Color.LightGray, fontSize = 9.sp)
                )
            }
        }
        val stationStep = if (result.rows.size > 1)
            (result.rows[1].chainage - result.rows[0].chainage).coerceAtLeast(0.01)
        else 10.0
        // گام شبکه ارتفاعی طوری که بیش از ~40 خط نشود
        var verticalGridStep = (stationStep * 2.0).coerceAtLeast(0.5)
        val zSpan = (maxZ - minZ).coerceAtLeast(1.0)
        if (zSpan / verticalGridStep > 40.0) {
            verticalGridStep = zSpan / 40.0
        }
        var z = minZ
        var guard = 0
        while (z <= maxZ + 1e-6 && guard < 50) {
            guard++
            val y = tx(Offset(left, sy(z))).y
            if (y.isFinite()) {
                drawLine(Color(0x3344FFFFFF), Offset(left, y), Offset(left + plotW, y), 1f)
                drawText(
                    textMeasurer,
                    String.format(java.util.Locale.US, "%.2f", z),
                    topLeft = Offset(4f, y - 6f),
                    style = androidx.compose.ui.text.TextStyle(color = Color.LightGray, fontSize = 9.sp)
                )
            }
            z += verticalGridStep
        }
        surfaceNames.forEachIndexed { idx, name ->
            var prev: Offset? = null
            result.rows.forEach { row ->
                val zz = row.elevations[name]
                if (zz == null) { prev = null; return@forEach }
                val p = tx(Offset(sx(row.chainage), sy(zz)))
                if (!p.x.isFinite() || !p.y.isFinite()) { prev = null; return@forEach }
                prev?.let { drawLine(if (idx == 0) Color(0xFF4FC3F7) else Color(0xFFFFB74D), it, p, 3f) }
                prev = p
            }
        }
        drawText(
            textMeasurer,
            "فاصله ایستگاه: ${if (result.rows.size > 1) String.format(java.util.Locale.US, "%.2f", result.rows[1].chainage - result.rows[0].chainage) else "-"} m",
            topLeft = Offset(left, 4f),
            style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp)
        )
    }
}


@Composable
private fun ProfileTable(result: ProfileResult2, surfaces: List<ProfileSurface>) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
            Column {
                Row {
                    Text("ایستگاه", Modifier.width(100.dp), fontWeight = FontWeight.Bold)
                    Text("X", Modifier.width(100.dp), fontWeight = FontWeight.Bold)
                    Text("Y", Modifier.width(100.dp), fontWeight = FontWeight.Bold)
                    surfaces.forEach { Text(it.name, Modifier.width(110.dp), fontWeight = FontWeight.Bold) }
                }
                result.rows.forEach { r ->
                    Row {
                        Text(ProfileEngine.stationLabel(r.chainage), Modifier.width(100.dp), fontSize = 11.sp)
                        Text("%.3f".format(r.x), Modifier.width(100.dp), fontSize = 11.sp)
                        Text("%.3f".format(r.y), Modifier.width(100.dp), fontSize = 11.sp)
                        surfaces.forEach { s -> Text(r.elevations[s.name]?.let { "%.3f".format(it) } ?: "—", Modifier.width(110.dp), fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}
