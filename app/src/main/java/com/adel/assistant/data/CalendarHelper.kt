package com.adel.assistant.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

/**
 * ثبت رویداد در تقویم دستگاه.
 * اگر حساب Google روی گوشی همگام باشد، رویداد در Google Calendar هم دیده می‌شود.
 */
object CalendarHelper {

    /**
     * @return eventId یا null در صورت خطا
     */
    fun insertProjectEvent(
        context: Context,
        title: String,
        description: String,
        yearJalali: Int,
        monthJalali: Int,
        dayJalali: Int,
        hour: Int,
        minute: Int,
        durationMinutes: Int = 60
    ): Long? {
        return try {
            val (gy, gm, gd) = jalaliToGregorian(yearJalali, monthJalali, dayJalali)
            val start = Calendar.getInstance().apply {
                set(Calendar.YEAR, gy)
                set(Calendar.MONTH, gm - 1)
                set(Calendar.DAY_OF_MONTH, gd)
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = start.clone() as Calendar
            end.add(Calendar.MINUTE, durationMinutes)

            val calId = primaryCalendarId(context) ?: return null

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, start.timeInMillis)
                put(CalendarContract.Events.DTEND, end.timeInMillis)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return null
            val eventId = ContentUris.parseId(uri)

            // یادآور ۱۵ دقیقه قبل
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, 15)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)

            eventId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun primaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            var fallback: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                if (fallback == null) fallback = id
                if (primaryIdx >= 0 && cursor.getInt(primaryIdx) == 1) return id
            }
            return fallback
        }
        return null
    }

    /** تبدیل شمسی به میلادی */
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jy2 = jy + 1595
        var days = -355668 + (365 * jy2) + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4 + jd
        days += if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30 + 186)
        val gy = 400 * (days / 146097)
        days %= 146097
        var gy2 = gy
        if (days > 36524) {
            gy2 += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy2 += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy2 += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val salA = if ((gy2 % 4 == 0 && gy2 % 100 != 0) || (gy2 % 400 == 0)) 1 else 0
        val gdm = intArrayOf(0, 31, salA + 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > gdm[gm]) {
            gd -= gdm[gm]
            gm++
        }
        return Triple(gy2, gm, gd)
    }
}
