package fr.ibdjournal.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.MainActivity
import fr.ibdjournal.app.R
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programme le rappel quotidien précis à l'aide d'AlarmManager (setExactAndAllowWhileIdle)
 * et affiche la notification interactive avec les 8 choix de Bristol (0 à 7).
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scheduleDailyReminder(hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_ALARM_REMINDER
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAtMillis = nextOccurrenceMillis(hour, minute)

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    fun cancelDailyReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_ALARM_REMINDER
            setPackage(context.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
    }

    /** Affiche la notification de rappel. */
    fun showStoolReminder() {
        showReminder(context)
    }

    // Prochaine occurrence de HH:MM (demain si l'heure est déjà passée aujourd'hui).
    private fun nextOccurrenceMillis(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(hour, minute)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1001
        const val ACTION_ALARM_REMINDER = "fr.ibdjournal.app.ACTION_ALARM_REMINDER"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
        private const val ALARM_REQUEST_CODE = 2001

        /** Affiche la notification de rappel avec les 8 icônes d'enregistrement direct (0 à 7). */
        fun showReminder(context: Context) {
            val manager = NotificationManagerCompat.from(context)
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

            val remoteViews = RemoteViews(context.packageName, R.layout.notification_stool_reminder)

            val itemIds = intArrayOf(
                R.id.item_bristol_0, R.id.item_bristol_1, R.id.item_bristol_2, R.id.item_bristol_3,
                R.id.item_bristol_4, R.id.item_bristol_5, R.id.item_bristol_6, R.id.item_bristol_7,
            )
            val btnIds = intArrayOf(
                R.id.btn_bristol_0, R.id.btn_bristol_1, R.id.btn_bristol_2, R.id.btn_bristol_3,
                R.id.btn_bristol_4, R.id.btn_bristol_5, R.id.btn_bristol_6, R.id.btn_bristol_7,
            )

            for (i in 0..7) {
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = NotificationReceiver.ACTION_RECORD_STOOL
                    putExtra(NotificationReceiver.EXTRA_BRISTOL_TYPE, i)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    i + 100,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

                remoteViews.setOnClickPendingIntent(itemIds[i], pendingIntent)
                remoteViews.setOnClickPendingIntent(btnIds[i], pendingIntent)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.daily_reminder))
                .setContentText(context.getString(R.string.daily_reminder_desc))
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
