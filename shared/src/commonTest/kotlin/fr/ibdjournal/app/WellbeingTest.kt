package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.BackupData
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.data.model.WellbeingDay
import fr.ibdjournal.app.domain.WellbeingFactor
import fr.ibdjournal.app.domain.toCsvFiles
import fr.ibdjournal.app.domain.wellbeingAverages
import fr.ibdjournal.app.domain.wellbeingLinks
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WellbeingTest {
    private val start = LocalDate(2026, 10, 1)
    private fun day(i: Int) = start.plus(i, DateTimeUnit.DAY)
    private fun bad(i: Int) = Stool(date = day(i), bristolType = 7)
    private fun good(i: Int) = Stool(date = day(i), bristolType = 4)

    @Test
    fun highStressDaysWithMoreBadDaysAreReportedAgainstTheOtherDays() {
        // Days 0-5: stress 5, 5 bad of 6. Days 6-15: stress 1, 1 bad of 10.
        val days = (0..5).map { WellbeingDay(day(it), stress = 5) } + (6..15).map { WellbeingDay(day(it), stress = 1) }
        val stools = (0..4).map { bad(it) } + good(5) + (6..14).map { good(it) } + bad(15)
        val links = wellbeingLinks(days, stools)
        assertEquals(1, links.size)
        val link = links.single()
        assertEquals(WellbeingFactor.HIGH_STRESS, link.factor)
        assertEquals(6, link.days)
        assertEquals(5.0 / 6, link.badRate, 1e-9)
        assertEquals(0.1, link.baseline, 1e-9)
    }

    @Test
    fun tooFewFlaggedDaysOrNoDifferenceGivesNothing() {
        val fewDays = (0..3).map { WellbeingDay(day(it), mood = 1) } + (4..12).map { WellbeingDay(day(it), mood = 5) }
        assertTrue(wellbeingLinks(fewDays, (0..12).map { bad(it) }).isEmpty())
        val sameRate = (0..5).map { WellbeingDay(day(it), sleep = 1) } + (6..11).map { WellbeingDay(day(it), sleep = 5) }
        assertTrue(wellbeingLinks(sameRate, (0..11).map { good(it) }).isEmpty())
    }

    @Test
    fun daysWithoutStoolsOrWithoutTheValueAreLeftOut() {
        // Stress noted on days 0-5 but stools only logged on days 3-5: only 3 flagged days, below the minimum.
        val days = (0..5).map { WellbeingDay(day(it), stress = 5) } + (6..15).map { WellbeingDay(day(it), sleep = 3) }
        val stools = (3..5).map { bad(it) } + (6..15).map { good(it) }
        assertTrue(wellbeingLinks(days, stools).none { it.factor == WellbeingFactor.HIGH_STRESS })
    }

    @Test
    fun periodDaysCompareAgainstOtherNotedDays() {
        val days = (0..5).map { WellbeingDay(day(it), period = true) } + (6..15).map { WellbeingDay(day(it), mood = 4) }
        val stools = (0..5).map { bad(it) } + (6..15).map { good(it) }
        assertEquals(listOf(WellbeingFactor.PERIOD), wellbeingLinks(days, stools).map { it.factor })
    }

    @Test
    fun averagesCountOnlyNotedValuesInsideThePeriod() {
        val days = listOf(
            WellbeingDay(day(0), sleep = 2, stress = 4),
            WellbeingDay(day(1), sleep = 4, mood = 3, period = true),
            WellbeingDay(day(20), sleep = 5),
        )
        val averages = wellbeingAverages(days, day(0), day(10))
        assertEquals(3.0, averages.sleep)
        assertEquals(4.0, averages.stress)
        assertEquals(3.0, averages.mood)
        assertEquals(1, averages.periodDays)
        assertEquals(2, averages.entries)
        assertNull(wellbeingAverages(emptyList(), day(0), day(10)).sleep)
    }

    @Test
    fun csvLeavesUnnotedValuesEmpty() {
        val csv = BackupData(
            meals = emptyList(),
            stools = emptyList(),
            wellbeing = listOf(WellbeingDay(day(0), sleep = 3, period = true), WellbeingDay(day(1), mood = 2)),
        ).toCsvFiles().getValue("wellbeing.csv")
        assertEquals("date,sleep,stress,mood,period\r\n2026-10-01,3,,,1\r\n2026-10-02,,,2,0\r\n", csv)
    }
}
