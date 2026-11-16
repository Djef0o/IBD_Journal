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
import fr.ibdjournal.app.data.model.RefillBasis
import fr.ibdjournal.app.domain.localDateTimeAt
import fr.ibdjournal.app.domain.now
import fr.ibdjournal.app.domain.occurrencesBetween
import fr.ibdjournal.app.domain.toEpochMillis
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.labelRes
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import fr.ibdjournal.app.calendar.CalendarSync
import fr.ibdjournal.app.data.AppPrefs

/**
 * Rappels de prise de traitement, de commande et de contrôles (prise de sang, test des selles).
 *
 * Deux modes, choisis dans les réglages :
 * - notifications de l'app : une alarme par rappel, identifiée par l'URI de son intent
 *   (`ibdjournal://reminder/medication/12`) ; seule la prochaine occurrence est posée, la
 *   suivante est calculée au déclenchement ([onAlarm]), au démarrage et aux changements
 *   d'heure ([rescheduleAll]) ;
 * - agenda du téléphone ([CalendarSync]) : un événement par rappel, sans alarme de l'app.
 *   Si l'autorisation d'agenda est retirée, l'app reprend ses notifications au démarrage
 *   suivant plutôt que de laisser les rappels se perdre.
 */
@Singleton
class TreatmentReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val notificationHelper: NotificationHelper,
    private val calendarSync: CalendarSync,
    private val prefs: AppPrefs,
) {

    enum class Kind(val path: String) { MEDICATION("medication"), REFILL("refill"), CHECKUP("checkup") }

    /** Rappel de prise et alerte de commande : deux rappels distincts par traitement. */
    suspend fun schedule(medication: Medication, after: LocalDateTime = now()) {
        if (calendarSync.isActive()) {
            cancelAlarms(Kind.MEDICATION, medication.id)
            cancelAlarms(Kind.REFILL, medication.id)
            calendarSync.sync(medication)
        } else {
            schedule(Kind.MEDICATION, medication.id, medication.schedule.nextIfEnabled(after))
            schedule(Kind.REFILL, medication.id, medication.nextRefillAlert(after))
        }
    }

    suspend fun schedule(checkup: Checkup, after: LocalDateTime = now()) {
        if (calendarSync.isActive()) {
            cancelAlarms(Kind.CHECKUP, checkup.id)
            calendarSync.sync(checkup)
        } else {
            schedule(Kind.CHECKUP, checkup.id, checkup.schedule.nextIfEnabled(after))
        }
    }

    suspend fun cancelMedication(id: Long) {
        cancelAlarms(Kind.MEDICATION, id)
        cancelAlarms(Kind.REFILL, id)
        calendarSync.removeMedication(id)
    }

    suspend fun cancelCheckup(id: Long) {
        cancelAlarms(Kind.CHECKUP, id)
        calendarSync.removeCheckup(id)
    }

    private fun cancelAlarms(kind: Kind, id: Long) {
        notificationHelper.cancelAlarm(alarmIntent(kind, id, triggerAtMillis = null))
        NotificationManagerCompat.from(context).cancel(kind.path, id.toInt())
    }

    suspend fun rescheduleAll() {
        database.medicationDao().getAll().forEach { schedule(it.toMedication()) }
        database.checkupDao().getAll().forEach { schedule(it.toCheckup()) }
    }

    suspend fun cancelAll() {
        database.medicationDao().getAll().forEach { cancelMedication(it.id) }
        database.checkupDao().getAll().forEach { cancelCheckup(it.id) }
    }

    /**
     * Bascule entre agenda et notifications. En quittant l'agenda, les événements créés
     * par l'app sont supprimés : les rappels ne doivent pas arriver en double.
     */
    suspend fun setCalendarMode(enabled: Boolean) {
        prefs.setCalendarSyncEnabled(enabled)
        if (!enabled) calendarSync.removeAll()
        rescheduleAll()
    }

    /** Changement d'agenda : les événements sont recréés dans le nouvel agenda. */
    suspend fun setCalendar(calendarId: Long) {
        calendarSync.removeAll()
        prefs.setCalendarId(calendarId)
        rescheduleAll()
    }

    /** Déclenchement d'une alarme : notifie puis pose l'occurrence suivante. */
    suspend fun onAlarm(intent: Intent) {
        // Alarme posée avant le passage à l'agenda : l'agenda prévient déjà.
        if (calendarSync.isActive()) return
        val segments = intent.data?.pathSegments ?: return
        val kind = Kind.entries.firstOrNull { it.path == segments.getOrNull(0) } ?: return
        val id = segments.getOrNull(1)?.toLongOrNull() ?: return
        // Repère : l'occurrence prévue, pour ne jamais la reprogrammer si l'alarme sonne en avance.
        val firedAt = localDateTimeAt(
            maxOf(System.currentTimeMillis(), intent.getLongExtra(EXTRA_TRIGGER_AT, 0L))
        )

        when (kind) {
            Kind.MEDICATION -> {
                val medication = database.medicationDao().get(id)?.toMedication() ?: return
                if (!medication.schedule.enabled) return
                schedule(kind, id, medication.schedule.nextOccurrence(firedAt))
                // Clé de la prise : l'occurrence prévue exacte (celle de la carte « Traitements du
                // jour »), pas l'instant réel du déclenchement, décalé de quelques millisecondes.
                val planned = medication.schedule.occurrencesBetween(firedAt.date, firedAt.date)
                    .lastOrNull { it <= firedAt } ?: firedAt
                showMedication(medication, planned.toEpochMillis())
            }
            Kind.REFILL -> {
                val medication = database.medicationDao().get(id)?.toMedication() ?: return
                if (!medication.refillAlert) return
                schedule(kind, id, medication.nextRefillAlert(firedAt))
                showRefill(medication, firedAt)
            }
            Kind.CHECKUP -> {
                val checkup = database.checkupDao().get(id)?.toCheckup() ?: return
                if (!checkup.schedule.enabled) return
                schedule(kind, id, checkup.schedule.nextOccurrence(firedAt))
                showCheckup(checkup)
            }
        }
    }

    private fun ReminderSchedule.nextIfEnabled(after: LocalDateTime) = takeIf { it.enabled }?.nextOccurrence(after)

    private fun schedule(kind: Kind, id: Long, next: LocalDateTime?) {
        if (next == null) {
            notificationHelper.cancelAlarm(alarmIntent(kind, id, triggerAtMillis = null))
            return
        }
        val triggerAt = next.toEpochMillis()
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

    private fun showMedication(medication: Medication, scheduledAt: Long) {
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
            actions = listOf(
                doseAction(medication.id, scheduledAt, ACTION_DOSE_TAKEN, R.string.dose_taken),
                doseAction(medication.id, scheduledAt, ACTION_DOSE_MISSED, R.string.dose_missed),
            ),
        )
    }

    /** URI propre à la prise et au statut : sans elle, les PendingIntent se remplaceraient. */
    private fun doseAction(medicationId: Long, scheduledAt: Long, action: String, labelRes: Int): NotificationCompat.Action {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("ibdjournal://dose/$medicationId/$scheduledAt/$action")
            setPackage(context.packageName)
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, context.getString(labelRes), pendingIntent).build()
    }

    /** Retire la notification de prise une fois la réponse donnée. */
    fun cancelMedicationNotification(medicationId: Long) {
        NotificationManagerCompat.from(context).cancel(Kind.MEDICATION.path, medicationId.toInt())
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

    /** « Pensez à commander » : avant la prochaine prise ou avant la fin du stock. */
    private fun showRefill(medication: Medication, after: LocalDateTime) {
        val text = when (medication.refillBasis) {
            RefillBasis.BEFORE_DOSE -> medication.schedule.nextOccurrence(after)?.let { dose ->
                context.getString(
                    R.string.refill_reminder_text_dose,
                    medication.name,
                    DateFormats.weekdayDayMonth(dose.date),
                )
            }
            RefillBasis.STOCK_END -> medication.stockEndDate?.let { end ->
                context.getString(R.string.refill_reminder_text_stock, medication.name, DateFormats.weekdayDayMonth(end))
            }
        } ?: medication.name
        notify(
            kind = Kind.REFILL,
            id = medication.id,
            channelId = CHANNEL_MEDICATION,
            channelName = context.getString(R.string.medication_reminder_channel),
            title = context.getString(R.string.refill_reminder_title),
            text = text,
        )
    }

    private fun notify(
        kind: Kind,
        id: Long,
        channelId: String,
        channelName: String,
        title: String,
        text: String,
        actions: List<NotificationCompat.Action> = emptyList(),
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
            .apply { actions.forEach { addAction(it) } }
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
        const val ACTION_DOSE_TAKEN = "fr.ibdjournal.app.ACTION_DOSE_TAKEN"
        const val ACTION_DOSE_MISSED = "fr.ibdjournal.app.ACTION_DOSE_MISSED"
        const val EXTRA_MEDICATION_ID = "extra_medication_id"
        const val EXTRA_SCHEDULED_AT = "extra_scheduled_at"
        private const val CHANNEL_MEDICATION = "medication_reminder"
        private const val CHANNEL_CHECKUP = "checkup_reminder"
    }
}
