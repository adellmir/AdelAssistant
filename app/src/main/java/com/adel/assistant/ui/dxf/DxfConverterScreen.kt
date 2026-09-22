package com.adel.assistant.ui.dxf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.adel.assistant.data.CodeSetting
import com.adel.assistant.data.DefaultCodeRules
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.GsiParser
import com.adel.assistant.data.KmlParser
import com.adel.assistant.data.PointConverter
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.data.GsiPoint
import com.adel.assistant.ui.PointsSpreadsheet
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.ToolPrimary
import com.adel.assistant.utils.DxfMapGenerator
import com.adel.assistant.utils.DxfPointParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

@Composable
fun DxfConverterScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var stage by remember { mutableStateOf(0) } // 0=pick, 1=categorize, 2=result
    var points by remember { mutableStateOf<List<SurveyPoint>>(emptyList()) }
    var fileName by remember { mutableStateOf("") }
    var codeSettings by remember { mutableStateOf<Map<String, CodeSetting>>(emptyMap()) }
    var generatedDxf by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var workTab by remember { mutableStateOf(0) } // 0 ویرایش نقاط 1 تعیین وضعیت
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectAll by remember { mutableStateOf(true) }
    var newestFirst by remember { mutableStateOf(false) }
    var rangeMode by remember { mutableStateOf(false) }
    var rangeAnchorId by remember { mutableStateOf<Long?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = com.adel.assistant.data.AdelDocuments.OpenDocumentContract()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val name = getFileName(context, uri) ?: "points.dat"
                fileName = name
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    val lower = name.lowercase()
                    val parsedPoints: List<SurveyPoint> = when {
                        lower.endsWith(".kml") || lower.endsWith(".kmz") ->
                            KmlParser.parseBytes(bytes, name).points
                        lower.endsWith(".gsi") ||
                            bytes.toString(Charsets.UTF_8).trimStart().startsWith("*11") -> {
                            val gsi = GsiParser.parse(bytes.toString(Charsets.UTF_8))
                            gsi.map { g ->
                                SurveyPoint(
                                    id = g.name,
                                    x = g.e,
                                    y = g.n,
                                    z = g.z,
                                    code = g.code.ifBlank { g.name }
                                )
                            }
                        }
                        else -> {
                            // DAT/TXT/CSV/IDX و سایر
                            try {
                                PointConverter.readBytes(bytes, name)
                            } catch (_: Exception) {
                                DxfPointParser.parse(ByteArrayInputStream(bytes), name).points
                            }
                        }
                    }
                    points = parsedPoints
                    val uniqueCodes = parsedPoints.map { it.code }.distinct().filter { it.isNotBlank() }
                    codeSettings = uniqueCodes.associateWith { DefaultCodeRules.createDefaultSetting(it) }
                    if (parsedPoints.isNotEmpty()) {
                        stage = 1
                        workTab = 0
                        selectAll = true
                        selectedIds = parsedPoints.mapIndexed { i, _ -> i.toLong() }.toSet()
                        rangeMode = false
                        rangeAnchorId = null
                        statusMessage = null
                    } else {
                        statusMessage = "هیچ نقطه‌ای خوانده نشد."
                    }
                }
            } catch (e: Exception) {
                statusMessage = "خطا: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                when (stage) {
                    0 -> onBack()
                    1 -> stage = 0
                    2 -> stage = 1
                }
            }) {
                Text("بازگشت", color = ToolPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                when (stage) {
                    0 -> "ترسیم نقشه"
                    1 -> "دسته‌بندی کدها"
                    else -> "نتیجه"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        when (stage) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(40.dp))
                    Icon(Icons.Default.UploadFile, null, tint = ToolPrimary, modifier = Modifier.size(72.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "فایل نقاط را انتخاب کنید",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        ".dat → n y x z d\nسایر متنی → N x y z d\n.gsi → هر دو مدل لیکا",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ToolPrimary)
                    ) {
                        Text("انتخاب فایل")
                    }
                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(statusMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            1 -> {
                // تبدیل به GsiPoint برای جدول (id پایدار = ایندکس)
                fun toGsi(list: List<SurveyPoint>): List<GsiPoint> =
                    list.mapIndexed { i, sp ->
                        GsiPoint(
                            name = sp.id.ifBlank { (i + 1).toString() },
                            e = sp.x,
                            n = sp.y,
                            z = sp.z,
                            code = sp.code,
                            id = i.toLong()
                        )
                    }
                fun fromGsi(g: GsiPoint): SurveyPoint =
                    SurveyPoint(
                        id = g.name,
                        x = g.e,
                        y = g.n,
                        z = g.z,
                        code = g.code
                    )
                val gsiPoints = remember(points) { toGsi(points) }
                val displayList = remember(gsiPoints, newestFirst) {
                    if (newestFirst) gsiPoints.asReversed() else gsiPoints
                }
                fun activePoints(): List<SurveyPoint> {
                    if (selectAll || selectedIds.size == points.size) return points
                    return points.filterIndexed { i, _ -> i.toLong() in selectedIds }
                }
                fun doGenerate() {
                    val use = activePoints()
                    if (use.isEmpty()) {
                        statusMessage = "هیچ نقطه‌ای انتخاب نشده"
                        return
                    }
                    // تنظیمات کد را با نقاط فعال همگام کن
                    val codes = use.map { it.code }.distinct().filter { it.isNotBlank() }
                    val settings = codes.associateWith { c ->
                        codeSettings[c] ?: DefaultCodeRules.createDefaultSetting(c)
                    }
                    codeSettings = codeSettings + settings
                    generatedDxf = DxfMapGenerator.generate(use, codeSettings + settings)
                    stage = 2
                }

                Column(Modifier.fillMaxSize()) {
                    // ردیف توضیح فایل + تولید DXF
                    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("فایل: $fileName", fontWeight = FontWeight.SemiBold, color = Color.Black)
                                Text(
                                    "${points.size} نقطه · ${selectedIds.size} گزینش‌شده",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF555555)
                                )
                                statusMessage?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Button(
                                onClick = { doGenerate() },
                                colors = ButtonDefaults.buttonColors(containerColor = ToolPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("تولید DXF") }
                        }
                    }
                    TabRow(selectedTabIndex = workTab, containerColor = Color.White, contentColor = ToolPrimary) {
                        Tab(selected = workTab == 0, onClick = { workTab = 0 }, text = { Text("ویرایش نقاط") })
                        Tab(selected = workTab == 1, onClick = { workTab = 1 }, text = { Text("تعیین وضعیت") })
                    }
                    when (workTab) {
                        0 -> {
                            Column(Modifier.fillMaxSize().padding(8.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { newestFirst = !newestFirst }) {
                                        Icon(
                                            if (newestFirst) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                                            null, tint = ToolPrimary
                                        )
                                    }
                                    IconButton(onClick = {
                                        selectAll = true
                                        selectedIds = points.indices.map { it.toLong() }.toSet()
                                    }) {
                                        Icon(Icons.Outlined.Checklist, "همه", tint = ToolPrimary)
                                    }
                                    IconButton(onClick = {
                                        selectAll = false
                                        selectedIds = emptySet()
                                        rangeMode = false
                                        rangeAnchorId = null
                                    }) {
                                        Icon(Icons.Outlined.Close, "گزینش", tint = ToolPrimary)
                                    }
                                    IconButton(onClick = {
                                        rangeMode = !rangeMode
                                        rangeAnchorId = null
                                        statusMessage = if (rangeMode)
                                            "بازه: ابتدا و انتهای برداشت را تیک بزن"
                                        else null
                                    }) {
                                        Icon(
                                            Icons.Filled.LinearScale, "بازه",
                                            tint = if (rangeMode) Color(0xFF43A047) else ToolPrimary
                                        )
                                    }
                                }
                                if (rangeMode) {
                                    Text(
                                        "بازه فعال — تیک نقطهٔ اول، بعد تیک نقطهٔ آخر",
                                        color = Color(0xFF43A047),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                                PointsSpreadsheet(
                                    points = displayList,
                                    color = ToolPrimary,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    selectedIds = selectedIds,
                                    selectAll = selectAll,
                                    showCheckbox = true,
                                    onToggleSelect = { id, on ->
                                        if (rangeMode && on) {
                                            val order = points.indices.map { it.toLong() }
                                            if (rangeAnchorId == null) {
                                                rangeAnchorId = id
                                                selectAll = false
                                                selectedIds = setOf(id)
                                                statusMessage = "ابتدای بازه ✓ — انتها را تیک بزن"
                                            } else {
                                                val i1 = order.indexOf(rangeAnchorId)
                                                val i2 = order.indexOf(id)
                                                if (i1 >= 0 && i2 >= 0) {
                                                    val a = minOf(i1, i2)
                                                    val b = maxOf(i1, i2)
                                                    selectedIds = (a..b).map { it.toLong() }.toSet()
                                                    selectAll = selectedIds.size == points.size
                                                    statusMessage = "بازه: ${b - a + 1} نقطه"
                                                }
                                                rangeAnchorId = null
                                            }
                                        } else {
                                            selectAll = false
                                            selectedIds = if (on) selectedIds + id else selectedIds - id
                                            rangeAnchorId = null
                                        }
                                    },
                                    onChange = { g ->
                                        val idx = g.id.toInt()
                                        if (idx in points.indices) {
                                            points = points.toMutableList().also {
                                                it[idx] = fromGsi(g)
                                            }
                                            // بروزرسانی تنظیمات کد در صورت کد جدید
                                            val c = g.code.trim()
                                            if (c.isNotBlank() && c !in codeSettings) {
                                                codeSettings = codeSettings + (c to DefaultCodeRules.createDefaultSetting(c))
                                            }
                                        }
                                    },
                                    onDelete = { g ->
                                        val idx = g.id.toInt()
                                        if (idx in points.indices) {
                                            points = points.filterIndexed { i, _ -> i != idx }
                                            // بازسازی ایندکس‌ها با نگه‌داشتن داده
                                            selectedIds = emptySet()
                                            selectAll = true
                                            selectedIds = points.indices.map { it.toLong() }.toSet()
                                        }
                                    }
                                )
                            }
                        }
                        else -> {
                            // بدون دکمه تأیید پایین — تولید از بالا
                            CodeCategorizationContent(
                                points = activePoints(),
                                fileName = fileName,
                                settings = codeSettings,
                                onSettingsChange = { codeSettings = it },
                                onConfirm = { doGenerate() }
                            )
                        }
                    }
                }
            }

            2 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Icon(Icons.Default.CheckCircle, null, tint = ToolPrimary, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "DXF ساخته شد",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1C)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "فایل: $fileName\nنقاط: ${points.size}\nحجم: ${(generatedDxf?.length ?: 0) / 1024} KB\nمسیر: Documents/AdelAssistant",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1C1C1C)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { generatedDxf?.let { saveAndShare(context, it, fileName) } },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ToolPrimary)
                    ) {
                        Text("ذخیره و اشتراک DXF")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            if (points.isNotEmpty()) {
                                val kml = com.adel.assistant.data.UtmGeo.toKml(points, fileName)
                                saveAndShareKml(context, kml, fileName)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("ذخیره و اشتراک KML")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { stage = 1 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("بازگشت به دسته‌بندی")
                    }
                }
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
    }
    return name
}

private fun saveAndShare(context: Context, content: String, originalName: String) {
    try {
        val base = originalName.substringBeforeLast(".").ifBlank { "map" }
        val fileName = "${base}_map.dxf"
        FileExport.exportTextToDocuments(
            context,
            fileName,
            content,
            mimeType = "application/dxf"
        )
        val cacheFile = File(context.cacheDir, fileName)
        FileOutputStream(cacheFile).use { it.write(content.toByteArray(Charsets.UTF_8)) }
        val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/dxf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک DXF — ذخیره در Documents/AdelAssistant"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun saveAndShareKml(context: Context, content: String, originalName: String) {
    try {
        val base = originalName.substringBeforeLast(".").ifBlank { "map" }
        val fileName = "${base}_map.kml"
        FileExport.exportTextToDocuments(
            context, fileName, content,
            mimeType = "application/vnd.google-earth.kml+xml"
        )
        val cacheFile = File(context.cacheDir, fileName)
        FileOutputStream(cacheFile).use { it.write(content.toByteArray(Charsets.UTF_8)) }
        val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.google-earth.kml+xml"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک KML"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
