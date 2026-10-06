package com.adel.assistant.data

import android.content.Context
import org.json.JSONObject

/**
 * رنگ لایه‌های خطی نقشه زمینه تونل + نمایش لایه اتفاقات.
 * در حافظه پایدار می‌ماند تا دفعات بعد همان ترکیب رنگ باشد.
 */
object TunnelLayerPrefs {
    private const val PREF = "tunnel_layer_prefs"
    private const val KEY_COLORS = "layer_colors_json"
    private const val KEY_SHOW_EVENTS = "show_events"

    fun loadColors(context: Context): Map<String, Int> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_COLORS, null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            val out = mutableMapOf<String, Int>()
            o.keys().forEach { k -> out[k] = o.getInt(k) }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveColors(context: Context, colors: Map<String, Int>) {
        val o = JSONObject()
        colors.forEach { (k, v) -> o.put(k, v) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COLORS, o.toString())
            .apply()
    }

    fun setColor(context: Context, layer: String, argb: Int) {
        val m = loadColors(context).toMutableMap()
        m[layer] = argb
        saveColors(context, m)
    }

    fun showEvents(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_EVENTS, true)

    fun setShowEvents(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_EVENTS, value)
            .apply()
    }

    /** پالت پیش‌فرض برای انتخاب رنگ لایه */
    val palette: List<Pair<String, Int>> = listOf(
        "آبی روشن" to 0xFF90CAF9.toInt(),
        "آبی" to 0xFF1E88E5.toInt(),
        "سبز" to 0xFF43A047.toInt(),
        "زرد" to 0xFFFDD835.toInt(),
        "قرمز" to 0xFFE53935.toInt(),
        "نارنجی" to 0xFFFB8C00.toInt(),
        "بنفش" to 0xFF8E24AA.toInt(),
        "سفید" to 0xFFEEEEEE.toInt(),
        "خاکستری" to 0xFF9E9E9E.toInt(),
        "فیروزه" to 0xFF00ACC1.toInt()
    )
}
