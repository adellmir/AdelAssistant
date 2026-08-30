package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import kotlin.math.abs

@Composable
fun TunnelStatusScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "وضعیت تونل", color = color, onBack = onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("وضعیت کلی", "بازه‌ای").forEachIndexed { i, label ->
                val selected = i == tab
                Surface(
                    modifier = Modifier.weight(1f).clickable { tab = i },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) color.copy(alpha = 0.18f) else Color.Transparent
                ) {
                    Text(label, modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center,
                        color = if (selected) color else Color(0xFFAAB697), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (tab == 0) OverallStatus(context, color) else RangeStatus(context, color)
    }
}

@Composable
private fun OverallStatus(context: android.content.Context, color: Color) {
    // مرتب‌سازی شفت‌ها/دهانه‌ها بر اساس کیلومتراژ ثابت برای تعیین «جبهه‌ی مقابل»
    val shafts = TunnelReportStore.allShafts(context).sortedBy { it.fixedKm }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        items(shafts.filter { it.type == "شفت" }) { s ->
            val idx = shafts.indexOf(s)
            val nextEntry = shafts.getOrNull(idx + 1)
            val prevEntry = shafts.getOrNull(idx - 1)

            val kmSide1 = TunnelReportStore.currentKm(context, s.name, "1") // سمت بیشتر
            val kmSide0 = TunnelReportStore.currentKm(context, s.name, "0") // سمت کمتر

            val targetSide1 = nextEntry?.let {
                if (it.type == "شفت") TunnelReportStore.currentKm(context, it.name, "0") else it.fixedKm
            } ?: s.fixedKm
            val targetSide0 = prevEntry?.let {
                if (it.type == "شفت") TunnelReportStore.currentKm(context, it.name, "1") else it.fixedKm
            } ?: s.fixedKm

            val aA = abs(kmSide1 - s.fixedKm) // پیشرفت سمت بیشتر
            val bA = abs(targetSide1 - kmSide1) // مانده سمت بیشتر
            val aC = abs(s.fixedKm - kmSide0) // پیشرفت سمت کمتر
            val bC = abs(kmSide0 - targetSide0) // مانده سمت کمتر

            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("%.1f".format(bC), style = MaterialTheme.typography.bodySmall)
                        Text(s.name, style = MaterialTheme.typography.titleSmall)
                        Text("%.1f".format(bA), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("سمت کمتر: پیشرفت %.1f — سمت بیشتر: پیشرفت %.1f".format(aC, aA),
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C8A6B))
                }
            }
        }
    }
}

@Composable
private fun RangeStatus(context: android.content.Context, color: Color) {
    var fromDay by remember { mutableStateOf("") }
    var fromMonth by remember { mutableStateOf("") }
    var fromYear by remember { mutableStateOf("1405") }
    var toDay by remember { mutableStateOf("") }
    var toMonth by remember { mutableStateOf("") }
    var toYear by remember { mutableStateOf("1405") }
    var results by remember { mutableStateOf(listOf<String>()) }

    Column {
        Text("از", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = fromDay, onValueChange = { fromDay = it }, label = { Text("روز") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = fromMonth, onValueChange = { fromMonth = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = fromYear, onValueChange = { fromYear = it }, label = { Text("سال") }, modifier = Modifier.weight(1f))
        }
        Text("الی", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = toDay, onValueChange = { toDay = it }, label = { Text("روز") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = toMonth, onValueChange = { toMonth = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = toYear, onValueChange = { toYear = it }, label = { Text("سال") }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val fromKey = "%s%02d%02d".format(fromYear, fromMonth.toIntOrNullFa() ?: 0, fromDay.toIntOrNullFa() ?: 0)
                val toKey = "%s%02d%02d".format(toYear, toMonth.toIntOrNullFa() ?: 0, toDay.toIntOrNullFa() ?: 0)
                results = TunnelReportStore.allShafts(context).filter { it.type == "شفت" }.map { s ->
                    val fromKm1 = TunnelReportStore.lastKmBefore(context, s.name, "1", fromKey) ?: s.fixedKm
                    val toKm1 = TunnelReportStore.lastKmBefore(context, s.name, "1", toKey) ?: s.fixedKm
                    val fromKm0 = TunnelReportStore.lastKmBefore(context, s.name, "0", fromKey) ?: s.fixedKm
                    val toKm0 = TunnelReportStore.lastKmBefore(context, s.name, "0", toKey) ?: s.fixedKm
                    val a = abs(toKm1 - fromKm1)
                    val c = abs(toKm0 - fromKm0)
                    "%.1f ← ${s.name} → %.1f".format(c, a)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text("نمایش") }

        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results) { r ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Text(r, modifier = Modifier.padding(12.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
