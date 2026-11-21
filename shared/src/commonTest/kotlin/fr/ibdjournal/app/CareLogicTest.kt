package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.Appointment
import fr.ibdjournal.app.data.model.AppointmentKind
import fr.ibdjournal.app.data.model.Flare
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.SteroidCourse
import fr.ibdjournal.app.data.model.SteroidStep
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.data.model.Vaccination
import fr.ibdjournal.app.data.model.VaccineType
import fr.ibdjournal.app.domain.covers
import fr.ibdjournal.app.domain.nextReminder
import kotlinx.datetime.LocalDateTime
import fr.ibdjournal.app.domain.cumulativeMgThrough
import fr.ibdjournal.app.domain.doseOn
import fr.ibdjournal.app.domain.end
import fr.ibdjournal.app.domain.foodSymptomLinks
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SteroidCourseTest {
    private val start = LocalDate(2026, 11, 1)
    private val course = SteroidCourse(
        drug = "Prednisone",
        start = start,
        steps = listOf(SteroidStep(40.0, 3), SteroidStep(30.0, 2), SteroidStep(20.0, 1)),
    )

    @Test
    fun doseFollowsTheStepsAndEndsAfterTheLastDay() {
        assertNull(course.doseOn(start.plus(-1, DateTimeUnit.DAY)))
        assertEquals(40.0, course.doseOn(start))
        assertEquals(40.0, course.doseOn(start.plus(2, DateTimeUnit.DAY)))
        assertEquals(30.0, course.doseOn(start.plus(3, DateTimeUnit.DAY)))
        assertEquals(20.0, course.doseOn(start.plus(5, DateTimeUnit.DAY)))
        assertNull(course.doseOn(start.plus(6, DateTimeUnit.DAY)))
        assertEquals(start.plus(5, DateTimeUnit.DAY), course.end)
    }

    @Test
    fun cumulativeDoseCountsDaysTakenSoFarIncludingToday() {
        assertEquals(0.0, course.cumulativeMgThrough(start.plus(-1, DateTimeUnit.DAY)))
        assertEquals(40.0, course.cumulativeMgThrough(start))
        assertEquals(120.0 + 30.0, course.cumulativeMgThrough(start.plus(3, DateTimeUnit.DAY)))
        assertEquals(120.0 + 60.0 + 20.0, course.cumulativeMgThrough(start.plus(30, DateTimeUnit.DAY)))
    }
}

class FoodSymptomLinksTest {
    private val first = LocalDate(2026, 10, 1)

    private fun day(offset: Int) = first.plus(offset, DateTimeUnit.DAY)

    private fun meal(offset: Int, text: String) = Meal(date = day(offset), type = MealType.LUNCH, description = text)

    @Test
    fun reportsAFoodFollowedByBadDaysMuchMoreThanUsual() {
        // 12 days with one stool each (2 days apart so windows do not overlap); the 4 "lait" days are bad.
        val meals = mutableListOf<Meal>()
        val stools = mutableListOf<Stool>()
        for (i in 0 until 12) {
            val offset = i * 3
            val milk = i < 4
            meals += meal(offset, if (milk) "Lait, riz" else "Riz, poulet")
            stools += Stool(date = day(offset), bristolType = if (milk) 7 else 4)
        }
        val links = foodSymptomLinks(meals, stools)
        assertEquals(listOf("lait"), links.map { it.item })
        assertEquals(1.0, links.single().badRate)
        assertEquals(4.0 / 12, links.single().baseline)
    }

    @Test
    fun staysSilentBelowTheMinimumOccurrences() {
        val meals = (0 until 3).map { meal(it * 3, "Lait") } + (3 until 9).map { meal(it * 3, "Riz") }
        val stools = (0 until 9).map { Stool(date = day(it * 3), bristolType = if (it < 3) 7 else 4) }
        assertEquals(emptyList(), foodSymptomLinks(meals, stools))
    }

    @Test
    fun daysWithoutLoggedStoolsAreIgnored() {
        assertEquals(emptyList(), foodSymptomLinks((0 until 10).map { meal(it * 3, "Lait") }, emptyList()))
    }
}

