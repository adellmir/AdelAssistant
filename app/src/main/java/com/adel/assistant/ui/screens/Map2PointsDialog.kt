package com.adel.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adel.assistant.data.*
import com.adel.assistant.utils.DxfMapGenerator
import java.util.Locale

/**
 * ورود نقاط نقشه ۲ — تمام‌صفحه
 * سربرگ ۱: ترسیم نقطه‌ای | سربرگ ۲: ترسیم خطی (بر اساس کد)
 */
@Composable
fun Map2PointsDialog(
    color: Color,
    onDismiss: () -> Unit,
    onCommitPointDraw: (List<Map2Point>, PointDrawOptions) -> Unit,
    onCommitLineDraw: (String) -> Unit
) {
    val context = LocalContext.current
    var points by remember { mutableStateOf<List<Map2Point>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tab by remember { mutableStateOf(0) } // 0 نقطه‌ای 1 خطی
    var message by remember { mutableStateOf("") }
    var filePicked by remember { mutableStateOf(false) }

    // ترسیم نقطه‌ای
    var textSize by remember { mutableStateOf("1.0") }
    var colorIdx by remember { mutableStateOf(7) } // سفید پیش‌فرض در ACI
    var showSymbol by remember { mutableStateOf(true) }
    var showName by remember { mutableStateOf(true) }
    var showCode by remember { mutableStateOf(true) }
    var showElev by remember { mutableStateOf(true) }

    // گزینش
    var selectMode by remember { mutableStateOf("all") } // all | none | range
    var rangeAnchor by remember { mutableStateOf<String?>(null) }
    var sortBy by remember { mutableStateOf("num") } // num | code

    // ترسیم خطی — تنظیمات کدهای POINT
    var codeSettings by remember { mutableStateOf<Map<String, CodeSetting>>(emptyMap()) }
    var lineColorAci by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var colorDialogCode by remember { mutableStateOf<String?>(null) }
    var lineTextSize by remember { mutableStateOf("1.0") }
    var lineShowName by remember { mutableStateOf(true) }
    var lineShowCode by remember { mutableStateOf(true) }
    var lineShowElev by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            if (!filePicked && points.isEmpty()) onDismiss()
            return@rememberLauncherForActivityResult
        }
        try {
            val name = uri.lastPathSegment.orEmpty()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val parsed = PointConverter.readBytes(bytes, name)
            if (parsed.isEmpty()) {
                message = "نقطه‌ای خوانده نشد"
                return@rememberLauncherForActivityResult
            }
            val base = System.currentTimeMillis()
            val mapped = parsed.mapIndexed { i, s ->
                Map2Point(
                    id = "p${base}_$i",
                    name = s.id.ifBlank { "P${i + 1}" },
                    x = s.x, y = s.y, z = s.z,
                    code = s.code
                )
            }
            points = mapped
            selectedIds = mapped.map { it.id }.toSet()
            selectMode = "all"
            filePicked = true
            // تنظیمات کد برای ترسیم خطی
            val unique = mapped.map { codeBase(it.code) }.filter { it.isNotBlank() }.distinct()
            codeSettings = unique.associateWith { DefaultCodeRules.createDefaultSetting(it) }
            lineColorAci = unique.associateWith { code ->
                Map2Session.lineColors[code.lowercase()]
                    ?: DefaultCodeRules.createDefaultSetting(code).colorIndex
            }
            message = "${mapped.size} نقطه از $name"
        } catch (e: Exception) {
            message = "خطا: ${e.message}"
        }
    }

    // با باز شدن دیالوگ مستقیم درخواست فایل
    LaunchedEffect(Unit) {
        if (!filePicked && points.isEmpty()) {
            picker.launch(arrayOf("*/*", "text/*", "application/octet-stream", "application/vnd.google-earth.kml+xml"))
        }
    }

    fun displayList(): List<Map2Point> {
        val base = when (sortBy) {
            "code" -> points.sortedWith(compareBy({ it.code }, { it.name.toDoubleOrNull() ?: Double.MAX_VALUE }, { it.name }))
            else -> points.sortedWith(compareBy({ it.name.toDoubleOrNull() ?: Double.MAX_VALUE }, { it.name }))
        }
        return base
    }

    fun toggleSelect(id: String) {
        when (selectMode) {
            "range" -> {
                if (rangeAnchor == null) {
                    rangeAnchor = id
                    selectedIds = setOf(id)
                    message = "ابتدای بازه — نقطه پایان را بزن"
                } else {
                    val ids = displayList().map { it.id }
                    val i1 = ids.indexOf(rangeAnchor)
                    val i2 = ids.indexOf(id)
                    if (i1 >= 0 && i2 >= 0) {
                        val a = minOf(i1, i2); val b = maxOf(i1, i2)
                        selectedIds = ids.subList(a, b + 1).toSet()
                        message = "${b - a + 1} نقطه در بازه"
                    }
                    rangeAnchor = null
                }
            }
            else -> {
                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
            }
        }
    }

    fun selectedPoints(): List<Map2Point> {
        val list = displayList()
        return if (selectedIds.isEmpty()) list else list.filter { it.id in selectedIds }
    }

    fun commitPointDraw() {
        val chosen = selectedPoints()
        if (chosen.isEmpty()) {
            message = "نقطه‌ای برای ثبت نیست"
            return
        }
        val h = textSize.replace(',', '.').toDoubleOrNull()?.coerceIn(0.05, 50.0) ?: 1.0
        val opts = PointDrawOptions(
            textSize = h,
            colorAci = DxfColors.aci.getOrElse(colorIdx) { 7 },
            showSymbol = showSymbol,
            showName = showName,
            showCode = showCode,
            showElev = showElev
        )
        // ذخیره در نشست
        val cat = Map2Session.addCategory(
            name = Map2Session.nextPgName(),
            points = chosen
        )
        Map2Session.updateCategory(cat.id) {
            it.copy(
                showSymbol = showSymbol,
                showName = showName,
                showCode = showCode,
                showElev = showElev,
                textSize = h,
                textColorAci = opts.colorAci
            )
        }
        Map2Session.seedToolsFromPoints()
        onCommitPointDraw(chosen, opts)
    }

    fun commitLineDraw() {
        if (points.isEmpty()) {
            message = "نقطه‌ای نیست"
            return
        }
        val h = lineTextSize.replace(',', '.').toFloatOrNull()?.coerceIn(0.05f, 50f) ?: 1f
        // اعمال تنظیمات متن روی کدهای POINT
        val updated = codeSettings.mapValues { (code, s) ->
            val aci = lineColorAci[code] ?: s.colorIndex
            Map2Session.setLineColor(code, aci)
            if (s.category == CodeCategory.LINE) {
                s.copy(colorIndex = aci)
            } else if (s.category == CodeCategory.POINT) {
                s.copy(
                    textSize = h,
                    showNumber = lineShowName,
                    showCode = lineShowCode,
                    showZ = lineShowElev,
                    colorIndex = aci
                )
            } else s
        }
        // نقاط را هم در نشست نگه دار (پروژه یکپارچه)
        if (Map2Session.allPoints().isEmpty()) {
            Map2Session.addCategory(Map2Session.nextPgName(), points)
        }
        Map2Session.seedToolsFromPoints()
        val survey = points.map { it.toSurvey() }
        val dxf = DxfMapGenerator.generate(survey, updated)
        Map2Session.addProjectPart("خطوط", dxf)
        onCommitLineDraw(dxf)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF12150F),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                // هدر
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ورود نقاط",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        picker.launch(arrayOf("*/*", "text/*", "application/octet-stream"))
                    }) { Text("فایل دیگر", color = color, fontSize = 12.sp) }
                    TextButton(onClick = onDismiss) { Text("بستن", color = Color(0xFFE57373), fontSize = 12.sp) }
                }
                if (message.isNotBlank()) {
                    Text(message, color = Color(0xFFB0B8A8), fontSize = 12.sp)
                }

                if (points.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("فایل نقاط (txt / gsi / kml / dat) را انتخاب کنید", color = Color(0xFF8A9280))
                    }
                    return@Surface
                }

                // سربرگ‌ها
                TabRow(selectedTabIndex = tab, containerColor = Color(0xFF1A1F16)) {
                    Tab(selected = tab == 0, onClick = { tab = 0 },
                        text = { Text("ترسیم نقطه‌ای", fontSize = 13.sp) })
                    Tab(selected = tab == 1, onClick = { tab = 1 },
                        text = { Text("ترسیم خطی", fontSize = 13.sp) })
                }

                Spacer(Modifier.height(6.dp))

                if (tab == 0) {
                    // ردیف ۱: سایز | رنگ | چک‌باکس‌ها | ثبت
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = textSize,
                            onValueChange = { textSize = it },
                            label = { Text("سایز", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.width(72.dp),
                            textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        // رنگ
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("رنگ", color = Color(0xFFB0B8A8), fontSize = 11.sp)
                            Spacer(Modifier.width(4.dp))
                            DxfColors.compose.take(8).forEachIndexed { idx, c ->
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .background(c, RoundedCornerShape(3.dp))
                                        .border(
                                            if (colorIdx == idx) 2.dp else 0.dp,
                                            Color.White,
                                            RoundedCornerShape(3.dp)
                                        )
                                        .clickable { colorIdx = idx }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MiniCheck("نماد", showSymbol) { showSymbol = it }
                            MiniCheck("نام", showName) { showName = it }
                            MiniCheck("کد", showCode) { showCode = it }
                            MiniCheck("ارتفاع", showElev) { showElev = it }
                        }
                        Button(
                            onClick = { commitPointDraw() },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("ثبت", fontSize = 13.sp) }
                    }

                    Spacer(Modifier.height(4.dp))

                    // ردیف ۲: گزینش و ترتیب
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectLink("همه", selectMode == "all" && selectedIds.size == points.size) {
                            selectMode = "all"
                            selectedIds = points.map { it.id }.toSet()
                            rangeAnchor = null
                        }
                        Sep()
                        SelectLink("هیچکدام", selectedIds.isEmpty()) {
                            selectMode = "none"
                            selectedIds = emptySet()
                            rangeAnchor = null
                        }
                        Sep()
                        SelectLink("بازه", selectMode == "range") {
                            selectMode = "range"
                            rangeAnchor = null
                            message = "حالت بازه: دو نقطه ابتدا و انتها"
                        }
                        Sep()
                        SelectLink("ترتیب شماره", sortBy == "num") { sortBy = "num" }
                        Sep()
                        SelectLink("ترتیب کد", sortBy == "code") { sortBy = "code" }
                    }

                    Spacer(Modifier.height(4.dp))

                    // لیست اکسلی
                    PointsExcelList(
                        points = displayList(),
                        selectedIds = selectedIds,
                        color = color,
                        onToggle = { toggleSelect(it) },
                        onChange = { updated ->
                            points = points.map { if (it.id == updated.id) updated else it }
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // ترسیم خطی
                    Text(
                        "نقاط بر اساس کد دسته‌بندی می‌شوند (مثل ترسیم نقشه). برای کدهای نقطه‌ای سایز و نمایش متن را تنظیم کنید.",
                        color = Color(0xFF8A9280),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = lineTextSize,
                            onValueChange = { lineTextSize = it },
                            label = { Text("سایز متن نقطه", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.width(100.dp),
                            textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        MiniCheck("نام", lineShowName) { lineShowName = it }
                        MiniCheck("کد", lineShowCode) { lineShowCode = it }
                        MiniCheck("ارتفاع", lineShowElev) { lineShowElev = it }
                        Button(
                            onClick = { commitLineDraw() },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("ثبت ترسیم", fontSize = 13.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val codes = codeSettings.keys.sorted()
                        items(codes, key = { it }) { code ->
                            val s = codeSettings[code]!!
                            Surface(
                                color = Color(0xFF1E241A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(code, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                                    listOf(
                                        CodeCategory.LINE to "خط",
                                        CodeCategory.POINT to "نقطه",
                                        CodeCategory.IGNORE to "نادیده"
                                    ).forEach { (cat, label) ->
                                        FilterChip(
                                            selected = s.category == cat,
                                            onClick = {
                                                codeSettings = codeSettings + (code to s.copy(category = cat))
                                            },
                                            label = { Text(label, fontSize = 10.sp) },
                                            modifier = Modifier.padding(end = 2.dp)
                                        )
                                    }
                                    Text(
                                        "${points.count { codeBase(it.code).equals(code, true) }} نقطه",
                                        color = Color(0xFF8A9280),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                                // دکمه رنگ — باز شدن پنجره انتخاب
                                if (s.category == CodeCategory.LINE) {
                                    val cur = lineColorAci[code] ?: s.colorIndex
                                    TextButton(
                                        onClick = { colorDialogCode = code },
                                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                    ) {
                                        Text("رنگ ($cur)", color = Color(0xFF81C995), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    // پنجره انتخاب رنگ خط
    colorDialogCode?.let { code ->
        val s = codeSettings[code]
        val cur = lineColorAci[code] ?: s?.colorIndex ?: 7
        val palette = listOf(
            1 to "قرمز",
            3 to "سبز",
            5 to "آبی",
            2 to "زرد",
            6 to "magenta",
            4 to "فیروزه",
            7 to "سفید",
            30 to "نارنجی",
            8 to "خاکستری"
        )
        AlertDialog(
            onDismissRequest = { colorDialogCode = null },
            title = { Text("رنگ خط — کد $code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.chunked(3).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { (aci, label) ->
                                FilterChip(
                                    selected = cur == aci,
                                    onClick = {
                                        lineColorAci = lineColorAci + (code to aci)
                                        if (s != null) {
                                            codeSettings = codeSettings + (code to s.copy(colorIndex = aci))
                                        }
                                        colorDialogCode = null
                                    },
                                    label = { Text("$label ($aci)", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { colorDialogCode = null }) { Text("بستن") }
            }
        )
    }
}


data class PointDrawOptions(
    val textSize: Double,
    val colorAci: Int,
    val showSymbol: Boolean,
    val showName: Boolean,
    val showCode: Boolean,
    val showElev: Boolean
)

@Composable
private fun MiniCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { onChange(!checked) }
            .padding(horizontal = 2.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.size(28.dp),
            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF81C995))
        )
        Text(label, color = Color(0xFFCFD8C8), fontSize = 11.sp)
    }
}

@Composable
private fun SelectLink(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Color(0xFF81C995) else Color(0xFFB0B8A8),
        fontSize = 12.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun Sep() {
    Text("|", color = Color(0xFF5A6258), fontSize = 12.sp)
}

@Composable
private fun PointsExcelList(
    points: List<Map2Point>,
    selectedIds: Set<String>,
    color: Color,
    onToggle: (String) -> Unit,
    onChange: (Map2Point) -> Unit,
    modifier: Modifier = Modifier
) {
    val hScroll = rememberScrollState()
    Column(modifier) {
        Row(
            Modifier
                .horizontalScroll(hScroll)
                .background(Color(0xFF2A3324), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Header("✓", 36)
            Header("N", 70)
            Header("X", 100)
            Header("Y", 100)
            Header("Z", 80)
            Header("کد", 80)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            items(points, key = { it.id }) { p ->
                Row(
                    Modifier
                        .horizontalScroll(hScroll)
                        .background(Color(0xFF1E241A))
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = p.id in selectedIds,
                        onCheckedChange = { onToggle(p.id) },
                        modifier = Modifier.width(36.dp).size(28.dp),
                        colors = CheckboxDefaults.colors(checkedColor = color)
                    )
                    Cell(p.name, 70) { onChange(p.copy(name = it)) }
                    Cell(fmt(p.x), 100, true) { v ->
                        v.replace(',', '.').toDoubleOrNull()?.let { onChange(p.copy(x = it)) }
                    }
                    Cell(fmt(p.y), 100, true) { v ->
                        v.replace(',', '.').toDoubleOrNull()?.let { onChange(p.copy(y = it)) }
                    }
                    Cell(fmt(p.z), 80, true) { v ->
                        v.replace(',', '.').toDoubleOrNull()?.let { onChange(p.copy(z = it)) }
                    }
                    Cell(p.code, 80) { onChange(p.copy(code = it)) }
                }
            }
        }
    }
}

@Composable
private fun Header(t: String, w: Int) {
    Text(
        t,
        color = Color(0xFFCFD8C8),
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.width(w.dp).padding(horizontal = 4.dp)
    )
}

@Composable
private fun Cell(value: String, w: Int, numeric: Boolean = false, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(value) { text = value }
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onCommit(it)
        },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions.Default,
        modifier = Modifier
            .width(w.dp)
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .background(Color(0xFF2A3324), RoundedCornerShape(3.dp))
            .padding(4.dp)
    )
}

private fun fmt(v: Double): String = String.format(Locale.US, "%.3f", v)
