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
 * Programme le rappel quotidien avec AlarmManager.setExactAndAllowWhileIdle et affiche la
 * notification interactive avec les 8 choix de Bristol (0 à 7).
 *
 * Volontairement pas de setAlarmClock : il affiche une icône de réveil dans la barre
 * d'état et se présente comme « prochaine alarme » au système, ce qui n'est pas le rôle
 * d'un rappel. La fiabilité repose sur :
 * - une alarme exacte autorisée en Doze (permission « alarmes exactes ») ;
 * - l'exemption d'optimisation de batterie, proposée dans les réglages, pour que les
 *   surcouches (Honor, Xiaomi, Samsung…) ne gèlent pas l'app ;
 * - la replanification au démarrage, à la mise à jour et au changement d'heure
 *   ([ReminderRescheduleReceiver]) ainsi qu'à chaque déclenchement.
 *
 * Une seule alarme « quotidienne » existe à la fois : [scheduleDailyReminder] annule
 * systématiquement l'occurrence précédente avant d'en poser une nouvelle. C'est ce qui
 * garantit qu'un changement d'heure dans l'application remplace bien l'ancien horaire.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Depuis Android 12, l'autorisation « alarmes exactes » peut être refusée ou révoquée
     * par l'utilisateur. Sans elle, le rappel reste programmé mais peut être retardé par
     * le mode Doze : l'heure n'est alors plus garantie.
     */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleDailyReminder(hour: Int, minute: Int) {
        // Même PendingIntent (même action + même requestCode) : le recréer avec
        // FLAG_UPDATE_CURRENT remplace l'occurrence en attente au lieu d'en ajouter une.
        val pendingIntent = alarmPendingIntent(hour, minute)
        alarmManager.cancel(pendingIntent)

        setReminderAlarm(nextOccurrenceMillis(hour, minute), pendingIntent)
    }

    /**
     * Pose une alarme de rappel : exacte et autorisée en Doze si possible, sinon inexacte.
     * Partagée par le rappel quotidien et les rappels de traitements et de contrôles.
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
                // Autorisation révoquée entre le test et l'appel.
            }
        }

        // Sans alarmes exactes : le système peut décaler le rappel de quelques minutes.
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
     * Les versions précédentes (WorkManager) laissaient un travail périodique unique nommé
     * « daily_reminder » dans la base de WorkManager. Cette base survit aux mises à jour de
     * l'application : sans cette annulation, l'ancien horaire continue de notifier chaque
     * jour en parallèle de la nouvelle alarme.
     */
    fun cancelLegacyWorkManagerReminder() {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
        }
    }

    /** Replanifie (ou annule) le rappel à partir des préférences enregistrées. */
    suspend fun rescheduleFromPreferences(prefs: AppPrefs) {
        // Migration : s'assure qu'aucun ancien rappel WorkManager ne subsiste.
        cancelLegacyWorkManagerReminder()
        val settings = prefs.notificationSettings.first()
        if (settings.enabled) {
            scheduleDailyReminder(settings.hour, settings.minute)
        } else {
            cancelDailyReminder()
        }
    }

    /** Affiche la notification de rappel. */
    fun showStoolReminder() {
        showReminder(context)
    }

    // Prochaine occurrence de HH:MM (demain si l'heure est déjà passée aujourd'hui).
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

        /** Nom du travail périodique WorkManager de l'ancienne implémentation. */
        private const val LEGACY_WORK_NAME = "daily_reminder"

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
                .setSmallIcon(R.drawable.ic_stat_reminder)
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
