package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.normalizeSide
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import kotlin.math.abs

/** کیلومتر «جبهه‌ی مقابل» یک شفت-سمت: اگر سمت شماره‌ی شفت دیگری‌ست، جبهه‌ی متحرک همان شفت؛ اگر start/end است، کیلومتر ثابت دهانه */
private fun oppositeKm(context: android.content.Context, shaft: String, side: String): Double {
    val s = normalizeSide(side)
    return if (s == "start" || s == "end") {
        TunnelReportStore.shaftFixedKm(context, s) ?: 0.0
    } else {
        TunnelReportStore.currentKm(context, s, shaft)
    }
}

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
        ScreenTopBar(title = "پیشرفت تونل", color = color, onBack = onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("پیشرفت کلی", "بازه‌ای").forEachIndexed { i, label ->
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
        if (tab == 0) OverallStatus(context) else RangeStatus(context, color)
    }
}

@Composable
private fun OverallStatus(context: android.content.Context) {
    val byShaft = TunnelReportStore.TUNNEL_LAYOUT.groupBy { it.first }.toSortedMap()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        items(byShaft.entries.toList()) { (shaftName, sides) ->
            // سمت کمتر و بیشتر
            val lessSide = sides.firstOrNull { (sh, side) -> TunnelReportStore.isTowardLessSide(sh, side) }
            val moreSide = sides.firstOrNull { (sh, side) -> !TunnelReportStore.isTowardLessSide(sh, side) }
            fun sideBlock(shaft: String, side: String): Triple<String, Double, Pair<Double, Double>> {
                val km = TunnelReportStore.currentKm(context, shaft, side)
                val fixedKm = TunnelReportStore.shaftFixedKm(context, shaft) ?: km
                val progress = abs(km - fixedKm)
                val remaining = abs(km - oppositeKm(context, shaft, side))
                return Triple(side, km, progress to remaining)
            }
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // چپ: جهت بیشتر
                    Column(Modifier.weight(1f)) {
                        if (moreSide != null) {
                            val (side, km, pr) = sideBlock(moreSide.first, moreSide.second)
                            Text(side, style = MaterialTheme.typography.bodySmall, color = Color(0xFF81C995))
                            Text(formatEn("%.3f", km), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9BA888))
                            Text(formatEn("پ:%.1f / م:%.1f", pr.first, pr.second), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B8A8))
                        }
                    }
                    // وسط: شماره شفت
                    Text(
                        shaftName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    // راست: جهت کمتر
                    Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        if (lessSide != null) {
                            val (side, km, pr) = sideBlock(lessSide.first, lessSide.second)
                            Text(side, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D))
                            Text(formatEn("%.3f", km), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9BA888))
                            Text(formatEn("پ:%.1f / م:%.1f", pr.first, pr.second), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B8A8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeStatus(context: android.content.Context, color: Color) {
    val allEntries = remember { TunnelReportStore.allEntries(context) }
    val first = allEntries.minByOrNull { it.dateSortKey }
    val last = allEntries.maxByOrNull { it.dateSortKey }
    var fromDay by remember { mutableStateOf(first?.day ?: "") }
    var fromMonth by remember { mutableStateOf(first?.month ?: "") }
    var fromYear by remember { mutableStateOf(first?.year ?: "1405") }
    var toDay by remember { mutableStateOf(last?.day ?: "") }
    var toMonth by remember { mutableStateOf(last?.month ?: "") }
    var toYear by remember { mutableStateOf(last?.year ?: "1405") }
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
                results = TunnelReportStore.TUNNEL_LAYOUT.map { (shaft, side) ->
                    val fromKm = TunnelReportStore.kmOnOrBefore(context, shaft, side, fromKey)
                    val toKm = TunnelReportStore.kmOnOrBefore(context, shaft, side, toKey)
                    formatEn("شفت %s سمت %s: %.2f", shaft, side, kotlin.math.abs(toKm - fromKm))
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
