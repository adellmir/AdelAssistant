package com.adel.assistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.adel.assistant.MainActivity
import com.adel.assistant.R
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.TaskItem
import com.adel.assistant.data.TaskStore
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.navigation.Routes

class AdelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_COMPLETE_TASK -> {
                val store = intent.getStringExtra(EXTRA_STORE) ?: return
                val title = intent.getStringExtra(EXTRA_TITLE) ?: return
                val created = intent.getLongExtra(EXTRA_CREATED, 0L)
                val all = TaskStore.load(context, store).map {
                    if (it.title == title && it.createdAt == created) it.copy(completed = true) else it
                }
                TaskStore.save(context, store, all)
                refreshAll(context)
            }
            ACTION_CYCLE_OPACITY -> {
                cycleOpacity(context)
                refreshAll(context)
            }
            ACTION_REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_COMPLETE_TASK = "com.adel.assistant.widget.COMPLETE_TASK"
        const val ACTION_REFRESH = "com.adel.assistant.widget.REFRESH"
        const val ACTION_CYCLE_OPACITY = "com.adel.assistant.widget.CYCLE_OPACITY"
        const val EXTRA_STORE = "store"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CREATED = "created"
        private const val PREFS = "adel_widget_prefs"
        private const val KEY_ALPHA = "bg_alpha"
        // alpha levels 255, 204, 153, 102, 51 (~100%..20%)
        private val ALPHA_STEPS = intArrayOf(255, 204, 153, 102, 51)

        fun getAlpha(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_ALPHA, 255).coerceIn(30, 255)
        }

        private fun cycleOpacity(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cur = prefs.getInt(KEY_ALPHA, 255)
            val idx = ALPHA_STEPS.indexOfFirst { it == cur }.let { if (it < 0) 0 else it }
            val next = ALPHA_STEPS[(idx + 1) % ALPHA_STEPS.size]
            prefs.edit().putInt(KEY_ALPHA, next).apply()
        }

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, AdelWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val views = buildViews(context)
                ids.forEach { mgr.updateAppWidget(it, views) }
            }
        }

        private fun openRouteIntent(context: Context, route: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_route", route)
                action = "com.adel.assistant.OPEN_ROUTE_$requestCode"
            }
            return PendingIntent.getActivity(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun completeIntent(context: Context, store: String, task: TaskItem, requestCode: Int): PendingIntent {
            val intent = Intent(context, AdelWidgetProvider::class.java).apply {
                action = ACTION_COMPLETE_TASK
                putExtra(EXTRA_STORE, store)
                putExtra(EXTRA_TITLE, task.title)
                putExtra(EXTRA_CREATED, task.createdAt)
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun opacityIntent(context: Context): PendingIntent {
            val intent = Intent(context, AdelWidgetProvider::class.java).apply {
                action = ACTION_CYCLE_OPACITY
            }
            return PendingIntent.getBroadcast(
                context, 999, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openTasks(context: Context, store: String): List<TaskItem> {
            return TaskStore.load(context, store)
                .filter { !it.completed }
                .sortedByDescending { it.createdAt }
                .take(3)
        }

        private fun upcomingProjects(context: Context): List<String> {
            val (ty, tm, td) = CalendarStore.todayJalali()
            val todayKey = ty * 10000 + tm * 100 + td
            return ProjectStore.all(context)
                .filter { p ->
                    if (p.name.isBlank() || p.name == "پروژه") return@filter false
                    val y = p.year.toIntOrNullFa() ?: return@filter false
                    val m = p.month.toIntOrNullFa() ?: return@filter false
                    val d = p.day.toIntOrNullFa() ?: 1
                    y * 10000 + m * 100 + d >= todayKey
                }
                .sortedBy { p ->
                    val y = p.year.toIntOrNullFa() ?: 0
                    val m = p.month.toIntOrNullFa() ?: 0
                    val d = p.day.toIntOrNullFa() ?: 0
                    y * 10000 + m * 100 + d
                }
                .take(3)
                .map { "${it.name} — ${it.year}/${it.month}/${it.day}" }
        }

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.adel_widget)
            val alpha = getAlpha(context)
            val bg = Color.argb(alpha, 0x1A, 0x1F, 0x16)
            views.setInt(R.id.widget_root, "setBackgroundColor", bg)

            val pct = (alpha * 100 / 255)
            views.setTextViewText(R.id.btn_opacity, "شفاف $pct٪")
            views.setOnClickPendingIntent(R.id.btn_opacity, opacityIntent(context))

            views.setOnClickPendingIntent(
                R.id.btn_daily_report,
                openRouteIntent(context, Routes.SURVEY_TUNNEL_REPORT, 101)
            )
            views.setOnClickPendingIntent(
                R.id.btn_project_register,
                openRouteIntent(context, Routes.SURVEY_PROJECT_REGISTER, 102)
            )
            views.setOnClickPendingIntent(
                R.id.title_project_tasks,
                openRouteIntent(context, Routes.SURVEY_PROJECT_TASKS, 103)
            )
            views.setOnClickPendingIntent(
                R.id.title_tunnel_tasks,
                openRouteIntent(context, Routes.SURVEY_TUNNEL_TASKS, 104)
            )

            val pTasks = openTasks(context, "project_tasks")
            val tTasks = openTasks(context, "tunnel_tasks")
            val upcoming = upcomingProjects(context)

            fun bindTask(textId: Int, doneId: Int, task: TaskItem?, store: String, baseCode: Int) {
                if (task == null) {
                    views.setTextViewText(textId, if (baseCode % 10 == 1) "تسک باز ندارد" else "")
                    views.setViewVisibility(doneId, View.GONE)
                } else {
                    views.setTextViewText(textId, "• ${task.title}")
                    views.setViewVisibility(doneId, View.VISIBLE)
                    views.setOnClickPendingIntent(doneId, completeIntent(context, store, task, baseCode))
                }
            }

            bindTask(R.id.project_task_1, R.id.project_task_1_done, pTasks.getOrNull(0), "project_tasks", 201)
            bindTask(R.id.project_task_2, R.id.project_task_2_done, pTasks.getOrNull(1), "project_tasks", 202)
            bindTask(R.id.project_task_3, R.id.project_task_3_done, pTasks.getOrNull(2), "project_tasks", 203)
            bindTask(R.id.tunnel_task_1, R.id.tunnel_task_1_done, tTasks.getOrNull(0), "tunnel_tasks", 301)
            bindTask(R.id.tunnel_task_2, R.id.tunnel_task_2_done, tTasks.getOrNull(1), "tunnel_tasks", 302)
            bindTask(R.id.tunnel_task_3, R.id.tunnel_task_3_done, tTasks.getOrNull(2), "tunnel_tasks", 303)

            views.setTextViewText(R.id.upcoming_1, upcoming.getOrNull(0) ?: "برنامه‌ای نیست")
            views.setTextViewText(R.id.upcoming_2, upcoming.getOrNull(1) ?: "")
            views.setTextViewText(R.id.upcoming_3, upcoming.getOrNull(2) ?: "")

            return views
        }
    }
}
