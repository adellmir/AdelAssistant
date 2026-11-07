package com.adel.assistant.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.data.LineOffsetStore
import com.adel.assistant.data.LineOffsetProject
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.appearanceCardColor
import com.adel.assistant.ui.appearanceScreenBg
import com.adel.assistant.ui.appearanceTextPrimary
import com.adel.assistant.ui.appearanceTextSecondary
import com.adel.assistant.ui.components.AdelOutlinedTextField
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private data class LoPoint(val x: Double, val y: Double)
private data class LoColumn(
    val parallelIndex: Int,
    val perpIndex: Int,
    val parallelLabel: String,
    val perpLabel: String,
    val station: Double,   // لاین (روی مبنا)
    val offset: Double,    // آفست از مبنا
    val x: Double,
    val y: Double
)

private fun letterLabel(i: Int): String {
    // 0 -> A, 25 -> Z, 26 -> AA
    var n = i
    val sb = StringBuilder()
    do {
        sb.insert(0, ('A' + (n % 26)))
        n = n / 26 - 1
    } while (n >= 0)
    return sb.toString()
}

@Composable
fun LineOffsetScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val numKb = KeyboardOptions(keyboardType = KeyboardType.Number)

    // نقطه ۱ و ۲ (خط مبنا)
    var screenMode by remember { mutableStateOf("list") } // list | form | map
    var projectName by remember { mutableStateOf("") }
    var projectId by remember { mutableStateOf<String?>(null) }
    var listTick by remember { mutableStateOf(0) }
    val savedProjects = remember(listTick) { LineOffsetStore.all(context) }
    var x1 by remember { mutableStateOf("") }
    var y1 by remember { mutableStateOf("") }
    var x2 by remember { mutableStateOf("") }
    var y2 by remember { mutableStateOf("") }

    // آفست محور موازی اول + لاین و طول محور عمود اول
    var firstOffset by remember { mutableStateOf("") }
    var firstLine by remember { mutableStateOf("") }
    var perpLength by remember { mutableStateOf("") }

    // تعداد محورها
    var parallelCount by remember { mutableStateOf("") }
    var perpCount by remember { mutableStateOf("") }
    var parallelIsLetter by remember { mutableStateOf(true) } // موازی حروفی؟
    // موقعیت ستون‌ها: all = همه تقاطع‌ها | corners = فقط گوشه‌ها | none_until_tap = بعداً روی نقشه
    var columnMode by remember { mutableStateOf("all") }

    // فواصل
    var parallelSpacing by remember { mutableStateOf("") }
    var perpSpacing by remember { mutableStateOf("") }
    var parSpaceList by remember { mutableStateOf(listOf<String>()) }
    var perSpaceList by remember { mutableStateOf(listOf<String>()) }

    var built by remember { mutableStateOf(false) }
    var columns by remember { mutableStateOf(listOf<LoColumn>()) }
    var baseline by remember { mutableStateOf(LoPoint(0.0, 0.0) to LoPoint(0.0, 14.0)) }
    var parallelSegs by remember { mutableStateOf(listOf<Pair<LoPoint, LoPoint>>()) }
    var perpSegs by remember { mutableStateOf(listOf<Pair<LoPoint, LoPoint>>()) }
    var selected by remember { mutableStateOf<LoColumn?>(null) }
    var allIntersections by remember { mutableStateOf(listOf<LoColumn>()) }
    var activeKeys by remember { mutableStateOf(setOf<String>()) }
    var status by remember { mutableStateOf("") }

    var scale by remember { mutableStateOf(40f) }
    var offset by remember { mutableStateOf(Offset(80f, 120f)) }

    fun build() {
        val p1x = x1.toDoubleOrNullFa()
        val p1y = y1.toDoubleOrNullFa()
        val p2x = x2.toDoubleOrNullFa()
        val p2y = y2.toDoubleOrNullFa()
        if (p1x == null || p1y == null || p2x == null || p2y == null) {
            status = "مختصات نقطه ۱ و ۲ را کامل وارد کنید"
            return
        }
        val o0 = firstOffset.toDoubleOrNullFa()
        val s0 = firstLine.toDoubleOrNullFa()
        val pLen = perpLength.toDoubleOrNullFa()
        val nPar = parallelCount.toIntOrNullFa()
        val nPer = perpCount.toIntOrNullFa()
        val dPar = parallelSpacing.toDoubleOrNullFa()
        val dPer = perpSpacing.toDoubleOrNullFa()
        if (o0 == null) { status = "آفست محور موازی اول را وارد کنید"; return }
        if (s0 == null) { status = "لاین محور عمود اول را وارد کنید"; return }
        if (pLen == null) { status = "طول محور عمود را وارد کنید"; return }
        if (nPar == null || nPar < 1) { status = "تعداد محور موازی را وارد کنید"; return }
        if (nPer == null || nPer < 1) { status = "تعداد محور عمود را وارد کنید"; return }
        if (dPar == null || dPar <= 0) { status = "فاصله بین محورهای موازی را وارد کنید"; return }
        if (dPer == null || dPer <= 0) { status = "فاصله بین محورهای عمود را وارد کنید"; return }
        val p1 = LoPoint(p1x, p1y)
        val p2 = LoPoint(p2x, p2y)
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val len = hypot(dx, dy)
        if (len < 1e-9) {
            status = "نقطه ۱ و ۲ نباید یکی باشند"
            return
        }
        val ux = dx / len
        val uy = dy / len
        // راست نسبت به جهت ۱→۲
        val nx = uy
        val ny = -ux

        val nParC = nPar.coerceIn(1, 50)
        val nPerC = nPer.coerceIn(1, 50)

        fun world(station: Double, off: Double) = LoPoint(
            p1.x + station * ux + off * nx,
            p1.y + station * uy + off * ny
        )

        // محدوده ایستگاه‌ها و آفست‌ها
        val stations = (0 until nPerC).map { s0 + it * dPer }
        val offsets = (0 until nParC).map { o0 + it * dPar }
        val sMin = stations.minOrNull() ?: s0
        val sMax = max(stations.maxOrNull() ?: s0, sMin + pLen)
        // اگر طول عمود مشخص شده، انتهای محور موازی را با آن هم‌تراز کن
        val sEnd = max(sMax, s0 + pLen)
        val sStart = min(sMin, s0)

        val pars = offsets.map { off ->
            world(sStart, off) to world(sEnd, off)
        }
        val perps = stations.map { st ->
            val oMin = offsets.minOrNull() ?: o0
            val oMax = offsets.maxOrNull() ?: o0
            world(st, oMin) to world(st, oMax)
        }

        fun parLabel(i: Int) =
            if (parallelIsLetter) letterLabel(i) else (i + 1).toString()
        fun perLabel(j: Int) =
            if (parallelIsLetter) (j + 1).toString() else letterLabel(j)

        val cols = mutableListOf<LoColumn>()
        for (i in offsets.indices) {
            for (j in stations.indices) {
                val w = world(stations[j], offsets[i])
                cols += LoColumn(
                    parallelIndex = i,
                    perpIndex = j,
                    parallelLabel = parLabel(i),
                    perpLabel = perLabel(j),
                    station = stations[j],
                    offset = offsets[i],
                    x = w.x,
                    y = w.y
                )
            }
        }

        baseline = p1 to p2
        parallelSegs = pars
        perpSegs = perps
        allIntersections = cols
        val finalCols = when (columnMode) {
            "corners" -> {
                val maxI = (nPar - 1).coerceAtLeast(0)
                val maxJ = (nPer - 1).coerceAtLeast(0)
                cols.filter { (it.parallelIndex == 0 || it.parallelIndex == maxI) && (it.perpIndex == 0 || it.perpIndex == maxJ) }
            }
            "none" -> emptyList()
            else -> cols // all
        }
        columns = finalCols
        activeKeys = finalCols.map { "${it.parallelIndex}_${it.perpIndex}" }.toSet()
        built = true
        selected = null
        status = "شبکه ساخته شد — ستون: ${finalCols.size} از ${cols.size} تقاطع"
        // فیت تقریبی
        val xs = cols.map { it.x } + listOf(p1.x, p2.x)
        val ys = cols.map { it.y } + listOf(p1.y, p2.y)
        val minX = xs.minOrNull() ?: 0.0
        val maxX = xs.maxOrNull() ?: 1.0
        val minY = ys.minOrNull() ?: 0.0
        val maxY = ys.maxOrNull() ?: 1.0
        // scale/offset بعداً با اندازه بوم تنظیم می‌شود
        scale = 40f
        offset = Offset(80f, 120f)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(appearanceScreenBg())
            .padding(horizontal = 12.dp)
    ) {
        ScreenTopBar(title = "لاین آف‌ست", color = color, onBack = onBack)
        Spacer(Modifier.height(6.dp))

        if (!built) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("خط مبنا — دو نقطه (متر)", color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdelOutlinedTextField(x1, { x1 = it }, label = { Text("X نقطه ۱") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                    AdelOutlinedTextField(y1, { y1 = it }, label = { Text("Y نقطه ۱") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdelOutlinedTextField(x2, { x2 = it }, label = { Text("X نقطه ۲") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                    AdelOutlinedTextField(y2, { y2 = it }, label = { Text("Y نقطه ۲") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb)
                }

                Text("محور اول", color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                AdelOutlinedTextField(
                    firstOffset, { firstOffset = it },
                    label = { Text("آفست محور موازی اول (متر)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AdelOutlinedTextField(
                        firstLine, { firstLine = it },
                        label = { Text("لاین محور عمود اول (متر)") },
                        modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
                    )
                    AdelOutlinedTextField(
                        perpLength, { perpLength = it },
                        label = { Text("طول محور عمود (متر)") },
                        modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
                    )
                }

                Text("فواصل بین محورها", color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                AdelOutlinedTextField(
                    parallelSpacing, { parallelSpacing = it },
                    label = { Text("فاصله بین محورهای موازی (متر)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb
                )
                AdelOutlinedTextField(
                    perpSpacing, { perpSpacing = it },
                    label = { Text("فاصله بین محورهای عمود (متر)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb
                )

                Text("تعداد و عنوان‌گذاری", color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AdelOutlinedTextField(
                        parallelCount, { parallelCount = it },
                        label = { Text("تعداد محور موازی") },
                        modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = numKb
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = parallelIsLetter, onCheckedChange = { parallelIsLetter = it })
                        Text("حروفی", color = appearanceTextPrimary(), fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = !parallelIsLetter, onCheckedChange = { parallelIsLetter = !it })
                        Text("عددی", color = appearanceTextPrimary(), fontSize = 12.sp)
                    }
                }
                AdelOutlinedTextField(
                    perpCount, { perpCount = it },
                    label = { Text("تعداد محور عمود") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = numKb
                )

                Text("موقعیت ستون‌ها", color = appearanceTextPrimary(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { columnMode = "all" }) {
                    RadioButton(selected = columnMode == "all", onClick = { columnMode = "all" })
                    Text("همه تقاطع‌ها", color = appearanceTextPrimary(), fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { columnMode = "corners" }) {
                    RadioButton(selected = columnMode == "corners", onClick = { columnMode = "corners" })
                    Text("فقط گوشه‌های شبکه", color = appearanceTextPrimary(), fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { columnMode = "none" }) {
                    RadioButton(selected = columnMode == "none", onClick = { columnMode = "none" })
                    Text("بدون ستون (فقط خطوط)", color = appearanceTextPrimary(), fontSize = 12.sp)
                }

                if (status.isNotBlank()) {
                    Text(status, color = Color(0xFFFFB74D), fontSize = 12.sp)
                }
                Button(
                    onClick = { build() },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ترسیم شبکه") }
                Spacer(Modifier.height(16.dp))
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(status, color = appearanceTextSecondary(), fontSize = 12.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { built = false }) { Text("ویرایش") }
            }
            Spacer(Modifier.height(4.dp))
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
                                val ns = (scale * zoom).coerceIn(2f, 500f)
                                scale = ns
                                offset += pan
                            }
                        }
                        .pointerInput(columns, scale, offset) {
                            detectTapGestures { tap ->
                                fun toScreen(x: Double, y: Double) =
                                    Offset((x * scale + offset.x).toFloat(), (-y * scale + offset.y).toFloat())
                                val hitR = 28f
                                var best: LoColumn? = null
                                var bestD = hitR
                                columns.forEach { c ->
                                    val s = toScreen(c.x, c.y)
                                    val d = hypot((s.x - tap.x).toDouble(), (s.y - tap.y).toDouble()).toFloat()
                                    if (d < bestD) {
                                        bestD = d
                                        best = c
                                    }
                                }
                                selected = best
                            }
                        }
                ) {
                    fun toScreen(x: Double, y: Double) =
                        Offset((x * scale + offset.x).toFloat(), (-y * scale + offset.y).toFloat())

                    // محورهای شبکه خاکستری
                    val axisColor = Color(0xFF9E9E9E)
                    parallelSegs.forEach { (a, b) ->
                        drawLine(axisColor, toScreen(a.x, a.y), toScreen(b.x, b.y), strokeWidth = 2f)
                    }
                    perpSegs.forEach { (a, b) ->
                        drawLine(axisColor, toScreen(a.x, a.y), toScreen(b.x, b.y), strokeWidth = 2f)
                    }

                    // خط مبنا سبز
                    val (b1, b2) = baseline
                    drawLine(
                        Color(0xFF4CAF50),
                        toScreen(b1.x, b1.y),
                        toScreen(b2.x, b2.y),
                        strokeWidth = 4f
                    )

                    // ستون‌ها
                    columns.forEach { c ->
                        val s = toScreen(c.x, c.y)
                        val isSel = selected?.let { it.parallelIndex == c.parallelIndex && it.perpIndex == c.perpIndex } == true
                        drawCircle(
                            color = if (isSel) Color(0xFFFF9800) else Color(0xFF81C995),
                            radius = if (isSel) 10f else 6f,
                            center = s
                        )
                        // برچسب کوچک
                        val labelPaint = android.graphics.Paint().apply {
                            this.color = android.graphics.Color.WHITE
                            textSize = 28f
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            "${c.parallelLabel}${c.perpLabel}",
                            s.x + 8f,
                            s.y - 8f,
                            labelPaint
                        )
                    }
                }

                selected?.let { c ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = appearanceCardColor(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .fillMaxWidth(0.92f)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "لاین: ${"%.3f".format(c.station)}   آفست: ${"%.3f".format(c.offset)}",
                                color = appearanceTextPrimary(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "محور: ${c.parallelLabel} / ${c.perpLabel}",
                                color = appearanceTextSecondary(),
                                fontSize = 12.sp
                            )
                            Text(
                                "X: ${"%.4f".format(c.x)}    Y: ${"%.4f".format(c.y)}",
                                color = appearanceTextPrimary(),
                                fontSize = 13.sp
                            )
                            TextButton(onClick = { selected = null }) { Text("بستن") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
