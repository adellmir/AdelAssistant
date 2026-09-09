package com.adel.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.formatMoney
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import com.adel.assistant.ui.theme.TextMuted
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

@Composable
fun TunnelFinanceSummaryScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val summary = remember(tick) { TunnelFinanceStore.summary(context) }
    val rows = remember(tick) { TunnelFinanceStore.all(context) }
    var showMenu by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val n = TunnelFinanceStore.importCsvText(context, input.bufferedReader().readText())
                    statusMsg = "وارد شد: $n سطر"
                    tick++
                }
            } catch (e: Exception) { statusMsg = "خطا در ورود" }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp)) {
        Box {
            ScreenTopBar(title = "خلاصه مطالبات تونل", color = color, onBack = onBack)
            IconButton(onClick = { showMenu = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Filled.Settings, null, tint = Color(0xFFAAB697))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("وارد کردن CSV") }, onClick = {
                    showMenu = false; importLauncher.launch(arrayOf("text/*", "*/*"))
                })
                DropdownMenuItem(text = { Text("خارج کردن CSV") }, onClick = {
                    showMenu = false
                    val text = TunnelFinanceStore.exportCsvText(context)
                    val uri = FileExport.exportTextToDocuments(context, "Tunel-financial.csv", text)
                    statusMsg = if (uri != null) "در Documents/AdelAssistant ذخیره شد" else "خطا"
                })
                DropdownMenuItem(text = { Text("بروزرسانی") }, onClick = {
                    showMenu = false; tick++; statusMsg = "بروز شد"
                })
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard("جمع کل صورت‌وضعیت", formatMoney(summary.sumPayable), Color(0xFF1565C0))
            SummaryCard("جمع کل حسن‌انجام", formatMoney(summary.sumRetention), Color(0xFF6A1B9A))
            SummaryCard("جمع دریافتی‌ها", formatMoney(summary.sumReceived), Color(0xFF2E7D32))
            SummaryCard("حسن‌انجام ۱۲ ماه آخر", formatMoney(summary.retentionLast12), Color(0xFFC62828))
            SummaryCard("مانده مطالبات", formatMoney(summary.remaining), Color(0xFFE65100))

            if (statusMsg.isNotBlank()) {
                Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
            }

            Spacer(Modifier.height(4.dp))
            Text("تعداد ماه‌های ثبت‌شده: ${rows.size}", fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("آخرین ماه‌ها", fontWeight = FontWeight.Bold, color = TextPrimary)
            rows.takeLast(8).reversed().forEach { r ->
                Text(
                    formatEn("%d/%02d  صورت: %.0f  دریافت: %s", r.year, r.month, r.payable,
                        r.receiveAmount?.let { formatEn("%.0f", it) } ?: "—"),
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, accent: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Text(value, fontWeight = FontWeight.Bold, color = accent, fontSize = 22.sp)
        }
    }
}
