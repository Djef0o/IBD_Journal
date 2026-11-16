package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.Stool
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/**
 * Période analysée par l'écran Tendances (tuiles et symptômes). [days] null = tout
 * l'historique, sans période de comparaison. Libellés : côté plateforme.
 */
enum class AnalysisPeriod(val days: Int?) {
    LAST_30(30),
    LAST_90(90),
    ALL(null),
}

/** Bristol 6–7 : selles liquides, critère des scores cliniques (Mayo, Harvey-Bradshaw). */
private const val LIQUID_MIN_TYPE = 6

/** Seuil « fort » pour les échelles 0–5 de douleur et de fatigue. */
private const val STRONG_LEVEL = 3

/**
 * Symptômes comptés en jours touchés plutôt qu'en moyenne : trois jours de sang sur trente
 * restent visibles, là où une moyenne par selle les diluait jusqu'à ~0.
 */
enum class Symptom(val isPresent: (Stool) -> Boolean) {
    BLOOD({ it.bloodLevel > 0 }),
    MUCUS({ it.mucusLevel > 0 }),
    STRONG_PAIN({ it.painLevel >= STRONG_LEVEL }),
    STRONG_FATIGUE({ it.fatigueLevel >= STRONG_LEVEL }),
    URGENCY({ it.urgency }),
    NOCTURNAL({ it.nocturnal }),
}

/** [previousDays] vaut null sans période de comparaison ou si rien n'y a été noté. */
data class SymptomDays(val symptom: Symptom, val days: Int, val previousDays: Int?)

data class AnalysisSummary(
    val period: AnalysisPeriod = AnalysisPeriod.LAST_30,
    /** Nombre de jours couverts par la période (dénominateur des barres de symptômes). */
    val periodDays: Int = 30,
    /** Selles de la période (tuiles). */
    val stools: List<Stool> = emptyList(),
    /** Repas de la période. */
    val meals: List<Meal> = emptyList(),
    /** Toutes les selles enregistrées (graphique par jour, défilable). */
    val stoolHistory: List<Stool> = emptyList(),
    val symptomDays: List<SymptomDays> = emptyList(),
    /** Selles liquides par jour noté sur la période ; null sans aucun jour noté. */
    val liquidPerDay: Double? = null,
) {
    /** Selles réelles : une entrée « type 0 » note une journée sans selle. */
    val realStools: List<Stool> get() = stools.filter { it.bristolType > 0 }

    /** Bristol moyen des selles réelles, ou null sans selle. */
    val averageBristol: Double? get() = realStools.map { it.bristolType }.average().takeIf { !it.isNaN() }
}

/** Premier jour de [period] se terminant à [today], ou null pour « tout l'historique ». */
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
    // Période de comparaison : même durée, juste avant.
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
