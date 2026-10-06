package com.adel.assistant.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * نقشه ۲ = همان فضای «نمایش نقشه» با منوی ساختاریافته
 * (فایل / نقاط / ترسیم / اندازه / ویرایش / سه‌بعدی / نمایش).
 */
@Composable
fun Map2Screen(
    color: Color,
    onBack: () -> Unit,
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
        profileMode = null,
        onOpenTopography = onOpenTopography,
        map2Mode = true,
        onOpenProfile = onOpenProfile,
        onOpenVolume = onOpenVolume,
        onOpenAlign = onOpenAlign,
        onOpenArea = onOpenArea
    )
}
