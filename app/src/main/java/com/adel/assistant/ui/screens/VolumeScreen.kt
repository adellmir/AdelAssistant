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

private data class Section(val area: Double, val distanceToNext: Double)

@Composable
fun VolumeScreen(color: Color, onBack: () -> Unit) {
    var areaInput by remember { mutableStateOf("") }
    var distInput by remember { mutableStateOf("") }
    var sections by remember { mutableStateOf(listOf<Section>()) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "محاسبه احجام", color = color, onBack = onBack)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "روش سطح مقاطع متوالی: مساحت هر مقطع را وارد کن، بعد فاصله تا مقطع بعدی را بده.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B6B6B)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = areaInput, onValueChange = { areaInput = it }, label = { Text("مساحت مقطع (m²)") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = distInput, onValueChange = { distInput = it }, label = { Text("فاصله تا بعدی (m)") }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val a = areaInput.toDoubleOrNull()
                    val d = distInput.toDoubleOrNull() ?: 0.0
                    if (a != null) {
                        sections = sections + Section(a, d)
                        areaInput = ""
                        distInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.weight(1f)
            ) { Text("افزودن مقطع") }
            OutlinedButton(
                onClick = { sections = emptyList(); result = null },
                modifier = Modifier.weight(1f)
            ) { Text("پاک کردن") }
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(sections.size) { i ->
                Text("مقطع ${i + 1}: ${sections[i].area} m²  →  ${sections[i].distanceToNext} m", style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = {
                if (sections.size < 2) {
                    result = "حداقل ۲ مقطع لازم است"
                } else {
                    var volume = 0.0
                    for (i in 0 until sections.size - 1) {
                        val a1 = sections[i].area
                        val a2 = sections[i + 1].area
                        val d = sections[i].distanceToNext
                        volume += ((a1 + a2) / 2.0) * d
                    }
                    result = "حجم تخمینی (روش سطح مقاطع متوالی): %.2f متر مکعب".format(volume)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text("محاسبه حجم") }

        Spacer(modifier = Modifier.height(12.dp))
        result?.let {
            Surface(shape = RoundedCornerShape(10.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
