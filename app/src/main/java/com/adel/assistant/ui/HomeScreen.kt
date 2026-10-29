package com.adel.assistant.ui
import com.adel.assistant.ui.appearanceTextPrimary
import com.adel.assistant.ui.appearanceTextSecondary
import com.adel.assistant.ui.appearanceTextMuted
import com.adel.assistant.ui.appearanceControlBg
import com.adel.assistant.data.TunnelFinanceStore
import com.adel.assistant.data.toEnglishDigits
import com.adel.assistant.ui.PendingDailyReport
import com.adel.assistant.ui.PendingTunnelLookup

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.material.icons.filled.Navigation

import androidx.compose.material.icons.filled.Sms

import androidx.compose.material.icons.filled.Phone

import com.adel.assistant.ui.PendingClientsNav

import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.sideDisplayName

import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import com.adel.assistant.ui.components.AdelOutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.R
import com.adel.assistant.data.AppMenu
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.MenuItem
import com.adel.assistant.data.ProjectEntry
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.WeatherStore
import com.adel.assistant.data.AppearanceMode
import com.adel.assistant.data.AppearanceStore
import com.adel.assistant.data.LiveLocationStore
import com.adel.assistant.data.WeatherIconKind
import com.adel.assistant.data.openNeshanNav
import com.adel.assistant.ui.screens.ProjectLocationPickerDialog
import com.adel.assistant.data.TaskItem
import com.adel.assistant.data.TaskStore
import com.adel.assistant.widget.AdelWidgetProvider
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.appearanceScreenBg
import com.adel.assistant.ui.appearanceCardColor
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.adel.assistant.navigation.Routes
import com.adel.assistant.ui.theme.ToolPrimary

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


private val PERSIAN_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)

private fun monthIndexFromName(s: String): Int? {
    val t = s.trim().replace("ي", "ی").replace("ك", "ک")
    PERSIAN_MONTHS.forEachIndexed { i, name ->
        if (t.contains(name)) return i + 1
    }
    return null
}

private fun parseDayMonthYear(q: String): Triple<Int?, Int?, Int?> {
    // returns day, month, year (nullable)
    val e = q.toEnglishDigits().trim()
    val mon = monthIndexFromName(e)
    val yearM = Regex("""(13|14)\d{2}""").find(e)?.value?.toIntOrNull()
    val dayM = Regex("""(?<![\d.])(\d{1,2})(?![\d.])""").find(e.replace(Regex("""(13|14)\d{2}"""), ""))?.groupValues?.getOrNull(1)?.toIntOrNull()
    // "05/05" or "5/5"
    val slash = Regex("""(\d{1,2})\s*/\s*(\d{1,2})(?:\s*/\s*((?:13|14)\d{2}))?""").find(e)
    if (slash != null) {
        val a = slash.groupValues[1].toInt()
        val b = slash.groupValues[2].toInt()
        val y = slash.groupValues.getOrNull(3)?.toIntOrNull()
        // day/month
        return Triple(a, b, y)
    }
    if (mon != null) {
        // if only month+year or month+day
        val nums = Regex("""\d+""").findAll(e).map { it.value.toInt() }.toList()
        val day = nums.firstOrNull { it in 1..31 && it != mon && (yearM == null || it != yearM) }
        return Triple(day, mon, yearM)
    }
    return Triple(null, null, null)
}


object PendingAssistantPrompt {
    var text: String = ""
}

object PendingProjectEdit {
    var rowId: String? = null
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    var locEditProject by remember { mutableStateOf<com.adel.assistant.data.ProjectEntry?>(null) }
    val sections = AppMenu.sections
    var sectionIndex by rememberSaveable { mutableStateOf(0) }
    var tabIndex by rememberSaveable { mutableStateOf(0) }

