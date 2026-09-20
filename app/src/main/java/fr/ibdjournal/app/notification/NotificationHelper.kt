package fr.ibdjournal.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        private const val NOTIFICATION_ID = 1001

        /** Affiche la notification de rappel (no-op si les notifications sont désactivées). */
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
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.daily_reminder))
                .setContentText(context.getString(R.string.daily_reminder_desc))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
