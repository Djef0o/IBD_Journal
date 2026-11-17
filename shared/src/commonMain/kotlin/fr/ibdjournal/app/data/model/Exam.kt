package fr.ibdjournal.app.data.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/** Endoscopic exams tracked in Care. */
@Serializable
enum class ExamType { COLONOSCOPY, RECTOSIGMOIDOSCOPY }

/**
 * Scheduled exam and its preparation: low-residue diet ([dietDays] days before, 0 = none),
 * doses of the drinkable preparation ([prepFirstAt], [prepSecondAt] for a split dose)
 * and, for colonoscopy under anesthesia, the anesthesia consultation ([anesthesiaAt], null
 * until it is set). [remindersEnabled]: reminders for each step.
 */
@Serializable
data class Exam(
    val id: Long = 0,
    val type: ExamType,
    val at: LocalDateTime,
    val note: String = "",
    val dietDays: Int = 0,
    val prepFirstAt: LocalDateTime? = null,
    val prepSecondAt: LocalDateTime? = null,
    val anesthesiaAt: LocalDateTime? = null,
    val remindersEnabled: Boolean = true,
)
