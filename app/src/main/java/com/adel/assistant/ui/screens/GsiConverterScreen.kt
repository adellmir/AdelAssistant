package com.adel.assistant.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.PointConverter
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import java.io.File

@Composable
fun GsiConverterScreen(color: Color, onBack: () -> Unit, title: String = "مبدل") {
    val context = LocalContext.current
    var sourceName by remember { mutableStateOf("") }
    var sourceExt by remember { mutableStateOf("") }
    var points by remember { mutableStateOf<List<SurveyPoint>>(emptyList()) }
    var target by remember { mutableStateOf("CSV") }
    var message by remember { mutableStateOf("یک فایل انتخاب کن. سپس می‌توانی ردیف‌ها را ویرایش یا گزینش کنی.") }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    // حالت گزینش: false = همه انتخاب‌شده برای خروجی
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }

    fun exportPoints(): List<SurveyPoint> =
        if (!selectMode) points
        else points.filterIndexed { i, _ -> i in selected }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            sourceName = fileName(context, uri)
            sourceExt = sourceName.substringAfterLast('.', "").lowercase()
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            points = PointConverter.read(text, sourceExt)
            selected = points.indices.toSet()
            selectMode = false
            message = if (points.isEmpty()) "نقطه قابل خواندن پیدا نشد."
            else "${points.size} نقطه خوانده شد. پیش‌فرض: انتخاب همه."
        } catch (e: Exception) {
            message = "خطا در خواندن فایل: ${e.message}"
        }
    }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) try {
            val outPts = exportPoints()
            if (outPts.isEmpty()) {
                message = "ردیفی برای خروجی انتخاب نشده"
                return@rememberLauncherForActivityResult
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(PointConverter.write(outPts, target.lowercase()))
            }
            message = "${outPts.size} نقطه با پسوند .$target ذخیره شد."
        } catch (e: Exception) {
            message = "خطا در ذخیره فایل: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = title, color = color, onBack = onBack)

        Button(
            onClick = { openFile.launch(arrayOf("text/*", "application/octet-stream", "application/dxf", "*/*")) },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text("انتخاب فایل") }

        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("CSV", "TXT", "DAT", "DXF", "GSI", "IDX", "KML").forEach { ext ->
                FilterChip(
                    selected = target == ext,
                    onClick = { target = ext },
                    label = { Text(ext) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = !selectMode,
                onClick = {
                    selectMode = false
                    selected = points.indices.toSet()
                    message = "حالت انتخاب همه — همه ردیف‌ها خروجی می‌شوند"
                },
                label = { Text("انتخاب همه") }
            )
            FilterChip(
                selected = selectMode,
                onClick = {
                    selectMode = true
                    if (selected.isEmpty()) selected = points.indices.toSet()
                    message = "حالت گزینش — ردیف‌های تیک‌خورده خروجی می‌شوند"
                },
                label = { Text("گزینش") }
            )
            if (selectMode) {
                TextButton(onClick = { selected = points.indices.toSet() }) { Text("همه") }
                TextButton(onClick = { selected = emptySet() }) { Text("هیچ") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            OutlinedButton(onClick = { showAdd = true }, enabled = points.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("+ افزودن ردیف")
            }
            Button(
                enabled = points.isNotEmpty() && exportPoints().isNotEmpty(),
                onClick = {
                    val base = sourceName.substringBeforeLast(".").ifBlank { "points" }
                    saveFile.launch("$base.${target.lowercase()}")
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("ذخیره خروجی") }
        }

        if (points.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(points, key = { i, p -> "$i-${p.id}" }) { index, p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectMode) {
                            Checkbox(
                                checked = index in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + index else selected - index
                                }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${p.id}  |  ${p.code}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "X=${p.x}  Y=${p.y}  Z=${p.z}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { editIndex = index }) { Text("ویرایش") }
                        TextButton(onClick = {
                            points = points.filterIndexed { i, _ -> i != index }
                            selected = selected.filter { it != index }.map { if (it > index) it - 1 else it }.toSet()
                        }) { Text("حذف") }
                        TextButton(onClick = {
                            val copy = p.copy(id = p.id + "_c")
                            points = points.toMutableList().also { it.add(index + 1, copy) }
                            if (!selectMode) selected = points.indices.toSet()
                        }) { Text("کپی") }
                    }
                    HorizontalDivider()
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    if (editIndex != null) {
        PointEditorDialog(points[editIndex!!], "ویرایش ردیف", onDismiss = { editIndex = null }) { edited ->
            val list = points.toMutableList()
            list[editIndex!!] = edited
            points = list
            editIndex = null
        }
    }
    if (showAdd) {
        PointEditorDialog(null, "افزودن ردیف", onDismiss = { showAdd = false }) { added ->
            points = points + added
            selected = selected + (points.lastIndex)
            showAdd = false
        }
    }
}

@Composable
private fun PointEditorDialog(
    point: SurveyPoint?,
    title: String,
    onDismiss: () -> Unit,
    onSave: (SurveyPoint) -> Unit
) {
    var id by remember(point) { mutableStateOf(point?.id ?: "") }
    var x by remember(point) { mutableStateOf(point?.x?.toString() ?: "") }
    var y by remember(point) { mutableStateOf(point?.y?.toString() ?: "") }
    var z by remember(point) { mutableStateOf(point?.z?.toString() ?: "") }
    var code by remember(point) { mutableStateOf(point?.code ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(id, { id = it }, label = { Text("نام/شماره نقطه") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(x, { x = it }, label = { Text("X") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(y, { y = it }, label = { Text("Y") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(z, { z = it }, label = { Text("ارتفاع Z") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(code, { code = it }, label = { Text("اطلاعات / کد") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val px = x.replace(',', '.').toDoubleOrNull()
                val py = y.replace(',', '.').toDoubleOrNull()
                val pz = z.replace(',', '.').toDoubleOrNull()
                if (id.isNotBlank() && px != null && py != null && pz != null) {
                    onSave(SurveyPoint(id, px, py, pz, code))
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
    )
}

private fun fileName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    } ?: File(uri.path ?: "points.txt").name
