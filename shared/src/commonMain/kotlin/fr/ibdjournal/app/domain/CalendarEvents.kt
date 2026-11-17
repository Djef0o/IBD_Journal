package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.REFILL_LEAD_DAYS
import fr.ibdjournal.app.data.model.RefillBasis
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus

/** Calendar event type, one stable key per app item. */
enum class CalendarEventKind {
    DOSE, REFILL, CHECKUP,

    /** Exam steps: a series of one-off events per exam. */
    EXAM_ANESTHESIA, EXAM_DIET, EXAM_PREP_FIRST, EXAM_PREP_SECOND, EXAM,
}

/**
 * Calendar event derived from a reminder, platform-independent (Android Calendar).
 * [repeatUnit] [RepeatUnit.ONCE] = one-off event. Titles: platform side.
 */
data class CalendarEventSpec(
    val kind: CalendarEventKind,
    val itemId: Long,
    val start: LocalDateTime,
    val repeatEvery: Int,
    val repeatUnit: RepeatUnit,
) {
    /** Stable identifier of the item ("dose:12"), to find the created event again. */
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

/** Dose event, only if the reminder is enabled. */
fun Medication.doseEvent(): CalendarEventSpec? =
    schedule.takeIf { it.enabled }?.event(CalendarEventKind.DOSE, id)

/**
 * "Order" event: series shifted [REFILL_LEAD_DAYS] days before the doses, or a
 * one-off event before the stock runs out. Same rule as [Medication.nextRefillAlert].
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

/** Checkup event (blood test, stool test), if the reminder is enabled. */
fun Checkup.event(): CalendarEventSpec? =
    schedule.takeIf { it.enabled }?.event(CalendarEventKind.CHECKUP, id)

/** Calendar event type for each exam step. */
val ExamStep.calendarKind: CalendarEventKind
    get() = when (this) {
        ExamStep.ANESTHESIA -> CalendarEventKind.EXAM_ANESTHESIA
        ExamStep.DIET -> CalendarEventKind.EXAM_DIET
        ExamStep.PREP_FIRST -> CalendarEventKind.EXAM_PREP_FIRST
        ExamStep.PREP_SECOND -> CalendarEventKind.EXAM_PREP_SECOND
        ExamStep.EXAM -> CalendarEventKind.EXAM
    }

/**
 * Event for each exam step, at the step's time (the calendar notifies by itself);
 * null for a missing step or when the exam's reminders are off.
 */
fun Exam.events(): Map<ExamStep, CalendarEventSpec?> {
    val byStep = if (remindersEnabled) steps().associateBy { it.step } else emptyMap()
    return ExamStep.entries.associateWith { step ->
        byStep[step]?.let {
            CalendarEventSpec(step.calendarKind, id, it.at, repeatEvery = 1, repeatUnit = RepeatUnit.ONCE)
        }
    }
}
