package com.adel.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import com.adel.assistant.data.MonAnalysisRow
import com.adel.assistant.data.MonEpoch
import com.adel.assistant.data.MonPoint
import com.adel.assistant.data.MonProject
import com.adel.assistant.data.MonitoringAnalyzer
import com.adel.assistant.data.MonitoringExport
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.MonitoringStore
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.ToolPrimary
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(color: Color = ToolPrimary, onBack: () -> Unit) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf(MonitoringStore.loadAll(context)) }
    var open by remember { mutableStateOf<MonProject?>(null) }

    var name by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var reportNo by remember { mutableStateOf("") }
    val todayJ = remember { CalendarStore.todayJalali() }
    var day by remember { mutableStateOf(todayJ.third.toString()) }
    var month by remember { mutableStateOf(todayJ.second.toString()) }
    var year by remember { mutableStateOf(todayJ.first.toString()) }
    var query by remember { mutableStateOf("") }

    fun refresh() { projects = MonitoringStore.loadAll(context) }

    if (open != null) {
        ProjectDetail(
            project = open!!,
            color = color,
            onBack = {
                MonitoringStore.upsert(context, open!!)
                open = null
                refresh()
            },
            onChange = {
                open = it
                MonitoringStore.upsert(context, it)
                refresh()
            }
        )
        return
    }

    val filtered = remember(projects, query) {
        val q = query.trim()
        val base = if (q.isBlank()) projects
        else projects.filter { it.name.contains(q, true) || it.client.contains(q, true) }
        base.sortedByDescending { it.updatedAt }
    }

    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    Column(Modifier.fillMaxSize().background(Background).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = color)
            }
            Text("پایش", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("نام پروژه") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(client, { client = it }, label = { Text("کارفرما") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        OutlinedTextField(reportNo, { reportNo = it }, label = { Text("شماره گزارش") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(day, { day = it }, label = { Text("روز") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
            OutlinedTextField(month, { month = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
            OutlinedTextField(year, { year = it }, label = { Text("سال") }, modifier = Modifier.weight(1.2f), singleLine = true, keyboardOptions = numKb)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val p = MonProject(
                        name = name.trim(), client = client.trim(), reportNo = reportNo.trim(),
                        day = day, month = month, year = year
                    )
                    MonitoringStore.upsert(context, p)
                    name = ""; client = ""; reportNo = ""
                    refresh()
                    open = p
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("ثبت") }
            OutlinedButton(onClick = { query = name.ifBlank { client } }, modifier = Modifier.weight(1f)) {
                Text("جستجو")
            }
            OutlinedButton(onClick = {
                val text = MonitoringStore.exportCsv(context)
                val uri = FileExport.exportTextToDocuments(context, "monitoring_backup.csv", text, "text/csv")
                // silent
            }, modifier = Modifier.weight(1f)) {
                Text("پشتیبان")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("پروژه‌ها (جدید → قدیم)", color = Color(0xFFAAB697), fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { p ->
                Card(
                    onClick = { open = p },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E241A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${p.client} · ${p.year}/${p.month}/${p.day} · پایش‌ها: ${p.epochs.size}", color = Color(0xFF9BA888), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectDetail(
    project: MonProject,
    color: Color,
    onBack: () -> Unit,
    onChange: (MonProject) -> Unit
) {
    val context = LocalContext.current
    var p by remember { mutableStateOf(project) }
    var showNewEpoch by remember { mutableStateOf(false) }
    var showBase by remember { mutableStateOf(false) }
    var analyzeEpoch by remember { mutableStateOf<MonEpoch?>(null) }
    var message by remember { mutableStateOf("") }

    val templatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            val path = MonitoringStore.saveTemplate(context, p.id, bytes)
            p = p.copy(templatePath = path)
            onChange(p)
            message = "قالب ذخیره شد"
        } catch (_: Exception) {
            message = "خطا در خواندن قالب"
        }
    }

    if (analyzeEpoch != null) {
        AnalyzePage(
            project = p,
            epoch = analyzeEpoch!!,
            color = color,
            onBack = { analyzeEpoch = null },
            onSaved = { ep ->
                p = p.copy(epochs = p.epochs.map { if (it.id == ep.id) ep else it }.toMutableList())
                onChange(p)
            }
        )
        return
    }

    if (showBase) {
        BaseEditor(
            project = p,
            color = color,
            onBack = { showBase = false },
            onSave = {
                p = it
                onChange(it)
                showBase = false
                message = "نقاط پایه ذخیره شد"
            }
        )
        return
    }

    if (showNewEpoch) {
        NewEpochDialog(
            color = color,
            onDismiss = { showNewEpoch = false },
            onCreated = { ep ->
                p = p.copy(epochs = (listOf(ep) + p.epochs).toMutableList())
                onChange(p)
                showNewEpoch = false
                analyzeEpoch = ep
            }
        )
    }

    Column(Modifier.fillMaxSize().background(Background).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                onChange(p)
                onBack()
            }) { Icon(Icons.Filled.ArrowBack, null, tint = color) }
            Column(Modifier.weight(1f)) {
                Text(p.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(p.client, color = Color(0xFF9BA888), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { showNewEpoch = true }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.weight(1f)) {
                Text("ثبت پایش جدید", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { showBase = true }, modifier = Modifier.weight(1f)) {
                Text("نقاط پایه", fontSize = 12.sp)
            }
            OutlinedButton(onClick = {
                templatePicker.launch(arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "*/*"
                ))
            }, modifier = Modifier.weight(1f)) {
                Text("قالب گزارش", fontSize = 12.sp)
            }
        }
        if (message.isNotBlank()) {
            Text(message, color = Color(0xFFB0B8A8), fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        Text("پایش‌ها · پایه: ${p.basePoints.size} نقطه · قالب: ${if (p.templatePath != null) "دارد" else "ندارد"}", color = Color(0xFFAAB697), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(p.epochs.sortedByDescending { it.createdAt }, key = { it.id }) { ep ->
                val idx = p.epochs.sortedBy { it.createdAt }.indexOfFirst { it.id == ep.id } + 1
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E241A)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("پایش ${MonitoringAnalyzer.persianOrdinal(idx)} · ${ep.year}/${ep.month}/${ep.day}", color = Color.White)
                        Text("${ep.points.size} نقطه", color = Color(0xFF9BA888), fontSize = 12.sp)
                        Row {
                            TextButton(onClick = { analyzeEpoch = ep }) { Text("ویرایش/بررسی", color = color) }
                            TextButton(onClick = {
                                if (p.basePoints.isEmpty()) {
                                    message = "ابتدا نقاط پایه را وارد کنید"
                                    return@TextButton
                                }
                                if (p.templatePath == null) {
                                    message = "ابتدا قالب گزارش را وارد کنید"
                                    return@TextButton
                                }
                                val rows = MonitoringAnalyzer.analyze(p.basePoints, ep.points)
                                val uri = MonitoringExport.export(context, p, ep, rows, idx)
                                if (uri != null) {
                                    message = "گزارش ذخیره شد"
                                    MonitoringExport.shareUri(context, uri)
                                } else message = "خطا در صدور گزارش"
                            }) { Text("صدور گزارش", color = Color(0xFFFFB74D)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewEpochDialog(color: Color, onDismiss: () -> Unit, onCreated: (MonEpoch) -> Unit) {
    val context = LocalContext.current
    val cal = Calendar.getInstance()
    var day by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH).toString()) }
    var month by remember { mutableStateOf((cal.get(Calendar.MONTH) + 1).toString()) }
    var year by remember { mutableStateOf("1405") }
    var points by remember { mutableStateOf<List<MonPoint>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins)).readText()
            } ?: return@rememberLauncherForActivityResult
            val parsed = MonitoringAnalyzer.parsePointsFile(text)
            points = parsed
            msg = "${parsed.size} نقطه خوانده شد"
        } catch (_: Exception) {
            msg = "خطا در خواندن فایل"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت پایش جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(day, { day = it }, label = { Text("روز") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                    OutlinedTextField(month, { month = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                    OutlinedTextField(year, { year = it }, label = { Text("سال") }, modifier = Modifier.weight(1.2f), singleLine = true, keyboardOptions = numKb)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        filePicker.launch(arrayOf("text/*", "text/csv", "text/plain", "*/*"))
                    }) { Text("ورود برداشت‌ها") }
                    Button(
                        onClick = {
                            if (points.isEmpty()) {
                                msg = "ابتدا فایل برداشت را بخوانید"
                                return@Button
                            }
                            onCreated(
                                MonEpoch(
                                    day = day, month = month, year = year,
                                    points = points.toMutableList()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) { Text("بررسی") }
                }
                if (msg.isNotBlank()) Text(msg, fontSize = 12.sp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun BaseEditor(
    project: MonProject,
    color: Color,
    onBack: () -> Unit,
    onSave: (MonProject) -> Unit
) {
    val context = LocalContext.current
    var client by remember { mutableStateOf(project.client) }
    var points by remember { mutableStateOf(project.basePoints.map { it.copy() }.toMutableList()) }
    var msg by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it)).readText() } ?: return@rememberLauncherForActivityResult
            val parsed = MonitoringAnalyzer.parsePointsFile(text).map {
                it.copy(isBm = it.name.startsWith("S", true) || it.name.startsWith("BM", true))
            }.toMutableList()
            points = parsed
            msg = "${parsed.size} نقطه پایه"
        } catch (_: Exception) {
            msg = "خطا در فایل"
        }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = color) }
            Text("نقاط پایه (Base)", color = Color.White, modifier = Modifier.weight(1f))
            Button(onClick = {
                onSave(project.copy(client = client, basePoints = points))
            }, colors = ButtonDefaults.buttonColors(containerColor = color)) { Text("ثبت") }
        }
        OutlinedTextField(client, { client = it }, label = { Text("کارفرما") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { filePicker.launch(arrayOf("text/*", "*/*")) }) { Text("ورود فایل") }
            if (msg.isNotBlank()) Text(msg, color = Color(0xFFB0B8A8), fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Text("Bm", color = Color.Gray, modifier = Modifier.width(48.dp), fontSize = 11.sp)
            Text("نام", color = Color.Gray, modifier = Modifier.width(72.dp), fontSize = 11.sp)
            Text("X", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 11.sp)
            Text("Y", color = Color.Gray, modifier = Modifier.width(100.dp), fontSize = 11.sp)
            Text("Z", color = Color.Gray, modifier = Modifier.width(80.dp), fontSize = 11.sp)
        }
        LazyColumn {
            items(points.size) { i ->
                val pt = points[i]
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(pt.isBm, {
                        points = points.toMutableList().also { list -> list[i] = pt.copy(isBm = it) }
                    }, modifier = Modifier.width(48.dp))
                    Text(pt.name, color = Color.White, modifier = Modifier.width(72.dp), fontSize = 12.sp)
                    Text(String.format(Locale.US, "%.4f", pt.x), color = Color(0xFFCFD8C8), modifier = Modifier.width(100.dp), fontSize = 11.sp)
                    Text(String.format(Locale.US, "%.4f", pt.y), color = Color(0xFFCFD8C8), modifier = Modifier.width(100.dp), fontSize = 11.sp)
                    Text(String.format(Locale.US, "%.4f", pt.z), color = Color(0xFFCFD8C8), modifier = Modifier.width(80.dp), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AnalyzePage(
    project: MonProject,
    epoch: MonEpoch,
    color: Color,
    onBack: () -> Unit,
    onSaved: (MonEpoch) -> Unit
) {
    val context = LocalContext.current
    var ep by remember { mutableStateOf(epoch) }
    val rows = remember(ep.points, project.basePoints) {
        MonitoringAnalyzer.analyze(project.basePoints, ep.points)
    }
    var msg by remember { mutableStateOf("") }
    val idx = project.epochs.sortedBy { it.createdAt }.indexOfFirst { it.id == ep.id }.let { if (it < 0) project.epochs.size else it + 1 }

    Column(Modifier.fillMaxSize().background(Background).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                onSaved(ep)
                onBack()
            }) { Icon(Icons.Filled.ArrowBack, null, tint = color) }
            Text("بررسی پایش", color = Color.White, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (project.templatePath == null) {
                        msg = "قالب گزارش را در پروژه وارد کنید"
                        return@Button
                    }
                    val uri = MonitoringExport.export(context, project, ep, rows, idx)
                    if (uri != null) {
                        msg = "گزارش ذخیره شد"
                        MonitoringExport.shareUri(context, uri)
                    } else msg = "خطا در صدور"
                },
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) { Text("صدور گزارش") }
        }
        if (msg.isNotBlank()) Text(msg, color = Color(0xFFB0B8A8), fontSize = 12.sp)
        Text("سبز ≤3 · نارنجی 4–5 · قرمز ≥6 mm  |  + داخل گود", color = Color(0xFF7A8570), fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            HeaderCell("نقطه", 64)
            HeaderCell("نوع", 40)
            HeaderCell("X دوره", 88)
            HeaderCell("Y دوره", 88)
            HeaderCell("Z دوره", 72)
            HeaderCell("داخل/خارج", 72)
            HeaderCell("نشست", 56)
            HeaderCell("سه‌بعدی", 56)
        }
        LazyColumn {
            items(rows) { r ->
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    DataCell(r.name, 64)
                    DataCell(if (r.isBm) "Bm" else "TP", 40)
                    DataCell(r.epX?.let { String.format(Locale.US, "%.3f", it) } ?: "", 88)
                    DataCell(r.epY?.let { String.format(Locale.US, "%.3f", it) } ?: "", 88)
                    DataCell(r.epZ?.let { String.format(Locale.US, "%.3f", it) } ?: "", 72)
                    ValCell(r.inOutMm, 72)
                    ValCell(r.settleMm, 56)
                    ValCell(r.d3dMm, 56)
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(t: String, w: Int) {
    Text(t, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.width(w.dp).padding(2.dp))
}

@Composable
private fun DataCell(t: String, w: Int) {
    Text(t, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(w.dp).padding(2.dp))
}

@Composable
private fun ValCell(v: Double?, w: Int) {
    val text = v?.let { String.format(Locale.US, "%.1f", it) } ?: ""
    val c = when {
        v == null -> Color.Gray
        abs(v) <= 3.0 -> Color(0xFF81C995)
        abs(v) < 6.0 -> Color(0xFFFFB74D)
        else -> Color(0xFFE57373)
    }
    Text(text, color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(w.dp).padding(2.dp))
}
