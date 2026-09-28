package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

private val WEEK_DAYS_FA = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

private val PERSIAN_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

@Composable
fun WorkCalendarScreen(color: Color, onBack: () -> Unit, onAddProject: ((day: Int, month: Int, year: Int) -> Unit)? = null) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)
    val today = remember { CalendarStore.todayJalali() }
    var year by remember { mutableStateOf(today.first) }
    var month by remember { mutableStateOf(today.second) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var dayProjects by remember { mutableStateOf(listOf<ProjectEntry>()) }
    var editingRow by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editEmployer by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editDay by remember { mutableStateOf("") }
    var editMonth by remember { mutableStateOf("") }
    var editYear by remember { mutableStateOf("") }
    var editHour by remember { mutableStateOf("") }
    var editMinute by remember { mutableStateOf("") }
    var editEventId by remember { mutableStateOf("") }
    var confirmDeleteRow by remember { mutableStateOf<ProjectEntry?>(null) }
    var statusMsg by remember { mutableStateOf("") }

    val markedDays = remember(year, month) { ProjectStore.daysWithProjectsIn(context, month.toString(), year.toString()) }
    val monthLength = remember(year, month) { CalendarStore.jalaliMonthLength(year, month) }

    fun goPrev() { if (month > 1) month-- else { month = 12; year-- }; selectedDay = null; dayProjects = emptyList() }
    fun goNext() { if (month < 12) month++ else { month = 1; year++ }; selectedDay = null; dayProjects = emptyList() }

    fun selectDay(d: Int) {
        selectedDay = d
        dayProjects = ProjectStore.forDate(context, d.toString(), month.toString(), year.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "تقویم کار", color = color, onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { goNext() }) { Icon(Icons.Outlined.ChevronRight, contentDescription = "ماه بعد", tint = color) }
            Text(
                "${PERSIAN_MONTHS.getOrElse(month - 1) { "" }} $year",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { goPrev() }) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "ماه قبل", tint = color) }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // هدر ایام هفته شمسی (شنبه اول)
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEK_DAYS_FA.forEach { name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        val firstOffset = remember(year, month) { CalendarStore.jalaliWeekdayIndex(year, month, 1) }
        val cellCount = firstOffset + monthLength
        val rows = (cellCount + 6) / 7
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.height((48.dp * rows).coerceIn(288.dp, 432.dp))
        ) {
            items(firstOffset) {
                Box(modifier = Modifier.aspectRatio(1f))
            }
            items((1..monthLength).toList()) { d ->
                val hasProject = markedDays.contains(d)
                val selected = selectedDay == d
                Surface(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { selectDay(d) },
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        selected -> color
                        hasProject -> color.copy(alpha = 0.25f)
                        else -> SurfaceColor
                    }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(formatEn("%d", d), style = MaterialTheme.typography.bodySmall,
                            color = if (selected) Color.White else Color(0xFFE8ECD9))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (selectedDay != null) {
            Text("پروژه‌های روز $selectedDay", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
            Spacer(modifier = Modifier.height(6.dp))
            if (onAddProject != null) {
                Button(
                    onClick = { onAddProject(selectedDay!!, month, year) },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("افزودن پروژه برای این روز")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (statusMsg.isNotBlank()) {
            Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dayProjects) { p ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${p.name} — ${p.employer}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatEn("%s/%s/%s %02d:%02d — مبلغ: %.0f", p.day, p.month, p.year,
                                        p.hour.toIntOrNullFa() ?: 9, p.minute.toIntOrNullFa() ?: 0, p.amount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF7C8A6B)
                                )
                                if (p.description.isNotBlank()) {
                                    Text(p.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA88B))
                                }
                            }
                            IconButton(onClick = {
                                editingRow = p.row
                                editName = p.name
                                editEmployer = p.employer
                                editPhone = p.phone
                                editAmount = if (p.amount == p.amount.toLong().toDouble()) p.amount.toLong().toString() else p.amount.toString()
                                editDescription = p.description
                                editDay = p.day
                                editMonth = p.month
                                editYear = p.year
                                editHour = p.hour
                                editMinute = p.minute
                                editEventId = p.calendarEventId
                            }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "ویرایش", tint = color)
                            }
                            IconButton(onClick = { confirmDeleteRow = p }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "حذف", tint = Color(0xFFE57373))
                            }
                        }
                        if (editingRow == p.row) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(editDay, { editDay = it }, label = { Text("روز") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                                OutlinedTextField(editMonth, { editMonth = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                                OutlinedTextField(editYear, { editYear = it }, label = { Text("سال") }, modifier = Modifier.weight(1.2f), singleLine = true, keyboardOptions = numKb)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(editHour, { editHour = it }, label = { Text("ساعت") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                                OutlinedTextField(editMinute, { editMinute = it }, label = { Text("دقیقه") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                            }
                            OutlinedTextField(editName, { editName = it }, label = { Text("نام پروژه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(editEmployer, { editEmployer = it }, label = { Text("کارفرما") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(editAmount, { editAmount = it }, label = { Text("مبلغ (میلیون)") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                                OutlinedTextField(editPhone, { editPhone = it }, label = { Text("تلفن") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                            }
                            OutlinedTextField(editDescription, { editDescription = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                Button(
                                    onClick = {
                                        val amt = editAmount.toDoubleOrNullFa() ?: p.amount
                                        val updated = p.copy(
                                            name = editName.trim().ifBlank { p.name },
                                            employer = editEmployer,
                                            phone = editPhone,
                                            amount = amt,
                                            remaining = amt - p.settled,
                                            description = editDescription,
                                            day = editDay.ifBlank { p.day },
                                            month = editMonth.ifBlank { p.month },
                                            year = editYear.ifBlank { p.year },
                                            hour = editHour.ifBlank { p.hour },
                                            minute = editMinute.ifBlank { p.minute },
                                            calendarEventId = editEventId
                                        )
                                        ProjectStore.save(context, updated)
                                        editingRow = null
                                        statusMsg = "ویرایش ثبت شد"
                                        selectDay(selectedDay!!)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = color),
                                    modifier = Modifier.weight(1f)
                                ) { Text("ثبت ویرایش") }
                                OutlinedButton(onClick = { editingRow = null }, modifier = Modifier.weight(0.7f)) {
                                    Text("انصراف")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteRow != null) {
        val p = confirmDeleteRow!!
        AlertDialog(
            onDismissRequest = { confirmDeleteRow = null },
            title = { Text("حذف پروژه؟") },
            text = {
                Text("«${p.name}» از لیست و در صورت وجود از تقویم Google/دستگاه حذف می‌شود.")
            },
            confirmButton = {
                TextButton(onClick = {
                    // حذف از تقویم
                    var calOk = false
                    val eid = p.calendarEventId.toLongOrNull()
                    if (eid != null) {
                        calOk = com.adel.assistant.data.CalendarHelper.deleteEvent(context, eid)
                    }
                    if (!calOk) {
                        calOk = com.adel.assistant.data.CalendarHelper.deleteEventByTitleAndDay(
                            context,
                            title = "پروژه: ${p.name}",
                            yearJalali = p.year.toIntOrNullFa() ?: year,
                            monthJalali = p.month.toIntOrNullFa() ?: month,
                            dayJalali = p.day.toIntOrNullFa() ?: (selectedDay ?: 1)
                        )
                    }
                    ProjectStore.delete(context, p.row)
                    confirmDeleteRow = null
                    editingRow = null
                    statusMsg = if (calOk) "حذف شد (+ تقویم)" else "حذف از لیست (رویداد تقویم یافت نشد یا مجوز نیست)"
                    selectedDay?.let { selectDay(it) }
                }) { Text("حذف", color = Color(0xFFC62828)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteRow = null }) { Text("انصراف") }
            }
        )
    }

}
