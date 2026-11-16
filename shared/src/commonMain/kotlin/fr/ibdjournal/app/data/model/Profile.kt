package fr.ibdjournal.app.data.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.until
import kotlinx.serialization.Serializable

/**
 * Identité de l'utilisateur (Réglages → Profil). [sex] : sert aux valeurs usuelles des analyses
 * qui en dépendent (hémoglobine, ferritine) ; null = non renseigné.
 */
@Serializable
data class Profile(
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: LocalDate? = null,
    val sex: Sex? = null,
)

@Serializable
enum class Sex { FEMALE, MALE }

@Serializable
enum class RepeatUnit { ONCE, DAY, WEEK, MONTH }

/**
 * Planification d'un rappel : première occurrence le [startDate] à [hour]:[minute], puis
 * toutes les [repeatEvery] unités de [repeatUnit] ([RepeatUnit.ONCE] : une seule fois).
 * [enabled] ne commande que la notification ; la date reste utile sans rappel.
 */
@Serializable
data class ReminderSchedule(
    val enabled: Boolean = true,
    val startDate: LocalDate,
    val hour: Int = 8,
    val minute: Int = 0,
    val repeatEvery: Int = 1,
    val repeatUnit: RepeatUnit = RepeatUnit.DAY,
) {
    /**
     * Première occurrence strictement postérieure à [after], ou null si la série est terminée.
     * Les mois sont comptés depuis [startDate] (et non d'occurrence en occurrence) : un rappel
     * le 31 tombe le 30 avril puis revient au 31 mai, sans dériver.
     */
    fun nextOccurrence(after: LocalDateTime): LocalDateTime? {
        val first = startDate.atTime(hour, minute)
        if (first > after) return first
        val unit: DateTimeUnit.DateBased = when (repeatUnit) {
            RepeatUnit.ONCE -> return null
            RepeatUnit.DAY -> DateTimeUnit.DAY
            RepeatUnit.WEEK -> DateTimeUnit.WEEK
            RepeatUnit.MONTH -> DateTimeUnit.MONTH
        }
        val step = repeatEvery.coerceAtLeast(1).toLong()
        // Point de départ juste avant `after`, puis on avance d'un pas au plus deux fois.
        var index = startDate.until(after.date, unit) / step
        while (true) {
            val candidate = startDate.plus(index * step, unit).atTime(hour, minute)
            if (candidate > after) return candidate
            index++
        }
    }
}

/** Avance de l'alerte « pensez à commander », en jours. */
const val REFILL_LEAD_DAYS = 7

/** Référence de l'alerte de commande, déduite de la fréquence des prises. */
enum class RefillBasis {
    /** Prises espacées de plus de [REFILL_LEAD_DAYS] jours (injections…) : avant chaque prise. */
    BEFORE_DOSE,

    /** Prises rapprochées (quotidiennes…) : avant la date de fin du stock saisie. */
    STOCK_END,
}

/**
 * Traitement suivi, issu de la liste standard ou saisi librement. [refillAlert] active
 * l'alerte de commande ; [stockEndDate] n'est utilisée qu'en mode [RefillBasis.STOCK_END].
 */
@Serializable
data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val schedule: ReminderSchedule,
    val refillAlert: Boolean = false,
    val stockEndDate: LocalDate? = null,
) {
    val refillBasis: RefillBasis
        get() {
            val intervalDays = when (schedule.repeatUnit) {
                RepeatUnit.ONCE -> return RefillBasis.BEFORE_DOSE
                RepeatUnit.DAY -> schedule.repeatEvery
                RepeatUnit.WEEK -> schedule.repeatEvery * 7
                RepeatUnit.MONTH -> schedule.repeatEvery * 28
            }
            return if (intervalDays > REFILL_LEAD_DAYS) RefillBasis.BEFORE_DOSE else RefillBasis.STOCK_END
        }

    /**
     * Prochaine alerte de commande strictement après [after], à l'heure de prise, ou null.
     * Avant une prise : la prise doit tomber plus de [REFILL_LEAD_DAYS] jours après [after].
     */
    fun nextRefillAlert(after: LocalDateTime): LocalDateTime? {
        if (!refillAlert) return null
        val lead = DatePeriod(days = REFILL_LEAD_DAYS)
        val alert = when (refillBasis) {
            RefillBasis.BEFORE_DOSE -> {
                val dose = schedule.nextOccurrence(after.date.plus(lead).atTime(after.time)) ?: return null
                dose.date.minus(lead).atTime(dose.time)
            }
            RefillBasis.STOCK_END -> stockEndDate?.minus(lead)?.atTime(schedule.hour, schedule.minute)
        }
        return alert?.takeIf { it > after }
    }
}

@Serializable
enum class CheckupType { BLOOD_TEST, STOOL_TEST }

/** Contrôle médical programmé : prise de sang ou test des selles (calprotectine…). */
@Serializable
data class Checkup(
    val id: Long = 0,
    val type: CheckupType,
    val note: String = "",
    val schedule: ReminderSchedule,
)
