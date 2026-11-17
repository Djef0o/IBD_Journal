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
import fr.ibdjournal.app.data.local.toExam
import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.domain.ExamStep
import fr.ibdjournal.app.domain.ExamStepTime
import fr.ibdjournal.app.domain.reminderAt
import fr.ibdjournal.app.domain.steps
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
 * Reminders for taking treatments, reordering, check-ups (blood test, stool test)
 * and exam steps (colonoscopy, rectosigmoidoscopy).
 *
 * Two modes, chosen in the settings:
 * - app notifications: one alarm per reminder, identified by the URI of its intent
 *   (`ibdjournal://reminder/medication/12`); only the next occurrence is set, the
 *   following one is computed when it fires ([onAlarm]), at startup and on time
 *   changes ([rescheduleAll]);
 * - phone calendar ([CalendarSync]): one event per reminder, no app alarm.
 *   If the calendar permission is removed, the app resumes its notifications at the
 *   next startup rather than letting reminders get lost.
 */
@Singleton
class TreatmentReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val notificationHelper: NotificationHelper,
    private val calendarSync: CalendarSync,
    private val prefs: AppPrefs,
) {

    enum class Kind(val path: String) {
        MEDICATION("medication"), REFILL("refill"), CHECKUP("checkup"),
        EXAM_ANESTHESIA("exam-anesthesia"), EXAM_DIET("exam-diet"),
        EXAM_PREP_FIRST("exam-prep-first"), EXAM_PREP_SECOND("exam-prep-second"), EXAM("exam"),
    }

    /** Reminder for each exam step. */
    private val EXAM_KINDS = mapOf(
        ExamStep.ANESTHESIA to Kind.EXAM_ANESTHESIA,
        ExamStep.DIET to Kind.EXAM_DIET,
        ExamStep.PREP_FIRST to Kind.EXAM_PREP_FIRST,
        ExamStep.PREP_SECOND to Kind.EXAM_PREP_SECOND,
        ExamStep.EXAM to Kind.EXAM,
    )

    /** Intake reminder and reorder alert: two distinct reminders per treatment. */
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

    /**
     * One alarm per exam step (anesthesia appointment, start of the diet, bowel prep
     * doses, exam); only steps still to come are set.
     */
    suspend fun schedule(exam: Exam, after: LocalDateTime = now()) {
        if (calendarSync.isActive()) {
            EXAM_KINDS.values.forEach { cancelAlarms(it, exam.id) }
            calendarSync.sync(exam)
            return
        }
        val reminders = if (exam.remindersEnabled) exam.steps().associate { it.step to it.reminderAt() } else emptyMap()
        EXAM_KINDS.forEach { (step, kind) ->
            schedule(kind, exam.id, reminders[step]?.takeIf { it > after })
        }
    }

    suspend fun cancelExam(id: Long) {
        EXAM_KINDS.values.forEach { cancelAlarms(it, id) }
        calendarSync.removeExam(id)
    }

    private fun cancelAlarms(kind: Kind, id: Long) {
        notificationHelper.cancelAlarm(alarmIntent(kind, id, triggerAtMillis = null))
        NotificationManagerCompat.from(context).cancel(kind.path, id.toInt())
    }

    suspend fun rescheduleAll() {
        database.medicationDao().getAll().forEach { schedule(it.toMedication()) }
        database.checkupDao().getAll().forEach { schedule(it.toCheckup()) }
        database.examDao().getAll().forEach { schedule(it.toExam()) }
    }

    suspend fun cancelAll() {
        database.medicationDao().getAll().forEach { cancelMedication(it.id) }
        database.checkupDao().getAll().forEach { cancelCheckup(it.id) }
        database.examDao().getAll().forEach { cancelExam(it.id) }
    }

    /**
     * Switches between calendar and notifications. When leaving the calendar, the events
     * created by the app are deleted: reminders must not arrive twice.
     */
    suspend fun setCalendarMode(enabled: Boolean) {
        prefs.setCalendarSyncEnabled(enabled)
        if (!enabled) calendarSync.removeAll()
        rescheduleAll()
    }

    /** Calendar change: events are recreated in the new calendar. */
    suspend fun setCalendar(calendarId: Long) {
        calendarSync.removeAll()
        prefs.setCalendarId(calendarId)
        rescheduleAll()
    }

    /** Alarm fired: notifies, then sets the next occurrence. */
    suspend fun onAlarm(intent: Intent) {
        // Alarm set before switching to the calendar: the calendar already notifies.
        if (calendarSync.isActive()) return
        val segments = intent.data?.pathSegments ?: return
        val kind = Kind.entries.firstOrNull { it.path == segments.getOrNull(0) } ?: return
        val id = segments.getOrNull(1)?.toLongOrNull() ?: return
        // Marker: the scheduled occurrence, to never reschedule it if the alarm rings early.
        val firedAt = localDateTimeAt(
            maxOf(System.currentTimeMillis(), intent.getLongExtra(EXTRA_TRIGGER_AT, 0L))
        )

        when (kind) {
            Kind.MEDICATION -> {
                val medication = database.medicationDao().get(id)?.toMedication() ?: return
                if (!medication.schedule.enabled) return
                schedule(kind, id, medication.schedule.nextOccurrence(firedAt))
                // Intake key: the exact scheduled occurrence (that of the "Today's treatments"
                // card), not the actual trigger instant, off by a few milliseconds.
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
            else -> {
                // Exam step: one-off reminder, nothing to reschedule. The exam may have changed
                // since the alarm was set: only notify for the step still planned.
                val step = EXAM_KINDS.entries.firstOrNull { it.value == kind }?.key ?: return
                val exam = database.examDao().get(id)?.toExam()?.takeIf { it.remindersEnabled } ?: return
                val stepTime = exam.steps().firstOrNull { it.step == step } ?: return
                showExamStep(exam, stepTime, kind)
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

    /** URI specific to the intake and status: without it, the PendingIntents would replace each other. */
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

    /** Removes the intake notification once the answer is given. */
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

    private fun showExamStep(exam: Exam, step: ExamStepTime, kind: Kind) {
        notify(
            kind = kind,
            id = exam.id,
            channelId = CHANNEL_EXAM,
            channelName = context.getString(R.string.exam_reminder_channel),
            title = examStepTitle(context, exam, step.step),
            text = examStepText(context, exam, step.step, step.at),
        )
    }

    /** "Remember to reorder": before the next intake or before the stock runs out. */
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
            // Notification permission removed between the check and the send.
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
        private const val CHANNEL_EXAM = "exam_reminder"
    }
}
