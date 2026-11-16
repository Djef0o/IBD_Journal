package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.REFILL_LEAD_DAYS
import fr.ibdjournal.app.data.model.RefillBasis
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus

/** Type d'événement d'agenda, une clé stable par élément de l'app. */
enum class CalendarEventKind { DOSE, REFILL, CHECKUP }

/**
 * Événement d'agenda dérivé d'un rappel, indépendant de la plateforme (Android Calendar,
 * EventKit iOS). [repeatUnit] [RepeatUnit.ONCE] = événement unique. Titres : côté plateforme.
 */
data class CalendarEventSpec(
    val kind: CalendarEventKind,
    val itemId: Long,
    val start: LocalDateTime,
    val repeatEvery: Int,
    val repeatUnit: RepeatUnit,
) {
    /** Identifiant stable de l'élément (« dose:12 »), pour retrouver l'événement créé. */
    val key: String get() = keyOf(kind, itemId)

    companion object {
        fun keyOf(kind: CalendarEventKind, itemId: Long) = "${kind.name.lowercase()}:$itemId"
    }
}

private fun ReminderSchedule.event(kind: CalendarEventKind, itemId: Long, shiftDays: Int = 0) =
    CalendarEventSpec(
        kind = kind,
        itemId = itemId,
        start = startDate.minus(DatePeriod(days = shiftDays)).atTime(hour, minute),
        repeatEvery = repeatEvery.coerceAtLeast(1),
        repeatUnit = repeatUnit,
    )

/** Événement de prise, seulement si le rappel est activé. */
fun Medication.doseEvent(): CalendarEventSpec? =
    schedule.takeIf { it.enabled }?.event(CalendarEventKind.DOSE, id)

/**
 * Événement « commander » : série décalée de [REFILL_LEAD_DAYS] jours avant les prises, ou
 * événement unique avant la fin du stock. Même règle que [Medication.nextRefillAlert].
 */
fun Medication.refillEvent(): CalendarEventSpec? {
    if (!refillAlert) return null
    return when (refillBasis) {
        RefillBasis.BEFORE_DOSE -> schedule.event(CalendarEventKind.REFILL, id, shiftDays = REFILL_LEAD_DAYS)
        RefillBasis.STOCK_END -> stockEndDate?.let { end ->
            CalendarEventSpec(
                kind = CalendarEventKind.REFILL,
                itemId = id,
                start = end.minus(DatePeriod(days = REFILL_LEAD_DAYS)).atTime(schedule.hour, schedule.minute),
                repeatEvery = 1,
                repeatUnit = RepeatUnit.ONCE,
            )
        }
    }
}

/** Événement de contrôle (prise de sang, test des selles), si le rappel est activé. */
fun Checkup.event(): CalendarEventSpec? =
    schedule.takeIf { it.enabled }?.event(CalendarEventKind.CHECKUP, id)
