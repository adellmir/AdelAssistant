package com.adel.assistant.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.adel.assistant.data.AppearanceMode
import com.adel.assistant.data.AppearanceStore
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.BorderColor
import com.adel.assistant.ui.theme.Surface
import com.adel.assistant.ui.theme.TextMuted
import com.adel.assistant.ui.theme.TextPrimary
import com.adel.assistant.ui.theme.TextSecondary
import com.adel.assistant.ui.theme.WorkPrimary

val LocalAppearanceTextPrimary = staticCompositionLocalOf { TextPrimary }
val LocalAppearanceTextSecondary = staticCompositionLocalOf { TextSecondary }
val LocalAppearanceTextMuted = staticCompositionLocalOf { TextMuted }

@Composable
fun appearanceIsLightTheme(): Boolean {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return AppearanceStore.modeState == AppearanceMode.Topo
}

@Composable
fun appearanceIsThemed(): Boolean {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return AppearanceStore.modeState != AppearanceMode.Simple
}

@Composable
fun AppearanceBackdrop(content: @Composable () -> Unit) {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    val mode = AppearanceStore.modeState
    val textPrimary = appearanceTextPrimary()
    val textSecondary = appearanceTextSecondary()
    val textMuted = appearanceTextMuted()
    Box(Modifier.fillMaxSize()) {
        when (mode) {
            AppearanceMode.Simple -> {
                Box(Modifier.fillMaxSize().background(Background))
            }
            AppearanceMode.Iran -> {
                AssetOrFileBackground(assetName = "bg_iran.jpg", file = null, version = 0)
                // لایه محو ۲۰٪ تیره‌تر از قبل
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0D08).copy(alpha = 0.36f)))
            }
            AppearanceMode.Topo -> {
                AssetOrFileBackground(assetName = "bg_topo.jpg", file = null, version = 0)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.08f)))
            }
            AppearanceMode.Custom -> {
                val f = AppearanceStore.customFile(context)
                // نسخه از customVersion یا lastModified
                val ver = AppearanceStore.customVersion.let { v ->
                    if (v > 0) v else (f.lastModified() % Int.MAX_VALUE).toInt()
                }
                if (f.exists()) {
                    AssetOrFileBackground(assetName = null, file = f, version = ver)
                    Box(Modifier.fillMaxSize().background(Color(0xFF12160E).copy(alpha = 0.30f)))
                } else {
                    Box(Modifier.fillMaxSize().background(Background))
                }
            }
        }
        CompositionLocalProvider(
            LocalAppearanceTextPrimary provides textPrimary,
            LocalAppearanceTextSecondary provides textSecondary,
            LocalAppearanceTextMuted provides textMuted,
            LocalContentColor provides textPrimary
        ) {
            content()
        }
    }
}

@Composable
private fun AssetOrFileBackground(assetName: String?, file: java.io.File?, version: Int = 0) {
    val context = LocalContext.current
    val bmp = remember(assetName, file?.absolutePath, file?.length(), version) {
        try {
            val stream = when {
                file != null && file.exists() -> file.inputStream()
                assetName != null -> context.assets.open(assetName)
                else -> null
            } ?: return@remember null
            stream.use {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(Modifier.fillMaxSize().background(Background))
    }
}

@Composable
fun appearanceScreenBg(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Simple -> Background
        else -> Color.Transparent
    }
}

@Composable
fun appearanceCardColor(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Simple -> Surface
        AppearanceMode.Iran -> Color(0xFF141910).copy(alpha = 0.84f)
        AppearanceMode.Topo -> Color.White.copy(alpha = 0.90f)
        AppearanceMode.Custom -> Color(0xFF12160E).copy(alpha = 0.90f)
    }
}

/**
 * پس‌زمینه سربرگ / دکمه / کنترل — در تم ساده شفاف (رفتار قبلی)،
 * در بقیه تم‌ها شیشه‌ای مثل کارت.
 */
@Composable
fun appearanceControlBg(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Simple -> Color.Transparent
        AppearanceMode.Iran -> Color(0xFF141910).copy(alpha = 0.78f)
        AppearanceMode.Topo -> Color.White.copy(alpha = 0.85f)
        AppearanceMode.Custom -> Color(0xFF12160E).copy(alpha = 0.75f)
    }
}

@Composable
fun appearanceFieldContainer(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Simple -> Surface.copy(alpha = 0.85f)
        AppearanceMode.Iran -> Color(0xFF141910).copy(alpha = 0.66f)
        AppearanceMode.Topo -> Color.White.copy(alpha = 0.75f)
        AppearanceMode.Custom -> Color(0xFF12160E).copy(alpha = 0.60f)
    }
}

@Composable
fun appearanceTextPrimary(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Topo -> Color(0xFF111111)
        else -> TextPrimary
    }
}

@Composable
fun appearanceTextSecondary(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Topo -> Color(0xFF2A2A2A)
        else -> TextSecondary
    }
}

@Composable
fun appearanceTextMuted(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Topo -> Color(0xFF3D3D3D)
        else -> TextMuted
    }
}

@Composable
fun appearanceIconTint(): Color = appearanceTextPrimary()

@Composable
fun appearanceOnCard(): Color = appearanceTextPrimary()

@Composable
fun appearanceOnCardMuted(): Color = appearanceTextMuted()

@Composable
fun appearanceFilledButtonColors(accent: Color = WorkPrimary) =
    ButtonDefaults.buttonColors(
        containerColor = if (appearanceIsThemed()) {
            accent.copy(alpha = 0.88f)
        } else {
            accent
        },
        contentColor = Color.White
    )

@Composable
fun appearanceOutlinedButtonColors(accent: Color = WorkPrimary) =
    ButtonDefaults.outlinedButtonColors(
        containerColor = appearanceControlBg(),
        contentColor = if (appearanceIsLightTheme()) Color(0xFF111111) else appearanceTextPrimary()
    )

@Composable
fun appearanceFieldColors(): TextFieldColors {
    val container = appearanceFieldContainer()
    val text = appearanceTextPrimary()
    val secondary = appearanceTextSecondary()
    val muted = appearanceTextMuted()
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container.copy(alpha = 0.5f),
        focusedTextColor = text,
        unfocusedTextColor = text,
        disabledTextColor = muted,
        focusedLabelColor = secondary,
        unfocusedLabelColor = muted,
        cursorColor = WorkPrimary,
        focusedBorderColor = WorkPrimary.copy(alpha = 0.85f),
        unfocusedBorderColor = if (appearanceIsLightTheme()) Color(0xFF666666) else BorderColor.copy(alpha = 0.7f),
        focusedPlaceholderColor = muted,
        unfocusedPlaceholderColor = muted,
        focusedTrailingIconColor = secondary,
        unfocusedTrailingIconColor = muted,
        focusedLeadingIconColor = secondary,
        unfocusedLeadingIconColor = muted
    )
}
