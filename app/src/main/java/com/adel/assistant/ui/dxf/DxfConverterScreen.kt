package com.adel.assistant.ui.dxf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.adel.assistant.data.CodeCategory
import com.adel.assistant.data.CodeSetting
import com.adel.assistant.data.DefaultCodeRules
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.WorkPrimary
import com.adel.assistant.utils.DxfGenerator
import com.adel.assistant.utils.PointFileParser
import java.io.File
import java.io.FileOutputStream

/**
 * صفحه اصلی تبدیل نقاط به DXF
 * جریان: انتخاب فایل → دسته‌بندی کدها → تولید و ذخیره
 */
@Composable
fun DxfConverterScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var stage by remember { mutableStateOf(DxfStage.PICK_FILE) }
    var points by remember { mutableStateOf<List<SurveyPoint>>(emptyList()) }
    var fileName by remember { mutableStateOf("") }
    var parseErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var codeSettings by remember { mutableStateOf<Map<String, CodeSetting>>(emptyMap()) }
    var generatedDxf by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // انتخاب فایل
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val name = getFileName(context, uri) ?: "points.dat"
                fileName = name
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val result = PointFileParser.parse(stream, name)
                    points = result.points
                    parseErrors = result.errors

                    // ساخت تنظیمات پیش‌فرض برای همه کدهای یکتا
                    val uniqueCodes = result.points.map { it.code }.distinct()
                    codeSettings = uniqueCodes.associateWith { code ->
                        DefaultCodeRules.createDefaultSetting(code)
                    }

                    if (result.points.isNotEmpty()) {
                        stage = DxfStage.CATEGORIZE
                        statusMessage = null
                    } else {
                        statusMessage = "هیچ نقطه‌ای خوانده نشد."
                    }
                }
            } catch (e: Exception) {
                statusMessage = "خطا در خواندن فایل: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        ScreenTopBar(
            title = when (stage) {
                DxfStage.PICK_FILE -> "تبدیل نقاط به DXF"
                DxfStage.CATEGORIZE -> "دسته‌بندی کدها"
                DxfStage.RESULT -> "نتیجه"
            },
            color = WorkPrimary,
            onBack = {
                when (stage) {
                    DxfStage.PICK_FILE -> onBack()
                    DxfStage.CATEGORIZE -> stage = DxfStage.PICK_FILE
                    DxfStage.RESULT -> stage = DxfStage.CATEGORIZE
                }
            }
        )

        when (stage) {
            DxfStage.PICK_FILE -> PickFileContent(
                statusMessage = statusMessage,
                onPick = {
                    filePicker.launch(arrayOf("*/*"))
                }
            )

            DxfStage.CATEGORIZE -> CodeCategorizationContent(
                points = points,
                fileName = fileName,
                settings = codeSettings,
                onSettingsChange = { codeSettings = it },
                onConfirm = {
                    // تولید DXF
                    val dxf = DxfGenerator.generate(points, codeSettings)
                    generatedDxf = dxf
                    stage = DxfStage.RESULT
                }
            )

            DxfStage.RESULT -> ResultContent(
                fileName = fileName,
                pointCount = points.size,
                dxfContent = generatedDxf ?: "",
                onSave = { content ->
                    saveAndShareDxf(context, content, fileName)
                },
                onBackToCategorize = { stage = DxfStage.CATEGORIZE }
            )
        }
    }
}

private enum class DxfStage {
    PICK_FILE, CATEGORIZE, RESULT
}

@Composable
private fun PickFileContent(
    statusMessage: String?,
    onPick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Icon(
            Icons.Default.UploadFile,
            contentDescription = null,
            tint = WorkPrimary,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "فایل نقاط را انتخاب کنید",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "پشتیبانی از .dat (ترتیب n y x z d)\nو سایر پسوندها (ترتیب N x y z d)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onPick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WorkPrimary)
        ) {
            Text("انتخاب فایل", style = MaterialTheme.typography.titleMedium)
        }

        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                statusMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ResultContent(
    fileName: String,
    pointCount: Int,
    dxfContent: String,
    onSave: (String) -> Unit,
    onBackToCategorize: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = WorkPrimary,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "DXF با موفقیت ساخته شد",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "فایل: $fileName\nتعداد نقاط: $pointCount\nحجم تقریبی: ${dxfContent.length / 1024} کیلوبایت",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onSave(dxfContent) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WorkPrimary)
        ) {
            Text("ذخیره و اشتراک‌گذاری DXF", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBackToCategorize,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("بازگشت به دسته‌بندی")
        }
    }
}

// ---------- کمکی ----------

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) {
            name = cursor.getString(index)
        }
    }
    return name
}

private fun saveAndShareDxf(context: Context, content: String, originalName: String) {
    try {
        val baseName = originalName.substringBeforeLast(".")
        val outFile = File(context.cacheDir, "${baseName}_map.dxf")
        FileOutputStream(outFile).use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/dxf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TITLE, outFile.name)
        }
        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری DXF"))
    } catch (e: Exception) {
        // در نسخه بعدی می‌توانیم Snackbar نشان دهیم
        e.printStackTrace()
    }
}
