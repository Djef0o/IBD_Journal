package fr.ibdjournal.app.notification

import android.content.Context
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.domain.ExamStep
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.labelRes
import kotlinx.datetime.LocalDateTime

/**
 * Titre d'une étape d'examen, pour la notification et l'événement d'agenda : l'examen lui-même
 * (« Coloscopie · Clinique du Parc ») ou l'étape et son examen (« 1re prise de la préparation ·
 * Coloscopie »).
 */
fun examStepTitle(context: Context, exam: Exam, step: ExamStep): String {
    val type = context.getString(exam.type.labelRes)
    return if (step == ExamStep.EXAM) {
        listOf(type, exam.note).filter { it.isNotBlank() }.joinToString(" · ")
    } else {
        "${context.getString(step.labelRes)} · $type"
    }
}

/** Texte de la notification d'une étape : son heure et ce qu'il faut faire. */
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
