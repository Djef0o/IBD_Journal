package fr.ibdjournal.app.notification

import android.content.Context
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.domain.ExamStep
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.labelRes
import kotlinx.datetime.LocalDateTime

/**
 * Title of an exam step, for the notification and the calendar event: the exam itself
 * ("Colonoscopy · Parc Clinic") or the step and its exam ("1st preparation dose ·
 * Colonoscopy").
 */
fun examStepTitle(context: Context, exam: Exam, step: ExamStep): String {
    val type = context.getString(exam.type.labelRes)
    return if (step == ExamStep.EXAM) {
        listOf(type, exam.note).filter { it.isNotBlank() }.joinToString(" · ")
    } else {
        "${context.getString(step.labelRes)} · $type"
    }
}

/** Text of a step's notification: its time and what needs to be done. */
fun examStepText(context: Context, exam: Exam, step: ExamStep, at: LocalDateTime): String {
    val examWhen = context.getString(R.string.exam_reminder_when, DateFormats.weekdayDayMonthInline(exam.at.date), exam.at.time())
    return when (step) {
        ExamStep.ANESTHESIA -> context.getString(R.string.exam_reminder_anesthesia, at.time(), examWhen)
        ExamStep.DIET -> context.getString(R.string.exam_reminder_diet, examWhen)
        ExamStep.PREP_FIRST, ExamStep.PREP_SECOND -> context.getString(R.string.exam_reminder_prep, examWhen)
        ExamStep.EXAM -> context.getString(R.string.exam_reminder_exam, at.time())
    }
}

private fun LocalDateTime.time(): String = DateFormats.time(hour, minute)
