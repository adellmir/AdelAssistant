package com.adel.assistant.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adel.assistant.data.AdelDocuments
import com.adel.assistant.data.FileExport
import com.adel.assistant.data.PointConverter
import com.adel.assistant.data.ProfileEngine
import com.adel.assistant.data.ProfileResult2
import com.adel.assistant.data.ProfileSurface
import com.adel.assistant.data.VolPoint
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface as SurfaceColor
import com.adel.assistant.ui.theme.TextMuted
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.ToolPrimary
import kotlin.math.max
import kotlin.math.min

private data class ProfileSurfaceSlot(val name: String, val points: List<VolPoint>)

@Composable
fun ProfileScreen(color: Color = ToolPrimary, onBack: () -> Unit) {
    val context = LocalContext.current
    var surfaces by remember { mutableStateOf(listOf<ProfileSurfaceSlot>()) }
    var alignment by remember { mutableStateOf(listOf<com.adel.assistant.data.AlignmentVertex>()) }
    var stepText by remember { mutableStateOf("5") }
    var startText by remember { mutableStateOf("0") }
    var selectedSurface by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<ProfileResult2?>(null) }
    var message by remember { mutableStateOf("حداقل یک سطح را وارد کن، سپس Alignment را روی نقشه رسم کن.") }
    var drawing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showTable by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(AdelDocuments.OpenDocumentContract()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        // پردازش در همین callback سبک است؛ پارسرهای فعلی پروژه برای فایل‌های نقشه‌برداری استفاده می‌شوند.
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val name = (uri.lastPathSegment ?: "surface").substringAfterLast('/').substringBeforeLast('.').ifBlank { "سطح ${surfaces.size + 1}" }
            val pts = PointConverter.readBytes(bytes, name)
            if (pts.size < 3) message = "از فایل «$name» حداقل ۳ نقطه XYZ لازم است."
            else {
                val unique = name.ifBlank { "سطح ${surfaces.size + 1}" }
                surfaces = surfaces + ProfileSurfaceSlot(unique, pts.map { VolPoint(it.id, it.x, it.y, it.z, it.code) })
                selectedSurface = surfaces.lastIndex
                message = "سطح «$unique» با ${pts.size} نقطه اضافه شد."
                result = null
            }
        } catch (e: Exception) { message = "خطا در خواندن فایل: ${e.message}" }
        busy = false
    }

    fun mapBounds(): DoubleArray {
        val pts = surfaces.flatMap { it.points }
        if (pts.isEmpty()) return doubleArrayOf(0.0, 0.0, 100.0, 100.0)
        return doubleArrayOf(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
    }

    fun runProfile() {
        if (surfaces.isEmpty()) { message = "اول سطح وارد کن."; return }
        if (alignment.size < 2) { message = "Alignment حداقل ۲ رأس لازم دارد."; return }
        val step = stepText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 5.0
        val start = startText.replace(',', '.').toDoubleOrNull() ?: 0.0
        busy = true
        val built = surfaces.mapNotNull { ProfileEngine.buildSurface(it.name, it.points) }
        if (built.isEmpty()) message = "TIN هیچ سطحی ساخته نشد."
        else {
            result = ProfileEngine.sample(alignment, built, step, start)
            message = "پروفیل ساخته شد: ${result!!.rows.size} ایستگاه، طول ${"%.2f".format(result!!.length)} متر."
        }
        busy = false
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        ScreenTopBar(title = "پروفیل طولی", color = color, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("افزودن سطح") }
                OutlinedButton(onClick = { alignment = emptyList(); result = null; message = "Alignment پاک شد." }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("پاک‌کردن مسیر") }
            }
            if (surfaces.isNotEmpty()) {
                Text("سطح‌ها", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 8.dp))
                surfaces.forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(if (i == selectedSurface) color.copy(alpha = .12f) else SurfaceColor, RoundedCornerShape(10.dp)).clickable { selectedSurface = i }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, Modifier.weight(1f), color = TextPrimary)
                        Text("${s.points.size} نقطه", color = TextMuted, fontSize = 12.sp)
                        IconButton(onClick = { surfaces = surfaces.filterIndexed { idx, _ -> idx != i }; selectedSurface = min(selectedSurface, max(0, surfaces.lastIndex)); result = null }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(stepText, { stepText = it }, label = { Text("فاصله ایستگاه (m)") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(startText, { startText = it }, label = { Text("چینیج شروع") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Text(if (drawing) "روی نقشه نقاط Alignment را به ترتیب لمس کن؛ دکمه «پایان مسیر» را بزن." else "برای رسم Alignment روی نقشه بزن.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            ProfileMap(
                bounds = mapBounds(), alignment = alignment, drawing = drawing,
                onTap = { x, y -> if (drawing) alignment = alignment + com.adel.assistant.data.AlignmentVertex(x, y) },
                modifier = Modifier.fillMaxWidth().height(330.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Button(onClick = { drawing = !drawing }, enabled = surfaces.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(if (drawing) "پایان مسیر" else "رسم Alignment") }
                Button(onClick = { runProfile() }, enabled = !busy && alignment.size >= 2 && surfaces.isNotEmpty(), modifier = Modifier.weight(1f)) { Icon(Icons.Default.ShowChart, null); Spacer(Modifier.width(4.dp)); Text("ساخت پروفیل") }
            }
            if (alignment.isNotEmpty()) Text("Alignment: ${alignment.size} رأس — طول ${"%.2f".format(ProfileEngine.polylineLength(alignment))} m", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(5.dp))
            Text(message, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(5.dp))
            result?.let { r ->
                ProfileChart(r, surfaces.mapNotNull { ProfileEngine.buildSurface(it.name, it.points) }, color)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedButton(onClick = { showTable = !showTable }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(4.dp)); Text(if (showTable) "بستن جدول" else "جدول ایستگاه‌ها") }
                    OutlinedButton(onClick = { exportCsv(context, r, surfaces) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(4.dp)); Text("خروجی CSV") }
                }
                if (showTable) ProfileTable(r, surfaces)
            }
        }
    }
}

@Composable
private fun ProfileMap(bounds: DoubleArray, alignment: List<com.adel.assistant.data.AlignmentVertex>, drawing: Boolean, onTap: (Double, Double) -> Unit, modifier: Modifier) {
    Canvas(modifier.pointerInput(drawing, bounds) { if (drawing) detectTapGestures { p ->
        val w = size.width.toDouble(); val h = size.height.toDouble(); val pad = 24.0
        val minX=bounds[0]; val minY=bounds[1]; val dx=max(bounds[2]-minX,1e-9); val dy=max(bounds[3]-minY,1e-9)
        val scale=min((w-2*pad)/dx,(h-2*pad)/dy)
        val ox=(w-dx*scale)/2.0; val oy=(h-dy*scale)/2.0
        val x=minX+(p.x-ox)/scale; val y=bounds[3]-(p.y-oy)/scale
        onTap(x,y)
    } }) {
        drawRect(SurfaceColor)
        val w=size.width.toDouble(); val h=size.height.toDouble(); val pad=24.0
        val minX=bounds[0]; val minY=bounds[1]; val dx=max(bounds[2]-minX,1e-9); val dy=max(bounds[3]-minY,1e-9)
        val scale=min((w-2*pad)/dx,(h-2*pad)/dy); val ox=(w-dx*scale)/2.0; val oy=(h-dy*scale)/2.0
        fun sx(x: Double): Float = ((x - minX) * scale + ox).toFloat()
        fun sy(y: Double): Float = ((bounds[3] - y) * scale + oy).toFloat()
        drawRect(Color.LightGray, topLeft=Offset(0f,0f), size=androidx.compose.ui.geometry.Size(size.width,size.height), style=Stroke(1f))
        if (alignment.size >= 2) {
            val path=Path(); alignment.forEachIndexed { i,p -> if(i==0) path.moveTo(sx(p.x),sy(p.y)) else path.lineTo(sx(p.x),sy(p.y)) }
            drawPath(path, color=ToolPrimary, style=Stroke(width=5f))
        }
        alignment.forEachIndexed { i,p -> drawCircle(ToolPrimary, 8f, Offset(sx(p.x),sy(p.y))); if(i==0) drawCircle(Color.White,3f,Offset(sx(p.x),sy(p.y))) }
    }
}

@Composable
private fun ProfileChart(r: ProfileResult2, surfaces: List<ProfileSurface>, color: Color) {
    val vals = r.rows.flatMap { it.elevations.values.filterNotNull() }
    val minZ=(vals.minOrNull() ?: 0.0)-1.0; val maxZ=(vals.maxOrNull() ?: 1.0)+1.0; val dx=max(r.length,1.0)
    Column(Modifier.fillMaxWidth().padding(top=10.dp)) {
        Text("پروفیل طولی", fontWeight=FontWeight.Bold, color=TextPrimary)
        Canvas(Modifier.fillMaxWidth().height(260.dp).background(SurfaceColor, RoundedCornerShape(10.dp))) {
            val left=52f; val right=size.width-12f; val top=16f; val bottom=size.height-28f
            fun px(ch:Double)=left+(ch/dx).toFloat()*(right-left)
            fun py(z:Double)=bottom-((z-minZ)/(maxZ-minZ)).toFloat()*(bottom-top)
            for(i in 0..5){ val y=top+(bottom-top)*i/5f; drawLine(Color.LightGray,Offset(left,y),Offset(right,y),1f) }
            surfaces.forEach { s ->
                val path=Path(); var started=false
                r.rows.forEach { row -> val z=row.elevations[s.name]; if(z!=null){ if(!started){path.moveTo(px(row.chainage),py(z));started=true}else path.lineTo(px(row.chainage),py(z))} }
                if(started) drawPath(path,color=if(s.name==surfaces.firstOrNull()?.name) color else Color.DarkGray,style=Stroke(4f))
            }
        }
        Text("Z: ${"%.2f".format(minZ+1)} تا ${"%.2f".format(maxZ-1)} m | طول: ${"%.2f".format(r.length)} m", color=TextMuted, fontSize=12.sp)
    }
}

@Composable
private fun ProfileTable(r: ProfileResult2, slots: List<ProfileSurfaceSlot>) {
    val surfaces = slots.mapNotNull { ProfileEngine.buildSurface(it.name, it.points) }
    Row(Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth().padding(top=6.dp)) {
        Column(Modifier.border(1.dp, Color.LightGray)) {
            Row { listOf("Station","X","Y").plus(surfaces.map { it.name }).forEach { Text(it, Modifier.width(105.dp).padding(5.dp), fontWeight=FontWeight.Bold, fontSize=11.sp) } }
            r.rows.forEach { row -> Row { Text(ProfileEngine.stationLabel(row.chainage),Modifier.width(105.dp).padding(5.dp),fontSize=11.sp); Text("%.3f".format(row.x),Modifier.width(105.dp).padding(5.dp),fontSize=11.sp); Text("%.3f".format(row.y),Modifier.width(105.dp).padding(5.dp),fontSize=11.sp); surfaces.forEach { s -> Text(row.elevations[s.name]?.let { "%.3f".format(it) } ?: "—",Modifier.width(105.dp).padding(5.dp),fontSize=11.sp) } } }
        }
    }
}

private fun exportCsv(context: Context, r: ProfileResult2, slots: List<ProfileSurfaceSlot>) {
    val surfaces = slots.mapNotNull { ProfileEngine.buildSurface(it.name,it.points) }
    val text = ProfileEngine.csv(r,surfaces)
    FileExport.exportTextToDocuments(context, "profile.csv", text, "text/csv")
}
