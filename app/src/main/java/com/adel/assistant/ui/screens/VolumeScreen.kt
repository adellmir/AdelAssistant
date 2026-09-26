package com.adel.assistant.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import com.adel.assistant.data.ContourSet
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.PointConverter
import com.adel.assistant.data.VolPoint
import com.adel.assistant.data.VolumeEngine
import com.adel.assistant.data.VolumeResult
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.ToolPrimary
import java.util.Locale

/**
 * احجام + تراز + تحلیل دو سطح
 * ۱) سطح اصلی (نام + نقاط)
 * ۲) سطح دوم (نام + نقاط)
 * مقایسه → جدول: مساحت مشترک، Cut، Fill، نتیجه خاکی
 * DXF: لایه‌های {نام}-POINTS / -CONTOUR / -LABEL (متن ۵ cm)
 */
@Composable
fun VolumeScreen(color: Color = ToolPrimary, onBack: () -> Unit) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    var primaryName by remember { mutableStateOf("موجود") }
    var secondaryName by remember { mutableStateOf("طراحی") }
    var primary by remember { mutableStateOf<List<VolPoint>>(emptyList()) }
    var secondary by remember { mutableStateOf<List<VolPoint>>(emptyList()) }
    var primaryFile by remember { mutableStateOf("") }
    var secondaryFile by remember { mutableStateOf("") }

    var methodTin by remember { mutableStateOf(false) }
    var gridSize by remember { mutableStateOf("1.0") }
    var contourInterval by remember { mutableStateOf("1.0") }
    var cutFactor by remember { mutableStateOf("1.0") }
    var fillFactor by remember { mutableStateOf("1.0") }
    var calcName by remember { mutableStateOf("تحلیل احجام") }

    var result by remember { mutableStateOf<VolumeResult?>(null) }
    var contours by remember { mutableStateOf<List<ContourSet>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var importTarget by remember { mutableStateOf("primary") }

    fun loadUri(uri: Uri, name: String): List<VolPoint> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return emptyList()
        val pts = try {
            PointConverter.readBytes(bytes, name)
        } catch (_: Exception) {
            emptyList()
        }
        val fromSurvey = VolumeEngine.fromSurvey(pts)
        return fromSurvey.ifEmpty { parseSimplePoints(bytes.toString(Charsets.UTF_8)) }
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
            if (importTarget == "primary") {
                primary = pts
                primaryFile = name
                message = "سطح اصلی «$primaryName»: ${pts.size} نقطه"
            } else {
                secondary = pts
                secondaryFile = name
                message = "سطح دوم «$secondaryName»: ${pts.size} نقطه"
            }
            result = null
            contours = emptyList()
        } catch (e: Exception) {
            message = "خطا: ${e.message}"
        }
    }

    fun runAnalysis() {
        if (primary.size < 3 || secondary.size < 3) {
            message = "هر سطح حداقل ۳ نقطه نیاز دارد"
            return
        }
        val gs = gridSize.replace(',', '.').toDoubleOrNull() ?: 1.0
        val iv = contourInterval.replace(',', '.').toDoubleOrNull() ?: 1.0
        val cf = cutFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
        val ff = fillFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
        busy = true
        message = "در حال تحلیل..."
        try {
            val r = if (methodTin) {
                VolumeEngine.computeTin(
                    primary, secondary, null, cf, ff, primaryName, secondaryName
                )
            } else {
                VolumeEngine.computeGrid(
                    primary, secondary, gs, null, cf, ff, primaryName, secondaryName
                )
            }
            result = r
            val c1 = VolumeEngine.buildContours(primaryName, primary, iv)
            val c2 = VolumeEngine.buildContours(secondaryName, secondary, iv)
            contours = listOf(c1, c2)
            message = "تحلیل انجام شد — تراز: ${c1.segments.size + c2.segments.size} قطعه"
        } catch (e: Exception) {
            message = "خطا: ${e.message}"
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
            Text("احجام و تراز", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Text(
            "سطح اصلی → سطح دوم → تحلیل Cut/Fill + خطوط تراز",
            color = Color(0xFF9BA888), fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            calcName, { calcName = it },
            label = { Text("نام محاسبه") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))

        // ---- سطح اصلی ----
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1F16), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("۱. سطح اصلی", color = color, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    primaryName, { primaryName = it },
                    label = { Text("نام سطح اصلی") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    if (primary.isEmpty()) "نقاط فراخوانی نشده"
                    else "$primaryFile — ${primary.size} نقطه",
                    color = Color(0xFFB0B8A8), fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            importTarget = "primary"
                            filePicker.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) { Text("فراخوانی نقاط") }
                    if (primary.isNotEmpty()) {
                        TextButton(onClick = {
                            primary = emptyList(); primaryFile = ""; result = null
                        }) { Text("پاک", color = Color(0xFFFF8A65)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---- سطح دوم ----
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1F16), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("۲. سطح دوم (مقایسه)", color = color, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    secondaryName, { secondaryName = it },
                    label = { Text("نام سطح دوم") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    if (secondary.isEmpty()) "نقاط فراخوانی نشده"
                    else "$secondaryFile — ${secondary.size} نقطه",
                    color = Color(0xFFB0B8A8), fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            importTarget = "secondary"
                            filePicker.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) { Text("فراخوانی نقاط") }
                    if (secondary.isNotEmpty()) {
                        TextButton(onClick = {
                            secondary = emptyList(); secondaryFile = ""; result = null
                        }) { Text("پاک", color = Color(0xFFFF8A65)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("پارامترها", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !methodTin,
                onClick = { methodTin = false },
                label = { Text("Grid") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = methodTin,
                onClick = { methodTin = true },
                label = { Text("TIN") }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                gridSize, { gridSize = it },
                label = { Text("Grid (m)") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = numKb, enabled = !methodTin
            )
            OutlinedTextField(
                contourInterval, { contourInterval = it },
                label = { Text("فاصله تراز (m)") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = numKb
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                cutFactor, { cutFactor = it },
                label = { Text("ضریب Cut") },
                modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
            )
            OutlinedTextField(
                fillFactor, { fillFactor = it },
                label = { Text("ضریب Fill") },
                modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
            )
        }
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { runAnalysis() },
            enabled = !busy && primary.size >= 3 && secondary.size >= 3,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(if (busy) "تحلیل..." else "تحلیل سطوح")
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, color = Color(0xFFB0B8A8), fontSize = 12.sp)
        }

        result?.let { r ->
            Spacer(Modifier.height(12.dp))
            Text("جدول تحلیل", color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1F16), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(0.dp)) {
                    AnalysisRow("سطح اصلی", r.existingName, Color.White, bold = true)
                    AnalysisRow("سطح دوم", r.designName, Color.White, bold = true)
                    AnalysisRow("روش", r.method, Color(0xFFB0B8A8))
                    HorizontalDivider(color = Color(0xFF3A4530))
                    AnalysisRow("مساحت منطقه مشترک (m²)", fmt(r.areaM2), Color.White)
                    AnalysisRow("حجم خاکبرداری Cut (m³)", fmt(r.cutM3), Color(0xFFE57373))
                    AnalysisRow("حجم خاکریزی Fill (m³)", fmt(r.fillM3), Color(0xFF64B5F6))
                    AnalysisRow("خالص Net (m³)", fmt(r.netM3), Color(0xFFFFD54F))
                    HorizontalDivider(color = Color(0xFF3A4530))
                    AnalysisRow(
                        "نتیجه احجام خاکی",
                        r.earthworkLabel,
                        when (r.earthworkLabel) {
                            "خاکبرداری" -> Color(0xFFE57373)
                            "خاکریزی" -> Color(0xFF64B5F6)
                            else -> Color(0xFF81C784)
                        },
                        bold = true
                    )
                    AnalysisRow("نقاط اصلی / دوم", "${r.existingCount} / ${r.designCount}", Color(0xFF9BA888))
                    if (r.gridSize != null)
                        AnalysisRow("اندازه شبکه", "${fmt(r.gridSize)} m", Color(0xFF9BA888))
                    AnalysisRow("ΔZ min / max / avg",
                        "${fmt(r.minDz)} / ${fmt(r.maxDz)} / ${fmt(r.avgDz)}", Color(0xFF9BA888))
                }
            }

            if (r.warnings.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                r.warnings.forEach {
                    Text("• $it", color = Color(0xFFFFB74D), fontSize = 11.sp)
                }
            }

            if (contours.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    contours.joinToString(" | ") {
                        "${it.surfaceName}: ${it.segments.size} خط تراز، ${it.labels.size} برچسب"
                    },
                    color = Color(0xFF9BA888), fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val cf = cutFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
                        val ff = fillFactor.replace(',', '.').toDoubleOrNull() ?: 1.0
                        val text = VolumeEngine.reportText(calcName, r, cf, ff)
                        val uri = FileExport.exportTextToDocuments(
                            context, "volume_analysis.txt", text, "text/plain"
                        )
                        message = if (uri != null) "گزارش ذخیره شد" else "خطا در ذخیره"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.weight(1f)
                ) { Text("گزارش") }

                Button(
                    onClick = {
                        val dxf = VolumeEngine.exportDxf(
                            surfaces = listOf(
                                primaryName to primary,
                                secondaryName to secondary
                            ),
                            contours = contours,
                            textSizeM = 0.05
                        )
                        val safe = calcName.replace(Regex("[^A-Za-z0-9_\\-\\u0600-\\u06FF]"), "_")
                            .ifBlank { "volume" }
                        val uri = FileExport.exportTextToDocuments(
                            context, "${safe}_contour.dxf", dxf, "application/dxf"
                        )
                        message = if (uri != null)
                            "DXF ذخیره شد\nلایه‌ها: $primaryName-CONTOUR/LABEL و $secondaryName-..."
                        else "خطا در DXF"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                    modifier = Modifier.weight(1f)
                ) { Text("DXF تراز") }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val csv = buildString {
                        appendLine("item,value")
                        appendLine("سطح اصلی,${r.existingName}")
                        appendLine("سطح دوم,${r.designName}")
                        appendLine("مساحت مشترک m2,${fmtPlain(r.areaM2)}")
                        appendLine("خاکبرداری m3,${fmtPlain(r.cutM3)}")
                        appendLine("خاکریزی m3,${fmtPlain(r.fillM3)}")
                        appendLine("خالص m3,${fmtPlain(r.netM3)}")
                        appendLine("نتیجه,${r.earthworkLabel}")
                    }
                    val uri = FileExport.exportTextToDocuments(
                        context, "volume_table.csv", csv, "text/csv"
                    )
                    message = if (uri != null) "جدول CSV ذخیره شد" else "خطا"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                modifier = Modifier.fillMaxWidth()
            ) { Text("خروجی جدول CSV") }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "لایه‌های DXF برای هر سطح:\n" +
                "  {نام}-POINTS  نقاط\n" +
                "  {نام}-CONTOUR خطوط تراز\n" +
                "  {نام}-LABEL  ارتفاع تراز (سایز ۵ cm)\n" +
                "ΔZ = Z اصلی − Z دوم  | مثبت=خاکبرداری | منفی=خاکریزی",
            color = Color(0xFF7A8570), fontSize = 11.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AnalysisRow(
    label: String,
    value: String,
    valueColor: Color,
    bold: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFB0B8A8), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
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
            parts.size >= 4 && d(parts[1]) != null && d(parts[2]) != null && d(parts[3]) != null -> {
                val a = d(parts[0]); val b = d(parts[1])!!; val c = d(parts[2])!!; val e = d(parts[3])
                if (a != null && e != null && kotlin.math.abs(a) > 1000) {
                    out.add(VolPoint(parts.getOrElse(3) { "P$idx" }, a, b, c))
                } else {
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
