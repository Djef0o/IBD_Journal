package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.domain.dailyStats
import fr.ibdjournal.app.domain.nextWeighIn
import fr.ibdjournal.app.domain.parseWeightKg
import fr.ibdjournal.app.domain.weekView
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeekSummaryTest {
    private val today = LocalDate(2026, 11, 20)

    @Test
    fun falseUrgesAreNotCountedButTheirBloodIs() {
        val stats = listOf(
            Stool(date = today, bristolType = 6),
            Stool(date = today, bristolType = 4),
            Stool(date = today, bristolType = 0, bloodLevel = 2),
        ).dailyStats()
        assertEquals(1, stats.size)
        assertEquals(2, stats[0].stools)
        assertEquals(10, stats[0].bristolSum)
        assertEquals(true, stats[0].blood)
    }

    @Test
    fun weekCoversSevenDaysEndingTodayWithEmptyDaysIncluded() {
        val stats = listOf(
            Stool(date = LocalDate(2026, 11, 14), bristolType = 7),
            Stool(date = LocalDate(2026, 11, 13), bristolType = 1),
            Stool(date = today, bristolType = 5, bloodLevel = 1),
        ).dailyStats()
        val week = weekView(stats, today)
        assertEquals(7, week.days.size)
        assertEquals(LocalDate(2026, 11, 14), week.days.first().date)
        assertEquals(today, week.days.last().date)
        // Nov 13 is outside the window.
        assertEquals(2, week.stools)
        assertEquals(6.0, week.averageBristol)
        assertEquals(1, week.bloodDays)
    }

    @Test
    fun noStoolMeansNoAverage() {
        assertNull(weekView(emptyList(), today).averageBristol)
    }

    @Test
    fun weighInIsTheNextChosenWeekdayAtNine() {
        // Friday Nov 20, 2026
        assertEquals(LocalDateTime(2026, 11, 22, 9, 0), nextWeighIn(DayOfWeek.SUNDAY, LocalDateTime(2026, 11, 20, 12, 0)))
        // Same day before 9: today; at 9 or later: next week.
        assertEquals(LocalDateTime(2026, 11, 20, 9, 0), nextWeighIn(DayOfWeek.FRIDAY, LocalDateTime(2026, 11, 20, 8, 0)))
        assertEquals(LocalDateTime(2026, 11, 27, 9, 0), nextWeighIn(DayOfWeek.FRIDAY, LocalDateTime(2026, 11, 20, 9, 0)))
        // Earlier weekday: wraps to next week.
        assertEquals(LocalDateTime(2026, 11, 23, 9, 0), nextWeighIn(DayOfWeek.MONDAY, LocalDateTime(2026, 11, 20, 12, 0)))
    }

    @Test
    fun weightTypedInANotificationAcceptsCommaUnitAndRejectsNonsense() {
        assertEquals(71.8, parseWeightKg("71,8"))
        assertEquals(72.0, parseWeightKg(" 72 kg "))
        assertEquals(71.8, parseWeightKg("71.84"))
        assertNull(parseWeightKg("abc"))
        assertNull(parseWeightKg("7"))
        assertNull(parseWeightKg("718"))
    }
}
