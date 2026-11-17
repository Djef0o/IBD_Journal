package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.DoseLog
import fr.ibdjournal.app.data.model.DoseStatus
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus

/** True if the schedule has a dose on that day (months counted from the start, like reminders). */
fun ReminderSchedule.occursOn(date: LocalDate): Boolean {
    if (date < startDate) return false
    val every = repeatEvery.coerceAtLeast(1)
    return when (repeatUnit) {
        RepeatUnit.ONCE -> date == startDate
        RepeatUnit.DAY -> startDate.daysUntil(date) % every == 0
        RepeatUnit.WEEK -> startDate.daysUntil(date) % (7 * every) == 0
        RepeatUnit.MONTH -> {
            // February 28 is only "0 months" after January 31: also test the following month.
            val months = startDate.monthsUntil(date)
            listOf(months, months + 1).any { m ->
                m % every == 0 && startDate.plus(m, DateTimeUnit.MONTH) == date
            }
        }
    }
}

/** Scheduled occurrences between [from] and [to] inclusive, in order. */
fun ReminderSchedule.occurrencesBetween(from: LocalDate, to: LocalDate): List<LocalDateTime> {
    if (to < from) return emptyList()
    return generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it <= to }
        .filter { occursOn(it) }
        .map { it.atTime(hour, minute) }
        .toList()
}

/** Dose scheduled on a given day, with its confirmation if any. */
data class DoseDue(val medication: Medication, val at: LocalDateTime, val status: DoseStatus?) {
    /** Key of the occurrence in [DoseLog.scheduledAt]. */
    val scheduledAt: Long get() = at.toEpochMillis()
}

/** Doses scheduled on [date] for all medications, by time. */
fun dosesOn(date: LocalDate, medications: List<Medication>, logs: List<DoseLog>): List<DoseDue> {
    val statuses = logs.associateBy({ it.medicationId to it.scheduledAt }, { it.status })
    return medications
        .flatMap { medication ->
            medication.schedule.occurrencesBetween(date, date).map { at ->
                DoseDue(medication, at, statuses[medication.id to at.toEpochMillis()])
            }
        }
        .sortedBy { it.at }
}

/**
 * Adherence for a medication: doses scheduled from [from] through [until] inclusive, and among
 * them those confirmed taken or missed. Doses without an answer count for neither
 * one nor the other.
 */
data class Adherence(val expected: Int, val taken: Int, val missed: Int) {
    /** Share of scheduled doses confirmed taken, or null with no scheduled dose. */
    val takenRate: Double? get() = if (expected == 0) null else taken.toDouble() / expected
}

fun adherence(medication: Medication, logs: List<DoseLog>, from: LocalDate, until: LocalDateTime): Adherence {
    val due = medication.schedule.occurrencesBetween(from, until.date)
        .filter { it <= until }
        .map { it.toEpochMillis() }
        .toSet()
    val answered = logs.filter { it.medicationId == medication.id && it.scheduledAt in due }
    return Adherence(
        expected = due.size,
        taken = answered.count { it.status == DoseStatus.TAKEN },
        missed = answered.count { it.status == DoseStatus.MISSED },
    )
}
