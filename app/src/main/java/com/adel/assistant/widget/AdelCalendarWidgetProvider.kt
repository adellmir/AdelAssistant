package com.adel.assistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.adel.assistant.MainActivity
import com.adel.assistant.R
import com.adel.assistant.data.CalendarStore
import com.adel.assistant.data.ProjectStore
import com.adel.assistant.data.toIntOrNullFa
import com.adel.assistant.navigation.Routes

/** ویجت ۲×۲ تقویم شمسی با رنگ روزهای کاری (صبح/بعدازظهر) */
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

        private val DAY_IDS = IntArray(42) { i ->
            contextDayId(i)
        }

        private fun contextDayId(i: Int): Int = when (i) {
            0 -> R.id.cal_d_0; 1 -> R.id.cal_d_1; 2 -> R.id.cal_d_2; 3 -> R.id.cal_d_3
            4 -> R.id.cal_d_4; 5 -> R.id.cal_d_5; 6 -> R.id.cal_d_6; 7 -> R.id.cal_d_7
            8 -> R.id.cal_d_8; 9 -> R.id.cal_d_9; 10 -> R.id.cal_d_10; 11 -> R.id.cal_d_11
            12 -> R.id.cal_d_12; 13 -> R.id.cal_d_13; 14 -> R.id.cal_d_14; 15 -> R.id.cal_d_15
            16 -> R.id.cal_d_16; 17 -> R.id.cal_d_17; 18 -> R.id.cal_d_18; 19 -> R.id.cal_d_19
            20 -> R.id.cal_d_20; 21 -> R.id.cal_d_21; 22 -> R.id.cal_d_22; 23 -> R.id.cal_d_23
            24 -> R.id.cal_d_24; 25 -> R.id.cal_d_25; 26 -> R.id.cal_d_26; 27 -> R.id.cal_d_27
            28 -> R.id.cal_d_28; 29 -> R.id.cal_d_29; 30 -> R.id.cal_d_30; 31 -> R.id.cal_d_31
            32 -> R.id.cal_d_32; 33 -> R.id.cal_d_33; 34 -> R.id.cal_d_34; 35 -> R.id.cal_d_35
            36 -> R.id.cal_d_36; 37 -> R.id.cal_d_37; 38 -> R.id.cal_d_38; 39 -> R.id.cal_d_39
            40 -> R.id.cal_d_40; else -> R.id.cal_d_41
        }

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, AdelCalendarWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        data class Slot(val morning: Boolean, val afternoon: Boolean)

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.adel_calendar_widget)
            val (y, m, dToday) = try {
                CalendarStore.todayJalali()
            } catch (_: Exception) {
                Triple(1405, 1, 1)
            }
            val monthName = MONTHS.getOrElse(m - 1) { "" }
            views.setTextViewText(R.id.cal_month, "$monthName $y")

            val monthLen = try {
                CalendarStore.jalaliMonthLength(y, m)
            } catch (_: Exception) {
                30
            }
            val firstOffset = try {
                CalendarStore.jalaliWeekdayIndex(y, m, 1)
            } catch (_: Exception) {
                0
            }

            val marks = mutableMapOf<Int, Slot>()
            try {
                ProjectStore.all(context).forEach { e ->
                    val ey = e.year.toIntOrNullFa() ?: return@forEach
                    val em = e.month.toIntOrNullFa() ?: return@forEach
                    if (ey != y || em != m) return@forEach
                    val ed = e.day.toIntOrNullFa() ?: return@forEach
                    val h = e.hour.toIntOrNullFa() ?: 9
                    val prev = marks[ed]
                    val morning = h < 12
                    marks[ed] = Slot(
                        morning = (prev?.morning == true) || morning,
                        afternoon = (prev?.afternoon == true) || !morning
                    )
                }
            } catch (_: Exception) {
            }

            // رنگ‌ها: خالی، هر دو، فقط صبح (نیمه پایین پررنگ‌تر با یک رنگ تیره‌تر)، فقط عصر
            val emptyBg = Color.parseColor("#332A3A20")
            val bothBg = Color.parseColor("#996C9A4E")      // یکدست
            val morningBg = Color.parseColor("#AA8FBC6A")  // صبح
            val afternoonBg = Color.parseColor("#AA558B2F") // بعدازظهر
            val todayBorder = Color.parseColor("#FFFFFFFF")

            for (i in 0 until 42) {
                val id = contextDayId(i)
                val dayNum = i - firstOffset + 1
                if (dayNum in 1..monthLen) {
                    views.setTextViewText(id, dayNum.toString())
                    val mark = marks[dayNum]
                    val bgRes = when {
                        dayNum == dToday && mark == null -> R.drawable.cal_day_today
                        mark == null -> R.drawable.cal_day_empty
                        mark.morning && mark.afternoon -> R.drawable.cal_day_both
                        mark.morning -> R.drawable.cal_day_morning
                        mark.afternoon -> R.drawable.cal_day_afternoon
                        else -> R.drawable.cal_day_empty
                    }
                    views.setInt(id, "setBackgroundResource", bgRes)
                    views.setTextColor(
                        id,
                        if (dayNum == dToday) Color.parseColor("#FFFFF59D")
                        else Color.parseColor("#FFE8EDE0")
                    )
                } else {
                    views.setTextViewText(id, "")
                    views.setInt(id, "setBackgroundColor", Color.TRANSPARENT)
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
            views.setOnClickPendingIntent(R.id.cal_month, pi)
            return views
        }
    }
}
