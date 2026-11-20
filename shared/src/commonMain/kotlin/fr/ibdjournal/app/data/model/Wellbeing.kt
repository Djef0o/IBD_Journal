package fr.ibdjournal.app.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * How a day went, beyond the gut: [sleep] (1 = very poor, 5 = very good), [stress] (1 = calm,
 * 5 = very stressed) and [mood] (1 = very low, 5 = very good); null = not noted. [period]: menstrual
 * period that day (only offered when the profile says female).
 */
@Serializable
data class WellbeingDay(
    val date: LocalDate,
    val sleep: Int? = null,
    val stress: Int? = null,
    val mood: Int? = null,
    val period: Boolean = false,
) {
    /** Nothing noted: such a day has no entry. */
    val isEmpty: Boolean get() = sleep == null && stress == null && mood == null && !period
}

/** Bounds of the 1 to 5 scales. */
const val WELLBEING_MIN = 1
const val WELLBEING_MAX = 5
