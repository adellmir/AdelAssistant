package com.adel.assistant.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.AlignTransform
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.GsiParser
import com.adel.assistant.data.GsiPoint
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import java.io.File

@Composable
fun AlignScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var controlPts by remember { mutableStateOf<List<GsiPoint>>(emptyList()) } // نقاط کنترل مشترک (هدف)
    var surveyPts by remember { mutableStateOf<List<GsiPoint>>(emptyList()) }  // نقاطی که باید الاین شوند
    var status by remember { mutableStateOf("") }
    var useScale by remember { mutableStateOf(true) }
    var useAverage by remember { mutableStateOf(false) }
    var baseName by remember { mutableStateOf<String?>(null) } // نقطه مرجع اولیه از مشترک‌ها

    fun parseFile(uri: Uri): List<GsiPoint> {
        val name = uri.lastPathSegment?.lowercase() ?: ""
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return when {
            name.endsWith(".dat") -> GsiParser.parseDat(text)
            name.endsWith(".gsi") || text.trimStart().startsWith("*11") -> GsiParser.parse(text)
            else -> GsiParser.parseTxt(text).ifEmpty { GsiParser.parse(text) }
        }
    }

    fun adelInitialUri(): Uri? {
        return try {
            val path = "primary:Documents/AdelAssistant"
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", path)
        } catch (_: Exception) {
            null
        }
    }

    val pickControl = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        try {
            controlPts = parseFile(uri)
            status = "کنترل: ${controlPts.size} نقطه"
            baseName = null
        } catch (e: Exception) {
            status = "خطا کنترل: ${e.message}"
        }
    }
    val pickSurvey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        try {
            surveyPts = parseFile(uri)
            status = "برداشت: ${surveyPts.size} نقطه"
            baseName = null
        } catch (e: Exception) {
            status = "خطا برداشت: ${e.message}"
        }
    }

    val commonNames = remember(controlPts, surveyPts) {
        val c = controlPts.map { it.name.trim().lowercase() }.toSet()
        surveyPts.map { it.name }.filter { it.trim().lowercase() in c }.distinct()
    }

    fun openPicker(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
        // EXTRA_INITIAL_URI از طریق OpenDocument مستقیم نیست؛ با Intent سفارشی:
        launcher.launch(arrayOf("*/*", "text/*", "application/octet-stream"))
    }

    fun applyAlign() {
        if (controlPts.isEmpty() || surveyPts.isEmpty()) {
            status = "هر دو فایل کنترل و برداشت لازم است"
            return
        }
        if (commonNames.size < 2) {
            status = "حداقل ۲ نقطه مشترک لازم است (الان ${commonNames.size})"
            return
        }
        val base = baseName ?: commonNames.first()
        val dir = commonNames.firstOrNull { !it.equals(base, true) } ?: run {
            status = "نقطه جهت پیدا نشد"
            return
        }
        val baseCtrl = controlPts.find { it.name.equals(base, true) } ?: run {
            status = "مبنا در کنترل نیست"
            return
        }
        val dirCtrl = controlPts.find { it.name.equals(dir, true) } ?: run {
            status = "جهت در کنترل نیست"
            return
        }
        val res = AlignTransform.alignSimple(
            points = surveyPts,
            baseName = base,
            dirName = dir,
            targetBase = Triple(baseCtrl.e, baseCtrl.n, baseCtrl.z),
            targetDir = Triple(dirCtrl.e, dirCtrl.n, dirCtrl.z),
            useScale = useScale,
            useAverage = useAverage
        )
        surveyPts = res.points
        status = res.message
    }

    fun export(kind: String) {
        if (surveyPts.isEmpty()) {
            status = "نقطه‌ای برای خروجی نیست"
            return
        }
        val ok = when (kind) {
            "txt" -> FileExport.exportTextToDocuments(context, "aligned.txt", GsiParser.toTxt(surveyPts)) != null
            "dat" -> FileExport.exportTextToDocuments(context, "aligned.dat", GsiParser.toDat(surveyPts)) != null
            "dxf" -> FileExport.exportTextToDocuments(context, "aligned.dxf", GsiParser.toDxf(surveyPts), "application/dxf") != null
            else -> false
        }
        status = if (ok) "ذخیره در Documents/AdelAssistant: $kind" else "خطا در ذخیره"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "الاین / هم‌مختصات", color = color, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (status.isNotBlank()) Text(status, color = TextSecondary, fontSize = 13.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { openPicker(pickControl) },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.weight(1f)
                ) { Text("فایل کنترل\n(${controlPts.size})", fontSize = 12.sp) }
                Button(
                    onClick = { openPicker(pickSurvey) },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.weight(1f)
                ) { Text("فایل برداشت\n(${surveyPts.size})", fontSize = 12.sp) }
            }

            Text("نقاط مشترک — نقطه مرجع اولیه را تیک بزن", color = TextPrimary, fontSize = 13.sp)
            if (commonNames.isEmpty()) {
                Text("هنوز نقطه مشترکی نیست", color = TextSecondary, fontSize = 12.sp)
            } else {
                Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        commonNames.forEach { name ->
                            val ctrl = controlPts.find { it.name.equals(name, true) }
                            val srv = surveyPts.find { it.name.equals(name, true) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = baseName?.equals(name, true) == true,
                                    onCheckedChange = { on -> baseName = if (on) name else null },
                                    colors = CheckboxDefaults.colors(checkedColor = color)
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(name, color = TextPrimary)
                                    if (ctrl != null && srv != null) {
                                        Text(
                                            String.format(java.util.Locale.US, "کنترل E%.1f  |  برداشت E%.1f", ctrl.e, srv.e),
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(useScale, { useScale = it }, colors = CheckboxDefaults.colors(checkedColor = color))
                Text("مقیاس", color = TextPrimary)
                Spacer(Modifier.width(12.dp))
                Checkbox(useAverage, { useAverage = it }, colors = CheckboxDefaults.colors(checkedColor = color))
                Text("میانگین‌گیری", color = TextPrimary)
            }

            Button(
                onClick = { applyAlign() },
                enabled = surveyPts.isNotEmpty() && controlPts.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth()
            ) { Text("اعمال الاین") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { export("txt") }, enabled = surveyPts.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("TXT") }
                OutlinedButton(onClick = { export("dat") }, enabled = surveyPts.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("DAT") }
                OutlinedButton(onClick = { export("dxf") }, enabled = surveyPts.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("DXF") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
