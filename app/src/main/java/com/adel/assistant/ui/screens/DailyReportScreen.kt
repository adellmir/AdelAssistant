package com.adel.assistant.ui.screens

import com.adel.assistant.ui.components.AdelOutlinedTextField

import android.content.Intent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.ReportHub
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.CsvStore
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.ExportHub
import com.adel.assistant.data.ReportEntry
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.normalizeSide
import com.adel.assistant.data.WorkingDraftStore
import com.adel.assistant.data.XlsxReportWriter
import com.adel.assistant.data.filterNumericInput
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.data.toEnglishDigits
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.appearanceScreenBg
import com.adel.assistant.ui.appearanceCardColor
import com.adel.assistant.ui.theme.Surface as SurfaceColor

private val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

private data class PreviewRow(
    val shaft: String, val side: String, val pointNo: String, val lengthCm: Double,
    val km: Double, val dailyProgress: Double, val shaftProgress: Double, val remaining: Double,
    val deviation: String = "", val collapse: String = "", val notes: String = ""
)

/** سمت «کمتر»؟ — از منطق یکسان Store استفاده می‌شود */
private fun isTowardLess(shaft: String, side: String): Boolean =
    TunnelReportStore.isTowardLessSide(shaft, side)

@Composable
fun DailyReportScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current

    val today = remember { CalendarStore.todayJalali() }
    var day by remember { mutableStateOf(today.third.toString()) }
    var month by remember { mutableStateOf(today.second.toString()) }
    var year by remember { mutableStateOf(today.first.toString()) }

    var shaft by remember { mutableStateOf("") }
    var side by remember { mutableStateOf("") }
    var pointNo by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var deviation by remember { mutableStateOf("") }
    var collapse by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var slotNo by remember { mutableStateOf("4") }  // شماره فیلد ۱..۷ پیش‌فرض ۴
    var lastNotesByKey by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editingIndex by remember { mutableStateOf(-1) }

    var rows by remember { mutableStateOf(listOf<PreviewRow>()) }
    var showMenu by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    // فقط وقتی خود کاربر سمت را وارد کرد: شماره نقطه و طول را پیش‌بینی کن
    LaunchedEffect(shaft, side) {
        // سمت هرگز پیش‌فرض/پیش‌بینی نمی‌شود؛ فقط بعد از ورود سمت توسط کاربر، طول و شماره نقطه پیشنهاد می‌شود
        if (shaft.isNotBlank() && side.isNotBlank() && editingIndex < 0) {
            val suggested = TunnelReportStore.suggestedNextPointNo(context, shaft, side)
            if (suggested != null) {
                pointNo = suggested.toString()
            }
            val last = TunnelReportStore.allEntries(context)
                .filter { it.key == "$shaft-${com.adel.assistant.data.normalizeSide(side)}" }
                .maxByOrNull { it.dateSortKey }
            if (last != null && length.isBlank()) {
                val lastLen = last.lengthCm
                length = if (kotlin.math.abs(lastLen - lastLen.toLong()) < 1e-9)
                    lastLen.toLong().toString() else lastLen.toString()
            }
        }
    }

    val weekday = remember(day, month) { CalendarStore.weekdayFor(context, day, month) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = input.bufferedReader().readText()
                    CsvStore.importRawText(context, "survey_tunnel_report", text)
                    statusMsg = "فایل گزارش‌ها با موفقیت وارد شد"
                }
            } catch (e: Exception) {
                statusMsg = "خطا در وارد کردن فایل"
            }
        }
    }

    fun todayDateKey() = TunnelReportStore.dateKey(year, month, day)

    fun loadDay() {
        // نرمال‌سازی ارقام فارسی و سال ۲رقمی قبل از جستجو
        day = day.toEnglishDigits().trim()
        month = month.toEnglishDigits().trim()
        year = year.toEnglishDigits().trim().let { y ->
            val yi = y.toIntOrNullFa() ?: 0
            if (yi in 0..99) (1400 + yi).toString() else y
        }
        val existing = TunnelReportStore.entriesForDate(context, year, month, day)
        rows = existing.map { e ->
            PreviewRow(
                e.shaft, e.side, e.pointNo, e.lengthCm, e.km, e.dailyProgress, e.shaftProgress,
                e.remaining, e.deviation, e.collapse, e.notes
            )
        }
        // اگر هنوز خالی بود، یک‌بار با همه رکوردها تطبیق نرم بده (سال ۲رقمی ذخیره‌شده)
        if (rows.isEmpty()) {
            val all = TunnelReportStore.allEntries(context)
            val soft = all.filter { TunnelReportStore.sameDate(it, year, month, day) }
            rows = soft.map { e ->
                PreviewRow(
                    e.shaft, e.side, e.pointNo, e.lengthCm, e.km, e.dailyProgress, e.shaftProgress,
                    e.remaining, e.deviation, e.collapse, e.notes
                )
            }
        }
        statusMsg = if (rows.isEmpty()) {
            "برای این تاریخ رکوردی ثبت نشده (کل: ${TunnelReportStore.allEntries(context).size})"
        } else {
            "${rows.size} ردیف بارگذاری شد"
        }
    }

    fun addOrUpdateRow() {
        val len = length.toDoubleOrNullFa() ?: return
        if (len < 0) {
            statusMsg = "طول باید عدد مثبت باشد (به سانتی‌متر)"; return
        }
        if (shaft.isBlank() || side.isBlank() || pointNo.isBlank()) {
            statusMsg = "شفت، سمت و شماره نقطه الزامی‌اند"; return
        }
        val v = TunnelReportStore.computeEntryValues(context, shaft, side, pointNo, len, todayDateKey())
        if (v == null) {
            statusMsg = "نقطه‌ی $pointNo در فایل نقاط پیدا نشد — اول از «نقاط تونل» ثبتش کن"
            return
        }
        val slot = (slotNo.toIntOrNullFa() ?: 4).coerceIn(1, 7)
        // ادغام مقدار فیلد جاری با مقادیر قبلی همان ردیف
        val prev = if (editingIndex >= 0) rows.getOrNull(editingIndex)
            else rows.firstOrNull { it.shaft == shaft && it.side == side }
        var dev = prev?.deviation.orEmpty()
        var col = prev?.collapse.orEmpty()
        if (deviation.isNotBlank()) {
            dev = TunnelReportStore.mergeSlotValue(dev, slot, deviation.trim())
        }
        if (collapse.isNotBlank()) {
            col = TunnelReportStore.mergeSlotValue(col, slot, collapse.trim())
        }
        val noteText = notes.trim().ifBlank { prev?.notes.orEmpty() }
        val newRow = PreviewRow(
            shaft, side, pointNo, len, v.km, v.dailyProgress, v.shaftProgress, v.remaining,
            dev, col, noteText
        )
        rows = if (editingIndex >= 0) {
            rows.toMutableList().also { it[editingIndex] = newRow }
        } else {
            rows.filterNot { it.shaft == shaft && it.side == side } + newRow
        }
        // یادداشت جهت برای روز بعد
        if (noteText.isNotBlank()) {
            val k = "$shaft-${normalizeSide(side)}"
            lastNotesByKey = lastNotesByKey + (k to noteText)
        }
        shaft = ""; side = ""; pointNo = ""; length = ""
        deviation = ""; collapse = ""; notes = ""; slotNo = "4"; editingIndex = -1
        statusMsg = TunnelReportStore.dailyProgressWarning(newRow.dailyProgress) ?: ""
    }

    fun appendSlotOnly() {
        // فقط ثبت ریزش/انحراف بدون تغییر طول — برای چند محل
        if (shaft.isBlank() || side.isBlank()) {
            statusMsg = "اول شفت و سمت را مشخص کن"; return
        }
        val slot = (slotNo.toIntOrNullFa() ?: 4).coerceIn(1, 7)
        val idx = rows.indexOfFirst { it.shaft == shaft && it.side == side }
        if (idx < 0) {
            statusMsg = "اول ردیف حفاری را با طول ثبت کن"; return
        }
        val r = rows[idx]
        var dev = r.deviation
        var col = r.collapse
        if (deviation.isNotBlank()) dev = TunnelReportStore.mergeSlotValue(dev, slot, deviation.trim())
        if (collapse.isNotBlank()) col = TunnelReportStore.mergeSlotValue(col, slot, collapse.trim())
        val noteText = notes.trim().ifBlank { r.notes }
        rows = rows.toMutableList().also {
            it[idx] = r.copy(deviation = dev, collapse = col, notes = noteText)
        }
        deviation = ""; collapse = ""; notes = noteText; slotNo = "4"
        statusMsg = "ریزش/انحراف در فیلد $slot ثبت شد — برای مورد بعد آماده"
    }

    fun startEdit(i: Int) {
        val r = rows[i]
        shaft = r.shaft; side = r.side; pointNo = r.pointNo
        length = r.lengthCm.toString()
        deviation = ""; collapse = ""; notes = r.notes; slotNo = "4"
        editingIndex = i
    }

    fun deleteRow(i: Int) {
        rows = rows.toMutableList().also { it.removeAt(i) }
    }

    fun registerAll() {
        val newEntries = rows.map {
            val xyz = TunnelReportStore.interpolateAtKm(context, it.km)
            ReportEntry(
                year, month, day, it.shaft, it.side, it.pointNo, it.lengthCm, it.deviation, it.collapse,
                it.km, it.dailyProgress, it.shaftProgress, it.remaining,
                x = xyz?.first ?: 0.0,
                y = xyz?.second ?: 0.0,
                z = xyz?.third ?: 0.0,
                notes = it.notes
            )
        }
        TunnelReportStore.replaceEntriesForDate(context, year, month, day, newEntries)
        WorkingDraftStore.clearDaily(context)
        statusMsg = "ثبت شد"
    }

    
    // بازیابی پیش‌نویس گزارش (چرخش / خروج موقت)
    
    LaunchedEffect(rows, year, month, day) {
        WorkingDraftStore.saveDaily(
            context,
            WorkingDraftStore.DailyDraft(
                year, month, day,
                rows.map {
                    WorkingDraftStore.DailyRow(
                        it.shaft, it.side, it.pointNo, it.lengthCm,
                        it.km, it.dailyProgress, it.shaftProgress, it.remaining,
                        it.deviation, it.collapse, it.notes
                    )
                }
            )
        )
    }

    LaunchedEffect(Unit) {
        val draft = WorkingDraftStore.loadDaily(context)
        if (draft != null && draft.year == year && draft.month == month && draft.day == day && draft.rows.isNotEmpty()) {
            rows = draft.rows.map {
                PreviewRow(
                    it.shaft, it.side, it.pointNo, it.lengthCm,
                    it.km, it.dailyProgress, it.shaftProgress, it.remaining,
                    it.deviation, it.collapse, it.notes
                )
            }
            statusMsg = "پیش‌نویس گزارش بازیابی شد"
        } else {
            // اگر برای روز جاری گزارشی ثبت شده، در پیش‌نمایش پایین نشان بده
            loadDay()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appearanceScreenBg())
            .padding(horizontal = 20.dp)
    ) {
        Box {
            ScreenTopBar(title = "گزارش روزانه", color = color, onBack = onBack)
            IconButton(onClick = { showMenu = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Outlined.Settings, contentDescription = "ایمپورت/اکسپورت", tint = Color(0xFFAAB697))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("وارد کردن") }, onClick = {
                    showMenu = false
                    importLauncher.launch(com.adel.assistant.data.AdelDocuments.openDocumentIntent("text/*", "*/*"))
                })
                DropdownMenuItem(text = { Text("خارج کردن") }, onClick = {
                    showMenu = false
                    val text = ExportHub.readCsvText(context, "survey_tunnel_report")
                    val uri = ExportHub.exportText(context, "survey_tunnel_report.csv", text)
                    statusMsg = if (uri != null) "در Documents/AdelAssistant ذخیره شد" else "خطا در خارج کردن"
                })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdelOutlinedTextField(
                value = day, onValueChange = { day = filterNumericInput(it) }, label = { Text("روز") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(0.9f)
            )
            AdelOutlinedTextField(
                value = month, onValueChange = { month = filterNumericInput(it) }, label = { Text("ماه") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(0.9f)
            )
            AdelOutlinedTextField(
                value = year, onValueChange = { year = filterNumericInput(it) }, label = { Text("سال") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1.15f)
            )
            IconButton(onClick = { loadDay() }) {
                Icon(Icons.Outlined.Search, contentDescription = "نمایش گزارش این روز", tint = color)
            }
        }
        if (weekday != null) {
            Text(
                weekday,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAB697),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("افزودن پیشرفت شفت", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            AdelOutlinedTextField(
                value = shaft, onValueChange = { shaft = filterNumericInput(it) }, label = { Text("شفت") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            AdelOutlinedTextField(
                value = side, onValueChange = { side = filterNumericInput(it) }, label = { Text("سمت") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdelOutlinedTextField(
                value = pointNo, onValueChange = { pointNo = filterNumericInput(it) },
                label = { Text("شماره نقطه") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            AdelOutlinedTextField(
                value = length, onValueChange = { length = filterNumericInput(it) },
                label = { Text("طول (سانتی‌متر)") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            // عدد سفید ارتفاع زیر + — بدون به‌هم‌ریختن ردیف
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { addOrUpdateRow() }) {
                    Icon(Icons.Outlined.Add, contentDescription = "افزودن", tint = color)
                }
                val elevHint = remember(pointNo, length, shaft, side) {
                    val lenCm = length.toDoubleOrNullFa() ?: return@remember null
                    if (pointNo.isBlank() || lenCm <= 0.0) return@remember null
                    TunnelReportStore.elevOffsetAtLength(
                        context, pointNo, lenCm, isTowardLess(shaft, side)
                    )
                }
                if (elevHint != null) {
                    Text(
                        String.format(java.util.Locale.US, "%+.2f", elevHint),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdelOutlinedTextField(
                value = deviation, onValueChange = { deviation = filterNumericInput(it) }, label = { Text("انحراف") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(0.9f)
            )
            AdelOutlinedTextField(
                value = collapse, onValueChange = { collapse = filterNumericInput(it) }, label = { Text("ریزش") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(0.9f)
            )
            AdelOutlinedTextField(
                value = slotNo, onValueChange = { slotNo = filterNumericInput(it) },
                label = { Text("موقعیت") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1.15f)
            )
            IconButton(onClick = {
                // بعلاوه: ثبت ردیف (یا فقط ریزش/انحراف) + ذخیره نهایی
                when {
                    length.isNotBlank() && shaft.isNotBlank() && side.isNotBlank() && pointNo.isNotBlank() ->
                        addOrUpdateRow()
                    deviation.isNotBlank() || collapse.isNotBlank() ->
                        appendSlotOnly()
                    else -> {
                        statusMsg = "شفت، سمت، نقطه و طول (یا ریزش/انحراف) را پر کنید"
                        return@IconButton
                    }
                }
                if (rows.isNotEmpty()) registerAll()
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "ثبت", tint = color)
            }
        }
        AdelOutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("توضیحات") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // اخطار پیشرفت حفاری (نسبت به روز قبل) برای ورودی جاری
        val progressWarn = remember(length, pointNo, shaft, side, year, month, day) {
            val cm = length.toDoubleOrNullFa() ?: return@remember null
            if (pointNo.isBlank() || shaft.isBlank() || side.isBlank() || cm <= 0) return@remember null
            val v = TunnelReportStore.computeEntryValues(
                context, shaft, side, pointNo, cm, todayDateKey()
            ) ?: return@remember null
            TunnelReportStore.dailyProgressWarning(v.dailyProgress)
        }
        if (progressWarn != null) {
            Text(
                progressWarn,
                color = Color(0xFFFF9800),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
        }
        // اخطار پیشرفت برای ردیف‌های ثبت‌شده
        rows.mapNotNull { r -> TunnelReportStore.dailyProgressWarning(r.dailyProgress) }
            .distinct()
            .forEach { alert ->
                Text(
                    alert,
                    color = Color(0xFFFF9800),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )
            }

        // یادآوری اتفاق تونل: از ۳ متر قبل تا ۳ متر بعد
        val proximityAlerts = rows.mapNotNull { r ->
            val towardLess = isTowardLess(r.shaft, r.side)
            TunnelReportStore.specialPointProximityAlert(context, r.km, towardLess)
        }.distinct()
        proximityAlerts.forEach { alert ->
            Text(
                alert,
                color = Color(0xFFE53935),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
        }

        Text("پیش‌نمایش گزارش", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows.size) { i ->
                val r = rows[i]
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = appearanceCardColor(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "${r.shaft}به${r.side} (ن${r.pointNo})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { startEdit(i) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Outlined.Edit, contentDescription = "ویرایش",
                                    tint = Color(0xFF7C8A6B), modifier = Modifier.size(14.dp)
                                )
                            }
                            IconButton(onClick = { deleteRow(i) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Outlined.Delete, contentDescription = "پاک کردن",
                                    tint = Color(0xFFC2685E), modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            formatEn(
                                "ک:%.3f  پ.روز:%.3f  پ.شفت:%.3f  مانده:%.3f",
                                r.km, r.dailyProgress, r.shaftProgress, r.remaining
                            ),
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C8A6B)
                        )
                    }
                }
            }
        }

        if (statusMsg.isNotBlank()) {
            Text(
                statusMsg,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAB697),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Button(
                onClick = { registerAll() },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("ثبت") }
            OutlinedButton(
                onClick = {
                    val uri = XlsxReportWriter.generate(context, year, month, day, weekday)
                    if (uri != null) {
                        statusMsg = "فایل در Documents/AdelAssistant ذخیره شد"
                        try {
                            ReportHub.shareUri(
                                context, uri,
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "اشتراک گزارش روزانه"
                            )
                        } catch (_: Exception) {
                            statusMsg = "ذخیره شد (اشتراک ممکن نشد)"
                        }
                    } else {
                        statusMsg = "خطا در ساخت فایل"
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("صدور گزارش") }
        }
    }
}
