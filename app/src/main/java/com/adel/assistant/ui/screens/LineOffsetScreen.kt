package com.adel.assistant.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.ReportHub
import com.adel.assistant.data.LineOffsetProject
import com.adel.assistant.data.LineOffsetStore
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.appearanceCardColor
import com.adel.assistant.ui.appearanceScreenBg
import com.adel.assistant.ui.appearanceTextMuted
import com.adel.assistant.ui.appearanceTextPrimary
import com.adel.assistant.ui.appearanceTextSecondary
import com.adel.assistant.ui.components.AdelOutlinedTextField
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private data class LoPoint(val x: Double, val y: Double)
private data class LoCol(
    val pi: Int, val qi: Int,
    val pLabel: String, val qLabel: String,
    val station: Double, val offset: Double,
    val x: Double, val y: Double
)
private data class LoRect(
    val key: String,
    val x: Double, val y: Double,
    val lenCm: Double, val widCm: Double,
    val alongNumeric: Boolean,
    val isColumn: Boolean, // true=column red, false=plate white
    val label: String
)

private fun letterLabel(i: Int): String {
    var n = i
    val sb = StringBuilder()
    do {
        sb.insert(0, ('A' + n % 26))
        n = n / 26 - 1
    } while (n >= 0)
    return sb.toString()
}

