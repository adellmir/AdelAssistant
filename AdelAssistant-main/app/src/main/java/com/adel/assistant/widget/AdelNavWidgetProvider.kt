package com.adel.assistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.adel.assistant.MainActivity
import com.adel.assistant.R
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.UtmGeo
import com.adel.assistant.navigation.Routes

/**
 * ویجت باریک (۱×۳): مسیریابی شفت‌ها و پروژه‌ها با نشان
 */
class AdelNavWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, buildViews(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_SHAFTS) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val open = prefs.getBoolean(KEY_SHAFTS_OPEN, true)
            prefs.edit().putBoolean(KEY_SHAFTS_OPEN, !open).apply()
            refreshAll(context)
        }
    }

    companion object {
        const val PREFS = "adel_nav_widget"
        const val KEY_SHAFTS_OPEN = "shafts_open"
        const val ACTION_TOGGLE_SHAFTS = "com.adel.assistant.widget.TOGGLE_SHAFTS"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, AdelNavWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        private fun openUrl(context: Context, url: String, code: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return PendingIntent.getActivity(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openRoute(context: Context, route: String, code: Int, extras: Map<String, String> = emptyMap()): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("open_route", route)
                extras.forEach { (k, v) -> putExtra(k, v) }
                action = "com.adel.assistant.NAV_$code"
                data = Uri.parse("adelassistant://open?route=" + Uri.encode(route) + "&c=$code")
            }
            return PendingIntent.getActivity(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun toggleIntent(context: Context): PendingIntent {
            val intent = Intent(context, AdelNavWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_SHAFTS
            }
            return PendingIntent.getBroadcast(
                context, 900,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** مختصات شفت از نقاط تونل (کد sh1..) → لینک نشان */
        private fun shaftNavUrl(context: Context, shaftNo: Int): String? {
            val pts = try { TunnelReportStore.allPoints(context) } catch (_: Exception) { emptyList() }
            val p = pts.firstOrNull { pt ->
                val t = pt.type.trim().lowercase()
                t == "sh$shaftNo" || t == "shaft$shaftNo" ||
                    (t.startsWith("sh") && t.drop(2).filter { it.isDigit() } == shaftNo.toString()) ||
                    t.contains("شفت") && t.contains(shaftNo.toString())
            } ?: return null
            if (p.x == 0.0 && p.y == 0.0) return null
            return try {
                val (lat, lon) = UtmGeo.toLatLon(p.x, p.y, UtmGeo.DEFAULT_ZONE)
                String.format(java.util.Locale.US, "https://nshn.ir/?lat=%.6f&lng=%.6f", lat, lon)
            } catch (_: Exception) {
                null
            }
        }

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.adel_nav_widget)
            val open = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SHAFTS_OPEN, true)

            views.setTextViewText(R.id.nav_title_tunnel, if (open) "تونل ▼" else "تونل ▶")
            views.setOnClickPendingIntent(R.id.nav_title_tunnel, toggleIntent(context))
            views.setViewVisibility(R.id.nav_shafts_box, if (open) View.VISIBLE else View.GONE)

            val shaftIds = listOf(
                R.id.nav_shaft_1 to 1,
                R.id.nav_shaft_2 to 2,
                R.id.nav_shaft_3 to 3,
                R.id.nav_shaft_4 to 4
            )
            shaftIds.forEach { (id, num) ->
                views.setTextViewText(id, "شفت $num")
                val url = shaftNavUrl(context, num)
                if (!url.isNullOrBlank()) {
                    views.setOnClickPendingIntent(id, openUrl(context, url, 910 + num))
                    views.setTextColor(id, 0xFF81C995.toInt())
                } else {
                    // بدون مختصات → نقشه تونل
                    views.setOnClickPendingIntent(
                        id,
                        openRoute(context, "survey/tunnel/map", 920 + num)
                    )
                    views.setTextColor(id, 0xFFE8EDE0.toInt())
                }
            }

            // پروژه‌ها: یکتا بر اساس نام، جدیدترین اول
            val projects = try {
                ProjectStore.all(context)
                    .filter { it.name.isNotBlank() && it.name != "پروژه" }
                    .groupBy { it.name.trim() }
                    .map { (_, rows) -> rows.maxByOrNull { it.dateSortKey }!! }
                    .sortedByDescending { it.dateSortKey }
                    .take(6)
            } catch (_: Exception) {
                emptyList()
            }

            val projIds = listOf(
                R.id.nav_proj_1, R.id.nav_proj_2, R.id.nav_proj_3,
                R.id.nav_proj_4, R.id.nav_proj_5, R.id.nav_proj_6
            )
            projIds.forEachIndexed { idx, viewId ->
                val p = projects.getOrNull(idx)
                if (p == null) {
                    views.setTextViewText(viewId, if (idx == 0) "پروژه‌ای نیست" else "")
                    views.setOnClickPendingIntent(
                        viewId,
                        openRoute(context, Routes.SURVEY_PROJECT_REGISTER, 950 + idx)
                    )
                } else {
                    views.setTextViewText(viewId, "• ${p.name}")
                    if (p.hasLocation && p.neshanUrl.isNotBlank()) {
                        views.setOnClickPendingIntent(viewId, openUrl(context, p.neshanUrl, 960 + idx))
                        views.setTextColor(viewId, 0xFF81C995.toInt())
                    } else {
                        // بدون آدرس → ثبت پروژه با نام پیش‌فرض
                        views.setOnClickPendingIntent(
                            viewId,
                            openRoute(
                                context,
                                Routes.SURVEY_PROJECT_REGISTER,
                                970 + idx,
                                mapOf("prefill_project_name" to p.name)
                            )
                        )
                        views.setTextColor(viewId, 0xFFFFB74D.toInt())
                    }
                }
            }
            return views
        }
    }
}
