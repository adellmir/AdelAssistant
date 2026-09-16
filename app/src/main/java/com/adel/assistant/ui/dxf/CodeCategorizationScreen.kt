package com.adel.assistant.ui.dxf

import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import com.adel.assistant.ui.theme.TextMuted


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.CodeCategory
import com.adel.assistant.data.CodeSetting
import com.adel.assistant.data.DxfColors
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.ui.theme.ToolPrimary

@Composable
fun CodeCategorizationContent(
    points: List<SurveyPoint>,
    fileName: String,
    settings: Map<String, CodeSetting>,
    onSettingsChange: (Map<String, CodeSetting>) -> Unit,
    onConfirm: () -> Unit
) {
    val lineCodes = settings.filter { it.value.category == CodeCategory.LINE }.keys.sorted()
    val pointCodes = settings.filter { it.value.category == CodeCategory.POINT }.keys.sorted()
    val ignoreCodes = settings.filter { it.value.category == CodeCategory.IGNORE }.keys.sorted()

    var expandedCode by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // هدر اطلاعات
        Surface(
            color = Color.White,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "فایل: $fileName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                Text(
                    "${points.size} نقطه • ${settings.size} کد یکتا",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF333333)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ---- خطوط ----
            item {
                CategoryHeader("ترسیم خطوط", lineCodes.size, Color(0xFFE53935))
            }
            items(lineCodes) { code ->
                val setting = settings[code]!!
                CodeCard(
                    setting = setting,
                    isExpanded = expandedCode == code,
                    onExpandToggle = {
                        expandedCode = if (expandedCode == code) null else code
                    },
                    onCategoryChange = { newCat ->
                        val updated = setting.copy(category = newCat)
                        onSettingsChange(settings + (code to updated))
                    },
                    onColorChange = { idx ->
                        onSettingsChange(settings + (code to setting.copy(colorIndex = idx)))
                    },
                    onLayerChange = { name ->
                        onSettingsChange(settings + (code to setting.copy(layerName = name)))
                    },
                    onCloseOnEChange = { checked ->
                        onSettingsChange(settings + (code to setting.copy(closeOnE = checked)))
                    },
                    showLineOptions = true
                )
            }

            // ---- نقاط ----
            item {
                Spacer(modifier = Modifier.height(8.dp))
                CategoryHeader("ترسیم نقاط", pointCodes.size, Color(0xFF1E88E5))
            }
            items(pointCodes) { code ->
                val setting = settings[code]!!
                CodeCard(
                    setting = setting,
                    isExpanded = expandedCode == code,
                    onExpandToggle = {
                        expandedCode = if (expandedCode == code) null else code
                    },
                    onCategoryChange = { newCat ->
                        val updated = setting.copy(category = newCat)
                        onSettingsChange(settings + (code to updated))
                    },
                    onColorChange = { idx ->
                        onSettingsChange(settings + (code to setting.copy(colorIndex = idx)))
                    },
                    onLayerChange = { name ->
                        onSettingsChange(settings + (code to setting.copy(layerName = name)))
                    },
                    onLabelChange = { showNum, showXY, showZ, showCode, size ->
                        onSettingsChange(
                            settings + (code to setting.copy(
                                showNumber = showNum,
                                showXY = showXY,
                                showZ = showZ,
                                showCode = showCode,
                                textSize = size
                            ))
                        )
                    },
                    showPointOptions = true
                )
            }

            // ---- نادیده ----
            item {
                Spacer(modifier = Modifier.height(8.dp))
                CategoryHeader("نادیده گرفته", ignoreCodes.size, Color(0xFF757575))
            }
            items(ignoreCodes) { code ->
                val setting = settings[code]!!
                CodeCard(
                    setting = setting,
                    isExpanded = expandedCode == code,
                    onExpandToggle = {
                        expandedCode = if (expandedCode == code) null else code
                    },
                    onCategoryChange = { newCat ->
                        val updated = setting.copy(category = newCat)
                        onSettingsChange(settings + (code to updated))
                    },
                    showMinimal = true
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        // دکمه تأیید
        Surface(
            color = Color.White,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ToolPrimary)
            ) {
                Text("تولید DXF", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
private fun CodeCard(
    setting: CodeSetting,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onCategoryChange: (CodeCategory) -> Unit,
    onColorChange: (Int) -> Unit = {},
    onLayerChange: (String) -> Unit = {},
    onCloseOnEChange: (Boolean) -> Unit = {},
    onLabelChange: (Boolean, Boolean, Boolean, Boolean, Float) -> Unit = { _, _, _, _, _ -> },
    showLineOptions: Boolean = false,
    showPointOptions: Boolean = false,
    showMinimal: Boolean = false
) {
    val bgColor = when (setting.category) {
        CodeCategory.LINE -> Color(0xFFFFEBEE)
        CodeCategory.POINT -> Color(0xFFE3F2FD)
        CodeCategory.IGNORE -> Color(0xFFF5F5F5)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ردیف اصلی
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() }
            ) {
                // رنگ
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(DxfColors.compose.getOrElse(setting.colorIndex) { Color.Gray })
                        .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        setting.code,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    Text(
                        "لایه: ${setting.layerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF333333)
                    )
                }

                // انتخاب دسته
                CategoryChips(
                    current = setting.category,
                    onSelect = onCategoryChange
                )

                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }

            // جزئیات بازشونده
            if (isExpanded && !showMinimal) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // نام لایه
                OutlinedTextField(
                    value = setting.layerName,
                    onValueChange = onLayerChange,
                    label = { Text("نام لایه") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // انتخاب رنگ
                Text("رنگ:", style = MaterialTheme.typography.bodySmall, color = Color.Black)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DxfColors.compose.forEachIndexed { idx, color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (setting.colorIndex == idx) 3.dp else 1.dp,
                                    color = if (setting.colorIndex == idx) ToolPrimary else Color.Black.copy(0.2f),
                                    shape = CircleShape
                                )
                                .clickable { onColorChange(idx) }
                        )
                    }
                }

                if (showLineOptions) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCloseOnEChange(!setting.closeOnE) }
                    ) {
                        Checkbox(
                            checked = setting.closeOnE,
                            onCheckedChange = onCloseOnEChange,
                            colors = CheckboxDefaults.colors(checkedColor = ToolPrimary)
                        )
                        Text("بستن ترسیم بعد از .E", color = Color.Black)
                    }
                }

                if (showPointOptions) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("اطلاعات کنار نقطه:", style = MaterialTheme.typography.bodySmall, color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))

                    LabelCheck("شماره نقطه", setting.showNumber) {
                        onLabelChange(it, setting.showXY, setting.showZ, setting.showCode, setting.textSize)
                    }
                    LabelCheck("مختصات (X,Y)", setting.showXY) {
                        onLabelChange(setting.showNumber, it, setting.showZ, setting.showCode, setting.textSize)
                    }
                    LabelCheck("ارتفاع (Z)", setting.showZ) {
                        onLabelChange(setting.showNumber, setting.showXY, it, setting.showCode, setting.textSize)
                    }
                    LabelCheck("کد", setting.showCode) {
                        onLabelChange(setting.showNumber, setting.showXY, setting.showZ, it, setting.textSize)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("سایز نوشته: ${"%.1f".format(setting.textSize)}", style = MaterialTheme.typography.bodySmall, color = Color.Black)
                    Slider(
                        value = setting.textSize,
                        onValueChange = {
                            onLabelChange(setting.showNumber, setting.showXY, setting.showZ, setting.showCode, it)
                        },
                        valueRange = 1f..6f,
                        steps = 9,
                        colors = SliderDefaults.colors(thumbColor = ToolPrimary, activeTrackColor = ToolPrimary)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(
    current: CodeCategory,
    onSelect: (CodeCategory) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TinyChip("خط", current == CodeCategory.LINE, Color(0xFFE53935)) {
            onSelect(CodeCategory.LINE)
        }
        TinyChip("نقطه", current == CodeCategory.POINT, Color(0xFF1E88E5)) {
            onSelect(CodeCategory.POINT)
        }
        TinyChip("نادیده", current == CodeCategory.IGNORE, Color(0xFF757575)) {
            onSelect(CodeCategory.IGNORE)
        }
    }
}

@Composable
private fun TinyChip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) color else Color.Transparent,
        border = if (!selected) androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.5f)) else null,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = if (selected) Color.White else Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LabelCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChecked,
            colors = CheckboxDefaults.colors(checkedColor = ToolPrimary)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Black)
    }
}
