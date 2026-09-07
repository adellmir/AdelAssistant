package com.adel.assistant.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    var sourceText by remember { mutableStateOf("") }
    var points by remember { mutableStateOf<List<SurveyPoint>>(emptyList()) }
    var target by remember { mutableStateOf("CSV") }
    var message by remember { mutableStateOf("یک فایل انتخاب کن. فرمت‌های پشتیبانی‌شده: GSI, IDX, CSV, TXT, DAT, DXF") }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                sourceName = fileName(context, uri)
                sourceExt = sourceName.substringAfterLast('.', "").lowercase()
                sourceText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                points = PointConverter.read(sourceText, sourceExt)
                message = if (points.isEmpty()) "نقطه قابل تبدیل پیدا نشد." else "${points.size} نقطه خوانده شد. رکوردهای OCUPAR و RE در GSI نادیده گرفته می‌شوند."
            } catch (e: Exception) { message = "خطا در خواندن فایل: ${e.message}" }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(PointConverter.write(points, target.lowercase())) }
            message = "فایل خروجی ذخیره شد."
        } catch (e: Exception) { message = "خطا در ذخیره فایل: ${e.message}" }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp)) {
        ScreenTopBar(title = "مبدل نقاط", color = color, onBack = onBack)
        Text("GSI، IDX، CSV، TXT، DAT و DXF", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { openFile.launch(arrayOf("text/*", "application/octet-stream", "application/dxf")) }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) { Text("انتخاب فایل") }
        if (sourceName.isNotBlank()) Text("فایل: $sourceName", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Text("فرمت خروجی")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("CSV", "TXT", "DAT", "DXF", "GSI", "IDX").forEach { ext ->
                FilterChip(selected = target == ext, onClick = { target = ext }, label = { Text(ext) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodySmall)
        if (points.isNotEmpty()) Text("نمونه: ${points.take(3).joinToString { "${it.id} (${it.x}, ${it.y}, ${it.z})" }}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Button(enabled = points.isNotEmpty(), onClick = { saveFile.launch("${sourceName.substringBeforeLast('.', "points")}.${target.lowercase()}") }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) { Text("تبدیل و ذخیره فایل") }
    }
}

private fun fileName(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    } ?: File(uri.path ?: "points.txt").name
}
