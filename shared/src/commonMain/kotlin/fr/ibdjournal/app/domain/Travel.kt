package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Medication
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Extra days of treatment to take along, in case of delay: the default of the travel list. */
const val DEFAULT_TRAVEL_MARGIN_DAYS = 3

/**
 * What to take along for one treatment: [onTrip] scheduled intakes from departure to return (both
 * days included), [margin] more for the safety days after return. [missing]: how many are not
 * covered by the counted stock, null when the stock is not counted.
 */
data class TravelLine(val medication: Medication, val onTrip: Int, val margin: Int) {
    val total: Int get() = onTrip + margin
    val missing: Int? get() = medication.stockDoses?.let { (total - it).coerceAtLeast(0) }
}

/** Days of the trip, departure and return days included; at least 1. */
fun tripDays(departure: LocalDate, returnDate: LocalDate): Int = (departure.daysUntil(returnDate) + 1).coerceAtLeast(1)

/**
 * Intakes to take for every treatment that has some over the trip and the margin, by name. Intakes
 * follow each treatment's schedule, whether or not its reminder is on.
 */
fun travelPlan(
    medications: List<Medication>,
    departure: LocalDate,
    returnDate: LocalDate,
    marginDays: Int = DEFAULT_TRAVEL_MARGIN_DAYS,
): List<TravelLine> {
    val marginStart = returnDate.plus(1, DateTimeUnit.DAY)
    val marginEnd = returnDate.plus(marginDays.coerceAtLeast(0), DateTimeUnit.DAY)
    return medications
        .map { medication ->
            TravelLine(
                medication = medication,
                onTrip = medication.schedule.occurrencesBetween(departure, returnDate).size,
                margin = medication.schedule.occurrencesBetween(marginStart, marginEnd).size,
            )
        }
        .filter { it.total > 0 }
        .sortedBy { it.medication.name.lowercase() }
}
