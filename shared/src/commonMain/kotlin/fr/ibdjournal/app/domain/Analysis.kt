package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.Stool
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/**
 * Period analyzed by the Trends screen (tiles and symptoms). [days] null = the whole
 * history, with no comparison period. Labels: platform side.
 */
enum class AnalysisPeriod(val days: Int?) {
    LAST_30(30),
    LAST_90(90),
    ALL(null),
}

/** Bristol 6–7: liquid stools, criterion of clinical scores (Mayo, Harvey-Bradshaw). */
private const val LIQUID_MIN_TYPE = 6

/** "Strong" threshold for the 0–5 pain and fatigue scales. */
private const val STRONG_LEVEL = 3

/**
 * Symptoms counted in affected days rather than as an average: three days of blood out of thirty
 * stay visible, where a per-stool average diluted them down to ~0.
 */
enum class Symptom(val isPresent: (Stool) -> Boolean) {
    BLOOD({ it.bloodLevel > 0 }),
    MUCUS({ it.mucusLevel > 0 }),
    STRONG_PAIN({ it.painLevel >= STRONG_LEVEL }),
    STRONG_FATIGUE({ it.fatigueLevel >= STRONG_LEVEL }),
    URGENCY({ it.urgency }),
    NOCTURNAL({ it.nocturnal }),
    FALSE_URGE({ it.falseUrge }),
}

/** [previousDays] is null without a comparison period or if nothing was logged in it. */
data class SymptomDays(val symptom: Symptom, val days: Int, val previousDays: Int?)

data class AnalysisSummary(
    val period: AnalysisPeriod = AnalysisPeriod.LAST_30,
    /** Number of days covered by the period (denominator of the symptom bars). */
    val periodDays: Int = 30,
    /** Stools of the period (tiles). */
    val stools: List<Stool> = emptyList(),
    /** Meals of the period. */
    val meals: List<Meal> = emptyList(),
    /** All recorded stools (per-day chart, scrollable). */
    val stoolHistory: List<Stool> = emptyList(),
    val symptomDays: List<SymptomDays> = emptyList(),
    /** Liquid stools per logged day over the period; null with no logged day. */
    val liquidPerDay: Double? = null,
) {
    /** Real stools: excluding no-stool days (type 0) and false urges. */
    val realStools: List<Stool> get() = stools.filter { it.isRealStool }

    /** Average Bristol of real stools, or null with no stool. */
    val averageBristol: Double? get() = realStools.map { it.bristolType }.average().takeIf { !it.isNaN() }
}

/** First day of [period] ending at [today], or null for "the whole history". */
fun AnalysisPeriod.startFrom(today: LocalDate): LocalDate? =
    days?.let { today.minus(DatePeriod(days = it - 1)) }

fun analyze(
    history: List<Stool>,
    meals: List<Meal>,
    today: LocalDate,
    period: AnalysisPeriod,
): AnalysisSummary {
    val start = period.startFrom(today)
        ?: history.minOfOrNull { it.date }?.coerceAtMost(today)
        ?: today
    val periodDays = start.daysUntil(today) + 1
    val current = history.filter { it.date in start..today }
    // Comparison period: same length, right before.
    val previous = period.days?.let { days ->
        val previousStart = start.minus(DatePeriod(days = days))
        history.filter { it.date >= previousStart && it.date < start }
    }.orEmpty()

    fun daysWith(stools: List<Stool>, symptom: Symptom) =
        stools.filter(symptom.isPresent).map { it.date }.distinct().size

    val trackedDays = current.map { it.date }.distinct().size
    return AnalysisSummary(
        period = period,
        periodDays = periodDays,
        stools = current,
        meals = meals.filter { it.date in start..today },
        stoolHistory = history,
        symptomDays = Symptom.entries.map { symptom ->
            SymptomDays(
                symptom = symptom,
                days = daysWith(current, symptom),
                previousDays = if (previous.isEmpty()) null else daysWith(previous, symptom),
            )
        },
        liquidPerDay = if (trackedDays == 0) {
            null
        } else {
            current.count { it.bristolType >= LIQUID_MIN_TYPE }.toDouble() / trackedDays
        },
    )
}
