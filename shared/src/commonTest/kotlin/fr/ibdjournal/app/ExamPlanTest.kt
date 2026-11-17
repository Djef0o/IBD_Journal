package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.data.model.ExamType
import fr.ibdjournal.app.domain.ExamStep
import fr.ibdjournal.app.domain.anesthesiaMissing
import fr.ibdjournal.app.domain.events
import fr.ibdjournal.app.domain.reminderAt
import fr.ibdjournal.app.domain.steps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

class ExamPlanTest {

    private val colonoscopy = Exam(type = ExamType.COLONOSCOPY, at = LocalDateTime(2026, 12, 4, 9, 30))

    /** Preparation entered by the user, following their doctor's instructions. */
    private val prepared = colonoscopy.copy(
        dietDays = 3,
        prepFirstAt = LocalDateTime(2026, 12, 3, 19, 0),
        prepSecondAt = LocalDateTime(2026, 12, 4, 4, 30),
    )

    @Test
    fun aNewExamHasOnlyItsOwnStep() {
        // No preparation suggested by the app: only the exam is dated.
        assertEquals(listOf(ExamStep.EXAM), colonoscopy.steps().map { it.step })
    }

    @Test
    fun stepsAreChronologicalWithAppointmentRemindersTwoHoursBefore() {
        val exam = prepared.copy(anesthesiaAt = LocalDateTime(2026, 11, 20, 10, 15))
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
    fun anesthesiaConsultationToBookForColonoscopy() {
        assertTrue(colonoscopy.anesthesiaMissing())
        assertFalse(colonoscopy.copy(type = ExamType.RECTOSIGMOIDOSCOPY).anesthesiaMissing())
        assertFalse(colonoscopy.copy(anesthesiaAt = LocalDateTime(2026, 12, 2, 9, 30)).anesthesiaMissing())
    }

    @Test
    fun calendarEventsFollowTheRemindersSwitch() {
        assertNull(prepared.events()[ExamStep.ANESTHESIA])
        assertEquals(LocalDateTime(2026, 12, 4, 9, 30), prepared.events()[ExamStep.EXAM]?.start)
        assertTrue(prepared.copy(remindersEnabled = false).events().values.all { it == null })
    }
}
