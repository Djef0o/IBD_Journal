package fr.ibdjournal.app.data.model

import androidx.annotation.StringRes
import fr.ibdjournal.app.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/** Identité de l'utilisateur, affichée dans l'onglet Profil. */
@Serializable
data class Profile(
    val firstName: String = "",
    val lastName: String = "",
    @Serializable(with = LocalDateSerializer::class)
    val birthDate: LocalDate? = null,
)

@Serializable
enum class RepeatUnit(@StringRes val labelRes: Int) {
    ONCE(R.string.repeat_unit_once),
    DAY(R.string.repeat_unit_day),
    WEEK(R.string.repeat_unit_week),
    MONTH(R.string.repeat_unit_month),
}

/**
 * Planification d'un rappel : première occurrence le [startDate] à [hour]:[minute], puis
 * toutes les [repeatEvery] unités de [repeatUnit] ([RepeatUnit.ONCE] : une seule fois).
 * [enabled] ne commande que la notification ; la date reste utile sans rappel.
 */
@Serializable
data class ReminderSchedule(
    val enabled: Boolean = true,
    @Serializable(with = LocalDateSerializer::class)
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
        if (first.isAfter(after)) return first
        val unit = when (repeatUnit) {
            RepeatUnit.ONCE -> return null
            RepeatUnit.DAY -> ChronoUnit.DAYS
            RepeatUnit.WEEK -> ChronoUnit.WEEKS
            RepeatUnit.MONTH -> ChronoUnit.MONTHS
        }
        val step = repeatEvery.coerceAtLeast(1).toLong()
        // Point de départ juste avant `after`, puis on avance d'un pas au plus deux fois.
        var index = unit.between(startDate, after.toLocalDate()) / step
        while (true) {
            val candidate = startDate.plus(index * step, unit).atTime(hour, minute)
            if (candidate.isAfter(after)) return candidate
            index++
        }
    }
}

/** Traitement suivi, issu de la liste standard ou saisi librement. */
@Serializable
data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val schedule: ReminderSchedule,
)

@Serializable
enum class CheckupType(@StringRes val labelRes: Int) {
    BLOOD_TEST(R.string.checkup_blood_test),
    STOOL_TEST(R.string.checkup_stool_test),
}

/** Contrôle médical programmé : prise de sang ou test des selles (calprotectine…). */
@Serializable
data class Checkup(
    val id: Long = 0,
    val type: CheckupType,
    val note: String = "",
    val schedule: ReminderSchedule,
)
