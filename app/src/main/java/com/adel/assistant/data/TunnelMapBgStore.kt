package com.adel.assistant.data

import android.content.Context
import java.io.File

/**
 * فایل نقشهٔ ثابت تونل (DXF) — در data ذخیره می‌شود تا در پشتیبان ZIP بیاید.
 */
object TunnelMapBgStore {
    private const val FILE_NAME = "tunnel_map_bg.dxf"

    private fun file(context: Context): File {
        val dir = File(context.filesDir, "data")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    fun hasMap(context: Context): Boolean = file(context).exists() && file(context).length() > 0

    fun saveBytes(context: Context, bytes: ByteArray) {
        file(context).writeBytes(bytes)
    }

    fun loadText(context: Context): String? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            f.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    fun parseModel(context: Context): DxfModel? {
        val text = loadText(context) ?: return null
        return try {
            DxfParser.parse(text)
        } catch (_: Exception) {
            null
        }
    }
}
