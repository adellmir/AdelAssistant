package com.adel.assistant.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.GsiParser
import com.adel.assistant.data.GsiPoint
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GsiConverterScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var points by remember { mutableStateOf<List<GsiPoint>>(emptyList()) }
    var status by remember { mutableStateOf("یک فایل GSI انتخاب کنید (هر دو مدل پشتیبانی می‌شود)") }
    var selectAll by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins, Charsets.UTF_8)).readText()
            } ?: ""
            val parsed = GsiParser.parse(text)
            points = parsed
            selected = parsed.map { it.index }.toSet()
            selectAll = true
            status = if (parsed.isEmpty()) {
                "نقطه‌ای پیدا نشد — فایل را بررسی کنید"
            } else {
                "${parsed.size} نقطه خوانده شد (OCUPAR/RE حذف شدند)"
            }
        } catch (e: Exception) {
            status = "خطا در خواندن: ${e.message}"
            points = emptyList()
        } finally {
            busy = false
        }
    }

    fun currentSelection(): List<GsiPoint> {
        return if (selectAll) points else points.filter { it.index in selected }
    }

    fun saveToAdelFolder(fileName: String, body: String): Boolean {
        return try {
            val bytes = body.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/AdelAssistant"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), values
                ) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: return false
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "AdelAssistant"
                )
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun exportTxt() {
        val list = currentSelection()
        if (list.isEmpty()) {
            status = "نقطه‌ای برای خروجی انتخاب نشده"
            return
        }
        val ok = saveToAdelFolder("gsi_export.txt", GsiParser.toTxt(list))
        status = if (ok) "TXT ذخیره شد در Documents/AdelAssistant (${list.size} نقطه)" else "خطا در ذخیره TXT"
    }

    fun exportGsi() {
        val list = currentSelection()
        if (list.isEmpty()) {
            status = "نقطه‌ای برای خروجی انتخاب نشده"
            return
        }
        val ok = saveToAdelFolder("gsi_for_instrument.gsi", GsiParser.toGsiModel2(list))
        status = if (ok) {
            "GSI مدل‌۲ ذخیره شد (${list.size} نقطه) — برای بارگذاری روی دوربین"
        } else {
            "خطا در ذخیره GSI"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مبدل GSI") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("بازگشت", color = color) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1F16),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF12150F)
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(status, color = Color(0xFFB0B8A8), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        picker.launch(arrayOf("*/*", "text/*", "application/octet-stream"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    enabled = !busy
                ) { Text("انتخاب فایل GSI") }

                OutlinedButton(
                    onClick = {
                        selectAll = true
                        selected = points.map { it.index }.toSet()
                    },
                    enabled = points.isNotEmpty()
                ) { Text("همه") }

                OutlinedButton(
                    onClick = {
                        selectAll = false
                        selected = emptySet()
                    },
                    enabled = points.isNotEmpty()
                ) { Text("پاک‌کردن انتخاب") }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportTxt() },
                    enabled = points.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) { Text("خروجی TXT") }

                Button(
                    onClick = { exportGsi() },
                    enabled = points.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("خروجی GSI دوربین") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "خروجی GSI = فقط مختصات (مدل BAHAR) برای بارگذاری روی دستگاه",
                color = Color(0xFF889078),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(points, key = { it.index }) { p ->
                    val checked = selectAll || p.index in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E241A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                selectAll = false
                                selected = if (on) selected + p.index else selected - p.index
                            },
                            colors = CheckboxDefaults.colors(checkedColor = color)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "E ${fmt(p.e)}  N ${fmt(p.n)}  Z ${fmt(p.z)}",
                                color = Color(0xFFB0B8A8),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun fmt(v: Double): String =
    String.format(java.util.Locale.US, "%.3f", v)
