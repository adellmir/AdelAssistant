package com.adel.assistant.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.PointConverter
import com.adel.assistant.data.VolPoint
import com.adel.assistant.data.VolumeEngine
import com.adel.assistant.data.VolumeResult
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.ToolPrimary
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

/**
 * محاسبه احجام خاکبرداری / خاکریزی
 * سطح موجود (Existing) و سطح طراحی (Design)
 * روش Grid (پیشنهادی برای سرعت) و TIN (دقیق‌تر)
 */
@Composable
fun VolumeScreen(color: Color = ToolPrimary, onBack: () -> Unit) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    var existing by remember { mutableStateOf<List<VolPoint>>(emptyList()) }
    var design by remember { mutableStateOf<List<VolPoint>>(emptyList()) }
    var existingName by remember { mutableStateOf("") }
    var designName by remember { mutableStateOf("") }

    var methodTin by remember { mutableStateOf(false) } // false=Grid true=TIN
    var gridSize by remember { mutableStateOf("1.0") }
    var cutFactor by remember { mutableStateOf("1.0") }
    var fillFactor by remember { mutableStateOf("1.0") }
    var calcName by remember { mutableStateOf("محاسبه احجام") }

    var result by remember { mutableStateOf<VolumeResult?>(null) }
    var resultTin by remember { mutableStateOf<VolumeResult?>(null) }
    var resultGrid by remember { mutableStateOf<VolumeResult?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    var importTarget by remember { mutableStateOf("existing") } // existing | design

    fun loadUri(uri: Uri, name: String): List<VolPoint> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return emptyList()
        val pts = try {
            PointConverter.readBytes(bytes, name)
        } catch (_: Exception) {
            // fallback simple CSV/TXT
            val text = bytes.toString(Charsets.UTF_8)
            parseSimplePoints(text)
        }
        return VolumeEngine.fromSurvey(pts).ifEmpty {
            parseSimplePoints(bytes.toString(Charsets.UTF_8))
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
            if (importTarget == "existing") {
                existing = pts
                existingName = name
                message = "سطح موجود: ${pts.size} نقطه"
            } else {
                design = pts
                designName = name
                message = "سطح طراحی: ${pts.size} نقطه"
            }
            result = null
            resultTin = null
            resultGrid = null
        } catch (e: Exception) {
            message = "خطا: ${e.message}"
        }
    }

    fun runCalc() {
        if (existing.size < 3 || design.size < 3) {
            message = "هر سطح حداقل ۳ نقطه نیاز دارد"
            return
        }
        val gs = gridSize.replace(',', '.').toDoubleOrNull() ?: 1.0
        val cf = cutFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
        val ff = fillFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
        busy = true
        message = "در حال محاسبه..."
        try {
            val g = VolumeEngine.computeGrid(existing, design, gs, null, cf, ff)
            resultGrid = g
            val t = if (existing.size <= 3000 && design.size <= 3000) {
                VolumeEngine.computeTin(existing, design, null, cf, ff)
            } else {
                null
            }
            resultTin = t
            result = if (methodTin && t != null) t else g
            message = "محاسبه انجام شد"
        } catch (e: Exception) {
            message = "خطا در محاسبه: ${e.message}"
        }
        busy = false
    }

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
            Text("محاسبه احجام", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Text(
            "سطح موجود (Existing) و سطح طراحی (Design) → Cut / Fill / Net",
            color = Color(0xFF9BA888),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            calcName, { calcName = it },
            label = { Text("نام محاسبه") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        // سطح موجود
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1F16), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("سطح موجود (Existing)", color = color, fontWeight = FontWeight.Bold)
                Text(
                    if (existing.isEmpty()) "فایلی انتخاب نشده"
                    else "$existingName — ${existing.size} نقطه",
                    color = Color(0xFFB0B8A8),
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            importTarget = "existing"
                            filePicker.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) { Text("ورود فایل") }
                    if (existing.isNotEmpty()) {
                        TextButton(onClick = {
                            existing = emptyList()
                            existingName = ""
                            result = null
                        }) { Text("پاک", color = Color(0xFFFF8A65)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // سطح طراحی
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1F16), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("سطح طراحی (Design / کف)", color = color, fontWeight = FontWeight.Bold)
                Text(
                    if (design.isEmpty()) "فایلی انتخاب نشده"
                    else "$designName — ${design.size} نقطه",
                    color = Color(0xFFB0B8A8),
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            importTarget = "design"
                            filePicker.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) { Text("ورود فایل") }
                    if (design.isNotEmpty()) {
                        TextButton(onClick = {
                            design = emptyList()
                            designName = ""
                            result = null
                        }) { Text("پاک", color = Color(0xFFFF8A65)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("روش محاسبه", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !methodTin,
                onClick = { methodTin = false; result = resultGrid ?: result },
                label = { Text("Grid (سریع)") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = methodTin,
                onClick = { methodTin = true; result = resultTin ?: result },
                label = { Text("TIN (دقیق‌تر)") }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                gridSize, { gridSize = it },
                label = { Text("Grid Size (m)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = numKb,
                enabled = !methodTin
            )
            OutlinedTextField(
                cutFactor, { cutFactor = it },
                label = { Text("ضریب Cut") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = numKb
            )
            OutlinedTextField(
                fillFactor, { fillFactor = it },
                label = { Text("ضریب Fill") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = numKb
            )
        }
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { runCalc() },
            enabled = !busy && existing.size >= 3 && design.size >= 3,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(if (busy) "محاسبه..." else "محاسبه Cut / Fill")
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, color = Color(0xFFB0B8A8), fontSize = 12.sp)
        }

        result?.let { r ->
            Spacer(Modifier.height(12.dp))
            ResultCard("نتایج ${r.method}", color, listOf(
                "خاکبرداری Cut (m³)" to r.cutM3,
                "خاکریزی Fill (m³)" to r.fillM3,
                "خالص Net (m³)" to r.netM3,
                "مساحت (m²)" to r.areaM2,
                "حداقل ΔZ (m)" to r.minDz,
                "حداکثر ΔZ (m)" to r.maxDz,
                "میانگین ΔZ (m)" to r.avgDz
            ), extra = buildString {
                append("نقاط موجود: ${r.existingCount} | طراحی: ${r.designCount}\n")
                append(if (r.gridSize != null) "شبکه: ${r.gridSize} m | " else "")
                append("تعداد واحد: ${r.cellOrTriCount}")
            })
            if (r.warnings.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("هشدارها:", color = Color(0xFFFFB74D), fontSize = 12.sp)
                r.warnings.forEach {
                    Text("• $it", color = Color(0xFFFFB74D), fontSize = 11.sp)
                }
            }

            // مقایسه دو روش اگر هر دو موجود
            val g = resultGrid
            val t = resultTin
            if (g != null && t != null) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1F16), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("مقایسه TIN و Grid", color = color, fontWeight = FontWeight.Bold)
                        Text(
                            "Cut: TIN ${fmt(t.cutM3)} | Grid ${fmt(g.cutM3)}",
                            color = Color.White, fontSize = 12.sp
                        )
                        Text(
                            "Fill: TIN ${fmt(t.fillM3)} | Grid ${fmt(g.fillM3)}",
                            color = Color.White, fontSize = 12.sp
                        )
                        val dCut = if (g.cutM3 > 1e-6) absPct(t.cutM3, g.cutM3) else 0.0
                        val dFill = if (g.fillM3 > 1e-6) absPct(t.fillM3, g.fillM3) else 0.0
                        Text(
                            "اختلاف نسبی Cut ≈ ${fmt(dCut)}%  Fill ≈ ${fmt(dFill)}%",
                            color = Color(0xFF9BA888), fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val cf = cutFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
                        val ff = fillFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
                        val text = VolumeEngine.reportText(calcName, r, cf, ff)
                        val uri = FileExport.exportTextToDocuments(
                            context,
                            "volume_report.txt",
                            text,
                            "text/plain"
                        )
                        message = if (uri != null) "گزارش ذخیره شد" else "خطا در ذخیره"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.weight(1f)
                ) { Text("خروجی گزارش") }
                Button(
                    onClick = {
                        val csv = buildString {
                            appendLine("name,method,existing_n,design_n,cut_m3,fill_m3,net_m3,area_m2,min_dz,max_dz,avg_dz,units,grid_m")
                            appendLine(
                                listOf(
                                    "\"$calcName\"", r.method, r.existingCount, r.designCount,
                                    fmt(r.cutM3), fmt(r.fillM3), fmt(r.netM3), fmt(r.areaM2),
                                    fmt(r.minDz), fmt(r.maxDz), fmt(r.avgDz), r.cellOrTriCount,
                                    r.gridSize?.let { fmt(it) } ?: ""
                                ).joinToString(",")
                            )
                        }
                        val uri = FileExport.exportTextToDocuments(
                            context, "volume_result.csv", csv, "text/csv"
                        )
                        message = if (uri != null) "CSV ذخیره شد" else "خطا در CSV"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                    modifier = Modifier.weight(1f)
                ) { Text("خروجی CSV") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "ΔZ = Z موجود − Z طراحی  |  مثبت = خاکبرداری (Cut)  |  منفی = خاکریزی (Fill)\n" +
                "Boundary پیش‌فرض: Convex Hull مشترک دو سطح\n" +
                "فرمت فایل: CSV/TXT/DAT با X,Y,Z یا N,X,Y,Z",
            color = Color(0xFF7A8570),
            fontSize = 11.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultCard(
    title: String,
    color: Color,
    rows: List<Pair<String, Double>>,
    extra: String
) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1F16), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = color, fontWeight = FontWeight.Bold)
            Text(extra, color = Color(0xFF9BA888), fontSize = 11.sp)
            HorizontalDivider(color = Color(0xFF3A4530))
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = Color(0xFFB0B8A8), fontSize = 13.sp)
                    Text(
                        fmt(value),
                        color = when {
                            label.contains("Cut") -> Color(0xFFE57373)
                            label.contains("Fill") -> Color(0xFF64B5F6)
                            label.contains("Net") -> Color(0xFFFFD54F)
                            else -> Color.White
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private fun fmt(v: Double) = String.format(Locale.US, "%,.3f", v)

private fun absPct(a: Double, b: Double): Double {
    if (kotlin.math.abs(b) < 1e-9) return 0.0
    return kotlin.math.abs(a - b) / kotlin.math.abs(b) * 100.0
}

private fun parseSimplePoints(text: String): List<VolPoint> {
    val out = mutableListOf<VolPoint>()
    text.lineSequence().forEachIndexed { idx, raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed
        val parts = line.split(',', ';', '\t', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 3) return@forEachIndexed
        // try N,X,Y,Z or X,Y,Z or X,Y,Z,ID
        fun d(s: String) = s.replace(',', '.').toDoubleOrNull()
        when {
            parts.size >= 4 && d(parts[1]) != null && d(parts[2]) != null && d(parts[3]) != null -> {
                // N X Y Z  OR  X Y Z ID
                val a = d(parts[0])
                val b = d(parts[1])!!
                val c = d(parts[2])!!
                val e = d(parts[3])
                if (a != null && e != null && kotlin.math.abs(a) > 1000) {
                    // X Y Z ID
                    out.add(VolPoint(parts.getOrElse(3) { "P$idx" }, a, b, c))
                } else {
                    // N X Y Z
                    out.add(VolPoint(parts[0], b, c, e ?: 0.0))
                }
            }
            parts.size >= 3 && d(parts[0]) != null && d(parts[1]) != null && d(parts[2]) != null -> {
                out.add(VolPoint("P$idx", d(parts[0])!!, d(parts[1])!!, d(parts[2])!!))
            }
        }
    }
    return out
}
