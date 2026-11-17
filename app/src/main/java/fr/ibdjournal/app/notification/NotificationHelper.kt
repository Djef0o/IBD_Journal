package fr.ibdjournal.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import android.view.View
import fr.ibdjournal.app.data.DailyCheckIn
import fr.ibdjournal.app.domain.today
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.MainActivity
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.AppPrefs
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Schedules the daily reminder with AlarmManager.setExactAndAllowWhileIdle and shows the
 * interactive evening notification: stool type (Bristol 0 to 7) and fatigue (0 to 5).
 *
 * Deliberately no setAlarmClock: it shows an alarm icon in the status bar and presents
 * itself to the system as the "next alarm", which is not the role of a reminder.
 * Reliability relies on:
 * - an exact alarm allowed in Doze (the "exact alarms" permission);
 * - the battery optimization exemption, offered in the settings, so that vendor
 *   overlays (Honor, Xiaomi, Samsung…) do not freeze the app;
 * - rescheduling at boot, on update and on time change
 *   ([ReminderRescheduleReceiver]) as well as on every trigger.
 *
 * Only one "daily" alarm exists at a time: [scheduleDailyReminder] always cancels
 * the previous occurrence before setting a new one. This guarantees that changing the
 * time in the app properly replaces the old schedule.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPrefs: AppPrefs,
) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Since Android 12, the "exact alarms" permission can be denied or revoked by the
     * user. Without it, the reminder stays scheduled but may be delayed by Doze mode:
     * the time is then no longer guaranteed.
     */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleDailyReminder(hour: Int, minute: Int) {
        // Same PendingIntent (same action + same requestCode): recreating it with
        // FLAG_UPDATE_CURRENT replaces the pending occurrence instead of adding one.
        val pendingIntent = alarmPendingIntent(hour, minute)
        alarmManager.cancel(pendingIntent)

        setReminderAlarm(nextOccurrenceMillis(hour, minute), pendingIntent)
    }

    /**
     * Sets a reminder alarm: exact and allowed in Doze if possible, otherwise inexact.
     * Shared by the daily reminder and the treatment and check-up reminders.
     */
    fun setReminderAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
                return
            } catch (_: SecurityException) {
                // Permission revoked between the check and the call.
            }
        }

        // Without exact alarms: the system may shift the reminder by a few minutes.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    fun cancelAlarm(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
    }

    fun cancelDailyReminder() {
        alarmManager.cancel(alarmPendingIntent())
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Previous versions (WorkManager) left a unique periodic work named
     * "daily_reminder" in the WorkManager database. This database survives app
     * updates: without this cancellation, the old schedule keeps notifying every
     * day alongside the new alarm.
     */
    fun cancelLegacyWorkManagerReminder() {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
        }
    }

    /** Reschedules (or cancels) the reminder from the saved preferences. */
    suspend fun rescheduleFromPreferences(prefs: AppPrefs) {
        // Migration: make sure no old WorkManager reminder remains.
        cancelLegacyWorkManagerReminder()
        val settings = prefs.notificationSettings.first()
        if (settings.enabled) {
            scheduleDailyReminder(settings.hour, settings.minute)
        } else {
            cancelDailyReminder()
        }
    }

    // Next occurrence of HH:MM (tomorrow if the time has already passed today).
    private fun nextOccurrenceMillis(hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var target = now.toLocalDate().atTime(hour, minute)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.atZone(zone).toInstant().toEpochMilli()
    }

    private fun alarmPendingIntent(hour: Int? = null, minute: Int? = null): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_ALARM_REMINDER
            setPackage(context.packageName)
            if (hour != null && minute != null) {
                putExtra(EXTRA_HOUR, hour)
                putExtra(EXTRA_MINUTE, minute)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1001
        const val ACTION_ALARM_REMINDER = "fr.ibdjournal.app.ACTION_ALARM_REMINDER"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
        private const val ALARM_REQUEST_CODE = 2001
        private const val STOOL_REQUEST_BASE = 100
        private const val LEVEL_REQUEST_BASE = 200

        /** Name of the periodic WorkManager work of the old implementation. */
        private const val LEGACY_WORK_NAME = "daily_reminder"

        private val BRISTOL_ITEMS = intArrayOf(
            R.id.item_bristol_0, R.id.item_bristol_1, R.id.item_bristol_2, R.id.item_bristol_3,
            R.id.item_bristol_4, R.id.item_bristol_5, R.id.item_bristol_6, R.id.item_bristol_7,
        )
        private val BRISTOL_BUTTONS = intArrayOf(
            R.id.btn_bristol_0, R.id.btn_bristol_1, R.id.btn_bristol_2, R.id.btn_bristol_3,
            R.id.btn_bristol_4, R.id.btn_bristol_5, R.id.btn_bristol_6, R.id.btn_bristol_7,
        )
    }

    /** New evening reminder: answers start from zero for today. */
    suspend fun startDailyCheckIn() {
        val checkIn = DailyCheckIn(today().toEpochDays())
        appPrefs.saveDailyCheckIn(checkIn)
        showDailyCheckIn(checkIn)
    }

    /**
     * Shows the notification with the answers already given, or removes it when stool,
     * pain and fatigue are all filled in.
     */
    fun showDailyCheckIn(checkIn: DailyCheckIn) {
        val manager = NotificationManagerCompat.from(context)
        if (checkIn.complete) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.daily_reminder),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        if (!manager.areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val collapsed = checkInViews(checkIn, onlyFirstPending = true)
        val expanded = checkInViews(checkIn, onlyFirstPending = false)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(R.string.daily_reminder))
            .setContentText(context.getString(R.string.daily_reminder_desc))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(contentIntent)
            // Stays displayed while an answer is missing; updates do not ring again.
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * [onlyFirstPending]: collapsed view, reduced to the first unanswered question
     * (stool, then pain, then fatigue); otherwise all questions.
     */
    private fun checkInViews(checkIn: DailyCheckIn, onlyFirstPending: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_stool_reminder)
        val firstPendingScale = CheckInScale.entries.firstOrNull { it.answer(checkIn) == null }
        fun visibility(shown: Boolean) = if (shown) View.VISIBLE else View.GONE

        val showStool = !onlyFirstPending || checkIn.bristolType == null
        views.setViewVisibility(R.id.notification_title, visibility(showStool))
        views.setViewVisibility(R.id.row_bristol, visibility(showStool))
        BRISTOL_ITEMS.forEachIndexed { type, itemId ->
            val pendingIntent = answerIntent(NotificationReceiver.ACTION_RECORD_STOOL, STOOL_REQUEST_BASE + type) {
                putExtra(NotificationReceiver.EXTRA_BRISTOL_TYPE, type)
            }
            views.setOnClickPendingIntent(itemId, pendingIntent)
            views.setOnClickPendingIntent(BRISTOL_BUTTONS[type], pendingIntent)
            views.setInt(itemId, "setBackgroundResource", if (checkIn.bristolType == type) R.drawable.notification_choice_selected else 0)
        }

        CheckInScale.entries.forEach { scale ->
            val shown = !onlyFirstPending || (!showStool && scale == firstPendingScale)
            views.setViewVisibility(scale.headerId, visibility(shown))
            views.setViewVisibility(scale.rowId, visibility(shown))
            scale.itemIds.forEachIndexed { level, itemId ->
                val requestCode = LEVEL_REQUEST_BASE + scale.ordinal * 10 + level
                views.setOnClickPendingIntent(
                    itemId,
                    answerIntent(NotificationReceiver.ACTION_RECORD_LEVEL, requestCode) {
                        putExtra(NotificationReceiver.EXTRA_SCALE, scale.name)
                        putExtra(NotificationReceiver.EXTRA_LEVEL, level)
                    },
                )
                views.setInt(
                    itemId,
                    "setBackgroundResource",
                    if (scale.answer(checkIn) == level) R.drawable.notification_choice_selected else R.drawable.notification_choice,
                )
            }
        }
        return views
    }

    private fun answerIntent(action: String, requestCode: Int, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            this.action = action
            setPackage(context.packageName)
            extras()
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
