package com.adel.assistant.data

import android.content.Context
import org.json.JSONObject

/**
 * تنظیمات پایدار نقشه تونل: رنگ لایه‌ها، خاموش/روشن لایه‌ها، پس‌زمینه.
 */
object TunnelLayerPrefs {
    private const val PREF = "tunnel_layer_prefs"
    private const val KEY_COLORS = "layer_colors_json"
    private const val KEY_SHOW_EVENTS = "show_events"
    private const val KEY_SHOW_BG = "show_bg"
    private const val KEY_SHOW_TUNNEL = "show_tunnel"
    private const val KEY_SHOW_REPORT = "show_report"
    private const val KEY_SHOW_OVERLAY = "show_overlay"
    private const val KEY_SHOW_BENCHMARKS = "show_benchmarks"
    private const val KEY_BASE_MAP = "base_map"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun loadColors(context: Context): Map<String, Int> {
        val raw = prefs(context).getString(KEY_COLORS, null) ?: return emptyMap()
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
        prefs(context).edit().putString(KEY_COLORS, o.toString()).apply()
    }

    fun setColor(context: Context, layer: String, argb: Int) {
        val m = loadColors(context).toMutableMap()
        m[layer] = argb
        saveColors(context, m)
    }

    fun showEvents(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_EVENTS, true)

    fun setShowEvents(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_EVENTS, value).apply()
    }

    fun showBg(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_BG, true)
    fun setShowBg(context: Context, v: Boolean) { prefs(context).edit().putBoolean(KEY_SHOW_BG, v).apply() }

    fun showTunnel(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_TUNNEL, false)
    fun setShowTunnel(context: Context, v: Boolean) { prefs(context).edit().putBoolean(KEY_SHOW_TUNNEL, v).apply() }

    fun showReport(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_REPORT, true)
    fun setShowReport(context: Context, v: Boolean) { prefs(context).edit().putBoolean(KEY_SHOW_REPORT, v).apply() }

    fun showOverlay(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_OVERLAY, true)
    fun setShowOverlay(context: Context, v: Boolean) { prefs(context).edit().putBoolean(KEY_SHOW_OVERLAY, v).apply() }

    fun showBenchmarks(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_BENCHMARKS, true)
    fun setShowBenchmarks(context: Context, v: Boolean) { prefs(context).edit().putBoolean(KEY_SHOW_BENCHMARKS, v).apply() }

    fun baseMapName(context: Context): String = prefs(context).getString(KEY_BASE_MAP, "NONE") ?: "NONE"
    fun setBaseMapName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_BASE_MAP, name).apply()
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
