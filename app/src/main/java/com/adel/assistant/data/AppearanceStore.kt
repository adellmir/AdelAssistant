package com.adel.assistant.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

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

    /** state قابل مشاهده در Compose */
    var modeState by mutableStateOf(AppearanceMode.Simple)
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
        loaded = true
    }

    fun mode(context: Context): AppearanceMode {
        ensureLoaded(context)
        return modeState
    }

    fun setMode(context: Context, mode: AppearanceMode) {
        ensureLoaded(context)
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
    }

    fun customFile(context: Context): File =
        File(File(context.filesDir, "appearance").also { it.mkdirs() }, CUSTOM_NAME)

    fun hasCustom(context: Context): Boolean = customFile(context).exists()

    fun saveCustomFromUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                customFile(context).outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** مسیر نسبی داخل ZIP پشتیبان */
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
        val m = when (f.readText().trim()) {
            "iran" -> AppearanceMode.Iran
            "topo" -> AppearanceMode.Topo
            "custom" -> AppearanceMode.Custom
            else -> AppearanceMode.Simple
        }
        setMode(context, m)
    }
}
