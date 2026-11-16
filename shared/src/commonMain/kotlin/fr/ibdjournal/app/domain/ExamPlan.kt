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

/** Étapes d'un examen, dans l'ordre où elles arrivent en général. */
enum class ExamStep { ANESTHESIA, DIET, PREP_FIRST, PREP_SECOND, EXAM }

/** Étape datée d'un examen : [at] heure de l'étape (début du régime : le matin). */
data class ExamStepTime(val step: ExamStep, val at: LocalDateTime)

/** Le régime sans résidu commence ce jour-là, rappel le matin. */
val DIET_REMINDER_TIME = LocalTime(8, 0)

/** Rappel d'un rendez-vous (examen, anesthésiste) : avant l'heure, le temps de s'y rendre. */
val APPOINTMENT_REMINDER_LEAD: Duration = 2.hours

/** Premier jour du régime sans résidu, ou null sans régime. */
fun Exam.dietStart(): LocalDate? =
    dietDays.takeIf { it > 0 }?.let { at.date.minus(DatePeriod(days = it)) }

/** Toutes les étapes renseignées, dans l'ordre chronologique. */
fun Exam.steps(): List<ExamStepTime> = listOfNotNull(
    anesthesiaAt?.let { ExamStepTime(ExamStep.ANESTHESIA, it) },
    dietStart()?.let { ExamStepTime(ExamStep.DIET, it.atTime(DIET_REMINDER_TIME)) },
    prepFirstAt?.let { ExamStepTime(ExamStep.PREP_FIRST, it) },
    prepSecondAt?.let { ExamStepTime(ExamStep.PREP_SECOND, it) },
    ExamStepTime(ExamStep.EXAM, at),
).sortedBy { it.at }

/** Heure de la notification d'une étape : avant un rendez-vous, à l'heure pour le reste. */
fun ExamStepTime.reminderAt(): LocalDateTime = when (step) {
    ExamStep.ANESTHESIA, ExamStep.EXAM -> at.minusDuration(APPOINTMENT_REMINDER_LEAD)
    ExamStep.DIET, ExamStep.PREP_FIRST, ExamStep.PREP_SECOND -> at
}

/** La coloscopie se fait sous anesthésie : la consultation est à prévoir. */
val ExamType.needsAnesthesia: Boolean get() = this == ExamType.COLONOSCOPY

/** Consultation d'anesthésie à prendre (coloscopie sans rendez-vous fixé). */
fun Exam.anesthesiaMissing(): Boolean = type.needsAnesthesia && anesthesiaAt == null

/** Arithmétique d'horloge, indépendante des changements d'heure : on reste en heure locale. */
@OptIn(ExperimentalTime::class)
private fun LocalDateTime.minusDuration(duration: Duration): LocalDateTime =
    toInstant(TimeZone.UTC).minus(duration).toLocalDateTime(TimeZone.UTC)
