package com.adel.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.*
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import kotlin.math.*

private enum class Map2Tab(val title: String, val icon: @Composable () -> Unit) {
    BASE("پایه", { Icon(Icons.Filled.Map, null, Modifier.size(18.dp)) }),
    POINTS("نقاط", { Icon(Icons.Filled.Place, null, Modifier.size(18.dp)) }),
    DIMS("ابعاد", { Icon(Icons.Filled.SquareFoot, null, Modifier.size(18.dp)) }),
    THREED("سه‌بعدی", { Icon(Icons.Filled.ViewInAr, null, Modifier.size(18.dp)) }),
    DRAW("ترسیم", { Icon(Icons.Filled.Architecture, null, Modifier.size(18.dp)) }),
    ALIGN("الاین", { Icon(Icons.Filled.Timeline, null, Modifier.size(18.dp)) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Map2Screen(
    color: Color,
    onBack: () -> Unit,
    onOpenMap: (profileMode: String?) -> Unit = {},
    onOpenVolume: () -> Unit = {},
    onOpenTopography: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenAlign: () -> Unit = {},
    onOpenDxf: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableStateOf(Map2Tab.BASE) }
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    LaunchedEffect(Unit) {
        Map2Session.load(context)
    }
    BackHandler {
        Map2Session.save(context)
        onBack()
    }
    // همگام‌سازی الایمنت از نمایش نقشه / پروفیل
    LaunchedEffect(ProfileSession.alignmentResult) {
        val a = ProfileSession.alignmentResult
        if (a.size >= 2) {
            Map2Session.alignment = a
            Map2Session.message = "الایمنت دریافت شد: ${a.size} رأس"
        }
    }

    val points = Map2Session.points
    var filterCode by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    fun importSurvey(list: List<SurveyPoint>) {
        val base = System.currentTimeMillis()
        val mapped = list.mapIndexed { i, s ->
            Map2Point(
                id = "p${base + i}",
                name = s.id.ifBlank { "P${i + 1}" },
                x = s.x, y = s.y, z = s.z,
                code = s.code,
                group = codeBase(s.code)
            )
        }
        Map2Session.addPoints(mapped)
        Map2Session.message = "${mapped.size} نقطه اضافه شد (جمع: ${Map2Session.points.size})"
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val name = uri.lastPathSegment.orEmpty()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val parsed = PointConverter.readBytes(bytes, name)
            if (parsed.isEmpty()) Map2Session.message = "نقطه‌ای خوانده نشد"
            else importSurvey(parsed)
        } catch (e: Exception) {
            Map2Session.message = "خطا: ${e.message}"
        }
    }

    fun exportPoints(ext: String) {
        val src = if (filterCode.isBlank()) points else Map2Session.pointsByCode(filterCode)
        if (src.isEmpty()) {
            Map2Session.message = "نقطه‌ای برای خروجی نیست"
            return
        }
        val survey = src.map { it.toSurvey() }
        val text = PointConverter.write(survey, ext)
        val fname = "map2_${System.currentTimeMillis() / 1000}.$ext"
        val uri = FileExport.exportTextToDocuments(context, fname, text)
        Map2Session.message = if (uri != null) "خروجی: $fname" else "خطای ذخیره"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // —— نوار بالا ثابت ——
        TopAppBar(
            title = {
                Column {
                    Text("نقشه ۲", fontWeight = FontWeight.Bold)
                    Text(
                        Map2Session.projectName + " · ${points.size} نقطه · Zone ${Map2Session.zone}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    Map2Session.save(context)
                    onBack()
                }) { Icon(Icons.Filled.ArrowBack, "بازگشت") }
            },
            actions = {
                IconButton(onClick = { Map2Session.save(context) }) {
                    Icon(Icons.Filled.Done, "ذخیره نشست")
                }
                IconButton(onClick = { Map2Session.load(context) }) {
                    Icon(Icons.Filled.Folder, "باز کردن نشست")
                }
                IconButton(onClick = { onOpenMap(null) }) {
                    Icon(Icons.Filled.Map, "نمایش نقشه")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = color.copy(alpha = 0.12f))
        )

        // —— منوی افقی ——
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Map2Tab.entries.forEach { t ->
                if (Map2Session.simpleMode && t != Map2Tab.BASE && t != Map2Tab.POINTS) return@forEach
                val sel = tab == t
                FilterChip(
                    selected = sel,
                    onClick = { tab = t },
                    label = { Text(t.title, fontSize = 12.sp) },
                    leadingIcon = { t.icon() }
                )
            }
        }

        if (Map2Session.message.isNotBlank()) {
            Text(
                Map2Session.message,
                color = color,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        // —— محتوا ——
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Map2Tab.BASE -> Map2BaseTab(color, onOpenMap, onOpenVolume, onOpenTopography, onOpenProfile, onOpenAlign, onOpenDxf)
                Map2Tab.POINTS -> Map2PointsTab(
                    color, numKb, filterCode, { filterCode = it }, selectedIds, { selectedIds = it },
                    filePicker = { filePicker.launch(arrayOf("*/*")) },
                    onExport = { exportPoints(it) },
                    onOpenMap = { onOpenMap(null) }
                )
                Map2Tab.DIMS -> Map2DimsTab(color, numKb, selectedIds, onOpenVolume)
                Map2Tab.THREED -> Map2ThreeDTab(color, numKb, onOpenMap, onOpenTopography, onOpenProfile, onOpenVolume)
                Map2Tab.DRAW -> Map2DrawTab(color, filterCode, onOpenDxf, { onOpenMap(null) })
                Map2Tab.ALIGN -> Map2AlignTab(color, onOpenAlign)
            }
        }
    }
}

@Composable
private fun Map2BaseTab(
    color: Color,
    onOpenMap: (String?) -> Unit,
    onOpenVolume: () -> Unit,
    onOpenTopography: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAlign: () -> Unit,
    onOpenDxf: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("محیط یکپارچه نقشه", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "بوم اصلی همان «نمایش نقشه» است. نقاط مشترک را از منوی نقاط وارد کنید؛ سپس توپو، پروفیل، احجام و مساحت از همان داده استفاده می‌کنند.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Button(
                    onClick = { onOpenMap(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("باز کردن بوم نقشه (نمایش نقشه)")
                }
            }
        }

        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("تنظیمات پروژه", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    Map2Session.projectName,
                    { Map2Session.projectName = it },
                    label = { Text("نام پروژه") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        Map2Session.zone.toString(),
                        {
                            it.filter(Char::isDigit).take(2).toIntOrNull()?.let { z ->
                                Map2Session.zone = z.coerceIn(1, 60)
                            }
                        },
                        label = { Text("UTM Zone") },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.width(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(Map2Session.simpleMode, { Map2Session.simpleMode = it })
                        Text("حالت ساده", fontSize = 13.sp)
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("میانبر ابزارها", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onOpenTopography, modifier = Modifier.weight(1f)) { Text("توپو", fontSize = 11.sp) }
                    OutlinedButton(onClick = onOpenProfile, modifier = Modifier.weight(1f)) { Text("پروفیل", fontSize = 11.sp) }
                    OutlinedButton(onClick = onOpenVolume, modifier = Modifier.weight(1f)) { Text("احجام", fontSize = 11.sp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onOpenAlign, modifier = Modifier.weight(1f)) { Text("الاین", fontSize = 11.sp) }
                    OutlinedButton(onClick = onOpenDxf, modifier = Modifier.weight(1f)) { Text("ترسیم DXF", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onOpenMap("alignment") }, modifier = Modifier.weight(1f)) { Text("الایمنت", fontSize = 11.sp) }
                }
            }
        }

        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("وضعیت نشست", fontWeight = FontWeight.Bold)
                Text("نقاط: ${Map2Session.points.size}", fontSize = 12.sp, color = TextSecondary)
                Text("الایمنت: ${Map2Session.alignment.size} رأس", fontSize = 12.sp, color = TextSecondary)
                Map2Session.lastArea?.let {
                    Text("آخرین مساحت: ${formatMoney(it)} m²", fontSize = 12.sp, color = color)
                }
                Map2Session.lastVolume?.let {
                    Text(
                        "آخرین حجم: خاکبرداری ${formatMoney(it.cutM3)} / خاکریزی ${formatMoney(it.fillM3)} m³",
                        fontSize = 12.sp,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun Map2PointsTab(
    color: Color,
    numKb: KeyboardOptions,
    filterCode: String,
    onFilter: (String) -> Unit,
    selectedIds: Set<String>,
    onSelect: (Set<String>) -> Unit,
    filePicker: () -> Unit,
    onExport: (String) -> Unit,
    onOpenMap: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var z by remember { mutableStateOf("0") }

    val shown = if (filterCode.isBlank()) Map2Session.points else Map2Session.pointsByCode(filterCode)

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = filePicker, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.weight(1f)) {
                Text("ورود فایل", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onOpenMap, modifier = Modifier.weight(1f)) { Text("ثبت روی نقشه", fontSize = 12.sp) }
            OutlinedButton(
                onClick = {
                    val n = Map2Session.dedupe(0.01)
                    Map2Session.message = if (n > 0) "$n نقطه تکراری حذف شد" else "تکراری نبود"
                },
                modifier = Modifier.weight(1f)
            ) { Text("حذف تکراری", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            filterCode, onFilter,
            label = { Text("فیلتر کد (مثلاً tp)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text("گروه‌ها: ${Map2Session.groups().joinToString(" · ")}", fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))

        // ورود دستی
        Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ثبت دستی", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("N") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(code, { code = it }, label = { Text("کد") }, modifier = Modifier.weight(0.8f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(x, { x = it }, label = { Text("X") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                    OutlinedTextField(y, { y = it }, label = { Text("Y") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                    OutlinedTextField(z, { z = it }, label = { Text("Z") }, modifier = Modifier.weight(0.8f), singleLine = true, keyboardOptions = numKb)
                }
                Button(
                    onClick = {
                        val xx = x.toDoubleOrNullFa() ?: return@Button
                        val yy = y.toDoubleOrNullFa() ?: return@Button
                        val zz = z.toDoubleOrNullFa() ?: 0.0
                        val id = System.nanoTime().toString()
                        Map2Session.addPoints(
                            listOf(
                                Map2Point(id, name.ifBlank { "P${Map2Session.points.size + 1}" }, xx, yy, zz, code, codeBase(code))
                            )
                        )
                        name = ""; x = ""; y = ""; z = "0"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("افزودن نقطه") }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("txt", "dat", "gsi", "csv", "kml").forEach { ext ->
                OutlinedButton(onClick = { onExport(ext) }) { Text(ext.uppercase(), fontSize = 11.sp) }
            }
            TextButton(onClick = {
                Map2Session.clearPoints()
                onSelect(emptySet())
                Map2Session.message = "نقاط پاک شد"
            }) { Text("پاک‌سازی", color = Color(0xFFC62828)) }
        }

        Spacer(Modifier.height(6.dp))
        Text("${shown.size} نقطه", fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(shown, key = { it.id }) { p ->
                val sel = p.id in selectedIds
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (sel) color.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable {
                            onSelect(if (sel) selectedIds - p.id else selectedIds + p.id)
                        }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(sel, {
                        onSelect(if (it) selectedIds + p.id else selectedIds - p.id)
                    })
                    Column(Modifier.weight(1f)) {
                        Text("${p.name}  [${p.code}]", fontSize = 13.sp, color = TextPrimary)
                        Text(
                            formatEn("X %.3f  Y %.3f  Z %.3f", p.x, p.y, p.z),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = {
                        val (lat, lon) = UtmGeo.toLatLon(p.x, p.y, Map2Session.zone)
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UtmGeo.neshanIntentUri(lat, lon))))
                        } catch (_: Exception) {
                            Map2Session.message = "نشان باز نشد"
                        }
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Navigation, "نشان", tint = color, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(formatEn("%.3f\t%.3f\t%.3f", p.x, p.y, p.z)))
                        Map2Session.message = "کپی شد"
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ContentCopy, null, tint = color, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { Map2Session.removePoint(p.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Map2DimsTab(color: Color, numKb: KeyboardOptions, selectedIds: Set<String>, onOpenVolume: () -> Unit) {
    var codeEx by remember { mutableStateOf("") }
    var codeDs by remember { mutableStateOf("") }
    var planeZ by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مساحت و محیط", fontWeight = FontWeight.Bold)
                Text(
                    if (selectedIds.isEmpty()) "از همه نقاط به ترتیب لیست"
                    else "از ${selectedIds.size} نقطه انتخاب‌شده در منوی نقاط",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Button(
                    onClick = {
                        val ids = selectedIds.toList().ifEmpty { null }
                        val (a, p) = Map2Session.polygonAreaPerimeter(ids)
                        if (a <= 0) {
                            Map2Session.message = "حداقل ۳ نقطه لازم است"
                        } else {
                            Map2Session.lastArea = a
                            Map2Session.lastPerimeter = p
                            Map2Session.message = "مساحت ${formatMoney(a)} m² | محیط ${formatMoney(p)} m"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("محاسبه مساحت / محیط") }
                Map2Session.lastArea?.let { a ->
                    Text("مساحت: ${formatMoney(a)} m²  (${formatMoney(a / 10000.0)} ha)", color = color)
                    Text("محیط: ${formatMoney(Map2Session.lastPerimeter ?: 0.0)} m", color = TextSecondary)
                }
            }
        }

        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("احجام (TIN)", fontWeight = FontWeight.Bold)
                OutlinedTextField(codeEx, { codeEx = it }, label = { Text("کد سطح موجود (خالی=همه)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(codeDs, { codeDs = it }, label = { Text("کد سطح طراحی (خالی=ارتفاع ثابت)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(planeZ, { planeZ = it }, label = { Text("ارتفاع ثابت طراحی (اختیاری)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb)
                Button(
                    onClick = {
                        val ex = Map2Session.toVolPoints(codeEx)
                        if (ex.size < 3) {
                            Map2Session.message = "سطح موجود: حداقل ۳ نقطه"
                            return@Button
                        }
                        val ds: List<VolPoint> = when {
                            codeDs.isNotBlank() -> Map2Session.toVolPoints(codeDs)
                            planeZ.toDoubleOrNullFa() != null -> {
                                val z0 = planeZ.toDoubleOrNullFa()!!
                                ex.map { it.copy(z = z0, id = it.id + "_ds") }
                            }
                            else -> emptyList()
                        }
                        if (ds.size < 3) {
                            Map2Session.message = "سطح طراحی کافی نیست"
                            return@Button
                        }
                        val r = VolumeEngine.computeTin(ex, ds)
                        Map2Session.lastVolume = r
                        Map2Session.message =
                            "خاکبرداری ${formatMoney(r.cutM3)} | خاکریزی ${formatMoney(r.fillM3)} | خالص ${formatMoney(r.netM3)} m³"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("محاسبه احجام") }
                OutlinedButton(onClick = onOpenVolume, modifier = Modifier.fillMaxWidth()) {
                    Text("باز کردن ماژول کامل احجام")
                }
                Map2Session.lastVolume?.let { r ->
                    Text("Cut: ${formatMoney(r.cutM3)} m³", color = Color(0xFFC62828))
                    Text("Fill: ${formatMoney(r.fillM3)} m³", color = Color(0xFF2E7D32))
                    Text("Net: ${formatMoney(r.netM3)} m³ | Area: ${formatMoney(r.areaM2)} m²", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun Map2ThreeDTab(
    color: Color,
    numKb: KeyboardOptions,
    onOpenMap: (String?) -> Unit,
    onOpenTopography: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenVolume: () -> Unit
) {
    var code by remember { mutableStateOf("tp") }
    var interval by remember { mutableStateOf(Map2Session.topoInterval.toString()) }
    var step by remember { mutableStateOf(Map2Session.profileStep.toString()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("توپوگرافی", fontWeight = FontWeight.Bold)
                OutlinedTextField(code, { code = it }, label = { Text("فیلتر کد (tp یا خالی)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(interval, { interval = it }, label = { Text("فاصله تراز (m)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb)
                Button(
                    onClick = {
                        val iv = interval.toDoubleOrNullFa()?.coerceAtLeast(0.01) ?: 1.0
                        Map2Session.topoInterval = iv
                        val vols = if (code.isBlank()) Map2Session.toVolPoints() else Map2Session.toVolPoints(code)
                        val use = if (vols.size >= 3) vols else Map2Session.toVolPoints()
                        if (use.size < 3) {
                            Map2Session.message = "حداقل ۳ نقطه برای توپو"
                            return@Button
                        }
                        Map2Session.syncToTopography(code)
                        val r = TopographyEngine.build(use, iv)
                        Map2Session.lastTopo = r
                        Map2Session.message = "TIN: ${r.triangles.size} مثلث | تراز: ${r.contours.segments.size} قطعه"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ساخت توپوگرافی") }
                OutlinedButton(onClick = {
                    Map2Session.syncToTopography(code)
                    onOpenTopography()
                }, modifier = Modifier.fillMaxWidth()) { Text("نمایش کامل توپوگرافی") }

                Map2Session.lastTopo?.let { r ->
                    Spacer(Modifier.height(6.dp))
                    Map2SketchVol(r.points, color, Modifier.fillMaxWidth().height(160.dp))
                }
            }
        }

        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("پروفیل طولی", fontWeight = FontWeight.Bold)
                OutlinedTextField(step, { step = it }, label = { Text("فاصله ایستگاه (m)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb)
                Text("الایمنت: ${Map2Session.alignment.size} رأس", fontSize = 12.sp, color = TextSecondary)
                Button(
                    onClick = {
                        Map2Session.syncToProfile("سطح", code)
                        Map2Session.profileStep = step.toDoubleOrNullFa()?.coerceAtLeast(0.01) ?: 10.0
                        onOpenMap("alignment")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("رسم الایمنت روی نقشه") }
                Button(
                    onClick = {
                        val st = step.toDoubleOrNullFa()?.coerceAtLeast(0.01) ?: 10.0
                        Map2Session.profileStep = st
                        val vols = Map2Session.toVolPoints(code).ifEmpty { Map2Session.toVolPoints() }
                        val surf = ProfileEngine.buildSurface("سطح", vols)
                        if (surf == null) {
                            Map2Session.message = "سطح پروفیل ساخته نشد"
                            return@Button
                        }
                        val verts = Map2Session.alignment
                        if (verts.size < 2) {
                            Map2Session.message = "ابتدا الایمنت را رسم کنید"
                            return@Button
                        }
                        val r = ProfileEngine.sample(verts, listOf(surf), st, Map2Session.startChainage)
                        Map2Session.lastProfile = r
                        Map2Session.message = "پروفیل: ${r.rows.size} ایستگاه، طول ${formatMoney(r.length)} m"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("محاسبه پروفیل") }
                OutlinedButton(onClick = {
                    Map2Session.syncToProfile("سطح", code)
                    onOpenProfile()
                }, modifier = Modifier.fillMaxWidth()) { Text("باز کردن ماژول پروفیل") }

                Map2Session.lastProfile?.let { r ->
                    Text(
                        "Z: ${formatMoney(r.minElevation)} … ${formatMoney(r.maxElevation)}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("نمایش سه‌بعدی", fontWeight = FontWeight.Bold)
                Text("از ماژول احجام (حالت ۳د) یا توپوگرافی برای نمایش فضایی استفاده کنید.", fontSize = 12.sp, color = TextSecondary)
                OutlinedButton(onClick = onOpenVolume, modifier = Modifier.fillMaxWidth()) { Text("احجام / نمای ۳د") }
            }
        }
    }
}

@Composable
private fun Map2DrawTab(color: Color, filterCode: String, onOpenDxf: () -> Unit, onOpenMap: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ترسیم از نقاط نشست", fontWeight = FontWeight.Bold)
                Text("خروجی DXF با لایه‌بندی بر اساس کد نقطه", fontSize = 12.sp, color = TextSecondary)
                Button(
                    onClick = {
                        val src = if (filterCode.isBlank()) Map2Session.points else Map2Session.pointsByCode(filterCode)
                        if (src.isEmpty()) {
                            Map2Session.message = "نقطه‌ای نیست"
                            return@Button
                        }
                        val text = PointConverter.write(src.map { it.toSurvey() }, "dxf")
                        val name = "map2_${System.currentTimeMillis() / 1000}.dxf"
                        val uri = FileExport.exportTextToDocuments(context, name, text)
                        Map2Session.message = if (uri != null) "DXF ذخیره شد: $name" else "خطای ذخیره DXF"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("خروجی DXF از نقاط") }
                OutlinedButton(onClick = onOpenDxf, modifier = Modifier.fillMaxWidth()) {
                    Text("ترسیم نقشه (ماژول کامل)")
                }
                OutlinedButton(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) {
                    Text("پیش‌نمایش روی بوم نقشه")
                }
            }
        }
    }
}

@Composable
private fun Map2AlignTab(color: Color, onOpenAlign: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الاین / تبدیل مختصات", fontWeight = FontWeight.Bold)
                Text(
                    "برای تبدیل تشابه کنترل↔برداشت از ماژول الاین استفاده کنید. پس از تبدیل می‌توانید نقاط را دوباره به نشست نقشه ۲ وارد کنید.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Button(
                    onClick = onOpenAlign,
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("باز کردن الاین") }
            }
        }
    }
}

@Composable
private fun Map2SketchVol(points: List<VolPoint>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFF1A1D21), RoundedCornerShape(8.dp))) {
        if (points.size < 2) return@Canvas
        val xs = points.map { it.x }
        val ys = points.map { it.y }
        val minX = xs.minOrNull()!!
        val maxX = xs.maxOrNull()!!
        val minY = ys.minOrNull()!!
        val maxY = ys.maxOrNull()!!
        val dx = (maxX - minX).coerceAtLeast(1.0)
        val dy = (maxY - minY).coerceAtLeast(1.0)
        val pad = 16f
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        fun sx(e: Double) = pad + ((e - minX) / dx * w).toFloat()
        fun sy(n: Double) = pad + ((maxY - n) / dy * h).toFloat()
        points.forEach { p ->
            drawCircle(color, 4f, Offset(sx(p.x), sy(p.y)))
        }
    }
}
