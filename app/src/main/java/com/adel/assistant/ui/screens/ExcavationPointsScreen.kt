package com.adel.assistant.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.ReportEntry
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.filterNumericInput
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.ToolbarIcon
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import java.util.Locale

/**
 * نقاط حفاری — نمایش/خروجی نقاط گزارش روزانه در یک بازهٔ تاریخ
 * با مختصات درون‌یابی‌شده و خروجی TXT / DXF
 */
@Composable
fun ExcavationPointsScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current

    val todayJ = remember { CalendarStore.todayJalali() }
    var fromDay by remember { mutableStateOf("1") }
    var fromMonth by remember { mutableStateOf("1") }
    var fromYear by remember { mutableStateOf(todayJ.first.toString()) }
    var toDay by remember { mutableStateOf(todayJ.third.toString()) }
    var toMonth by remember { mutableStateOf(todayJ.second.toString()) }
    var toYear by remember { mutableStateOf(todayJ.first.toString()) }

    var points by remember { mutableStateOf<List<ReportEntry>>(emptyList()) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("بازهٔ تاریخ را وارد و «نمایش» را بزن") }

    fun rowKey(e: ReportEntry) = "${e.dateSortKey}-${e.shaft}-${e.side}-${e.pointNo}-${e.km}"

    fun load() {
        if (fromDay.isBlank() || fromMonth.isBlank() || fromYear.isBlank() ||
            toDay.isBlank() || toMonth.isBlank() || toYear.isBlank()
        ) {
            status = "روز / ماه / سال از و تا را کامل وارد کن"
            return
        }
        points = TunnelReportStore.entriesInRange(
            context, fromYear, fromMonth, fromDay, toYear, toMonth, toDay
        )
        selected = emptySet()
        status = if (points.isEmpty()) "در این بازه نقطه‌ای نیست"
        else "${points.size} نقطه (جدید → قدیم)"
    }

    fun exportList(): List<ReportEntry> {
        return if (selectMode && selected.isNotEmpty()) {
            points.filter { rowKey(it) in selected }
        } else {
            points
        }
    }

    fun exportTxt(): Boolean {
        val list = exportList()
        if (list.isEmpty()) {
            status = "نقطه‌ای برای خروجی نیست"; return false
        }
        val body = buildString {
            list.forEach { e ->
                append(e.dateLabel)
                append('\t')
                append(String.format(Locale.US, "%.4f", e.x))
                append('\t')
                append(String.format(Locale.US, "%.4f", e.y))
                append('\t')
                append(String.format(Locale.US, "%.4f", e.z))
                append('\t')
                append(String.format(Locale.US, "%.3f", e.km))
                append('\n')
            }
        }
        val name = "excavation_${fromYear}${fromMonth.padStart(2, '0')}${fromDay.padStart(2, '0')}_" +
            "${toYear}${toMonth.padStart(2, '0')}${toDay.padStart(2, '0')}.txt"
        val uri = FileExport.exportTextToDocuments(context, name, body, "text/plain")
        status = if (uri != null) "TXT ذخیره شد (${list.size} نقطه)" else "خطا در ذخیره TXT"
        return uri != null
    }

    fun exportDxf(): Boolean {
        val list = exportList()
        if (list.isEmpty()) {
            status = "نقطه‌ای برای خروجی نیست"; return false
        }
        val dxf = buildExcavationDxf(list)
        val name = "excavation_${fromYear}${fromMonth.padStart(2, '0')}${fromDay.padStart(2, '0')}_" +
            "${toYear}${toMonth.padStart(2, '0')}${toDay.padStart(2, '0')}.dxf"
        val uri = FileExport.exportTextToDocuments(context, name, dxf, "application/dxf")
        status = if (uri != null) "DXF ذخیره شد (${list.size} نقطه)" else "خطا در ذخیره DXF"
        return uri != null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "نقاط حفاری", color = color, onBack = onBack)

        Text("بازهٔ تاریخ (از – تا)", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DateField("روز از", fromDay, { fromDay = filterNumericInput(it) }, Modifier.weight(1f))
            DateField("ماه از", fromMonth, { fromMonth = filterNumericInput(it) }, Modifier.weight(1f))
            DateField("سال از", fromYear, { fromYear = filterNumericInput(it) }, Modifier.weight(1.2f))
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DateField("روز تا", toDay, { toDay = filterNumericInput(it) }, Modifier.weight(1f))
            DateField("ماه تا", toMonth, { toMonth = filterNumericInput(it) }, Modifier.weight(1f))
            DateField("سال تا", toYear, { toYear = filterNumericInput(it) }, Modifier.weight(1.2f))
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { load() },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text("نمایش") }

        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarIcon(Icons.Outlined.Notes, "خروجی TXT", color, onClick = { exportTxt() })
            ToolbarIcon(Icons.Outlined.Polyline, "خروجی DXF", color, onClick = { exportDxf() })
            ToolbarIcon(
                if (selectMode) Icons.Outlined.LibraryAddCheck else Icons.Outlined.Checklist,
                if (selectMode) "همه" else "گزینش",
                color,
                onClick = {
                    if (!selectMode) {
                        selectMode = true
                        selected = emptySet()
                    } else {
                        selected = if (selected.size == points.size) emptySet()
                        else points.map { rowKey(it) }.toSet()
                    }
                }
            )
        }
        if (selectMode) {
            TextButton(onClick = { selectMode = false; selected = emptySet() }) {
                Text("لغو گزینش", color = TextSecondary)
            }
        }

        Text(status, color = TextSecondary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(points, key = { rowKey(it) }) { e ->
                val key = rowKey(e)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectMode) {
                            Checkbox(
                                checked = key in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + key else selected - key
                                },
                                colors = CheckboxDefaults.colors(checkedColor = color)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.dateLabel,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "X=${"%.3f".format(e.x)}  Y=${"%.3f".format(e.y)}  Z=${"%.3f".format(e.z)}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                "کیلومتراژ ${"%.3f".format(e.km)}  |  شفت ${e.shaft} سمت ${e.side}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** DXF R12 — ضربدر + سه خط متن در سمت چپ نقطه */
private fun buildExcavationDxf(list: List<ReportEntry>): String {
    val crlf = "\r\n"
    fun pair(code: Int, value: String) = "$code$crlf$value$crlf"
    val sb = StringBuilder()
    sb.append(pair(0, "SECTION")).append(pair(2, "HEADER"))
    sb.append(pair(9, "\$ACADVER")).append(pair(1, "AC1009"))
    sb.append(pair(0, "ENDSEC"))
    sb.append(pair(0, "SECTION")).append(pair(2, "TABLES"))
    sb.append(pair(0, "TABLE")).append(pair(2, "LTYPE")).append(pair(70, "1"))
    sb.append(pair(0, "LTYPE")).append(pair(2, "CONTINUOUS")).append(pair(70, "0"))
    sb.append(pair(3, "Solid line")).append(pair(72, "65")).append(pair(73, "0")).append(pair(40, "0.0"))
    sb.append(pair(0, "ENDTAB"))
    val layers = list.map { "S${it.shaft}-${it.side}" }.distinct()
    sb.append(pair(0, "TABLE")).append(pair(2, "LAYER")).append(pair(70, (layers.size + 1).toString()))
    sb.append(pair(0, "LAYER")).append(pair(2, "0")).append(pair(70, "0")).append(pair(62, "7")).append(pair(6, "CONTINUOUS"))
    layers.forEachIndexed { i, ly ->
        sb.append(pair(0, "LAYER")).append(pair(2, ly)).append(pair(70, "0"))
        sb.append(pair(62, (1 + i % 6).toString())).append(pair(6, "CONTINUOUS"))
    }
    sb.append(pair(0, "ENDTAB")).append(pair(0, "ENDSEC"))
    sb.append(pair(0, "SECTION")).append(pair(2, "ENTITIES"))

    val textH = 0.05
    val cross = 0.08
    val gap = 0.12 // فاصله متن از نقطه به سمت چپ (X منفی)

    list.forEach { e ->
        val ly = "S${e.shaft}-${e.side}"
        val x = e.x; val y = e.y; val z = e.z
        // ضربدر
        sb.append(pair(0, "LINE")).append(pair(8, ly))
        sb.append(pair(10, fmt(x - cross))).append(pair(20, fmt(y - cross))).append(pair(30, fmt(z)))
        sb.append(pair(11, fmt(x + cross))).append(pair(21, fmt(y + cross))).append(pair(31, fmt(z)))
        sb.append(pair(0, "LINE")).append(pair(8, ly))
        sb.append(pair(10, fmt(x - cross))).append(pair(20, fmt(y + cross))).append(pair(30, fmt(z)))
        sb.append(pair(11, fmt(x + cross))).append(pair(21, fmt(y - cross))).append(pair(31, fmt(z)))
        // سه خط متن سمت چپ
        val lines = listOf(
            e.dateLabel,
            String.format(Locale.US, "%.3f", e.z),
            String.format(Locale.US, "%.3f", e.km)
        )
        lines.forEachIndexed { i, txt ->
            val tx = x - gap
            val ty = y + gap - i * (textH * 1.4)
            sb.append(pair(0, "TEXT")).append(pair(8, ly)).append(pair(62, "7"))
            sb.append(pair(10, fmt(tx))).append(pair(20, fmt(ty))).append(pair(30, fmt(z)))
            sb.append(pair(40, fmt(textH))).append(pair(1, txt)).append(pair(50, "0"))
        }
    }
    sb.append(pair(0, "ENDSEC")).append(pair(0, "EOF"))
    return sb.toString()
}

private fun fmt(v: Double): String = String.format(Locale.US, "%.4f", v)
