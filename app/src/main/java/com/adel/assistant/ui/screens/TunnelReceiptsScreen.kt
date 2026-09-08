package com.adel.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import com.adel.assistant.ui.theme.TextMuted
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

@Composable
fun TunnelReceiptsScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val today = remember { CalendarStore.todayJalali() }

    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(today.third.toString()) }
    var month by remember { mutableStateOf(today.second.toString()) }
    var year by remember { mutableStateOf(today.first.toString()) }
    var note by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    var list by remember { mutableStateOf(TunnelFinanceStore.all(context)) }
    fun refresh() { list = TunnelFinanceStore.all(context) }

    val nextEmpty = list.firstOrNull { it.receiveAmount == null }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val n = TunnelFinanceStore.importCsvText(context, input.bufferedReader().readText())
                    statusMsg = "وارد شد: $n سطر"
                    refresh()
                }
            } catch (e: Exception) { statusMsg = "خطا در ورود فایل" }
        }
    }

    fun save() {
        val amt = amount.toDoubleOrNullFa()
        if (amt == null || amt <= 0) {
            statusMsg = "مبلغ را درست وارد کنید"
            return
        }
        if (nextEmpty == null) {
            statusMsg = "سطری با دریافت خالی وجود ندارد — اول کارکرد ماهانه ثبت کنید"
            return
        }
        val y = year.padStart(4, '0')
        val m = month.padStart(2, '0')
        val d = day.padStart(2, '0')
        val recvDate = "$y$m$d".replace(" ", "")
        val ok = TunnelFinanceStore.addReceipt(context, amt, recvDate, note)
        statusMsg = if (ok) {
            formatEn("ثبت شد روی سطر %d — مبلغ %.0f", nextEmpty.dateCode, amt)
        } else "ثبت نشد"
        amount = ""; note = ""
        refresh()
    }

    val receivedRows = list.filter { it.receiveAmount != null }.reversed()
    val totalRecv = list.mapNotNull { it.receiveAmount }.sum()

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp)) {
        Box {
            ScreenTopBar(title = "دریافتی‌های تونل", color = color, onBack = onBack)
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
                    statusMsg = if (uri != null) "در Documents/AdelAssistant ذخیره شد" else "خطا در خروجی"
                })
            }
        }

        nextEmpty?.let {
            Text(
                formatEn("ثبت روی اولین سطر خالی: کد %d (%d/%02d)", it.dateCode, it.year, it.month),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1565C0),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        } ?: Text(
            "همه سطرها دریافت دارند — کارکرد جدید ثبت کنید",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFC62828),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("روز") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("سال") }, modifier = Modifier.weight(1.2f))
        }
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("مبلغ دریافتی") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        Button(onClick = { save() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
            Text("ثبت دریافتی")
        }
        if (statusMsg.isNotBlank()) {
            Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697), modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text(formatEn("جمع دریافتی‌ها: %.0f", totalRecv), fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(receivedRows) { item ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                formatEn("%.0f — تاریخ دریافت: %s", item.receiveAmount ?: 0.0, item.receiveDate.ifBlank { "—" }),
                                fontWeight = FontWeight.SemiBold, color = TextPrimary
                            )
                            Text(
                                formatEn("روی سطر کارکرد %d (%d/%02d)", item.dateCode, item.year, item.month),
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary
                            )
                            if (item.note.isNotBlank()) {
                                Text(item.note, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                        IconButton(onClick = {
                            TunnelFinanceStore.clearReceipt(context, item.dateCode)
                            refresh()
                            statusMsg = "دریافت حذف شد"
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Delete, null, tint = Color(0xFFC2685E))
                        }
                    }
                }
            }
        }
    }
}
