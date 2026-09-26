package com.adel.assistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.adel.assistant.MainActivity
import com.adel.assistant.R
import com.adel.assistant.navigation.Routes

class AdelToolsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = buildViews(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    companion object {
        private fun openRoute(context: Context, route: String, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("open_route", route)
                action = "com.adel.assistant.TOOLS_$code"
                data = android.net.Uri.parse(
                    "adelassistant://open?route=" + android.net.Uri.encode(route)
                )
            }
            return PendingIntent.getActivity(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.adel_tools_widget)
            views.setOnClickPendingIntent(R.id.btn_draw_map, openRoute(context, Routes.TOOL_DXF, 501))
            views.setOnClickPendingIntent(R.id.btn_preview_map, openRoute(context, Routes.TOOL_DXF_PREVIEW, 502))
            views.setOnClickPendingIntent(R.id.btn_converter, openRoute(context, Routes.TOOL_GSI, 503))
            views.setOnClickPendingIntent(R.id.btn_dump, openRoute(context, Routes.TOOL_TOTAL_STATION, 504))
            views.setOnClickPendingIntent(R.id.btn_plumb, openRoute(context, "tool/plumb", 505))
            views.setOnClickPendingIntent(R.id.btn_monitoring, openRoute(context, "tool/monitoring", 506))
            views.setOnClickPendingIntent(R.id.btn_tunnel_map, openRoute(context, "survey/tunnel/map", 507))
            views.setOnClickPendingIntent(R.id.btn_tunnel_points, openRoute(context, Routes.SURVEY_TUNNEL_EVENTS, 508))
            views.setOnClickPendingIntent(R.id.btn_clients, openRoute(context, Routes.SURVEY_PROJECT_CLIENTS, 509))
            return views
        }

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, AdelToolsWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }
    }
}
