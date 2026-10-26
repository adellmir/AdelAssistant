package com.adel.assistant.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.adel.assistant.data.AppearanceMode
import com.adel.assistant.data.AppearanceStore
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
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    val mode = AppearanceStore.modeState
    val scheme = when (mode) {
        AppearanceMode.Topo -> AppColorScheme.copy(
            onBackground = Color(0xFF111111),
            onSurface = Color(0xFF111111),
            onSurfaceVariant = Color(0xFF2A2A2A),
            onPrimary = Color(0xFF111111),
            onSecondary = Color(0xFF111111),
            onTertiary = Color(0xFF111111),
            primary = Color(0xFF3D5A1A),
            secondary = Color(0xFF2A2A2A),
            surface = Color(0x80B8D4B8),
            surfaceVariant = Color(0x80B8D4B8),
            primaryContainer = Color(0xCC8FAF5E),
            secondaryContainer = Color(0x80B8D4B8)
        )
        AppearanceMode.Iran -> AppColorScheme.copy(
            surface = Color(0xD6141910),
            surfaceVariant = Color(0xC7141910),
            primaryContainer = Color(0xCC8FAF5E),
            secondaryContainer = Color(0xD6141910)
        )
        AppearanceMode.Custom -> AppColorScheme.copy(
            surface = Color(0xB31C2216),
            surfaceVariant = Color(0xA61C2216),
            primaryContainer = Color(0xCC8FAF5E),
            secondaryContainer = Color(0xB31C2216)
        )
        else -> AppColorScheme
    }
    val selection = TextSelectionColors(
        handleColor = WorkPrimary,
        backgroundColor = WorkPrimary.copy(alpha = 0.35f)
    )
    CompositionLocalProvider(LocalTextSelectionColors provides selection) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            content = content
        )
    }
}
