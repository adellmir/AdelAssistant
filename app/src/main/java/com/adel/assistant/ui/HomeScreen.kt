package com.adel.assistant.ui

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.material.icons.filled.Navigation

import androidx.compose.material.icons.filled.Sms

import androidx.compose.material.icons.filled.Phone

import com.adel.assistant.ui.PendingClientsNav

import com.adel.assistant.data.TunnelReportStore

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
import androidx.compose.material3.OutlinedTextField
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
    LaunchedEffect(Unit) {
        weatherLoading = true
        weatherSnap = WeatherStore.fetch(context, force = true)
        weatherLoading = false
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
                                color = TextPrimary,
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
                                        color = TextSecondary
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
                                        else -> TextSecondary
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
                                        color = TextSecondary
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
                color = SurfaceColor,
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
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "پروژه ${projects.size} | تسک ${projectTasks.size + tunnelTasks.size}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Icon(
                            imageVector = if (planOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    if (planOpen) {
                        Spacer(modifier = Modifier.height(10.dp))

                        Text("پروژه پیش‌رو", color = WorkPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (projects.isEmpty()) {
                            Text("پروژه‌ای از امروز به بعد ثبت نشده", color = TextMuted, style = MaterialTheme.typography.bodySmall)
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
                                            p.name.ifBlank { "بدون نام" },
                                            color = if (p.hasLocation) WorkPrimary else TextPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(formatProjectDate(p), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
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
                            Text("تسک باز ندارد", color = TextMuted, style = MaterialTheme.typography.bodySmall)
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
                                        Text(task.title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                            Text("تسک باز ندارد", color = TextMuted, style = MaterialTheme.typography.bodySmall)
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
                                        Text(task.title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                color = SurfaceColor,
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
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("جستجو: شفت، پیشرفت، مطالبات، پروژه…", color = TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                        leadingIcon = {
                            Icon(Icons.Filled.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp))
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
                    val body = "با سلام جهت پرداخت هزینه نقشه‌برداری به‌مبلغ ${moneyM(amount)}. ممنون می‌شوم پس از پرداخت اطلاع‌رسانی بفرمایید.\nکارت: 5859831142797561\nشبا: 430180000000242375213376"
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phone")).apply {
                            putExtra("sms_body", body)
                        })
                    } catch (_: Exception) { }
                }

                // تشخیص نوع کوئری
                val isShaft = qLower.contains("شفت") || Regex("""(?i)^sh\s*\d*$""").matches(qLower) || qLower.startsWith("sh ")
                val isWell = qLower.contains("چاه") || Regex("""(?i)^ch\s*\d*$""").matches(qLower) || qLower.startsWith("ch ")
                val isSump = qLower.contains("سامپ") || qLower.contains("sump")
                val isProgress = qLower.contains("پیشرفت")
                val isClaims = qLower.contains("مطالبات") || qLower.contains("طلب")

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
                            Text("نقطه‌ای با این کد یافت نشد", color = TextMuted, fontSize = 12.sp)
                        } else {
                            filtered.take(30).forEach { p ->
                                val ctx = remember(p.pointNo, p.km) { TunnelReportStore.kmContext(context, p.km) }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SurfaceHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("نقطه ${p.pointNo} · ${p.type}", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                        Text(
                                            String.format(java.util.Locale.US, "کیلومتر: %.3f", p.km),
                                            color = TextSecondary, fontSize = 12.sp
                                        )
                                        val ps = ctx.prevShaft
                                        val ns = ctx.nextShaft
                                        Text(
                                            "شفت قبل: ${ps?.label ?: "—"} ${ps?.let { String.format(java.util.Locale.US, "%.1f m", it.distanceM) } ?: ""}",
                                            color = TextSecondary, fontSize = 11.sp
                                        )
                                        Text(
                                            "شفت بعد: ${ns?.label ?: "—"} ${ns?.let { String.format(java.util.Locale.US, "%.1f m", it.distanceM) } ?: ""}",
                                            color = TextSecondary, fontSize = 11.sp
                                        )
                                        val face = ctx.prevReport ?: ctx.nextReport
                                        Text(
                                            "سینه کار: ${face?.let { String.format(java.util.Locale.US, "%.1f m", it.distanceM) } ?: "—"}",
                                            color = TextSecondary, fontSize = 11.sp
                                        )
                                        if (p.elevDiff.isNotBlank()) {
                                            Text("اختلاف تراز: ${p.elevDiff}", color = TextSecondary, fontSize = 11.sp)
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
                            Text("گزارشی ثبت نشده", color = TextMuted, fontSize = 12.sp)
                        } else {
                            entries.forEach { e ->
                                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(
                                            "${e.dateLabel} · شفت${e.shaft}/${e.side}",
                                            fontWeight = FontWeight.Medium, color = TextPrimary, fontSize = 13.sp
                                        )
                                        Text(
                                            String.format(
                                                java.util.Locale.US,
                                                "km %.3f · روزانه %.1f · شفت %.1f · مانده %.1f",
                                                e.km, e.dailyProgress, e.shaftProgress, e.remaining
                                            ),
                                            color = TextSecondary, fontSize = 11.sp
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
                                            color = TextPrimary,
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
                                                color = TextSecondary,
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
                            Text("کارفرمایان", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            emps.take(15).forEach { emp ->
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
                                                fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp
                                            )
                                            Text(
                                                "${moneyM(emp.sessions.sumOf { it.amount })} / ${moneyM(emp.totalClaims)}",
                                                color = TextSecondary, fontSize = 11.sp
                                            )
                                        }
                                        emp.sessions.take(8).forEach { s ->
                                            Text(
                                                "· ${s.name}  ${moneyM(s.amount)} / ${moneyM(s.remaining)}",
                                                color = TextMuted, fontSize = 11.sp,
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
                            Text("پروژه‌ها", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            projs.take(15).forEach { pr ->
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
                                                fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp
                                            )
                                            Text(
                                                "${moneyM(pr.sessions.sumOf { it.amount })} / ${moneyM(pr.totalClaims)}",
                                                color = TextSecondary, fontSize = 11.sp
                                            )
                                        }
                                        Text(pr.employer, color = TextMuted, fontSize = 11.sp)
                                        pr.sessions.take(12).forEach { s ->
                                            val settled = if (s.remaining <= 0.01) "تسویه" else "مانده ${moneyM(s.remaining)}"
                                            Text(
                                                "${s.year}/${s.month}/${s.day} — $settled",
                                                color = TextSecondary, fontSize = 11.sp,
                                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // برنامه‌های منو
                    if (filteredItems.isNotEmpty()) {
                        Text(
                            "برنامه‌ها (${filteredItems.size})",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                        MenuGrid(items = filteredItems, accent = WorkPrimary, onNavigate = onNavigate)
                    }
                }
            }


            if (searchQuery.trim().isEmpty()) {
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

            }

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
                    Icon(Icons.Filled.Architecture, "نقشه‌برداری", tint = if (sel) sections[surveyIdx].color else TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Text("نقشه‌برداری", fontSize = 11.sp, color = if (sel) sections[surveyIdx].color else TextMuted)
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
                    Icon(Icons.Filled.Build, "ابزار", tint = if (sel) sections[toolsIdx].color else TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Text("ابزار", fontSize = 11.sp, color = if (sel) sections[toolsIdx].color else TextMuted)
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
                    Icon(Icons.Filled.AttachMoney, "مالی", tint = if (sel) sections[financeIdx].color else TextMuted)
                    Spacer(Modifier.height(4.dp))
                    Text("مالی", fontSize = 11.sp, color = if (sel) sections[financeIdx].color else TextMuted)
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
    val hh = (p.hour.toIntOrNullFa() ?: 9).toString().padStart(2, '0')
    val mm = (p.minute.toIntOrNullFa() ?: 0).toString().padStart(2, '0')
    return "$y/$m/$d $hh:$mm"
}
