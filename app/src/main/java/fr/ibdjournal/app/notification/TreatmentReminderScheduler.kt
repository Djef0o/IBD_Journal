package fr.ibdjournal.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.MainActivity
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.toCheckup
import fr.ibdjournal.app.data.local.toMedication
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.ReminderSchedule
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rappels de prise de traitement et de contrôles (prise de sang, test des selles).
 *
 * Une alarme par élément, identifiée par l'URI de son intent
 * (`ibdjournal://reminder/medication/12`) : replanifier remplace l'occurrence en attente.
 * Seule la prochaine occurrence est posée ; la suivante est calculée au déclenchement
 * ([onAlarm]) ainsi qu'au démarrage et aux changements d'heure ([rescheduleAll]).
 */
@Singleton
class TreatmentReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val notificationHelper: NotificationHelper,
) {

    enum class Kind(val path: String) { MEDICATION("medication"), CHECKUP("checkup") }

    fun schedule(medication: Medication) = schedule(Kind.MEDICATION, medication.id, medication.schedule)

    fun schedule(checkup: Checkup) = schedule(Kind.CHECKUP, checkup.id, checkup.schedule)

    fun cancel(kind: Kind, id: Long) {
        notificationHelper.cancelAlarm(alarmIntent(kind, id, triggerAtMillis = null))
        NotificationManagerCompat.from(context).cancel(kind.path, id.toInt())
    }

    suspend fun rescheduleAll() {
        database.medicationDao().getAll().forEach { schedule(it.toMedication()) }
        database.checkupDao().getAll().forEach { schedule(it.toCheckup()) }
    }

    suspend fun cancelAll() {
        database.medicationDao().getAll().forEach { cancel(Kind.MEDICATION, it.id) }
        database.checkupDao().getAll().forEach { cancel(Kind.CHECKUP, it.id) }
    }

    /** Déclenchement d'une alarme : notifie puis pose l'occurrence suivante. */
    suspend fun onAlarm(intent: Intent) {
        val segments = intent.data?.pathSegments ?: return
        val kind = Kind.entries.firstOrNull { it.path == segments.getOrNull(0) } ?: return
        val id = segments.getOrNull(1)?.toLongOrNull() ?: return
        // Repère : l'occurrence prévue, pour ne jamais la reprogrammer si l'alarme sonne en avance.
        val firedAt = maxOf(
            System.currentTimeMillis(),
            intent.getLongExtra(EXTRA_TRIGGER_AT, 0L),
        )

        when (kind) {
            Kind.MEDICATION -> {
                val medication = database.medicationDao().get(id)?.toMedication() ?: return
                if (!medication.schedule.enabled) return
                schedule(kind, id, medication.schedule, firedAt)
                showMedication(medication)
            }
            Kind.CHECKUP -> {
                val checkup = database.checkupDao().get(id)?.toCheckup() ?: return
                if (!checkup.schedule.enabled) return
                schedule(kind, id, checkup.schedule, firedAt)
                showCheckup(checkup)
            }
        }
    }

    private fun schedule(
        kind: Kind,
        id: Long,
        schedule: ReminderSchedule,
        afterMillis: Long = System.currentTimeMillis(),
    ) {
        val zone = ZoneId.systemDefault()
        val after = LocalDateTime.ofInstant(Instant.ofEpochMilli(afterMillis), zone)
        val next = schedule.takeIf { it.enabled }?.nextOccurrence(after)
        if (next == null) {
            notificationHelper.cancelAlarm(alarmIntent(kind, id, triggerAtMillis = null))
            return
        }
        val triggerAt = next.atZone(zone).toInstant().toEpochMilli()
        notificationHelper.setReminderAlarm(triggerAt, alarmIntent(kind, id, triggerAt))
    }

    private fun alarmIntent(kind: Kind, id: Long, triggerAtMillis: Long?): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_TREATMENT_REMINDER
            data = Uri.parse("ibdjournal://reminder/${kind.path}/$id")
            setPackage(context.packageName)
            if (triggerAtMillis != null) putExtra(EXTRA_TRIGGER_AT, triggerAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showMedication(medication: Medication) {
        val text = if (medication.dosage.isBlank()) {
            context.getString(R.string.medication_reminder_text, medication.name)
        } else {
            context.getString(
                R.string.medication_reminder_text_dosage,
                medication.name,
                medication.dosage,
            )
        }
        notify(
            kind = Kind.MEDICATION,
            id = medication.id,
            channelId = CHANNEL_MEDICATION,
            channelName = context.getString(R.string.medication_reminder_channel),
            title = context.getString(R.string.medication_reminder_title),
            text = text,
        )
    }

    private fun showCheckup(checkup: Checkup) {
        notify(
            kind = Kind.CHECKUP,
            id = checkup.id,
            channelId = CHANNEL_CHECKUP,
            channelName = context.getString(R.string.checkup_reminder_channel),
            title = context.getString(checkup.type.labelRes),
            text = checkup.note.ifBlank { context.getString(R.string.checkup_reminder_text) },
        )
    }

    private fun notify(
        kind: Kind,
        id: Long,
        channelId: String,
        channelName: String,
        title: String,
        text: String,
    ) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
        )
        if (!manager.areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            manager.notify(kind.path, id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission de notification retirée entre le test et l'envoi.
        }
    }

    companion object {
        const val ACTION_TREATMENT_REMINDER = "fr.ibdjournal.app.ACTION_TREATMENT_REMINDER"
        private const val EXTRA_TRIGGER_AT = "extra_trigger_at"
        private const val CHANNEL_MEDICATION = "medication_reminder"
        private const val CHANNEL_CHECKUP = "checkup_reminder"
    }
}
