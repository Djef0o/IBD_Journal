package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.DoseStatus
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.domain.crossedLowStock
import fr.ibdjournal.app.domain.stockDelta
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StockTest {
    private fun med(stock: Int?, low: Int = 7) = Medication(
        id = 1,
        name = "Azathioprine",
        schedule = ReminderSchedule(startDate = LocalDate(2026, 1, 1)),
        stockDoses = stock,
        lowStockDoses = low,
    )

    @Test
    fun onlyAConfirmedTakenDoseUsesUpStock() {
        assertEquals(-1, stockDelta(null, DoseStatus.TAKEN))
        assertEquals(-1, stockDelta(DoseStatus.MISSED, DoseStatus.TAKEN))
        assertEquals(0, stockDelta(null, DoseStatus.MISSED))
        assertEquals(0, stockDelta(DoseStatus.TAKEN, DoseStatus.TAKEN))
        assertEquals(0, stockDelta(null, null))
    }

    @Test
    fun undoingOrChangingATakenDoseGivesItBack() {
        assertEquals(1, stockDelta(DoseStatus.TAKEN, null))
        assertEquals(1, stockDelta(DoseStatus.TAKEN, DoseStatus.MISSED))
    }

    @Test
    fun lowStockIsReachedAtTheThresholdAndNeverWithoutACount() {
        assertTrue(med(7).isLowStock)
        assertTrue(med(0).isLowStock)
        assertFalse(med(8).isLowStock)
        assertFalse(med(null).isLowStock)
        assertTrue(med(2, low = 2).isLowStock)
    }

    @Test
    fun theAlertFiresOnlyWhenTheStockFallsAcrossTheThreshold() {
        assertTrue(crossedLowStock(med(8), med(7)))
        assertFalse(crossedLowStock(med(7), med(6)))
        assertFalse(crossedLowStock(med(9), med(8)))
        // Stock first entered already low, or not known before: no alert.
        assertFalse(crossedLowStock(null, med(3)))
        assertFalse(crossedLowStock(med(null), med(3)))
        // Restocking never alerts.
        assertFalse(crossedLowStock(med(3), med(30)))
    }
}