    var clock by remember { mutableStateOf(currentTimeString()) }
    var weatherSnap by remember { mutableStateOf(WeatherStore.cached()) }
    var weatherLoading by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // هر بار ورود به صفحه اصلی — رفرش فوری هواشناسی
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            LiveLocationStore.ensure(context)
            weatherLoading = true
            weatherSnap = WeatherStore.fetch(context, force = true)
            weatherLoading = false
        }
    }
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
    var planOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var expandedEmp by remember { mutableStateOf<String?>(null) }
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
            .background(appearanceScreenBg())
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
                color = appearanceCardColor(),
                border = BorderStroke(0.5.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // سمت چپ: لوگو
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = "لوگو",
                            modifier = Modifier
                                .size(67.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceHigh),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        // سمت راست: ساعت / تاریخ / خوش‌آمد + هواشناسی
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            // ردیف ۱: ساعت (وسط‌چین)
                            Text(
                                clock,
                                style = MaterialTheme.typography.headlineSmall,
                                color = appearanceTextPrimary(),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // ردیف ۲ و ۳ + هواشناسی سمت راست
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        jalali,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = appearanceTextSecondary()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "$greeting مهندس پورمیر",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = WorkPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val snap = weatherSnap
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            val url = if (snap != null)
                                                WeatherStore.googleWeatherUrl(snap.lat, snap.lon)
                                            else
                                                WeatherStore.googleWeatherUrlFallback()
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } catch (_: Exception) {
                                            }
                                        }
                                        .padding(horizontal = 4.dp)
                                ) {
                                    val kind = snap?.let { WeatherStore.iconKind(it.conditionId) } ?: WeatherIconKind.Unknown
                                    val icon = when (kind) {
                                        WeatherIconKind.Clear -> Icons.Outlined.WbSunny
                                        WeatherIconKind.Clouds -> Icons.Outlined.Cloud
                                        WeatherIconKind.Rain, WeatherIconKind.Drizzle -> Icons.Outlined.WaterDrop
                                        WeatherIconKind.Snow -> Icons.Outlined.AcUnit
                                        WeatherIconKind.Thunder -> Icons.Outlined.Thunderstorm
                                        WeatherIconKind.Fog -> Icons.Outlined.CloudQueue
                                        WeatherIconKind.Unknown -> Icons.Outlined.CloudQueue
                                    }
                                    val iconTint = when (kind) {
                                        WeatherIconKind.Clear -> Color(0xFFFFB74D)
                                        WeatherIconKind.Rain, WeatherIconKind.Drizzle -> Color(0xFF64B5F6)
                                        WeatherIconKind.Snow -> Color(0xFFE0E0E0)
                                        WeatherIconKind.Thunder -> Color(0xFFBA68C8)
                                        else -> appearanceTextSecondary()
                                    }
                                    Icon(
                                        icon,
                                        contentDescription = snap?.description ?: "هواشناسی",
                                        tint = iconTint,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Text(
                                        when {
                                            snap != null -> String.format(java.util.Locale.US, "%.0f°", snap.tempC)
                                            weatherLoading -> "…"
                                            else -> "—"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = appearanceTextSecondary()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- برنامه‌ریزی (پروژه + تسک) ----
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = appearanceCardColor(),
                border = BorderStroke(0.5.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { planOpen = !planOpen },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "برنامه‌ریزی",
                            style = MaterialTheme.typography.titleSmall,
                            color = appearanceTextPrimary(),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "پروژه ${projects.size} | تسک ${projectTasks.size + tunnelTasks.size}",
                            color = appearanceTextSecondary(),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Icon(
                            imageVector = if (planOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = appearanceTextSecondary(),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    if (planOpen) {
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("پروژه پیش‌رو", color = WorkPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (projects.isEmpty()) {
                            Text("پروژه‌ای از امروز به بعد ثبت نشده", color = appearanceTextMuted(), style = MaterialTheme.typography.bodySmall)
                        } else {
                            ScrollBox3 {
                                projects.forEach { p ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                if (p.hasLocation) openNeshanNav(context, p)
                                                else locEditProject = p
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${p.name.ifBlank { "بدون نام" }} - ${formatProjectDate(p)}",
                                            color = if (p.hasLocation) WorkPrimary else appearanceTextPrimary(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "تسک پروژه ▶",
                            color = WorkPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onNavigate(Routes.SURVEY_PROJECT_TASKS) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (projectTasks.isEmpty()) {
                            Text("تسک باز ندارد", color = appearanceTextMuted(), style = MaterialTheme.typography.bodySmall)
                        } else {
                            ScrollBox3 {
                                projectTasks.forEach { task ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable { completeTask("project_tasks", task.title, task.createdAt) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("○", color = WorkPrimary, modifier = Modifier.padding(end = 6.dp))
                                        Text(task.title, color = appearanceTextPrimary(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "تسک تونل ▶",
                            color = WorkPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onNavigate(Routes.SURVEY_TUNNEL_TASKS) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (tunnelTasks.isEmpty()) {
                            Text("تسک باز ندارد", color = appearanceTextMuted(), style = MaterialTheme.typography.bodySmall)
                        } else {
                            ScrollBox3 {
                                tunnelTasks.forEach { task ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable { completeTask("tunnel_tasks", task.title, task.createdAt) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("○", color = WorkPrimary, modifier = Modifier.padding(end = 6.dp))
                                        Text(task.title, color = appearanceTextPrimary(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- نوار جستجو + دستیار ----
            val allMenuItems = remember {
                AppMenu.sections.flatMap { sec -> sec.tabs.flatMap { tab -> tab.items } }.distinctBy { it.route }
            }
            val tunnelDefaultItems = remember {
                AppMenu.sections
                    .find { it.title == "نقشه‌برداری" }
                    ?.tabs?.find { it.title == "تونل" }
                    ?.items
                    ?: emptyList()
            }
            val query = searchQuery.trim()
            val filteredItems: List<MenuItem> = remember(query, sectionIndex, tabIndex) {
                if (query.isNotEmpty()) {
                    val words = query.split(Regex("\\s+")).filter { it.isNotBlank() }
                    allMenuItems.filter { item ->
                        val title = item.title
                        words.any { w -> title.contains(w, ignoreCase = true) }
                    }
                } else {
                    emptyList()
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = appearanceCardColor(),
                border = BorderStroke(0.5.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.SmartToy,
                        contentDescription = "دستیار",
                        tint = ToolPrimary,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                val q = searchQuery.trim()
                                if (q.isNotEmpty()) {
                                    PendingAssistantPrompt.text = q
                                }
                                onNavigate(Routes.ASSISTANT)
                            }
                            .padding(4.dp)
                    )
                    AdelOutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("جستجو: شفت، پیشرفت، مطالبات، پروژه…", color = appearanceTextMuted(), fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = appearanceTextPrimary(),
                            unfocusedTextColor = appearanceTextPrimary()
                        ),
                        textStyle = TextStyle(fontSize = 14.sp, color = appearanceTextPrimary()),
                        leadingIcon = {
                            Icon(Icons.Filled.Search, null, tint = appearanceTextMuted(), modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "پاک",
                                    tint = appearanceTextMuted(),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }
                    )
                }
            }

            // ---- نتایج جستجو: آیکن برنامه‌ها مثل گیت‌هاب (MenuGrid) ----
            if (query.isNotEmpty() && filteredItems.isNotEmpty()) {
                Text(
                    "نتایج جستجو (${filteredItems.size})",
                    color = appearanceTextSecondary(),
                    style = MaterialTheme.typography.labelLarge
                )
                MenuGrid(items = filteredItems, accent = WorkPrimary, onNavigate = onNavigate)
            }

            // ---- نتایج هوشمند چندمنبعی (ماه / تاریخ / مطالبات / درآمد / …) ----
            if (query.isNotEmpty()) {
                val qLower = query.lowercase().trim()
                val qEn = query.toEnglishDigits().trim()

                fun moneyM(v: Double): String =
                    if (kotlin.math.abs(v - v.toLong().toDouble()) < 1e-9)
                        String.format(java.util.Locale.US, "%,.0f م", v)
                    else
                        String.format(java.util.Locale.US, "%,.1f م", v)

                fun callPhone(phone: String) {
                    if (phone.isBlank()) return
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    } catch (_: Exception) { }
                }
                fun smsClaims(phone: String, amount: Double) {
                    if (phone.isBlank()) return
                    val amt = amount.coerceAtLeast(0.0)
                    val amountStr = if (kotlin.math.abs(amt - amt.toLong().toDouble()) < 1e-6)
                        String.format(java.util.Locale.US, "%,.0f", amt)
                    else
                        String.format(java.util.Locale.US, "%,.1f", amt)
                    val body =
                        "با سلام جهت پرداخت هزینه نقشه‌برداری به‌مبلغ $amountStr م تومان. ممنون می‌شوم پس از پرداخت اطلاع‌رسانی بفرمایید.\n" +
                            "کارت: 5859831142797561\n" +
                            "شبا: 430180000000242375213376"
                    try {
                        val uri = Uri.parse("smsto:$phone?body=" + Uri.encode(body))
                        context.startActivity(Intent(Intent.ACTION_SENDTO, uri).apply {
                            putExtra("sms_body", body)
                            putExtra(Intent.EXTRA_TEXT, body)
                        })
                    } catch (_: Exception) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phone?body=" + Uri.encode(body))))
                        } catch (_: Exception) { }
                    }
                }

                val monthNames = listOf(
                    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
                )
                val monthNum = monthNames.indexOfFirst { qLower.contains(it) }.let { if (it >= 0) it + 1 else null }
                // سال همراه ماه: مهر ۱۴۰۵
                val yearWithMonth = Regex("""(14\d{2}|13\d{2})""").find(qEn)?.value?.toIntOrNull()
                val todayJ = try { CalendarStore.todayJalali() } catch (_: Exception) { Triple(1404, 1, 1) }
                val focusYear = yearWithMonth ?: todayJ.first

                // تاریخ فشرده YYYYMMDD یا YYYY/MM/DD
                val ymd = Regex("""^(14\d{2}|13\d{2})[/\-.]?(\d{1,2})[/\-.]?(\d{1,2})$""").matchEntire(qEn.replace(" ", ""))
                val ymd2 = Regex("""^(14\d{2}|13\d{2})(\d{2})(\d{2})$""").matchEntire(qEn.replace(" ", ""))
                val dateY: Int?
                val dateM: Int?
                val dateD: Int?
                when {
                    ymd != null -> {
                        dateY = ymd.groupValues[1].toIntOrNull()
                        dateM = ymd.groupValues[2].toIntOrNull()
                        dateD = ymd.groupValues[3].toIntOrNull()
                    }
                    ymd2 != null -> {
                        dateY = ymd2.groupValues[1].toIntOrNull()
                        dateM = ymd2.groupValues[2].toIntOrNull()
                        dateD = ymd2.groupValues[3].toIntOrNull()
                    }
                    else -> { dateY = null; dateM = null; dateD = null }
                }
                val pureYear = Regex("""^(14\d{2}|13\d{2})$""").matchEntire(qEn)?.value?.toIntOrNull()
                val pureNum = qEn.toDoubleOrNull()
                val isPureNumberQ = pureNum != null && dateY == null && pureYear == null

                val isClaims = qLower.contains("مطالبات") || qLower.contains("طلب")
                val isIncome = qLower.contains("درآمد") || qLower.contains("دریافت")
                val isFallQ = qLower.contains("ریزش")
                val isDevQ = qLower.contains("انحراف")
                val isNoteAny = qLower.contains("توضیح")
                val isShaft = qLower.contains("شفت") || Regex("""(?i)^sh\s*\d*$""").matches(qLower)

                // —— داده پروژه‌ها / تونل / مالی ——
                val allProjects = remember(query) {
                    try { ProjectStore.all(context) } catch (_: Exception) { emptyList() }
                }
                val allTunnelEntries = remember(query) {
                    try { TunnelReportStore.allEntries(context) } catch (_: Exception) { emptyList() }
                }
                val allTunnelFinance = remember(query) {
                    try { TunnelFinanceStore.all(context) } catch (_: Exception) { emptyList() }
                }

                fun monthMatch(y: String, m: String, wantY: Int, wantM: Int): Boolean {
                    val yi = y.toIntOrNull() ?: return false
                    val mi = m.toIntOrNull() ?: return false
                    return yi == wantY && mi == wantM
                }
                fun dayMatch(y: String, m: String, d: String, wy: Int, wm: Int, wd: Int): Boolean {
                    val yi = y.toIntOrNull() ?: return false
                    val mi = m.toIntOrNull() ?: return false
                    val di = d.toIntOrNull() ?: return false
                    return yi == wy && mi == wm && di == wd
                }

                // پروژه‌های ماه
                val monthProjects = if (monthNum != null) {
                    allProjects.filter { monthMatch(it.year, it.month, focusYear, monthNum) }
                        .sortedByDescending { it.dateSortKey }
                } else emptyList()
                // پروژه‌های روز
                val dayProjects = if (dateY != null && dateM != null && dateD != null) {
                    allProjects.filter { dayMatch(it.year, it.month, it.day, dateY!!, dateM!!, dateD!!) }
                } else emptyList()
                // پروژه‌های سال
                val yearProjects = if (pureYear != null) {
                    allProjects.filter { (it.year.toIntOrNull() ?: 0) == pureYear }
                } else emptyList()
                // نام پروژه/کارفرما با شروع کوئری
                val nameProjects = allProjects.filter { it.name.startsWith(query, true) }.distinctBy { it.name }
                val nameEmployers = allProjects.filter { it.employer.startsWith(query, true) }
                    .groupBy { it.employer }.map { it.key to it.value }

                val tunnelMonth = if (monthNum != null) {
                    allTunnelEntries.filter { monthMatch(it.year, it.month, focusYear, monthNum) }
                } else emptyList()
                val tunnelDay = if (dateY != null && dateM != null && dateD != null) {
                    allTunnelEntries.filter { dayMatch(it.year, it.month, it.day, dateY!!, dateM!!, dateD!!) }
                } else emptyList()
                val tunnelYear = if (pureYear != null) {
                    allTunnelEntries.filter { (it.year.toIntOrNull() ?: 0) == pureYear }
                } else emptyList()

                val finMonth = if (monthNum != null) {
                    allTunnelFinance.filter { it.year == focusYear && it.month == monthNum }
                } else emptyList()
                val finYear = if (pureYear != null) {
                    allTunnelFinance.filter { it.year == pureYear }
                } else emptyList()

                // درآمد/دریافتی پروژه‌ها در ماه
                val projIncomeMonth = monthProjects.sumOf { it.amount }
                val projReceivedMonth = monthProjects.sumOf { it.settled }
                val tunnelIncomeMonth = finMonth.sumOf { it.income }
                val tunnelReceivedMonth = finMonth.sumOf { it.receiveAmount ?: 0.0 }

                val pointHits = if (isPureNumberQ) {
                    try {
                        TunnelReportStore.allPoints(context).filter {
                            it.pointNo == qEn || it.pointNo.contains(qEn) ||
                                kotlin.math.abs(it.km - (pureNum ?: -1.0)) < 0.001
                        }
                    } catch (_: Exception) { emptyList() }
                } else emptyList()

                val empHits = try {
                    ProjectStore.employerProfiles(context, query)
                } catch (_: Exception) { emptyList() }
                val projHits = try {
                    ProjectStore.projectProfiles(context, query)
                } catch (_: Exception) { emptyList() }

                data class SearchTab(val id: String, val title: String, val count: Int)
                val searchTabs = buildList {
                    if (monthProjects.isNotEmpty() || dayProjects.isNotEmpty() || yearProjects.isNotEmpty() || nameProjects.isNotEmpty())
                        add(SearchTab("projects", "پروژه‌ها",
                            (monthProjects.ifEmpty { dayProjects.ifEmpty { yearProjects.ifEmpty { nameProjects } } }).size))
                    if (monthNum != null || pureYear != null || isIncome) {
                        val hasInc = projIncomeMonth > 0 || tunnelIncomeMonth > 0 || projReceivedMonth > 0 ||
                            tunnelReceivedMonth > 0 || finYear.isNotEmpty()
                        if (hasInc || isIncome) add(SearchTab("income", "درآمد و دریافتی", 1))
                    }
                    if (tunnelMonth.isNotEmpty() || tunnelDay.isNotEmpty() || tunnelYear.isNotEmpty())
                        add(SearchTab("tunnel", "تونل",
                            tunnelMonth.ifEmpty { tunnelDay.ifEmpty { tunnelYear } }.size))
                    if (nameEmployers.isNotEmpty() || empHits.isNotEmpty())
                        add(SearchTab("emp", "کارفرما", nameEmployers.size.coerceAtLeast(empHits.size)))
                    if (projHits.isNotEmpty() && nameProjects.isEmpty())
                        add(SearchTab("proj", "پروژه", projHits.size))
                    if (pointHits.isNotEmpty())
                        add(SearchTab("points", "نقاط", pointHits.size))
                    if (isClaims) {
                        add(SearchTab("claims_proj", "مطالبات پروژه", 1))
                        add(SearchTab("claims_tunnel", "مطالبات تونل", 1))
                    }
                    if (isFallQ) {
                        val n = allTunnelEntries.count { it.collapse.isNotBlank() }
                        if (n > 0) add(SearchTab("fall", "ریزش", n))
                    }
                    if (isDevQ) {
                        val n = allTunnelEntries.count { it.deviation.isNotBlank() }
                        if (n > 0) add(SearchTab("dev", "انحراف", n))
                    }
                    if (isNoteAny) {
                        val n = allTunnelEntries.count { it.notes.isNotBlank() }
                        if (n > 0) add(SearchTab("note_ex", "توضیحات حفاری", n))
                    }
                }

                var searchTabId by remember(query) {
                    mutableStateOf(searchTabs.firstOrNull()?.id ?: "")
                }
                LaunchedEffect(query, searchTabs.joinToString { it.id }) {
                    if (searchTabs.none { it.id == searchTabId }) {
                        searchTabId = searchTabs.firstOrNull()?.id ?: ""
                    }
                }

                if (searchTabs.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        searchTabs.forEach { tab ->
                            val sel = searchTabId == tab.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (sel) WorkPrimary.copy(alpha = 0.35f) else appearanceCardColor(),
                                border = if (sel) BorderStroke(1.dp, WorkPrimary) else null,
                                modifier = Modifier.clickable { searchTabId = tab.id }
                            ) {
                                Text(
                                    if (tab.count > 1) "${tab.title} (${tab.count})" else tab.title,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (sel) WorkPrimary else appearanceTextPrimary(),
                                    fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = appearanceCardColor(),
                        border = BorderStroke(0.5.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            when (searchTabId) {
                                "projects" -> {
                                    val list = monthProjects.ifEmpty { dayProjects.ifEmpty { yearProjects.ifEmpty { nameProjects } } }
                                    list.take(40).forEach { p ->
                                        Text(
                                            "${p.name} / ${p.employer} · ${p.year}/${p.month}/${p.day} · ${moneyM(p.remaining)}",
                                            color = appearanceTextPrimary(),
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    PendingClientsNav.openProject(p.name)
                                                    onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                                }
                                                .padding(vertical = 3.dp)
                                        )
                                    }
                                    if (list.isEmpty()) Text("پروژه‌ای نیست", color = appearanceTextMuted(), fontSize = 12.sp)
                                }
                                "income" -> {
                                    if (monthNum != null) {
                                        val mn = monthNames.getOrNull(monthNum - 1) ?: ""
                                        Text("ماه $mn $focusYear", color = WorkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("درآمد پروژه: ${moneyM(projIncomeMonth)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                        Text("دریافتی پروژه: ${moneyM(projReceivedMonth)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                        Text("درآمد تونل: ${moneyM(tunnelIncomeMonth)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                        Text("دریافتی تونل: ${moneyM(tunnelReceivedMonth)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                    } else if (pureYear != null) {
                                        val py = yearProjects.sumOf { it.amount }
                                        val pr = yearProjects.sumOf { it.settled }
                                        val ty = finYear.sumOf { it.income }
                                        val tr = finYear.sumOf { it.receiveAmount ?: 0.0 }
                                        Text("سال $pureYear", color = WorkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("درآمد پروژه: ${moneyM(py)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                        Text("دریافتی پروژه: ${moneyM(pr)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                        Text("درآمد تونل: ${moneyM(ty)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                        Text("دریافتی تونل: ${moneyM(tr)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                    } else {
                                        Text("نام ماه یا سال را وارد کنید (مثل مهر یا ۱۴۰۵)", color = appearanceTextMuted(), fontSize = 12.sp)
                                    }
                                }
                                "tunnel" -> {
                                    val list = tunnelMonth.ifEmpty { tunnelDay.ifEmpty { tunnelYear } }
                                    list.take(30).forEach { e ->
                                        Text(
                                            "${e.year}/${e.month}/${e.day} شفت ${e.shaft} سمت ${e.side} · طول ${e.lengthCm} · نقطه ${e.pointNo}",
                                            color = appearanceTextPrimary(), fontSize = 12.sp,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                    if (list.isEmpty()) Text("گزارش حفاری نیست", color = appearanceTextMuted(), fontSize = 12.sp)
                                }
                                "emp" -> {
                                    if (nameEmployers.isNotEmpty()) {
                                        nameEmployers.take(20).forEach { (emp, rows) ->
                                            Text(
                                                "$emp — ${rows.size} پروژه · مطالبات ${moneyM(rows.sumOf { it.remaining })}",
                                                color = appearanceTextPrimary(), fontSize = 12.sp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        PendingClientsNav.openEmployer(emp)
                                                        onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                                    }
                                                    .padding(vertical = 3.dp)
                                            )
                                        }
                                    } else {
                                        empHits.take(20).forEach { ep ->
                                            Text(
                                                "${ep.employer} — ${ep.projectCount} پروژه · ${moneyM(ep.totalClaims)}",
                                                color = appearanceTextPrimary(), fontSize = 12.sp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        PendingClientsNav.openEmployer(ep.employer)
                                                        onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                                    }
                                                    .padding(vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                                "proj" -> projHits.take(30).forEach { pp ->
                                    Text(
                                        "${pp.name} / ${pp.employer} · ${moneyM(pp.totalClaims)}",
                                        color = appearanceTextPrimary(), fontSize = 12.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                PendingClientsNav.openProject(pp.name)
                                                onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                            }
                                            .padding(vertical = 3.dp)
                                    )
                                }
                                "points" -> pointHits.take(25).forEach { pt ->
                                    Text(
                                        "نقطه ${pt.pointNo} · km ${"%.3f".format(pt.km)}",
                                        color = appearanceTextPrimary(), fontSize = 12.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                PendingTunnelLookup.openPoint(pt.pointNo)
                                                onNavigate(Routes.SURVEY_TUNNEL_EVENTS)
                                            }
                                            .padding(vertical = 3.dp)
                                    )
                                }
                                "claims_proj" -> {
                                    val employers = try {
                                        ProjectStore.employerProfiles(context, "").filter {
                                            !it.employer.contains("تونل") && it.totalClaims > 0.01
                                        }
                                    } catch (_: Exception) { emptyList() }
                                    Text("جمع مطالبات پروژه: ${moneyM(employers.sumOf { it.totalClaims })}", color = WorkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    employers.take(30).forEach { emp ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(emp.employer, color = appearanceTextPrimary(), fontSize = 12.sp, modifier = Modifier.weight(1f))
                                            Text(moneyM(emp.totalClaims), color = WorkPrimary, fontSize = 12.sp)
                                            IconButton(onClick = { callPhone(emp.phone) }) {
                                                Icon(Icons.Filled.Phone, null, tint = WorkPrimary, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = { smsClaims(emp.phone, emp.totalClaims) }) {
                                                Icon(Icons.Filled.Sms, null, tint = WorkPrimary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                                "claims_tunnel" -> {
                                    val sum = try { TunnelFinanceStore.summary(context) } catch (_: Exception) { null }
                                    if (sum != null) {
                                        Text("خلاصه مالی تونل", color = WorkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("جمع صورت‌وضعیت: ${moneyM(sum.sumPayable)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                        Text("جمع دریافتی: ${moneyM(sum.sumReceived)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                        Text("مانده مطالبات: ${moneyM(sum.remaining)}", color = WorkPrimary, fontSize = 12.sp)
                                        Text(
                                            "جزئیات بیشتر →",
                                            color = WorkPrimary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.clickable { onNavigate(Routes.FIN_TUNNEL_SUMMARY) }
                                        )
                                    } else {
                                        Text("داده مالی تونل نیست", color = appearanceTextMuted(), fontSize = 12.sp)
                                    }
                                }
                                "fall" -> allTunnelEntries.filter { it.collapse.isNotBlank() }.take(25).forEach { e ->
                                    Text("${e.year}/${e.month}/${e.day} شفت ${e.shaft} · ${e.collapse}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                }
                                "dev" -> allTunnelEntries.filter { it.deviation.isNotBlank() }.take(25).forEach { e ->
                                    Text("${e.year}/${e.month}/${e.day} شفت ${e.shaft} · ${e.deviation}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                }
                                "note_ex" -> allTunnelEntries.filter { it.notes.isNotBlank() }.take(25).forEach { e ->
                                    Text("${e.year}/${e.month}/${e.day} · ${e.notes}", color = appearanceTextPrimary(), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ---- منوی بخش (خالی بودن جستجو) مثل گیت‌هاب ----
            if (searchQuery.trim().isEmpty()) {
                Text(
                    section.title,
                    color = appearanceTextSecondary(),
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
                                color = if (selected) {
                                    val base = section.color
                                    if (AppearanceStore.modeState == AppearanceMode.Iran)
                                        base.copy(alpha = 0.48f) // حدود ۲۰٪ تیره‌تر از حالت قبل
                                    else
                                        base.copy(alpha = 0.28f)
                                } else appearanceControlBg(),
                                border = if (!selected) BorderStroke(0.5.dp, BorderColor) else null
                            ) {
                                Text(
                                    tab.title,
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
                                        .fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    color = if (selected) section.color else appearanceTextSecondary(),
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                MenuGrid(
                    items = currentTab.items,
                    accent = section.color,
                    onNavigate = onNavigate
                )
            }
        } // end scroll Column

        locEditProject?.let { pe ->
            ProjectLocationPickerDialog(
                initialLat = pe.lat,
                initialLon = pe.lon,
                initialLink = pe.neshanLink,
                onConfirm = { la, lo, link ->
                    ProjectStore.updateLocationByProjectName(context, pe.name, la, lo, link)
                    locEditProject = null
                },
                onDismiss = { locEditProject = null }
            )
        }

        // ---- نوار پایین: نقشه‌برداری / نقشه / ابزار / مالی ----
        Surface(color = SurfaceHigh, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // index mapping: 0=survey in sections? AppMenu.sections = finance, survey, tools
                val surveyIdx = sections.indexOfFirst { it.title == "نقشه‌برداری" }.let { if (it < 0) 1 else it }
                val toolsIdx = sections.indexOfFirst { it.title == "ابزار" }.let { if (it < 0) 2 else it }
                val financeIdx = sections.indexOfFirst { it.title == "مالی" }.let { if (it < 0) 0 else it }

                // نقشه‌برداری
                Column(
                    modifier = Modifier.weight(1f).clickable {
                        sectionIndex = surveyIdx
                        tabIndex = 0
                        searchQuery = ""
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val sel = sectionIndex == surveyIdx
                    Icon(Icons.Filled.Architecture, "نقشه‌برداری", tint = if (sel) sections[surveyIdx].color else appearanceTextMuted())
                    Spacer(Modifier.height(4.dp))
                    Text("نقشه‌برداری", fontSize = 11.sp, color = if (sel) sections[surveyIdx].color else appearanceTextMuted())
                }
                // نقشه (نقشه ۲)
                Column(
                    modifier = Modifier.weight(1f).clickable { onNavigate(Routes.TOOL_MAP2) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Layers, "نقشه", tint = ToolPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("نقشه", fontSize = 11.sp, color = ToolPrimary)
                }
                // ابزار
                Column(
                    modifier = Modifier.weight(1f).clickable {
                        sectionIndex = toolsIdx
                        tabIndex = 0
                        searchQuery = ""
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val sel = sectionIndex == toolsIdx
                    Icon(Icons.Filled.Build, "ابزار", tint = if (sel) sections[toolsIdx].color else appearanceTextMuted())
                    Spacer(Modifier.height(4.dp))
                    Text("ابزار", fontSize = 11.sp, color = if (sel) sections[toolsIdx].color else appearanceTextMuted())
                }
                // مالی
                Column(
                    modifier = Modifier.weight(1f).clickable {
                        sectionIndex = financeIdx
                        tabIndex = 0
                        searchQuery = ""
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val sel = sectionIndex == financeIdx
                    Icon(Icons.Filled.AttachMoney, "مالی", tint = if (sel) sections[financeIdx].color else appearanceTextMuted())
                    Spacer(Modifier.height(4.dp))
                    Text("مالی", fontSize = 11.sp, color = if (sel) sections[financeIdx].color else appearanceTextMuted())
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
                        color = appearanceCardColor()
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
                                color = appearanceTextPrimary(),
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
        color = appearanceCardColor(),
        border = BorderStroke(0.5.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = appearanceTextPrimary(),
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
    val yi = p.year.toIntOrNullFa() ?: 0
    val mi = p.month.toIntOrNullFa() ?: 0
    val di = p.day.toIntOrNullFa() ?: 0
    val wd = if (yi > 1300 && mi in 1..12 && di in 1..31)
        CalendarStore.weekdayNameJalali(yi, mi, di) else ""
    val dateStr = if (yi > 0)
        String.format(Locale.US, "%d/%d/%d", yi, mi, di) else "—"
    val hh = (p.hour.toIntOrNullFa() ?: 9).toString().padStart(2, '0')
    val mm = (p.minute.toIntOrNullFa() ?: 0).toString().padStart(2, '0')
    return if (wd.isNotBlank()) "$wd $dateStr - $hh:$mm" else "$dateStr - $hh:$mm"
}
