package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.data.ReportEntry
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import kotlin.math.abs

private data class PreviewRow(
    val shaft: String, val side: String, val pointNo: String,
    val prevKm: Double, val todayKm: Double, val dig: Double, val progress: Double,
    val isEditing: Boolean = false
)

@Composable
fun DailyReportScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current

    val today = remember { CalendarStore.todayJalali() }
    var day by remember { mutableStateOf(today.third.toString()) }
    var month by remember { mutableStateOf(today.second.toString()) }
    var year by remember { mutableStateOf(today.first.toString()) }

    var shaft by remember { mutableStateOf("") }
    var side by remember { mutableStateOf("") }
    var pointNo by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf(-1) }

    var rows by remember { mutableStateOf(listOf<PreviewRow>()) }
    var showMenu by remember { mutableStateOf(false) }

    val weekday = remember(day, month) { CalendarStore.weekdayFor(context, day, month) }

    fun loadDay() {
        val existing = TunnelReportStore.entriesForDate(context, year, month, day)
        val existingKeys = existing.map { it.key }.toSet()
        val fromExisting = existing.map { e ->
            val prevKm = TunnelReportStore.lastKmBefore(context, e.shaft, e.side,
                "%s%02d%02d".format(year, month.toIntOrNullFa() ?: 0, (day.toIntOrNullFa() ?: 0) - 1))
                ?: TunnelReportStore.shaftFixedKm(context, e.shaft) ?: 0.0
            val todayKm = prevKm + e.length
            val fixedKm = TunnelReportStore.shaftFixedKm(context, e.shaft) ?: prevKm
            PreviewRow(e.shaft, e.side, e.pointNo, prevKm, todayKm, abs(e.length), abs(todayKm - fixedKm))
        }
        // بقیه‌ی شفت-سمت‌هایی که امروز کاری روشون نشده: بدون تغییر
        val allKeys = TunnelReportStore.allShafts(context).flatMap { s -> listOf("${s.name}-0", "${s.name}-1") }
        val untouched = allKeys.filterNot { existingKeys.contains(it) }.mapNotNull { key ->
            val parts = key.split("-")
            if (parts.size < 2) return@mapNotNull null
            val sh = parts[0]; val sd = parts[1]
            val km = TunnelReportStore.currentKm(context, sh, sd)
            val fixedKm = TunnelReportStore.shaftFixedKm(context, sh) ?: km
            PreviewRow(sh, sd, "", km, km, 0.0, abs(km - fixedKm))
        }
        rows = fromExisting + untouched
    }

    LaunchedEffect(day, month, year) { loadDay() }

    fun addOrUpdateRow() {
        val len = length.toDoubleOrNullFa() ?: return
        if (shaft.isBlank() || side.isBlank()) return
        val prevKm = TunnelReportStore.lastKmBefore(context, shaft, side,
            "%s%02d%02d".format(year, month.toIntOrNullFa() ?: 0, (day.toIntOrNullFa() ?: 0) - 1))
            ?: TunnelReportStore.shaftFixedKm(context, shaft) ?: 0.0
        val todayKm = prevKm + len
        val fixedKm = TunnelReportStore.shaftFixedKm(context, shaft) ?: prevKm
        val newRow = PreviewRow(shaft, side, pointNo, prevKm, todayKm, abs(len), abs(todayKm - fixedKm))
        rows = if (editingIndex >= 0) {
            rows.toMutableList().also { it[editingIndex] = newRow }
        } else {
            rows.filterNot { it.shaft == shaft && it.side == side } + newRow
        }
        shaft = ""; side = ""; pointNo = ""; length = ""; editingIndex = -1
    }

    fun startEdit(i: Int) {
        val r = rows[i]
        shaft = r.shaft; side = r.side; pointNo = r.pointNo
        length = (r.todayKm - r.prevKm).toString()
        editingIndex = i
    }

    fun registerAll() {
        val newEntries = rows.filter { it.dig != 0.0 || it.pointNo.isNotBlank() }.map {
            ReportEntry(year, month, day, it.shaft, it.side, it.pointNo, it.todayKm - it.prevKm)
        }
        TunnelReportStore.replaceEntriesForDate(context, year, month, day, newEntries)
        loadDay()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        Box {
            ScreenTopBar(title = "گزارش روزانه", color = color, onBack = onBack)
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "ایمپورت/اکسپورت", tint = Color(0xFFAAB697))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("وارد کردن") }, onClick = { showMenu = false })
                DropdownMenuItem(text = { Text("خارج کردن") }, onClick = { showMenu = false })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("روز") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("سال") }, modifier = Modifier.weight(1f))
        }
        if (weekday != null) {
            Text(weekday, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697), modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("افزودن پیشرفت شفت", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            OutlinedTextField(value = shaft, onValueChange = { shaft = it }, label = { Text("شفت") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = side, onValueChange = { side = it }, label = { Text("سمت") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = pointNo, onValueChange = { pointNo = it }, label = { Text("شماره نقطه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = length, onValueChange = { length = it }, label = { Text("طول") }, modifier = Modifier.weight(1f))
            IconButton(onClick = { addOrUpdateRow() }) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن", tint = color)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("پیش‌نمایش گزارش", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows.size) { i ->
                val r = rows[i]
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${r.shaft}به${r.side}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("ق:%.1f".format(r.prevKm), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("ا:%.1f".format(r.todayKm), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("ح:%.1f".format(r.dig), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("پ:%.1f".format(r.progress), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { startEdit(i) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = Color(0xFF7C8A6B), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
            Button(
                onClick = { registerAll() },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("ثبت") }
            OutlinedButton(onClick = { /* خروجی اکسل واقعی نیاز به قالب دارد */ }, modifier = Modifier.weight(1f)) {
                Text("صدور گزارش")
            }
        }
    }
}
