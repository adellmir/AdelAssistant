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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.ExportHub
import com.adel.assistant.data.GsiParser
import com.adel.assistant.data.PointConverter
import com.adel.assistant.data.PointImport
import com.adel.assistant.data.SurveyPoint
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * تخلیه دوربین / توتال‌استیشن از طریق بلوتوث
 *
 * حالت Sanding: دریافت فایل روی SPP، پارس GSI/TXT، خروجی GSI / TXT / CSV / DXF
 * (مشابه جریان Tivan برای ماژول Sanding — بدون پروتکل جاب لیکا)
 */
@SuppressLint("MissingPermission")
@Composable
fun TotalStationDumpScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sppUuid = remember { UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") }

    // 0=عمومی  1=Sanding
    var brand by remember { mutableStateOf(1) }
    var status by remember { mutableStateOf("برند Sanding: دوربین را Pair کن، ارسال فایل را از روی دستگاه شروع کن، بعد اتصال و دریافت.") }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selected by remember { mutableStateOf<BluetoothDevice?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var receiving by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var rawText by remember { mutableStateOf("") }
    var points by remember { mutableStateOf<List<SurveyPoint>>(emptyList()) }
    var socketRef by remember { mutableStateOf<BluetoothSocket?>(null) }
    var receivedBytes by remember { mutableStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }

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
            status = "بلوتوث پشتیبانی نمی‌شود"
            return
        }
        if (!adapter.isEnabled) {
            status = "بلوتوث خاموش است"
            return
        }
        val all = adapter.bondedDevices?.toList().orEmpty()
        // در حالت Sanding دستگاه‌هایی که نام مرتبط دارند بالاتر
        devices = if (brand == 1) {
            all.sortedWith(
                compareByDescending<BluetoothDevice> {
                    val n = (it.name ?: "").lowercase()
                    n.contains("sand") || n.contains("sts") || n.contains("ts") || n.contains("total")
                }.thenBy { it.name ?: it.address }
            )
        } else {
            all.sortedBy { it.name ?: it.address }
        }
        status = if (devices.isEmpty()) {
            "دستگاه جفت‌شده نیست — اول در تنظیمات اندروید Pair کن"
        } else {
            "${devices.size} دستگاه جفت‌شده"
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshBonded() }

    fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    fun parseReceived(text: String): List<SurveyPoint> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val name = when {
            trimmed.lines().any { it.trimStart().startsWith("*") } -> "dump.gsi"
            brand == 1 -> "sanding.txt"
            else -> "dump.txt"
        }
        return try {
            PointImport.readText(trimmed, name)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun saveRaw(text: String) {
        val name = "ts_${if (brand == 1) "sanding" else "raw"}_${stamp()}.txt"
        ExportHub.exportBytes(
            context, name, text.toByteArray(Charsets.UTF_8), "text/plain"
        )
    }

    fun exportPoints(fmt: String) {
        if (points.isEmpty()) {
            status = "نقطه‌ای برای خروجی نیست"
            return
        }
        val base = "sanding_${stamp()}"
        when (fmt) {
            "gsi" -> {
                val gsiPts = points.map {
                    com.adel.assistant.data.GsiPoint(name = it.id, e = it.x, n = it.y, z = it.z, code = it.code)
                }
                val body = try {
                    PointImport.writeGsiList(gsiPts, "gsi")
                } catch (_: Throwable) {
                    points.joinToString("\r\n") { p ->
                        val id = p.id.filter { it.isDigit() }.ifEmpty { "0" }.padStart(16, '0')
                        fun f(v: Double) = String.format(Locale.US, "%.0f", v * 1000.0).padStart(16, '0').takeLast(16)
                        "*11$id 81..10+${f(p.x)} 82..10+${f(p.y)} 83..10+${f(p.z)} "
                    } + "\r\n"
                }
                ExportHub.exportBytes(
                    context, "$base.gsi", body.toByteArray(Charsets.UTF_8), "text/plain"
                )
                status = "GSI ذخیره شد ($base.gsi) — ${points.size} نقطه"
            }
            "txt" -> {
                val body = points.joinToString("\n") {
                    String.format(Locale.US, "%s %.4f %.4f %.4f %s", it.id, it.x, it.y, it.z, it.code)
                } + "\n"
                ExportHub.exportBytes(
                    context, "$base.txt", body.toByteArray(Charsets.UTF_8), "text/plain"
                )
                status = "TXT ذخیره شد — ${points.size} نقطه"
            }
            "csv" -> {
                val body = buildString {
                    append("Point,East,North,Elev,Code\n")
                    points.forEach {
                        append(
                            String.format(
                                Locale.US, "%s,%.4f,%.4f,%.4f,%s\n",
                                it.id, it.x, it.y, it.z, it.code.replace(",", " ")
                            )
                        )
                    }
                }
                ExportHub.exportBytes(
                    context, "$base.csv", body.toByteArray(Charsets.UTF_8), "text/csv"
                )
                status = "CSV ذخیره شد — ${points.size} نقطه"
            }
            "dxf" -> {
                val sb = StringBuilder()
                fun p(c: Int, v: String) { sb.append(c).append("\r\n").append(v).append("\r\n") }
                fun fmt(v: Double) = String.format(Locale.US, "%.4f", v)
                p(0, "SECTION"); p(2, "HEADER"); p(9, "\$ACADVER"); p(1, "AC1009"); p(0, "ENDSEC")
                p(0, "SECTION"); p(2, "TABLES")
                p(0, "TABLE"); p(2, "LAYER"); p(70, "2")
                p(0, "LAYER"); p(2, "POINTS"); p(70, "0"); p(62, "1"); p(6, "CONTINUOUS")
                p(0, "LAYER"); p(2, "CODES"); p(70, "0"); p(62, "3"); p(6, "CONTINUOUS")
                p(0, "ENDTAB"); p(0, "ENDSEC")
                p(0, "SECTION"); p(2, "ENTITIES")
                points.forEach { q ->
                    val s = 0.15
                    p(0, "LINE"); p(8, "POINTS")
                    p(10, fmt(q.x - s)); p(20, fmt(q.y - s)); p(30, fmt(q.z))
                    p(11, fmt(q.x + s)); p(21, fmt(q.y + s)); p(31, fmt(q.z))
                    p(0, "LINE"); p(8, "POINTS")
                    p(10, fmt(q.x - s)); p(20, fmt(q.y + s)); p(30, fmt(q.z))
                    p(11, fmt(q.x + s)); p(21, fmt(q.y - s)); p(31, fmt(q.z))
                    val label = listOf(q.id, q.code).filter { it.isNotBlank() }.joinToString(" ")
                    if (label.isNotBlank()) {
                        p(0, "TEXT"); p(8, "CODES")
                        p(10, fmt(q.x)); p(20, fmt(q.y)); p(30, fmt(q.z))
                        p(40, "0.5"); p(1, label)
                    }
                }
                p(0, "ENDSEC"); p(0, "EOF")
                ExportHub.exportBytes(
                    context, "$base.dxf", sb.toString().toByteArray(Charsets.UTF_8), "application/dxf"
                )
                status = "DXF ذخیره شد — ${points.size} نقطه"
            }
        }
        showExportDialog = false
    }

    fun disconnect() {
        try { socketRef?.close() } catch (_: Exception) {}
        socketRef = null
        connected = false
        receiving = false
    }

    /**
     * اتصال SPP و دریافت تا:
     * - قطع از سمت دستگاه، یا
     * - سکوت بیش از idleMs بعد از حداقل یک بایت (مناسب ارسال فایل Sanding)
     */
    fun connectAndReceive(device: BluetoothDevice) {
        scope.launch {
            connecting = true
            receiving = false
            receivedBytes = 0
            status = "اتصال به ${device.name ?: device.address}…"
            try {
                withContext(Dispatchers.IO) {
                    disconnect()
                    val socket = try {
                        device.createRfcommSocketToServiceRecord(sppUuid)
                    } catch (_: Exception) {
                        device.javaClass
                            .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            .invoke(device, 1) as BluetoothSocket
                    }
                    btAdapter()?.cancelDiscovery()
                    socket.connect()
                    socketRef = socket
                    withContext(Dispatchers.Main) {
                        connected = true
                        receiving = true
                        status = if (brand == 1)
                            "متصل — از روی دوربین Sanding ارسال فایل (GSI/TXT) را بزن"
                        else
                            "متصل — در حال دریافت…"
                    }
                    val input = socket.inputStream
                    val buf = ByteArray(4096)
                    val out = StringBuilder()
                    val idleMs = if (brand == 1) 2500L else 8000L
                    var lastDataAt = System.currentTimeMillis()
                    var gotAny = false
                    // تا وقتی coroutine فعال و receiving
                    while (isActive && receiving) {
                        val available = try { input.available() } catch (_: Exception) { -1 }
                        if (available < 0) break
                        if (available > 0) {
                            val n = input.read(buf, 0, minOf(buf.size, available))
                            if (n <= 0) break
                            gotAny = true
                            lastDataAt = System.currentTimeMillis()
                            val chunk = String(buf, 0, n, Charsets.ISO_8859_1)
                            out.append(chunk)
                            val total = out.length
                            withContext(Dispatchers.Main) {
                                receivedBytes = total
                                if (total % 8000 < n) {
                                    status = "دریافت… ${total} بایت"
                                }
                            }
                        } else {
                            if (gotAny && System.currentTimeMillis() - lastDataAt > idleMs) {
                                // پایان ارسال فایل
                                break
                            }
                            delay(40)
                        }
                    }
                    val text = out.toString()
                    withContext(Dispatchers.Main) {
                        rawText = text
                        if (text.isNotBlank()) {
                            saveRaw(text)
                            val pts = parseReceived(text)
                            points = pts
                            status = if (pts.isNotEmpty()) {
                                "تمام — ${pts.size} نقطه از ${text.length} بایت"
                            } else {
                                "خام ذخیره شد (${text.length} بایت) — نقطه مختصاتی تشخیص داده نشد؛ فایل را در مبدل باز کن"
                            }
                        } else {
                            status = "داده‌ای دریافت نشد — ارسال از روی دوربین را چک کن"
                        }
                        receiving = false
                    }
                }
            } catch (e: IOException) {
                status = "خطای ارتباط: ${e.message ?: "قطع شد"}"
                receiving = false
                connected = false
            } catch (e: Exception) {
                status = "خطا: ${e.message}"
                receiving = false
                connected = false
            } finally {
                connecting = false
                try { socketRef?.close() } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasConnectPermission()) refreshBonded() else requestPermissions()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(12.dp)
    ) {
        ScreenTopBar(title = "تخلیه دوربین", color = color, onBack = onBack)

        // برند
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = brand == 1,
                onClick = {
                    brand = 1
                    status = "حالت Sanding: Pair → ارسال فایل از دوربین → اتصال و دریافت → خروجی"
                },
                label = { Text("Sanding", fontSize = 13.sp) }
            )
            FilterChip(
                selected = brand == 0,
                onClick = {
                    brand = 0
                    status = "حالت عمومی: دریافت جریان خام بلوتوث"
                },
                label = { Text("عمومی", fontSize = 13.sp) }
            )
        }

        if (brand == 1) {
            Text(
                "راهنما Sanding: ۱) بلوتوث دوربین را Pair کنید  ۲) روی دوربین ارسال/Export فایل را بزنید  ۳) اینجا اتصال و دریافت  ۴) خروجی GSI/TXT/CSV/DXF",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Text(status, color = TextPrimary, fontSize = 13.sp)
        if (receivedBytes > 0) {
            Text("حجم: $receivedBytes بایت | نقاط: ${points.size}", color = TextSecondary, fontSize = 11.sp)
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {
                if (!hasConnectPermission()) requestPermissions() else refreshBonded()
            }) { Text("بروز لیست") }
            if (points.isNotEmpty()) {
                Button(
                    onClick = { showExportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) { Text("خروجی (${points.size})") }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(devices, key = { it.address }) { dev ->
                val isSel = selected?.address == dev.address
                val name = dev.name ?: "(بدون نام)"
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) color.copy(alpha = 0.2f) else SurfaceColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = dev }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(name, color = TextPrimary)
                        Text(dev.address, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
            if (points.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("پیش‌نمایش (${points.size})", color = color)
                }
                items(points.take(80), key = { it.id + "_" + it.x }) { p ->
                    Text(
                        "${p.id} | ${p.code} | E=${"%.3f".format(Locale.US, p.x)} N=${"%.3f".format(Locale.US, p.y)} Z=${"%.3f".format(Locale.US, p.z)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (points.size > 80) {
                    item { Text("… و ${points.size - 80} نقطه دیگر", color = TextSecondary) }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        ) {
            Button(
                onClick = {
                    val dev = selected
                    if (dev == null) {
                        status = "اول دستگاه را انتخاب کن"
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
            ) {
                Text(
                    when {
                        connecting -> "اتصال…"
                        receiving -> "دریافت…"
                        else -> "اتصال و دریافت"
                    }
                )
            }
            OutlinedButton(
                onClick = {
                    receiving = false
                    disconnect()
                    status = "قطع شد"
                },
                modifier = Modifier.weight(0.55f)
            ) { Text("قطع") }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("خروجی Sanding / نقاط") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${points.size} نقطه — فرمت را انتخاب کن", fontSize = 13.sp)
                    listOf(
                        "gsi" to "GSI (سازگار Sanding/Leica)",
                        "txt" to "TXT (شماره E N Z کد)",
                        "csv" to "CSV",
                        "dxf" to "DXF نقاط"
                    ).forEach { (k, label) ->
                        Button(
                            onClick = { exportPoints(k) },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("بستن") }
            }
        )
    }
}
