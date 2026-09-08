package com.adel.assistant.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun GsiConverterScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var sourceName by remember { mutableStateOf("") }
    var sourceExt by remember { mutableStateOf("") }
    var points by remember { mutableStateOf<List<SurveyPoint>>(emptyList()) }
    var target by remember { mutableStateOf("CSV") }
    var message by remember { mutableStateOf("یک فایل انتخاب کن. سپس می‌توانی ردیف‌ها را ویرایش، حذف، اضافه یا کپی کنی.") }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            sourceName = fileName(context, uri)
            sourceExt = sourceName.substringAfterLast('.', "").lowercase()
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            points = PointConverter.read(text, sourceExt)
            message = if (points.isEmpty()) "نقطه قابل خواندن پیدا نشد." else "${points.size} نقطه خوانده شد. برای GSI رکوردهای OCUPAR و RE نادیده گرفته می‌شوند."
        } catch (e: Exception) { message = "خطا در خواندن فایل: ${e.message}" }
    }

    // Binary MIME prevents Android's document provider from automatically appending .txt.
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(PointConverter.write(points, target.lowercase())) }
            message = "فایل با پسوند .$target ذخیره شد."
        } catch (e: Exception) { message = "خطا در ذخیره فایل: ${e.message}" }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp)) {
        ScreenTopBar(title = "مبدل و ویرایشگر نقاط", color = color, onBack = onBack)
        Button(onClick = { openFile.launch(arrayOf("text/*", "application/octet-stream", "application/dxf")) }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) { Text("انتخاب فایل") }
        if (sourceName.isNotBlank()) Text("فایل: $sourceName", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("CSV", "TXT", "DAT", "DXF", "GSI", "IDX").forEach { ext -> FilterChip(selected = target == ext, onClick = { target = ext }, label = { Text(ext) }) }
        }
        Text(message, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAdd = true }, enabled = points.isNotEmpty()) { Text("+ افزودن ردیف") }
            Button(enabled = points.isNotEmpty(), onClick = {
                val base = sourceName.substringBeforeLast('.', "points").ifBlank { "points" }
                saveFile.launch("$base.${target.lowercase()}")
            }, colors = ButtonDefaults.buttonColors(containerColor = color)) { Text("ذخیره خروجی") }
        }
        if (points.isNotEmpty()) {
            Text("ردیف‌ها: ${points.size}  •  لمس ردیف = ویرایش", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(points) { index, p ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editIndex = index }) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("${index + 1}. ${p.id}", style = MaterialTheme.typography.titleSmall)
                                Text("X: ${p.x}   Y: ${p.y}   Z: ${p.z}", style = MaterialTheme.typography.bodySmall)
                                if (p.code.isNotBlank()) Text("اطلاعات: ${p.code}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { points = points.filterIndexed { i, _ -> i != index } }) { Text("حذف") }
                            TextButton(onClick = {
                                val copy = p.copy(id = "${p.id}_copy")
                                points = points.toMutableList().apply { add(index + 1, copy) }
                            }) { Text("کپی") }
                        }
                    }
                }
            }
        } else Spacer(Modifier.weight(1f))
    }

    if (editIndex != null) PointEditorDialog(points[editIndex!!], "ویرایش ردیف", onDismiss = { editIndex = null }) { edited ->
        val list = points.toMutableList(); list[editIndex!!] = edited; points = list; editIndex = null
    }
    if (showAdd) PointEditorDialog(null, "افزودن ردیف", onDismiss = { showAdd = false }) { added ->
        points = points + added; showAdd = false
    }
}

@Composable
private fun PointEditorDialog(point: SurveyPoint?, title: String, onDismiss: () -> Unit, onSave: (SurveyPoint) -> Unit) {
    var id by remember(point) { mutableStateOf(point?.id ?: "") }
    var x by remember(point) { mutableStateOf(point?.x?.toString() ?: "") }
    var y by remember(point) { mutableStateOf(point?.y?.toString() ?: "") }
    var z by remember(point) { mutableStateOf(point?.z?.toString() ?: "") }
    var code by remember(point) { mutableStateOf(point?.code ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            OutlinedTextField(id, { id = it }, label = { Text("نام/شماره نقطه") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(x, { x = it }, label = { Text("X") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(y, { y = it }, label = { Text("Y") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(z, { z = it }, label = { Text("ارتفاع Z") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(code, { code = it }, label = { Text("اطلاعات / کد") }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        TextButton(onClick = {
            val px = x.replace(',', '.').toDoubleOrNull(); val py = y.replace(',', '.').toDoubleOrNull(); val pz = z.replace(',', '.').toDoubleOrNull()
            if (id.isNotBlank() && px != null && py != null && pz != null) onSave(SurveyPoint(id, px, py, pz, code))
        }) { Text("ذخیره") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } })
}

private fun fileName(context: Context, uri: Uri): String = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
    val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
    if (i >= 0 && c.moveToFirst()) c.getString(i) else null
} ?: File(uri.path ?: "points.txt").name
