package com.adel.assistant.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private data class Pt(val name: String, val x: Double, val y: Double)

@Composable
fun AreaScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("") }
    var xInput by remember { mutableStateOf("") }
    var yInput by remember { mutableStateOf("") }
    var bulkInput by remember { mutableStateOf("") }
    var points by remember { mutableStateOf(listOf<Pt>()) }
    var result by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun addPoint(name: String, xText: String, yText: String) {
        val x = xText.trim().replace(',', '.').toDoubleOrNull()
        val y = yText.trim().replace(',', '.').toDoubleOrNull()
        if (x == null || y == null) {
            message = "مختصات X و Y را درست وارد کن"
            return
        }
        points = points + Pt(name.trim().ifBlank { (points.size + 1).toString() }, x, y)
        nameInput = ""; xInput = ""; yInput = ""; result = null; message = null
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val imported = parsePoints(text)
                if (imported.isEmpty()) throw IllegalArgumentException("هیچ نقطه قابل تشخیصی پیدا نشد")
                points = imported
                result = null
                message = "${imported.size} نقطه از فایل خوانده شد"
            }.onFailure { message = "خطا در خواندن فایل: ${it.message ?: "نامشخص"}" }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "مساحت و محیط", color = color, onBack = onBack)
        Text("نقاط را به ترتیب دور زمین وارد یا از فایل CSV / TXT / DAT / IDX بخوان.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B6B6B))
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(nameInput, { nameInput = it }, label = { Text("نام") }, modifier = Modifier.weight(.8f), singleLine = true)
            OutlinedTextField(xInput, { xInput = it }, label = { Text("X") }, modifier = Modifier.weight(1.2f), singleLine = true)
            OutlinedTextField(yInput, { yInput = it }, label = { Text("Y") }, modifier = Modifier.weight(1.2f), singleLine = true)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { addPoint(nameInput, xInput, yInput) }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("افزودن")
            }
            OutlinedButton(onClick = { filePicker.launch(arrayOf("text/*", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(4.dp)); Text("خواندن فایل")
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = bulkInput,
            onValueChange = { bulkInput = it },
            label = { Text("ورود چند نقطه‌ای") },
            placeholder = { Text("هر خط: نام X Y   یا   X,Y") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = {
                val imported = parsePoints(bulkInput)
                if (imported.isEmpty()) message = "نقطه‌ای برای اضافه کردن پیدا نشد"
                else { points = points + imported; bulkInput = ""; result = null; message = "${imported.size} نقطه اضافه شد" }
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(4.dp)); Text("افزودن لیست") }
            OutlinedButton(onClick = { points = emptyList(); result = null; message = null }, modifier = Modifier.weight(1f)) { Text("پاک کردن همه") }
        }

        message?.let { Text(it, color = if (it.startsWith("خطا")) MaterialTheme.colorScheme.error else color, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(6.dp))

        if (points.size >= 2) {
            PolygonPreview(points, color, Modifier.fillMaxWidth().height(170.dp))
            Spacer(Modifier.height(6.dp))
        }

        Text("لیست نقاط (${points.size})", style = MaterialTheme.typography.titleSmall)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            itemsIndexed(points, key = { _, p -> "${p.name}-${p.x}-${p.y}" }) { index, point ->
                var name by remember(point) { mutableStateOf(point.name) }
                var x by remember(point) { mutableStateOf(point.x.toString()) }
                var y by remember(point) { mutableStateOf(point.y.toString()) }
                Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${index + 1}", modifier = Modifier.width(22.dp).padding(top = 14.dp))
                        OutlinedTextField(name, { name = it; points = points.toMutableList().also { list -> list[index] = point.copy(name = it) } }, label = { Text("نام") }, modifier = Modifier.weight(.8f), singleLine = true)
                        OutlinedTextField(x, { x = it; it.trim().replace(',', '.').toDoubleOrNull()?.let { v -> points = points.toMutableList().also { list -> list[index] = list[index].copy(x = v) }; result = null } }, label = { Text("X") }, modifier = Modifier.weight(1.1f), singleLine = true)
                        OutlinedTextField(y, { y = it; it.trim().replace(',', '.').toDoubleOrNull()?.let { v -> points = points.toMutableList().also { list -> list[index] = list[index].copy(y = v) }; result = null } }, label = { Text("Y") }, modifier = Modifier.weight(1.1f), singleLine = true)
                        IconButton(onClick = { points = points.toMutableList().also { it.removeAt(index) }; result = null }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (points.size < 3) result = "حداقل ۳ نقطه لازم است"
                else {
                    var signed = 0.0; var perimeter = 0.0
                    points.indices.forEach { i ->
                        val a = points[i]; val b = points[(i + 1) % points.size]
                        signed += a.x * b.y - b.x * a.y
                        perimeter += sqrt((b.x-a.x)*(b.x-a.x) + (b.y-a.y)*(b.y-a.y))
                    }
                    val area = abs(signed) / 2.0
                    result = "مساحت: ${"%.3f".format(area)} مترمربع\nهکتار: ${"%.6f".format(area / 10000.0)} هکتار\nمحیط: ${"%.3f".format(perimeter)} متر"
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()
        ) { Text("محاسبه مساحت و محیط") }
        result?.let { Surface(shape = RoundedCornerShape(10.dp), color = Color.White, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(it, modifier = Modifier.padding(12.dp)) } }
    }
}

@Composable
private fun PolygonPreview(points: List<Pt>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.border(1.dp, Color.LightGray, RoundedCornerShape(10.dp))) {
        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        val dx = max(maxX - minX, 1e-9); val dy = max(maxY - minY, 1e-9)
        val pad = 18.dp.toPx()
        val scale = min((size.width - pad*2) / dx.toFloat(), (size.height - pad*2) / dy.toFloat())
        fun pos(p: Pt) = Offset(pad + ((p.x-minX)*scale).toFloat(), size.height-pad - ((p.y-minY)*scale).toFloat())
        val path = Path(); path.moveTo(pos(points.first()).x, pos(points.first()).y)
        points.drop(1).forEach { p -> path.lineTo(pos(p).x, pos(p).y) }; if (points.size >= 3) path.close()
        drawPath(path, color = color.copy(alpha = .15f))
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
        points.forEach { p -> drawCircle(color, radius = 4.dp.toPx(), center = pos(p)) }
    }
}

private fun parsePoints(text: String): List<Pt> {
    return text.lineSequence().mapNotNull { raw ->
        val line = raw.trim().replace("\uFEFF", "")
        if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) return@mapNotNull null
        val tokens = line.split(Regex("[\\s;]+|,(?=\\s)|,(?=[A-Za-zآ-ی])")).filter { it.isNotBlank() }
        fun num(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
        when {
            tokens.size >= 3 && num(tokens[1]) != null && num(tokens[2]) != null -> Pt(tokens[0], num(tokens[1])!!, num(tokens[2])!!)
            tokens.size >= 2 && num(tokens[0]) != null && num(tokens[1]) != null -> Pt("", num(tokens[0])!!, num(tokens[1])!!)
            else -> {
                val nums = Regex("[-+]?\\d+(?:[.,]\\d+)?").findAll(line).map { it.value.replace(',', '.').toDoubleOrNull() }.filterNotNull().toList()
                if (nums.size >= 2) Pt("", nums[0], nums[1]) else null
            }
        }
    }.mapIndexed { i, p -> if (p.name.isBlank()) p.copy(name = (i + 1).toString()) else p }.toList()
}
