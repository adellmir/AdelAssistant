package com.adel.assistant.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AppColorScheme = darkColorScheme(
    primary = WorkPrimary,
    onPrimary = Color(0xFF12160E),
    secondary = FinancePrimary,
    onSecondary = Color(0xFF12160E),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    error = DangerColor,
    onError = Color.White
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = TextPrimary),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = TextPrimary),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 17.sp, color = TextPrimary),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 16.sp, color = TextPrimary),
    bodyMedium = TextStyle(fontSize = 15.sp, color = TextPrimary),
    bodySmall = TextStyle(fontSize = 13.sp, color = TextSecondary),
    labelLarge = TextStyle(fontSize = 14.sp, color = TextPrimary),
    labelMedium = TextStyle(fontSize = 12.sp, color = TextSecondary),
    labelSmall = TextStyle(fontSize = 11.sp, color = TextMuted)
)

@Composable
fun AdelAssistantTheme(content: @Composable () -> Unit) {
    val selection = TextSelectionColors(
        handleColor = WorkPrimary,
        backgroundColor = WorkPrimary.copy(alpha = 0.35f)
    )
    CompositionLocalProvider(LocalTextSelectionColors provides selection) {
        MaterialTheme(
            colorScheme = AppColorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
