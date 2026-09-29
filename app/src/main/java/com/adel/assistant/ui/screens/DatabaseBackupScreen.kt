package com.adel.assistant.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.PlumbStore
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Composable
fun DatabaseBackupScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }

    fun dataDir(): File {
        val d = File(context.filesDir, "data")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun exportZip(): File? {
        return try {
            val dir = dataDir()
            val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
            val hasPlumb = File(context.filesDir, "plumb_projects.json").exists()
            if (files.isEmpty() && !hasPlumb) {
                status = "پایگاهی برای خروجی نیست"
                return null
            }
            val out = File(context.cacheDir, "AdelAssistant_backup.zip")
            ZipOutputStream(BufferedOutputStream(out.outputStream())).use { zos ->
                // همه CSV و JSON داخل data/
                files.forEach { f ->
                    zos.putNextEntry(ZipEntry("data/${f.name}"))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                // شاقولی: JSON پروژه + CSV خروجی
                val plumbJson = File(context.filesDir, "plumb_projects.json")
                if (plumbJson.exists()) {
                    zos.putNextEntry(ZipEntry("plumb_projects.json"))
                    plumbJson.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                try {
                    val csv = PlumbStore.exportCsv(context)
                    zos.putNextEntry(ZipEntry("plumb_projects.csv"))
                    zos.write(csv.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                } catch (_: Exception) {}
                // JSON خام شاقولی از store
                try {
                    val json = PlumbStore.exportJson(context)
                    zos.putNextEntry(ZipEntry("plumb_export.json"))
                    zos.write(json.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                } catch (_: Exception) {}
            }
            // کپی به Documents
            val bytes = out.readBytes()
            FileExport.exportBytesToDocuments(
                context,
                "AdelAssistant_backup.zip",
                bytes,
                "application/zip"
            )
            status = "خروجی: data + شاقولی (CSV/JSON) → Documents/AdelAssistant"
            out
        } catch (e: Exception) {
            status = "خطا: ${e.message}"
            null
        }
    }

    fun importZip(bytes: ByteArray) {
        try {
            val dir = dataDir()
            var n = 0
            ZipInputStream(BufferedInputStream(bytes.inputStream())).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val raw = entry.name.replace("\\", "/")
                        val name = File(raw).name
                        val target = when {
                            name == "plumb_projects.json" || name == "plumb_export.json" ->
                                File(context.filesDir, "plumb_projects.json")
                            name == "plumb_projects.csv" -> File(dir, name)
                            raw.startsWith("data/") -> File(dir, name)
                            else -> File(dir, name)
                        }
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zis.copyTo(it) }
                        n++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            status = "وارد شد: $n فایل"
        } catch (e: Exception) {
            status = "خطای ورود: ${e.message}"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(com.adel.assistant.data.AdelDocuments.OpenDocumentContract()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { importZip(it.readBytes()) }
            } catch (e: Exception) {
                status = "خطا: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "پشتیبان پایگاه داده", color = color, onBack = onBack)
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "خروجی یکجای همه فایل‌های data (پروژه، تونل، گزارش، تسک، …) و ورود مجدد همان بسته.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        val f = exportZip()
                        if (f != null) {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "اشتراک پشتیبان"))
                            } catch (_: Exception) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("خروجی یکجا + اشتراک") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/zip", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ورود یکجا از فایل ZIP") }
            }
        }
        if (status.isNotBlank()) {
            Text(status, color = TextPrimary, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
