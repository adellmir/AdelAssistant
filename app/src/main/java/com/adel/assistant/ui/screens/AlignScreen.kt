package com.adel.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.AlignTransform
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.GsiParser
import com.adel.assistant.data.GsiPoint
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

@Composable
fun AlignScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var points by remember { mutableStateOf<List<GsiPoint>>(emptyList()) }
    var status by remember { mutableStateOf("فایل نقاط را باز کن؛ مبنا و جهت را مشخص کن.") }
    var baseName by remember { mutableStateOf("B1") }
    var dirName by remember { mutableStateOf("B2") }
    var baseE by remember { mutableStateOf("") }
    var baseN by remember { mutableStateOf("") }
    var baseZ by remember { mutableStateOf("") }
    var dirE by remember { mutableStateOf("") }
    var dirN by remember { mutableStateOf("") }
    var dirZ by remember { mutableStateOf("") }
    var useScale by remember { mutableStateOf(true) }
    var useAverage by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        try {
            val name = uri.lastPathSegment?.lowercase() ?: ""
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val parsed = when {
                name.endsWith(".dat") -> GsiParser.parseDat(text)
                name.endsWith(".gsi") || text.trimStart().startsWith("*11") -> GsiParser.parse(text)
                else -> GsiParser.parseTxt(text).ifEmpty { GsiParser.parse(text) }
            }
            points = parsed
            status = if (parsed.isEmpty()) "نقطه‌ای یافت نشد" else "${parsed.size} نقطه بارگذاری شد"
        } catch (e: Exception) {
            status = "خطا: ${e.message}"
        }
    }

    fun applyAlign() {
        val be = baseE.replace(',', '.').toDoubleOrNull()
        val bn = baseN.replace(',', '.').toDoubleOrNull()
        val bz = baseZ.replace(',', '.').toDoubleOrNull()
        val de = dirE.replace(',', '.').toDoubleOrNull()
        val dn = dirN.replace(',', '.').toDoubleOrNull()
        val dz = dirZ.replace(',', '.').toDoubleOrNull()
        if (points.isEmpty()) {
            status = "ابتدا فایل نقاط را باز کن"
            return
        }
        if (baseName.isBlank() || dirName.isBlank() || be == null || bn == null || bz == null ||
            de == null || dn == null || dz == null
        ) {
            status = "نام و مختصات هدف را کامل وارد کن"
            return
        }
        val res = AlignTransform.alignSimple(
            points = points,
            baseName = baseName.trim(),
            dirName = dirName.trim(),
            targetBase = Triple(be, bn, bz),
            targetDir = Triple(de, dn, dz),
            useScale = useScale,
            useAverage = useAverage
        )
        points = res.points
        status = res.message
    }

    fun export(kind: String) {
        if (points.isEmpty()) {
            status = "نقطه‌ای نیست"
            return
        }
        val ok = when (kind) {
            "txt" -> FileExport.exportTextToDocuments(context, "aligned.txt", GsiParser.toTxt(points)) != null
            "dat" -> FileExport.exportTextToDocuments(context, "aligned.dat", GsiParser.toDat(points)) != null
            "dxf" -> FileExport.exportTextToDocuments(context, "aligned.dxf", GsiParser.toDxf(points), "application/dxf") != null
            else -> false
        }
        status = if (ok) "ذخیره شد: $kind" else "خطا در ذخیره"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "الاین / هم‌مختصات", color = color, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(status, color = TextSecondary, fontSize = 13.sp)
            Button(
                onClick = { picker.launch(arrayOf("*/*", "text/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth()
            ) { Text("باز کردن فایل نقاط") }

            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نقطه مبنا (جابجایی XYZ + مچ ارتفاع)", color = TextPrimary)
                    OutlinedTextField(
                        value = baseName, onValueChange = { baseName = it },
                        label = { Text("نام مبنا در فایل") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(value = baseE, onValueChange = { baseE = it }, label = { Text("E هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = baseN, onValueChange = { baseN = it }, label = { Text("N هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = baseZ, onValueChange = { baseZ = it }, label = { Text("Z هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نقطه جهت (چرخش محور)", color = TextPrimary)
                    OutlinedTextField(
                        value = dirName, onValueChange = { dirName = it },
                        label = { Text("نام جهت در فایل") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(value = dirE, onValueChange = { dirE = it }, label = { Text("E هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = dirN, onValueChange = { dirN = it }, label = { Text("N هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = dirZ, onValueChange = { dirZ = it }, label = { Text("Z هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(useScale, { useScale = it }, colors = CheckboxDefaults.colors(checkedColor = color))
                Text("مقیاس (طول افقی و ارتفاع نسبی)", color = TextPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(useAverage, { useAverage = it }, colors = CheckboxDefaults.colors(checkedColor = color))
                Text("میانگین‌گیری (نصف residual جهت)", color = TextPrimary)
            }
            Button(
                onClick = { applyAlign() },
                enabled = points.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth()
            ) { Text("اعمال الاین") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { export("txt") }, enabled = points.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("TXT") }
                OutlinedButton(onClick = { export("dat") }, enabled = points.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("DAT") }
                OutlinedButton(onClick = { export("dxf") }, enabled = points.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("DXF") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
