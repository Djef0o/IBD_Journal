package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.data.model.ExamType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Steps of an exam, in the order they usually occur. */
enum class ExamStep { ANESTHESIA, DIET, PREP_FIRST, PREP_SECOND, EXAM }

/** Dated step of an exam: [at] time of the step (diet start: the morning). */
data class ExamStepTime(val step: ExamStep, val at: LocalDateTime)

/** The low-residue diet starts that day, reminder in the morning. */
val DIET_REMINDER_TIME = LocalTime(8, 0)

/** Reminder for an appointment (exam, anesthesiologist): before the time, leaving time to get there. */
val APPOINTMENT_REMINDER_LEAD: Duration = 2.hours

/** First day of the low-residue diet, or null with no diet. */
fun Exam.dietStart(): LocalDate? =
    dietDays.takeIf { it > 0 }?.let { at.date.minus(DatePeriod(days = it)) }

/** All filled-in steps, in chronological order. */
fun Exam.steps(): List<ExamStepTime> = listOfNotNull(
    anesthesiaAt?.let { ExamStepTime(ExamStep.ANESTHESIA, it) },
    dietStart()?.let { ExamStepTime(ExamStep.DIET, it.atTime(DIET_REMINDER_TIME)) },
    prepFirstAt?.let { ExamStepTime(ExamStep.PREP_FIRST, it) },
    prepSecondAt?.let { ExamStepTime(ExamStep.PREP_SECOND, it) },
    ExamStepTime(ExamStep.EXAM, at),
).sortedBy { it.at }

/** Notification time of a step: before an appointment, on time for the rest. */
fun ExamStepTime.reminderAt(): LocalDateTime = when (step) {
    ExamStep.ANESTHESIA, ExamStep.EXAM -> at.minusDuration(APPOINTMENT_REMINDER_LEAD)
    ExamStep.DIET, ExamStep.PREP_FIRST, ExamStep.PREP_SECOND -> at
}

/** A colonoscopy is done under anesthesia: the consultation must be planned. */
val ExamType.needsAnesthesia: Boolean get() = this == ExamType.COLONOSCOPY

/** Anesthesia consultation to schedule (colonoscopy with no fixed appointment). */
fun Exam.anesthesiaMissing(): Boolean = type.needsAnesthesia && anesthesiaAt == null

/** Clock arithmetic, independent of time changes: we stay in local time. */
@OptIn(ExperimentalTime::class)
private fun LocalDateTime.minusDuration(duration: Duration): LocalDateTime =
    toInstant(TimeZone.UTC).minus(duration).toLocalDateTime(TimeZone.UTC)
