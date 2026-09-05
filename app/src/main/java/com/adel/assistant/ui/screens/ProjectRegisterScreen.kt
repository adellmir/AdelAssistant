package com.adel.assistant.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.CsvStore
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

@Composable
fun ProjectRegisterScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val today = remember { CalendarStore.todayJalali() }

    var day by remember { mutableStateOf(today.third.toString()) }
    var month by remember { mutableStateOf(today.second.toString()) }
    var year by remember { mutableStateOf(today.first.toString()) }
    var name by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var editingRow by remember { mutableStateOf<String?>(null) }

    var results by remember { mutableStateOf(listOf<ProjectEntry>()) }
    var showMenu by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var confirmCallFor by remember { mutableStateOf<ProjectEntry?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    CsvStore.importRawText(context, "projects", input.bufferedReader().readText())
                    statusMsg = "فایل پروژه‌ها با موفقیت وارد شد"
                }
            } catch (e: Exception) { statusMsg = "خطا در وارد کردن فایل" }
        }
    }

    fun clearForm() {
        day = today.third.toString(); month = today.second.toString(); year = today.first.toString()
        name = ""; employer = ""; amount = ""; description = ""; phone = ""; editingRow = null
    }

    fun register() {
        val amt = amount.toDoubleOrNullFa() ?: 0.0
        val rowId = editingRow ?: ProjectStore.nextRowId(context)
        val existingSettled = editingRow?.let { id -> ProjectStore.all(context).firstOrNull { it.row == id }?.settled } ?: 0.0
        val entry = ProjectEntry(rowId, day, month, name, amt, existingSettled, amt - existingSettled, employer, phone, description, year)
        ProjectStore.save(context, entry)
        statusMsg = "ثبت شد"
        clearForm()
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
            ScreenTopBar(title = "ثبت پروژه", color = color, onBack = onBack)
            IconButton(onClick = { showMenu = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
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
        }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام پروژه") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = employer, onValueChange = { employer = it }, label = { Text("کارفرما") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("مبلغ") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("شماره تماس") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { register() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.weight(1f)) {
                Text(if (editingRow != null) "ثبت ویرایش" else "ثبت")
            }
            OutlinedButton(onClick = { search() }, modifier = Modifier.weight(1f)) { Text("جستجو") }
        }

        if (statusMsg.isNotBlank()) {
            Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697), modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results.take(6)) { p ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${p.name} — ${p.employer}", style = MaterialTheme.typography.bodySmall)
                        Text(formatEn("%s/%s — مبلغ: %.0f — %s", p.day, p.month, p.amount, p.description),
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C8A6B))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
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
                                day = p.day; month = p.month; year = p.year; name = p.name; employer = p.employer
                                amount = p.amount.toString(); description = p.description; phone = p.phone; editingRow = p.row
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = Color(0xFF7C8A6B))
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
}
