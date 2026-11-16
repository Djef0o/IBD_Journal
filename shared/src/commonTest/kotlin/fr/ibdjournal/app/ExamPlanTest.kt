package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.data.model.ExamType
import fr.ibdjournal.app.domain.ExamStep
import fr.ibdjournal.app.domain.anesthesiaMissing
import fr.ibdjournal.app.domain.anesthesiaTooLate
import fr.ibdjournal.app.domain.events
import fr.ibdjournal.app.domain.reminderAt
import fr.ibdjournal.app.domain.steps
import fr.ibdjournal.app.domain.withDefaultPreparation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

class ExamPlanTest {

    private val colonoscopy = Exam(type = ExamType.COLONOSCOPY, at = LocalDateTime(2026, 12, 4, 9, 30))

    @Test
    fun colonoscopyGetsASplitPreparationAndThreeDaysOfDiet() {
        val exam = colonoscopy.withDefaultPreparation()
        assertEquals(3, exam.dietDays)
        assertEquals(LocalDateTime(2026, 12, 3, 19, 0), exam.prepFirstAt)
        // Seconde prise le matin même, 5 h avant ; à travers minuit si l'examen est tôt.
        assertEquals(LocalDateTime(2026, 12, 4, 4, 30), exam.prepSecondAt)
        assertEquals(
            LocalDateTime(2026, 12, 4, 2, 0),
            colonoscopy.copy(at = LocalDateTime(2026, 12, 4, 7, 0)).withDefaultPreparation().prepSecondAt,
        )

        val rectosigmoidoscopy = colonoscopy.copy(type = ExamType.RECTOSIGMOIDOSCOPY).withDefaultPreparation()
        assertEquals(1, rectosigmoidoscopy.dietDays)
        assertNull(rectosigmoidoscopy.prepSecondAt)
    }

    @Test
    fun stepsAreChronologicalWithAppointmentRemindersTwoHoursBefore() {
        val exam = colonoscopy.withDefaultPreparation().copy(anesthesiaAt = LocalDateTime(2026, 11, 20, 10, 15))
        val steps = exam.steps()
        assertEquals(
            listOf(ExamStep.ANESTHESIA, ExamStep.DIET, ExamStep.PREP_FIRST, ExamStep.PREP_SECOND, ExamStep.EXAM),
            steps.map { it.step },
        )
        assertEquals(LocalDateTime(2026, 12, 1, 8, 0), steps.first { it.step == ExamStep.DIET }.at)
        assertEquals(LocalDateTime(2026, 11, 20, 8, 15), steps.first().reminderAt())
        assertEquals(LocalDateTime(2026, 12, 4, 7, 30), steps.last().reminderAt())
        assertEquals(LocalDateTime(2026, 12, 3, 19, 0), steps.first { it.step == ExamStep.PREP_FIRST }.reminderAt())
    }

    @Test
    fun anesthesiaConsultationMustBeTwoDaysBefore() {
        assertTrue(colonoscopy.anesthesiaMissing())
        assertFalse(colonoscopy.copy(type = ExamType.RECTOSIGMOIDOSCOPY).anesthesiaMissing())
        assertFalse(colonoscopy.copy(anesthesiaAt = LocalDateTime(2026, 12, 2, 9, 30)).anesthesiaTooLate())
        assertTrue(colonoscopy.copy(anesthesiaAt = LocalDateTime(2026, 12, 2, 10, 0)).anesthesiaTooLate())
    }

    @Test
    fun calendarEventsFollowTheRemindersSwitch() {
        val exam = colonoscopy.withDefaultPreparation()
        assertNull(exam.events()[ExamStep.ANESTHESIA])
        assertEquals(LocalDateTime(2026, 12, 4, 9, 30), exam.events()[ExamStep.EXAM]?.start)
        assertTrue(exam.copy(remindersEnabled = false).events().values.all { it == null })
    }
}
