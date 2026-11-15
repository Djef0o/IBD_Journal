package fr.ibdjournal.app.ui

import android.text.format.DateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Formatage des dates dans la langue de l'app (Locale par défaut, que la langue
 * choisie dans les réglages met à jour). L'ordre des éléments suit les conventions
 * de chaque langue : « samedi 14 novembre » / « Saturday, November 14 ».
 */
object DateFormats {

    private class Formatters(val locale: Locale) {
        val monthYear = skeleton("MMMMyyyy", FormatStyle.LONG)
        val dayMonth = skeleton("dMMMM", FormatStyle.LONG)
        val dayMonthYear = skeleton("dMMMMyyyy", FormatStyle.LONG)
        val fullDate = skeleton("EEEEdMMMMyyyy", FormatStyle.FULL)
        val weekdayDayMonth = skeleton("EEEEdMMMM", FormatStyle.FULL)
        // Heure courte localisée : 24 h en français, « 8:00 PM » en anglais américain…
        val time: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        val weekdaysNarrow: List<String> =
            DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale) }

        /** Motif ICU adapté à la langue ; repli sur le format localisé standard. */
        private fun skeleton(skeleton: String, fallback: FormatStyle): DateTimeFormatter =
            runCatching {
                DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            }.getOrElse { DateTimeFormatter.ofLocalizedDate(fallback).withLocale(locale) }
    }

    @Volatile
    private var cached: Formatters? = null

    private val formatters: Formatters
        get() {
            val locale = Locale.getDefault()
            return cached?.takeIf { it.locale == locale } ?: Formatters(locale).also { cached = it }
        }

    /** Initiales des jours, lundi en premier : « L M M J V S D ». */
    val weekdaysNarrow: List<String> get() = formatters.weekdaysNarrow

    fun weekdayNarrow(day: DayOfWeek): String = formatters.weekdaysNarrow[day.ordinal]

    fun monthYear(month: YearMonth): String = capitalize(month.atDay(1).format(formatters.monthYear))

    fun dayMonth(date: LocalDate): String = capitalize(date.format(formatters.dayMonth))

    fun fullDate(date: LocalDate): String = capitalize(date.format(formatters.fullDate))

    fun weekdayDayMonth(date: LocalDate): String = capitalize(date.format(formatters.weekdayDayMonth))

    /** « 12 mars 1985 » / « March 12, 1985 ». */
    fun dayMonthYear(date: LocalDate): String = date.format(formatters.dayMonthYear)

    fun time(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatters.time)

    fun time(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(formatters.time)

    fun capitalize(text: String): String =
        text.replaceFirstChar { it.titlecase(formatters.locale) }
}
