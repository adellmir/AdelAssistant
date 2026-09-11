package com.adel.assistant.ui.screens

import android.content.Intent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.CsvStore
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.ReportEntry
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.XlsxReportWriter
import com.adel.assistant.data.filterNumericInput
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

private val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

private data class PreviewRow(
    val shaft: String, val side: String, val pointNo: String, val lengthCm: Double,
    val km: Double, val dailyProgress: Double, val shaftProgress: Double, val remaining: Double,
    val deviation: String = "", val collapse: String = ""
)

@Composable
fun DailyReportScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    val today = remember { CalendarStore.todayJalali() }
    var day by remember { mutableStateOf(today.third.toString()) }
    var month by remember { mutableStateOf(today.second.toString()) }
    var year by remember { mutableStateOf(today.first.toString()) }

    var shaft by remember { mutableStateOf("") }
    var side by remember { mutableStateOf("") }
    var pointNo by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var deviation by remember { mutableStateOf("") }
    var collapse by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf(-1) }

    var rows by remember { mutableStateOf(listOf<PreviewRow>()) }
    var showMenu by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    LaunchedEffect(shaft, side) {
        if (shaft.isNotBlank() && side.isNotBlank() && editingIndex < 0) {
            val suggested = TunnelReportStore.suggestedNextPointNo(context, shaft, side)
            if (suggested != null) pointNo = suggested.toString()
        }
    }

    val weekday = remember(day, month) { CalendarStore.weekdayFor(context, day, month) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = input.bufferedReader().readText()
                    CsvStore.importRawText(context, "survey_tunnel_report", text)
                    statusMsg = "فایل گزارش‌ها با موفقیت وارد شد"
                }
            } catch (e: Exception) { statusMsg = "خطا در وارد کردن فایل" }
        }
    }

    fun todayDateKey() = "%s%02d%02d".format(year, month.toIntOrNullFa() ?: 0, day.toIntOrNullFa() ?: 0)

    fun loadDay() {
        val existing = TunnelReportStore.entriesForDate(context, year, month, day)
        rows = existing.map { e ->
            PreviewRow(e.shaft, e.side, e.pointNo, e.lengthCm, e.km, e.dailyProgress, e.shaftProgress, e.remaining, e.deviation, e.collapse)
        }
        statusMsg = if (rows.isEmpty()) "برای این تاریخ رکوردی ثبت نشده" else "${rows.size} ردیف بارگذاری شد"
    }

    fun addOrUpdateRow() {
        val len = length.toDoubleOrNullFa() ?: return
        if (len < 0) { statusMsg = "طول باید عدد مثبت باشد (به سانتی‌متر)"; return }
        if (shaft.isBlank() || side.isBlank() || pointNo.isBlank()) {
            statusMsg = "شفت، سمت و شماره نقطه الزامی‌اند"; return
        }
        val v = TunnelReportStore.computeEntryValues(context, shaft, side, pointNo, len, todayDateKey())
        if (v == null) {
            statusMsg = "نقطه‌ی $pointNo در فایل نقاط پیدا نشد — اول از «نقاط تونل» ثبتش کن"
            return
        }
        val newRow = PreviewRow(shaft, side, pointNo, len, v.km, v.dailyProgress, v.shaftProgress, v.remaining, deviation, collapse)
        rows = if (editingIndex >= 0) {
            rows.toMutableList().also { it[editingIndex] = newRow }
        } else {
            rows.filterNot { it.shaft == shaft && it.side == side } + newRow
        }
        shaft = ""; side = ""; pointNo = ""; length = ""; deviation = ""; collapse = ""; editingIndex = -1
        statusMsg = ""
    }

    fun startEdit(i: Int) {
        val r = rows[i]
        shaft = r.shaft; side = r.side; pointNo = r.pointNo
        length = r.lengthCm.toString()
        deviation = r.deviation; collapse = r.collapse
        editingIndex = i
    }

    fun deleteRow(i: Int) {
        rows = rows.toMutableList().also { it.removeAt(i) }
    }

    fun registerAll() {
        val newEntries = rows.map {
            ReportEntry(year, month, day, it.shaft, it.side, it.pointNo, it.lengthCm, it.deviation, it.collapse,
                it.km, it.dailyProgress, it.shaftProgress, it.remaining)
        }
        TunnelReportStore.replaceEntriesForDate(context, year, month, day, newEntries)
        statusMsg = "ثبت شد"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        Box {
            ScreenTopBar(title = "گزارش روزانه", color = color, onBack = onBack)
            IconButton(onClick = { showMenu = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Filled.Settings, contentDescription = "ایمپورت/اکسپورت", tint = Color(0xFFAAB697))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("وارد کردن") }, onClick = {
                    showMenu = false
                    importLauncher.launch(arrayOf("text/*", "*/*"))
                })
                DropdownMenuItem(text = { Text("خارج کردن") }, onClick = {
                    showMenu = false
                    val text = FileExport.readAsCsvText(context, "survey_tunnel_report")
                    val uri = FileExport.exportTextToDocuments(context, "survey_tunnel_report.csv", text)
                    statusMsg = if (uri != null) "در Documents/AdelAssistant ذخیره شد" else "خطا در خارج کردن"
                })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = day, onValueChange = { day = filterNumericInput(it) }, label = { Text("روز") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = month, onValueChange = { month = filterNumericInput(it) }, label = { Text("ماه") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = year, onValueChange = { year = filterNumericInput(it) }, label = { Text("سال") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { loadDay() }) {
                Icon(Icons.Filled.Search, contentDescription = "نمایش گزارش این روز", tint = color)
            }
        }
        if (weekday != null) {
            Text(weekday, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697), modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("افزودن پیشرفت شفت", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            OutlinedTextField(
                value = shaft, onValueChange = { shaft = filterNumericInput(it) }, label = { Text("شفت") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(value = side, onValueChange = { side = it }, label = { Text("سمت") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = pointNo, onValueChange = { pointNo = filterNumericInput(it) }, label = { Text("شماره نقطه") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = length, onValueChange = { length = filterNumericInput(it) }, label = { Text("طول (سانتی‌متر)") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { addOrUpdateRow() }) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن", tint = color)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = deviation, onValueChange = { deviation = it }, label = { Text("انحراف") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = collapse, onValueChange = { collapse = it }, label = { Text("ریزش") }, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("پیش‌نمایش گزارش", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows.size) { i ->
                val r = rows[i]
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${r.shaft}به${r.side} (ن${r.pointNo})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { startEdit(i) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = Color(0xFF7C8A6B), modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { deleteRow(i) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "پاک کردن", tint = Color(0xFFC2685E), modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(
                            formatEn("ک:%.3f  پ.روز:%.3f  پ.شفت:%.3f  مانده:%.3f", r.km, r.dailyProgress, r.shaftProgress, r.remaining),
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C8A6B)
                        )
                    }
                }
            }
        }

        if (statusMsg.isNotBlank()) {
            Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697), modifier = Modifier.padding(vertical = 4.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
            Button(
                onClick = { registerAll() },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("ثبت") }
            OutlinedButton(
                onClick = {
                    val uri = XlsxReportWriter.generate(context, year, month, day, weekday)
                    if (uri != null) {
                        statusMsg = "فایل در Documents/AdelAssistant ذخیره شد"
                        try {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "اشتراک گزارش روزانه"))
                        } catch (_: Exception) {
                            statusMsg = "ذخیره شد (اشتراک ممکن نشد)"
                        }
                    } else {
                        statusMsg = "خطا در ساخت فایل"
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("صدور گزارش") }
        }
    }
}