package com.adel.assistant.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.SwapVert
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
import com.adel.assistant.data.GsiParser
import com.adel.assistant.data.GsiPoint
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GsiConverterScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var points by remember { mutableStateOf<List<GsiPoint>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectAll by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(true) }
    var sortByCode by remember { mutableStateOf(false) }
    var rangeSelectMode by remember { mutableStateOf(false) }
    var rangeAnchorId by remember { mutableStateOf<Long?>(null) }
    var editTarget by remember { mutableStateOf<GsiPoint?>(null) }
    var editName by remember { mutableStateOf("") }
    var editE by remember { mutableStateOf("") }
    var editN by remember { mutableStateOf("") }
    var editZ by remember { mutableStateOf("") }

    val displayList = remember(points, newestFirst, sortByCode) {
        val base = if (sortByCode) {
            points.sortedWith(compareBy<GsiPoint>({ it.code }, { it.name }, { it.id }))
        } else points
        if (newestFirst) base.asReversed() else base
    }

    fun selectedPoints(): List<GsiPoint> {
        return if (selectAll) points else points.filter { it.id in selectedIds }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
            } ?: ""
            val name = uri.lastPathSegment?.lowercase() ?: ""
            val parsed = when {
                name.endsWith(".gsi") || name.endsWith(".dat") ||
                    text.trimStart().startsWith("*11") ||
                    text.contains("81..") ||
                    text.contains("81.") -> GsiParser.parse(text)
                name.endsWith(".dat") -> GsiParser.parseDat(text)
                else -> GsiParser.parseTxt(text).ifEmpty { GsiParser.parseDat(text) }.ifEmpty { GsiParser.parse(text) }
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

    fun saveFile(fileName: String, body: String, mime: String = "text/plain"): Boolean {
        return FileExport.exportTextToDocuments(context, fileName, body, mime) != null
    }

    fun export(kind: String) {
        val list = selectedPoints()
        if (list.isEmpty()) {
            status = "نقطه‌ای انتخاب نشده"
            return
        }
        val ok = when (kind) {
            "txt" -> saveFile("gsi_export.txt", GsiParser.toTxt(list))
            "gsi" -> saveFile("gsi_export.gsi", GsiParser.toGsi(list))
            "dat" -> saveFile("gsi_export.dat", GsiParser.toDat(list))
            "kml" -> saveFile(
                "gsi_export.kml",
                GsiParser.toKml(list),
                "application/vnd.google-earth.kml+xml"
            )
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
            if (it.id == t.id) it.copy(name = editName.trim(), e = e, n = n, z = z) else it
        }
        editTarget = null
        status = "ویرایش شد"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مبدل") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("بازگشت", color = color) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1F16),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF12150F)
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            if (status.isNotBlank()) {
                Text(status, color = Color(0xFFB0B8A8), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
            }

            // یک ردیف آیکن: باز کردن | ترتیب جدید/قدیم | انتخاب همه | گزینش | بازه | مرتب‌سازی کد
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { picker.launch(arrayOf("*/*", "text/*", "application/octet-stream")) }
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "باز کردن", tint = color)
                }
                IconButton(
                    onClick = { newestFirst = !newestFirst },
                    enabled = points.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.SwapVert,
                        contentDescription = if (newestFirst) "جدید→قدیم" else "قدیم→جدید",
                        tint = if (points.isNotEmpty()) color else Color.Gray
                    )
                }
                IconButton(
                    onClick = {
                        selectAll = true
                        selectedIds = points.map { it.id }.toSet()
                        rangeAnchorId = null
                        status = "همه انتخاب شد"
                    },
                    enabled = points.isNotEmpty()
                ) {
                    Icon(Icons.Filled.SelectAll, contentDescription = "انتخاب همه", tint = if (points.isNotEmpty()) color else Color.Gray)
                }
                IconButton(
                    onClick = {
                        selectAll = false
                        selectedIds = emptySet()
                        rangeAnchorId = null
                        rangeSelectMode = false
                        status = "گزینش: هیچ‌کدام"
                    },
                    enabled = points.isNotEmpty()
                ) {
                    Icon(Icons.Filled.CheckBoxOutlineBlank, contentDescription = "گزینش خالی", tint = if (points.isNotEmpty()) color else Color.Gray)
                }
                IconButton(
                    onClick = {
                        rangeSelectMode = !rangeSelectMode
                        rangeAnchorId = null
                        selectAll = false
                        status = if (rangeSelectMode) "بازه: دو نقطه ابتدا و انتها را بزن" else "بازه خاموش"
                    },
                    enabled = points.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.CheckBox,
                        contentDescription = "انتخاب بازه‌ای",
                        tint = if (rangeSelectMode) Color(0xFF81C995) else (if (points.isNotEmpty()) color else Color.Gray)
                    )
                }
                IconButton(
                    onClick = {
                        sortByCode = !sortByCode
                        status = if (sortByCode) "مرتب بر اساس کد" else "مرتب پیش‌فرض"
                    },
                    enabled = points.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.SortByAlpha,
                        contentDescription = "مرتب‌سازی کد",
                        tint = if (sortByCode) Color(0xFF81C995) else (if (points.isNotEmpty()) color else Color.Gray)
                    )
                }
            }
            Text(
                "📁 باز کردن  |  ↕ جدید/قدیم  |  ☑ همه  |  ☐ خالی  |  ▣ بازه  |  A کد",
                color = Color(0xFF8A9280),
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "TXT" to "txt",
                    "GSI" to "gsi",
                    "KML" to "kml",
                    "DXF" to "dxf"
                ).forEach { (label, kind) ->
                    Button(
                        onClick = { export(kind) },
                        enabled = points.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                                if (rangeSelectMode) {
                                    if (rangeAnchorId == null) {
                                        rangeAnchorId = p.id
                                        selectedIds = setOf(p.id)
                                        status = "ابتدای بازه: ${p.name} — نقطه پایان را بزن"
                                    } else {
                                        val ids = displayList.map { it.id }
                                        val i1 = ids.indexOf(rangeAnchorId)
                                        val i2 = ids.indexOf(p.id)
                                        if (i1 >= 0 && i2 >= 0) {
                                            val a = minOf(i1, i2); val b = maxOf(i1, i2)
                                            selectedIds = ids.subList(a, b + 1).toSet()
                                            status = "بازه ${b - a + 1} نقطه انتخاب شد"
                                        } else {
                                            selectedIds = setOf(p.id)
                                        }
                                        rangeAnchorId = null
                                    }
                                } else {
                                    selectedIds = if (on) selectedIds + p.id else selectedIds - p.id
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = color)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "E ${fmt(p.e)}  N ${fmt(p.n)}  Z ${fmt(p.z)}",
                                color = Color(0xFFB0B8A8),
                                fontSize = 12.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                editTarget = p
                                editName = p.name
                                editE = fmt(p.e)
                                editN = fmt(p.n)
                                editZ = fmt(p.z)
                            }
                        ) { Text("ویرایش", color = color, fontSize = 12.sp) }
                        TextButton(
                            onClick = {
                                points = points.filter { it.id != p.id }
                                selectedIds = selectedIds - p.id
                                status = "حذف شد"
                            }
                        ) { Text("حذف", color = Color(0xFFE57373), fontSize = 12.sp) }
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
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("نام") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editE,
                        onValueChange = { editE = it },
                        label = { Text("E") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = editN,
                        onValueChange = { editN = it },
                        label = { Text("N") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = editZ,
                        onValueChange = { editZ = it },
                        label = { Text("Z") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { applyEdit() }) { Text("ذخیره", color = color) }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("انصراف") }
            }
        )
    }
}

private fun fmt(v: Double): String =
    String.format(java.util.Locale.US, "%.3f", v)
