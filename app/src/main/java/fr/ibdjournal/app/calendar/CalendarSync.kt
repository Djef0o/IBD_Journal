package fr.ibdjournal.app.calendar

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.domain.ExamStep
import fr.ibdjournal.app.domain.calendarKind
import fr.ibdjournal.app.domain.events
import fr.ibdjournal.app.notification.examStepTitle
import fr.ibdjournal.app.domain.CalendarEventKind
import fr.ibdjournal.app.domain.CalendarEventSpec
import fr.ibdjournal.app.domain.doseEvent
import fr.ibdjournal.app.domain.event
import fr.ibdjournal.app.domain.refillEvent
import fr.ibdjournal.app.ui.labelRes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Reflète traitements et contrôles dans l'agenda du téléphone : un événement par rappel
 * (prise, commande, contrôle), mis à jour ou supprimé avec l'élément. Les identifiants des
 * événements créés sont conservés pour ne jamais toucher aux autres événements de l'agenda.
 */
@Singleton
class CalendarSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPrefs,
    private val calendar: PhoneCalendar,
) {

    /** Mode agenda effectif : activé et autorisé (sinon l'app reprend ses notifications). */
    suspend fun isActive(): Boolean = prefs.calendarSync.first().enabled && calendar.hasPermission()

    suspend fun sync(medication: Medication) {
        put(CalendarEventKind.DOSE, medication.id, medication.doseEvent()?.let { doseEvent(medication, it) })
        put(CalendarEventKind.REFILL, medication.id, medication.refillEvent()?.let { refillEvent(medication, it) })
    }

    suspend fun sync(checkup: Checkup) {
        put(CalendarEventKind.CHECKUP, checkup.id, checkup.event()?.let { checkupEvent(checkup, it) })
    }

    suspend fun removeMedication(id: Long) {
        put(CalendarEventKind.DOSE, id, null)
        put(CalendarEventKind.REFILL, id, null)
    }

    suspend fun removeCheckup(id: Long) = put(CalendarEventKind.CHECKUP, id, null)

    suspend fun sync(exam: Exam) {
        exam.events().forEach { (step, spec) ->
            put(step.calendarKind, exam.id, spec?.let { examEvent(exam, step, it) })
        }
    }

    suspend fun removeExam(id: Long) {
        ExamStep.entries.forEach { put(it.calendarKind, id, null) }
    }

    /** Supprime tous les événements créés par l'app (désactivation, changement d'agenda). */
    suspend fun removeAll() {
        prefs.calendarEventIds.first().forEach { (key, eventId) ->
            calendar.delete(eventId)
            prefs.setCalendarEventId(key, null)
        }
    }

    private suspend fun put(kind: CalendarEventKind, itemId: Long, event: CalendarEvent?) {
        val key = CalendarEventSpec.keyOf(kind, itemId)
        val existing = prefs.calendarEventIds.first()[key]
        if (event == null) {
            if (existing != null) {
                calendar.delete(existing)
                prefs.setCalendarEventId(key, null)
            }
            return
        }
        val calendarId = targetCalendarId() ?: return
        val eventId = calendar.upsert(calendarId, existing, event) ?: return
        if (eventId != existing) prefs.setCalendarEventId(key, eventId)
    }

    /** Agenda choisi s'il existe encore, sinon l'agenda principal, sinon le premier modifiable. */
    private suspend fun targetCalendarId(): Long? {
        val calendars = calendar.writableCalendars()
        val chosen = prefs.calendarSync.first().calendarId
        return calendars.firstOrNull { it.id == chosen }?.id
            ?: (calendars.firstOrNull { it.primary } ?: calendars.firstOrNull())?.id
    }

    private fun doseEvent(medication: Medication, spec: CalendarEventSpec) = CalendarEvent(
        spec = spec,
        title = if (medication.dosage.isBlank()) {
            context.getString(R.string.medication_reminder_text, medication.name)
        } else {
            context.getString(R.string.medication_reminder_text_dosage, medication.name, medication.dosage)
        },
        description = context.getString(R.string.calendar_event_description),
    )

    private fun refillEvent(medication: Medication, spec: CalendarEventSpec) = CalendarEvent(
        spec = spec,
        title = context.getString(R.string.calendar_refill_title, medication.name),
        description = context.getString(R.string.calendar_event_description),
    )

    private fun checkupEvent(checkup: Checkup, spec: CalendarEventSpec) = CalendarEvent(
        spec = spec,
        title = listOf(context.getString(checkup.type.labelRes), checkup.note)
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        description = context.getString(R.string.calendar_event_description),
    )

    /** « Coloscopie · Clinique du Parc » ; étapes : « 1re prise de la préparation (coloscopie) ». */
    private fun examEvent(exam: Exam, step: ExamStep, spec: CalendarEventSpec) = CalendarEvent(
        spec = spec,
        title = examStepTitle(context, exam, step),
        description = context.getString(R.string.calendar_event_description),
    )
}
