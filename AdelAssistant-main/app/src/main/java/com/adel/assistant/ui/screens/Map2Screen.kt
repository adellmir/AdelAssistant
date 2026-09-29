package com.adel.assistant.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * نقشه ۲ = فضای نمایش نقشه با منوی ساختاریافته (map2Mode).
 * در صورت profileMode (alignment / placement) همان جریان پروفیل روی نقشه فعال می‌شود.
 */
@Composable
fun Map2Screen(
    color: Color,
    onBack: () -> Unit,
    profileMode: String? = null,
    onOpenTopography: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenVolume: () -> Unit = {},
    onOpenAlign: () -> Unit = {},
    onOpenArea: () -> Unit = {},
    onOpenDxf: () -> Unit = {}
) {
    DxfPreviewScreen(
        color = color,
        onBack = onBack,
        profileMode = profileMode,
        onOpenTopography = onOpenTopography,
        map2Mode = true,
        onOpenProfile = onOpenProfile,
        onOpenVolume = onOpenVolume,
        onOpenAlign = onOpenAlign,
        onOpenArea = onOpenArea
    )
}
