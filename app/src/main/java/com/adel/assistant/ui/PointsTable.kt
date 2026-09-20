package com.adel.assistant.ui

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.GsiPoint
import java.util.Locale

/**
 * جدول سلول‌قابل‌ویرایش شبیه اکسل: N X Y Z D (+ چک‌باکس اختیاری)
 */
@Composable
fun PointsSpreadsheet(
    points: List<GsiPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    selectedIds: Set<Long> = emptySet(),
    selectAll: Boolean = true,
    showCheckbox: Boolean = true,
    onToggleSelect: ((Long, Boolean) -> Unit)? = null,
    onChange: (GsiPoint) -> Unit,
    onDelete: ((GsiPoint) -> Unit)? = null,
    onMove: ((GsiPoint) -> Unit)? = null,
    rowBackground: Color = Color(0xFF1E241A),
    headerBackground: Color = Color(0xFF2A3324)
) {
    val hScroll = rememberScrollState()
    val wChk = 36.dp
    val wN = 70.dp
    val wX = 102.dp
    val wY = 102.dp
    val wZ = 78.dp
    val wD = 70.dp
    val wDel = 36.dp
    val wMove = 36.dp

    Column(modifier) {
        Row(
            Modifier
                .horizontalScroll(hScroll)
                .background(headerBackground, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCheckbox) HeaderCell("✓", wChk)
            HeaderCell("N", wN)
            HeaderCell("X", wX)
            HeaderCell("Y", wY)
            HeaderCell("Z", wZ)
            HeaderCell("D", wD)
            if (onMove != null) HeaderCell("↔", wMove)
            if (onDelete != null) HeaderCell("✕", wDel)
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(points, key = { it.id }) { p ->
                val checked = selectAll || p.id in selectedIds
                Row(
                    Modifier
                        .horizontalScroll(hScroll)
                        .background(rowBackground)
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCheckbox) {
                        Box(Modifier.width(wChk), contentAlignment = Alignment.Center) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on -> onToggleSelect?.invoke(p.id, on) },
                                colors = CheckboxDefaults.colors(checkedColor = color),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    CellField(p.name, wN) { onChange(p.copy(name = it)) }
                    CellField(fmtNum(p.e), wX, numeric = true) { s ->
                        s.replace(',', '.').toDoubleOrNull()?.let { onChange(p.copy(e = it)) }
                    }
                    CellField(fmtNum(p.n), wY, numeric = true) { s ->
                        s.replace(',', '.').toDoubleOrNull()?.let { onChange(p.copy(n = it)) }
                    }
                    CellField(fmtNum(p.z), wZ, numeric = true) { s ->
                        s.replace(',', '.').toDoubleOrNull()?.let { onChange(p.copy(z = it)) }
                    }
                    CellField(p.code, wD) { onChange(p.copy(code = it)) }
                    if (onMove != null) {
                        Text(
                            "↔",
                            color = Color(0xFF81C995),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .width(wMove)
                                .clickable { onMove(p) }
                                .padding(6.dp)
                        )
                    }
                    if (onDelete != null) {
                        Text(
                            "✕",
                            color = Color(0xFFE57373),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .width(wDel)
                                .clickable { onDelete(p) }
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text,
        color = Color(0xFFCFD8C8),
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.width(width).padding(horizontal = 4.dp)
    )
}

@Composable
private fun CellField(
    value: String,
    width: Dp,
    numeric: Boolean = false,
    onCommit: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    // همگام با مقدار بیرونی وقتی نقطه از بیرون عوض شود
    LaunchedEffect(value) { text = value }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TextStyle(color = Color(0xFFE8EEDF), fontSize = 12.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
        ),
        modifier = Modifier
            .width(width)
            .heightIn(min = 36.dp)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .border(1.dp, Color(0xFF3A4534), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .onFocusChanged { fs ->
                if (!fs.isFocused && text != value) {
                    onCommit(text)
                }
            }
    )
}

private fun fmtNum(v: Double): String = String.format(Locale.US, "%.3f", v)
