import androidx.compose.material.icons.filled.SmartToy
import com.adel.assistant.ui.theme.ToolPrimary
package com.adel.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.R
import com.adel.assistant.navigation.Routes
import com.adel.assistant.data.AppMenu
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.MenuItem
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.TaskItem
import com.adel.assistant.data.TaskStore
import com.adel.assistant.widget.AdelWidgetProvider
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.BorderColor
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.SurfaceHigh
import com.adel.assistant.ui.theme.TextMuted
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import com.adel.assistant.ui.theme.WorkPrimary
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

private fun timeBasedGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10 -> "صبح بخیر"
        in 11..13 -> "ظهر بخیر"
        in 14..18 -> "عصر بخیر"
        else -> "شب بخیر"
    }
}

private fun currentTimeString(): String {
    val c = Calendar.getInstance()
    return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

private fun jalaliDateString(): String {
    val (y, m, d) = CalendarStore.todayJalali()
    return String.format(Locale.US, "%04d/%02d/%02d", y, m, d)
}

private fun todayWeekdayName(): String {
    return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> "شنبه"
        Calendar.SUNDAY -> "یکشنبه"
        Calendar.MONDAY -> "دوشنبه"
        Calendar.TUESDAY -> "سه‌شنبه"
        Calendar.WEDNESDAY -> "چهارشنبه"
        Calendar.THURSDAY -> "پنج‌شنبه"
        Calendar.FRIDAY -> "جمعه"
        else -> ""
    }
}

private fun jalaliDateWithWeekday(): String {
    return "${todayWeekdayName()} ${jalaliDateString()}"
}

