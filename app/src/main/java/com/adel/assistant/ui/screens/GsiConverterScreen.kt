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
import com.adel.assistant.data.DwgDxfConverter
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
    var editTarget by remember { mutableStateOf<GsiPoint?>(null) }
    var editName by remember { mutableStateOf("") }
    var editE by remember { mutableStateOf("") }
    var editN by remember { mutableStateOf("") }
    var editZ by remember { mutableStateOf("") }
    var drawingDxf by remember { mutableStateOf<String?>(null) }
    var drawingName by remember { mutableStateOf("") }

    val displayList = remember(points, newestFirst) {
        if (newestFirst) points.asReversed() else points
    }

    fun selectedPoints(): List<GsiPoint> {
        return if (selectAll) points else points.filter { it.id in selectedIds }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val name = uri.lastPathSegment?.lowercase() ?: ""
            val shortName = name.substringAfterLast('/').substringAfterLast(':')
            val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrDefault("")
            when {
                shortName.endsWith(".dwg") || shortName.endsWith(".dxf") ||
                    text.trimStart().startsWith("0") && text.contains("SECTION") ||
                    bytes.size >= 6 && String(bytes, 0, 6, Charsets.ISO_8859_1).startsWith("AC") -> {
                    val conv = DwgDxfConverter.convert(bytes, shortName)
                    drawingDxf = if (conv.ok) conv.dxf else null
                    drawingName = shortName.substringAfterLast('.').let { ext ->
                        shortName.removeSuffix(".$ext").ifBlank { "drawing" } + ".dxf"
                    }.substringAfterLast('/')
                    if (drawingName.isBlank() || !drawingName.endsWith(".dxf")) drawingName = "converted.dxf"
                    points = emptyList()
                    selectedIds = emptySet()
                    status = conv.message
                }
                else -> {
                    drawingDxf = null
                    val parsed = when {
                        shortName.endsWith(".gsi") ||
                            text.trimStart().startsWith("*11") ||
                            text.contains("81..") ||
                            text.contains("81.") -> GsiParser.parse(text)
                        else -> GsiParser.parseTxt(text).ifEmpty { GsiParser.parse(text) }
                    }
                    points = parsed
                    selectedIds = parsed.map { it.id }.toSet()
                    selectAll = true
                    status = if (parsed.isEmpty()) "نقطه‌ای یافت نشد" else "${parsed.size} نقطه"
                }
            }
        } catch (e: Exception) {
            status = "خطا: ${e.message}"
            points = emptyList()
            selectedIds = emptySet()
            drawingDxf = null
        }
    }

    fun saveFile(fileName: String, body: String, mime: String = "text/plain"): Boolean {
        return try {
            val bytes = body.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/AdelAssistant"
                    )
                }
                val outUri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), values
                ) ?: return false
                context.contentResolver.openOutputStream(outUri)?.use { it.write(bytes) } ?: return false
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "AdelAssistant"
                )
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
                true
            }
        } catch (_: Exception) {
            false
        }
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { picker.launch(com.adel.assistant.data.AdelDocuments.openDocumentIntent("*/*", "text/*", "application/octet-stream")) },
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) { Text("باز کردن") }

                OutlinedButton(
                    onClick = { newestFirst = !newestFirst },
                    enabled = points.isNotEmpty()
                ) {
                    Text(if (newestFirst) "جدید→قدیم" else "قدیم→جدید")
                }

                Button(
                    onClick = {
                        val body = drawingDxf
                        if (body.isNullOrBlank()) {
                            status = "ابتدا فایل DWG یا DXF را باز کن"
                            return@Button
                        }
                        val uriOut = FileExport.exportTextToDocuments(
                            context,
                            drawingName.ifBlank { "converted.dxf" },
                            body,
                            "application/dxf"
                        )
                        status = if (uriOut != null)
                            "DXF در Documents/AdelAssistant ذخیره شد"
                        else "خطا در ذخیره DXF"
                    },
                    enabled = drawingDxf != null,
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) { Text("خروجی DXF نقشه") }
            }

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        selectAll = true
                        selectedIds = points.map { it.id }.toSet()
                    },
                    enabled = points.isNotEmpty()
                ) { Text("انتخاب همه") }

                OutlinedButton(
                    onClick = {
                        selectAll = false
                        selectedIds = emptySet()
                    },
                    enabled = points.isNotEmpty()
                ) { Text("گزینش") }
            }

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
                                selectedIds = if (on) selectedIds + p.id else selectedIds - p.id
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
