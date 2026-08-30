package com.adel.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CsvStore
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.TunnelReportStore.TunnelPoint
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

@Composable
fun TunnelPointsScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current

    var pointNo by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var elevDiff by remember { mutableStateOf("") }
    var slope by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var isEditMode by remember { mutableStateOf(false) }
    var editingOriginalNo by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(listOf<TunnelPoint>()) }
    var showMenu by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = input.bufferedReader().readText()
                    CsvStore.importRawText(context, "tunnel_points", text)
                    statusMsg = "فایل نقاط با موفقیت وارد شد"
                }
            } catch (e: Exception) { statusMsg = "خطا در وارد کردن فایل" }
        }
    }

    fun clearForm() {
        pointNo = ""; km = ""; x = ""; y = ""; elevDiff = ""; slope = ""; description = ""
        isEditMode = false; editingOriginalNo = null
    }

    fun autofillFromPoint(p: TunnelPoint) {
        pointNo = p.pointNo; km = p.km.toString(); x = "%.3f".format(p.x); y = "%.3f".format(p.y)
        elevDiff = p.elevDiff; slope = p.slope; description = p.type
    }

    fun search() {
        val byNo = if (pointNo.isNotBlank()) TunnelReportStore.findByPointNo(context, pointNo) else null
        if (byNo != null) { autofillFromPoint(byNo); results = listOf(byNo); return }
        val byKm = km.toDoubleOrNullFa()?.let { TunnelReportStore.findByKm(context, it) }
        if (byKm != null) { autofillFromPoint(byKm); results = listOf(byKm); return }
        if (description.isNotBlank()) {
            results = TunnelReportStore.searchByKeyword(context, description)
            return
        }
        results = emptyList()
    }

    fun register() {
        val kmVal = km.toDoubleOrNullFa() ?: return
        val xVal = x.toDoubleOrNullFa() ?: 0.0
        val yVal = y.toDoubleOrNullFa() ?: 0.0
        if (isEditMode && editingOriginalNo != null) {
            val updated = TunnelPoint(pointNo, xVal, yVal, 0.0, kmVal, elevDiff, slope, description)
            TunnelReportStore.replacePoint(context, editingOriginalNo!!, updated)
        } else {
            val finalNo = pointNo.ifBlank {
                val nearest = TunnelReportStore.findByKm(context, kmVal)
                if (nearest != null) TunnelReportStore.nextSubPointNo(context, nearest.pointNo.substringBefore(".")) else kmVal.toString()
            }
            TunnelReportStore.savePoint(context, TunnelPoint(finalNo, xVal, yVal, 0.0, kmVal, elevDiff, slope, description))
        }
        statusMsg = "ثبت شد"
        clearForm()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        Box {
            ScreenTopBar(title = "نقاط تونل", color = color, onBack = onBack)
            IconButton(onClick = { showMenu = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Filled.Settings, contentDescription = "ایمپورت/اکسپورت", tint = Color(0xFFAAB697))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("وارد کردن") }, onClick = {
                    showMenu = false
                    importLauncher.launch(arrayOf("text/*", "*/*"))
                })
                DropdownMenuItem(text = { Text("خارج کردن") }, onClick = {
                    showMenu = false
                    val f = CsvStore.getFile(context, "tunnel_points")
                    statusMsg = "مسیر فایل: ${f.absolutePath}"
                })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = pointNo, onValueChange = { pointNo = it }, label = { Text("شماره نقطه") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = km, onValueChange = { km = it }, label = { Text("کیلومتراژ") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = x, onValueChange = { x = it }, label = { Text("X") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = y, onValueChange = { y = it }, label = { Text("Y") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = elevDiff, onValueChange = { elevDiff = it }, label = { Text("اختلاف‌تراز") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = slope, onValueChange = { slope = it }, label = { Text("شیب") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("توضیحات (نوع نقطه)") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { if (isEditMode) register() else search() },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isEditMode) "ثبت" else "جستجو") }

        if (statusMsg.isNotBlank()) {
            Text(statusMsg, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697), modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("نتایج", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results) { p ->
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("نقطه ${p.pointNo} — کیلومتر ${p.km}", style = MaterialTheme.typography.bodySmall)
                            Text(p.type, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C8A6B))
                        }
                        IconButton(onClick = {
                            autofillFromPoint(p)
                            isEditMode = true
                            editingOriginalNo = p.pointNo
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = color)
                        }
                    }
                }
            }
        }
    }
}
