package com.adel.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adel.assistant.data.DxfModel
import com.adel.assistant.data.DxfParser
import com.adel.assistant.data.DxfColors
import com.adel.assistant.data.UtmGeo
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

@Composable
fun DxfPreviewScreen(color: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    var model by remember { mutableStateOf<DxfModel?>(null) }
    var message by remember { mutableStateOf("نقشه DXF را وارد کن") }
    var zoneText by remember { mutableStateOf("40") }
    var satellite by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var measureA by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var measureB by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var distanceMsg by remember { mutableStateOf<String?>(null) }
    var myLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) } // UTM x,y
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var tiles by remember { mutableStateOf<List<TileBmp>>(emptyList()) }
    var fitTrigger by remember { mutableStateOf(0) }

    val zone = zoneText.toIntOrNull()?.coerceIn(1, 60) ?: 40

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val m = DxfParser.parse(text)
            model = m
            measureA = null; measureB = null; distanceMsg = null
            fitTrigger++
            message = if (m.isEmpty) "موجودیتی پیدا نشد" else
                "خط: ${m.lines.size} | نقطه: ${m.circles.size} | متن: ${m.texts.size}"
        } catch (e: Exception) {
            message = "خطا در خواندن DXF: ${e.message}"
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    fun readGps() {
        try {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (p in lm.getProviders(true)) {
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            }
            if (best == null) {
                message = "موقعیت GPS دریافت نشد"
                return
            }
            val (e, n) = UtmGeo.fromLatLon(best.latitude, best.longitude, zone)
            myLoc = e to n
            message = "موقعیت من روی نقشه (زون $zone)"
        } catch (_: SecurityException) {
            message = "دسترسی موقعیت داده نشده"
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) readGps()
    }
    fun requestMyLocation() {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        else readGps()
    }

    fun fitToModel(m: DxfModel, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val sx = (w * 0.9f) / m.width().toFloat()
        val sy = (h * 0.9f) / m.height().toFloat()
        scale = min(sx, sy).coerceIn(0.000001f, 1000f)
        val cx = ((m.minX + m.maxX) / 2.0).toFloat()
        val cy = ((m.minY + m.maxY) / 2.0).toFloat()
        offset = Offset(w / 2f - cx * scale, h / 2f + cy * scale)
    }

    // world (UTM x right, y up) -> screen
    fun worldToScreen(x: Double, y: Double): Offset {
        return Offset(
            (x * scale + offset.x).toFloat(),
            ((-y) * scale + offset.y).toFloat()
        )
    }
    fun screenToWorld(sx: Float, sy: Float): Pair<Double, Double> {
        val x = (sx - offset.x) / scale
        val y = -((sy - offset.y) / scale)
        return x.toDouble() to y.toDouble()
    }

    // load satellite tiles when enabled
    LaunchedEffect(satellite, model, scale, offset, canvasSize, zone, fitTrigger) {
        if (!satellite || model == null || canvasSize.x <= 0f) {
            tiles = emptyList()
            return@LaunchedEffect
        }
        val m = model!!
        val corners = listOf(
            screenToWorld(0f, 0f),
            screenToWorld(canvasSize.x, 0f),
            screenToWorld(0f, canvasSize.y),
            screenToWorld(canvasSize.x, canvasSize.y)
        )
        val lats = corners.map { UtmGeo.toLatLon(it.first, it.second, zone).first }
        val lons = corners.map { UtmGeo.toLatLon(it.first, it.second, zone).second }
        val minLat = lats.minOrNull() ?: return@LaunchedEffect
        val maxLat = lats.maxOrNull() ?: return@LaunchedEffect
        val minLon = lons.minOrNull() ?: return@LaunchedEffect
        val maxLon = lons.maxOrNull() ?: return@LaunchedEffect
        val z = estimateZoom(minLat, maxLat, minLon, maxLon, canvasSize.x)
        val loaded = withContext(Dispatchers.IO) {
            loadEsriTiles(minLat, maxLat, minLon, maxLon, z)
        }
        tiles = loaded
    }

    LaunchedEffect(fitTrigger, model, canvasSize) {
        val m = model ?: return@LaunchedEffect
        if (canvasSize.x > 0f) fitToModel(m, canvasSize.x, canvasSize.y)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 12.dp)
    ) {
        ScreenTopBar(title = "پیش‌نمایش نقشه", color = color, onBack = onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = zoneText,
                onValueChange = { zoneText = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text("زون UTM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
                singleLine = true
            )
            Button(
                onClick = { openFile.launch(arrayOf("*/*", "application/dxf", "text/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("وارد کردن نقشه") }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            FilterChip(
                selected = satellite,
                onClick = { satellite = !satellite },
                label = { Text(if (satellite) "ماهواره روشن" else "ماهواره") }
            )
            OutlinedButton(onClick = { requestMyLocation() }) { Text("مکان من") }
            OutlinedButton(onClick = {
                model?.let { fitToModel(it, canvasSize.x, canvasSize.y) }
            }) { Text("Fit") }
            OutlinedButton(onClick = { showLayers = true }, enabled = model != null) { Text("لایه‌ها") }
        }

        Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            "اندازه: نقطه اول و آخر را روی نقشه بزن — فاصله افقی",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        distanceMsg?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = color)
        }

        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (satellite) Color(0xFF1A1A1A) else SurfaceColor,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.000001f, 5000f)
                            offset += pan
                        }
                    }
                    .pointerInput(model, scale, offset) {
                        detectTapGestures { tap ->
                            val (wx, wy) = screenToWorld(tap.x, tap.y)
                            if (measureA == null || measureB != null) {
                                measureA = wx to wy
                                measureB = null
                                distanceMsg = "نقطه اول انتخاب شد — نقطه دوم را بزن"
                            } else {
                                measureB = wx to wy
                                val d = DxfParser.horizontalDistance(measureA!!.first, measureA!!.second, wx, wy)
                                distanceMsg = "فاصله افقی: ${"%.3f".format(java.util.Locale.US, d)} متر"
                            }
                        }
                    }
            ) {
                canvasSize = Offset(size.width, size.height)
                val m = model

                // satellite tiles
                if (satellite && tiles.isNotEmpty()) {
                    tiles.forEach { t ->
                        val (e0, n0) = UtmGeo.fromLatLon(t.latNorth, t.lonWest, zone)
                        val (e1, n1) = UtmGeo.fromLatLon(t.latSouth, t.lonEast, zone)
                        val tl = worldToScreen(e0, n0)
                        val br = worldToScreen(e1, n1)
                        val dstW = (br.x - tl.x)
                        val dstH = (br.y - tl.y)
                        if (dstW > 1 && dstH > 1) {
                            drawImage(
                                image = t.image,
                                dstOffset = androidx.compose.ui.unit.IntOffset(tl.x.roundToInt(), tl.y.roundToInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    dstW.roundToInt().coerceAtLeast(1),
                                    dstH.roundToInt().coerceAtLeast(1)
                                )
                            )
                        }
                    }
                }

                if (m != null) {
                    val layers = m.layers
                    fun layerVisible(name: String) = layers[name]?.visible != false
                    fun layerColor(name: String, entityAci: Int): Color {
                        val info = layers[name]
                        info?.displayColor?.let { return it }
                        val aci = when {
                            entityAci in 1..255 -> entityAci
                            info != null -> info.colorAci
                            else -> 7
                        }
                        return DxfParser.aciToColor(aci)
                    }

                    m.lines.forEach { ln ->
                        if (!layerVisible(ln.layer)) return@forEach
                        val a = worldToScreen(ln.x1, ln.y1)
                        val b = worldToScreen(ln.x2, ln.y2)
                        drawLine(layerColor(ln.layer, ln.color), a, b, strokeWidth = 2.5f)
                    }
                    m.circles.forEach { c ->
                        if (!layerVisible(c.layer)) return@forEach
                        val center = worldToScreen(c.x, c.y)
                        val r = (c.r * scale).toFloat().coerceAtLeast(3f)
                        val col = layerColor(c.layer, c.color)
                        drawCircle(col, radius = r, center = center, style = Stroke(width = 2f))
                        drawLine(col, Offset(center.x - r, center.y - r), Offset(center.x + r, center.y + r), 2f)
                        drawLine(col, Offset(center.x - r, center.y + r), Offset(center.x + r, center.y - r), 2f)
                    }
                    m.texts.forEach { t ->
                        if (!layerVisible(t.layer)) return@forEach
                        val p = worldToScreen(t.x, t.y)
                        val paint = android.graphics.Paint().apply {
                            this.color = android.graphics.Color.BLACK
                            textSize = (t.height * scale).toFloat().coerceIn(18f, 48f)
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(t.text, p.x, p.y, paint)
                    }
                }

                // measure
                measureA?.let { a ->
                    val sa = worldToScreen(a.first, a.second)
                    drawCircle(Color(0xFFFFEB3B), 8f, sa)
                    measureB?.let { b ->
                        val sb = worldToScreen(b.first, b.second)
                        drawCircle(Color(0xFFFFEB3B), 8f, sb)
                        drawLine(Color(0xFFFFEB3B), sa, sb, strokeWidth = 3f)
                    }
                }

                // my location
                myLoc?.let { (ex, ny) ->
                    val p = worldToScreen(ex, ny)
                    drawCircle(Color(0xFF2196F3), 12f, p)
                    drawCircle(Color.White, 5f, p)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showLayers && model != null) {
        AlertDialog(
            onDismissRequest = { showLayers = false },
            title = { Text("لایه‌ها") },
            text = {
                LazyColumn {
                    items(model!!.layers.values.toList(), key = { it.name }) { layer ->
                        var expanded by remember(layer.name) { mutableStateOf(false) }
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = layer.visible,
                                    onCheckedChange = {
                                        layer.visible = it
                                        // force recomposition
                                        model = model!!.copy(layers = model!!.layers.toMutableMap().also { map ->
                                            map[layer.name] = layer
                                        })
                                    }
                                )
                                Text(layer.name, modifier = Modifier.weight(1f), color = TextPrimary)
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .background(
                                            layer.displayColor ?: DxfParser.aciToColor(layer.colorAci),
                                            RoundedCornerShape(4.dp)
                                        )
                                )
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(if (expanded) "بستن" else "رنگ")
                                }
                            }
                            if (expanded) {
                                DxfColors.names.zip(DxfColors.compose).chunked(3).forEach { rowItems ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        rowItems.forEach { (name, col) ->
                                            TextButton(onClick = {
                                                layer.displayColor = col
                                                model = model!!.copy(layers = model!!.layers.toMutableMap().also { map ->
                                                    map[layer.name] = layer
                                                })
                                                expanded = false
                                            }) { Text(name.take(4), color = col) }
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLayers = false }) { Text("بستن") }
            }
        )
    }
}

