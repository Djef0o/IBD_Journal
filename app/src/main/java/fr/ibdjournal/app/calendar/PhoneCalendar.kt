package fr.ibdjournal.app.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.domain.CalendarEventSpec
import fr.ibdjournal.app.domain.toEpochMillis
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Phone calendar the app can write to. */
data class WritableCalendar(val id: Long, val name: String, val account: String, val primary: Boolean)

/** Event to write: the schedule (shared module) plus the localized texts. */
data class CalendarEvent(val spec: CalendarEventSpec, val title: String, val description: String)

/**
 * Access to Android's calendar provider (CalendarContract). All operations
 * assume calendar permission: without it, they do nothing and return null/false.
 */
@Singleton
class PhoneCalendar @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver get() = context.contentResolver

    fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun writableCalendars(): List<WritableCalendar> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.IS_PRIMARY,
        )
        val selection = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${Calendars.VISIBLE} = 1"
        val args = arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        return resolver.query(Calendars.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        WritableCalendar(
                            id = cursor.getLong(0),
                            name = cursor.getString(1).orEmpty(),
                            account = cursor.getString(2).orEmpty(),
                            primary = cursor.getInt(3) == 1,
                        )
                    )
                }
            }
        }.orEmpty()
    }

    /**
     * Updates the event [eventId] if it still exists, otherwise creates a new one in
     * [calendarId] with an alert at the event time. Returns the event id.
     */
    fun upsert(calendarId: Long, eventId: Long?, event: CalendarEvent): Long? {
        if (!hasPermission()) return null
        val values = event.toValues(calendarId)
        if (eventId != null) {
            val updated = resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null, null)
            if (updated > 0) return eventId
        }
        val uri = resolver.insert(Events.CONTENT_URI, values) ?: return null
        val newId = ContentUris.parseId(uri)
        resolver.insert(
            Reminders.CONTENT_URI,
            ContentValues().apply {
                put(Reminders.EVENT_ID, newId)
                put(Reminders.MINUTES, 0)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            },
        )
        return newId
    }

    fun delete(eventId: Long) {
        if (!hasPermission()) return
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    private fun CalendarEvent.toValues(calendarId: Long) = ContentValues().apply {
        val start = spec.start.toEpochMillis()
        put(Events.CALENDAR_ID, calendarId)
        put(Events.TITLE, title)
        put(Events.DESCRIPTION, description)
        put(Events.DTSTART, start)
        put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        put(Events.HAS_ALARM, 1)
        val rule = rrule()
        // The provider requires DURATION for a series and DTEND for a single event.
        if (rule == null) {
            put(Events.DTEND, start + EVENT_DURATION_MILLIS)
            putNull(Events.DURATION)
            putNull(Events.RRULE)
        } else {
            put(Events.DURATION, EVENT_DURATION)
            putNull(Events.DTEND)
            put(Events.RRULE, rule)
        }
    }

    /**
     * RFC 5545 recurrence rule. Known gap: for a monthly reminder on the 29th, 30th or 31st,
     * the calendar skips months that are too short, whereas the app's notifications fall on the last day.
     */
    private fun CalendarEvent.rrule(): String? {
        val freq = when (spec.repeatUnit) {
            RepeatUnit.ONCE -> return null
            RepeatUnit.DAY -> "DAILY"
            RepeatUnit.WEEK -> "WEEKLY"
            RepeatUnit.MONTH -> "MONTHLY"
        }
        return "FREQ=$freq;INTERVAL=${spec.repeatEvery}"
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        private const val EVENT_DURATION = "PT15M"
        private const val EVENT_DURATION_MILLIS = 15 * 60 * 1000L
    }
}
