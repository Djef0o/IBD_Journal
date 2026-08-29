package fr.ibdjournal.app.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Formatage des dates en français. */
object DateFormats {

    val WEEKDAYS: List<String> = DayOfWeek.entries
        .map { it.getDisplayName(TextStyle.NARROW, Locale.FRANCE) }

    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)
    private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.FRANCE)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)

    fun monthYear(month: YearMonth): String = capitalize(month.atDay(1).format(monthYearFormatter))

    fun dayMonth(date: LocalDate): String = capitalize(date.format(dayMonthFormatter))

    fun fullDate(date: LocalDate): String = capitalize(date.format(fullDateFormatter))

    fun weekday(date: LocalDate): String = capitalize(date.format(weekdayFormatter))

    fun time(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter)

    /** "24 août – 30 août", avec l'année si la semaine chevauche deux années. */
    fun weekRange(start: LocalDate): String {
        val end = start.plusDays(6)
        val endLabel = if (end.year != start.year) {
            capitalize(end.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)))
        } else {
            dayMonth(end)
        }
        return "${dayMonth(start)} – $endLabel"
    }

    fun capitalize(text: String): String =
        text.replaceFirstChar { it.titlecase(Locale.FRANCE) }
}