private data class TileBmp(
    val image: androidx.compose.ui.graphics.ImageBitmap,
    val latNorth: Double,
    val latSouth: Double,
    val lonWest: Double,
    val lonEast: Double
)

private fun estimateZoom(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, screenW: Float): Int {
    val latDiff = (maxLat - minLat).absoluteValue.coerceAtLeast(1e-6)
    val lonDiff = (maxLon - minLon).absoluteValue.coerceAtLeast(1e-6)
    val z = ln(360.0 / lonDiff) / ln(2.0)
    return z.roundToInt().coerceIn(12, 18)
}

private fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
    val n = 1 shl zoom
    val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    val latRad = Math.toRadians(lat)
    val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    return x to y
}

private fun tileToLatLon(x: Int, y: Int, zoom: Int): Pair<Double, Double> {
    val n = 1 shl zoom
    val lon = x.toDouble() / n * 360.0 - 180.0
    val latRad = atan(sinh(PI * (1 - 2.0 * y / n)))
    val lat = Math.toDegrees(latRad)
    return lat to lon
}

private fun loadEsriTiles(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, zoom: Int): List<TileBmp> {
    val (x0, y1) = latLonToTile(minLat, minLon, zoom) // south-west-ish
    val (x1, y0) = latLonToTile(maxLat, maxLon, zoom)
    val minX = min(x0, x1)
    val maxX = max(x0, x1)
    val minY = min(y0, y1)
    val maxY = max(y0, y1)
    // limit tiles
    val result = mutableListOf<TileBmp>()
    var count = 0
    for (x in minX..maxX) {
        for (y in minY..maxY) {
            if (count >= 16) break
            try {
                val url = URL("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$y/$x")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.inputStream.use { input ->
                    val bmp = BitmapFactory.decodeStream(input) ?: return@use
                    val (latN, lonW) = tileToLatLon(x, y, zoom)
                    val (latS, lonE) = tileToLatLon(x + 1, y + 1, zoom)
                    result += TileBmp(bmp.asImageBitmap(), latN, latS, lonW, lonE)
                    count++
                }
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }
    return result
}
