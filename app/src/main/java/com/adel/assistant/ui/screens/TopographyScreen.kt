package com.adel.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.*
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.ToolPrimary
import java.io.File
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopographyScreen(color: Color = ToolPrimary, onBack: () -> Unit) {
    val context = LocalContext.current
    var points by remember { mutableStateOf(TopographySession.points) }
    var intervalText by remember { mutableStateOf("1") }
    var result by remember { mutableStateOf<TopographyEngine.Result?>(null) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var message by remember { mutableStateOf("") }
    var showInterval by remember { mutableStateOf(points.size >= 3) }

    fun rebuild() {
        val iv = intervalText.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.01) ?: 1.0
        result = TopographyEngine.build(points, iv)
        message = if (result!!.warnings.isEmpty()) "توپوگرافی ساخته شد: ${result!!.triangles.size} مثلث، ${result!!.contours.segments.size} قطعه خط تراز" else result!!.warnings.joinToString("\n")
    }

    LaunchedEffect(Unit) {
        if (points.size >= 3) rebuild()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val name = uri.lastPathSegment ?: "points.dat"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val parsedAll = PointConverter.readBytes(bytes, name).map { VolPoint(it.id, it.x, it.y, it.z, it.code) }
            val tp = parsedAll.filter { codeBase(it.code) == "tp" }
            val parsed = if (tp.size >= 3) tp else parsedAll
            if (parsed.size >= 3) {
                points = parsed
                TopographySession.updatePoints(parsed)
                zoom = 1f; pan = Offset.Zero
                showInterval = true
                rebuild()
            } else message = "حداقل ۳ نقطه معتبر لازم است."
        } catch (e: Exception) { message = "خطا در خواندن فایل: ${e.message}" }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("توپوگرافی") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "بازگشت") } },
                actions = {
                    IconButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.FileOpen, "ورود نقاط") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${points.size} نقطه", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { showInterval = true }) {
                        Icon(Icons.Filled.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("فاصله خطوط تراز")
                    }
                    Spacer(Modifier.width(5.dp))
                    Button(onClick = { rebuild() }, enabled = points.size >= 3) { Text("ترسیم") }
                }
            }

            Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.fillMaxSize().background(Color(0xFFF7F7F7))) {
                    TopographyCanvas(result, zoom, pan) { z, p -> zoom = z; pan = p }
                    Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallFloatingActionButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(20f) }) { Icon(Icons.Filled.Add, null) }
                        SmallFloatingActionButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.2f) }) { Icon(Icons.Filled.Remove, null) }
                        SmallFloatingActionButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Icon(Icons.Filled.CenterFocusStrong, null) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    result?.let {
                        val text = TopographyEngine.exportDxf(it)
                        FileExport.exportTextToDocuments(context, "topography.dxf", text, "application/dxf")
                        message = "DXF توپوگرافی ذخیره شد"
                    }
                }, enabled = result != null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("ترسیم/ذخیره DXF")
                }
                Text("TIN: ${result?.triangles?.size ?: 0}  |  تراز: ${result?.contours?.segments?.size ?: 0}", modifier = Modifier.align(Alignment.CenterVertically), fontSize = 11.sp)
            }
            if (message.isNotBlank()) Text(message, color = color, fontSize = 12.sp)
        }
    }

    if (showInterval) {
        AlertDialog(
            onDismissRequest = { showInterval = false },
            title = { Text("فاصله خطوط تراز") },
            text = { OutlinedTextField(intervalText, { intervalText = it }, label = { Text("فاصله (متر)") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { showInterval = false; if (points.size >= 3) rebuild() }) { Text("تأیید") } },
            dismissButton = { TextButton(onClick = { showInterval = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun TopographyCanvas(
    result: TopographyEngine.Result?,
    zoom: Float,
    pan: Offset,
    onTransform: (Float, Offset) -> Unit
) {
    if (result == null || result.points.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("فایل نقاط را وارد کنید") }
        return
    }
    val pts = result.points
    val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
    val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
    val span = max(maxX - minX, maxY - minY).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTransformGestures { _, panChange, scaleChange, _ ->
            onTransform((zoom * scaleChange).coerceIn(0.2f, 20f), pan + panChange)
        }
    }) {
        val pad = 30f
        val base = min((size.width - 2 * pad) / span, (size.height - 2 * pad) / span)
        val s = base * zoom
        fun mapX(x: Double) = pad + ((x - minX) * s).toFloat() + pan.x
        fun mapY(y: Double) = size.height - pad - ((y - minY) * s).toFloat() + pan.y

        result.triangles.forEach { t ->
            val a=pts[t.a]; val b=pts[t.b]; val c=pts[t.c]
            drawLine(Color(0xFFB0BEC5), Offset(mapX(a.x),mapY(a.y)), Offset(mapX(b.x),mapY(b.y)), 1f)
            drawLine(Color(0xFFB0BEC5), Offset(mapX(b.x),mapY(b.y)), Offset(mapX(c.x),mapY(c.y)), 1f)
            drawLine(Color(0xFFB0BEC5), Offset(mapX(c.x),mapY(c.y)), Offset(mapX(a.x),mapY(a.y)), 1f)
        }
        result.contours.segments.forEach { q ->
            drawLine(Color(0xFF1565C0), Offset(mapX(q.x1),mapY(q.y1)), Offset(mapX(q.x2),mapY(q.y2)), 2f)
        }
        pts.forEach { q ->
            drawCircle(Color(0xFFE53935), 3.5f, Offset(mapX(q.x),mapY(q.y)))
        }
    }
}
