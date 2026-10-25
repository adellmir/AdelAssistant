package com.adel.assistant.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.adel.assistant.data.AppearanceMode
import com.adel.assistant.data.AppearanceStore
import com.adel.assistant.ui.theme.Background
import com.adel.assistant.ui.theme.Surface

/**
 * پس‌زمینه نما برای کل اپ (به‌جز نقشه‌ها که خودشان Background تیره می‌گذارند).
 */
@Composable
fun AppearanceBackdrop(content: @Composable () -> Unit) {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    val mode = AppearanceStore.modeState
    Box(Modifier.fillMaxSize()) {
        when (mode) {
            AppearanceMode.Simple -> {
                Box(Modifier.fillMaxSize().background(Background))
            }
            AppearanceMode.Iran -> {
                AssetOrFileBackground(assetName = "bg_iran.jpg", file = null)
                // محو خاکستری ~۳۰٪
                Box(Modifier.fillMaxSize().background(Color(0xFF12160E).copy(alpha = 0.30f)))
            }
            AppearanceMode.Topo -> {
                AssetOrFileBackground(assetName = "bg_topo.jpg", file = null)
                // تصویر واضح؛ لایه خیلی کم برای یکدستی
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.08f)))
            }
            AppearanceMode.Custom -> {
                val f = AppearanceStore.customFile(context)
                if (f.exists()) {
                    AssetOrFileBackground(assetName = null, file = f)
                    Box(Modifier.fillMaxSize().background(Color(0xFF12160E).copy(alpha = 0.30f)))
                } else {
                    Box(Modifier.fillMaxSize().background(Background))
                }
            }
        }
        content()
    }
}

@Composable
private fun AssetOrFileBackground(assetName: String?, file: java.io.File?) {
    val context = LocalContext.current
    val bmp = remember(assetName, file?.absolutePath, file?.length()) {
        try {
            val stream = when {
                file != null && file.exists() -> file.inputStream()
                assetName != null -> context.assets.open(assetName)
                else -> null
            } ?: return@remember null
            stream.use { BitmapFactory.decodeStream(it) }
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

/** رنگ پس‌زمینه صفحه: در حالت تم‌دار شفاف تا تصویر دیده شود */
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
 * رنگ کاشی/کارت شیشه‌ای:
 * - ساده: Surface فعلی
 * - ایران: شیشه‌ای تیره ~۵۰٪
 * - توپو / سفارشی: سفید ~۷۰٪ محو برای خوانایی روی تصویر شلوغ
 */
@Composable
fun appearanceCardColor(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Simple -> Surface
        AppearanceMode.Iran -> Color(0xFF1C2216).copy(alpha = 0.50f)
        AppearanceMode.Topo -> Color.White.copy(alpha = 0.70f)
        AppearanceMode.Custom -> Color(0xFF12160E).copy(alpha = 0.70f)
    }
}

/** رنگ متن روی کارت — برای توپو تیره، برای بقیه روشن */
@Composable
fun appearanceOnCard(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Topo -> Color(0xFF1A1A1A)
        else -> Color(0xFFE8EDE0)
    }
}

@Composable
fun appearanceOnCardMuted(): Color {
    val context = LocalContext.current
    AppearanceStore.ensureLoaded(context)
    return when (AppearanceStore.modeState) {
        AppearanceMode.Topo -> Color(0xFF444444)
        else -> Color(0xFFAAB697)
    }
}
