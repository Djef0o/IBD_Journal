package fr.ibdjournal.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.MainActivity
import fr.ibdjournal.app.R
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programme le rappel quotidien (notification WorkManager) et affiche la notification.
 * Le rappel est une tâche périodique unique : [scheduleDailyReminder] ne remplace un
 * horaire existant que si la politique [ExistingPeriodicWorkPolicy.UPDATE] est passée.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scheduleDailyReminder(
        hour: Int,
        minute: Int,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
    ) {
        val initialDelay = Duration.between(LocalDateTime.now(), nextOccurrence(hour, minute))
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    fun cancelDailyReminder() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Affiche la notification de rappel. */
    fun showStoolReminder() {
        showReminder(context)
    }

    // Prochaine occurrence de HH:MM (demain si l'heure est déjà passée aujourd'hui).
    private fun nextOccurrence(hour: Int, minute: Int): LocalDateTime {
        val now = LocalDateTime.now()
        val today = now.toLocalDate().atTime(hour, minute)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    companion object {
        const val WORK_NAME = "daily_reminder"
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1001

        /** Affiche la notification de rappel avec les 7 icônes d'enregistrement direct. */
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
                R.id.item_bristol_4, R.id.item_bristol_5, R.id.item_bristol_6, R.id.item_bristol_7
            )
            val btnIds = intArrayOf(
                R.id.btn_bristol_0, R.id.btn_bristol_1, R.id.btn_bristol_2, R.id.btn_bristol_3,
                R.id.btn_bristol_4, R.id.btn_bristol_5, R.id.btn_bristol_6, R.id.btn_bristol_7
            )

            for (type in 0..7) {
                val intent = Intent(context, NotificationReceiver::class.java).apply {
                    action = NotificationReceiver.ACTION_RECORD_STOOL
                    putExtra(NotificationReceiver.EXTRA_BRISTOL_TYPE, type)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    type,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

                remoteViews.setOnClickPendingIntent(itemIds[type], pendingIntent)
                remoteViews.setOnClickPendingIntent(btnIds[type], pendingIntent)
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
