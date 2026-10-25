package com.adel.assistant.ui.screens

import com.adel.assistant.ui.components.AdelOutlinedTextField

import androidx.compose.foundation.BorderStroke
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
import com.adel.assistant.data.ProjectLocation
import com.adel.assistant.data.formatEn
import com.adel.assistant.ui.PendingProjectEdit
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.data.CalendarHelper
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.appearanceScreenBg
import com.adel.assistant.ui.appearanceCardColor
import com.adel.assistant.ui.theme.Surface as SurfaceColor

private val WEEK_DAYS_FA = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

private val PERSIAN_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

@Composable
fun WorkCalendarScreen(
    color: Color,
    onBack: () -> Unit,
    onAddProject: ((day: Int, month: Int, year: Int) -> Unit)? = null,
    onEditProject: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)
    val today = remember { CalendarStore.todayJalali() }
    var year by remember { mutableStateOf(today.first) }
    var month by remember { mutableStateOf(today.second) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var dayProjects by remember { mutableStateOf(listOf<ProjectEntry>()) }
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
            .background(appearanceScreenBg())
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
                val isToday = year == today.first && month == today.second && d == today.third
                Surface(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { selectDay(d) },
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        selected -> color
                        hasProject -> color.copy(alpha = 0.25f)
                        else -> SurfaceColor
                    },
                    border = if (isToday) BorderStroke(2.dp, Color.White) else null
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
                Surface(shape = RoundedCornerShape(10.dp), color = appearanceCardColor(), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${p.name} — ${p.employer}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (p.hasLocation) color else Color.Unspecified,
                                    modifier = Modifier.clickable {
                                        if (p.hasLocation) ProjectLocation.openNavigation(context, p)
                                    }
                                )
                                Text(
                                    formatEn("مبلغ: %.0f  |  %s:%s", p.amount, p.hour.ifBlank { "9" }, p.minute.padStart(2, '0')),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF7C8A6B)
                                )
                                if (p.description.isNotBlank()) {
                                    Text(
                                        p.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9E9E9E)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                PendingProjectEdit.rowId = p.row
                                if (onEditProject != null) onEditProject.invoke()
                                else statusMsg = "مسیر ویرایش در دسترس نیست"
                            }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "ویرایش کامل", tint = color)
                            }
                            IconButton(onClick = {
                                val y = p.year.toIntOrNull() ?: year
                                val m = p.month.toIntOrNull() ?: month
                                val d = p.day.toIntOrNull() ?: (selectedDay ?: 1)
                                try {
                                    CalendarHelper.deleteEventByTitleAndDay(
                                        context,
                                        title = "پروژه: ${p.name}",
                                        yearJalali = y,
                                        monthJalali = m,
                                        dayJalali = d
                                    )
                                } catch (_: Exception) { }
                                ProjectStore.delete(context, p.row)
                                statusMsg = "پروژه حذف شد"
                                selectedDay?.let { selectDay(it) }
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "حذف", tint = Color(0xFFCF6679))
                            }
                        }
                    }
                }
            }

        }
    }
}