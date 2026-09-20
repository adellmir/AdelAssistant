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

private fun oppositeKm(context: android.content.Context, shaft: String, side: String, asOf: String?): Double {
    val s = normalizeSide(side)
    return if (s == "start" || s == "end") {
        TunnelReportStore.shaftFixedKm(context, s) ?: 0.0
    } else {
        if (asOf != null) TunnelReportStore.kmOnOrBefore(context, s, shaft, asOf)
        else TunnelReportStore.currentKm(context, s, shaft)
    }
}

/** یک ردیف شفت برای نمایش کارت‌مانند */
private data class ShaftCard(
    val shaftName: String,
    val moreLabel: String,
    val moreKm: Double,
    val moreProgress: Double,
    val moreRemain: Double,
    val lessLabel: String,
    val lessKm: Double,
    val lessProgress: Double,
    val lessRemain: Double
)

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
    var cards by remember { mutableStateOf(buildOverallCards(context)) }

    fun refreshOverall() {
        rangeMode = false
        cards = buildOverallCards(context)
    }

    fun showRange() {
        val fromKey = "%s%02d%02d".format(fromYear, fromMonth.toIntOrNullFa() ?: 0, fromDay.toIntOrNullFa() ?: 0)
        val toKey = "%s%02d%02d".format(toYear, toMonth.toIntOrNullFa() ?: 0, toDay.toIntOrNullFa() ?: 0)
        rangeMode = true
        cards = buildRangeCards(context, fromKey, toKey)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "پیشرفت تونل", color = color, onBack = onBack)
        Spacer(Modifier.height(8.dp))

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
            OutlinedButton(onClick = { refreshOverall() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("رفرش")
            }
        }

        Text(
            if (rangeMode) "پیشرفت بازه‌ای" else "پیشرفت کلی",
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFFAAB697)
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(cards) { c ->
                ShaftProgressCard(c)
            }
        }
    }
}

@Composable
private fun ShaftProgressCard(c: ShaftCard) {
    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // چپ: بیشتر
            Column(Modifier.weight(1f)) {
                Text(c.moreLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF81C995), fontSize = 12.sp)
                Text(formatEn("%.3f", c.moreKm), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9BA888), fontSize = 11.sp)
                Text(formatEn("پ:%.1f / م:%.1f", c.moreProgress, c.moreRemain), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B8A8), fontSize = 11.sp)
            }
            // وسط: فقط شماره شفت
            Text(
                c.shaftName,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            // راست: کمتر
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(c.lessLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB74D), fontSize = 12.sp)
                Text(formatEn("%.3f", c.lessKm), style = MaterialTheme.typography.bodySmall, color = Color(0xFF9BA888), fontSize = 11.sp)
                Text(formatEn("پ:%.1f / م:%.1f", c.lessProgress, c.lessRemain), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B8A8), fontSize = 11.sp)
            }
        }
    }
}

private fun buildOverallCards(context: android.content.Context): List<ShaftCard> {
    val byShaft = TunnelReportStore.TUNNEL_LAYOUT.groupBy { it.first }.toSortedMap()
    return byShaft.map { (shaftName, sides) ->
        val lessSide = sides.firstOrNull { (sh, side) -> TunnelReportStore.isTowardLessSide(sh, side) }
        val moreSide = sides.firstOrNull { (sh, side) -> !TunnelReportStore.isTowardLessSide(sh, side) }
        fun block(shaft: String, side: String): Triple<String, Double, Pair<Double, Double>> {
            val km = TunnelReportStore.currentKm(context, shaft, side)
            val fixedKm = TunnelReportStore.shaftFixedKm(context, shaft) ?: km
            val progress = abs(km - fixedKm)
            val remaining = abs(km - oppositeKm(context, shaft, side, null))
            return Triple(sideDisplayName(side), km, progress to remaining)
        }
        val more = moreSide?.let { block(it.first, it.second) }
        val less = lessSide?.let { block(it.first, it.second) }
        ShaftCard(
            shaftName = shaftName,
            moreLabel = more?.first ?: "—",
            moreKm = more?.second ?: 0.0,
            moreProgress = more?.third?.first ?: 0.0,
            moreRemain = more?.third?.second ?: 0.0,
            lessLabel = less?.first ?: "—",
            lessKm = less?.second ?: 0.0,
            lessProgress = less?.third?.first ?: 0.0,
            lessRemain = less?.third?.second ?: 0.0
        )
    }
}

/** پیشرفت بازه‌ای: پ = جابجایی کیلومتر در بازه؛ م = مانده تا جبهه مقابل در انتهای بازه */
private fun buildRangeCards(context: android.content.Context, fromKey: String, toKey: String): List<ShaftCard> {
    val byShaft = TunnelReportStore.TUNNEL_LAYOUT.groupBy { it.first }.toSortedMap()
    return byShaft.map { (shaftName, sides) ->
        val lessSide = sides.firstOrNull { (sh, side) -> TunnelReportStore.isTowardLessSide(sh, side) }
        val moreSide = sides.firstOrNull { (sh, side) -> !TunnelReportStore.isTowardLessSide(sh, side) }
        fun block(shaft: String, side: String): Triple<String, Double, Pair<Double, Double>> {
            val fromKm = TunnelReportStore.kmOnOrBefore(context, shaft, side, fromKey)
            val toKm = TunnelReportStore.kmOnOrBefore(context, shaft, side, toKey)
            val progress = abs(toKm - fromKm)
            val remaining = abs(toKm - oppositeKm(context, shaft, side, toKey))
            return Triple(sideDisplayName(side), toKm, progress to remaining)
        }
        val more = moreSide?.let { block(it.first, it.second) }
        val less = lessSide?.let { block(it.first, it.second) }
        ShaftCard(
            shaftName = shaftName,
            moreLabel = more?.first ?: "—",
            moreKm = more?.second ?: 0.0,
            moreProgress = more?.third?.first ?: 0.0,
            moreRemain = more?.third?.second ?: 0.0,
            lessLabel = less?.first ?: "—",
            lessKm = less?.second ?: 0.0,
            lessProgress = less?.third?.first ?: 0.0,
            lessRemain = less?.third?.second ?: 0.0
        )
    }
}
