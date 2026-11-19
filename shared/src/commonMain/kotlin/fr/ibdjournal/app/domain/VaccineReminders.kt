package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Vaccination
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus

/** Days before the due date of the first vaccine reminder (time to book an appointment). */
const val VACCINE_REMINDER_LEAD_DAYS = 7

/** Hour of the reminders. */
const val VACCINE_REMINDER_HOUR = 9

/**
 * Next reminder strictly after [after]: a week before [Vaccination.nextDue], then on that day,
 * both at [VACCINE_REMINDER_HOUR]; null when reminders are off, nothing is planned or both have passed.
 */
fun Vaccination.nextReminder(after: LocalDateTime): LocalDateTime? {
    if (!reminderEnabled) return null
    val due = nextDue ?: return null
    return listOf(due.minus(VACCINE_REMINDER_LEAD_DAYS, DateTimeUnit.DAY), due)
        .map { it.atTime(VACCINE_REMINDER_HOUR, 0) }
        .firstOrNull { it > after }
}
