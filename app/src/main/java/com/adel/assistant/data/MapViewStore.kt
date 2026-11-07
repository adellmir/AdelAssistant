package com.adel.assistant.data

import android.content.Context

/** آخرین بزرگ‌نمایی و موقعیت نقشه برای هر کلید (مثلاً map2 / tunnel) */
object MapViewStore {
    private const val PREF = "map_view_state"

    fun save(context: Context, key: String, scale: Float, offsetX: Float, offsetY: Float, rotation: Float = 0f) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putFloat("${key}_scale", scale)
            .putFloat("${key}_ox", offsetX)
            .putFloat("${key}_oy", offsetY)
            .putFloat("${key}_rot", rotation)
            .putBoolean("${key}_has", true)
            .apply()
    }

    fun load(context: Context, key: String): Quad? {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!p.getBoolean("${key}_has", false)) return null
        return Quad(
            p.getFloat("${key}_scale", 1f),
            p.getFloat("${key}_ox", 0f),
            p.getFloat("${key}_oy", 0f),
            p.getFloat("${key}_rot", 0f)
        )
    }

    data class Quad(val scale: Float, val ox: Float, val oy: Float, val rot: Float)
}
