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
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.navigation.Routes

/** ویجت ۳×۳ تقویم کاری — پروژه‌های پیش‌رو */
class AdelCalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        private val MONTHS = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )
        private val WEEK = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, AdelCalendarWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.adel_calendar_widget)
            val (y, m, d) = try {
                CalendarStore.todayJalali()
            } catch (_: Exception) {
                Triple(1405, 1, 1)
            }
            val monthName = MONTHS.getOrElse(m - 1) { "" }
            views.setTextViewText(R.id.cal_title, "تقویم کار")
            views.setTextViewText(R.id.cal_month, "$monthName $y")

            val wd = try {
                CalendarStore.jalaliWeekdayIndex(y, m, d)
            } catch (_: Exception) {
                0
            }
            val dayName = WEEK.getOrElse(wd) { "" }
            views.setTextViewText(R.id.cal_today, "امروز: $dayName $d $monthName")

            val upcoming = try {
                ProjectStore.all(context)
                    .mapNotNull { p ->
                        val py = p.year.toIntOrNullFa() ?: return@mapNotNull null
                        val pm = p.month.toIntOrNullFa() ?: return@mapNotNull null
                        val pd = p.day.toIntOrNullFa() ?: return@mapNotNull null
                        val key = py * 10000 + pm * 100 + pd
                        val todayKey = y * 10000 + m * 100 + d
                        if (key < todayKey) return@mapNotNull null
                        key to p
                    }
                    .sortedBy { it.first }
                    .map { it.second }
                    .distinctBy { "${it.name}|${it.year}|${it.month}|${it.day}" }
                    .take(5)
            } catch (_: Exception) {
                emptyList()
            }

            val rowIds = listOf(
                R.id.cal_row_1, R.id.cal_row_2, R.id.cal_row_3, R.id.cal_row_4, R.id.cal_row_5
            )
            rowIds.forEachIndexed { i, id ->
                val p = upcoming.getOrNull(i)
                if (p != null) {
                    val h = p.hour.ifBlank { "—" }
                    val mn = p.minute.ifBlank { "00" }
                    val line = "${p.name} — ${p.day}/${p.month} $h:$mn"
                    views.setTextViewText(id, line)
                } else {
                    views.setTextViewText(id, if (i == 0) "پروژه پیش‌رو نیست" else " ")
                }
            }

            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("route", Routes.SURVEY_PROJECT_CALENDAR)
            }
            val pi = PendingIntent.getActivity(
                context, 7101, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.cal_root, pi)
            views.setOnClickPendingIntent(R.id.cal_title, pi)
            return views
        }
    }
}
