package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background
import kotlin.math.abs
import kotlin.math.sqrt

private data class Pt(val x: Double, val y: Double)

@Composable
fun AreaScreen(color: Color, onBack: () -> Unit) {
    var xInput by remember { mutableStateOf("") }
    var yInput by remember { mutableStateOf("") }
    var points by remember { mutableStateOf(listOf<Pt>()) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "مساحت و محیط", color = color, onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        Text("نقاط زمین را به‌ترتیب اضافه کن (حداقل ۳ نقطه)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B6B6B))
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = xInput, onValueChange = { xInput = it }, label = { Text("X") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = yInput, onValueChange = { yInput = it }, label = { Text("Y") }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val x = xInput.toDoubleOrNull()
                    val y = yInput.toDoubleOrNull()
                    if (x != null && y != null) {
                        points = points + Pt(x, y)
                        xInput = ""
                        yInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("افزودن نقطه") }
            OutlinedButton(
                onClick = { points = emptyList(); result = null },
                modifier = Modifier.weight(1f)
            ) { Text("پاک کردن") }
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(points.size) { i ->
                Text("${i + 1}) X=${points[i].x}  Y=${points[i].y}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = {
                if (points.size < 3) {
                    result = "حداقل ۳ نقطه لازم است"
                } else {
                    var area = 0.0
                    var perimeter = 0.0
                    val n = points.size
                    for (i in 0 until n) {
                        val p1 = points[i]
                        val p2 = points[(i + 1) % n]
                        area += (p1.x * p2.y - p2.x * p1.y)
                        perimeter += sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y))
                    }
                    area = abs(area) / 2.0
                    result = "مساحت: %.2f متر مربع\nمحیط: %.2f متر".format(area, perimeter)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text("محاسبه مساحت و محیط") }

        Spacer(modifier = Modifier.height(12.dp))
        result?.let {
            Surface(shape = RoundedCornerShape(10.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