@Composable
fun LineOffsetScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    // list | form | map
    var mode by remember { mutableStateOf("list") }
    var tick by remember { mutableStateOf(0) }
    val projects = remember(tick) { LineOffsetStore.all(context) }

    var projectId by remember { mutableStateOf<String?>(null) }
    var projectName by remember { mutableStateOf("") }
    var x1 by remember { mutableStateOf("") }
    var y1 by remember { mutableStateOf("") }
    var x2 by remember { mutableStateOf("") }
    var y2 by remember { mutableStateOf("") }
    var firstOffset by remember { mutableStateOf("") }
    var firstLine by remember { mutableStateOf("") }
    var perpLength by remember { mutableStateOf("") }
    var parallelCount by remember { mutableStateOf("") }
    var perpCount by remember { mutableStateOf("") }
    var parallelIsLetter by remember { mutableStateOf(true) }
    var parSpaces by remember { mutableStateOf(listOf<String>()) }
    var perSpaces by remember { mutableStateOf(listOf<String>()) }
    var status by remember { mutableStateOf("") }

    // map state
    var cols by remember { mutableStateOf(listOf<LoCol>()) }
    var baseline by remember { mutableStateOf(LoPoint(0.0, 0.0) to LoPoint(0.0, 1.0)) }
    var parSegs by remember { mutableStateOf(listOf<Pair<LoPoint, LoPoint>>()) }
    var perSegs by remember { mutableStateOf(listOf<Pair<LoPoint, LoPoint>>()) }
    var plateParSegs by remember { mutableStateOf(listOf<Pair<LoPoint, LoPoint>>()) }
    var platePerSegs by remember { mutableStateOf(listOf<Pair<LoPoint, LoPoint>>()) }
    var plateParOff by remember { mutableStateOf(listOf<String>()) }
    var platePerOff by remember { mutableStateOf(listOf<String>()) }
    var rects by remember { mutableStateOf(listOf<LoRect>()) }
    var selected by remember { mutableStateOf<LoCol?>(null) }
    var scale by remember { mutableStateOf(40f) }
    var offset by remember { mutableStateOf(Offset(80f, 120f)) }
    var showGear by remember { mutableStateOf(false) }
    var showPlateDlg by remember { mutableStateOf(false) }
    var showAxisVis by remember { mutableStateOf(false) }
    var showColPlateVis by remember { mutableStateOf(false) }
    var showColAxes by remember { mutableStateOf(true) }
    var showPlateAxes by remember { mutableStateOf(true) }
    var showColumns by remember { mutableStateOf(true) }
    var showPlates by remember { mutableStateOf(true) }
    var pickColumnMode by remember { mutableStateOf(false) }
    var measureMode by remember { mutableStateOf(false) }
    var measureA by remember { mutableStateOf<LoPoint?>(null) }
    var measureB by remember { mutableStateOf<LoPoint?>(null) }
    var measureMsg by remember { mutableStateOf<String?>(null) }
    var colDlg by remember { mutableStateOf<LoCol?>(null) }
    var colCount by remember { mutableStateOf("") }
    var colType by remember { mutableStateOf("") }
    var colAlongNumeric by remember { mutableStateOf(true) }
    var plateLen by remember { mutableStateOf("") }
    var plateWid by remember { mutableStateOf("") }
    var plateAlongNumeric by remember { mutableStateOf(true) }

    fun pLabel(i: Int) = if (parallelIsLetter) letterLabel(i) else (i + 1).toString()
    fun qLabel(j: Int) = if (parallelIsLetter) (j + 1).toString() else letterLabel(j)

    fun syncSpaceFields() {
        val np = (parallelCount.toIntOrNullFa() ?: 0).coerceIn(0, 40)
        val nq = (perpCount.toIntOrNullFa() ?: 0).coerceIn(0, 40)
        if (np >= 2) {
            while (parSpaces.size < np - 1) parSpaces = parSpaces + ""
            if (parSpaces.size > np - 1) parSpaces = parSpaces.take(np - 1)
        } else parSpaces = emptyList()
        if (nq >= 2) {
            while (perSpaces.size < nq - 1) perSpaces = perSpaces + ""
            if (perSpaces.size > nq - 1) perSpaces = perSpaces.take(nq - 1)
        } else perSpaces = emptyList()
    }

    fun loadProject(p: LineOffsetProject) {
        projectId = p.id
        projectName = p.name
        x1 = p.x1.toString(); y1 = p.y1.toString()
        x2 = p.x2.toString(); y2 = p.y2.toString()
        firstOffset = p.firstOffset.toString()
        firstLine = p.firstLine.toString()
        perpLength = p.perpLength.toString()
        parallelCount = p.parallelCount.toString()
        perpCount = p.perpCount.toString()
        parallelIsLetter = p.parallelIsLetter
        parSpaces = p.parallelSpacings.map { it.toString() }
        perSpaces = p.perpSpacings.map { it.toString() }
        plateParOff = p.plateParallelOffsets.map { it.toString() }
        platePerOff = p.platePerpOffsets.map { it.toString() }
        mode = "form"
    }

    fun buildGeometry(): Boolean {
        val p1x = x1.toDoubleOrNullFa(); val p1y = y1.toDoubleOrNullFa()
        val p2x = x2.toDoubleOrNullFa(); val p2y = y2.toDoubleOrNullFa()
        if (p1x == null || p1y == null || p2x == null || p2y == null) {
            status = "مختصات مبنا کامل نیست"; return false
        }
        val o0 = firstOffset.toDoubleOrNullFa()
        val s0 = firstLine.toDoubleOrNullFa()
        val pLen = perpLength.toDoubleOrNullFa()
        val nPar = parallelCount.toIntOrNullFa()
        val nPer = perpCount.toIntOrNullFa()
        if (o0 == null || s0 == null || pLen == null || nPar == null || nPer == null || nPar < 1 || nPer < 1) {
            status = "فیلدهای محور اول / تعداد را کامل کنید"; return false
        }
        val dPars = mutableListOf<Double>()
        for (i in 0 until (nPar - 1)) {
            val v = parSpaces.getOrNull(i)?.toDoubleOrNullFa()
            if (v == null) { status = "فاصله محور موازی ${i + 1} را وارد کنید"; return false }
            dPars += v
        }
        val dPers = mutableListOf<Double>()
        for (i in 0 until (nPer - 1)) {
            val v = perSpaces.getOrNull(i)?.toDoubleOrNullFa()
            if (v == null) { status = "فاصله محور عمود ${i + 1} را وارد کنید"; return false }
            dPers += v
        }
        val p1 = LoPoint(p1x, p1y); val p2 = LoPoint(p2x, p2y)
        val dx = p2.x - p1.x; val dy = p2.y - p1.y
        val len = hypot(dx, dy)
        if (len < 1e-9) { status = "نقطه ۱ و ۲ یکی هستند"; return false }
        val ux = dx / len; val uy = dy / len
        // راست = (uy, -ux) ؛ مثبت آفست به راست، منفی به چپ
        val nx = uy; val ny = -ux

        fun world(st: Double, off: Double) =
            LoPoint(p1.x + st * ux + off * nx, p1.y + st * uy + off * ny)

        val offsets = mutableListOf(o0)
        dPars.forEach { offsets += offsets.last() + it }
        val stations = mutableListOf(s0)
        dPers.forEach { stations += stations.last() + it }
        val sStart = min(stations.minOrNull() ?: s0, s0)
        val sEnd = max(stations.maxOrNull() ?: s0, s0 + pLen)
        val oMin = offsets.minOrNull() ?: o0
        val oMax = offsets.maxOrNull() ?: o0

        parSegs = offsets.map { off -> world(sStart, off) to world(sEnd, off) }
        perSegs = stations.map { st -> world(st, oMin) to world(st, oMax) }
        baseline = p1 to p2

        // plate axes (relative offset to each column axis)
        while (plateParOff.size < nPar) plateParOff = plateParOff + ""
        while (platePerOff.size < nPer) platePerOff = platePerOff + ""
        val plateOffsets = offsets.mapIndexed { i, baseOff ->
            baseOff + (plateParOff.getOrNull(i)?.toDoubleOrNullFa() ?: 0.0)
        }
        val plateStations = stations.mapIndexed { j, baseSt ->
            baseSt + (platePerOff.getOrNull(j)?.toDoubleOrNullFa() ?: 0.0)
        }
        val hasPlate = plateParOff.any { it.toDoubleOrNullFa() != null && it.toDoubleOrNullFa() != 0.0 } ||
            platePerOff.any { it.toDoubleOrNullFa() != null && it.toDoubleOrNullFa() != 0.0 }
        if (hasPlate) {
            val poMin = plateOffsets.minOrNull() ?: oMin
            val poMax = plateOffsets.maxOrNull() ?: oMax
            plateParSegs = plateOffsets.map { off -> world(sStart, off) to world(sEnd, off) }
            platePerSegs = plateStations.map { st -> world(st, poMin) to world(st, poMax) }
        } else {
            plateParSegs = emptyList(); platePerSegs = emptyList()
        }

        cols = buildList {
            for (i in offsets.indices) for (j in stations.indices) {
                val w = world(stations[j], offsets[i])
                add(
                    LoCol(
                        i, j, pLabel(i), qLabel(j),
                        stations[j], offsets[i], w.x, w.y
                    )
                )
            }
        }
        return true
    }

    fun saveProject() {
        if (!buildGeometry()) return
        val id = projectId ?: LineOffsetStore.nextId(context)
        val p = LineOffsetProject(
            id = id,
            name = projectName.ifBlank { "پروژه $id" },
            x1 = x1.toDoubleOrNullFa() ?: 0.0,
            y1 = y1.toDoubleOrNullFa() ?: 0.0,
            x2 = x2.toDoubleOrNullFa() ?: 0.0,
            y2 = y2.toDoubleOrNullFa() ?: 0.0,
            firstOffset = firstOffset.toDoubleOrNullFa() ?: 0.0,
            firstLine = firstLine.toDoubleOrNullFa() ?: 0.0,
            perpLength = perpLength.toDoubleOrNullFa() ?: 0.0,
            parallelCount = parallelCount.toIntOrNullFa() ?: 1,
            perpCount = perpCount.toIntOrNullFa() ?: 1,
            parallelIsLetter = parallelIsLetter,
            parallelSpacings = parSpaces.map { it.toDoubleOrNullFa() ?: 0.0 },
            perpSpacings = perSpaces.map { it.toDoubleOrNullFa() ?: 0.0 },
            plateParallelOffsets = plateParOff.map { it.toDoubleOrNullFa() ?: 0.0 },
            platePerpOffsets = platePerOff.map { it.toDoubleOrNullFa() ?: 0.0 }
        )
        LineOffsetStore.save(context, p)
        projectId = id
        tick++
        status = "ذخیره شد"
    }

    fun openMap() {
        if (!buildGeometry()) return
        saveProject()
        mode = "map"
        selected = null
    }

    fun exportDxf() {
        if (!buildGeometry()) return
        val sb = StringBuilder()
        fun ent(type: String, layer: String, color: Int, body: String) {
            sb.append("0\n$type\n8\n$layer\n62\n$color\n").append(body)
        }
        sb.append("0\nSECTION\n2\nENTITIES\n")
        val (b1, b2) = baseline
        ent("LINE", "BASE", 3, "10\n${b1.x}\n20\n${b1.y}\n11\n${b2.x}\n21\n${b2.y}\n")
        parSegs.forEach { (a, b) ->
            ent("LINE", "COL_AXIS", 8, "10\n${a.x}\n20\n${a.y}\n11\n${b.x}\n21\n${b.y}\n")
        }
        perSegs.forEach { (a, b) ->
            ent("LINE", "COL_AXIS", 8, "10\n${a.x}\n20\n${a.y}\n11\n${b.x}\n21\n${b.y}\n")
        }
        plateParSegs.forEach { (a, b) ->
            ent("LINE", "PLATE_AXIS", 5, "10\n${a.x}\n20\n${a.y}\n11\n${b.x}\n21\n${b.y}\n")
        }
        platePerSegs.forEach { (a, b) ->
            ent("LINE", "PLATE_AXIS", 5, "10\n${a.x}\n20\n${a.y}\n11\n${b.x}\n21\n${b.y}\n")
        }
        // rectangles as 4 lines - approximate axis-aligned in local frame is complex; store as polyline points in world using rotation of baseline
        val p1 = baseline.first; val p2 = baseline.second
        val dx = p2.x - p1.x; val dy = p2.y - p1.y
        val len = hypot(dx, dy).coerceAtLeast(1e-9)
        val ux = dx / len; val uy = dy / len
        val nx = uy; val ny = -ux
        rects.forEach { r ->
            val hl = (r.lenCm / 100.0) / 2.0
            val hw = (r.widCm / 100.0) / 2.0
            // local axes: along numeric uses station dir, else offset dir
            val ax = if (r.alongNumeric) ux else nx
            val ay = if (r.alongNumeric) uy else ny
            val bx = if (r.alongNumeric) nx else ux
            val by = if (r.alongNumeric) ny else uy
            val corners = listOf(
                LoPoint(r.x - ax * hl - bx * hw, r.y - ay * hl - by * hw),
                LoPoint(r.x + ax * hl - bx * hw, r.y + ay * hl - by * hw),
                LoPoint(r.x + ax * hl + bx * hw, r.y + ay * hl + by * hw),
                LoPoint(r.x - ax * hl + bx * hw, r.y - ay * hl + by * hw)
            )
            val layer = if (r.isColumn) "COLUMN" else "PLATE"
            val col = if (r.isColumn) 1 else 7 // red / white
            for (i in 0..3) {
                val a = corners[i]; val b = corners[(i + 1) % 4]
                ent("LINE", layer, col, "10\n${a.x}\n20\n${a.y}\n11\n${b.x}\n21\n${b.y}\n")
            }
        }
        sb.append("0\nENDSEC\n0\nEOF\n")
        try {
            ReportHub.shareTextFile(context, "line_offset_${projectId ?: "new"}.dxf", sb.toString(), "application/dxf", "DXF لاین آف‌ست")
            status = "DXF آماده اشتراک"
        } catch (e: Exception) {
            status = "خطا DXF: ${e.message}"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(appearanceScreenBg())
            .padding(horizontal = 12.dp)
    ) {
        ScreenTopBar(
            title = when (mode) {
                "list" -> "لاین آف‌ست — پروژه‌ها"
                "form" -> "لاین آف‌ست — ثبت"
                else -> "لاین آف‌ست — نقشه"
            },
            color = color,
            onBack = {
                when (mode) {
                    "map" -> mode = "form"
                    "form" -> mode = "list"
                    else -> onBack()
                }
            }
        )
        Spacer(Modifier.height(6.dp))

        when (mode) {
            "list" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("پروژه‌های ذخیره‌شده", color = appearanceTextSecondary(), fontSize = 12.sp)
                    IconButton(onClick = {
                        projectId = null; projectName = ""
                        x1 = ""; y1 = ""; x2 = ""; y2 = ""
                        firstOffset = ""; firstLine = ""; perpLength = ""
                        parallelCount = ""; perpCount = ""
                        parSpaces = emptyList(); perSpaces = emptyList()
                        plateParOff = emptyList(); platePerOff = emptyList()
                        rects = emptyList()
                        mode = "form"
                    }) { Icon(Icons.Filled.Add, null, tint = color) }
                }
                if (projects.isEmpty()) {
                    Text("پروژه‌ای نیست — دکمه + برای جدید", color = appearanceTextMuted(), fontSize = 13.sp)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(projects, key = { it.id }) { p ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = appearanceCardColor(),
                            modifier = Modifier.fillMaxWidth().clickable { loadProject(p) }
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name.ifBlank { "پروژه ${p.id}" }, color = appearanceTextPrimary(), fontWeight = FontWeight.Bold)
                                    Text(
                                        "موازی ${p.parallelCount} · عمود ${p.perpCount}",
                                        color = appearanceTextSecondary(), fontSize = 11.sp
                                    )
                                }
                                IconButton(onClick = {
                                    LineOffsetStore.delete(context, p.id); tick++
                                }) {
                                    Icon(Icons.Filled.Delete, null, tint = Color(0xFFCF6679))
                                }
                            }
                        }
                    }
                }
            }

            "form" -> {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AdelOutlinedTextField(
                        projectName, { projectName = it },
                        label = { Text("نام پروژه") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Text("خط مبنا", color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdelOutlinedTextField(x1, { x1 = it }, label = { Text("X1") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                        AdelOutlinedTextField(y1, { y1 = it }, label = { Text("Y1") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdelOutlinedTextField(x2, { x2 = it }, label = { Text("X2") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                        AdelOutlinedTextField(y2, { y2 = it }, label = { Text("Y2") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                    }

                    Text("محور اول — موازی", color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AdelOutlinedTextField(
                            firstOffset, { firstOffset = it },
                            label = { Text("آفست اول (+راست/-چپ)") },
                            modifier = Modifier.weight(1.2f), singleLine = true, keyboardOptions = numKb
                        )
                        AdelOutlinedTextField(
                            parallelCount, {
                                parallelCount = it
                                syncSpaceFields()
                            },
                            label = { Text("تعداد") },
                            modifier = Modifier.weight(0.7f), singleLine = true, keyboardOptions = numKb
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = parallelIsLetter, onCheckedChange = { parallelIsLetter = it })
                        Text("حروفی", color = appearanceTextPrimary(), fontSize = 12.sp)
                        Checkbox(checked = !parallelIsLetter, onCheckedChange = { parallelIsLetter = !it })
                        Text("عددی", color = appearanceTextPrimary(), fontSize = 12.sp)
                    }

                    Text("محور اول — عمود", color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdelOutlinedTextField(
                            firstLine, { firstLine = it },
                            label = { Text("لاین اول") },
                            modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
                        )
                        AdelOutlinedTextField(
                            perpLength, { perpLength = it },
                            label = { Text("طول محور") },
                            modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
                        )
                        AdelOutlinedTextField(
                            perpCount, {
                                perpCount = it
                                syncSpaceFields()
                            },
                            label = { Text("تعداد") },
                            modifier = Modifier.weight(0.8f), singleLine = true, keyboardOptions = numKb
                        )
                    }

                    if (parSpaces.isNotEmpty()) {
                        Text("فواصل محورهای موازی", color = appearanceTextSecondary(), fontSize = 12.sp)
                        parSpaces.forEachIndexed { i, v ->
                            AdelOutlinedTextField(
                                v, { nv ->
                                    parSpaces = parSpaces.toMutableList().also { it[i] = nv }
                                },
                                label = { Text("فاصله ${pLabel(i)} تا ${pLabel(i + 1)}") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb
                            )
                        }
                    }
                    if (perSpaces.isNotEmpty()) {
                        Text("فواصل محورهای عمود", color = appearanceTextSecondary(), fontSize = 12.sp)
                        perSpaces.forEachIndexed { i, v ->
                            AdelOutlinedTextField(
                                v, { nv ->
                                    perSpaces = perSpaces.toMutableList().also { it[i] = nv }
                                },
                                label = { Text("فاصله ${qLabel(i)} تا ${qLabel(i + 1)}") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb
                            )
                        }
                    }

                    if (status.isNotBlank()) Text(status, color = Color(0xFFFFB74D), fontSize = 12.sp)
                    Button(
                        onClick = { openMap() },
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("نمایش نقشه") }
                    OutlinedButton(onClick = { saveProject() }, modifier = Modifier.fillMaxWidth()) {
                        Text("ذخیره پروژه")
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            else -> {
                // MAP
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        projectName.ifBlank { "نقشه" },
                        color = appearanceTextSecondary(),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { showGear = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "تنظیمات", tint = color)
                        }
                        DropdownMenu(expanded = showGear, onDismissRequest = { showGear = false }) {
                            DropdownMenuItem(
                                text = { Text("ویرایش محور ستون‌ها") },
                                onClick = { showGear = false; mode = "form" }
                            )
                            DropdownMenuItem(
                                text = { Text("ویرایش محور صفحه پلیت") },
                                onClick = { showGear = false; showPlateDlg = true }
                            )
                            DropdownMenuItem(
                                text = { Text("نمایش محورها") },
                                onClick = { showGear = false; showAxisVis = true }
                            )
                            DropdownMenuItem(
                                text = { Text("ثبت موقعیت ستون‌ها") },
                                onClick = {
                                    showGear = false
                                    pickColumnMode = true
                                    status = "یک تقاطع را انتخاب کنید"
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("نمایش ستون / صفحه پلیت") },
                                onClick = { showGear = false; showColPlateVis = true }
                            )
                            DropdownMenuItem(
                                text = { Text(if (measureMode) "خاموش اندازه‌گذاری" else "اندازه‌گذاری") },
                                onClick = {
                                    showGear = false
                                    measureMode = !measureMode
                                    measureA = null; measureB = null; measureMsg = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("خروجی DXF") },
                                onClick = { showGear = false; exportDxf() }
                            )
                        }
                    }
                }
                if (status.isNotBlank()) Text(status, color = Color(0xFFFFB74D), fontSize = 11.sp)
                measureMsg?.let { Text(it, color = appearanceTextPrimary(), fontSize = 12.sp) }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF1A1F14), RoundedCornerShape(10.dp))
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(2f, 800f)
                                    offset += pan
                                }
                            }
                            .pointerInput(cols, scale, offset, pickColumnMode, measureMode, rects) {
                                detectTapGestures { tap ->
                                    fun toScreen(x: Double, y: Double) =
                                        Offset((x * scale + offset.x).toFloat(), (-y * scale + offset.y).toFloat())
                                    fun nearestCol(): LoCol? {
                                        var best: LoCol? = null
                                        var bestD = 36f
                                        cols.forEach { c ->
                                            val s = toScreen(c.x, c.y)
                                            val d = hypot((s.x - tap.x).toDouble(), (s.y - tap.y).toDouble()).toFloat()
                                            if (d < bestD) {
                                                bestD = d; best = c
                                            }
                                        }
                                        return best
                                    }
                                    if (measureMode) {
                                        val c = nearestCol()
                                        if (c != null) {
                                            val pt = LoPoint(c.x, c.y)
                                            if (measureA == null) {
                                                measureA = pt; measureMsg = "نقطه اول انتخاب شد"
                                            } else {
                                                measureB = pt
                                                val dist = hypot(pt.x - measureA!!.x, pt.y - measureA!!.y)
                                                measureMsg = "فاصله: ${"%.3f".format(dist)} m"
                                                measureA = null
                                            }
                                        }
                                        return@detectTapGestures
                                    }
                                    val c = nearestCol() ?: return@detectTapGestures
                                    if (pickColumnMode) {
                                        colDlg = c
                                        colCount = ""; colType = ""; plateLen = ""; plateWid = ""
                                    } else {
                                        selected = c
                                    }
                                }
                            }
                    ) {
                        fun toScreen(x: Double, y: Double) =
                            Offset((x * scale + offset.x).toFloat(), (-y * scale + offset.y).toFloat())
                        val gray = Color(0xFF9E9E9E)
                        val plateBlue = Color(0xFF64B5F6).copy(alpha = 0.7f)
                        if (showColAxes) {
                            parSegs.forEach { (a, b) ->
                                drawLine(gray, toScreen(a.x, a.y), toScreen(b.x, b.y), strokeWidth = 2f)
                            }
                            perSegs.forEach { (a, b) ->
                                drawLine(gray, toScreen(a.x, a.y), toScreen(b.x, b.y), strokeWidth = 2f)
                            }
                        }
                        if (showPlateAxes) {
                            plateParSegs.forEach { (a, b) ->
                                drawLine(plateBlue, toScreen(a.x, a.y), toScreen(b.x, b.y), strokeWidth = 2f)
                            }
                            platePerSegs.forEach { (a, b) ->
                                drawLine(plateBlue, toScreen(a.x, a.y), toScreen(b.x, b.y), strokeWidth = 2f)
                            }
                        }
                        val (b1, b2) = baseline
                        drawLine(Color(0xFF4CAF50), toScreen(b1.x, b1.y), toScreen(b2.x, b2.y), strokeWidth = 4f)

                        // rectangles
                        val p1 = baseline.first; val p2 = baseline.second
                        val dx = p2.x - p1.x; val dy = p2.y - p1.y
                        val len = hypot(dx, dy).coerceAtLeast(1e-9)
                        val ux = dx / len; val uy = dy / len
                        val nx = uy; val ny = -ux
                        rects.forEach { r ->
                            if (r.isColumn && !showColumns) return@forEach
                            if (!r.isColumn && !showPlates) return@forEach
                            val hl = (r.lenCm / 100.0) / 2.0
                            val hw = (r.widCm / 100.0) / 2.0
                            val ax = if (r.alongNumeric) ux else nx
                            val ay = if (r.alongNumeric) uy else ny
                            val bx = if (r.alongNumeric) nx else ux
                            val by = if (r.alongNumeric) ny else uy
                            val corners = listOf(
                                toScreen(r.x - ax * hl - bx * hw, r.y - ay * hl - by * hw),
                                toScreen(r.x + ax * hl - bx * hw, r.y + ay * hl - by * hw),
                                toScreen(r.x + ax * hl + bx * hw, r.y + ay * hl + by * hw),
                                toScreen(r.x - ax * hl + bx * hw, r.y - ay * hl + by * hw)
                            )
                            val col = if (r.isColumn) Color(0xFFE53935) else Color.White
                            for (i in 0..3) {
                                drawLine(col, corners[i], corners[(i + 1) % 4], strokeWidth = 3f)
                            }
                        }

                        cols.forEach { c ->
                            val s = toScreen(c.x, c.y)
                            val isSel = selected?.let { it.pi == c.pi && it.qi == c.qi } == true
                            drawCircle(
                                if (isSel) Color(0xFFFF9800) else Color(0xFF81C995),
                                radius = if (isSel) 9f else 5f,
                                center = s
                            )
                        }
                        measureA?.let { a ->
                            measureB?.let { b ->
                                drawLine(Color.Yellow, toScreen(a.x, a.y), toScreen(b.x, b.y), strokeWidth = 2f)
                            }
                        }
                    }

                    selected?.let { c ->
                        val colRect = rects.firstOrNull { it.key == "${c.pi}_${c.qi}_c" }
                        val plateRect = rects.firstOrNull { it.key == "${c.pi}_${c.qi}_p" }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = appearanceCardColor(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(8.dp)
                                .fillMaxWidth(0.94f)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "X: ${"%.4f".format(c.x)}   Y: ${"%.4f".format(c.y)}",
                                    color = appearanceTextPrimary(), fontSize = 13.sp, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "لاین: ${"%.3f".format(c.station)}   آفست: ${"%.3f".format(c.offset)}",
                                    color = appearanceTextSecondary(), fontSize = 12.sp
                                )
                                if (colRect != null || plateRect != null) {
                                    val cTxt = colRect?.let { "ستون ${it.label}" } ?: ""
                                    val pTxt = plateRect?.let { "صفحه پلیت ${"%.0f".format(it.lenCm)}×${"%.0f".format(it.widCm)}" } ?: ""
                                    Text("$cTxt  $pTxt".trim(), color = appearanceTextPrimary(), fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    selected = null
                                    colDlg = c
                                    pickColumnMode = true
                                }) { Text("ویرایش") }
                                TextButton(onClick = { selected = null }) { Text("بستن") }
                            }
                        }
                    }
                }
            }
        }
    }

    // Plate axes dialog
    if (showPlateDlg) {
        val nPar = parallelCount.toIntOrNullFa() ?: cols.map { it.pi }.maxOrNull()?.plus(1) ?: 0
        val nPer = perpCount.toIntOrNullFa() ?: cols.map { it.qi }.maxOrNull()?.plus(1) ?: 0
        while (plateParOff.size < nPar) plateParOff = plateParOff + ""
        while (platePerOff.size < nPer) platePerOff = platePerOff + ""
        AlertDialog(
            onDismissRequest = { showPlateDlg = false },
            title = { Text("محورهای صفحه پلیت") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("آفست نسبت به محور ستون (+راست / −چپ)", fontSize = 11.sp)
                    (0 until nPar).forEach { i ->
                        AdelOutlinedTextField(
                            plateParOff.getOrElse(i) { "" },
                            { v -> plateParOff = plateParOff.toMutableList().also {
                                while (it.size <= i) it.add("")
                                it[i] = v
                            }},
                            label = { Text("آفست موازی ${pLabel(i)}") },
                            singleLine = true, keyboardOptions = numKb, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text("لاین نسبت به محور عمود ستون (+/-)", fontSize = 11.sp)
                    (0 until nPer).forEach { j ->
                        AdelOutlinedTextField(
                            platePerOff.getOrElse(j) { "" },
                            { v -> platePerOff = platePerOff.toMutableList().also {
                                while (it.size <= j) it.add("")
                                it[j] = v
                            }},
                            label = { Text("لاین عمود ${qLabel(j)}") },
                            singleLine = true, keyboardOptions = numKb, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPlateDlg = false
                    buildGeometry()
                    saveProject()
                    status = "محور پلیت ذخیره شد"
                }) { Text("نمایش نقشه") }
            },
            dismissButton = { TextButton(onClick = { showPlateDlg = false }) { Text("بستن") } }
        )
    }

    if (showAxisVis) {
        AlertDialog(
            onDismissRequest = { showAxisVis = false },
            title = { Text("نمایش محورها") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showColAxes, onCheckedChange = { showColAxes = it })
                        Text("محور ستون (خاکستری)", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showPlateAxes, onCheckedChange = { showPlateAxes = it })
                        Text("محور صفحه پلیت (آبی)", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAxisVis = false }) { Text("تأیید") } }
        )
    }

    if (showColPlateVis) {
        AlertDialog(
            onDismissRequest = { showColPlateVis = false },
            title = { Text("نمایش ستون / صفحه پلیت") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showColumns, onCheckedChange = { showColumns = it })
                        Text("ستون‌ها (قرمز)", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showPlates, onCheckedChange = { showPlates = it })
                        Text("صفحه پلیت (سفید)", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showColPlateVis = false }) { Text("تأیید") } }
        )
    }

    // Column registration dialog
    colDlg?.let { c ->
        AlertDialog(
            onDismissRequest = { colDlg = null; pickColumnMode = false },
            title = { Text("تقاطع ${c.pLabel}${c.qLabel}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("X=${"%.3f".format(c.x)} Y=${"%.3f".format(c.y)}", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdelOutlinedTextField(
                            colCount, { colCount = it },
                            label = { Text("تعداد") },
                            modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
                        )
                        AdelOutlinedTextField(
                            colType, {
                                colType = it
                                if (plateLen.isBlank()) plateLen = it
                                if (plateWid.isBlank()) plateWid = it
                            },
                            label = { Text("نوع/عرض ستون cm") },
                            modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = colAlongNumeric, onCheckedChange = { colAlongNumeric = it })
                        Text("جهت عددی", fontSize = 12.sp)
                        Checkbox(checked = !colAlongNumeric, onCheckedChange = { colAlongNumeric = !it })
                        Text("جهت حروفی", fontSize = 12.sp)
                    }
                    Text("صفحه پلیت (cm)", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AdelOutlinedTextField(
                            plateLen, {
                                plateLen = it
                                if (plateWid.isBlank()) plateWid = it
                            },
                            label = { Text("طول") },
                            modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
                        )
                        AdelOutlinedTextField(
                            plateWid, { plateWid = it },
                            label = { Text("عرض") },
                            modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = plateAlongNumeric, onCheckedChange = { plateAlongNumeric = it })
                        Text("پلیت جهت عددی", fontSize = 12.sp)
                        Checkbox(checked = !plateAlongNumeric, onCheckedChange = { plateAlongNumeric = !it })
                        Text("حروفی", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = colCount.toDoubleOrNullFa()
                    val t = colType.toDoubleOrNullFa()
                    if (n != null && t != null) {
                        // طول = تعداد × نصف نوع ، عرض = نوع
                        val lenCm = n * (t / 2.0)
                        val widCm = t
                        val keyC = "${c.pi}_${c.qi}_c"
                        val keyP = "${c.pi}_${c.qi}_p"
                        rects = rects.filterNot { it.key == keyC || it.key == keyP } +
                            LoRect(keyC, c.x, c.y, lenCm, widCm, colAlongNumeric, true, "${n.toInt()}×${t.toInt()}")
                        val pl = plateLen.toDoubleOrNullFa()
                        val pw = plateWid.toDoubleOrNullFa() ?: pl
                        if (pl != null && pw != null) {
                            // موقعیت روی تقاطع محور صفحه پلیت (نه ستون)
                            val dOff = plateParOff.getOrNull(c.pi)?.toDoubleOrNullFa() ?: 0.0
                            val dSt = platePerOff.getOrNull(c.qi)?.toDoubleOrNullFa() ?: 0.0
                            val p1 = baseline.first; val p2 = baseline.second
                            val dx0 = p2.x - p1.x; val dy0 = p2.y - p1.y
                            val ln = hypot(dx0, dy0).coerceAtLeast(1e-9)
                            val ux0 = dx0 / ln; val uy0 = dy0 / ln
                            val nx0 = uy0; val ny0 = -ux0
                            val stP = c.station + dSt
                            val offP = c.offset + dOff
                            val px = p1.x + stP * ux0 + offP * nx0
                            val py = p1.y + stP * uy0 + offP * ny0
                            rects = rects + LoRect(keyP, px, py, pl, pw, plateAlongNumeric, false, "${pl.toInt()}×${pw.toInt()}")
                        }
                        status = "ستون ثبت شد"
                    }
                    colDlg = null
                    pickColumnMode = false
                }) { Text("ثبت") }
            },
            dismissButton = {
                TextButton(onClick = { colDlg = null; pickColumnMode = false }) { Text("لغو") }
            }
        )
    }
}
