package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.ReportEntry
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import kotlin.math.abs

private data class PreviewRow(
    val shaft: String, val side: String, val prevKm: Double, val todayKm: Double,
    val dig: Double, val progress: Double
)

@Composable
fun DailyReportScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("1405") }

    var shaft by remember { mutableStateOf("") }
    var side by remember { mutableStateOf("") }
    var pointNo by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }

    var rows by remember { mutableStateOf(listOf<PreviewRow>()) }

    fun buildDateKey() = "%s%02d%02d".format(
        year.ifBlank { "1405" }, month.toIntOrNull() ?: 0, day.toIntOrNull() ?: 0
    )

    fun addRow() {
        val len = length.toDoubleOrNull() ?: return
        if (shaft.isBlank() || side.isBlank()) return
        val prevKm = TunnelReportStore.lastKmBefore(context, shaft, side, buildDateKey())
            ?: TunnelReportStore.shaftFixedKm(context, shaft, side) ?: 0.0
        val todayKm = prevKm + len
        val fixedKm = TunnelReportStore.shaftFixedKm(context, shaft, side) ?: prevKm
        val progress = abs(todayKm - fixedKm)
        rows = rows + PreviewRow(shaft, side, prevKm, todayKm, abs(len), progress)
        shaft = ""; side = ""; pointNo = ""; length = ""
    }

    fun registerAll() {
        rows.forEach { r ->
            val len = r.todayKm - r.prevKm
            TunnelReportStore.saveEntry(
                context,
                ReportEntry(year, month, day, r.shaft, r.side, pointNo, len)
            )
        }
        rows = emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "گزارش روزانه", color = color, onBack = onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("روز") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("سال") }, modifier = Modifier.weight(1f))
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
            IconButton(onClick = { addRow() }) {
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
                        Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = Color(0xFF7C8A6B), modifier = Modifier.size(16.dp))
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
            OutlinedButton(onClick = { /* خروجی — نیازمند قالب واقعی، فعلاً ساده */ }, modifier = Modifier.weight(1f)) {
                Text("صدور گزارش")
            }
        }
    }
}
