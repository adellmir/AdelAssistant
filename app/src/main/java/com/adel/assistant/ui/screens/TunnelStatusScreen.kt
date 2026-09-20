package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.normalizeSide
import com.adel.assistant.data.sideDisplayName
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import kotlin.math.abs

/** کیلومتر جبهه مقابل */
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
    val allEntries = remember { TunnelReportStore.allEntries(context) }
    val first = allEntries.minByOrNull { it.dateSortKey }
    val last = allEntries.maxByOrNull { it.dateSortKey }

    var fromDay by remember { mutableStateOf(first?.day ?: "") }
    var fromMonth by remember { mutableStateOf(first?.month ?: "") }
    var fromYear by remember { mutableStateOf(first?.year ?: "1405") }
    var toDay by remember { mutableStateOf(last?.day ?: "") }
    var toMonth by remember { mutableStateOf(last?.month ?: "") }
    var toYear by remember { mutableStateOf(last?.year ?: "1405") }
    var rangeMode by remember { mutableStateOf(false) }
    var rangeResults by remember { mutableStateOf(listOf<String>()) }

    fun showRange() {
        val fromKey = "%s%02d%02d".format(fromYear, fromMonth.toIntOrNullFa() ?: 0, fromDay.toIntOrNullFa() ?: 0)
        val toKey = "%s%02d%02d".format(toYear, toMonth.toIntOrNullFa() ?: 0, toDay.toIntOrNullFa() ?: 0)
        rangeResults = TunnelReportStore.TUNNEL_LAYOUT.map { (shaft, side) ->
            val fromKm = TunnelReportStore.kmOnOrBefore(context, shaft, side, fromKey)
            val toKm = TunnelReportStore.kmOnOrBefore(context, shaft, side, toKey)
            val label = sideDisplayName(side)
            formatEn("شفت %s — %s: %.2f m", shaft, label, abs(toKm - fromKm))
        }
        rangeMode = true
    }

    fun refreshOverall() {
        rangeMode = false
        rangeResults = emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "پیشرفت تونل", color = color, onBack = onBack)
        Spacer(Modifier.height(8.dp))

        // فیلدهای بازه‌ای + نمایش + رفرش
        Text("بازه تاریخی", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(fromDay, { fromDay = it }, label = { Text("از روز") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(fromMonth, { fromMonth = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(fromYear, { fromYear = it }, label = { Text("سال") }, modifier = Modifier.weight(1.1f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(toDay, { toDay = it }, label = { Text("تا روز") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(toMonth, { toMonth = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(toYear, { toYear = it }, label = { Text("سال") }, modifier = Modifier.weight(1.1f), singleLine = true)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Button(
                onClick = { showRange() },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("نمایش") }
            OutlinedButton(
                onClick = { refreshOverall() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("رفرش")
            }
        }

        if (rangeMode) {
            Text("پیشرفت بازه‌ای", style = MaterialTheme.typography.titleSmall, color = Color(0xFFAAB697))
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                items(rangeResults) { r ->
                    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                        Text(r, modifier = Modifier.padding(12.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            Text("پیشرفت کلی", style = MaterialTheme.typography.titleSmall, color = Color(0xFFAAB697))
            Spacer(Modifier.height(6.dp))
            OverallStatusList(context)
        }
    }
}

@Composable
private fun OverallStatusList(context: android.content.Context) {
    val byShaft = TunnelReportStore.TUNNEL_LAYOUT.groupBy { it.first }.toSortedMap()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        items(byShaft.entries.toList()) { (shaftName, sides) ->
            val lessSide = sides.firstOrNull { (sh, side) -> TunnelReportStore.isTowardLessSide(sh, side) }
            val moreSide = sides.firstOrNull { (sh, side) -> !TunnelReportStore.isTowardLessSide(sh, side) }
            fun sideBlock(shaft: String, side: String): Triple<String, Double, Pair<Double, Double>> {
                val km = TunnelReportStore.currentKm(context, shaft, side)
                val fixedKm = TunnelReportStore.shaftFixedKm(context, shaft) ?: km
                val progress = abs(km - fixedKm)
                val remaining = abs(km - oppositeKm(context, shaft, side))
                return Triple(sideDisplayName(side), km, progress to remaining)
            }
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // چپ: جهت بیشتر
                    Column(Modifier.weight(1f)) {
                        if (moreSide != null) {
                            val (side, km, pr) = sideBlock(moreSide.first, moreSide.second)
                            Text(side, style = MaterialTheme.typography.bodySmall, color = Color(0xFF81C995), fontSize = 12.sp)
                            Text(formatEn("%.3f", km), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9BA888), fontSize = 11.sp)
                            Text(formatEn("پ:%.1f / م:%.1f", pr.first, pr.second), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B8A8), fontSize = 11.sp)
                        }
                    }
                    Text(
                        shaftName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    // راست: جهت کمتر
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        if (lessSide != null) {
                            val (side, km, pr) = sideBlock(lessSide.first, lessSide.second)
                            Text(side, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D), fontSize = 12.sp)
                            Text(formatEn("%.3f", km), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9BA888), fontSize = 11.sp)
                            Text(formatEn("پ:%.1f / م:%.1f", pr.first, pr.second), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B8A8), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
