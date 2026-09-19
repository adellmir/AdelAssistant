package com.adel.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.AlignPair
import com.adel.assistant.data.AlignPoint
import com.adel.assistant.data.AlignTransform
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.GsiParser
import com.adel.assistant.data.GsiPoint
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun AlignScreen(color: Color, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(Background)) {
        ScreenTopBar(title = "الاین / هم‌مختصات", color = color, onBack = onBack)
        TabRow(selectedTabIndex = tab, containerColor = SurfaceColor, contentColor = color) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("مختصاتی") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("نقشه") })
        }
        when (tab) {
            0 -> AlignCoordinateTab(color)
            else -> Box(Modifier.fillMaxSize()) {
                DxfPreviewScreen(color = color, onBack = onBack)
            }
        }
    }
}

@Composable
private fun AlignCoordinateTab(color: Color) {
    val context = LocalContext.current
    var controlPts by remember { mutableStateOf<List<GsiPoint>>(emptyList()) }
    var surveyPts by remember { mutableStateOf<List<GsiPoint>>(emptyList()) }
    var status by remember { mutableStateOf("فایل کنترل و فایل برداشت را بارگذاری کن") }
    var useScale by remember { mutableStateOf(false) }
    var useAverage by remember { mutableStateOf(false) }
    var baseName by remember { mutableStateOf<String?>(null) }
    var loadTarget by remember { mutableStateOf("control") }
    var showSave by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("aligned") }
    var editIdx by remember { mutableStateOf<Int?>(null) }
    var editIsControl by remember { mutableStateOf(true) }
    var eName by remember { mutableStateOf("") }
    var eE by remember { mutableStateOf("") }
    var eN by remember { mutableStateOf("") }
    var eZ by remember { mutableStateOf("") }

    fun loadText(text: String): List<GsiPoint> {
        val a = GsiParser.parseTxt(text)
        if (a.isNotEmpty()) return a
        val b = GsiParser.parseDat(text)
        if (b.isNotEmpty()) return b
        return GsiParser.parse(text)
    }

    val picker = rememberLauncherForActivityResult(com.adel.assistant.data.AdelDocuments.OpenDocumentContract()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val pts = loadText(text)
            if (loadTarget == "control") {
                controlPts = pts
                status = "کنترل: ${pts.size} نقطه"
            } else {
                surveyPts = pts
                status = "برداشت: ${pts.size} نقطه"
            }
            if (pts.isEmpty()) status = "نقطه‌ای از فایل خوانده نشد — فرمت N,X,Y,Z یا X,Y,Z"
        } catch (e: Exception) {
            status = "خطا: ${e.message}"
        }
    }

    fun commonNames(): List<String> {
        val c = controlPts.map { it.name.trim().lowercase() }.toSet()
        return surveyPts.map { it.name.trim() }.filter { it.lowercase() in c }.distinct()
    }

    fun applyAlign() {
        try {
            val ctrlMap = controlPts.associateBy { it.name.trim().lowercase() }
            val pairs = mutableListOf<AlignPair>()
            surveyPts.forEach { s ->
                val c = ctrlMap[s.name.trim().lowercase()] ?: return@forEach
                pairs += AlignPair(
                    AlignPoint(s.name, s.e, s.n, s.z),
                    AlignPoint(c.name, c.e, c.n, c.z)
                )
            }
            if (pairs.size < 2) {
                status = "حداقل ۲ نقطه هم‌نام بین کنترل و برداشت لازم است"
                return
            }
            // نقطه مبنا: انتقال اولیه روی آن
            val ordered = if (baseName != null) {
                val b = pairs.find { it.source.name.equals(baseName, true) }
                if (b != null) listOf(b) + pairs.filter { it !== b } else pairs
            } else pairs
            val params = AlignTransform.compute(ordered, useScale)
            // اختیاری میانگین روی باقیمانده ارتفاع/جابجایی با scale flag already in compute
            surveyPts = surveyPts.map { p ->
                val (x, y) = params.transform(p.e, p.n)
                var z = p.z
                if (useAverage && pairs.isNotEmpty()) {
                    // تصحیح ارتفاع میانگین اختلاف نقاط مشترک
                    val dz = pairs.map { it.target.z - it.source.z }.average()
                    z = p.z + dz
                }
                p.copy(e = x, n = y, z = z)
            }
            status = String.format(
                Locale.US,
                "الاین شد: %d جفت | مقیاس=%.6f | دوران=%.4f° | RMS=%.4f m",
                params.pairCount, params.scale, params.rotationDeg, params.residualRms
            )
        } catch (e: Exception) {
            status = "خطا: ${e.message}"
        }
    }

    fun export(kind: String) {
        if (surveyPts.isEmpty()) {
            status = "نقطه‌ای نیست"
            return
        }
        showSave = true
        saveName = "aligned"
        // kind stored in saveName suffix via dialog buttons
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(status, color = TextSecondary, fontSize = 13.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { loadTarget = "control"; picker.launch(arrayOf("*/*", "text/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("فایل کنترل") }
            Button(
                onClick = { loadTarget = "survey"; picker.launch(arrayOf("*/*", "text/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("فایل برداشت") }
        }

        Text("نقاط مشترک: ${commonNames().joinToString()}", color = TextPrimary, fontSize = 12.sp)

        Text("نقطه مبنا (انتقال اولیه — اختیاری)", color = TextSecondary, fontSize = 12.sp)
        commonNames().forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = baseName?.equals(name, true) == true,
                    onCheckedChange = { on -> baseName = if (on) name else null },
                    colors = CheckboxDefaults.colors(checkedColor = color)
                )
                Text(name, color = TextPrimary)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(useScale, { useScale = it }, colors = CheckboxDefaults.colors(checkedColor = color))
            Text("مقیاس", color = TextPrimary)
            Spacer(Modifier.width(12.dp))
            Checkbox(useAverage, { useAverage = it }, colors = CheckboxDefaults.colors(checkedColor = color))
            Text("میانگین ارتفاع", color = TextPrimary)
        }

        Button(
            onClick = { applyAlign() },
            enabled = surveyPts.isNotEmpty() && controlPts.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text("اعمال الاین") }

        Text("ویرایش دستی نقاط برداشت (ضربه روی ردیف)", color = TextSecondary, fontSize = 12.sp)
        surveyPts.take(40).forEachIndexed { idx, p ->
            TextButton(
                onClick = {
                    editIdx = idx
                    editIsControl = false
                    eName = p.name
                    eE = String.format(Locale.US, "%.4f", p.e)
                    eN = String.format(Locale.US, "%.4f", p.n)
                    eZ = String.format(Locale.US, "%.4f", p.z)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    String.format(Locale.US, "%s  E %.3f  N %.3f  Z %.3f", p.name, p.e, p.n, p.z),
                    color = TextPrimary,
                    fontSize = 12.sp
                )
            }
        }
        if (surveyPts.size > 40) Text("… و ${surveyPts.size - 40} نقطه دیگر", color = TextSecondary)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { showSave = true; saveName = "aligned" }, enabled = surveyPts.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("ذخیره با نام")
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (editIdx != null) {
        AlertDialog(
            onDismissRequest = { editIdx = null },
            title = { Text("ویرایش نقطه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(eName, { eName = it }, label = { Text("نام") }, singleLine = true)
                    OutlinedTextField(eE, { eE = it }, label = { Text("E (X)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(eN, { eN = it }, label = { Text("N (Y)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(eZ, { eZ = it }, label = { Text("Z") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val i = editIdx!!
                    val e = eE.replace(',', '.').toDoubleOrNull()
                    val n = eN.replace(',', '.').toDoubleOrNull()
                    val z = eZ.replace(',', '.').toDoubleOrNull()
                    if (e != null && n != null && z != null) {
                        surveyPts = surveyPts.toMutableList().also {
                            it[i] = it[i].copy(name = eName, e = e, n = n, z = z)
                        }
                        status = "نقطه ویرایش شد"
                    }
                    editIdx = null
                }) { Text("ثبت") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val i = editIdx!!
                    surveyPts = surveyPts.filterIndexed { idx, _ -> idx != i }
                    editIdx = null
                    status = "نقطه حذف شد"
                }) { Text("حذف", color = Color(0xFFE57373)) }
            }
        )
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("ذخیره خروجی الاین") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(saveName, { saveName = it }, label = { Text("نام فایل") }, singleLine = true)
                    Text("فرمت را انتخاب کن:", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("txt", "dat", "dxf", "gsi").forEach { kind ->
                            Button(
                                onClick = {
                                    val base = saveName.trim().ifBlank { "aligned" }
                                    val body = when (kind) {
                                        "txt" -> GsiParser.toTxt(surveyPts)
                                        "dat" -> GsiParser.toDat(surveyPts)
                                        "dxf" -> GsiParser.toDxf(surveyPts)
                                        else -> GsiParser.toGsi(surveyPts)
                                    }
                                    val fname = if (base.lowercase().endsWith(".$kind")) base else "$base.$kind"
                                    val ok = FileExport.exportTextToDocuments(
                                        context, fname, body,
                                        if (kind == "dxf") "application/dxf" else "text/plain"
                                    ) != null
                                    status = if (ok) "ذخیره شد: $fname" else "خطا در ذخیره"
                                    showSave = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = color)
                            ) { Text(kind.uppercase()) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("بستن") } }
        )
    }
}