private fun todaySortKey(): String {
    val (y, m, d) = CalendarStore.todayJalali()
    return String.format(Locale.US, "%d%02d%02d", y, m, d)
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val sections = AppMenu.sections
    var sectionIndex by rememberSaveable { mutableStateOf(1) }
    var tabIndex by rememberSaveable { mutableStateOf(0) }

    var clock by remember { mutableStateOf(currentTimeString()) }
    var greeting by remember { mutableStateOf(timeBasedGreeting()) }
    var jalali by remember { mutableStateOf(jalaliDateWithWeekday()) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = currentTimeString()
            greeting = timeBasedGreeting()
            jalali = jalaliDateWithWeekday()
            delay(30_000)
        }
    }

    val todayKey = remember(jalali) { todaySortKey() }
    val projects = remember(todayKey) {
        try {
            ProjectStore.all(context)
                .filter { projectIsTodayOrFuture(it, todayKey) }
                .sortedBy { it.dateSortKey }
        } catch (_: Exception) {
            emptyList()
        }
    }
    var tunnelTasks by remember {
        mutableStateOf(
            try {
                TaskStore.load(context, "tunnel_tasks")
                    .filter { !it.completed }
                    .sortedByDescending { it.createdAt }
            } catch (_: Exception) { emptyList() }
        )
    }
    var projectTasks by remember {
        mutableStateOf(
            try {
                TaskStore.load(context, "project_tasks")
                    .filter { !it.completed }
                    .sortedByDescending { it.createdAt }
            } catch (_: Exception) { emptyList() }
        )
    }

    fun completeTask(storeName: String, title: String, createdAt: Long) {
        val all = TaskStore.load(context, storeName).map {
            if (it.title == title && it.createdAt == createdAt) it.copy(completed = true) else it
        }
        TaskStore.save(context, storeName, all)
        val open = all.filter { !it.completed }.sortedByDescending { it.createdAt }.take(3)
        if (storeName == "tunnel_tasks") tunnelTasks = open else projectTasks = open
        try { AdelWidgetProvider.refreshAll(context) } catch (_: Exception) {}
    }

    val section = sections.getOrElse(sectionIndex) { sections.first() }
    val tabs = section.tabs
    val currentTab = tabs.getOrNull(tabIndex) ?: tabs.first()
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- کادر بالا ----
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceColor,
                border = BorderStroke(0.5.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            clock,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            jalali,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "$greeting مهندس پورمیر",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WorkPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // foreground امن‌تر از adaptive icon است
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "لوگو",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceHigh),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // ---- برنامه‌های کاری ----
            DashboardCard(title = "برنامه‌های کاری پیش‌رو") {
                if (projects.isEmpty()) {
                    Text(
                        "پروژه‌ای از امروز به بعد ثبت نشده",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    ScrollBox3 {
                        projects.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    p.name.ifBlank { "بدون نام" },
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    formatProjectDate(p),
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // ---- تسک‌ها ----
            DashboardCard(title = "تسک‌های انجام‌نشده") {
                Text(
                    "تسک‌های پروژه ▶",
                    color = WorkPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        onNavigate(com.adel.assistant.navigation.Routes.SURVEY_PROJECT_TASKS)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (projectTasks.isEmpty()) {
                    Text("تسک باز ندارد", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    ScrollBox3 {
                        projectTasks.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "• ${t.title}",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "انجام",
                                    color = WorkPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { completeTask("project_tasks", t.title, t.createdAt) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "تسک‌های تونل ▶",
                    color = WorkPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        onNavigate(com.adel.assistant.navigation.Routes.SURVEY_TUNNEL_TASKS)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (tunnelTasks.isEmpty()) {
                    Text("تسک باز ندارد", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    ScrollBox3 {
                        tunnelTasks.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "• ${t.title}",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "انجام",
                                    color = WorkPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { completeTask("tunnel_tasks", t.title, t.createdAt) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ---- عنوان بخش ----
            Text(
                section.title,
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (tabs.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = index == tabIndex
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { tabIndex = index },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) section.color.copy(alpha = 0.18f) else Color.Transparent,
                            border = if (!selected) BorderStroke(0.5.dp, BorderColor) else null
                        ) {
                            Text(
                                tab.title,
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = if (selected) section.color else TextSecondary,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // ---- منو بدون LazyVerticalGrid تو در تو (عامل کرش) ----
            MenuGrid(
                items = currentTab.items,
                accent = section.color,
                onNavigate = onNavigate
            )
        }

        // ---- نوار پایین ----
        Surface(color = SurfaceHigh, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sections.forEachIndexed { index, s ->
                    val selected = index == sectionIndex
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                sectionIndex = index
                                tabIndex = 0
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            s.icon,
                            contentDescription = s.title,
                            tint = if (selected) s.color else TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            s.title,
                            fontSize = 11.sp,
                            color = if (selected) s.color else TextMuted
                        )
                    }
                }
                // دستیار هوشمند — آیتم چهارم نوار پایین
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(Routes.ASSISTANT) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.SmartToy,
                        contentDescription = "دستیار",
                        tint = ToolPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "دستیار",
                        fontSize = 11.sp,
                        color = ToolPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuGrid(
    items: List<MenuItem>,
    accent: Color,
    onNavigate: (String) -> Unit
) {
    val rows = items.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(item.route) },
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceColor
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(item.icon, contentDescription = item.title, tint = accent)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                item.title,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // پر کردن خانه‌های خالی ردیف آخر
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = BorderStroke(0.5.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}


/** فقط امروز و آینده — مقایسه عددی سال/ماه/روز */
private fun projectIsTodayOrFuture(p: ProjectEntry, todayKey: String): Boolean {
    val key = normalizeDateKey(p.year, p.month, p.day)
    return key != null && key >= todayKey
}

private fun normalizeDateKey(year: String, month: String, day: String): String? {
    val y = year.toIntOrNullFa() ?: return null
    val m = month.toIntOrNullFa() ?: return null
    val d = day.toIntOrNullFa() ?: return null
    if (y < 1300 || y > 1500) return null
    if (m !in 1..12 || d !in 1..31) return null
    return String.format(Locale.US, "%d%02d%02d", y, m, d)
}

@Composable
private fun ScrollBox3(content: @Composable ColumnScope.() -> Unit) {
    // حدود ارتفاع ۳ ردیف؛ بقیه با اسکرول داخل کادر
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 90.dp)
            .verticalScroll(rememberScrollState())
    ) {
        content()
    }
}

private fun formatProjectDate(p: ProjectEntry): String {
    val y = p.year.ifBlank { "—" }
    val m = (p.month.toIntOrNullFa() ?: 0).toString().padStart(2, '0')
    val d = (p.day.toIntOrNullFa() ?: 0).toString().padStart(2, '0')
    return "$y/$m/$d"
}
