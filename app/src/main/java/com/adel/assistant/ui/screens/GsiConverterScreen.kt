package com.adel.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.AlignTransform
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.GsiParser
import com.adel.assistant.data.GsiPoint
import com.adel.assistant.data.XlsxPointReader
import com.adel.assistant.data.OnlineDwgConverter
import com.adel.assistant.data.DwgDxfConverter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GsiConverterScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var points by remember { mutableStateOf<List<GsiPoint>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectAll by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var convertingDwg by remember { mutableStateOf(false) }
    var lastDxfName by remember { mutableStateOf<String?>(null) }
    var newestFirst by remember { mutableStateOf(true) }
    var editTarget by remember { mutableStateOf<GsiPoint?>(null) }
    var editName by remember { mutableStateOf("") }
    var editE by remember { mutableStateOf("") }
    var editN by remember { mutableStateOf("") }
    var editZ by remember { mutableStateOf("") }
    var editCode by remember { mutableStateOf("") }
    var showAlign by remember { mutableStateOf(false) }
    var alignBaseName by remember { mutableStateOf("B1") }
    var alignDirName by remember { mutableStateOf("B2") }
    var alignBaseE by remember { mutableStateOf("") }
    var alignBaseN by remember { mutableStateOf("") }
    var alignBaseZ by remember { mutableStateOf("") }
    var alignDirE by remember { mutableStateOf("") }
    var alignDirN by remember { mutableStateOf("") }
    var alignDirZ by remember { mutableStateOf("") }
    var alignScale by remember { mutableStateOf(true) }
    var alignAverage by remember { mutableStateOf(false) }

    val displayList = remember(points, newestFirst) {
        if (newestFirst) points.asReversed() else points
    }


    val dwgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        convertingDwg = true
        status = "در حال تبدیل DWG (محلی/آنلاین)…"
        scope.launch {
            try {
                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "drawing.dwg"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("خواندن فایل ممکن نشد")
                val res = OnlineDwgConverter.convert(bytes, name)
                if (!res.ok || res.dxf.isBlank()) {
                    status = res.message.ifBlank { "تبدیل ناموفق" }
                } else {
                    val outName = name.substringBeforeLast('.').ifBlank { "converted" } + "_online.dxf"
                    val ok = FileExport.exportTextToDocuments(
                        context, outName, res.dxf, "application/dxf"
                    ) != null
                    lastDxfName = outName
                    status = if (ok) {
                        "✅ ${res.message}\nذخیره: Documents/AdelAssistant/dxf/$outName\nمنبع: ${res.source}"
                    } else {
                        "${res.message}\nاما ذخیره فایل شکست خورد"
                    }
                }
            } catch (e: Exception) {
                status = "خطا DWG: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                convertingDwg = false
            }
        }
    }

    fun selectedPoints(): List<GsiPoint> =
        if (selectAll) points else points.filter { it.id in selectedIds }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        try {
            val name = uri.lastPathSegment?.lowercase() ?: ""
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val parsed = when {
                name.endsWith(".xlsx") || name.endsWith(".xls") -> XlsxPointReader.parse(bytes)
                else -> {
                    val text = bytes.toString(Charsets.UTF_8)
                    when {
                        name.endsWith(".dat") -> GsiParser.parseDat(text)
                        name.endsWith(".gsi") ||
                            text.trimStart().startsWith("*11") ||
                            text.contains("81..") ||
                            text.contains("81.") -> GsiParser.parse(text)
                        else -> GsiParser.parseTxt(text).ifEmpty { GsiParser.parse(text) }
                    }
                }
            }
            points = parsed
            selectedIds = parsed.map { it.id }.toSet()
            selectAll = true
            status = if (parsed.isEmpty()) "نقطه‌ای یافت نشد" else "${parsed.size} نقطه"
        } catch (e: Exception) {
            status = "خطا: ${e.message}"
            points = emptyList()
            selectedIds = emptySet()
        }
    }

    fun saveFile(fileName: String, body: String, mime: String = "text/plain"): Boolean =
        FileExport.exportTextToDocuments(context, fileName, body, mime) != null

    fun export(kind: String) {
        val list = selectedPoints()
        if (list.isEmpty()) {
            status = "نقطه‌ای انتخاب نشده"
            return
        }
        val ok = when (kind) {
            "txt" -> saveFile("gsi_export.txt", GsiParser.toTxt(list))
            "dat" -> saveFile("gsi_export.dat", GsiParser.toDat(list))
            "gsi" -> saveFile("gsi_export.gsi", GsiParser.toGsi(list))
            "kml" -> saveFile("gsi_export.kml", GsiParser.toKml(list), "application/vnd.google-earth.kml+xml")
            "dxf" -> saveFile("gsi_export.dxf", GsiParser.toDxf(list), "application/dxf")
            else -> false
        }
        status = if (ok) "ذخیره شد: $kind (${list.size} نقطه)" else "خطا در ذخیره $kind"
    }

    fun applyEdit() {
        val t = editTarget ?: return
        val e = editE.toDoubleOrNull()
        val n = editN.toDoubleOrNull()
        val z = editZ.toDoubleOrNull()
        if (editName.isBlank() || e == null || n == null || z == null) {
            status = "مقادیر نامعتبر"
            return
        }
        points = points.map {
            if (it.id == t.id) it.copy(name = editName.trim(), e = e, n = n, z = z, code = editCode.trim()) else it
        }
        editTarget = null
        status = "ویرایش شد"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مبدل") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت", color = color) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1F16),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF12150F)
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp)) {
            if (status.isNotBlank()) {
                Text(status, color = Color(0xFFB0B8A8), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    picker.launch(arrayOf("*/*", "text/*", "application/octet-stream",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                }) { Icon(Icons.Outlined.FolderOpen, "باز کردن", tint = color) }
                IconButton(onClick = { newestFirst = !newestFirst }, enabled = points.isNotEmpty()) {
                    Icon(
                        if (newestFirst) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                        null, tint = color
                    )
                }
                IconButton(onClick = {
                    selectAll = true
                    selectedIds = points.map { it.id }.toSet()
                }, enabled = points.isNotEmpty()) {
                    Icon(Icons.Outlined.Checklist, "انتخاب همه", tint = color)
                }
                IconButton(onClick = {
                    selectAll = false
                    selectedIds = emptySet()
                }, enabled = points.isNotEmpty()) {
                    Icon(Icons.Outlined.Close, "گزینش", tint = color)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    dwgPicker.launch(
                        arrayOf(
                            "application/acad",
                            "application/x-dwg",
                            "application/octet-stream",
                            "image/vnd.dwg",
                            "*/*"
                        )
                    )
                },
                enabled = !convertingDwg,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (convertingDwg) "در حال تبدیل DWG…" else "DWG → DXF (آنلاین/محلی)")
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("TXT" to "txt", "DAT" to "dat", "GSI" to "gsi", "KML" to "kml", "DXF" to "dxf").forEach { (label, kind) ->
                    Button(
                        onClick = { export(kind) },
                        enabled = points.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) { Text(label, fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { showAlign = true },
                enabled = points.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) { Text("الاین / هم‌مختصات") }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(displayList, key = { it.id }) { p ->
                    val checked = selectAll || p.id in selectedIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E241A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                selectAll = false
                                selectedIds = if (on) selectedIds + p.id else selectedIds - p.id
                            },
                            colors = CheckboxDefaults.colors(checkedColor = color)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "E ${fmt(p.e)}  N ${fmt(p.n)}  Z ${fmt(p.z)}",
                                color = Color(0xFFB0B8A8), fontSize = 12.sp
                            )
                            if (p.code.isNotBlank()) {
                                Text("D: ${p.code}", color = Color(0xFF90CAF9), fontSize = 12.sp)
                            }
                        }
                        TextButton(onClick = {
                            editTarget = p
                            editName = p.name
                            editE = fmt(p.e)
                            editN = fmt(p.n)
                            editZ = fmt(p.z)
                            editCode = p.code
                        }) { Text("ویرایش", color = color, fontSize = 12.sp) }
                        TextButton(onClick = {
                            points = points.filter { it.id != p.id }
                            selectedIds = selectedIds - p.id
                            status = "حذف شد"
                        }) { Text("حذف", color = Color(0xFFE57373), fontSize = 12.sp) }
                    }
                }
            }
        }
    }

    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("ویرایش نقطه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("نام") }, singleLine = true)
                    OutlinedTextField(value = editE, onValueChange = { editE = it }, label = { Text("E") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = editN, onValueChange = { editN = it }, label = { Text("N") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = editZ, onValueChange = { editZ = it }, label = { Text("Z") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = editCode, onValueChange = { editCode = it }, label = { Text("D (اطلاعات / نوع)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { applyEdit() }) { Text("ذخیره", color = color) } },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("انصراف") } }
        )
    }

    if (showAlign) {
        AlertDialog(
            onDismissRequest = { showAlign = false },
            title = { Text("الاین نقاط") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نقطه مبنا (جابجایی + ارتفاع)", color = Color(0xFFB0B8A8), fontSize = 12.sp)
                    OutlinedTextField(value = alignBaseName, onValueChange = { alignBaseName = it }, label = { Text("نام مبنا در فایل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(value = alignBaseE, onValueChange = { alignBaseE = it }, label = { Text("E هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = alignBaseN, onValueChange = { alignBaseN = it }, label = { Text("N هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = alignBaseZ, onValueChange = { alignBaseZ = it }, label = { Text("Z هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Text("نقطه جهت (چرخش)", color = Color(0xFFB0B8A8), fontSize = 12.sp)
                    OutlinedTextField(value = alignDirName, onValueChange = { alignDirName = it }, label = { Text("نام جهت در فایل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(value = alignDirE, onValueChange = { alignDirE = it }, label = { Text("E هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = alignDirN, onValueChange = { alignDirN = it }, label = { Text("N هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(value = alignDirZ, onValueChange = { alignDirZ = it }, label = { Text("Z هدف") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(alignScale, { alignScale = it }, colors = CheckboxDefaults.colors(checkedColor = color))
                        Text("مقیاس (افقی و ارتفاع نسبی)", color = Color.White, fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(alignAverage, { alignAverage = it }, colors = CheckboxDefaults.colors(checkedColor = color))
                        Text("میانگین‌گیری (نصف residual جهت)", color = Color.White, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val be = alignBaseE.replace(',', '.').toDoubleOrNull()
                    val bn = alignBaseN.replace(',', '.').toDoubleOrNull()
                    val bz = alignBaseZ.replace(',', '.').toDoubleOrNull()
                    val de = alignDirE.replace(',', '.').toDoubleOrNull()
                    val dn = alignDirN.replace(',', '.').toDoubleOrNull()
                    val dz = alignDirZ.replace(',', '.').toDoubleOrNull()
                    if (alignBaseName.isBlank() || alignDirName.isBlank() ||
                        be == null || bn == null || bz == null || de == null || dn == null || dz == null
                    ) {
                        status = "مختصات یا نام نامعتبر"
                        return@TextButton
                    }
                    val src = selectedPoints()
                    if (src.isEmpty()) {
                        status = "نقطه‌ای انتخاب نشده"
                        return@TextButton
                    }
                    val res = AlignTransform.alignSimple(
                        points = src,
                        baseName = alignBaseName.trim(),
                        dirName = alignDirName.trim(),
                        targetBase = Triple(be, bn, bz),
                        targetDir = Triple(de, dn, dz),
                        useScale = alignScale,
                        useAverage = alignAverage
                    )
                    if (selectAll) points = res.points
                    else {
                        val map = res.points.associateBy { it.id }
                        points = points.map { map[it.id] ?: it }
                    }
                    selectedIds = points.map { it.id }.toSet()
                    status = res.message
                    showAlign = false
                }) { Text("اعمال", color = color) }
            },
            dismissButton = { TextButton(onClick = { showAlign = false }) { Text("انصراف") } }
        )
    }
}

private fun fmt(v: Double): String =
    String.format(java.util.Locale.US, "%.3f", v)
