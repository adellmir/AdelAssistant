package com.adel.assistant.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.ContourSet
import com.adel.assistant.data.CutFillCell
import com.adel.assistant.data.VolumeAnalysis
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.PointConverter
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.data.VolPoint
import com.adel.assistant.data.VolumeEngine
import com.adel.assistant.data.VolumeResult
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.ToolPrimary
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private data class SurfaceSlot(
    val name: String = "",
    val points: List<VolPoint> = emptyList(),
    val selectedForCompare: Boolean = false
)

@Composable
fun VolumeScreen(color: Color = ToolPrimary, onBack: () -> Unit) {
    val context = LocalContext.current

    var surfaces by remember {
        mutableStateOf(
            listOf(
                SurfaceSlot("سطح۱", emptyList(), true),
                SurfaceSlot("سطح۲", emptyList(), true)
            )
        )
    }
    var methodTin by remember { mutableStateOf(false) }
    var gridSize by remember { mutableStateOf("1.0") }
    var cutFactor by remember { mutableStateOf("1.0") }
    var fillFactor by remember { mutableStateOf("1.0") }

    var result by remember { mutableStateOf<VolumeResult?>(null) }
    var contours by remember { mutableStateOf<List<ContourSet>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    var boundaryPoints by remember { mutableStateOf<List<VolPoint>>(emptyList()) }
    var cutFillCells by remember { mutableStateOf<List<CutFillCell>>(emptyList()) }
    var boundaryUsed by remember { mutableStateOf<List<VolPoint>>(emptyList()) }
    var showCutFill by remember { mutableStateOf(true) }
    var breaklines by remember { mutableStateOf<List<List<VolPoint>>>(emptyList()) }
    var breaklineStep by remember { mutableStateOf("1.0") }

    // تنظیمات نقشه
    var contourInterval by remember { mutableStateOf("1.0") }
    var fixedColorMode by remember { mutableStateOf(false) }
    var fixedColorHue by remember { mutableStateOf(0.35f) } // سبز
    var showGrid by remember { mutableStateOf(false) }
    var showBoundary by remember { mutableStateOf(true) }
    var showPoints by remember { mutableStateOf(true) }
    var mode3d by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // ویرایشگر نقاط
    var assignIndex by remember { mutableStateOf(-1) } // سطحی که از روی دکمه نقاط باز شده
    var poolPoints by remember { mutableStateOf<List<VolPoint>>(emptyList()) }
    // assignment: pointKey -> set of surface indices
    var assignment by remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }
    var showAssign by remember { mutableStateOf(false) }

    fun pointKey(p: VolPoint) = "${p.id}|${p.x}|${p.y}|${p.z}"

    fun loadUri(uri: Uri, name: String): List<VolPoint> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return emptyList()
        val pts: List<SurveyPoint> = try {
            PointConverter.readBytes(bytes, name)
        } catch (_: Exception) {
            emptyList()
        }
        val fromSurvey = VolumeEngine.fromSurvey(pts)
        return if (fromSurvey.isNotEmpty()) fromSurvey
        else parseSimplePoints(bytes.toString(Charsets.UTF_8))
    }

    val boundaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: ""
            val pts = if (name.endsWith(".dxf", true) || text.contains("SECTION")) {
                VolumeEngine.extractBoundaryFromDxf(text)
            } else {
                parseSimplePoints(text).map { it.copy(z = 0.0) }
            }
            if (pts.size < 3) {
                message = "Boundary معتبر یافت نشد (حداقل ۳ رأس)"
            } else {
                boundaryPoints = pts
                message = "Boundary بارگذاری شد: ${pts.size} رأس"
                result = null
            }
        } catch (e: Exception) {
            message = "خطا Boundary: ${e.message}"
        }
    }

    val breaklinePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: ""
            val lines = if (name.endsWith(".dxf", true) || text.contains("SECTION")) {
                VolumeEngine.extractBreaklinesFromDxf(text)
            } else {
                // TXT: هر خط یک پلی‌لاین نیست؛ همه نقاط به‌ترتیب یک breakline
                val pts = parseSimplePoints(text)
                if (pts.size >= 2) listOf(pts) else emptyList()
            }
            if (lines.isEmpty()) {
                message = "Breakline یافت نشد"
            } else {
                breaklines = lines
                message = "Breakline: ${lines.size} خط / ${lines.sumOf { it.size }} رأس"
                result = null
            }
        } catch (e: Exception) {
            message = "خطا Breakline: ${e.message}"
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "points.txt"
            val pts = loadUri(uri, name)
            if (pts.isEmpty()) {
                message = "نقطه‌ای خوانده نشد"
                return@rememberLauncherForActivityResult
            }
            // ادغام با pool موجود
            val merged = (poolPoints + pts).distinctBy { pointKey(it) }
            poolPoints = merged
            // پیش‌فرض: نقاط جدید به سطح بازشده نسبت داده شوند
            val idx = assignIndex.coerceIn(0, surfaces.lastIndex)
            val next = assignment.toMutableMap()
            pts.forEach { p ->
                val k = pointKey(p)
                val set = (next[k] ?: emptySet()).toMutableSet()
                set.add(idx)
                next[k] = set
            }
            assignment = next
            showAssign = true
            message = "بارگذاری ${pts.size} نقطه — گزینش سطح"
        } catch (e: Exception) {
            message = "خطا: ${e.message}"
        }
    }

    fun toggleCompare(index: Int) {
        val selected = surfaces.mapIndexed { i, s -> i to s.selectedForCompare }.filter { it.second }.map { it.first }
        val currently = surfaces[index].selectedForCompare
        surfaces = surfaces.mapIndexed { i, s ->
            when {
                i == index && currently -> s.copy(selectedForCompare = false)
                i == index && !currently -> {
                    if (selected.size >= 2) {
                        // جایگزینی قدیمی‌ترین انتخاب‌شده
                        s.copy(selectedForCompare = true)
                    } else s.copy(selectedForCompare = true)
                }
                else -> s
            }
        }
        // اگر بیش از ۲ تا شد، یکی غیر از index را خاموش کن
        val after = surfaces.mapIndexed { i, s -> i to s }.filter { it.second.selectedForCompare }
        if (after.size > 2) {
            val drop = after.first { it.first != index }.first
            surfaces = surfaces.mapIndexed { i, s ->
                if (i == drop) s.copy(selectedForCompare = false) else s
            }
        }
    }

    fun applyAssignmentToSurfaces() {
        surfaces = surfaces.mapIndexed { si, s ->
            val pts = poolPoints.filter { p ->
                assignment[pointKey(p)]?.contains(si) == true
            }
            s.copy(points = pts)
        }
        message = surfaces.mapIndexed { i, s -> "${s.name.ifBlank { "سطح${i + 1}" }}: ${s.points.size}" }
            .joinToString(" | ")
        showAssign = false
        result = null
    }

    fun runAnalysis() {
        val picked = surfaces.mapIndexed { i, s -> i to s }.filter { it.second.selectedForCompare }
        if (picked.size != 2) {
            message = "دقیقاً دو سطح را با چک‌باکس برای مقایسه انتخاب کنید"
            return
        }
        val (i1, s1) = picked[0]
        val (i2, s2) = picked[1]
        if (s1.points.size < 3 || s2.points.size < 3) {
            message = "هر سطح انتخاب‌شده حداقل ۳ نقطه نیاز دارد"
            return
        }
        val gs = gridSize.replace(',', '.').toDoubleOrNull() ?: 1.0
        val iv = contourInterval.replace(',', '.').toDoubleOrNull() ?: 1.0
        val cf = cutFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
        val ff = fillFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
        val bnd = boundaryPoints.takeIf { it.size >= 3 }
        busy = true
        try {
            val n1 = s1.name.ifBlank { "سطح${i1 + 1}" }
            val n2 = s2.name.ifBlank { "سطح${i2 + 1}" }
            val blStep = breaklineStep.replace(',', '.').toDoubleOrNull() ?: 1.0
            val analysis: VolumeAnalysis = if (methodTin) {
                VolumeEngine.computeTinAnalysis(
                    s1.points, s2.points, bnd, cf, ff, n1, n2, gs, breaklines, blStep
                )
            } else {
                VolumeEngine.computeGridAnalysis(s1.points, s2.points, gs, bnd, cf, ff, n1, n2)
            }
            result = analysis.result
            cutFillCells = analysis.cells
            boundaryUsed = analysis.boundaryUsed
            contours = listOf(
                VolumeEngine.buildContours(n1, s1.points, iv),
                VolumeEngine.buildContours(n2, s2.points, iv)
            )
            message = "تحلیل انجام شد — ${analysis.cells.size} سلول Cut/Fill"
        } catch (e: Exception) {
            message = "خطا: ${e.message}"
        }
        busy = false
    }

    if (showMap && result != null) {
        VolumeMapView(
            color = color,
            surfaces = surfaces.filter { it.points.isNotEmpty() },
            contours = contours,
            result = result!!,
            cutFillCells = cutFillCells,
            boundaryUsed = boundaryUsed,
            breaklines = breaklines,
            showCutFill = showCutFill,
            contourInterval = contourInterval,
            fixedColorMode = fixedColorMode,
            fixedColorHue = fixedColorHue,
            showGrid = showGrid,
            showBoundary = showBoundary,
            showPoints = showPoints,
            mode3d = mode3d,
            showSettings = showSettings,
            onShowSettings = { showSettings = it },
            onContourInterval = { contourInterval = it },
            onFixedColorMode = { fixedColorMode = it },
            onFixedColorHue = { fixedColorHue = it },
            onShowGrid = { showGrid = it },
            onShowBoundary = { showBoundary = it },
            onShowPoints = { showPoints = it },
            onShowCutFill = { showCutFill = it },
            onMode3d = { mode3d = it },
            onRebuildContours = {
                val iv = contourInterval.replace(',', '.').toDoubleOrNull() ?: 1.0
                contours = surfaces.filter { it.points.isNotEmpty() }.map {
                    VolumeEngine.buildContours(it.name.ifBlank { "سطح" }, it.points, iv)
                }
            },
            onExportDxf = {
                val dxf = VolumeEngine.exportDxf(
                    surfaces = surfaces.filter { it.points.isNotEmpty() }
                        .map { (it.name.ifBlank { "S" }) to it.points },
                    contours = contours,
                    textSizeM = 0.05
                )
                val uri = FileExport.exportTextToDocuments(
                    context, "volume_map.dxf", dxf, "application/dxf"
                )
                message = if (uri != null) "DXF ذخیره شد" else "خطا DXF"
            },
            onBack = { showMap = false }
        )
        return
    }

    if (showAssign) {
        PointAssignScreen(
            color = color,
            surfaces = surfaces,
            pool = poolPoints,
            assignment = assignment,
            onAssignmentChange = { assignment = it },
            onAddSurfaceColumn = {
                surfaces = surfaces + SurfaceSlot("سطح${surfaces.size + 1}")
            },
            onApply = { applyAssignmentToSurfaces() },
            onBack = { showAssign = false },
            pointKey = ::pointKey
        )
        return
    }

    // ---------- صفحه اصلی ----------
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = color)
            }
            Text("احجام و سطوح", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Text(
            "سطح‌ها را بسازید، نقاط را گزینش کنید، دو سطح را تیک بزنید و بررسی کنید",
            color = Color(0xFF9BA888), fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))

        surfaces.forEachIndexed { index, s ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1A1F16),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = s.selectedForCompare,
                        onCheckedChange = { toggleCompare(index) },
                        colors = CheckboxDefaults.colors(checkedColor = color)
                    )
                    OutlinedTextField(
                        value = s.name,
                        onValueChange = { v ->
                            surfaces = surfaces.toMutableList().also {
                                it[index] = s.copy(name = v)
                            }
                        },
                        label = { Text("نام سطح") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            assignIndex = index
                            filePicker.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (s.points.isEmpty()) "نقاط" else "${s.points.size}",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = {
                surfaces = surfaces + SurfaceSlot("سطح${surfaces.size + 1}")
            },
            modifier = Modifier.align(Alignment.Start)
        ) {
            Icon(Icons.Filled.Add, null, tint = color)
            Spacer(Modifier.width(4.dp))
            Text("سطح جدید", color = color)
        }

        if (poolPoints.isNotEmpty()) {
            TextButton(onClick = { showAssign = true }) {
                Text("ویرایش گزینش نقاط (${poolPoints.size})", color = Color(0xFF90CAF9))
            }
        }

        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF1A1F16), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Boundary محدوده محاسبه", color = color, fontWeight = FontWeight.Bold)
                Text(
                    if (boundaryPoints.size >= 3) "${boundaryPoints.size} رأس — دستی/فایل"
                    else "پیش‌فرض: Convex Hull مشترک دو سطح",
                    color = Color(0xFFB0B8A8), fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { boundaryPicker.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("ورود DXF/TXT", fontSize = 12.sp) }
                    if (boundaryPoints.isNotEmpty()) {
                        TextButton(onClick = {
                            boundaryPoints = emptyList()
                            message = "Boundary پاک شد — Convex Hull"
                            result = null
                        }) { Text("پاک", color = Color(0xFFFF8A65)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF1A1F16), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Breakline (لبه / دیواره)", color = color, fontWeight = FontWeight.Bold)
                Text(
                    if (breaklines.isNotEmpty())
                        "${breaklines.size} خط — ${breaklines.sumOf { it.size }} رأس"
                    else "اختیاری — برای TIN روی لبه و دیواره",
                    color = Color(0xFFB0B8A8), fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { breaklinePicker.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("ورود DXF/TXT", fontSize = 12.sp) }
                    OutlinedTextField(
                        breaklineStep, { breaklineStep = it },
                        label = { Text("گام m") },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    if (breaklines.isNotEmpty()) {
                        TextButton(onClick = {
                            breaklines = emptyList()
                            message = "Breakline پاک شد"
                            result = null
                        }) { Text("پاک", color = Color(0xFFFF8A65)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !methodTin, onClick = { methodTin = false }, label = { Text("Grid") })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = methodTin, onClick = { methodTin = true }, label = { Text("TIN") })
            Spacer(Modifier.width(8.dp))
            if (!methodTin) {
                OutlinedTextField(
                    gridSize, { gridSize = it },
                    label = { Text("Grid m") },
                    modifier = Modifier.width(90.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                cutFactor, { cutFactor = it }, label = { Text("ضریب Cut") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                fillFactor, { fillFactor = it }, label = { Text("ضریب Fill") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { runAnalysis() },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(if (busy) "بررسی..." else "بررسی") }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, color = Color(0xFFB0B8A8), fontSize = 12.sp)
        }

        result?.let { r ->
            Spacer(Modifier.height(12.dp))
            Text("جدول احجام", color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1F16), modifier = Modifier.fillMaxWidth()) {
                Column {
                    AnalysisRow("سطح اصلی", r.existingName, Color.White, true)
                    AnalysisRow("سطح دوم", r.designName, Color.White, true)
                    HorizontalDivider(color = Color(0xFF3A4530))
                    AnalysisRow("مساحت مشترک (m²)", fmt(r.areaM2), Color.White)
                    AnalysisRow("خاکبرداری Cut (m³)", fmt(r.cutM3), Color(0xFFE57373))
                    AnalysisRow("خاکریزی Fill (m³)", fmt(r.fillM3), Color(0xFF64B5F6))
                    AnalysisRow("خالص Net (m³)", fmt(r.netM3), Color(0xFFFFD54F))
                    AnalysisRow(
                        "نتیجه احجام خاکی", r.earthworkLabel,
                        when (r.earthworkLabel) {
                            "خاکبرداری" -> Color(0xFFE57373)
                            "خاکریزی" -> Color(0xFF64B5F6)
                            else -> Color(0xFF81C784)
                        },
                        true
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { showMap = true },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Map, null)
                Spacer(Modifier.width(8.dp))
                Text("نمایش نقشه")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val cf = cutFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
                        val ff = fillFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
                        val text = VolumeEngine.reportText("تحلیل", r, cf, ff)
                        FileExport.exportTextToDocuments(context, "volume_analysis.txt", text, "text/plain")
                        message = "گزارش TXT ذخیره شد"
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("گزارش", color = color) }
                OutlinedButton(
                    onClick = {
                        val dxf = VolumeEngine.exportDxf(
                            surfaces.filter { it.points.isNotEmpty() }
                                .map { it.name.ifBlank { "S" } to it.points },
                            contours, 0.05
                        )
                        FileExport.exportTextToDocuments(context, "volume_contour.dxf", dxf, "application/dxf")
                        message = "DXF ذخیره شد"
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("DXF", color = color) }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val cf = cutFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
                    val ff = fillFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
                    val bytes = VolumeEngine.buildVolumePdf(
                        title = "${r.existingName} ↔ ${r.designName}",
                        result = r,
                        cutFactor = cf,
                        fillFactor = ff,
                        boundaryCount = boundaryPoints.size,
                        breaklineCount = breaklines.size
                    )
                    val uri = FileExport.exportBytesToDocuments(
                        context, "volume_report.pdf", bytes, "application/pdf"
                    )
                    message = if (uri != null) "PDF ذخیره شد" else "خطا در PDF"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("خروجی PDF گزارش") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------- گزینش نقاط ----------
@Composable
private fun PointAssignScreen(
    color: Color,
    surfaces: List<SurfaceSlot>,
    pool: List<VolPoint>,
    assignment: Map<String, Set<Int>>,
    onAssignmentChange: (Map<String, Set<Int>>) -> Unit,
    onAddSurfaceColumn: () -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
    pointKey: (VolPoint) -> String
) {
    var codeFilter by remember { mutableStateOf("") }
    var rangeFrom by remember { mutableStateOf("") }
    var rangeTo by remember { mutableStateOf("") }

    val filtered = remember(pool, codeFilter) {
        if (codeFilter.isBlank()) pool
        else pool.filter { it.code.contains(codeFilter, ignoreCase = true) || it.id.contains(codeFilter, ignoreCase = true) }
    }

    fun setRange(surfaceIdx: Int, on: Boolean) {
        val from = rangeFrom.toIntOrNull()
        val to = rangeTo.toIntOrNull()
        val next = assignment.toMutableMap()
        filtered.forEachIndexed { i, p ->
            val rowNum = i + 1
            if (from != null && to != null && rowNum !in from..to) return@forEachIndexed
            val k = pointKey(p)
            val set = (next[k] ?: emptySet()).toMutableSet()
            if (on) set.add(surfaceIdx) else set.remove(surfaceIdx)
            next[k] = set
        }
        onAssignmentChange(next)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = color) }
            Text("گزینش نقاط سطوح", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onApply, colors = ButtonDefaults.buttonColors(containerColor = color)) {
                Text("ثبت")
            }
        }
        OutlinedTextField(
            codeFilter, { codeFilter = it },
            label = { Text("فیلتر کد / شماره") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("خالی = همه") }
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                rangeFrom, { rangeFrom = it }, label = { Text("از ردیف") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                rangeTo, { rangeTo = it }, label = { Text("تا ردیف") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Spacer(Modifier.height(4.dp))
        // هدر ستون‌ها
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            surfaces.forEachIndexed { si, s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                    Text(s.name.ifBlank { "S${si + 1}" }, color = color, fontSize = 10.sp, maxLines = 1)
                    Row {
                        Text("✓", color = Color(0xFF81C784), fontSize = 10.sp, modifier = Modifier.clickable { setRange(si, true) })
                        Text(" ", fontSize = 10.sp)
                        Text("✗", color = Color(0xFFE57373), fontSize = 10.sp, modifier = Modifier.clickable { setRange(si, false) })
                    }
                }
            }
            IconButton(onClick = onAddSurfaceColumn, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, null, tint = color)
            }
            Text("N / X Y Z / کد", color = Color(0xFF9BA888), fontSize = 11.sp)
        }
        HorizontalDivider(color = Color(0xFF3A4530))

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(filtered, key = { _, p -> pointKey(p) }) { index, p ->
                val k = pointKey(p)
                val set = assignment[k] ?: emptySet()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    surfaces.forEachIndexed { si, _ ->
                        Checkbox(
                            checked = si in set,
                            onCheckedChange = { on ->
                                val next = assignment.toMutableMap()
                                val ss = (next[k] ?: emptySet()).toMutableSet()
                                if (on) ss.add(si) else ss.remove(si)
                                next[k] = ss
                                onAssignmentChange(next)
                            },
                            modifier = Modifier.size(42.dp),
                            colors = CheckboxDefaults.colors(checkedColor = color)
                        )
                    }
                    Text(
                        "${index + 1}. ${p.id}  ${fmtPlain(p.x)}  ${fmtPlain(p.y)}  ${fmtPlain(p.z)}  ${p.code}",
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text("${filtered.size} / ${pool.size} نقطه", color = Color(0xFF9BA888), fontSize = 11.sp)
    }
}

// ---------- نقشه ----------
@Composable
private fun VolumeMapView(
    color: Color,
    surfaces: List<SurfaceSlot>,
    contours: List<ContourSet>,
    result: VolumeResult,
    cutFillCells: List<CutFillCell>,
    boundaryUsed: List<VolPoint>,
    breaklines: List<List<VolPoint>> = emptyList(),
    showCutFill: Boolean,
    contourInterval: String,
    fixedColorMode: Boolean,
    fixedColorHue: Float,
    showGrid: Boolean,
    showBoundary: Boolean,
    showPoints: Boolean,
    mode3d: Boolean,
    showSettings: Boolean,
    onShowSettings: (Boolean) -> Unit,
    onContourInterval: (String) -> Unit,
    onFixedColorMode: (Boolean) -> Unit,
    onFixedColorHue: (Float) -> Unit,
    onShowGrid: (Boolean) -> Unit,
    onShowBoundary: (Boolean) -> Unit,
    onShowPoints: (Boolean) -> Unit,
    onShowCutFill: (Boolean) -> Unit,
    onMode3d: (Boolean) -> Unit,
    onRebuildContours: () -> Unit,
    onExportDxf: () -> Unit,
    onBack: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotY by remember { mutableStateOf(0.4f) }
    var rotX by remember { mutableStateOf(0.6f) }

    val allPts = surfaces.flatMap { it.points }
    val bounds = remember(allPts) {
        if (allPts.isEmpty()) doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        else VolumeEngine.bounds(allPts)
    }
    val zMin = allPts.minOfOrNull { it.z } ?: 0.0
    val zMax = allPts.maxOfOrNull { it.z } ?: 1.0

    Column(Modifier.fillMaxSize().background(Background)) {
        // نوار بالا
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1F16))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = color) }
            IconButton(onClick = { onShowSettings(true) }) {
                Icon(Icons.Filled.Settings, null, tint = color)
            }
            Text("نقشه احجام", color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = { onMode3d(!mode3d) }) {
                Text(if (mode3d) "۲ بعدی" else "۳ بعدی", color = color)
            }
            TextButton(onClick = { menuOpen = !menuOpen }) {
                Text(if (menuOpen) "▲" else "▼", color = color)
            }
        }
        if (menuOpen) {
            Surface(color = Color(0xCC1A1F16), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "${result.existingName} ↔ ${result.designName} | ${result.earthworkLabel}",
                        color = color, fontSize = 12.sp
                    )
                    Text(
                        "Cut ${fmt(result.cutM3)} | Fill ${fmt(result.fillM3)} | Net ${fmt(result.netM3)} m³",
                        color = Color.White, fontSize = 12.sp
                    )
                    Row {
                        TextButton(onClick = onExportDxf) { Text("خروجی DXF", color = color) }
                        TextButton(onClick = onRebuildContours) { Text("بازسازی تراز", color = color) }
                    }
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(mode3d) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (mode3d) {
                            rotY += pan.x * 0.005f
                            rotX = (rotX + pan.y * 0.005f).coerceIn(0.15f, 1.4f)
                            scale = (scale * zoom).coerceIn(0.3f, 20f)
                        } else {
                            scale = (scale * zoom).coerceIn(0.2f, 50f)
                            offset += pan
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val minX = bounds[0]; val minY = bounds[1]
                val maxX = bounds[2]; val maxY = bounds[3]
                val dx = (maxX - minX).coerceAtLeast(1.0)
                val dy = (maxY - minY).coerceAtLeast(1.0)
                val baseScale = (min(w, h) * 0.85f) / max(dx, dy).toFloat()

                fun project(x: Double, y: Double, z: Double = 0.0): Offset {
                    val lx = ((x - minX) / dx - 0.5).toFloat()
                    val ly = ((y - minY) / dy - 0.5).toFloat()
                    if (!mode3d) {
                        val sx = w / 2f + (lx * dx.toFloat() * baseScale * scale) + offset.x
                        val sy = h / 2f - (ly * dy.toFloat() * baseScale * scale) + offset.y
                        return Offset(sx, sy)
                    }
                    // ایزومتریک ساده با چرخش
                    val xz = z - zMin
                    val elevScale = (baseScale * scale * 0.3f)
                    val cy = cos(rotY.toDouble()).toFloat()
                    val sy = sin(rotY.toDouble()).toFloat()
                    val cx = cos(rotX.toDouble()).toFloat()
                    val sxr = sin(rotX.toDouble()).toFloat()
                    val x1 = lx * cy - ly * sy
                    val y1 = lx * sy + ly * cy
                    val z1 = (xz / (zMax - zMin + 1e-6)).toFloat()
                    val y2 = y1 * cx - z1 * sxr
                    val px = w / 2f + x1 * dx.toFloat() * baseScale * scale + offset.x
                    val py = h / 2f - y2 * dy.toFloat() * baseScale * scale - z1 * elevScale * 40f + offset.y
                    return Offset(px, py)
                }

                if (showGrid) {
                    val step = max(dx, dy) / 10.0
                    var gx = minX
                    while (gx <= maxX) {
                        drawLine(
                            Color(0x33FFFFFF),
                            project(gx, minY), project(gx, maxY), strokeWidth = 1f
                        )
                        gx += step
                    }
                    var gy = minY
                    while (gy <= maxY) {
                        drawLine(
                            Color(0x33FFFFFF),
                            project(minX, gy), project(maxX, gy), strokeWidth = 1f
                        )
                        gy += step
                    }
                }

                if (showBoundary) {
                    val hull = if (boundaryUsed.size >= 3) boundaryUsed
                    else if (allPts.size >= 3) VolumeEngine.convexHull(allPts)
                    else emptyList()
                    if (hull.size >= 2) {
                        val path = Path()
                        val first = project(hull[0].x, hull[0].y, hull[0].z)
                        path.moveTo(first.x, first.y)
                        for (i in 1 until hull.size) {
                            val p = project(hull[i].x, hull[i].y, hull[i].z)
                            path.lineTo(p.x, p.y)
                        }
                        path.close()
                        drawPath(path, Color(0x88FFEB3B), style = Stroke(width = 2f))
                    }
                }

                // Breakline نارنجی
                for (line in breaklines) {
                    for (i in 0 until line.size - 1) {
                        val a = line[i]; val b = line[i + 1]
                        drawLine(
                            Color(0xFFFF9800),
                            project(a.x, a.y, a.z),
                            project(b.x, b.y, b.z),
                            strokeWidth = 3f
                        )
                    }
                }


                // Breakline
                // (خطوط نارنجی در صورت وجود در contours/surfaces جدا نیست — از state در map پاس نشده)
                // Cut / Fill رنگی
                if (showCutFill && cutFillCells.isNotEmpty()) {
                    val maxAbs = cutFillCells.maxOf { kotlin.math.abs(it.dz) }.coerceAtLeast(1e-6)
                    for (cell in cutFillCells) {
                        val intensity = (kotlin.math.abs(cell.dz) / maxAbs).toFloat().coerceIn(0.15f, 0.75f)
                        val col = if (cell.dz > 0)
                            Color(1f, 0.2f, 0.2f, intensity) // قرمز Cut
                        else
                            Color(0.2f, 0.45f, 1f, intensity) // آبی Fill
                        val hs = cell.size / 2.0
                        val p1 = project(cell.x - hs, cell.y - hs, 0.0)
                        val p2 = project(cell.x + hs, cell.y - hs, 0.0)
                        val p3 = project(cell.x + hs, cell.y + hs, 0.0)
                        val p4 = project(cell.x - hs, cell.y + hs, 0.0)
                        val path = Path()
                        path.moveTo(p1.x, p1.y)
                        path.lineTo(p2.x, p2.y)
                        path.lineTo(p3.x, p3.y)
                        path.lineTo(p4.x, p4.y)
                        path.close()
                        drawPath(path, col)
                    }
                }

                // تراز
                contours.forEachIndexed { ci, cs ->
                    for (seg in cs.segments) {
                        val t = if (zMax > zMin) ((seg.z - zMin) / (zMax - zMin)).toFloat().coerceIn(0f, 1f) else 0.5f
                        val col = if (fixedColorMode) {
                            hsvColor(fixedColorHue * 360f, 0.7f, 0.9f)
                        } else {
                            // از آبی (پست) تا قرمز (بلند) — نزدیک استاندارد هیپسومتریک ساده
                            hsvColor(240f - t * 240f, 0.75f, 0.95f)
                        }
                        drawLine(
                            col,
                            project(seg.x1, seg.y1, seg.z),
                            project(seg.x2, seg.y2, seg.z),
                            strokeWidth = 2f
                        )
                    }
                }

                if (showPoints) {
                    val pointColors = listOf(
                        Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784),
                        Color(0xFFFFD54F), Color(0xFFBA68C8)
                    )
                    surfaces.forEachIndexed { si, s ->
                        val pc = pointColors[si % pointColors.size]
                        for (p in s.points) {
                            val o = project(p.x, p.y, p.z)
                            drawCircle(pc, radius = 4f, center = o)
                        }
                    }
                }
            }
        }

        Text(
            if (mode3d) "۳بعدی: دو انگشت چرخش/زوم" else "۲بعدی: دو انگشت جابجایی/زوم",
            color = Color(0xFF9BA888), fontSize = 11.sp,
            modifier = Modifier.padding(8.dp)
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { onShowSettings(false) },
            title = { Text("تنظیمات نقشه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        contourInterval, onContourInterval,
                        label = { Text("فاصله خطوط تراز (m)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Text("رنگ تراز", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(fixedColorMode, onFixedColorMode)
                        Text("رنگ ثابت")
                    }
                    if (fixedColorMode) {
                        Text("فام رنگ: ${(fixedColorHue * 360).toInt()}°", fontSize = 12.sp)
                        Slider(fixedColorHue, onFixedColorHue, valueRange = 0f..1f)
                    } else {
                        Text("رنگ‌بندی ارتفاعی (پست→بلند)", fontSize = 12.sp, color = Color.Gray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showGrid, onShowGrid); Text("شبکه‌بندی")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showBoundary, onShowBoundary); Text("Boundary")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showPoints, onShowPoints); Text("نقاط به تفکیک سطح")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(showCutFill, onShowCutFill); Text("رنگ Cut/Fill")
                    }
                    Text("قرمز=خاکبرداری  آبی=خاکریزی", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRebuildContours()
                    onShowSettings(false)
                }) { Text("اعمال") }
            },
            dismissButton = {
                TextButton(onClick = { onShowSettings(false) }) { Text("بستن") }
            }
        )
    }
}

@Composable
private fun AnalysisRow(label: String, value: String, valueColor: Color, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFB0B8A8), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value, color = valueColor, fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}


private fun hsvColor(hueDeg: Float, sat: Float, value: Float): Color {
    val h = ((hueDeg % 360f) + 360f) % 360f
    val c = value * sat
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = value - c
    val rp: Float
    val gp: Float
    val bp: Float
    when {
        h < 60f -> { rp = c; gp = x; bp = 0f }
        h < 120f -> { rp = x; gp = c; bp = 0f }
        h < 180f -> { rp = 0f; gp = c; bp = x }
        h < 240f -> { rp = 0f; gp = x; bp = c }
        h < 300f -> { rp = x; gp = 0f; bp = c }
        else -> { rp = c; gp = 0f; bp = x }
    }
    return Color(red = rp + m, green = gp + m, blue = bp + m, alpha = 1f)
}

private fun fmt(v: Double) = String.format(Locale.US, "%,.3f", v)
private fun fmtPlain(v: Double) = String.format(Locale.US, "%.3f", v)

private fun parseSimplePoints(text: String): List<VolPoint> {
    val out = mutableListOf<VolPoint>()
    text.lineSequence().forEachIndexed { idx, raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed
        val parts = line.split(',', ';', '\t', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 3) return@forEachIndexed
        fun d(s: String) = s.replace(',', '.').toDoubleOrNull()
        when {
            parts.size >= 5 && d(parts[1]) != null && d(parts[2]) != null && d(parts[3]) != null -> {
                // N X Y Z D
                out.add(VolPoint(parts[0], d(parts[1])!!, d(parts[2])!!, d(parts[3])!!, parts.getOrElse(4) { "" }))
            }
            parts.size >= 4 && d(parts[1]) != null && d(parts[2]) != null && d(parts[3]) != null -> {
                val a = d(parts[0]); val b = d(parts[1])!!; val c = d(parts[2])!!; val e = d(parts[3])
                if (a != null && e != null && kotlin.math.abs(a) > 1000) {
                    out.add(VolPoint(parts.getOrElse(3) { "P$idx" }, a, b, c, ""))
                } else {
                    out.add(VolPoint(parts[0], b, c, e ?: 0.0, ""))
                }
            }
            parts.size >= 3 && d(parts[0]) != null && d(parts[1]) != null && d(parts[2]) != null -> {
                out.add(VolPoint("P$idx", d(parts[0])!!, d(parts[1])!!, d(parts[2])!!, parts.getOrElse(3) { "" }))
            }
        }
    }
    return out
}
