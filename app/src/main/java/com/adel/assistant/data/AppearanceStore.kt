package com.adel.assistant.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream

enum class AppearanceMode {
    Simple,
    Iran,
    Topo,
    Custom
}

/**
 * تنظیمات نمای سراسری (به‌جز برنامه‌های نقشه).
 * تصویر سفارشی در filesDir/appearance/custom_bg.jpg نگهداری و در پشتیبان ZIP می‌آید.
 */
object AppearanceStore {
    private const val PREFS = "adel_appearance"
    private const val KEY_MODE = "mode"
    private const val CUSTOM_NAME = "custom_bg.jpg"

    var modeState by mutableStateOf(AppearanceMode.Simple)
        private set

    /** با هر ذخیرهٔ تصویر جدید افزایش می‌یابد تا Compose بیت‌مپ را دوباره بخواند */
    var customVersion by mutableIntStateOf(0)
        private set

    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        modeState = when (p.getString(KEY_MODE, "simple")) {
            "iran" -> AppearanceMode.Iran
            "topo" -> AppearanceMode.Topo
            "custom" -> AppearanceMode.Custom
            else -> AppearanceMode.Simple
        }
        if (modeState == AppearanceMode.Custom && !customFile(context).exists()) {
            modeState = AppearanceMode.Simple
        }
        if (customFile(context).exists()) {
            customVersion = (customFile(context).lastModified() % Int.MAX_VALUE).toInt().coerceAtLeast(1)
        }
        loaded = true
    }

    fun mode(context: Context): AppearanceMode {
        ensureLoaded(context)
        return modeState
    }

    fun setMode(context: Context, mode: AppearanceMode) {
        ensureLoaded(context)
        if (mode == AppearanceMode.Custom && !customFile(context).exists()) {
            return
        }
        modeState = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(
                KEY_MODE,
                when (mode) {
                    AppearanceMode.Simple -> "simple"
                    AppearanceMode.Iran -> "iran"
                    AppearanceMode.Topo -> "topo"
                    AppearanceMode.Custom -> "custom"
                }
            )
            .apply()
        persistModeFile(context)
    }

    fun customFile(context: Context): File =
        File(File(context.filesDir, "appearance").also { it.mkdirs() }, CUSTOM_NAME)

    fun hasCustom(context: Context): Boolean = customFile(context).exists()

    fun saveCustomFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val outFile = customFile(context)
            var ok = false
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                    ok = true
                }
            }
            if (!ok) return false

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(outFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input) ?: return false
                    FileOutputStream(outFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                    }
                    if (!bmp.isRecycled) bmp.recycle()
                } ?: return false
            } else {
                val maxSide = 1600
                var sample = 1
                var w = bounds.outWidth
                var h = bounds.outHeight
                while (w / sample > maxSide || h / sample > maxSide) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeFile(outFile.absolutePath, opts)
                if (bmp != null) {
                    FileOutputStream(outFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                    }
                    if (!bmp.isRecycled) bmp.recycle()
                }
            }

            customVersion = (customVersion + 1).coerceAtLeast(1)
            setMode(context, AppearanceMode.Custom)
            true
        } catch (_: Exception) {
            false
        }
    }

    const val BACKUP_ENTRY = "appearance/custom_bg.jpg"
    const val BACKUP_MODE_ENTRY = "appearance/mode.txt"

    fun modeFile(context: Context): File =
        File(File(context.filesDir, "appearance").also { it.mkdirs() }, "mode.txt")

    fun persistModeFile(context: Context) {
        val m = when (mode(context)) {
            AppearanceMode.Simple -> "simple"
            AppearanceMode.Iran -> "iran"
            AppearanceMode.Topo -> "topo"
            AppearanceMode.Custom -> "custom"
        }
        modeFile(context).writeText(m)
    }

    fun restoreModeFile(context: Context) {
        val f = modeFile(context)
        if (!f.exists()) return
        val raw = f.readText().trim()
        val m = when (raw) {
            "iran" -> AppearanceMode.Iran
            "topo" -> AppearanceMode.Topo
            "custom" -> AppearanceMode.Custom
            else -> AppearanceMode.Simple
        }
        if (m == AppearanceMode.Custom && !customFile(context).exists()) {
            setMode(context, AppearanceMode.Simple)
        } else {
            modeState = m
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, raw.ifBlank { "simple" })
                .apply()
            if (customFile(context).exists()) {
                customVersion = (customFile(context).lastModified() % Int.MAX_VALUE).toInt().coerceAtLeast(1)
            }
        }
    }
}
