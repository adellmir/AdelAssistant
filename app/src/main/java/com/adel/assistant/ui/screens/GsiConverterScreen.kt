package com.adel.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.GsiParser
import com.adel.assistant.data.GsiPoint
import com.adel.assistant.data.PointConverter
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GsiConverterScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var points by remember { mutableStateOf<List<GsiPoint>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var status by remember { mutableStateOf("") }
    var showExportNameDialog by remember { mutableStateOf(false) }
    var pendingExportKind by remember { mutableStateOf("") }
    var exportBaseName by remember { mutableStateOf("gsi_export") }
    var newestFirst by remember { mutableStateOf(false) }
    var sortByCode by remember { mutableStateOf(false) }
    var rangeSelectMode by remember { mutableStateOf(false) }
    var rangeAnchorId by remember { mutableStateOf<Long?>(null) }

    val displayList = remember(points, newestFirst, sortByCode) {
        val base = if (sortByCode) {
            points.sortedWith(compareBy<GsiPoint>({ it.code }, { it.name }, { it.id }))
        } else points
        if (newestFirst) base.asReversed() else base
    }

    fun selectedPoints(): List<GsiPoint> =
        if (selectedIds.isEmpty()) points else points.filter { it.id in selectedIds }

    fun displayName(uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) {
                        val n = c.getString(i)
                        if (!n.isNullOrBlank()) return n
                    }
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "points.txt"
    }

    fun parseAny(bytes: ByteArray, fileName: String): List<GsiPoint> {
        val name = fileName.lowercase()
        // 1) GSI
        if (name.endsWith(".gsi") || name.contains(".gsi")) {
            val t = bytes.toString(Charsets.UTF_8)
            val g = GsiParser.parse(t)
            if (g.isNotEmpty()) return g
        }
        // 2) DAT via PointConverter (N Y X Z)
        if (name.endsWith(".dat") || name.contains(".dat")) {
            val sp = try {
                PointConverter.readBytes(bytes, "file.dat")
            } catch (_: Exception) {
                emptyList()
            }
            if (sp.isNotEmpty()) {
                return sp.mapIndexed { i, p ->
                    GsiPoint(id = System.nanoTime() + i, name = p.id, e = p.x, n = p.y, z = p.z, code = p.code)
                }
            }
            val d = GsiParser.parseDat(bytes.toString(Charsets.UTF_8))
            if (d.isNotEmpty()) return d
        }
        // 3) PointConverter general (auto DAT detect)
        try {
            val sp = PointConverter.readBytes(bytes, fileName)
            if (sp.isNotEmpty()) {
                return sp.mapIndexed { i, p ->
                    GsiPoint(id = System.nanoTime() + i, name = p.id, e = p.x, n = p.y, z = p.z, code = p.code)
                }
            }
        } catch (_: Exception) {
        }
        val text = try {
            bytes.toString(Charsets.UTF_8)
        } catch (_: Exception) {
            bytes.toString(Charset.defaultCharset())
        }
        // 4) GSI by content
        if (text.trimStart().startsWith("*11") || text.contains("81..") || text.contains("*41")) {
            val g = GsiParser.parse(text)
            if (g.isNotEmpty()) return g
        }
        // 5) DAT then TXT
        val dat = GsiParser.parseDat(text)
        if (dat.isNotEmpty()) return dat
        val txt = GsiParser.parseTxt(text)
        if (txt.isNotEmpty()) return txt
        return GsiParser.parse(text)
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
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                status = "فایل خالی یا خوانده نشد"
                return@rememberLauncherForActivityResult
            }
            val fname = displayName(uri)
            val parsed = parseAny(bytes, fname)
            if (parsed.isEmpty()) {
                status = "نقطه‌ای از «$fname» خوانده نشد (${bytes.size} بایت)"
                points = emptyList()
                selectedIds = emptySet()
            } else {
                points = parsed
                selectedIds = parsed.map { it.id }.toSet()
                status = "${parsed.size} نقطه از «$fname» بارگذاری شد"
            }
        } catch (e: Exception) {
            status = "خطا: ${e.message}"
        }
    }

    fun export(kind: String) {
        val list = selectedPoints()
        if (list.isEmpty()) {
            status = "نقطه‌ای انتخاب نشده"
            return
        }
        pendingExportKind = kind
        exportBaseName = "gsi_export"
        showExportNameDialog = true
    }

    fun doExport(base: String) {
        val list = selectedPoints()
        val body = when (pendingExportKind) {
            "txt" -> GsiParser.toTxt(list)
            "dat" -> GsiParser.toDat(list)
            "csv" -> GsiParser.toCsv(list)
            "gsi" -> GsiParser.toGsi(list)
            else -> GsiParser.toTxt(list)
        }
        val ext = pendingExportKind.ifBlank { "txt" }
        val name = base.trim().ifBlank { "gsi_export" }.let {
            if (it.endsWith(".$ext", true)) it else "$it.$ext"
        }
        val ok = FileExport.exportTextToDocuments(context, name, body) != null
        status = if (ok) "ذخیره شد: $name (${list.size} نقطه)" else "خطا در ذخیره"
        showExportNameDialog = false
    }

    Scaffold(
        containerColor = Color(0xFF1A1F16),
        topBar = {
            TopAppBar(
                title = { Text("مبدل") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", color = Color.White, fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF24301C))
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Toolbar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.FolderOpen, "باز کردن", tint = color)
                }
                IconButton(onClick = {
                    selectedIds = points.map { it.id }.toSet()
                    status = "همه انتخاب شدند (${points.size})"
                }) {
                    Icon(Icons.Filled.SelectAll, "همه", tint = color)
                }
                TextButton(onClick = {
                    selectedIds = emptySet()
                    status = "انتخاب پاک شد"
                }) { Text("هیچ", color = color, fontSize = 12.sp) }
                Text("|", color = Color(0xFF6A7260))
                TextButton(onClick = {
                    rangeSelectMode = !rangeSelectMode
                    rangeAnchorId = null
                    status = if (rangeSelectMode) "بازه: نقطه اول را بزن" else "بازه خاموش"
                }) {
                    Text(if (rangeSelectMode) "بازه✓" else "بازه", color = color, fontSize = 12.sp)
                }
                IconButton(onClick = { sortByCode = !sortByCode }) {
                    Icon(Icons.Filled.SortByAlpha, "کد", tint = if (sortByCode) color else Color.Gray)
                }
                IconButton(onClick = { newestFirst = !newestFirst }) {
                    Icon(Icons.Filled.SwapVert, "ترتیب", tint = color)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("txt", "dat", "csv", "gsi").forEach { k ->
                    OutlinedButton(onClick = { export(k) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(k.uppercase(), fontSize = 11.sp)
                    }
                }
            }

            if (status.isNotBlank()) {
                Text(status, color = color, fontSize = 12.sp)
            }
            Text(
                if (points.isEmpty()) "فایلی باز نشده — GSI / DAT / TXT / CSV"
                else "${points.size} نقطه | انتخاب‌شده: ${selectedIds.size}",
                color = Color(0xFFB0B8A8),
                fontSize = 12.sp
            )

            // Excel-like table
            if (points.isNotEmpty()) {
                val hScroll = rememberScrollState()
                Column(Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        Modifier
                            .horizontalScroll(hScroll)
                            .background(Color(0xFF2A3324), RoundedCornerShape(6.dp))
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                            Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        HeaderCell("نام", 72)
                        HeaderCell("E (X)", 100)
                        HeaderCell("N (Y)", 100)
                        HeaderCell("Z", 80)
                        HeaderCell("کد", 72)
                    }
                    LazyColumn(
                        Modifier
                            .weight(1f)
                            .horizontalScroll(hScroll),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(displayList, key = { _, p -> p.id }) { _, p ->
                            val on = p.id in selectedIds
                            Row(
                                Modifier
                                    .background(
                                        if (on) Color(0xFF2A3A22) else Color(0xFF22281C),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(vertical = 2.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = on,
                                    onCheckedChange = { checked ->
                                        if (rangeSelectMode) {
                                            if (rangeAnchorId == null) {
                                                rangeAnchorId = p.id
                                                selectedIds = setOf(p.id)
                                                status = "نقطه اول بازه ثبت شد — نقطه دوم را بزن"
                                            } else {
                                                val ids = displayList.map { it.id }
                                                val i1 = ids.indexOf(rangeAnchorId)
                                                val i2 = ids.indexOf(p.id)
                                                if (i1 >= 0 && i2 >= 0) {
                                                    val a = minOf(i1, i2)
                                                    val b = maxOf(i1, i2)
                                                    selectedIds = ids.subList(a, b + 1).toSet()
                                                    status = "بازه ${b - a + 1} نقطه"
                                                }
                                                rangeAnchorId = null
                                            }
                                        } else {
                                            selectedIds =
                                                if (checked) selectedIds + p.id else selectedIds - p.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = color),
                                    modifier = Modifier.size(40.dp)
                                )
                                EditCell(p.name, 72) { v ->
                                    points = points.map {
                                        if (it.id == p.id) it.copy(name = v) else it
                                    }
                                }
                                EditCell(fmt(p.e), 100, true) { v ->
                                    v.replace(',', '.').toDoubleOrNull()?.let { d ->
                                        points = points.map {
                                            if (it.id == p.id) it.copy(e = d) else it
                                        }
                                    }
                                }
                                EditCell(fmt(p.n), 100, true) { v ->
                                    v.replace(',', '.').toDoubleOrNull()?.let { d ->
                                        points = points.map {
                                            if (it.id == p.id) it.copy(n = d) else it
                                        }
                                    }
                                }
                                EditCell(fmt(p.z), 80, true) { v ->
                                    v.replace(',', '.').toDoubleOrNull()?.let { d ->
                                        points = points.map {
                                            if (it.id == p.id) it.copy(z = d) else it
                                        }
                                    }
                                }
                                EditCell(p.code, 72) { v ->
                                    points = points.map {
                                        if (it.id == p.id) it.copy(code = v) else it
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportNameDialog) {
        AlertDialog(
            onDismissRequest = { showExportNameDialog = false },
            title = { Text("نام فایل خروجی") },
            text = {
                OutlinedTextField(
                    exportBaseName,
                    { exportBaseName = it },
                    label = { Text("نام بدون پسوند") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { doExport(exportBaseName) }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showExportNameDialog = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun HeaderCell(title: String, width: Int) {
    Text(
        title,
        Modifier.width(width.dp).padding(horizontal = 4.dp),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )
}

@Composable
private fun EditCell(
    value: String,
    width: Int,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    BasicTextField(
        value = local,
        onValueChange = {
            local = it
            onChange(it)
        },
        singleLine = true,
        textStyle = TextStyle(color = Color(0xFFE8EDE0), fontSize = 12.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = if (numeric)
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions.Default,
        modifier = Modifier
            .width(width.dp)
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .background(Color(0xFF1A2218), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

private fun fmt(v: Double): String =
    String.format(java.util.Locale.US, "%.3f", v)
