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
 * User identity (Settings → Profile). [sex]: identity mention on the PDF report;
 * null = not provided. The app draws no interpretation from it.
 */
@Serializable
data class Profile(
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: LocalDate? = null,
    val sex: Sex? = null,
    val diagnosis: Diagnosis? = null,
)

@Serializable
enum class Sex { FEMALE, MALE }

@Serializable
enum class RepeatUnit { ONCE, DAY, WEEK, MONTH }

/**
 * Reminder scheduling: first occurrence on [startDate] at [hour]:[minute], then
 * every [repeatEvery] units of [repeatUnit] ([RepeatUnit.ONCE]: only once).
 * [enabled] only controls the notification; the date stays useful without a reminder.
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
     * First occurrence strictly after [after], or null if the series is over.
     * Months are counted from [startDate] (not from occurrence to occurrence): a reminder
     * on the 31st falls on April 30 then returns to May 31, without drifting.
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
        // Starting point just before `after`, then advance by one step at most twice.
        var index = startDate.until(after.date, unit) / step
        while (true) {
            val candidate = startDate.plus(index * step, unit).atTime(hour, minute)
            if (candidate > after) return candidate
            index++
        }
    }
}

/** Lead time of the "remember to reorder" alert, in days. */
const val REFILL_LEAD_DAYS = 7

/** Intakes left at which the low-stock notification fires, unless the user chooses otherwise. */
const val DEFAULT_LOW_STOCK_DOSES = 7

/** Reference of the reorder alert, derived from the dose frequency. */
enum class RefillBasis {
    /** Doses spaced more than [REFILL_LEAD_DAYS] days apart (injections…): before each dose. */
    BEFORE_DOSE,

    /** Frequent doses (daily…): before the entered stock end date. */
    STOCK_END,
}

/**
 * Tracked treatment, from the standard list or freely entered. [refillAlert] enables
 * the reorder alert; [stockEndDate] is only used in [RefillBasis.STOCK_END] mode. [stockDoses]:
 * intakes left, counted down as doses are confirmed taken (null: not counted); the low-stock
 * notification fires when it reaches [lowStockDoses].
 */
@Serializable
data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: String = "",
    val schedule: ReminderSchedule,
    val refillAlert: Boolean = false,
    val stockDoses: Int? = null,
    val lowStockDoses: Int = DEFAULT_LOW_STOCK_DOSES,
    val stockEndDate: LocalDate? = null,
) {
    /** True when the counted stock has reached [lowStockDoses] or less; false when not counted. */
    val isLowStock: Boolean get() = stockDoses?.let { it <= lowStockDoses } == true

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
     * Next reorder alert strictly after [after], at the dose time, or null.
     * Before a dose: the dose must fall more than [REFILL_LEAD_DAYS] days after [after].
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

/** Scheduled medical checkup: blood test or stool test (calprotectin…). */
@Serializable
data class Checkup(
    val id: Long = 0,
    val type: CheckupType,
    val note: String = "",
    val schedule: ReminderSchedule,
)
