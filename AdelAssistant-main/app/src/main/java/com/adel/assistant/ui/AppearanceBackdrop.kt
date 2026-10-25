package com.adel.assistant.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * پس‌زمینه نما برای کل اپ (به‌جز نقشه‌ها که خودشان Background تیره می‌گذارند).
 */
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
                AssetOrFileBackground(assetName = "bg_iran.jpg", file = null)
                Box(Modifier.fillMaxSize().background(Color(0xFF12160E).copy(alpha = 0.30f)))
            }
            AppearanceMode.Topo -> {
                AssetOrFileBackground(assetName = "bg_topo.jpg", file = null)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.08f)))
            }
            AppearanceMode.Custom -> {
                val f = AppearanceStore.customFile(context)
                val ver = AppearanceStore.customVersion
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
            LocalAppearanceTextMuted provides textMuted
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

/**
 * کاشی/کادر — محو بودن ۲۰٪ کمتر از قبل (آلفا بالاتر = شیشه‌ای پررنگ‌تر)
 * قبلی: ایران ۰٫۵۰ / توپو ۰٫۷۰ / سفارشی ۰٫۷۰
 * جدید: ایران ۰٫۷۰ / توپو ۰٫۹۰ / سفارشی ۰٫۹۰
 */
@Composable
fun appearanceCardColor(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Simple -> Surface
        AppearanceMode.Iran -> Color(0xFF1C2216).copy(alpha = 0.70f)
        AppearanceMode.Topo -> Color.White.copy(alpha = 0.90f)
        AppearanceMode.Custom -> Color(0xFF12160E).copy(alpha = 0.90f)
    }
}

/** رنگ شیشه‌ای فیلدها در همه تم‌ها */
@Composable
fun appearanceFieldContainer(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Simple -> Surface.copy(alpha = 0.85f)
        AppearanceMode.Iran -> Color(0xFF1C2216).copy(alpha = 0.55f)
        AppearanceMode.Topo -> Color.White.copy(alpha = 0.75f)
        AppearanceMode.Custom -> Color(0xFF12160E).copy(alpha = 0.60f)
    }
}

@Composable
fun appearanceTextPrimary(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Topo -> Color.Black
        else -> TextPrimary
    }
}

@Composable
fun appearanceTextSecondary(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Topo -> Color(0xFF222222)
        else -> TextSecondary
    }
}

@Composable
fun appearanceTextMuted(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Topo -> Color(0xFF444444)
        else -> TextMuted
    }
}

@Composable
fun appearanceOnCard(): Color = appearanceTextPrimary()

@Composable
fun appearanceOnCardMuted(): Color = appearanceTextMuted()

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
        unfocusedBorderColor = BorderColor.copy(alpha = 0.7f),
        focusedPlaceholderColor = muted,
        unfocusedPlaceholderColor = muted,
        focusedTrailingIconColor = secondary,
        unfocusedTrailingIconColor = muted
    )
}
