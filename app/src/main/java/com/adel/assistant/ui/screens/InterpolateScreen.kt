package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sqrt

@Composable
fun InterpolateScreen(color: Color, onBack: () -> Unit) {
    var x1 by remember { mutableStateOf("") }
    var y1 by remember { mutableStateOf("") }
    var z1 by remember { mutableStateOf("") }
    var x2 by remember { mutableStateOf("") }
    var y2 by remember { mutableStateOf("") }
    var z2 by remember { mutableStateOf("") }
    var atDistance by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "درون‌یابی", color = color, onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        Text("نقطه اول (X, Y, Z)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = x1, onValueChange = { x1 = it }, label = { Text("X1") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = y1, onValueChange = { y1 = it }, label = { Text("Y1") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = z1, onValueChange = { z1 = it }, label = { Text("Z1") }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("نقطه دوم (X, Y, Z)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = x2, onValueChange = { x2 = it }, label = { Text("X2") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = y2, onValueChange = { y2 = it }, label = { Text("Y2") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = z2, onValueChange = { z2 = it }, label = { Text("Z2") }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = atDistance,
            onValueChange = { atDistance = it },
            label = { Text("فاصله از نقطه اول برای درون‌یابی ارتفاع (اختیاری)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                try {
                    val dx = x2.toDouble() - x1.toDouble()
                    val dy = y2.toDouble() - y1.toDouble()
                    val dz = z2.toDouble() - z1.toDouble()
                    val horizontalDist = sqrt(dx * dx + dy * dy)
                    val slopeDist = sqrt(dx * dx + dy * dy + dz * dz)
                    val slopePercent = if (horizontalDist != 0.0) (dz / horizontalDist) * 100 else 0.0
                    val slopeDeg = if (horizontalDist != 0.0) atan(dz / horizontalDist) * 180 / PI else 0.0

                    val extra = if (atDistance.isNotBlank()) {
                        val d = atDistance.toDouble()
                        val ratio = if (horizontalDist != 0.0) d / horizontalDist else 0.0
                        val interpolatedZ = z1.toDouble() + dz * ratio
                        "\nارتفاع در فاصله‌ی $d از نقطه اول: %.3f".format(interpolatedZ)
                    } else ""

                    result = "فاصله افقی: %.3f\nفاصله فضایی: %.3f\nاختلاف ارتفاع: %.3f\nشیب: %.2f%%  (%.2f درجه)%s"
                        .format(horizontalDist, slopeDist, dz, slopePercent, slopeDeg, extra)
                } catch (e: Exception) {
                    result = "لطفاً همه‌ی مقادیر را به‌صورت عدد وارد کن"
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("محاسبه")
        }
        Spacer(modifier = Modifier.height(16.dp))
        result?.let {
            Surface(shape = RoundedCornerShape(10.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
