package com.adel.assistant.ui.screens

import com.adel.assistant.ui.components.AdelOutlinedTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.appearanceCardColor
import com.adel.assistant.ui.appearanceScreenBg
import com.adel.assistant.ui.appearanceTextMuted
import com.adel.assistant.ui.appearanceTextPrimary
import com.adel.assistant.ui.appearanceTextSecondary

@Composable
fun ProjectEventsScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }

    // حالت: لیست گروه‌ها یا ثبت روی یک پروژه
    var mode by remember { mutableStateOf("search") } // search | register
    var focusName by remember { mutableStateOf("") }
    var focusEmployer by remember { mutableStateOf("") }
    var eventText by remember { mutableStateOf("") }
    var editingRow by remember { mutableStateOf<String?>(null) }

    fun all() = try { ProjectStore.all(context) } catch (_: Exception) { emptyList() }

    val nameQ = name.trim()
    val empQ = employer.trim()

    // جستجوی نام پروژه: گروه‌بندی بر اساس نام (نه لیست جلسات خام)
    val projectGroups = remember(nameQ, empQ, statusMsg) {
        val list = all()
        when {
            nameQ.isNotBlank() && empQ.isBlank() -> {
                list.filter { it.name.contains(nameQ, true) }
                    .groupBy { it.name.trim() }
                    .map { (nm, rows) ->
                        Triple(nm, rows.firstOrNull()?.employer.orEmpty(), rows.sortedByDescending { it.dateSortKey })
                    }
                    .sortedByDescending { it.third.firstOrNull()?.dateSortKey.orEmpty() }
            }
            empQ.isNotBlank() && nameQ.isBlank() -> {
                list.filter { it.employer.contains(empQ, true) }
                    .groupBy { it.name.trim() }
                    .map { (nm, rows) ->
                        Triple(nm, rows.firstOrNull()?.employer.orEmpty(), rows.sortedByDescending { it.dateSortKey })
                    }
                    .sortedByDescending { it.third.firstOrNull()?.dateSortKey.orEmpty() }
            }
            nameQ.isNotBlank() && empQ.isNotBlank() -> {
                list.filter { it.name.contains(nameQ, true) && it.employer.contains(empQ, true) }
                    .groupBy { it.name.trim() }
                    .map { (nm, rows) ->
                        Triple(nm, rows.firstOrNull()?.employer.orEmpty(), rows.sortedByDescending { it.dateSortKey })
                    }
            }
            else -> emptyList()
        }
    }

    val focusRecords = remember(focusName, statusMsg, mode) {
        if (focusName.isBlank()) emptyList()
        else all().filter { it.name.trim() == focusName.trim() }
            .filter { it.description.isNotBlank() || true }
            .sortedByDescending { it.dateSortKey }
            .sortedByDescending { it.row.toIntOrNull() ?: 0 }
    }

    fun openRegister(nm: String, emp: String) {
        focusName = nm
        focusEmployer = emp
        name = nm
        employer = emp
        eventText = ""
        editingRow = null
        mode = "register"
    }

    fun saveEvent() {
        val text = eventText.trim()
        if (focusName.isBlank()) {
            statusMsg = "نام پروژه لازم است"; return
        }
        if (editingRow != null) {
            val existing = all().firstOrNull { it.row == editingRow } ?: return
            ProjectStore.save(context, existing.copy(description = text))
            statusMsg = "ویرایش شد"
            editingRow = null
            eventText = ""
        } else {
            // رکورد جدید فقط برای واقعه — مبلغ ۰
            val entry = ProjectEntry(
                row = ProjectStore.nextRowId(context),
                day = "",
                month = "",
                name = focusName,
                amount = 0.0,
                settled = 0.0,
                remaining = 0.0,
                employer = focusEmployer,
                phone = all().firstOrNull { it.name.trim() == focusName.trim() }?.phone.orEmpty(),
                description = text,
                year = ""
            )
            ProjectStore.save(context, entry)
            statusMsg = "واقعه ثبت شد"
            eventText = ""
        }
    }

    fun deleteRow(row: String) {
        ProjectStore.delete(context, row)
        statusMsg = "حذف شد"
        if (editingRow == row) {
            editingRow = null
            eventText = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appearanceScreenBg())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "ثبت وقایع پروژه", color = color, onBack = {
            if (mode == "register") mode = "search" else onBack()
        })
        Spacer(Modifier.height(8.dp))

        if (mode == "search") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdelOutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("نام پروژه") }, modifier = Modifier.weight(1f)
                )
                AdelOutlinedTextField(
                    value = employer, onValueChange = { employer = it },
                    label = { Text("کارفرما") }, modifier = Modifier.weight(1f)
                )
            }
            if (statusMsg.isNotBlank()) {
                Text(statusMsg, color = appearanceTextSecondary(), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                if (nameQ.isBlank() && empQ.isBlank()) {
                    item {
                        Text(
                            "نام پروژه یا کارفرما را بنویسید — لیست جلسات کاری نمایش داده نمی‌شود",
                            color = appearanceTextMuted(), fontSize = 12.sp
                        )
                    }
                }
                items(projectGroups) { (nm, emp, rows) ->
                    val withDesc = rows.count { it.description.isNotBlank() }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = appearanceCardColor(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { openRegister(nm, emp) }) {
                                    Text(nm, color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (emp.isNotBlank()) {
                                        Text(emp, color = appearanceTextSecondary(), fontSize = 11.sp)
                                    }
                                    Text(
                                        "$withDesc مورد ثبت‌شده",
                                        color = appearanceTextMuted(), fontSize = 11.sp
                                    )
                                }
                                IconButton(onClick = { openRegister(nm, emp) }) {
                                    Icon(Icons.Filled.Add, null, tint = color)
                                }
                            }
                            // موارد از جدید به قدیم
                            rows.filter { it.description.isNotBlank() }.take(8).forEach { r ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Text(
                                        r.description,
                                        color = appearanceTextPrimary(),
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2
                                    )
                                    IconButton(onClick = {
                                        openRegister(nm, emp)
                                        editingRow = r.row
                                        eventText = r.description
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Edit, null, tint = color, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { deleteRow(r.row) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Delete, null, tint = Color(0xFFCF6679), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ثبت / ویرایش
            Text("پروژه: $focusName", color = appearanceTextPrimary(), fontWeight = FontWeight.Bold)
            if (focusEmployer.isNotBlank()) {
                Text("کارفرما: $focusEmployer", color = appearanceTextSecondary(), fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            AdelOutlinedTextField(
                value = eventText,
                onValueChange = { eventText = it },
                label = { Text(if (editingRow != null) "ویرایش واقعه" else "متن واقعه جدید") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(
                    onClick = { saveEvent() },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.weight(1f)
                ) { Text(if (editingRow != null) "ذخیره ویرایش" else "ثبت") }
                OutlinedButton(onClick = { mode = "search"; editingRow = null }) {
                    Text("بازگشت")
                }
            }
            if (statusMsg.isNotBlank()) {
                Text(statusMsg, color = appearanceTextSecondary(), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("موارد ثبت‌شده (جدید → قدیم)", color = appearanceTextSecondary(), fontSize = 12.sp)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f).padding(top = 6.dp)) {
                items(focusRecords.filter { it.description.isNotBlank() }) { r ->
                    Surface(shape = RoundedCornerShape(10.dp), color = appearanceCardColor(), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(r.description, color = appearanceTextPrimary(), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                editingRow = r.row
                                eventText = r.description
                            }) { Icon(Icons.Filled.Edit, null, tint = color) }
                            IconButton(onClick = { deleteRow(r.row) }) {
                                Icon(Icons.Filled.Delete, null, tint = Color(0xFFCF6679))
                            }
                        }
                    }
                }
            }
        }
    }
}
