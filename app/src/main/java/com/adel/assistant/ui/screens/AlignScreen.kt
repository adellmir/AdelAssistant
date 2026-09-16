package com.adel.assistant.ui.screens

import android.content.ContentValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.AlignParams
import com.adel.assistant.data.AlignPoint
import com.adel.assistant.data.AlignTransform
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import java.io.File
import java.io.FileOutputStream

/**
 * الاین سری دوم (برداشت) روی سری اول (مرجع)
 * حالت پیش‌فرض: انتقال + دوران
 * اختیاری: مقیاس
 */
@Composable
fun AlignScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current

    var refPoints by remember { mutableStateOf<List<AlignPoint>>(emptyList()) }
    var srcPoints by remember { mutableStateOf<List<AlignPoint>>(emptyList()) }
    var useScale by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("فایل مرجع و فایل برداشت را بخوان؛ نقاط هم‌نام جفت می‌شوند.") }
    var params by remember { mutableStateOf<AlignParams?>(null) }
    var residuals by remember { mutableStateOf<List<String>>(emptyList()) }
    var resultPoints by remember { mutableStateOf<List<AlignPoint>>(emptyList()) }

    fun loadPoints(uri: Uri, isRef: Boolean) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val pts = AlignTransform.parsePoints(text)
            if (pts.isEmpty()) {
                status = "نقطه‌ای در فایل پیدا نشد"
                return
            }
            if (isRef) {
                refPoints = pts
                status = "مرجع: ${pts.size} نقطه"
            } else {
                srcPoints = pts
                status = "برداشت: ${pts.size} نقطه"
            }
            params = null
            residuals = emptyList()
            resultPoints = emptyList()
        } catch (e: Exception) {
            status = "خطا: ${e.message}"
        }
    }

    val pickRef = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        // SAF starts at Documents/AdelAssistant
        if (uri != null) loadPoints(uri, true)
    }
    val pickSrc = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        // SAF starts at Documents/AdelAssistant
        if (uri != null) loadPoints(uri, false)
    }

    fun compute() {
        try {
            val pairs = AlignTransform.matchByName(srcPoints, refPoints)
            if (pairs.size < 2) {
                status = "حداقل ۲ نقطهٔ هم‌نام بین مرجع و برداشت لازم است (الان ${pairs.size})"
                params = null
                return
            }
            val p = AlignTransform.compute(pairs, useScale)
            params = p
            residuals = pairs.map { pair ->
                val t = p.transform(pair.source)
                val dx = t.x - pair.target.x
                val dy = t.y - pair.target.y
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                "${pair.source.name}: Δ=${"%.3f".format(d)} m  (dx=${"%.3f".format(dx)}, dy=${"%.3f".format(dy)})"
            }
            resultPoints = srcPoints.map { p.transform(it) }
            status = "تبدیل با ${pairs.size} نقطهٔ مشترک انجام شد — RMSE=${"%.3f".format(p.residualRms)} m"
        } catch (e: Exception) {
            status = "خطا در محاسبه: ${e.message}"
            params = null
        }
    }

    fun saveResult(): Boolean {
        if (resultPoints.isEmpty()) return false
        val body = AlignTransform.toTxt(resultPoints)
        val name = "align_result.txt"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/AdelAssistant")
                }
                val outUri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                    ?: return false
                context.contentResolver.openOutputStream(outUri)?.use { it.write(body.toByteArray()) } ?: return false
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "AdelAssistant"
                )
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, name)).use { it.write(body.toByteArray()) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "الاین مختصات", color = color, onBack = onBack)

        Text(
            "سری مرجع (ABC) و سری برداشت (a1b1c1…) را بخوان. نقاط هم‌نام جفت می‌شوند. پیش‌فرض: انتقال + دوران؛ در صورت نیاز مقیاس را روشن کن.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { pickRef.launch(com.adel.assistant.data.AdelDocuments.openDocumentIntent("*/*", "text/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("فایل مرجع (${refPoints.size})") }
            OutlinedButton(
                onClick = { pickSrc.launch(com.adel.assistant.data.AdelDocuments.openDocumentIntent("*/*", "text/*")) },
                modifier = Modifier.weight(1f)
            ) { Text("فایل برداشت (${srcPoints.size})") }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("اعمال مقیاس", color = TextPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = useScale,
                onCheckedChange = {
                    useScale = it
                    params = null
                    resultPoints = emptyList()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = color)
            )
        }
        Text(
            if (useScale) "حالت: انتقال + دوران + مقیاس (Helmert)"
            else "حالت: فقط انتقال + دوران (مقیاس = ۱)",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { compute() },
            enabled = refPoints.isNotEmpty() && srcPoints.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text("محاسبه الاین") }

        Spacer(Modifier.height(6.dp))
        Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Text(status, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        }

        params?.let { p ->
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("پارامترهای تبدیل", fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("Tx = ${"%.4f".format(p.tx)}", color = Color.Black, fontSize = 13.sp)
                    Text("Ty = ${"%.4f".format(p.ty)}", color = Color.Black, fontSize = 13.sp)
                    Text("دوران = ${"%.6f".format(p.rotationDeg)} °", color = Color.Black, fontSize = 13.sp)
                    Text(
                        "مقیاس = ${"%.8f".format(p.scale)}" + if (p.useScale) "" else " (ثابت ۱)",
                        color = Color.Black,
                        fontSize = 13.sp
                    )
                    Text("RMSE نقاط کنترل = ${"%.4f".format(p.residualRms)} m", color = Color.Black, fontSize = 13.sp)
                }
            }
        }

        if (residuals.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("باقیمانده روی نقاط مشترک", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 160.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(residuals) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (resultPoints.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("پیش‌نمایش نتیجه (${resultPoints.size} نقطه)", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(resultPoints.take(40)) { pt ->
                    Text(
                        "${pt.name}  X=${"%.3f".format(pt.x)}  Y=${"%.3f".format(pt.y)}  Z=${"%.3f".format(pt.z)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (resultPoints.size > 40) {
                    item { Text("… و ${resultPoints.size - 40} نقطه دیگر", color = TextSecondary) }
                }
            }
            Button(
                onClick = {
                    status = if (saveResult()) "ذخیره شد: Documents/AdelAssistant/align_result.txt"
                    else "خطا در ذخیره"
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) { Text("ذخیره نتیجه (TXT)") }
        }
    }
}
