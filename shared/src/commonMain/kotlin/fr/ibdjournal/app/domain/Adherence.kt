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

/** Vrai si le planning prévoit une prise ce jour-là (mois comptés depuis le début, comme les rappels). */
fun ReminderSchedule.occursOn(date: LocalDate): Boolean {
    if (date < startDate) return false
    val every = repeatEvery.coerceAtLeast(1)
    return when (repeatUnit) {
        RepeatUnit.ONCE -> date == startDate
        RepeatUnit.DAY -> startDate.daysUntil(date) % every == 0
        RepeatUnit.WEEK -> startDate.daysUntil(date) % (7 * every) == 0
        RepeatUnit.MONTH -> {
            // Le 28 février n'est qu'à « 0 mois » du 31 janvier : on teste aussi le mois suivant.
            val months = startDate.monthsUntil(date)
            listOf(months, months + 1).any { m ->
                m % every == 0 && startDate.plus(m, DateTimeUnit.MONTH) == date
            }
        }
    }
}

/** Occurrences prévues entre [from] et [to] inclus, dans l'ordre. */
fun ReminderSchedule.occurrencesBetween(from: LocalDate, to: LocalDate): List<LocalDateTime> {
    if (to < from) return emptyList()
    return generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it <= to }
        .filter { occursOn(it) }
        .map { it.atTime(hour, minute) }
        .toList()
}

/** Prise prévue un jour donné, avec sa confirmation éventuelle. */
data class DoseDue(val medication: Medication, val at: LocalDateTime, val status: DoseStatus?) {
    /** Clé de l'occurrence dans [DoseLog.scheduledAt]. */
    val scheduledAt: Long get() = at.toEpochMillis()
}

/** Prises prévues le [date] pour tous les traitements, par heure. */
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
 * Observance d'un traitement : prises prévues depuis [from] jusqu'à [until] inclus, et parmi
 * elles celles confirmées prises ou oubliées. Les prises sans réponse ne comptent ni pour
 * l'une ni pour l'autre.
 */
data class Adherence(val expected: Int, val taken: Int, val missed: Int) {
    /** Part des prises prévues confirmées prises, ou null sans prise prévue. */
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
