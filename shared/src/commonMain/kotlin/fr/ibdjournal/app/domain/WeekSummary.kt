package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Stool
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/** What the home-screen widget needs about one day: counts only, no comments or details. */
@Serializable
data class DayStats(val date: LocalDate, val stools: Int, val blood: Boolean, val bristolSum: Int)

/** Counts per day: real stools only for the count and the Bristol sum; blood from any entry of the day. */
fun List<Stool>.dailyStats(): List<DayStats> = groupBy { it.date }.map { (date, day) ->
    val real = day.filter { it.isRealStool }
    DayStats(date, real.size, day.any { it.bloodLevel > 0 }, real.sumOf { it.bristolType })
}.sortedBy { it.date }

/** The 7 days ending on [today] (oldest first, empty days included), with their totals. */
data class WeekView(val days: List<DayStats>, val stools: Int, val averageBristol: Double?, val bloodDays: Int)

fun weekView(stats: List<DayStats>, today: LocalDate): WeekView {
    val byDate = stats.associateBy { it.date }
    val days = (6 downTo 0).map { back ->
        val date = today.minus(back, DateTimeUnit.DAY)
        byDate[date] ?: DayStats(date, 0, false, 0)
    }
    val stools = days.sumOf { it.stools }
    return WeekView(
        days = days,
        stools = stools,
        averageBristol = if (stools == 0) null else days.sumOf { it.bristolSum }.toDouble() / stools,
        bloodDays = days.count { it.blood },
    )
}

/** Hour of the weekly weigh-in reminder. */
const val WEIGH_IN_HOUR = 9

/** Next weigh-in reminder strictly after [after]: the chosen weekday at [WEIGH_IN_HOUR]. */
fun nextWeighIn(dayOfWeek: DayOfWeek, after: LocalDateTime): LocalDateTime {
    val offset = (dayOfWeek.ordinal - after.date.dayOfWeek.ordinal + 7) % 7
    val due = after.date.plus(offset, DateTimeUnit.DAY).atTime(WEIGH_IN_HOUR, 0)
    return if (due > after) due else due.date.plus(7, DateTimeUnit.DAY).atTime(WEIGH_IN_HOUR, 0)
}

private const val MIN_KG = 20.0
private const val MAX_KG = 400.0

/**
 * Weight typed in a notification: "71.8", "71,8", "72 kg". Null if it is not a plausible
 * body weight; rounded to 0.1 kg.
 */
fun parseWeightKg(text: String): Double? {
    val number = text.trim().removeSuffix("kg").removeSuffix("KG").removeSuffix("Kg").trim().replace(',', '.')
    val kg = number.toDoubleOrNull() ?: return null
    if (kg.isNaN() || kg < MIN_KG || kg > MAX_KG) return null
    return kotlin.math.round(kg * 10) / 10
}
