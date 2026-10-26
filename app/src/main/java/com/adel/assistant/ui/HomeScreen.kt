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
    var sectionIndex by rememberSaveable { mutableStateOf(1) }
    var tabIndex by rememberSaveable { mutableStateOf(0) }

    var clock by remember { mutableStateOf(currentTimeString()) }
    var weatherSnap by remember { mutableStateOf(WeatherStore.cached()) }
    var weatherLoading by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // هر بار ورود به صفحه اصلی — رفرش فوری هواشناسی
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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
                    // خالی: اگر بخش نقشه‌برداری و تب تونل — پیش‌فرض تونل؛ وگرنه آیتم‌های بخش فعلی
                    val sec = sections.getOrElse(sectionIndex) { sections.first() }
                    val tabs = sec.tabs
                    val ti = tabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
                    if (sec.title == "نقشه‌برداری" && (tabs.getOrNull(ti)?.title == "تونل" || tabs.size <= 1)) {
                        tunnelDefaultItems.ifEmpty { tabs.getOrNull(ti)?.items.orEmpty() }
                    } else {
                        tabs.getOrNull(ti)?.items.orEmpty()
                    }
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
                    // سمت چپ: دستیار
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
                            focusedTextColor = appearanceTextPrimary(),
                            unfocusedTextColor = appearanceTextPrimary()
                        ),
                        textStyle = TextStyle(fontSize = 14.sp, color = appearanceTextPrimary()),
                        leadingIcon = {
                            Icon(Icons.Filled.Search, null, tint = appearanceTextMuted(), modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
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


            // ---- نتایج جستجوی هوشمند ----
            if (query.isNotEmpty()) {
                val qLower = query.lowercase().trim()
                val qNorm = qLower.replace("ی", "ي").replace("ک", "ك")

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
                    // مبلغ در سیستم میلیون تومان است — مثل صفحه مطالبات
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
                        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                            putExtra("sms_body", body)
                            putExtra(Intent.EXTRA_TEXT, body)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phone?body=" + Uri.encode(body)))
                            )
                        } catch (_: Exception) { }
                    }
                }

                // تشخیص نوع کوئری
                val isShaft = qLower.contains("شفت") || Regex("""(?i)^sh\s*\d*$""").matches(qLower) || qLower.startsWith("sh ")
                val isWell = qLower.contains("چاه") || Regex("""(?i)^ch\s*\d*$""").matches(qLower) || qLower.startsWith("ch ")
                val isSump = qLower.contains("سامپ") || qLower.contains("sump")
                val isProgress = qLower.contains("پیشرفت")
                val isClaims = qLower.contains("مطالبات") || qLower.contains("طلب")
                val isFallQ = qLower.contains("ریزش")
                val isDevQ = qLower.contains("انحراف")
                val isNoteExcav = qLower.contains("توضیحات حفاری") || (qLower.contains("توضیح") && qLower.contains("حفار"))
                val isNoteProj = qLower.contains("توضیحات پروژه") || (qLower.contains("توضیح") && qLower.contains("پروژه"))
                val isNoteBoth = qLower == "توضیحات" || qLower == "توضیح"
                val isNoteAny = isNoteExcav || isNoteProj || isNoteBoth || qLower.contains("توضیح")

                // —— جمع‌آوری نتایج از همه منابع (برای سربرگ‌های چندمنبعی) ——
                val fallHits = remember(query) {
                    val all = try {
                        TunnelReportStore.allEntries(context).filter { it.collapse.isNotBlank() }
                    } catch (_: Exception) { emptyList() }
                    when {
                        qLower.contains("ریزش") -> all.sortedByDescending { it.dateSortKey }
                        query.length >= 2 -> all.filter {
                            it.collapse.contains(query, ignoreCase = true) ||
                                it.pointNo.contains(query) ||
                                it.notes.contains(query, ignoreCase = true)
                        }.sortedByDescending { it.dateSortKey }
                        else -> emptyList()
                    }
                }
                val devHits = remember(query) {
                    val all = try {
                        TunnelReportStore.allEntries(context).filter { it.deviation.isNotBlank() }
                    } catch (_: Exception) { emptyList() }
                    when {
                        qLower.contains("انحراف") -> all.sortedByDescending { it.dateSortKey }
                        query.length >= 2 -> all.filter {
                            it.deviation.contains(query, ignoreCase = true) ||
                                it.pointNo.contains(query)
                        }.sortedByDescending { it.dateSortKey }
                        else -> emptyList()
                    }
                }
                val noteExHits = remember(query) {
                    val all = try {
                        TunnelReportStore.allEntries(context).filter { it.notes.isNotBlank() }
                    } catch (_: Exception) { emptyList() }
                    when {
                        isNoteExcav || isNoteBoth || qLower == "توضیحات" || qLower == "توضیح" ->
                            all.sortedByDescending { it.dateSortKey }
                        qLower.contains("توضیح") || query.length >= 2 ->
                            all.filter { it.notes.contains(query, ignoreCase = true) }
                                .sortedByDescending { it.dateSortKey }
                        else -> emptyList()
                    }
                }
                val notePrHits = remember(query) {
                    val all = try {
                        ProjectStore.all(context).filter { it.description.isNotBlank() }
                    } catch (_: Exception) { emptyList() }
                    when {
                        isNoteProj || isNoteBoth || qLower == "توضیحات" || qLower == "توضیح" ->
                            all.sortedWith(
                                compareBy<com.adel.assistant.data.ProjectEntry> { it.name }
                                    .thenByDescending { it.dateSortKey }
                            )
                        qLower.contains("توضیح") || query.length >= 2 ->
                            all.filter {
                                it.description.contains(query, ignoreCase = true) ||
                                    it.name.contains(query, ignoreCase = true)
                            }.sortedWith(
                                compareBy<com.adel.assistant.data.ProjectEntry> { it.name }
                                    .thenByDescending { it.dateSortKey }
                            )
                        else -> emptyList()
                    }
                }
                val empHits = remember(query) {
                    if (query.length < 2) emptyList()
                    else try {
                        ProjectStore.employerProfiles(context, query)
                    } catch (_: Exception) { emptyList() }
                }
                val projHits = remember(query) {
                    if (query.length < 2) emptyList()
                    else try {
                        ProjectStore.projectProfiles(context, query)
                    } catch (_: Exception) { emptyList() }
                }
                val pureNumQ = query.toEnglishDigits().trim()
                val isPureNumberQ = pureNumQ.matches(Regex("""^\d+(\.\d+)?$"""))
                val pointHits = remember(query) {
                    if (!isPureNumberQ) emptyList()
                    else try {
                        TunnelReportStore.allPoints(context).filter {
                            it.pointNo == pureNumQ || it.pointNo.contains(pureNumQ)
                        }
                    } catch (_: Exception) { emptyList() }
                }
                val hasSpecial =
                    isShaft || isWell || isSump || isProgress || isClaims ||
                    isFallQ || isDevQ || isNoteAny || isPureNumberQ ||
                    qLower.contains("مرداد") || qLower.contains("فروردین") ||
                    qLower.matches(Regex(""".*\d{1,2}\s*/\s*\d{1,2}.*""")) ||
                    (qLower.any { it.isDigit() } && query.length >= 2)

                // سربرگ‌ها: فقط منابعی که واقعاً نتیجه دارند — اول برنامه‌ها
                data class SearchTab(val id: String, val title: String, val count: Int)
                val searchTabs = buildList {
                    if (filteredItems.isNotEmpty()) add(SearchTab("apps", "برنامه‌ها", filteredItems.size))
                    if (empHits.isNotEmpty()) add(SearchTab("emp", "کارفرما", empHits.size))
                    if (projHits.isNotEmpty()) add(SearchTab("proj", "پروژه", projHits.size))
                    if (pointHits.isNotEmpty()) add(SearchTab("points", "نقاط", pointHits.size))
                    if (fallHits.isNotEmpty()) add(SearchTab("fall", "ریزش", fallHits.size))
                    if (devHits.isNotEmpty()) add(SearchTab("dev", "انحراف", devHits.size))
                    if (noteExHits.isNotEmpty()) add(SearchTab("note_ex", "توضیحات حفاری", noteExHits.size))
                    if (notePrHits.isNotEmpty()) add(SearchTab("note_pr", "توضیحات پروژه", notePrHits.size))
                    if (hasSpecial && (isShaft || isWell || isSump || isProgress || isClaims ||
                            isPureNumberQ || qLower.contains("ماه") ||
                            Regex("""\d""").containsMatchIn(qLower))
                    ) {
                        add(SearchTab("more", "سایر", 1))
                    }
                    if (isEmpty() && filteredItems.isNotEmpty()) {
                        add(SearchTab("apps", "برنامه‌ها", filteredItems.size))
                    }
                    if (isEmpty()) add(SearchTab("more", "نتایج", 0))
                }

                var searchTabId by remember(query) {
                    mutableStateOf(searchTabs.firstOrNull()?.id ?: "apps")
                }
                LaunchedEffect(query, searchTabs.joinToString { it.id }) {
                    if (searchTabs.none { it.id == searchTabId }) {
                        searchTabId = searchTabs.firstOrNull()?.id ?: "apps"
                    }
                }

                // نوار سربرگ منابع (وقتی بیش از یک منبع باشد واضح‌تر است)
                if (searchTabs.isNotEmpty()) {
                    Text(
                        if (searchTabs.size > 1) "منابع پاسخ — یک سربرگ را انتخاب کنید"
                        else "نتایج",
                        color = appearanceTextSecondary(),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .horizontalScroll(rememberScrollState()),
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
                                    if (tab.count > 0) "${tab.title} (${tab.count})" else tab.title,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (sel) WorkPrimary else appearanceTextPrimary(),
                                    fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                                // —— محتوای سربرگ فعال ——
                when (searchTabId) {
                    "fall" -> {
                        if (fallHits.isEmpty()) {
                            Text("موردی برای ریزش نیست", color = appearanceTextMuted(), fontSize = 12.sp)
                        } else {
                            fallHits.take(50).forEach { e ->
                                val slots = TunnelReportStore.parseSlotValues(e.collapse)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = appearanceCardColor(),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(
                                            "${e.year}/${e.month}/${e.day}  |  km ${"%.3f".format(e.km)}",
                                            color = appearanceTextPrimary(), fontSize = 12.sp
                                        )
                                        Text(
                                            "شفت ${e.shaft} → ${sideDisplayName(e.side)}  |  نقطه ${e.pointNo}",
                                            color = appearanceTextSecondary(), fontSize = 11.sp
                                        )
                                        if (slots.isEmpty()) {
                                            Text("ریزش: ${e.collapse}", color = Color(0xFFE57373), fontSize = 11.sp)
                                        } else {
                                            slots.forEach { (slot, value) ->
                                                Text("فیلد $slot : $value", color = Color(0xFFE57373), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "dev" -> {
                        if (devHits.isEmpty()) {
                            Text("موردی برای انحراف نیست", color = appearanceTextMuted(), fontSize = 12.sp)
                        } else {
                            devHits.take(50).forEach { e ->
                                val slots = TunnelReportStore.parseSlotValues(e.deviation)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = appearanceCardColor(),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(
                                            "${e.year}/${e.month}/${e.day}  |  km ${"%.3f".format(e.km)}",
                                            color = appearanceTextPrimary(), fontSize = 12.sp
                                        )
                                        Text(
                                            "شفت ${e.shaft} → ${sideDisplayName(e.side)}  |  نقطه ${e.pointNo}",
                                            color = appearanceTextSecondary(), fontSize = 11.sp
                                        )
                                        if (slots.isEmpty()) {
                                            Text("انحراف: ${e.deviation}", color = Color(0xFFFFB74D), fontSize = 11.sp)
                                        } else {
                                            slots.forEach { (slot, value) ->
                                                Text("فیلد $slot : $value", color = Color(0xFFFFB74D), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "note_ex" -> {
                        if (noteExHits.isEmpty()) {
                            Text("توضیحات حفاری نیست", color = appearanceTextMuted(), fontSize = 12.sp)
                        } else {
                            noteExHits.take(50).forEach { e ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = appearanceCardColor(),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(
                                            "${e.year}/${e.month}/${e.day}  |  km ${"%.3f".format(e.km)}",
                                            color = appearanceTextPrimary(), fontSize = 12.sp
                                        )
                                        Text(
                                            "شفت ${e.shaft} → ${sideDisplayName(e.side)}  |  نقطه ${e.pointNo}",
                                            color = appearanceTextSecondary(), fontSize = 11.sp
                                        )
                                        Text(e.notes, color = Color(0xFF81C784), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    "note_pr" -> {
                        if (notePrHits.isEmpty()) {
                            Text("توضیحات پروژه نیست", color = appearanceTextMuted(), fontSize = 12.sp)
                        } else {
                            notePrHits.take(50).forEach { e ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = appearanceCardColor(),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(e.name, color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("${e.year}/${e.month}/${e.day}", color = appearanceTextSecondary(), fontSize = 11.sp)
                                        Text(e.description, color = Color(0xFF81C784), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    "apps" -> {
                        if (filteredItems.isEmpty()) {
                            Text("برنامه‌ای یافت نشد", color = appearanceTextMuted(), fontSize = 12.sp)
                        } else {
                            MenuGrid(items = filteredItems, accent = WorkPrimary, onNavigate = onNavigate)
                        }
                    }
                    "emp" -> {
                        if (empHits.isEmpty()) {
                            Text("کارفرمایی یافت نشد", color = appearanceTextMuted(), fontSize = 12.sp)
                        } else {
                            empHits.take(40).forEach { ep ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = appearanceCardColor(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable {
                                            PendingClientsNav.openEmployer(ep.employer)
                                            onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                        }
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(ep.employer, color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            "${ep.projectCount} پروژه · مطالبات ${moneyM(ep.totalClaims)}",
                                            color = appearanceTextSecondary(), fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "proj" -> {
                        if (projHits.isEmpty()) {
                            Text("پروژه‌ای یافت نشد", color = appearanceTextMuted(), fontSize = 12.sp)
                        } else {
                            projHits.take(40).forEach { pr ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = appearanceCardColor(),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(
                                            pr.name,
                                            Modifier.clickable {
                                                PendingClientsNav.openProject(pr.name)
                                                onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                            },
                                            fontWeight = FontWeight.Bold,
                                            color = appearanceTextPrimary(),
                                            fontSize = 13.sp
                                        )
                                        Text(pr.employer, color = appearanceTextMuted(), fontSize = 11.sp)
                                        Text(
                                            "${moneyM(pr.sessions.sumOf { it.amount })} / ${moneyM(pr.totalClaims)}",
                                            color = appearanceTextSecondary(), fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "points" -> {
                        if (pointHits.isEmpty()) {
                            Text("نقطه‌ای یافت نشد", color = appearanceTextMuted(), fontSize = 12.sp)
                        } else {
                            pointHits.take(40).forEach { pt ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = appearanceCardColor(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable {
                                            PendingTunnelLookup.openPoint(pt.pointNo)
                                            onNavigate(Routes.SURVEY_TUNNEL_EVENTS)
                                        }
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(
                                            "نقطه ${pt.pointNo}  ·  km ${"%.3f".format(pt.km)}",
                                            color = appearanceTextPrimary(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            "X=${"%.3f".format(pt.x)}  Y=${"%.3f".format(pt.y)}  Z=${"%.3f".format(pt.z)}",
                                            color = appearanceTextSecondary(),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "more" -> {
                        // شفت، مطالبات، تاریخ و …
                // —— عدد خالص: شماره نقطه یا کیلومتر ——
                val pureNum = query.toEnglishDigits().trim()
                val isPureNumber = pureNum.matches(Regex("""^\d+(\.\d+)?$"""))
                if (isPureNumber) {
                    val pts = remember(query) { TunnelReportStore.allPoints(context) }
                    val asPoint = pts.filter { it.pointNo == pureNum || it.pointNo.contains(pureNum) }
                    val asKm = pureNum.toDoubleOrNull()
                    Text("شماره نقطه / کیلومتر", color = appearanceTextSecondary(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    // نقاط با این شماره
                    asPoint.forEach { p ->
                        val ctx = remember(p.pointNo) { TunnelReportStore.kmContext(context, p.km) }
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "نقطه ${p.pointNo} / km ${String.format(java.util.Locale.US, "%.3f", p.km)}",
                                    color = WorkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable {
                                        PendingTunnelLookup.openPoint(p.pointNo)
                                        onNavigate(Routes.SURVEY_TUNNEL_EVENTS)
                                    }
                                )
                                Text("کد: ${p.type}", color = appearanceTextSecondary(), fontSize = 12.sp)
                                Text(
                                    String.format(java.util.Locale.US, "X=%.3f Y=%.3f Z=%.3f", p.x, p.y, p.z),
                                    color = appearanceTextMuted(), fontSize = 11.sp
                                )
                                if (p.elevDiff.isNotBlank()) Text("اختلاف تراز: ${p.elevDiff}", color = appearanceTextSecondary(), fontSize = 11.sp)
                                val ps = ctx.prevShaft; val ns = ctx.nextShaft
                                Text("شفت‌ها: ${ps?.label ?: "—"} ${ps?.let { String.format(java.util.Locale.US, "%.1fm", it.distanceM) } ?: ""} | ${ns?.label ?: "—"} ${ns?.let { String.format(java.util.Locale.US, "%.1fm", it.distanceM) } ?: ""}", color = appearanceTextSecondary(), fontSize = 11.sp)
                                val face = ctx.prevReport ?: ctx.nextReport
                                Text("سینه کار: ${face?.let { String.format(java.util.Locale.US, "%.1f m", it.distanceM) } ?: "—"}", color = appearanceTextSecondary(), fontSize = 11.sp)
                            }
                        }
                    }
                    // نزدیک‌ترین کیلومتر
                    if (asKm != null && pts.isNotEmpty()) {
                        val near = pts.minByOrNull { kotlin.math.abs(it.km - asKm) }!!
                        val ctx = remember(near.pointNo, asKm) { TunnelReportStore.kmContext(context, asKm) }
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    String.format(java.util.Locale.US, "کیلومتر ≈ %.3f / نقطه %s", near.km, near.pointNo),
                                    color = WorkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable {
                                        PendingTunnelLookup.openKm(asKm)
                                        onNavigate(Routes.SURVEY_TUNNEL_EVENTS)
                                    }
                                )
                                Text("نزدیک‌ترین نقطه: ${near.pointNo} (${near.type})", color = appearanceTextSecondary(), fontSize = 12.sp)
                                val ps = ctx.prevShaft; val ns = ctx.nextShaft
                                Text("شفت قبل: ${ps?.label ?: "—"} ${ps?.let { String.format(java.util.Locale.US, "%.1f m", it.distanceM) } ?: ""}", color = appearanceTextSecondary(), fontSize = 11.sp)
                                Text("شفت بعد: ${ns?.label ?: "—"} ${ns?.let { String.format(java.util.Locale.US, "%.1f m", it.distanceM) } ?: ""}", color = appearanceTextSecondary(), fontSize = 11.sp)
                            }
                        }
                    }
                    if (asPoint.isEmpty() && (asKm == null || pts.isEmpty())) {
                        Text("نقطه یا کیلومتری یافت نشد", color = appearanceTextMuted(), fontSize = 12.sp)
                    }
                }

                // —— تاریخ و ماه ——
                val (pDay, pMonth, pYear) = parseDayMonthYear(query)
                val (cy, cm, cd) = CalendarStore.todayJalali()
                if (pMonth != null) {
                    val yearsToShow = if (pYear != null) listOf(pYear) else {
                        // سال جاری اول، بعد سال قبل اگر داده داشته باشد
                        listOf(cy, cy - 1)
                    }
                    // روز مشخص
                    if (pDay != null) {
                        yearsToShow.forEach { y ->
                            val wd = CalendarStore.weekdayNameJalali(y, pMonth, pDay)
                            Text(
                                "$wd ${String.format(java.util.Locale.US, "%04d/%02d/%02d", y, pMonth, pDay)}",
                                color = appearanceTextSecondary(), fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                            // پروژه‌های آن روز
                            val dayProjects = remember(query, y) {
                                ProjectStore.all(context).filter {
                                    (it.year.toEnglishDigits().toIntOrNull() ?: 0).let { yy -> if (yy in 0..99) yy + 1400 else yy } == y &&
                                        (it.month.toIntOrNull() ?: 0) == pMonth &&
                                        (it.day.toIntOrNull() ?: 0) == pDay
                                }
                            }
                            Text("پروژه‌ها", color = WorkPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (dayProjects.isEmpty()) {
                                Text("پروژه‌ای در این تاریخ نیست", color = appearanceTextMuted(), fontSize = 11.sp)
                            } else {
                                dayProjects.forEach { s ->
                                    val ok = s.remaining <= 0.01
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                PendingClientsNav.openProject(s.name)
                                                onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${s.name} / ${s.employer} / ${moneyM(s.amount)}", Modifier.weight(1f), color = appearanceTextPrimary(), fontSize = 12.sp)
                                        Text(if (ok) "✓" else "✕", color = if (ok) Color(0xFF43A047) else Color(0xFFE53935), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            // گزارش حفاری
                            val reports = remember(query, y) {
                                TunnelReportStore.allEntries(context).filter {
                                    TunnelReportStore.sameDate(it, y.toString(), pMonth.toString(), pDay.toString())
                                }
                            }
                            Text(
                                "گزارش حفاری",
                                color = WorkPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    PendingDailyReport.open(y, pMonth, pDay)
                                    onNavigate(Routes.SURVEY_TUNNEL_REPORT)
                                }
                            )
                            if (reports.isEmpty()) {
                                Text("گزارشی در این تاریخ نیست", color = appearanceTextMuted(), fontSize = 11.sp)
                            } else {
                                reports.forEach { e ->
                                    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceHigh, modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            PendingDailyReport.open(y, pMonth, pDay)
                                            onNavigate(Routes.SURVEY_TUNNEL_REPORT)
                                        }) {
                                        Column(Modifier.padding(8.dp)) {
                                            Text(
                                                "${e.dateLabel} · شفت${e.shaft}/${e.side} · نقطه ${e.pointNo}",
                                                fontWeight = FontWeight.Medium, color = appearanceTextPrimary(), fontSize = 12.sp
                                            )
                                            Text(
                                                String.format(
                                                    java.util.Locale.US,
                                                    "km %.3f · روزانه %.1f · شفت %.1f · مانده %.1f",
                                                    e.km, e.dailyProgress, e.shaftProgress, e.remaining
                                                ),
                                                color = appearanceTextSecondary(), fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // فقط ماه (± سال)
                        yearsToShow.forEach { y ->
                            val monthName = PERSIAN_MONTHS.getOrNull(pMonth - 1) ?: pMonth.toString()
                            Text(
                                "$monthName $y",
                                color = appearanceTextSecondary(), fontWeight = FontWeight.Bold, fontSize = 13.sp
                            )
                            // ۱ پروژه‌ها
                            val monthProjects = remember(query, y, pMonth) {
                                ProjectStore.all(context)
                                    .filter {
                                        (it.year.toEnglishDigits().toIntOrNull() ?: 0).let { yy -> if (yy in 0..99) yy + 1400 else yy } == y &&
                                            (it.month.toIntOrNull() ?: 0) == pMonth
                                    }
                                    .sortedByDescending { it.dateSortKey }
                            }
                            Text("۱. پروژه‌ها", color = WorkPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (monthProjects.isEmpty()) Text("—", color = appearanceTextMuted(), fontSize = 11.sp)
                            else monthProjects.forEach { s ->
                                val ok = s.remaining <= 0.01
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${s.name} / ${s.employer} / ${moneyM(s.amount)}",
                                        Modifier.weight(1f).clickable {
                                            PendingClientsNav.openProject(s.name)
                                            onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                        },
                                        color = appearanceTextPrimary(), fontSize = 12.sp
                                    )
                                    Text(if (ok) "✓" else "✕", color = if (ok) Color(0xFF43A047) else Color(0xFFE53935))
                                }
                            }
                            // ۲ وضعیت حفاری بازه‌ای آن ماه
                            Text("۲. وضعیت حفاری", color = WorkPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val monthReports = remember(query, y, pMonth) {
                                TunnelReportStore.allEntries(context).filter {
                                    (it.year.toIntOrNull() ?: 0).let { yy -> if (yy in 0..99) yy + 1400 else yy } == y &&
                                        (it.month.toIntOrNull() ?: 0) == pMonth
                                }
                            }
                            val byShaft = monthReports.groupBy { it.shaft }
                            if (byShaft.isEmpty()) Text("—", color = appearanceTextMuted(), fontSize = 11.sp)
                            else byShaft.forEach { (sh, list) ->
                                val last = list.maxByOrNull { it.dateSortKey }
                                Text(
                                    "شفت $sh: ${list.size} گزارش · آخرین km ${last?.let { String.format(java.util.Locale.US, "%.3f", it.km) } ?: "—"} · پیشرفت روزانه جمع ${String.format(java.util.Locale.US, "%.1f", list.sumOf { it.dailyProgress })}",
                                    color = appearanceTextSecondary(), fontSize = 11.sp,
                                    modifier = Modifier.clickable { onNavigate(Routes.SURVEY_TUNNEL_STATUS) }
                                )
                            }
                            // ۳ درآمد و دریافتی
                            Text("۳. درآمد و دریافتی", color = WorkPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val projIncome = monthProjects.sumOf { it.amount }
                            val projRecv = monthProjects.sumOf { it.settled }
                            Text("پروژه — درآمد: ${moneyM(projIncome)} / دریافتی: ${moneyM(projRecv)}", color = appearanceTextPrimary(), fontSize = 12.sp)
                            val tunRows = remember(query, y, pMonth) {
                                try {
                                    TunnelFinanceStore.all(context).filter { it.year == y && it.month == pMonth }
                                } catch (_: Exception) { emptyList() }
                            }
                            val tunIncome = tunRows.sumOf { it.income }
                            val tunRecv = tunRows.sumOf { it.receiveAmount ?: 0.0 }
                            Text(
                                "تونل — درآمد: ${moneyM(tunIncome)} / دریافتی: ${moneyM(tunRecv)}",
                                color = appearanceTextPrimary(), fontSize = 12.sp,
                                modifier = Modifier.clickable { onNavigate(Routes.FIN_TUNNEL_SUMMARY) }
                            )
                        }
                    }
                }


                                val typePrefix: String? = when {
                    isShaft -> "sh"
                    isWell -> "ch"
                    isSump -> "sump"
                    else -> null
                }
                val typeNum = Regex("""(?i)(?:sh|ch|sump|شفت|چاه|سامپ)\s*([0-9]+)""").find(qLower)?.groupValues?.getOrNull(1)

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // —— نقاط تونل: شفت / چاه / سامپ ——
                    if (typePrefix != null) {
                        val pts = remember(query) { TunnelReportStore.allPoints(context) }
                        val filtered = pts.filter { p ->
                            val t = p.type.trim().lowercase()
                            val okPrefix = when (typePrefix) {
                                "sh" -> t.startsWith("sh") || t.contains("shaft") || t == "start" || t == "end"
                                "ch" -> t.startsWith("ch") || t.contains("چاه") || t.contains("well")
                                "sump" -> t.contains("sump") || t.contains("سامپ")
                                else -> false
                            }
                            if (!okPrefix) return@filter false
                            if (typeNum != null) {
                                val n = Regex("""[0-9]+""").find(t)?.value
                                n == typeNum || p.pointNo.contains(typeNum)
                            } else true
                        }
                        Text(
                            "اطلاعات موقعیت",
                            color = WorkPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onNavigate(Routes.SURVEY_TUNNEL_EVENTS) }
                                .padding(vertical = 4.dp)
                        )
                        if (filtered.isEmpty()) {
                            Text("نقطه‌ای با این کد یافت نشد", color = appearanceTextMuted(), fontSize = 12.sp)
                        } else {
                            filtered.take(30).forEach { p ->
                                val ctx = remember(p.pointNo, p.km) { TunnelReportStore.kmContext(context, p.km) }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SurfaceHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("نقطه ${p.pointNo} · ${p.type}", fontWeight = FontWeight.Bold, color = appearanceTextPrimary(), fontSize = 13.sp)
                                        Text(
                                            String.format(java.util.Locale.US, "کیلومتر: %.3f", p.km),
                                            color = appearanceTextSecondary(), fontSize = 12.sp
                                        )
                                        val ps = ctx.prevShaft
                                        val ns = ctx.nextShaft
                                        Text(
                                            "شفت قبل: ${ps?.label ?: "—"} ${ps?.let { String.format(java.util.Locale.US, "%.1f m", it.distanceM) } ?: ""}",
                                            color = appearanceTextSecondary(), fontSize = 11.sp
                                        )
                                        Text(
                                            "شفت بعد: ${ns?.label ?: "—"} ${ns?.let { String.format(java.util.Locale.US, "%.1f m", it.distanceM) } ?: ""}",
                                            color = appearanceTextSecondary(), fontSize = 11.sp
                                        )
                                        val face = ctx.prevReport ?: ctx.nextReport
                                        Text(
                                            "سینه کار: ${face?.let { String.format(java.util.Locale.US, "%.1f m", it.distanceM) } ?: "—"}",
                                            color = appearanceTextSecondary(), fontSize = 11.sp
                                        )
                                        if (p.elevDiff.isNotBlank()) {
                                            Text("اختلاف تراز: ${p.elevDiff}", color = appearanceTextSecondary(), fontSize = 11.sp)
                                        }
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            IconButton(onClick = {
                                                // UTM → اگر lat/lon نداریم از x,y به‌عنوان نمایش
                                                try {
                                                    val uri = Uri.parse(
                                                        String.format(
                                                            java.util.Locale.US,
                                                            "https://nshn.ir/?lat=%.6f&lng=%.6f",
                                                            // تقریبی: اگر مختصات جغرافیایی نیست، باز هم لینک بساز با تبدیل ساده نمی‌کنیم — از PendingMapGoto
                                                            p.y, p.x
                                                        )
                                                    )
                                                    // مسیر بهتر: باز کردن نقشه تونل
                                                    onNavigate(Routes.SURVEY_TUNNEL_MAP)
                                                } catch (_: Exception) {
                                                    onNavigate(Routes.SURVEY_TUNNEL_MAP)
                                                }
                                            }) {
                                                Icon(Icons.Filled.Navigation, "مسیریاب", tint = WorkPrimary, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // —— پیشرفت تونل ——
                    if (isProgress) {
                        Text(
                            "وضعیت پیشرفت تونل",
                            color = WorkPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onNavigate(Routes.SURVEY_TUNNEL_STATUS) }
                                .padding(vertical = 4.dp)
                        )
                        val entries = remember(query) { TunnelReportStore.allEntries(context).takeLast(8).reversed() }
                        if (entries.isEmpty()) {
                            Text("گزارشی ثبت نشده", color = appearanceTextMuted(), fontSize = 12.sp)
                        } else {
                            entries.forEach { e ->
                                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(
                                            "${e.dateLabel} · شفت${e.shaft}/${e.side}",
                                            fontWeight = FontWeight.Medium, color = appearanceTextPrimary(), fontSize = 13.sp
                                        )
                                        Text(
                                            String.format(
                                                java.util.Locale.US,
                                                "km %.3f · روزانه %.1f · شفت %.1f · مانده %.1f",
                                                e.km, e.dailyProgress, e.shaftProgress, e.remaining
                                            ),
                                            color = appearanceTextSecondary(), fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // —— مطالبات ——
                    if (isClaims) {
                        val employers = remember(query) {
                            ProjectStore.employerProfiles(context, "").filter {
                                // بجز تونل
                                !it.employer.contains("تونل") && it.sessions.none { s -> s.name.contains("تونل") }
                            }
                        }
                        val total = employers.sumOf { it.totalClaims }
                        Text(
                            "مطالبات کارفرمایان — جمع کل: ${moneyM(total)}",
                            color = WorkPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onNavigate(Routes.FIN_PROJECT_RECEIVABLES) }
                                .padding(vertical = 4.dp)
                        )
                            employers.filter { it.totalClaims > 0.01 }.take(40).forEach { emp ->
                            Surface(shape = RoundedCornerShape(10.dp), color = SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            emp.employer,
                                            Modifier
                                                .weight(1f)
                                                .clickable { expandedEmp = if (expandedEmp == emp.employer) null else emp.employer },
                                            fontWeight = FontWeight.Bold,
                                            color = appearanceTextPrimary(),
                                            fontSize = 13.sp
                                        )
                                        Text(moneyM(emp.totalClaims), color = WorkPrimary, fontSize = 12.sp)
                                        IconButton(onClick = { callPhone(emp.phone) }) {
                                            Icon(Icons.Filled.Phone, null, tint = WorkPrimary, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { smsClaims(emp.phone, emp.totalClaims) }) {
                                            Icon(Icons.Filled.Sms, null, tint = WorkPrimary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    if (expandedEmp == emp.employer) {
                                        emp.sessions.forEach { s ->
                                            Text(
                                                "${s.name} · ${moneyM(s.remaining)}",
                                                color = appearanceTextSecondary(),
                                                fontSize = 12.sp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        PendingClientsNav.openProject(s.name)
                                                        onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                                    }
                                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // —— جستجوی کارفرما / پروژه (همیشه اگر کوئری متنی باشد) ——
                    if (!isProgress && !isClaims && typePrefix == null) {
                        val emps = remember(query) {
                            ProjectStore.employerProfiles(context, query)
                        }
                        val projs = remember(query) {
                            ProjectStore.projectProfiles(context, query)
                        }
                        if (emps.isNotEmpty()) {
                            Text("کارفرمایان", color = appearanceTextSecondary(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            emps.forEach { emp ->
                                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row {
                                            Text(
                                                emp.employer,
                                                Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        PendingClientsNav.openEmployer(emp.employer)
                                                        onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                                    },
                                                fontWeight = FontWeight.Bold, color = appearanceTextPrimary(), fontSize = 13.sp
                                            )
                                            Text(
                                                "${moneyM(emp.sessions.sumOf { it.amount })} / ${moneyM(emp.totalClaims)}",
                                                color = appearanceTextSecondary(), fontSize = 11.sp
                                            )
                                        }
                                        emp.sessions.forEach { s ->
                                            Text(
                                                "· ${s.name}  ${moneyM(s.amount)} / ${moneyM(s.remaining)}",
                                                color = appearanceTextMuted(), fontSize = 11.sp,
                                                modifier = Modifier
                                                    .clickable {
                                                        PendingClientsNav.openProject(s.name)
                                                        onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                                    }
                                                    .padding(start = 6.dp, top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (projs.isNotEmpty()) {
                            Text("پروژه‌ها", color = appearanceTextSecondary(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            projs.forEach { pr ->
                                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row {
                                            Text(
                                                pr.name,
                                                Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        PendingClientsNav.openProject(pr.name)
                                                        onNavigate(Routes.SURVEY_PROJECT_CLIENTS)
                                                    },
                                                fontWeight = FontWeight.Bold, color = appearanceTextPrimary(), fontSize = 13.sp
                                            )
                                            Text(
                                                "${moneyM(pr.sessions.sumOf { it.amount })} / ${moneyM(pr.totalClaims)}",
                                                color = appearanceTextSecondary(), fontSize = 11.sp
                                            )
                                        }
                                        Text(pr.employer, color = appearanceTextMuted(), fontSize = 11.sp)
                                        pr.sessions.take(12).forEach { s ->
                                            val settled = if (s.remaining <= 0.01) "تسویه" else "مانده ${moneyM(s.remaining)}"
                                            Text(
                                                "${s.year}/${s.month}/${s.day} — $settled",
                                                color = appearanceTextSecondary(), fontSize = 11.sp,
                                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }


                    } // end more/else tab
                } // end when searchTabId
            } // end if query.isNotEmpty

            if (searchQuery.trim().isEmpty()) {
            // ---- عنوان بخش ----
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
                            color = if (selected) section.color.copy(alpha = 0.28f) else appearanceControlBg(),
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

            // ---- منو بدون LazyVerticalGrid تو در تو (عامل کرش) ----
            MenuGrid(
                items = currentTab.items,
                accent = section.color,
                onNavigate = onNavigate
            )
            } // end if searchQuery empty
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
