package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Flare
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** True if [date] falls within the flare; an ongoing flare ([Flare.end] null) lasts through [today]. */
fun Flare.covers(date: LocalDate, today: LocalDate = today()): Boolean = date >= start && date <= (end ?: today)

/** The flare in progress on [date], if any (the most recently started one when several overlap). */
fun List<Flare>.activeOn(date: LocalDate, today: LocalDate = today()): Flare? =
    filter { it.covers(date, today) }.maxByOrNull { it.start }

/** Days from the start of the flare through [today], at least 1; null before the start. */
fun Flare.dayNumberOn(today: LocalDate): Int? = if (today < start) null else start.daysUntil(today) + 1

/** The ongoing flare ([Flare.end] null, already started) on [today], if any. */
fun List<Flare>.ongoingOn(today: LocalDate): Flare? =
    filter { it.end == null && it.start <= today }.maxByOrNull { it.start }
