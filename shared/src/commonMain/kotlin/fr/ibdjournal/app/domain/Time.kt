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

/* Device time, in its current time zone. */

@OptIn(ExperimentalTime::class)
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

@OptIn(ExperimentalTime::class)
fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** Local date and time of a timestamp in milliseconds. */
@OptIn(ExperimentalTime::class)
fun localDateTimeAt(epochMillis: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())

/** Timestamp in milliseconds of a local date and time. */
@OptIn(ExperimentalTime::class)
fun LocalDateTime.toEpochMillis(): Long =
    toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
