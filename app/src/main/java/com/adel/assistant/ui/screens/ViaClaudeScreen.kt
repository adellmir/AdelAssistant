package com.adel.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adel.assistant.ui.ScreenTopBar
import com.adel.assistant.ui.theme.Background

@Composable
fun ViaClaudeScreen(title: String, color: Color, description: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 20.dp)
    ) {
        ScreenTopBar(title = title, color = color, onBack = onBack)
        Spacer(modifier = Modifier.height(24.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6B6B6B))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "برای این کار، فایل موردنظر را در چت با کلود آپلود کن و درخواستت را بگو.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B6B6B)
        )
    }
}
