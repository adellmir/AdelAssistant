package com.adel.assistant.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.adel.assistant.data.AppMenu
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.MenuItem
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.TaskItem
import com.adel.assistant.data.TaskStore
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

private fun todaySortKey(): String {
    val (y, m, d) = CalendarStore.todayJalali()
    return String.format(Locale.US, "%d%02d%02d", y, m, d)
}

private fun upcomingProjects(context: Context): List<ProjectEntry> {
    val today = todaySortKey()
    return ProjectStore.all(context)
        .filter { it.dateSortKey >= today }
        .sortedBy { it.dateSortKey }
}

private fun openTasks(context: Context, storeName: String): List<TaskItem> {
    return TaskStore.load(context, storeName).filter { !it.completed }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val sections = AppMenu.sections
    var sectionIndex by rememberSaveable { mutableStateOf(1) }
    var tabIndex by rememberSaveable { mutableStateOf(0) }

    var clock by remember { mutableStateOf(currentTimeString()) }
    var greeting by remember { mutableStateOf(timeBasedGreeting()) }
    var jalali by remember { mutableStateOf(jalaliDateString()) }

    // به‌روزرسانی ساعت هر ۳۰ ثانیه
    LaunchedEffect(Unit) {
        while (true) {
            clock = currentTimeString()
            greeting = timeBasedGreeting()
            jalali = jalaliDateString()
            delay(30_000)
        }
    }

    val projects = remember(jalali) { upcomingProjects(context) }
    val tunnelTasks = remember { openTasks(context, "tunnel_tasks") }
    val projectTasks = remember { openTasks(context, "project_tasks") }

    val section = sections[sectionIndex]
    val tabs = section.tabs
    val currentTab = tabs.getOrNull(tabIndex) ?: tabs.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 12.dp)
        ) {
            // ---- کادر بالا: ساعت / تاریخ / خوش‌آمد + لوگو ----
            item {
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
                        // در RTL اولین فرزند سمت راست است
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
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "لوگو",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // ---- کادر وسط: برنامه‌های کاری از امروز به بعد ----
            item {
                DashboardCard(title = "برنامه‌های کاری پیش‌رو") {
                    if (projects.isEmpty()) {
                        Text(
                            "پروژه‌ای از امروز به بعد ثبت نشده",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        projects.take(12).forEach { p ->
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

            // ---- کادر پایین: تسک‌های انجام‌نشده ----
            item {
                DashboardCard(title = "تسک‌های انجام‌نشده") {
                    Text(
                        "پروژه‌ها",
                        color = WorkPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (projectTasks.isEmpty()) {
                        Text("تسک باز ندارد", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        projectTasks.take(8).forEach { t ->
                            Text("• ${t.title}", color = TextPrimary, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "تونل",
                        color = WorkPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (tunnelTasks.isEmpty()) {
                        Text("تسک باز ندارد", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        tunnelTasks.take(8).forEach { t ->
                            Text("• ${t.title}", color = TextPrimary, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }

            // ---- منوی بخش انتخاب‌شده از نوار پایین ----
            item {
                Text(
                    section.title,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (tabs.size > 1) {
                item {
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
            }

            item {
                // گرید منو داخل ارتفاع ثابت تا LazyColumn درست اسکرول شود
                val rows = (currentTab.items.size + 2) / 3
                val gridHeight = (rows * 100).dp
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight.coerceAtLeast(100.dp)),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false
                ) {
                    gridItems(currentTab.items) { item: MenuItem ->
                        Surface(
                            modifier = Modifier.clickable { onNavigate(item.route) },
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceColor
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(item.icon, contentDescription = item.title, tint = section.color)
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
                }
            }
        }

        // ---- نوار پایین ثابت (مثل قبل) ----
        Surface(
            color = SurfaceHigh,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp
        ) {
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

private fun formatProjectDate(p: ProjectEntry): String {
    val y = p.year.ifBlank { "—" }
    val m = (p.month.toIntOrNullFa() ?: 0).toString().padStart(2, '0')
    val d = (p.day.toIntOrNullFa() ?: 0).toString().padStart(2, '0')
    return "$y/$m/$d"
}
