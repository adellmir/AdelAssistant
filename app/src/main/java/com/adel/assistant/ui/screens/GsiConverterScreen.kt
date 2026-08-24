package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background

@Composable
fun GsiConverterScreen(color: Color, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var targetIsTab by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = "مبدل GSI", color = color, onBack = onBack)
        Text(
            "نسخه‌ی فعلی، فایل نقاط با ستون‌های شماره،Y،X،Z،کد (جدا با کاما، تب یا فاصله) را بین فرمت‌های متنی تبدیل می‌کند. رمزگشایی فرمت خام GSI لایکا در فاز بعدی تکمیل می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B6B6B)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("متن نقاط را اینجا پیست کن") },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = targetIsTab, onClick = { targetIsTab = true }, label = { Text("خروجی TXT (تب)") })
            FilterChip(selected = !targetIsTab, onClick = { targetIsTab = false }, label = { Text("خروجی CSV (کاما)") })
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val separator = if (targetIsTab) "\t" else ","
                output = input.lines()
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { line ->
                        line.split(",", "\t", " ").filter { it.isNotBlank() }.joinToString(separator)
                    }
            },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) { Text("تبدیل") }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = output,
            onValueChange = {},
            readOnly = true,
            label = { Text("خروجی") },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )
    }
}
