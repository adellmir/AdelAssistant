package com.adel.assistant.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.formatMoney
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary

@Composable
fun TunnelFinanceSummaryScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var statusMsg by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf(TunnelFinanceStore.summary(context)) }

    fun refresh() { summary = TunnelFinanceStore.summary(context) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        // SAF starts at Documents/AdelAssistant
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = input.bufferedReader().readText()
                    val n = TunnelFinanceStore.importCsvText(context, text)
                    statusMsg = "وارد شد: $n سطر"
                    refresh()
                }
            } catch (e: Exception) {
                statusMsg = "خطا در ورود"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "خلاصه مطالبات تونل", color = color, onBack = onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = {
                val text = TunnelFinanceStore.exportCsvText(context)
                val uri = FileExport.exportTextToDocuments(context, "tunnel_financial.csv", text, "text/csv")
                statusMsg = if (uri != null) "در Documents/AdelAssistant ذخیره شد" else "خطا"
            }) { Icon(Icons.Filled.Settings, null, tint = Color(0xFFAAB697)) }
            TextButton(onClick = {
                importLauncher.launch(com.adel.assistant.data.AdelDocuments.openDocumentIntent("text/*", "application/csv", "*/*"))
            }) { Text("ورود CSV", color = color) }
            TextButton(onClick = { refresh() }) { Text("بروزرسانی", color = color) }
        }

        Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryRow("کل صورت‌وضعیت‌ها", formatMoney(summary.sumPayable))
                SummaryRow("کل حسن انجام", formatMoney(summary.sumRetention))
                SummaryRow("حسن آزاد‌شده", formatMoney(summary.sumHasanAzad))
                SummaryRow("حسن بلوکه (از آخرین اردیبهشت)", formatMoney(summary.blockedRetention))
                SummaryRow("جمع دریافتی‌ها", formatMoney(summary.sumReceived))
                HorizontalDivider(color = Color(0xFF3A4530))
                Text(
                    "مطالبات آزاد",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                Text(
                    formatMoney(summary.remaining),
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "صورت + حسن آزاد − دریافتی",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        if (statusMsg.isNotBlank()) {
            Text(statusMsg, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}
