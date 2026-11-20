package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.domain.travelPlan
import fr.ibdjournal.app.domain.tripDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TravelTest {
    private val start = LocalDate(2026, 1, 1)
    private fun med(name: String, unit: RepeatUnit, every: Int = 1, stock: Int? = null) = Medication(
        id = name.hashCode().toLong(),
        name = name,
        schedule = ReminderSchedule(startDate = start, repeatEvery = every, repeatUnit = unit),
        stockDoses = stock,
    )

    private val departure = LocalDate(2026, 12, 1)
    private val back = LocalDate(2026, 12, 10)

    @Test
    fun dailyTreatmentCountsEveryDayOfTheTripPlusTheMargin() {
        val line = travelPlan(listOf(med("Azathioprine", RepeatUnit.DAY)), departure, back, marginDays = 3).single()
        assertEquals(10, line.onTrip)
        assertEquals(3, line.margin)
        assertEquals(13, line.total)
    }

    @Test
    fun weeklyInjectionCountsOnlyItsOwnDays() {
        // Starts Thursday Jan 1, 2026 every 2 weeks: Dec 3 and Dec 17 fall in 2026 (Dec 3 - Jan 1 = 336 days = 24 weeks).
        val line = travelPlan(listOf(med("Adalimumab", RepeatUnit.WEEK, every = 2)), departure, back, marginDays = 3).single()
        assertEquals(1, line.onTrip)
        assertEquals(0, line.margin)
    }

    @Test
    fun treatmentsWithNothingToTakeAreLeftOutAndTheRestIsSortedByName() {
        val plan = travelPlan(
            listOf(
                med("Zinc", RepeatUnit.DAY),
                med("Adalimumab", RepeatUnit.MONTH, every = 12),
                med("budesonide", RepeatUnit.DAY),
            ),
            departure, back,
        )
        assertEquals(listOf("budesonide", "Zinc"), plan.map { it.medication.name })
    }

    @Test
    fun missingComparesWithTheCountedStockOnly() {
        val counted = travelPlan(listOf(med("A", RepeatUnit.DAY, stock = 5)), departure, back, 0).single()
        assertEquals(5, counted.missing)
        val enough = travelPlan(listOf(med("A", RepeatUnit.DAY, stock = 50)), departure, back, 0).single()
        assertEquals(0, enough.missing)
        val uncounted = travelPlan(listOf(med("A", RepeatUnit.DAY)), departure, back, 0).single()
        assertNull(uncounted.missing)
    }

    @Test
    fun aReturnBeforeDepartureStillCountsOneDayAndNoIntakes() {
        assertEquals(1, tripDays(departure, LocalDate(2026, 11, 20)))
        assertEquals(10, tripDays(departure, back))
        assertTrue(travelPlan(listOf(med("A", RepeatUnit.DAY)), departure, LocalDate(2026, 11, 20), 0).isEmpty())
    }
}
