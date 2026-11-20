package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.data.model.WellbeingDay
import kotlinx.datetime.LocalDate

/** What a day can be flagged with, for the comparison with symptoms. */
enum class WellbeingFactor { POOR_SLEEP, HIGH_STRESS, LOW_MOOD, PERIOD }

/** Sleep of 2 or less, stress of 4 or more, mood of 2 or less. */
private const val LOW_MAX = 2
private const val HIGH_MIN = 4

/**
 * Whether [factor] holds that day; null if the day says nothing about it (value not noted), so
 * that it is left out of both sides of the comparison. For [WellbeingFactor.PERIOD], any entry
 * counts as knowing whether the period was on.
 */
private fun WellbeingDay.holds(factor: WellbeingFactor): Boolean? = when (factor) {
    WellbeingFactor.POOR_SLEEP -> sleep?.let { it <= LOW_MAX }
    WellbeingFactor.HIGH_STRESS -> stress?.let { it >= HIGH_MIN }
    WellbeingFactor.LOW_MOOD -> mood?.let { it <= LOW_MAX }
    WellbeingFactor.PERIOD -> period
}

/**
 * Days flagged with [factor] that were bad days clearly more often than the other days with a
 * note. [days]: flagged days with stools logged; [badRate]: share of them with symptoms;
 * [baseline]: the same share for the other days. An observation on logged data, not a cause.
 */
data class WellbeingLink(val factor: WellbeingFactor, val days: Int, val badRate: Double, val baseline: Double)

/** Minimum flagged days (with stools logged) before a link can appear. */
const val MIN_WELLBEING_DAYS = 5

/** Minimum gap above the baseline (share of days) to be reported. */
private const val MIN_EXCESS = 0.2

/**
 * Factors that came with bad days (liquid stool, blood, pain of 3 and above) clearly more often
 * than usual, most striking first. Only days with at least one stool logged are compared.
 */
fun wellbeingLinks(days: List<WellbeingDay>, stools: List<Stool>): List<WellbeingLink> {
    val badByDay: Map<LocalDate, Boolean> = stools.groupBy { it.date }.mapValues { (_, day) -> day.any { it.isBad() } }
    val compared = days.filter { it.date in badByDay }
    return WellbeingFactor.entries.mapNotNull { factor ->
        val (flagged, others) = compared.filter { it.holds(factor) != null }.partition { it.holds(factor) == true }
        if (flagged.size < MIN_WELLBEING_DAYS || others.isEmpty()) return@mapNotNull null
        val badRate = flagged.count { badByDay.getValue(it.date) }.toDouble() / flagged.size
        val baseline = others.count { badByDay.getValue(it.date) }.toDouble() / others.size
        WellbeingLink(factor, flagged.size, badRate, baseline).takeIf { badRate - baseline >= MIN_EXCESS }
    }.sortedByDescending { it.badRate - it.baseline }
}

/** Means over a period: null where nothing was noted. [periodDays]: days with a period. */
data class WellbeingAverages(val sleep: Double?, val stress: Double?, val mood: Double?, val periodDays: Int, val entries: Int)

fun wellbeingAverages(days: List<WellbeingDay>, from: LocalDate, to: LocalDate): WellbeingAverages {
    val inPeriod = days.filter { it.date in from..to && !it.isEmpty }
    fun mean(values: List<Int>) = if (values.isEmpty()) null else values.average()
    return WellbeingAverages(
        sleep = mean(inPeriod.mapNotNull { it.sleep }),
        stress = mean(inPeriod.mapNotNull { it.stress }),
        mood = mean(inPeriod.mapNotNull { it.mood }),
        periodDays = inPeriod.count { it.period },
        entries = inPeriod.size,
    )
}
