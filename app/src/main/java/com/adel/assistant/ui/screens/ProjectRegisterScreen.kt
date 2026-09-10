package com.adel.assistant.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adel.assistant.data.CalendarHelper
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.CsvStore
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.formatMoney
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

@Composable
fun ProjectRegisterScreen(
    color: Color,
    onBack: () -> Unit,
    initialDay: String? = null,
    initialMonth: String? = null,
    initialYear: String? = null
) {
    val context = LocalContext.current
    val today = remember { CalendarStore.todayJalali() }

    var day by remember { mutableStateOf(initialDay?.takeIf { it.isNotBlank() } ?: today.third.toString()) }
    var month by remember { mutableStateOf(initialMonth?.takeIf { it.isNotBlank() } ?: today.second.toString()) }
    var year by remember { mutableStateOf(initialYear?.takeIf { it.isNotBlank() } ?: today.first.toString()) }
    var hour by remember { mutableStateOf("9") }
    var minute by remember { mutableStateOf("0") }
    var name by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var addToCalendar by remember { mutableStateOf(true) }
    var editingRow by remember { mutableStateOf<String?>(null) }

    var results by remember { mutableStateOf(listOf<ProjectEntry>()) }
    var showMenu by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var confirmCallFor by remember { mutableStateOf<ProjectEntry?>(null) }
    var confirmDeleteFor by remember { mutableStateOf<ProjectEntry?>(null) }

    fun clearForm() {
        day = today.third.toString()
        month = today.second.toString()
        year = today.first.toString()
        hour = "9"
        minute = "0"
        name = ""
        employer = ""
        amount = ""
        description = ""
        phone = ""
        editingRow = null
    }

    BackHandler(enabled = editingRow != null) { clearForm() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    CsvStore.importRawText(context, "projects", input.bufferedReader().readText())
                    statusMsg = "فایل پروژه‌ها با موفقیت وارد شد"
                }
            } catch (e: Exception) {
                statusMsg = "خطا در وارد کردن فایل"
            }
        }
    }

    fun doRegister() {
        val amt = amount.toDoubleOrNullFa() ?: 0.0
        val rowId = editingRow ?: ProjectStore.nextRowId(context)
        val existingSettled = editingRow?.let { id ->
            ProjectStore.all(context).firstOrNull { it.row == id }?.settled
        } ?: 0.0
        val entry = ProjectEntry(
            row = rowId,
            day = day,
            month = month,
            name = name,
            amount = amt,
            settled = existingSettled,
            remaining = amt - existingSettled,
            employer = employer,
            phone = phone,
            description = description,
            year = year,
            hour = hour.ifBlank { "9" },
            minute = minute.ifBlank { "0" }
        )
        ProjectStore.save(context, entry)

        var calMsg = ""
        if (addToCalendar && name.isNotBlank()) {
            val eventId = CalendarHelper.insertProjectEvent(
                context = context,
                title = "پروژه: $name",
                description = buildString {
                    append("کارفرما: $employer\n")
                    if (phone.isNotBlank()) append("تلفن: $phone\n")
                    if (description.isNotBlank()) append(description)
                    append("\nمبلغ: ${formatEn("%.0f", amt)}")
                },
                yearJalali = year.toIntOrNullFa() ?: today.first,
                monthJalali = month.toIntOrNullFa() ?: today.second,
                dayJalali = day.toIntOrNullFa() ?: today.third,
                hour = hour.toIntOrNullFa() ?: 9,
                minute = minute.toIntOrNullFa() ?: 0
            )
            calMsg = if (eventId != null) " + تقویم" else " (تقویم ثبت نشد — مجوز یا تقویم را چک کنید)"
        }

        statusMsg = "ثبت شد$calMsg"
        clearForm()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            doRegister()
        } else {
            // ثبت پروژه حتی بدون تقویم
            val prev = addToCalendar
            addToCalendar = false
            doRegister()
            addToCalendar = prev
            statusMsg = "پروژه ثبت شد — برای تقویم مجوز ندادید"
        }
    }

    fun register() {
        if (name.isBlank()) {
            statusMsg = "نام پروژه را وارد کنید"
            return
        }
        if (addToCalendar) {
            val writeOk = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
            val readOk = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
            if (!writeOk || !readOk) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_CALENDAR,
                        Manifest.permission.READ_CALENDAR
                    )
                )
                return
            }
        }
        doRegister()
    }

    fun search() {
        results = ProjectStore.search(context, name, employer)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        Box {
            ScreenTopBar(
                title = "ثبت پروژه",
                color = color,
                onBack = { if (editingRow != null) clearForm() else onBack() }
            )
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "ایمپورت/اکسپورت", tint = Color(0xFFAAB697))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("وارد کردن") }, onClick = {
                    showMenu = false
                    importLauncher.launch(arrayOf("text/*", "*/*"))
                })
                DropdownMenuItem(text = { Text("خارج کردن") }, onClick = {
                    showMenu = false
                    val text = FileExport.readAsCsvText(context, "projects")
                    val uri = FileExport.exportTextToDocuments(context, "projects.csv", text)
                    statusMsg = if (uri != null) "در Documents/AdelAssistant ذخیره شد" else "خطا در خارج کردن"
                })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("روز") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("سال") }, modifier = Modifier.weight(1.2f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = hour, onValueChange = { hour = it }, label = { Text("ساعت") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = minute, onValueChange = { minute = it }, label = { Text("دقیقه") }, modifier = Modifier.weight(1f))
        }

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام پروژه") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = employer, onValueChange = { employer = it }, label = { Text("کارفرما") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("مبلغ (میلیون)") }, modifier = Modifier.weight(1f), supportingText = { val v = amount.toDoubleOrNullFa(); if (v != null) Text("${formatMoney(v)} میلیون تومان") })
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("شماره تماس") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Checkbox(
                checked = addToCalendar,
                onCheckedChange = { addToCalendar = it },
                colors = CheckboxDefaults.colors(checkedColor = color)
            )
            Text("ثبت در Google Calendar / تقویم گوشی", color = Color(0xFF1C1C1C))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { register() },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (editingRow != null) "ثبت ویرایش" else "ثبت")
            }
            OutlinedButton(onClick = { search() }, modifier = Modifier.weight(1f)) {
                Text("جستجو")
            }
        }

        if (statusMsg.isNotBlank()) {
            Text(
                statusMsg,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAB697),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results.take(6)) { p ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${p.name} — ${p.employer}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            formatEn(
                                "%s/%s %02d:%02d — مبلغ: %s — %s",
                                p.day, p.month,
                                p.hour.toIntOrNullFa() ?: 9,
                                p.minute.toIntOrNullFa() ?: 0,
                                formatMoney(p.amount), p.description
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7C8A6B)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            IconButton(onClick = { confirmCallFor = p }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Call, contentDescription = "تماس", tint = color)
                            }
                            IconButton(onClick = {
                                ProjectStore.markSettled(context, p.row)
                                search()
                                statusMsg = "تسویه شد"
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "تسویه", tint = Color(0xFF7FA35A))
                            }
                            IconButton(onClick = {
                                day = p.day
                                month = p.month
                                year = p.year
                                hour = p.hour
                                minute = p.minute
                                name = p.name
                                employer = p.employer
                                amount = if (p.amount == p.amount.toLong().toDouble()) p.amount.toLong().toString() else p.amount.toString()
                                description = p.description
                                phone = p.phone
                                editingRow = p.row
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = Color(0xFF7C8A6B))
                            }
                            IconButton(onClick = { confirmDeleteFor = p }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Color(0xFFC2685E))
                            }
                        }
                    }
                }
            }
        }
    }

    confirmCallFor?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmCallFor = null },
            title = { Text("تماس با ${p.employer}") },
            text = { Text("شماره ${p.phone} گرفته شود؟") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone}")))
                    confirmCallFor = null
                }) { Text("تماس") }
            },
            dismissButton = { TextButton(onClick = { confirmCallFor = null }) { Text("انصراف") } }
        )
    }

    confirmDeleteFor?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFor = null },
            title = { Text("حذف رکورد") },
            text = { Text("رکورد «${p.name}» حذف شود؟ این کار قابل بازگشت نیست.") },
            confirmButton = {
                TextButton(onClick = {
                    ProjectStore.delete(context, p.row)
                    confirmDeleteFor = null
                    search()
                    statusMsg = "حذف شد"
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteFor = null }) { Text("انصراف") } }
        )
    }
}
