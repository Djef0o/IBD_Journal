package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.SteroidCourse
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Number of days of the whole course. */
val SteroidCourse.totalDays: Int get() = steps.sumOf { it.days.coerceAtLeast(0) }

/** Last day of the course; [start] for an empty course. */
val SteroidCourse.end: LocalDate
    get() = start.plus((totalDays - 1).coerceAtLeast(0), DateTimeUnit.DAY)

/** Total dose of the whole course, in mg. */
val SteroidCourse.totalMg: Double get() = steps.sumOf { it.doseMg * it.days.coerceAtLeast(0) }

/** Dose planned on [date], or null before the start and after the end. */
fun SteroidCourse.doseOn(date: LocalDate): Double? {
    var day = start.daysUntil(date)
    if (day < 0) return null
    for (step in steps) {
        val days = step.days.coerceAtLeast(0)
        if (day < days) return step.doseMg
        day -= days
    }
    return null
}

/** Dose taken from the start through [date] included, in mg. */
fun SteroidCourse.cumulativeMgThrough(date: LocalDate): Double {
    var remaining = start.daysUntil(date) + 1
    if (remaining <= 0) return 0.0
    var total = 0.0
    for (step in steps) {
        val days = minOf(remaining, step.days.coerceAtLeast(0))
        total += step.doseMg * days
        remaining -= days
        if (remaining <= 0) break
    }
    return total
}

/** Taken in the course, not yet finished, on [date]. */
fun SteroidCourse.isActiveOn(date: LocalDate): Boolean = doseOn(date) != null
