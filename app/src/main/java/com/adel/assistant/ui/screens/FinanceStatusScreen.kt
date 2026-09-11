package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.CsvStore
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.formatMoney
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

@Composable
fun FinanceStatusScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val (ty, tm, td) = CalendarStore.todayJalali()
    var fromY by remember { mutableStateOf(ty.toString()) }
    var fromM by remember { mutableStateOf("1") }
    var fromD by remember { mutableStateOf("1") }
    var toY by remember { mutableStateOf(ty.toString()) }
    var toM by remember { mutableStateOf(tm.toString()) }
    var toD by remember { mutableStateOf(td.toString()) }
    var result by remember { mutableStateOf<StatusResult?>(null) }
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    fun ym(y: Int, m: Int) = y * 100 + m

    fun compute() {
        val fy = fromY.toIntOrNullFa() ?: 0
        val fm = fromM.toIntOrNullFa() ?: 1
        val tyi = toY.toIntOrNullFa() ?: 9999
        val tmi = toM.toIntOrNullFa() ?: 12
        val fromYm = ym(fy, fm)
        val toYm = ym(tyi, tmi)

        val projects = ProjectStore.all(context).filter { p ->
            val y = p.year.toIntOrNullFa() ?: 0
            val m = p.month.toIntOrNullFa() ?: 0
            if (y <= 0 || m <= 0) return@filter false
            if (p.name.isBlank() || p.name == "پروژه") return@filter false
            ym(y, m) in fromYm..toYm
        }
        val incomeProj = projects.sumOf { it.amount }
        val recvProjSettled = projects.sumOf { it.settled }
        val partial = CsvStore.readAll(context, "project_partial_payments").mapNotNull { row ->
            row.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
        }.sum()
        val recvProjAll = recvProjSettled + partial

        val tRows = TunnelFinanceStore.all(context).filter { ym(it.year, it.month) in fromYm..toYm }
        val incomeTun = tRows.sumOf { it.income }
        val recvTun = tRows.mapNotNull { it.receiveAmount }.sum()

        result = StatusResult(
            incomeProject = incomeProj,
            incomeTunnel = incomeTun,
            incomeTotal = incomeProj + incomeTun,
            recvProject = recvProjAll,
            recvTunnel = recvTun,
            recvTotal = recvProjAll + recvTun
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())
    ) {
        ScreenTopBar(title = "وضعیت مالی", color = color, onBack = onBack)
        Text("بازه تاریخ (شمسی)", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Text("از", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(fromY, { fromY = it }, label = { Text("سال") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
            OutlinedTextField(fromM, { fromM = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
            OutlinedTextField(fromD, { fromD = it }, label = { Text("روز") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
        }
        Spacer(Modifier.height(8.dp))
        Text("تا", color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(toY, { toY = it }, label = { Text("سال") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
            OutlinedTextField(toM, { toM = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
            OutlinedTextField(toD, { toD = it }, label = { Text("روز") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { compute() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
            Text("محاسبه")
        }
        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            StatusCard("درآمد", listOf("پروژه‌ها" to r.incomeProject, "تونل" to r.incomeTunnel, "جمع" to r.incomeTotal), color)
            Spacer(Modifier.height(12.dp))
            StatusCard("دریافتی", listOf("پروژه‌ها" to r.recvProject, "تونل" to r.recvTunnel, "جمع" to r.recvTotal), color)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class StatusResult(
    val incomeProject: Double,
    val incomeTunnel: Double,
    val incomeTotal: Double,
    val recvProject: Double,
    val recvTunnel: Double,
    val recvTotal: Double
)

@Composable
private fun StatusCard(title: String, rows: List<Pair<String, Double>>, color: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = TextSecondary)
                    Text(formatMoney(value), color = TextPrimary, fontWeight = if (label == "جمع") FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
