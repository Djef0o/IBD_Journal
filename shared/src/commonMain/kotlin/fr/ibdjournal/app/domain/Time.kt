package fr.ibdjournal.app.domain

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/* Heure de l'appareil, dans son fuseau courant. */

@OptIn(ExperimentalTime::class)
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

@OptIn(ExperimentalTime::class)
fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** Date et heure locales d'un horodatage en millisecondes. */
@OptIn(ExperimentalTime::class)
fun localDateTimeAt(epochMillis: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())

/** Horodatage en millisecondes d'une date et heure locales. */
@OptIn(ExperimentalTime::class)
fun LocalDateTime.toEpochMillis(): Long =
    toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
