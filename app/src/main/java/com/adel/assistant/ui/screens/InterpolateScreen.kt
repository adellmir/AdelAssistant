package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.sqrt

private fun fmt(v: Double) = "%.3f".format(v)

@Composable
fun InterpolateScreen(color: Color, onBack: () -> Unit) {
    var x1 by remember { mutableStateOf("") }
    var y1 by remember { mutableStateOf("") }
    var z1 by remember { mutableStateOf("") }
    var x2 by remember { mutableStateOf("") }
    var y2 by remember { mutableStateOf("") }
    var z2 by remember { mutableStateOf("") }
    var atDistance by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun calculate() {
        val aX1 = x1.toDoubleOrNull(); val aY1 = y1.toDoubleOrNull(); val aZ1 = z1.toDoubleOrNull()
        val aX2 = x2.toDoubleOrNull(); val aY2 = y2.toDoubleOrNull(); val aZ2 = z2.toDoubleOrNull()
        if (listOf(aX1, aY1, aZ1, aX2, aY2, aZ2).any { it == null }) {
            error = "لطفاً مختصات هر دو نقطه را کامل و عددی وارد کن"
            result = null
            return
        }
        val dx = aX2!! - aX1!!
        val dy = aY2!! - aY1!!
        val dz = aZ2!! - aZ1!!
        val horizontal = sqrt(dx * dx + dy * dy)
        val spatial = sqrt(horizontal * horizontal + dz * dz)
        val slopePercent = if (horizontal > 0.0) dz / horizontal * 100.0 else 0.0
        val slopeDegree = if (horizontal > 0.0) atan(dz / horizontal) * 180.0 / PI else 0.0

        val text = StringBuilder()
        text.append("فاصله افقی: ${fmt(horizontal)} متر\n")
        text.append("فاصله فضایی: ${fmt(spatial)} متر\n")
        text.append("اختلاف ارتفاع (نقطه ۲ - نقطه ۱): ${fmt(dz)} متر\n")
        text.append("شیب: ${"%.2f".format(slopePercent)}٪\n")
        text.append("زاویه شیب: ${"%.2f".format(slopeDegree)} درجه")

        if (atDistance.isNotBlank()) {
            val d = atDistance.toDoubleOrNull()
            if (d == null || d < 0.0 || d > horizontal) {
                error = "فاصله نقطه مجهول باید بین صفر و ${fmt(horizontal)} متر باشد"
                result = null
                return
            }
            val r = if (horizontal == 0.0) 0.0 else d / horizontal
            val ix = aX1 + dx * r
            val iy = aY1 + dy * r
            val iz = aZ1 + dz * r
            text.append("\n\nنقطه مجهول در فاصله ${fmt(d)} متر از نقطه اول:\n")
            text.append("X = ${fmt(ix)}\nY = ${fmt(iy)}\nZ = ${fmt(iz)}\n")
            text.append("اختلاف ارتفاع از نقطه اول: ${fmt(iz - aZ1)} متر\n")
            text.append("فاصله باقی‌مانده تا نقطه دوم: ${fmt(horizontal - d)} متر")
        }

        if (interval.isNotBlank()) {
            val step = interval.toDoubleOrNull()
            if (step == null || step <= 0.0) {
                error = "فاصله تقسیم باید یک عدد بزرگ‌تر از صفر باشد"
                result = null
                return
            }
            val count = floor(horizontal / step).toInt()
            text.append("\n\nنقاط میانی هر ${fmt(step)} متر:")
            if (count == 0) text.append("\nفاصله واردشده از طول مسیر بزرگ‌تر است.")
            for (i in 1..count) {
                val d = i * step
                if (d >= horizontal - 1e-9) break
                val r = d / horizontal
                text.append("\n${i}. X=${fmt(aX1 + dx*r)}, Y=${fmt(aY1 + dy*r)}, Z=${fmt(aZ1 + dz*r)}")
            }
        }
        error = null
        result = text.toString()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "درون‌یابی", color = color, onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Text("نقطه اول (X, Y, Z)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(x1, { x1 = it }, label = { Text("X1") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(y1, { y1 = it }, label = { Text("Y1") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(z1, { z1 = it }, label = { Text("Z1") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text("نقطه دوم (X, Y, Z)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(x2, { x2 = it }, label = { Text("X2") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(y2, { y2 = it }, label = { Text("Y2") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(z2, { z2 = it }, label = { Text("Z2") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(atDistance, { atDistance = it }, label = { Text("فاصله نقطه مجهول از نقطه اول (متر)") }, supportingText = { Text("برای درون‌یابی یک نقطه بین دو نقطه") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(interval, { interval = it }, label = { Text("تقسیم مسیر با فاصله مساوی (متر) - اختیاری") }, supportingText = { Text("مثلاً 10 برای ساخت نقاط میانی هر 10 متر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = { calculate() }, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.fillMaxWidth()) { Text("محاسبه و درون‌یابی") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
        result?.let {
            Spacer(Modifier.height(14.dp))
            Surface(color = Color.White, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1C1C1C))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