class VaccineReminderTest {
    private val due = LocalDate(2026, 12, 20)
    private fun vaccine(enabled: Boolean = true, nextDue: LocalDate? = due) =
        Vaccination(type = VaccineType.INFLUENZA, date = LocalDate(2025, 12, 20), nextDue = nextDue, reminderEnabled = enabled)

    @Test
    fun remindsAWeekBeforeThenOnTheDay() {
        val first = vaccine().nextReminder(LocalDateTime(2026, 11, 19, 12, 0))
        assertEquals(LocalDateTime(2026, 12, 13, 9, 0), first)
        assertEquals(LocalDateTime(2026, 12, 20, 9, 0), vaccine().nextReminder(first!!))
        assertNull(vaccine().nextReminder(LocalDateTime(2026, 12, 20, 9, 0)))
    }

    @Test
    fun skipsThePastLeadWhenTheDueDateIsClose() {
        assertEquals(LocalDateTime(2026, 12, 20, 9, 0), vaccine().nextReminder(LocalDateTime(2026, 12, 15, 8, 0)))
    }

    @Test
    fun staysSilentWhenOffOrWithoutDueDate() {
        assertNull(vaccine(enabled = false).nextReminder(LocalDateTime(2026, 1, 1, 0, 0)))
        assertNull(vaccine(nextDue = null).nextReminder(LocalDateTime(2026, 1, 1, 0, 0)))
    }
}

class FlareCoverageTest {
    private val today = LocalDate(2026, 11, 19)

    @Test
    fun anOngoingFlareStopsAtToday() {
        val ongoing = Flare(start = LocalDate(2026, 11, 10))
        assertEquals(false, ongoing.covers(LocalDate(2026, 11, 9), today))
        assertEquals(true, ongoing.covers(LocalDate(2026, 11, 10), today))
        assertEquals(true, ongoing.covers(today, today))
        assertEquals(false, ongoing.covers(LocalDate(2026, 11, 20), today))
    }

    @Test
    fun anEndedFlareCoversItsLastDay() {
        val ended = Flare(start = LocalDate(2026, 11, 1), end = LocalDate(2026, 11, 5))
        assertEquals(true, ended.covers(LocalDate(2026, 11, 5), today))
        assertEquals(false, ended.covers(LocalDate(2026, 11, 6), today))
    }
}

class AppointmentReminderTest {
    private fun appointment(at: LocalDateTime, enabled: Boolean = true) =
        Appointment(kind = AppointmentKind.GASTROENTEROLOGIST, at = at, remindersEnabled = enabled)

    @Test
    fun remindsTheEveningBeforeThenTwoHoursBefore() {
        val visit = appointment(LocalDateTime(2026, 12, 10, 14, 30))
        val first = visit.nextReminder(LocalDateTime(2026, 12, 1, 8, 0))
        assertEquals(LocalDateTime(2026, 12, 9, 18, 0), first)
        val second = visit.nextReminder(first!!)
        assertEquals(LocalDateTime(2026, 12, 10, 12, 30), second)
        assertNull(visit.nextReminder(second!!))
    }

    @Test
    fun anEarlyMorningAppointmentGetsItsSecondReminderTheNightBefore() {
        // Two hours before 01:30 is the previous day 23:30, after the 18:00 evening reminder.
        val visit = appointment(LocalDateTime(2026, 12, 10, 1, 30))
        val first = visit.nextReminder(LocalDateTime(2026, 12, 1, 8, 0))
        assertEquals(LocalDateTime(2026, 12, 9, 18, 0), first)
        assertEquals(LocalDateTime(2026, 12, 9, 23, 30), visit.nextReminder(first!!))
    }

    @Test
    fun skipsPastRemindersAndStaysSilentWhenOff() {
        val visit = appointment(LocalDateTime(2026, 12, 10, 14, 30))
        assertEquals(LocalDateTime(2026, 12, 10, 12, 30), visit.nextReminder(LocalDateTime(2026, 12, 10, 8, 0)))
        assertNull(visit.nextReminder(LocalDateTime(2026, 12, 10, 13, 0)))
        assertNull(appointment(LocalDateTime(2026, 12, 10, 14, 30), enabled = false).nextReminder(LocalDateTime(2026, 1, 1, 0, 0)))
    }
}
