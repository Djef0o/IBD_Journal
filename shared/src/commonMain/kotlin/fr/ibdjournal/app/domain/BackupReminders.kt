package fr.ibdjournal.app.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/** Days after the last backup (or after the reminder was set up) before the first reminder. */
const val BACKUP_INTERVAL_DAYS = 30

/** Days between two reminders while no backup has been made. */
const val BACKUP_REPEAT_DAYS = 7

/** Hour of the reminders. */
const val BACKUP_REMINDER_HOUR = 10

/** True if the last backup is missing or [BACKUP_INTERVAL_DAYS] days old or more. */
fun isBackupOverdue(lastBackup: LocalDate?, today: LocalDate): Boolean =
    lastBackup == null || lastBackup.plus(BACKUP_INTERVAL_DAYS, DateTimeUnit.DAY) <= today

/**
 * Next backup reminder strictly after [after]: [BACKUP_INTERVAL_DAYS] days after [lastBackup]
 * (or after [since], the day reminders were set up, when no backup was ever made), at
 * [BACKUP_REMINDER_HOUR]; then every [BACKUP_REPEAT_DAYS] days until a backup resets it.
 */
fun nextBackupReminder(lastBackup: LocalDate?, since: LocalDate, after: LocalDateTime): LocalDateTime {
    var due = (lastBackup ?: since).plus(BACKUP_INTERVAL_DAYS, DateTimeUnit.DAY).atTime(BACKUP_REMINDER_HOUR, 0)
    while (due <= after) due = due.date.plus(BACKUP_REPEAT_DAYS, DateTimeUnit.DAY).atTime(BACKUP_REMINDER_HOUR, 0)
    return due
}
