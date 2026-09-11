package com.adel.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.formatMoney
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.data.toIntOrNullFa
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
    var editingCode by remember { mutableStateOf<Int?>(null) }

    var list by remember { mutableStateOf(TunnelFinanceStore.all(context)) }
    fun refresh() { list = TunnelFinanceStore.all(context) }

    val nextEmpty = list.firstOrNull { it.receiveAmount == null }
    val receivedRows = list.filter { it.receiveAmount != null }.reversed()
    val totalRecv = list.mapNotNull { it.receiveAmount }.sum()
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

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

    fun clearForm() {
        amount = ""; note = ""; editingCode = null
        day = today.third.toString(); month = today.second.toString(); year = today.first.toString()
    }

    fun save() {
        val amt = amount.toDoubleOrNullFa() ?: return run { statusMsg = "مبلغ نامعتبر" }
        val y = year.toIntOrNullFa() ?: return run { statusMsg = "سال نامعتبر" }
        val m = month.toIntOrNullFa() ?: 1
        val d = day.toIntOrNullFa() ?: 1
        val recvDate = "%04d%02d%02d".format(y, m, d)
        val code = editingCode
        if (code != null) {
            TunnelFinanceStore.updateReceipt(context, code, amt, recvDate, note)
            statusMsg = "ویرایش شد"
        } else {
            val ok = TunnelFinanceStore.addReceipt(context, amt, recvDate, note)
            statusMsg = if (ok) "ثبت شد" else "سطر خالی برای دریافت نیست"
        }
        clearForm(); refresh()
    }

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp)) {
        Box {
            ScreenTopBar(title = "دریافتی‌های تونل", color = color, onBack = onBack)
            IconButton(onClick = { showMenu = true }, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Filled.Settings, null, tint = color)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("خروجی CSV") }, onClick = {
                    showMenu = false
                    val text = TunnelFinanceStore.exportCsvText(context)
                    FileExport.exportTextToDocuments(context, "Tunel-financial.csv", text, "text/csv")
                    statusMsg = "خروجی ذخیره شد"
                })
                DropdownMenuItem(text = { Text("ورود CSV") }, onClick = {
                    showMenu = false
                    importLauncher.launch(arrayOf("text/*", "text/csv", "*/*"))
                })
            }
        }

        if (nextEmpty != null && editingCode == null) {
            Text(
                formatEn("ثبت روی سطر %d (%d/%02d)", nextEmpty.dateCode, nextEmpty.year, nextEmpty.month),
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )
        }
        if (editingCode != null) {
            Text("ویرایش دریافت سطر $editingCode", color = color, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("مبلغ دریافت") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("روز") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
            OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
            OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("سال") }, modifier = Modifier.weight(1.2f), singleLine = true, keyboardOptions = numKb)
        }
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        Button(onClick = { save() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
            Text(if (editingCode != null) "ثبت ویرایش" else "ثبت دریافتی")
        }
        if (editingCode != null) {
            TextButton(onClick = { clearForm() }) { Text("انصراف از ویرایش") }
        }
        if (statusMsg.isNotBlank()) {
            Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697), modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text("جمع دریافتی‌ها: ${formatMoney(totalRecv)}", fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(receivedRows, key = { it.dateCode }) { item ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${formatMoney(item.receiveAmount ?: 0.0)} — ${item.receiveDate.ifBlank { "—" }}",
                                fontWeight = FontWeight.SemiBold, color = TextPrimary
                            )
                            Text(
                                item.note.ifBlank { "بدون توضیحات" },
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary
                            )
                        }
                        IconButton(onClick = {
                            editingCode = item.dateCode
                            val a = item.receiveAmount ?: 0.0
                            amount = if (kotlin.math.abs(a - a.toLong()) < 1e-9) a.toLong().toString() else a.toString()
                            note = item.note
                            val rd = item.receiveDate.filter { it.isDigit() }
                            if (rd.length >= 8) {
                                year = rd.substring(0, 4)
                                month = rd.substring(4, 6).trimStart('0').ifBlank { rd.substring(4, 6) }
                                day = rd.substring(6, 8).trimStart('0').ifBlank { rd.substring(6, 8) }
                            }
                            statusMsg = "در حال ویرایش"
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Edit, null, tint = color)
                        }
                        IconButton(onClick = {
                            TunnelFinanceStore.clearReceipt(context, item.dateCode)
                            refresh(); statusMsg = "دریافت حذف شد"
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Delete, null, tint = Color(0xFFC2685E))
                        }
                    }
                }
            }
        }
    }
}
