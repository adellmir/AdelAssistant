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
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.data.TunnelReportStore
import com.adel.assistant.data.UtmGeo
import com.adel.assistant.navigation.Routes

/**
 * ویجت ۱×۳: لینک مسیریاب شفت‌ها و پروژه‌ها
 */
class AdelNavWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, AdelNavWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.adel_nav_widget)

            // شفت‌ها ۱..۴
            val shaftIds = listOf(
                R.id.nav_shaft_1 to "1",
                R.id.nav_shaft_2 to "2",
                R.id.nav_shaft_3 to "3",
                R.id.nav_shaft_4 to "4"
            )
            shaftIds.forEach { (viewId, name) ->
                views.setTextViewText(viewId, "شفت $name")
                val ll = shaftLatLon(context, name)
                if (ll != null) {
                    views.setOnClickPendingIntent(viewId, openGeo(context, ll.first, ll.second, 600 + name.toInt()))
                } else {
                    views.setOnClickPendingIntent(viewId, openRoute(context, "survey/tunnel/map", 600 + name.toInt()))
                }
            }

            // پروژه‌ها از جدید به قدیم (ثبت جدید = بالای لیست؛ اولویت با ردیف ثبت و تاریخ)
            val projects = ProjectStore.all(context)
                .filter { it.name.isNotBlank() && it.name != "پروژه" }
                .sortedWith(
                    compareByDescending<com.adel.assistant.data.ProjectEntry> {
                        it.row.toIntOrNullFa() ?: 0
                    }.thenByDescending { it.dateSortKey }
                )
                .distinctBy { it.name.trim() }
                .take(4)

            val projIds = listOf(R.id.nav_proj_1, R.id.nav_proj_2, R.id.nav_proj_3, R.id.nav_proj_4)
            projIds.forEachIndexed { i, viewId ->
                if (i < projects.size) {
                    val p = projects[i]
                    views.setViewVisibility(viewId, View.VISIBLE)
                    views.setTextViewText(viewId, p.name)
                    if (p.hasLocation) {
                        val url = p.neshanUrl
                        views.setOnClickPendingIntent(viewId, openUrl(context, url, 700 + i))
                    } else {
                        views.setOnClickPendingIntent(
                            viewId,
                            openRoute(context, Routes.SURVEY_PROJECT_REGISTER, 700 + i)
                        )
                    }
                } else {
                    views.setViewVisibility(viewId, View.GONE)
                }
            }

            views.setOnClickPendingIntent(
                R.id.nav_title_tunnel,
                openRoute(context, "survey/tunnel/map", 599)
            )
            views.setOnClickPendingIntent(
                R.id.nav_title_projects,
                openRoute(context, Routes.SURVEY_PROJECT_CLIENTS, 598)
            )
            return views
        }

        private fun shaftLatLon(context: Context, shaft: String): Pair<Double, Double>? {
            return try {
                val km = TunnelReportStore.shaftFixedKm(context, shaft) ?: return null
                val pt = TunnelReportStore.findByKm(context, km) ?: return null
                if (pt.x == 0.0 && pt.y == 0.0) return null
                UtmGeo.toLatLon(pt.x, pt.y, UtmGeo.DEFAULT_ZONE)
            } catch (_: Exception) {
                null
            }
        }

        private fun openGeo(context: Context, lat: Double, lon: Double, req: Int): PendingIntent {
            val uri = Uri.parse(String.format(java.util.Locale.US, "geo:%f,%f?q=%f,%f", lat, lon, lat, lon))
            val intent = Intent(Intent.ACTION_VIEW, uri)
            return PendingIntent.getActivity(
                context, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openUrl(context: Context, url: String, req: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            return PendingIntent.getActivity(
                context, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openRoute(context: Context, route: String, req: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("route", route)
                data = Uri.parse("adel://open/$route")
            }
            return PendingIntent.getActivity(
                context, req, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
