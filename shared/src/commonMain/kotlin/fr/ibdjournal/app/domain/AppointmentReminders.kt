package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Appointment
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Hour of the reminder the evening before. */
const val APPOINTMENT_EVENING_HOUR = 18

/** Time between the last reminder and the appointment, in minutes. */
const val APPOINTMENT_LEAD_MINUTES = 120

/**
 * Next reminder strictly after [after] and before the appointment: the evening before at
 * [APPOINTMENT_EVENING_HOUR], then [APPOINTMENT_LEAD_MINUTES] minutes before. Null when reminders
 * are off or both have passed. A reminder that would fall after the appointment is skipped.
 */
@OptIn(ExperimentalTime::class)
fun Appointment.nextReminder(after: LocalDateTime): LocalDateTime? {
    if (!remindersEnabled) return null
    val evening = at.date.minus(1, DateTimeUnit.DAY).atTime(APPOINTMENT_EVENING_HOUR, 0)
    // Wall-clock subtraction: UTC has no daylight saving jumps to distort it.
    val shortlyBefore = at.toInstant(TimeZone.UTC).minus(APPOINTMENT_LEAD_MINUTES.minutes).toLocalDateTime(TimeZone.UTC)
    return listOf(evening, shortlyBefore).filter { it < at }.sorted().firstOrNull { it > after }
}
