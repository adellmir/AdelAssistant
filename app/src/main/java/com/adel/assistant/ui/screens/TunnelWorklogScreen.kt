package com.adel.assistant.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import com.adel.assistant.data.TunnelMonthRow
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.formatMoney
import com.adel.assistant.data.formatGroupedNumericInput
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import com.adel.assistant.ui.theme.TextMuted
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

@Composable
fun TunnelWorklogScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)
    val today = remember { CalendarStore.todayJalali() }

    var year by remember { mutableStateOf(today.first.toString()) }
    var month by remember { mutableStateOf(today.second.toString()) }
    var days by remember { mutableStateOf("") }
    var unitPrice by remember { mutableStateOf(TunnelFinanceStore.lastUnitPrice(context).let { if (it > 0) it.toLong().toString() else "" }) }
    var lunchDed by remember { mutableStateOf("0") }
    var overtime by remember { mutableStateOf("0") }
    var timesheetDed by remember { mutableStateOf("0") }
    var cameraDed by remember { mutableStateOf("0") }
    var cameraTimeDed by remember { mutableStateOf("0") }
    var surveyor by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var editingCode by remember { mutableStateOf<Int?>(null) }
    var statusMsg by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TunnelMonthRow?>(null) }

    var list by remember { mutableStateOf(TunnelFinanceStore.all(context).filter { it.days > 0.0 || it.totalAmount > 0.0 }.reversed()) }
    fun refresh() { list = TunnelFinanceStore.all(context).filter { it.days > 0.0 || it.totalAmount > 0.0 }.reversed() }

    fun clearForm() {
        year = today.first.toString(); month = today.second.toString()
        days = ""; unitPrice = TunnelFinanceStore.lastUnitPrice(context).let { if (it > 0) it.toLong().toString() else "" }
        lunchDed = "0"; overtime = "0"; timesheetDed = "0"; cameraDed = "0"; cameraTimeDed = "0"
        surveyor = "0"; note = ""; editingCode = null
    }
    BackHandler(enabled = editingCode != null) { clearForm() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        // SAF starts at Documents/AdelAssistant
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
        val y = year.toIntOrNullFa() ?: return run { statusMsg = "سال نامعتبر" }
        val m = month.toIntOrNullFa() ?: return run { statusMsg = "ماه نامعتبر" }
        val d = days.toDoubleOrNullFa() ?: return run { statusMsg = "روز کارکرد را وارد کنید" }
        val c = unitPrice.toDoubleOrNullFa() ?: return run { statusMsg = "مبلغ واحد را وارد کنید" }
        if (d < 0 || d > 31) { statusMsg = "روز باید بین ۰ تا ۳۱ باشد"; return }
        val code = editingCode ?: TunnelMonthRow.makeDateCode(y, m)
        val existing = TunnelFinanceStore.all(context).firstOrNull { it.dateCode == code }
        val row = TunnelMonthRow.compute(
            dateCode = code,
            days = d,
            unitPrice = c,
            lunchDeduction = lunchDed.toDoubleOrNullFa() ?: 0.0,
            overtimeAdd = overtime.toDoubleOrNullFa() ?: 0.0,
            timesheetDeduction = timesheetDed.toDoubleOrNullFa() ?: 0.0,
            cameraDeduction = cameraDed.toDoubleOrNullFa() ?: 0.0,
            cameraTimeDeduction = cameraTimeDed.toDoubleOrNullFa() ?: 0.0,
            surveyorPay = surveyor.toDoubleOrNullFa() ?: 0.0,
            receiveDate = existing?.receiveDate ?: "",
            receiveAmount = existing?.receiveAmount,
            note = note.ifBlank { existing?.note ?: "" }
        )
        TunnelFinanceStore.saveMonth(context, row)
        statusMsg = "ثبت شد — صورت‌وضعیت: ${formatMoney(row.payable)} | درآمد: ${formatMoney(row.income)}"
        clearForm(); refresh()
    }

    val dPrev = days.toDoubleOrNullFa()
    val cPrev = unitPrice.toDoubleOrNullFa()
    val preview = if (dPrev != null && cPrev != null) {
        TunnelMonthRow.compute(
            dateCode = 0, days = dPrev, unitPrice = cPrev,
            lunchDeduction = lunchDed.toDoubleOrNullFa() ?: 0.0,
            overtimeAdd = overtime.toDoubleOrNullFa() ?: 0.0,
            timesheetDeduction = timesheetDed.toDoubleOrNullFa() ?: 0.0,
            cameraDeduction = cameraDed.toDoubleOrNullFa() ?: 0.0,
            cameraTimeDeduction = cameraTimeDed.toDoubleOrNullFa() ?: 0.0,
            surveyorPay = surveyor.toDoubleOrNullFa() ?: 0.0
        )
    } else null

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp)) {
        Box {
            ScreenTopBar(title = "کارکرد ماهانه تونل", color = color, onBack = { if (editingCode != null) clearForm() else onBack() })
            IconButton(onClick = { showMenu = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Outlined.Tune, null, tint = Color(0xFFAAB697))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("وارد کردن CSV") }, onClick = {
                    showMenu = false; importLauncher.launch(com.adel.assistant.data.AdelDocuments.openDocumentIntent("text/*", "*/*"))
                })
                DropdownMenuItem(text = { Text("خارج کردن CSV") }, onClick = {
                    showMenu = false
                    val text = TunnelFinanceStore.exportCsvText(context)
                    val uri = FileExport.exportTextToDocuments(context, "Tunel-financial.csv", text)
                    statusMsg = if (uri != null) "در Documents/AdelAssistant ذخیره شد" else "خطا در خروجی"
                })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f),
                keyboardOptions = numKb)
            OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("سال") }, modifier = Modifier.weight(1.2f),
                keyboardOptions = numKb)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("روز کارکرد") }, modifier = Modifier.weight(1f),
                keyboardOptions = numKb)
            OutlinedTextField(value = unitPrice, onValueChange = { unitPrice = formatGroupedNumericInput(it) }, label = { Text("مبلغ واحد") }, modifier = Modifier.weight(1f),
                keyboardOptions = numKb)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = lunchDed, onValueChange = { lunchDed = it }, label = { Text("کسر نهاری") }, modifier = Modifier.weight(1f),
                keyboardOptions = numKb)
            OutlinedTextField(value = overtime, onValueChange = { overtime = it }, label = { Text("اضافه تایم") }, modifier = Modifier.weight(1f),
                keyboardOptions = numKb)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = timesheetDed, onValueChange = { timesheetDed = it }, label = { Text("کسر تایم‌شیت") }, modifier = Modifier.weight(1f),
                keyboardOptions = numKb)
            OutlinedTextField(value = cameraDed, onValueChange = { cameraDed = it }, label = { Text("کسر دوربین") }, modifier = Modifier.weight(1f),
                keyboardOptions = numKb)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = cameraTimeDed, onValueChange = { cameraTimeDed = it }, label = { Text("کسر تایم دوربین") }, modifier = Modifier.weight(1f),
                keyboardOptions = numKb)
            OutlinedTextField(value = surveyor, onValueChange = { surveyor = it }, label = { Text("نقشه‌بردار") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())

        preview?.let { p ->
            Text(
                "مبلغ کل: ${formatMoney(preview.totalAmount)} | حسن‌انجام: ${formatMoney(preview.retention)} | کسورات: ${formatMoney(preview.totalDeductions)} | صورت‌وضعیت: ${formatMoney(preview.payable)} | درآمد: ${formatMoney(preview.income)}",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = { save() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
            Text(if (editingCode != null) "ثبت ویرایش" else "ثبت کارکرد")
        }
        if (statusMsg.isNotBlank()) {
            Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697), modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(10.dp))
        Text("سوابق ماهانه", fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(list) { item ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(formatEn("%04d/%02d — %.1f روز × %s", item.year % 100, item.month, item.days, formatMoney(item.unitPrice)),
                            fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(formatEn("صورت‌وضعیت: %s | درآمد: %s | دریافت: %s",
                            formatMoney(item.payable), formatMoney(item.income), item.receiveAmount?.let { formatMoney(it) } ?: "—"),
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = {
                                year = item.year.toString(); month = item.month.toString()
                                days = item.days.toString(); unitPrice = item.unitPrice.toLong().toString()
                                lunchDed = item.lunchDeduction.toLong().toString()
                                overtime = item.overtimeAdd.toLong().toString()
                                timesheetDed = item.timesheetDeduction.toLong().toString()
                                cameraDed = item.cameraDeduction.toLong().toString()
                                cameraTimeDed = item.cameraTimeDeduction.toLong().toString()
                                surveyor = item.surveyorPay.toLong().toString()
                                note = item.note; editingCode = item.dateCode
                            }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Edit, null, tint = TextMuted) }
                            IconButton(onClick = { confirmDelete = item }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Outlined.Delete, null, tint = Color(0xFFC2685E))
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف ماه") },
            text = { Text(formatEn("سطر %d حذف شود؟", item.dateCode)) },
            confirmButton = {
                TextButton(onClick = {
                    TunnelFinanceStore.deleteMonth(context, item.dateCode)
                    confirmDelete = null; refresh(); statusMsg = "حذف شد"
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("انصراف") } }
        )
    }
}