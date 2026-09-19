package com.adel.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adel.assistant.data.CsvStore
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.TunnelReportStore.TunnelPoint
import com.adel.assistant.data.filterNumericInput
import com.adel.assistant.data.formatEn
import com.adel.assistant.data.UtmGeo
import com.adel.assistant.data.toDoubleOrNullFa
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor

private val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

@Composable
fun TunnelPointsScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var pointNo by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var z by remember { mutableStateOf("") }
    var elevDiff by remember { mutableStateOf("") }
    var slope by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var isEditMode by remember { mutableStateOf(false) }
    var editingOriginalNo by remember { mutableStateOf<String?>(null) }
    var kmContextText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<TunnelPoint>()) }
    var showMenu by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var myLocationResult by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

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
        pointNo = ""; km = ""; x = ""; y = ""; z = ""; elevDiff = ""; slope = ""; description = ""
        isEditMode = false; editingOriginalNo = null
        results = emptyList()
        statusMsg = ""
    }

    fun autofillFromPoint(p: TunnelPoint) {
        pointNo = p.pointNo; km = p.km.toString()
        x = formatEn("%.3f", p.x); y = formatEn("%.3f", p.y); z = formatEn("%.3f", p.z)
        elevDiff = p.elevDiff; slope = p.slope; description = p.type
    }

    fun findNearestPoint() {
        if (!hasPermission) {
            return // permissionLauncher handles request below
        }
        try {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (provider in lm.getProviders(true)) {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            if (best == null) {
                myLocationResult = "موقعیتی دریافت نشد؛ GPS را روشن کنید"
                return
            }
            val pts = TunnelReportStore.allPoints(context)
            if (pts.isEmpty()) {
                myLocationResult = "نقطه تونل ثبت نشده"
                return
            }
            var nearest = pts.first()
            var minDist = Double.MAX_VALUE
            pts.forEach { p ->
                val (lat, lon) = UtmGeo.toLatLon(p.x, p.y)
                val d = FloatArray(1)
                Location.distanceBetween(best!!.latitude, best!!.longitude, lat, lon, d)
                if (d[0].toDouble() < minDist) {
                    minDist = d[0].toDouble()
                    nearest = p
                }
            }
            results = listOf(nearest)
            autofillFromPoint(nearest)
            myLocationResult = formatEn(
                "نزدیک‌ترین: نقطه %s — کیلومتر %.3f — فاصله حدود %.0f متر (دقت GPS تقریبی)",
                nearest.pointNo, nearest.km, minDist
            )
        } catch (e: SecurityException) {
            myLocationResult = "دسترسی موقعیت داده نشده"
        } catch (e: Exception) {
            myLocationResult = "خطا: ${e.message}"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) findNearestPoint()
    }

    fun requestNearestPoint() {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            findNearestPoint()
        }
    }



    fun search() {
        kmContextText = ""
        val byNo = if (pointNo.isNotBlank()) TunnelReportStore.findByPointNo(context, pointNo) else null
        if (byNo != null) {
            autofillFromPoint(byNo)
            results = listOf(byNo)
            kmContextText = TunnelReportStore.kmContext(context, byNo.km).toText()
            return
        }
        val kmVal = km.toDoubleOrNullFa()
        if (kmVal != null) {
            val byKm = TunnelReportStore.findByKm(context, kmVal)
            if (byKm != null) autofillFromPoint(byKm)
            results = listOfNotNull(byKm)
            kmContextText = TunnelReportStore.kmContext(context, kmVal).toText()
            return
        }
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
        val zVal = z.toDoubleOrNullFa() ?: 0.0
        if (isEditMode && editingOriginalNo != null) {
            val updated = TunnelPoint(pointNo, xVal, yVal, zVal, kmVal, elevDiff, slope, description)
            TunnelReportStore.replacePoint(context, editingOriginalNo!!, updated)
        } else {
            val finalNo = pointNo.ifBlank {
                val nearest = TunnelReportStore.findByKm(context, kmVal)
                if (nearest != null) TunnelReportStore.nextSubPointNo(context, nearest.pointNo.substringBefore(".")) else kmVal.toString()
            }
            TunnelReportStore.savePoint(context, TunnelPoint(finalNo, xVal, yVal, zVal, kmVal, elevDiff, slope, description))
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
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = { clearForm() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "رفرش", tint = Color(0xFFAAB697))
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "ایمپورت/اکسپورت", tint = Color(0xFFAAB697))
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("وارد کردن") }, onClick = {
                    showMenu = false
                    importLauncher.launch(arrayOf("text/*", "*/*"))
                })
                DropdownMenuItem(text = { Text("خارج کردن") }, onClick = {
                    showMenu = false
                    val text = FileExport.readAsCsvText(context, "tunnel_points")
                    val uri = FileExport.exportTextToDocuments(context, "tunnel_points.txt", text)
                    statusMsg = if (uri != null) "در Documents/AdelAssistant ذخیره شد" else "خطا در خارج کردن"
                })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = pointNo, onValueChange = { pointNo = filterNumericInput(it) }, label = { Text("شماره نقطه") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = km, onValueChange = { km = filterNumericInput(it) }, label = { Text("کیلومتراژ") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = x, onValueChange = { x = filterNumericInput(it) }, label = { Text("X") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = y, onValueChange = { y = filterNumericInput(it) }, label = { Text("Y") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = z, onValueChange = { z = filterNumericInput(it) }, label = { Text("Z") },
                keyboardOptions = numberKeyboard, modifier = Modifier.weight(1f)
            )
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
        OutlinedButton(
            onClick = { requestNearestPoint() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("موقعیت من (نزدیک‌ترین نقطه)")
        }
        myLocationResult?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697), modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("نتایج", style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAB697))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            
            if (kmContextText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = color.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        kmContextText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCFD8C8)
                    )
                }
            }

            items(results) { p ->
                val coordTxt = formatEn("X=%.3f  Y=%.3f  Z=%.3f", p.x, p.y, p.z)
                val (lat, lon) = UtmGeo.toLatLon(p.x, p.y)
                val gmaps = UtmGeo.googleMapsUrl(lat, lon)
                val neshan = UtmGeo.neshanIntentUri(lat, lon)
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Background,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { clipboard.setText(AnnotatedString(coordTxt)) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(coordTxt, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("ضربه = کپی مختصات", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7C8A6B))
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = color.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gmaps)))
                                    } catch (_: Exception) {}
                                }
                        ) {
                            Text("Google Maps", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = color)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = color.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(neshan)))
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(formatEn("https://nshn.ir/?lat=%.6f&lng=%.6f", lat, lon)))
                                            )
                                        } catch (_: Exception) {}
                                    }
                                }
                        ) {
                            Text("مسیریاب نشان", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = color)
                        }
                    }
                }
            }
        }
    }
}
