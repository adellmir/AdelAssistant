package com.adel.assistant.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.core.content.ContextCompat
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.PointConverter
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * تخلیه دوربین / توتال‌استیشن از طریق بلوتوث
 * نسخه ۱: اتصال SPP، دریافت جریان داده، وارد کردن فایل GSI/TXT، پیش‌نمایش نقاط
 * لیست جاب داخلی دستگاه (مثل Tivan) نیاز به پروتکل اختصاصی سازنده دارد و در نسخه بعد تکمیل می‌شود.
 */
@SuppressLint("MissingPermission")
@Composable
fun TotalStationDumpScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sppUuid = remember { UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") }

    var status by remember { mutableStateOf("دستگاه را جفت (Pair) کن، بعد از لیست انتخاب کن.") }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selected by remember { mutableStateOf<BluetoothDevice?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var rawText by remember { mutableStateOf("") }
    var points by remember { mutableStateOf<List<SurveyPoint>>(emptyList()) }
    var socketRef by remember { mutableStateOf<BluetoothSocket?>(null) }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun btAdapter(): BluetoothAdapter? {
        val mgr = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as BluetoothManager
        return mgr.adapter
    }

    fun refreshBonded() {
        if (!hasConnectPermission()) {
            status = "مجوز بلوتوث داده نشده"
            return
        }
        val adapter = btAdapter()
        if (adapter == null) {
            status = "بلوتوث در این دستگاه پشتیبانی نمی‌شود"
            return
        }
        if (!adapter.isEnabled) {
            status = "بلوتوث خاموش است — از تنظیمات روشن کن"
            return
        }
        devices = adapter.bondedDevices?.toList()?.sortedBy { it.name ?: it.address } ?: emptyList()
        status = if (devices.isEmpty()) {
            "دستگاه جفت‌شده نیست. اول در تنظیمات اندروید با دوربین Pair کن."
        } else {
            "${devices.size} دستگاه جفت‌شده — یکی را انتخاب کن"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            refreshBonded()
        } else {
            status = "برای تخلیه، مجوز بلوتوث لازم است"
        }
    }

    fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        permissionLauncher.launch(perms)
    }

    fun parseReceived(text: String) {
        rawText = text
        val ext = when {
            text.contains("*11") || text.contains("81..") || text.contains("82..") -> "gsi"
            else -> "txt"
        }
        points = try {
            PointConverter.read(text, ext)
        } catch (_: Exception) {
            emptyList()
        }
        status = if (points.isEmpty()) {
            "داده دریافت شد (${text.length} کاراکتر) ولی نقطه شناخته نشد — فرمت را در مبدل چک کن"
        } else {
            "${points.size} نقطه خوانده شد"
        }
    }

    fun saveReceived(text: String) {
        val ext = if (text.contains("*11")) "gsi" else "txt"
        val name = "dump_${System.currentTimeMillis()}.$ext"
        try {
            FileExport.exportBytesToDocuments(
                context,
                name,
                text.toByteArray(Charsets.UTF_8),
                "text/plain"
            )
            status = "ذخیره شد: Documents/AdelAssistant/$name — ${points.size} نقطه"
        } catch (e: Exception) {
            // fallback cache
            try {
                val f = File(context.getExternalFilesDir(null), name)
                f.writeText(text)
                status = "ذخیره در حافظه اپ: ${f.absolutePath}"
            } catch (e2: Exception) {
                status = "خطا در ذخیره: ${e2.message}"
            }
        }
    }

    fun disconnect() {
        try {
            socketRef?.close()
        } catch (_: Exception) {
        }
        socketRef = null
        connected = false
        receiving = false
    }

    fun connectAndReceive(device: BluetoothDevice) {
        scope.launch {
            connecting = true
            status = "در حال اتصال به ${device.name ?: device.address}..."
            try {
                withContext(Dispatchers.IO) {
                    disconnect()
                    val socket = try {
                        device.createRfcommSocketToServiceRecord(sppUuid)
                    } catch (_: Exception) {
                        // fallback reflection sometimes needed on older devices
                        device.javaClass
                            .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            .invoke(device, 1) as BluetoothSocket
                    }
                    btAdapter()?.cancelDiscovery()
                    socket.connect()
                    socketRef = socket
                    connected = true
                    receiving = true
                    status = "متصل شد — در حال دریافت داده (از دستگاه ارسال/دانلود را بزن)..."
                    val buffer = ByteArray(4096)
                    val out = StringBuilder()
                    val input = socket.inputStream
                    // read with idle timeout loops
                    var idleRounds = 0
                    while (receiving && socket.isConnected && idleRounds < 30) {
                        val available = input.available()
                        if (available > 0) {
                            val n = input.read(buffer, 0, minOf(buffer.size, available))
                            if (n > 0) {
                                out.append(String(buffer, 0, n, Charsets.ISO_8859_1))
                                idleRounds = 0
                                status = "دریافت... ${out.length} بایت"
                            }
                        } else {
                            Thread.sleep(200)
                            idleRounds++
                        }
                    }
                    val text = out.toString()
                    withContext(Dispatchers.Main) {
                        if (text.isNotBlank()) {
                            parseReceived(text)
                            saveReceived(text)
                        } else {
                            status = "اتصالی برقرار شد ولی داده‌ای نیامد. از منوی دوربین ارسال فایل را بزن یا از «از فایل» استفاده کن."
                        }
                        receiving = false
                    }
                }
            } catch (e: IOException) {
                status = "خطای اتصال/دریافت: ${e.message}\nاگر دستگاه پروتکل اختصاصی دارد، فایل را با بلوتوث سیستم به گوشی بفرست و از «از فایل» باز کن."
                connected = false
                receiving = false
            } catch (e: Exception) {
                status = "خطا: ${e.message}"
                connected = false
                receiving = false
            } finally {
                connecting = false
            }
        }
    }

    val openFile = rememberLauncherForActivityResult(
        com.adel.assistant.data.AdelDocuments.OpenDocumentContract()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val name = uri.lastPathSegment ?: "import.txt"
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            parseReceived(text)
            saveReceived(text)
            status = "از فایل وارد شد ($name) — ${points.size} نقطه"
        } catch (e: Exception) {
            status = "خطا در خواندن فایل: ${e.message}"
        }
    }

    LaunchedEffect(Unit) {
        if (hasConnectPermission()) refreshBonded()
        else status = "برای شروع، مجوز بلوتوث را بده"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 16.dp)
    ) {
        ScreenTopBar(title = "تخلیه دوربین", color = color, onBack = onBack)

        Text(
            "سندینگ / توتال‌استیشن از طریق بلوتوث. ابتدا در تنظیمات گوشی Pair کن.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (hasConnectPermission()) refreshBonded() else requestPermissions()
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("مجوز / تازه‌سازی") }
            OutlinedButton(
                onClick = { openFile.launch(arrayOf("*/*", "text/*", "application/octet-stream")) },
                modifier = Modifier.weight(1f)
            ) { Text("از فایل") }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
            Text(
                status,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("دستگاه‌های جفت‌شده", style = MaterialTheme.typography.titleSmall, color = color)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(devices, key = { it.address }) { dev ->
                val name = dev.name ?: "(بدون نام)"
                val isSel = selected?.address == dev.address
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) color.copy(alpha = 0.2f) else SurfaceColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = dev }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(name, color = TextPrimary)
                        Text(dev.address, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
            if (points.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("پیش‌نمایش نقاط (${points.size})", color = color)
                }
                items(points.take(50), key = { it.id + it.x }) { p ->
                    Text(
                        "${p.id} | ${p.code} | X=${p.x} Y=${p.y} Z=${p.z}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (points.size > 50) {
                    item { Text("… و ${points.size - 50} نقطه دیگر", color = TextSecondary) }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Button(
                onClick = {
                    val dev = selected
                    if (dev == null) {
                        status = "اول یک دستگاه از لیست انتخاب کن"
                        return@Button
                    }
                    if (!hasConnectPermission()) {
                        requestPermissions()
                        return@Button
                    }
                    connectAndReceive(dev)
                },
                enabled = !connecting && !receiving,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text(if (connecting || receiving) "در حال کار..." else "اتصال و دریافت") }

            OutlinedButton(
                onClick = {
                    receiving = false
                    disconnect()
                    status = "قطع شد"
                },
                modifier = Modifier.weight(0.6f)
            ) { Text("قطع") }
        }
    }
}
