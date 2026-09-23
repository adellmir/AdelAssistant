package com.adel.assistant.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.PlumbColumn
import com.adel.assistant.data.PlumbNeighbor
import com.adel.assistant.data.PlumbProject
import com.adel.assistant.data.PlumbReading
import com.adel.assistant.data.PlumbStore
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.ToolPrimary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlumbScreen(color: Color = ToolPrimary, onBack: () -> Unit) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf(PlumbStore.loadAll(context)) }
    var name by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("1405") }
    var query by remember { mutableStateOf("") }
    var openProject by remember { mutableStateOf<PlumbProject?>(null) }
    var openMode by remember { mutableStateOf("report") } // report | control

    fun refresh() { projects = PlumbStore.loadAll(context) }

    if (openProject != null) {
        PlumbWorkspace(
            project = openProject!!,
            mode = openMode,
            color = color,
            onBack = {
                PlumbStore.upsert(context, openProject!!)
                openProject = null
                refresh()
            },
            onSave = {
                PlumbStore.upsert(context, it)
                openProject = it
                refresh()
            },
            onSwitchMode = { m -> openMode = m }
        )
        return
    }

    val filtered = remember(projects, query) {
        val q = query.trim()
        val base = if (q.isBlank()) projects
        else projects.filter {
            it.name.contains(q, true) || it.client.contains(q, true)
        }
        base.sortedByDescending { it.updatedAt }
    }

    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Column(
        Modifier.fillMaxSize().background(Background).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null, tint = color)
            }
            Text("شاقولی", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                val csv = PlumbStore.exportCsv(context)
                FileExport.exportTextToDocuments(context, "plumb_projects.csv", csv, "text/csv")
            }) { Text("CSV", color = color) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(name, { name = it }, label = { Text("نام پروژه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(client, { client = it }, label = { Text("کارفرما") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(day, { day = it }, label = { Text("روز") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
            OutlinedTextField(month, { month = it }, label = { Text("ماه") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
            OutlinedTextField(year, { year = it }, label = { Text("سال") }, modifier = Modifier.weight(1.2f), singleLine = true, keyboardOptions = numKb)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val p = PlumbProject(
                        name = name.trim(),
                        client = client.trim(),
                        reportDay = day, reportMonth = month, reportYear = year
                    )
                    PlumbStore.upsert(context, p)
                    name = ""; client = ""
                    refresh()
                    openProject = p
                    openMode = "report"
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("ثبت") }
            OutlinedButton(
                onClick = { query = name.ifBlank { client } },
                modifier = Modifier.weight(1f)
            ) { Text("جستجو") }
        }
        Spacer(Modifier.height(8.dp))
        Text("پروژه‌ها (جدید → قدیم)", color = Color(0xFFAAB697), fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { p ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E241A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(p.client, color = Color(0xFF9BA888), fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                openProject = p
                                openMode = "report"
                            }) { Text("گزارش", color = color) }
                            TextButton(
                                onClick = {
                                    if (p.columns.isEmpty() || p.letterCount == 0) return@TextButton
                                    openProject = p
                                    openMode = "control"
                                },
                                enabled = p.letterCount > 0 && p.columns.isNotEmpty()
                            ) { Text("پایش", color = Color(0xFFFFB74D)) }
                        }
                        Text(
                            "گزارش: ${p.reportYear}/${p.reportMonth}/${p.reportDay}  ·  پایش: ${p.controlYear}/${p.controlMonth}/${p.controlDay}",
                            color = Color(0xFF7A8570),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlumbWorkspace(
    project: PlumbProject,
    mode: String,
    color: Color,
    onBack: () -> Unit,
    onSave: (PlumbProject) -> Unit,
    onSwitchMode: (String) -> Unit
) {
    val context = LocalContext.current
    var p by remember { mutableStateOf(project) }
    var needsGridSetup by remember {
        mutableStateOf(p.letterCount <= 0 || p.numberCount <= 0)
    }
    var showSettings by remember { mutableStateOf(false) }
    var pickColumns by remember { mutableStateOf(false) }
    var showNeighbors by remember { mutableStateOf(false) }
    var showHeight by remember { mutableStateOf(false) }
    var showAxisEdit by remember { mutableStateOf(false) }
    var showBeforeAfter by remember { mutableStateOf(false) }
    var editColumn by remember { mutableStateOf<PlumbColumn?>(null) }
    var message by remember { mutableStateOf("") }

    var scale by remember { mutableStateOf(80f) }
    var offset by remember { mutableStateOf(Offset(80f, 120f)) }

    val numKb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val tol = p.toleranceMm()

    fun persist() {
        onSave(p)
        message = "ذخیره شد"
    }

    if (needsGridSetup) {
        var lc by remember { mutableStateOf(if (p.letterCount > 0) p.letterCount.toString() else "3") }
        var nc by remember { mutableStateOf(if (p.numberCount > 0) p.numberCount.toString() else "6") }
        var fac by remember { mutableStateOf(if (p.factor > 0) p.factor.toString() else "1") }
        var h by remember { mutableStateOf(p.heightM.toString()) }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("تعریف محورها") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(lc, { lc = it }, label = { Text("محور حروف") }, singleLine = true, keyboardOptions = numKb)
                    OutlinedTextField(nc, { nc = it }, label = { Text("محور عدد") }, singleLine = true, keyboardOptions = numKb)
                    OutlinedTextField(fac, { fac = it }, label = { Text("ضریب") }, singleLine = true, keyboardOptions = numKb)
                    OutlinedTextField(h, { h = it }, label = { Text("ارتفاع (m)") }, singleLine = true, keyboardOptions = numKb)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    p = p.copy(
                        letterCount = lc.toIntOrNull() ?: 3,
                        numberCount = nc.toIntOrNull() ?: 6,
                        factor = fac.replace(',', '.').toDoubleOrNull() ?: 1.0,
                        heightM = h.replace(',', '.').toDoubleOrNull() ?: 22.0
                    )
                    needsGridSetup = false
                    persist()
                }) { Text("تأیید") }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF12150F))) {
        // نقشه
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(8f, 400f)
                        offset += pan
                    }
                }
                .pointerInput(pickColumns, p.columns, p.letterCount, p.numberCount, scale, offset, mode) {
                    detectTapGestures { tap ->
                        val world = screenToWorld(tap, scale, offset)
                        if (pickColumns) {
                            val hitCol = hitColumn(p, world, 0.15)
                            if (hitCol != null) {
                                p = p.copy(columns = p.columns.filter { it.name != hitCol.name }.toMutableList())
                                message = "ستون ${hitCol.name} حذف شد"
                            } else {
                                val ix = nearestIntersection(p, world)
                                if (ix != null) {
                                    val (li, ni, name) = ix
                                    if (p.columns.none { it.name == name }) {
                                        p = p.copy(
                                            columns = (p.columns + PlumbColumn(name, li, ni)).toMutableList()
                                        )
                                        message = "ستون $name اضافه شد"
                                    }
                                }
                            }
                            return@detectTapGestures
                        }
                        val col = hitColumn(p, world, 0.18)
                        if (col != null) {
                            editColumn = col
                        }
                    }
                }
        ) {
            val longIsLet = p.longIsLetters()
            val longN = p.longCount()
            val crossN = p.crossCount()
            val ls = p.longSpacing()
            val cs = p.crossSpacing()
            val maxX = (longN - 1) * ls
            val maxY = (crossN - 1) * cs

            fun w2s(x: Double, y: Double): Offset =
                Offset((x * scale + offset.x).toFloat(), (-y * scale + offset.y).toFloat())

            // محورها
            if (p.axesVisible && longN > 0 && crossN > 0) {
                for (i in 0 until longN) {
                    val x = i * ls
                    val a = w2s(x, 0.0)
                    val b = w2s(x, maxY)
                    drawLine(Color(0xFF90A4AE), a, b, 2f)
                    val label = if (longIsLet) PlumbStore.letterLabel(i) else (i + 1).toString()
                    val tp = w2s(x, maxY + 1.0)
                    drawContext.canvas.nativeCanvas.drawText(
                        label, tp.x, tp.y,
                        android.graphics.Paint().apply {
                            this.color = android.graphics.Color.WHITE
                            textSize = (0.40 * scale).toFloat().coerceIn(18f, 48f)
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
                for (j in 0 until crossN) {
                    val y = j * cs
                    val a = w2s(0.0, y)
                    val b = w2s(maxX, y)
                    drawLine(Color(0xFF90A4AE), a, b, 2f)
                    val label = if (longIsLet) (j + 1).toString() else PlumbStore.letterLabel(j)
                    val tp = w2s(maxX + 1.0, y)
                    drawContext.canvas.nativeCanvas.drawText(
                        label, tp.x, tp.y,
                        android.graphics.Paint().apply {
                            this.color = android.graphics.Color.WHITE
                            textSize = (0.40 * scale).toFloat().coerceIn(18f, 48f)
                            isAntiAlias = true
                        }
                    )
                }
            }

            // همسایه / خیابان
            fun edgeLabel(n: PlumbNeighbor, pos: Offset) {
                val t = when {
                    n.street.isNotBlank() -> if (n.street.startsWith("خیابان")) n.street else "خیابان ${n.street}"
                    n.isNeighbor -> "همسایه"
                    else -> return
                }
                drawContext.canvas.nativeCanvas.drawText(
                    t, pos.x, pos.y,
                    android.graphics.Paint().apply {
                        this.color = android.graphics.Color.rgb(255, 213, 79)
                        textSize = (0.35 * scale).toFloat().coerceIn(14f, 40f)
                        isAntiAlias = true
                    }
                )
            }
            edgeLabel(p.topN, w2s(maxX / 2, maxY + 2.2))
            edgeLabel(p.bottomN, w2s(maxX / 2, -1.5))
            edgeLabel(p.rightN, w2s(maxX + 2.2, maxY / 2))
            edgeLabel(p.leftN, w2s(-2.0, maxY / 2))

            // ستون‌ها
            p.columns.forEach { col ->
                val (cx, cy) = columnWorld(p, col)
                val half = 0.10
                val tl = w2s(cx - half, cy + half)
                val br = w2s(cx + half, cy - half)
                drawRect(
                    Color(0xFF4FC3F7),
                    topLeft = Offset(min(tl.x, br.x), min(tl.y, br.y)),
                    size = androidx.compose.ui.geometry.Size(abs(br.x - tl.x), abs(br.y - tl.y)),
                    style = Stroke(2.5f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    col.name, w2s(cx, cy).x, w2s(cx, cy).y + 6f,
                    android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = (0.10 * scale).toFloat().coerceIn(10f, 28f)
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                )

                val reading = if (mode == "control") col.control else col.report
                val refMm = reading.computedRefMm()
                val lineMm = reading.computedLineMm()
                val textH = (if (showBeforeAfter) 0.15 else 0.20) * scale

                fun drawRef(mm: Double, gray: Boolean, aboveExtra: Float) {
                    val out = PlumbStore.isOutOfTol(mm, tol)
                    val colr = when {
                        gray -> android.graphics.Color.GRAY
                        out -> android.graphics.Color.RED
                        else -> android.graphics.Color.GREEN
                    }
                    val pos = w2s(cx, cy + half + 0.01)
                    val y = pos.y - aboveExtra
                    val label = if (gray) "(${fmtMm(mm)})" else fmtMm(mm)
                    drawContext.canvas.nativeCanvas.drawText(
                        label, pos.x, y,
                        android.graphics.Paint().apply {
                            this.color = colr
                            textSize = textH.toFloat().coerceIn(12f, 36f)
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                    // فلش: مثبت چپ، منفی راست
                    val dir = if (mm >= 0) -1f else 1f
                    val ay = y - textH * 0.7f
                    drawArrow(Offset(pos.x, ay), Offset(pos.x + dir * 24f, ay), Color(colr))
                }

                fun drawLineVal(mm: Double, gray: Boolean, rightExtra: Float) {
                    val out = PlumbStore.isOutOfTol(mm, tol)
                    val colr = when {
                        gray -> android.graphics.Color.GRAY
                        out -> android.graphics.Color.RED
                        else -> android.graphics.Color.GREEN
                    }
                    val pos = w2s(cx + half + 0.01, cy)
                    val x = pos.x + rightExtra
                    val label = if (gray) "(${fmtMm(mm)})" else fmtMm(mm)
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.rotate(-90f, x, pos.y)
                    drawContext.canvas.nativeCanvas.drawText(
                        label, x, pos.y,
                        android.graphics.Paint().apply {
                            this.color = colr
                            textSize = textH.toFloat().coerceIn(12f, 36f)
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                    drawContext.canvas.nativeCanvas.restore()
                    // فلش لاین: منفی بالا، مثبت پایین
                    val dir = if (mm >= 0) 1f else -1f
                    drawArrow(Offset(x + 8f, pos.y), Offset(x + 8f, pos.y + dir * 24f), Color(colr))
                }

                if (showBeforeAfter) {
                    col.report.computedRefMm()?.let { drawRef(it, true, textH * 2.2f) }
                    refMm?.let { drawRef(it, false, textH * 1.0f) }
                    col.report.computedLineMm()?.let { drawLineVal(it, true, textH * 2.2f) }
                    lineMm?.let { drawLineVal(it, false, textH * 1.0f) }
                } else {
                    refMm?.let { drawRef(it, false, textH * 1.0f) }
                    lineMm?.let { drawLineVal(it, false, textH * 1.0f) }
                }
            }
        }

        // نوار بالا
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color(0xCC1A1F16))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                persist()
                onBack()
            }) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
            Text(
                "${p.name} · ${if (mode == "report") "گزارش" else "کنترل"}",
                color = Color.White,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp
            )
            if (pickColumns) {
                TextButton(onClick = {
                    pickColumns = false
                    persist()
                    message = "ستون‌ها ثبت شد"
                }) { Text("ثبت", color = Color(0xFF81C995)) }
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, null, tint = Color.White)
            }
        }

        if (message.isNotBlank()) {
            Text(
                message,
                color = Color(0xFFB0B8A8),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
            )
        }
    }

    // تنظیمات
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("تنظیمات") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    listOf(
                        "تعیین ستون‌ها" to {
                            pickColumns = true
                            showSettings = false
                            message = "لمس تقاطع = افزودن · لمس مربع = حذف · سپس ثبت"
                        },
                        "ویرایش محور" to {
                            showAxisEdit = true
                            showSettings = false
                        },
                        if (p.axesVisible) "عدم نمایش محورها" else "نمایش محورها" to {
                            p = p.copy(axesVisible = !p.axesVisible)
                            persist()
                            showSettings = false
                        },
                        "صدور DXF" to {
                            val dxf = buildPlumbDxf(p, mode, showBeforeAfter)
                            FileExport.exportTextToDocuments(context, "${p.name}_plumb.dxf", dxf, "application/dxf")
                            showSettings = false
                            message = "DXF ذخیره شد"
                        },
                        "صدور PDF/اشتراک متن" to {
                            val txt = buildPlumbText(p)
                            FileExport.exportTextToDocuments(context, "${p.name}_plumb.txt", txt, "text/plain")
                            try {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, txt)
                                    putExtra(Intent.EXTRA_SUBJECT, p.name)
                                }
                                context.startActivity(Intent.createChooser(intent, "اشتراک گزارش شاقولی"))
                            } catch (_: Exception) {}
                            showSettings = false
                        },
                        if (mode == "report") "برو به کنترل" else "برو به گزارش" to {
                            if (mode == "report" && p.columns.isEmpty()) {
                                message = "ابتدا ستون‌ها را در گزارش تعیین کنید"
                            } else {
                                if (mode == "report") {
                                    // تاریخ کنترل اگر خالی
                                    if (p.controlYear.isBlank()) {
                                        p = p.copy(
                                            controlDay = p.reportDay,
                                            controlMonth = p.reportMonth,
                                            controlYear = p.reportYear
                                        )
                                    }
                                    persist()
                                    onSwitchMode("control")
                                } else {
                                    onSwitchMode("report")
                                }
                            }
                            showSettings = false
                        },
                        "قبل و بعد" to {
                            showBeforeAfter = !showBeforeAfter
                            showSettings = false
                            message = if (showBeforeAfter) "نمایش قبل و بعد" else "نمایش تک‌حالت"
                        },
                        "محل خیابان / همسایه" to {
                            showNeighbors = true
                            showSettings = false
                        },
                        "ارتفاع" to {
                            showHeight = true
                            showSettings = false
                        },
                        "ریست" to {
                            // confirm via second dialog simplified
                            p = p.copy(
                                letterCount = 0, numberCount = 0, factor = 1.0,
                                columns = mutableListOf(), heightM = 22.0
                            )
                            needsGridSetup = true
                            persist()
                            showSettings = false
                        }
                    ).forEach { (label, act) ->
                        TextButton(onClick = act, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("بستن") }
            }
        )
    }

    if (showAxisEdit) {
        var lc by remember { mutableStateOf(p.letterCount.toString()) }
        var nc by remember { mutableStateOf(p.numberCount.toString()) }
        var fac by remember { mutableStateOf(p.factor.toString()) }
        AlertDialog(
            onDismissRequest = { showAxisEdit = false },
            title = { Text("ویرایش محور") },
            text = {
                Column {
                    OutlinedTextField(lc, { lc = it }, label = { Text("محور حروف") }, keyboardOptions = numKb, singleLine = true)
                    OutlinedTextField(nc, { nc = it }, label = { Text("محور عدد") }, keyboardOptions = numKb, singleLine = true)
                    OutlinedTextField(fac, { fac = it }, label = { Text("ضریب") }, keyboardOptions = numKb, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    p = p.copy(
                        letterCount = lc.toIntOrNull() ?: p.letterCount,
                        numberCount = nc.toIntOrNull() ?: p.numberCount,
                        factor = fac.replace(',', '.').toDoubleOrNull() ?: p.factor
                    )
                    // حذف ستون‌های خارج از محدوده
                    p = p.copy(columns = p.columns.filter {
                        it.letterIdx < p.letterCount && it.numberIdx < p.numberCount
                    }.toMutableList())
                    persist()
                    showAxisEdit = false
                }) { Text("اعمال") }
            },
            dismissButton = { TextButton(onClick = { showAxisEdit = false }) { Text("انصراف") } }
        )
    }

    if (showHeight) {
        var h by remember { mutableStateOf(p.heightM.toString()) }
        AlertDialog(
            onDismissRequest = { showHeight = false },
            title = { Text("ارتفاع (m)") },
            text = {
                OutlinedTextField(h, { h = it }, keyboardOptions = numKb, singleLine = true)
                Text("حد مجاز ≈ ${String.format(Locale.US, "%.0f", (h.replace(',', '.').toDoubleOrNull() ?: 22.0) / 600.0 * 1000)} mm", fontSize = 12.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    p = p.copy(heightM = h.replace(',', '.').toDoubleOrNull() ?: 22.0)
                    persist()
                    showHeight = false
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { showHeight = false }) { Text("انصراف") } }
        )
    }

    if (showNeighbors) {
        var topS by remember { mutableStateOf(p.topN.street) }
        var topN by remember { mutableStateOf(p.topN.isNeighbor) }
        var botS by remember { mutableStateOf(p.bottomN.street) }
        var botN by remember { mutableStateOf(p.bottomN.isNeighbor) }
        var rightS by remember { mutableStateOf(p.rightN.street) }
        var rightN by remember { mutableStateOf(p.rightN.isNeighbor) }
        var leftS by remember { mutableStateOf(p.leftN.street) }
        var leftN by remember { mutableStateOf(p.leftN.isNeighbor) }
        AlertDialog(
            onDismissRequest = { showNeighbors = false },
            title = { Text("خیابان / همسایه") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fun row(title: String, n: Boolean, onN: (Boolean) -> Unit, s: String, onS: (String) -> Unit) {
                        Text(title, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(n, onN)
                            Text("همسایه")
                            OutlinedTextField(s, onS, label = { Text("خیابان") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                    row("بالا", topN, { topN = it }, topS, { topS = it })
                    row("پایین", botN, { botN = it }, botS, { botS = it })
                    row("راست", rightN, { rightN = it }, rightS, { rightS = it })
                    row("چپ", leftN, { leftN = it }, leftS, { leftS = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    p = p.copy(
                        topN = PlumbNeighbor(topN, topS),
                        bottomN = PlumbNeighbor(botN, botS),
                        rightN = PlumbNeighbor(rightN, rightS),
                        leftN = PlumbNeighbor(leftN, leftS)
                    )
                    persist()
                    showNeighbors = false
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { showNeighbors = false }) { Text("انصراف") } }
        )
    }

    editColumn?.let { col ->
        val reading = if (mode == "control") col.control else col.report
        var refB by remember(col.name, mode) { mutableStateOf(reading.refBottom?.toString() ?: "") }
        var refT by remember(col.name, mode) { mutableStateOf(reading.refTop?.toString() ?: "") }
        var refV by remember(col.name, mode) { mutableStateOf(reading.refValueMm?.toString() ?: "") }
        var lineB by remember(col.name, mode) { mutableStateOf(reading.lineBottom?.toString() ?: "") }
        var lineT by remember(col.name, mode) { mutableStateOf(reading.lineTop?.toString() ?: "") }
        var lineV by remember(col.name, mode) { mutableStateOf(reading.lineValueMm?.toString() ?: "") }

        fun d(s: String) = s.replace(',', '.').toDoubleOrNull()
        // auto delta
        LaunchedEffect(refB, refT) {
            val b = d(refB); val t = d(refT)
            if (b != null && t != null && refV.isBlank()) {
                // show computed in placeholder only via derived
            }
        }

        AlertDialog(
            onDismissRequest = { editColumn = null },
            title = { Text("ستون ${col.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("رفرنس (m) — مقدار mm", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(refT, { refT = it }, label = { Text("بالا") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                        OutlinedTextField(refB, { refB = it }, label = { Text("پایین") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                        OutlinedTextField(
                            refV.ifBlank {
                                val b = d(refB); val t = d(refT)
                                if (b != null && t != null) String.format(Locale.US, "%.1f", (t - b) * 1000) else ""
                            },
                            { refV = it },
                            label = { Text("مقدار") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = numKb
                        )
                    }
                    Text("لاین (m) — مقدار mm", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(lineT, { lineT = it }, label = { Text("بالا") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                        OutlinedTextField(lineB, { lineB = it }, label = { Text("پایین") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                        OutlinedTextField(
                            lineV.ifBlank {
                                val b = d(lineB); val t = d(lineT)
                                if (b != null && t != null) String.format(Locale.US, "%.1f", (t - b) * 1000) else ""
                            },
                            { lineV = it },
                            label = { Text("مقدار") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = numKb
                        )
                    }
                    Text("حد مجاز ±${String.format(Locale.US, "%.0f", tol)} mm", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val rb = d(refB); val rt = d(refT)
                    val lb = d(lineB); val lt = d(lineT)
                    var rv = d(refV)
                    var lv = d(lineV)
                    if (rv == null && rb != null && rt != null) rv = (rt - rb) * 1000
                    if (lv == null && lb != null && lt != null) lv = (lt - lb) * 1000
                    val newR = PlumbReading(rb, rt, lb, lt, rv, lv)
                    p = p.copy(
                        columns = p.columns.map {
                            if (it.name != col.name) it
                            else if (mode == "control") it.copy(control = newR) else it.copy(report = newR)
                        }.toMutableList()
                    )
                    if (mode == "control" && p.controlYear.isBlank()) {
                        p = p.copy(controlDay = dayNow(), controlMonth = monthNow(), controlYear = yearNow())
                    }
                    persist()
                    editColumn = null
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { editColumn = null }) { Text("انصراف") } }
        )
    }
}

private fun dayNow() = ""
private fun monthNow() = ""
private fun yearNow() = ""

private fun fmtMm(v: Double) = String.format(Locale.US, "%.0f", v)

private fun screenToWorld(tap: Offset, scale: Float, offset: Offset): Pair<Double, Double> {
    val x = (tap.x - offset.x) / scale
    val y = -(tap.y - offset.y) / scale
    return x.toDouble() to y.toDouble()
}

private fun columnWorld(p: PlumbProject, col: PlumbColumn): Pair<Double, Double> {
    val longIsLet = p.longIsLetters()
    val ls = p.longSpacing()
    val cs = p.crossSpacing()
    return if (longIsLet) {
        col.letterIdx * ls to col.numberIdx * cs
    } else {
        col.numberIdx * ls to col.letterIdx * cs
    }
}

private fun nearestIntersection(p: PlumbProject, world: Pair<Double, Double>): Triple<Int, Int, String>? {
    if (p.letterCount <= 0 || p.numberCount <= 0) return null
    val longIsLet = p.longIsLetters()
    val ls = p.longSpacing()
    val cs = p.crossSpacing()
    var best: Triple<Int, Int, String>? = null
    var bestD = 0.25
    for (li in 0 until p.letterCount) {
        for (ni in 0 until p.numberCount) {
            val (x, y) = if (longIsLet) li * ls to ni * cs else ni * ls to li * cs
            val d = hypot(world.first - x, world.second - y)
            if (d < bestD) {
                bestD = d
                val name = PlumbStore.letterLabel(li) + (ni + 1)
                best = Triple(li, ni, name)
            }
        }
    }
    return best
}

private fun hitColumn(p: PlumbProject, world: Pair<Double, Double>, tol: Double): PlumbColumn? {
    var best: PlumbColumn? = null
    var bestD = tol
    p.columns.forEach { c ->
        val (x, y) = columnWorld(p, c)
        val d = hypot(world.first - x, world.second - y)
        if (d < bestD) {
            bestD = d
            best = c
        }
    }
    return best
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(from: Offset, to: Offset, color: Color) {
    drawLine(color, from, to, 3f)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
    val ux = dx / len
    val uy = dy / len
    val px = -uy
    val py = ux
    val tip = to
    val left = Offset(tip.x - ux * 10f + px * 5f, tip.y - uy * 10f + py * 5f)
    val right = Offset(tip.x - ux * 10f - px * 5f, tip.y - uy * 10f - py * 5f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path, color)
}

private fun buildPlumbText(p: PlumbProject): String {
    val sb = StringBuilder()
    sb.appendLine("پروژه: ${p.name}")
    sb.appendLine("کارفرما: ${p.client}")
    sb.appendLine("ارتفاع: ${p.heightM} m · حد: ±${String.format(Locale.US, "%.0f", p.toleranceMm())} mm")
    sb.appendLine("---")
    p.columns.forEach { c ->
        sb.appendLine(c.name)
        sb.appendLine("  گزارش رفرنس: ${c.report.computedRefMm()} mm  لاین: ${c.report.computedLineMm()} mm")
        sb.appendLine("  کنترل رفرنس: ${c.control.computedRefMm()} mm  لاین: ${c.control.computedLineMm()} mm")
    }
    return sb.toString()
}

private fun buildPlumbDxf(p: PlumbProject, mode: String, beforeAfter: Boolean): String {
    val sb = StringBuilder()
    fun pair(c: Int, v: String) { sb.append(c).append('\n').append(v).append('\n') }
    fun pair(c: Int, v: Double) { pair(c, String.format(Locale.US, "%.4f", v)) }
    pair(0, "SECTION"); pair(2, "HEADER"); pair(0, "ENDSEC")
    pair(0, "SECTION"); pair(2, "TABLES"); pair(0, "ENDSEC")
    pair(0, "SECTION"); pair(2, "ENTITIES")
    val longIsLet = p.longIsLetters()
    val longN = p.longCount()
    val crossN = p.crossCount()
    val ls = p.longSpacing()
    val cs = p.crossSpacing()
    val maxX = (longN - 1) * ls
    val maxY = (crossN - 1) * cs
    if (p.axesVisible) {
        for (i in 0 until longN) {
            val x = i * ls
            pair(0, "LINE"); pair(8, "AXES"); pair(10, x); pair(20, 0.0); pair(11, x); pair(21, maxY)
        }
        for (j in 0 until crossN) {
            val y = j * cs
            pair(0, "LINE"); pair(8, "AXES"); pair(10, 0.0); pair(20, y); pair(11, maxX); pair(21, y)
        }
    }
    p.columns.forEach { col ->
        val (cx, cy) = columnWorld(p, col)
        val h = 0.10
        pair(0, "LINE"); pair(8, "COL"); pair(10, cx - h); pair(20, cy - h); pair(11, cx + h); pair(21, cy - h)
        pair(0, "LINE"); pair(8, "COL"); pair(10, cx + h); pair(20, cy - h); pair(11, cx + h); pair(21, cy + h)
        pair(0, "LINE"); pair(8, "COL"); pair(10, cx + h); pair(20, cy + h); pair(11, cx - h); pair(21, cy + h)
        pair(0, "LINE"); pair(8, "COL"); pair(10, cx - h); pair(20, cy + h); pair(11, cx - h); pair(21, cy - h)
        pair(0, "TEXT"); pair(8, "COL"); pair(10, cx); pair(20, cy); pair(40, 0.10); pair(1, col.name)
        val r = if (mode == "control") col.control else col.report
        r.computedRefMm()?.let {
            pair(0, "TEXT"); pair(8, "VAL"); pair(10, cx); pair(20, cy + 0.25); pair(40, 0.20); pair(1, fmtMm(it))
        }
        r.computedLineMm()?.let {
            pair(0, "TEXT"); pair(8, "VAL"); pair(10, cx + 0.25); pair(20, cy); pair(40, 0.20); pair(1, fmtMm(it))
        }
    }
    pair(0, "ENDSEC"); pair(0, "EOF")
    return sb.toString()
}
