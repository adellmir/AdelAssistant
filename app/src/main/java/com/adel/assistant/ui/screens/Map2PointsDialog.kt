package com.adel.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adel.assistant.data.*

/**
 * پنجره ورود/مدیریت نقاط نقشه ۲:
 * ردیف دسته‌ها + گزینش + نمایش نماد/شماره/کد/ارتفاع.
 */
@Composable
fun Map2PointsDialog(
    color: Color,
    onDismiss: () -> Unit,
    onCommitToMap: (List<Map2PointCategory>) -> Unit
) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf(Map2Session.categories.map { it.copy() }) }
    LaunchedEffect(Unit) {
        if (Map2Session.categories.isEmpty()) {
            Map2Session.addCategory(Map2Session.nextPgName())
        }
        rows = Map2Session.categories.map { it.copy() }
    }
    // staging points before commit for a row being imported
    var staging by remember { mutableStateOf<List<Map2Point>>(emptyList()) }
    var stagingRowId by remember { mutableStateOf<String?>(null) }
    var showStaging by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf("none") } // none|all|range
    var selectedIdx by remember { mutableStateOf(setOf<Int>()) }
    var rangeA by remember { mutableStateOf("") }
    var rangeB by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("name") }
    var showSymbol by remember { mutableStateOf(true) }
    var showName by remember { mutableStateOf(true) }
    var showCode by remember { mutableStateOf(false) }
    var showElev by remember { mutableStateOf(false) }
    var textSize by remember { mutableStateOf("1") }
    var message by remember { mutableStateOf("") }

    fun syncRowsFromSession() {
        rows = Map2Session.categories.map { it.copy() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val name = uri.lastPathSegment.orEmpty()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val parsed = PointConverter.readBytes(bytes, name)
            if (parsed.isEmpty()) {
                message = "نقطه‌ای خوانده نشد"
                return@rememberLauncherForActivityResult
            }
            val base = System.currentTimeMillis()
            staging = parsed.mapIndexed { i, s ->
                Map2Point(
                    id = "s${base}_$i",
                    name = s.id.ifBlank { "P${i + 1}" },
                    x = s.x, y = s.y, z = s.z,
                    code = s.code
                )
            }
            selectedIdx = emptySet()
            selectMode = "none"
            showStaging = true
            message = "${staging.size} نقطه — گزینش و ثبت کنید"
        } catch (e: Exception) {
            message = "خطا: ${e.message}"
        }
    }

    fun sortedStaging(): List<Map2Point> = when (sortBy) {
        "code" -> staging.sortedBy { it.code }
        "num" -> staging.sortedWith(compareBy({ it.name.toDoubleOrNull() ?: Double.MAX_VALUE }, { it.name }))
        else -> staging.sortedBy { it.name }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E241A),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("نقاط نقشه ۲", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, null, tint = Color.White)
                    }
                }
                if (message.isNotBlank()) {
                    Text(message, color = color, fontSize = 11.sp)
                }

                // ردیف‌های دسته
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(rows, key = { it.id }) { row ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2A3324),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    IconButton(
                                        onClick = {
                                            stagingRowId = row.id
                                            picker.launch(arrayOf("*/*"))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Filled.FileOpen, "فراخوانی", tint = color, modifier = Modifier.size(18.dp)) }

                                    IconButton(
                                        onClick = {
                                            stagingRowId = row.id
                                            staging = row.points
                                            selectedIdx = emptySet()
                                            showStaging = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Filled.List, "لیست", tint = Color.White, modifier = Modifier.size(18.dp)) }

                                    IconButton(
                                        onClick = {
                                            Map2Session.removeCategory(row.id)
                                            syncRowsFromSession()
                                            if (rows.isEmpty()) {
                                                Map2Session.addCategory()
                                                syncRowsFromSession()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Filled.Delete, "حذف", tint = Color(0xFFE57373), modifier = Modifier.size(18.dp)) }

                                    OutlinedTextField(
                                        value = row.name,
                                        onValueChange = { v ->
                                            Map2Session.updateCategory(row.id) { it.copy(name = v) }
                                            syncRowsFromSession()
                                        },
                                        label = { Text("نام دسته", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.width(100.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White)
                                    )
                                    Text("${row.points.size}", color = Color.White, fontSize = 12.sp)
                                    Checkbox(
                                        checked = row.visible,
                                        onCheckedChange = {
                                            Map2Session.updateCategory(row.id) { c -> c.copy(visible = it) }
                                            syncRowsFromSession()
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = color)
                                    )
                                    Text("نمایش", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    item {
                        TextButton(onClick = {
                            Map2Session.addCategory()
                            syncRowsFromSession()
                        }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+ دسته جدید")
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("بستن")
                    }
                    Button(
                        onClick = {
                            // اعمال تنظیمات نمایش پیش‌فرض روی دسته‌ها
                            val ts = textSize.replace(',', '.').toDoubleOrNull() ?: 1.0
                            Map2Session.categories.forEach { c ->
                                Map2Session.updateCategory(c.id) {
                                    it.copy(
                                        showSymbol = showSymbol,
                                        showName = showName,
                                        showCode = showCode,
                                        showElev = showElev,
                                        textSize = ts
                                    )
                                }
                            }
                            onCommitToMap(Map2Session.categories)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        modifier = Modifier.weight(1f)
                    ) { Text("ثبت روی نقشه") }
                }
            }
        }
    }

    if (showStaging) {
        val list = sortedStaging()
        Dialog(
            onDismissRequest = { showStaging = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E241A),
                modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text("نمایش نقاط (${list.size})", fontWeight = FontWeight.Bold, color = Color.White)
                    // ردیف گزینش
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            selectMode = "all"; selectedIdx = list.indices.toSet()
                        }, contentPadding = PaddingValues(4.dp)) { Text("همه", fontSize = 10.sp) }
                        TextButton(onClick = {
                            selectMode = "none"; selectedIdx = emptySet()
                        }, contentPadding = PaddingValues(4.dp)) { Text("هیچ", fontSize = 10.sp) }
                        OutlinedTextField(
                            rangeA, { rangeA = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.width(48.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White),
                            label = { Text("از", fontSize = 9.sp) }
                        )
                        OutlinedTextField(
                            rangeB, { rangeB = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.width(48.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White),
                            label = { Text("تا", fontSize = 9.sp) }
                        )
                        TextButton(onClick = {
                            val a = (rangeA.toIntOrNull() ?: 1).coerceAtLeast(1)
                            val b = (rangeB.toIntOrNull() ?: list.size).coerceAtMost(list.size)
                            selectedIdx = ((a - 1) until b).filter { it in list.indices }.toSet()
                            selectMode = "range"
                        }, contentPadding = PaddingValues(4.dp)) { Text("بازه", fontSize = 10.sp) }
                        TextButton(onClick = { sortBy = "num" }, contentPadding = PaddingValues(4.dp)) {
                            Text("ترتیب عدد", fontSize = 10.sp)
                        }
                        TextButton(onClick = { sortBy = "name" }, contentPadding = PaddingValues(4.dp)) {
                            Text("ترتیب نام", fontSize = 10.sp)
                        }
                        TextButton(onClick = { sortBy = "code" }, contentPadding = PaddingValues(4.dp)) {
                            Text("ترتیب کد", fontSize = 10.sp)
                        }
                        TextButton(
                            onClick = {
                                if (selectedIdx.isNotEmpty()) {
                                    val removeNames = selectedIdx.mapNotNull { list.getOrNull(it)?.id }.toSet()
                                    staging = staging.filter { it.id !in removeNames }
                                    selectedIdx = emptySet()
                                } else {
                                    staging = emptyList()
                                }
                            },
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("پاک", fontSize = 10.sp, color = Color(0xFFE57373)) }
                        TextButton(
                            onClick = {
                                val chosen = if (selectedIdx.isEmpty()) list
                                else selectedIdx.mapNotNull { list.getOrNull(it) }
                                val rid = stagingRowId
                                if (rid != null) {
                                    Map2Session.updateCategory(rid) { c ->
                                        c.copy(points = c.points + chosen)
                                    }
                                } else {
                                    Map2Session.addCategory(points = chosen)
                                }
                                staging = emptyList()
                                showStaging = false
                                syncRowsFromSession()
                                message = "${chosen.size} نقطه به دسته اضافه شد"
                            },
                            contentPadding = PaddingValues(4.dp)
                        ) { Text("ثبت", fontSize = 10.sp, color = color) }
                    }
                    // نمایش اطلاعات
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(showSymbol, { showSymbol = it }, colors = CheckboxDefaults.colors(checkedColor = color))
                        Text("نماد", color = Color.White, fontSize = 10.sp)
                        Checkbox(showName, { showName = it }, colors = CheckboxDefaults.colors(checkedColor = color))
                        Text("شماره", color = Color.White, fontSize = 10.sp)
                        Checkbox(showCode, { showCode = it }, colors = CheckboxDefaults.colors(checkedColor = color))
                        Text("کد", color = Color.White, fontSize = 10.sp)
                        Checkbox(showElev, { showElev = it }, colors = CheckboxDefaults.colors(checkedColor = color))
                        Text("ارتفاع", color = Color.White, fontSize = 10.sp)
                        OutlinedTextField(
                            textSize, { textSize = it },
                            modifier = Modifier.width(64.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White),
                            label = { Text("سایز", fontSize = 9.sp) }
                        )
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(list.size) { idx ->
                            val p = list[idx]
                            val sel = idx in selectedIdx
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (sel) color.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        selectedIdx = if (sel) selectedIdx - idx else selectedIdx + idx
                                    }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(sel, {
                                    selectedIdx = if (it) selectedIdx + idx else selectedIdx - idx
                                }, colors = CheckboxDefaults.colors(checkedColor = color))
                                Column {
                                    if (showName) Text(p.name, color = Color.White, fontSize = 12.sp)
                                    if (showCode) Text(p.code, color = Color.LightGray, fontSize = 10.sp)
                                    if (showElev) Text(String.format("%.3f", p.z), color = Color.LightGray, fontSize = 10.sp)
                                    Text(
                                        String.format("%.3f  %.3f", p.x, p.y),
                                        color = Color.Gray,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showStaging = false }) { Text("بستن لیست") }
                }
            }
        }
    }
}
