package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.domain.LabStatus
import fr.ibdjournal.app.domain.reference
import fr.ibdjournal.app.domain.status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class LabReferenceTest {

    private fun result(
        test: LabTest,
        value: Double,
        qualifier: LabQualifier = LabQualifier.EXACT,
        low: Double? = null,
        high: Double? = null,
    ) = LabResult(test = test, value = value, date = LocalDate(2026, 11, 10), qualifier = qualifier, referenceLow = low, referenceHigh = high)

    @Test
    fun withoutLabValuesThereIsNoReferenceNorStatus() {
        // The app provides no reference value, whatever the value.
        LabTest.entries.forEach { test ->
            assertNull(result(test, 0.001).reference())
            assertNull(result(test, 100_000.0).status())
        }
    }

    @Test
    fun comparesOnlyToTheLabValues() {
        assertNull(result(LabTest.HEMOGLOBIN, 16.1, low = 13.4, high = 16.7).status())
        assertEquals(LabStatus.ABOVE, result(LabTest.HEMOGLOBIN, 16.9, low = 13.4, high = 16.7).status())
        assertEquals(LabStatus.BELOW, result(LabTest.HEMOGLOBIN, 13.0, low = 13.4, high = 16.7).status())
        // Threshold alone: no lower bound checked.
        assertNull(result(LabTest.CRP, 0.5, high = 5.0).status())
    }

    @Test
    fun aThresholdOnlyFlagsWhatItProves() {
        // "< 1" under a maximum of 5: inside; never above.
        assertNull(result(LabTest.CRP, 1.0, LabQualifier.BELOW, high = 5.0).status())
        // "< 10" under a minimum of 30: necessarily below.
        assertEquals(LabStatus.BELOW, result(LabTest.VITAMIN_D, 10.0, LabQualifier.BELOW, low = 30.0).status())
        // "> 160" above a maximum of 5: necessarily above.
        assertEquals(LabStatus.ABOVE, result(LabTest.CRP, 160.0, LabQualifier.ABOVE, high = 5.0).status())
        // "< 50" with a maximum of 5: cannot conclude.
        assertNull(result(LabTest.CRP, 50.0, LabQualifier.BELOW, high = 5.0).status())
    }
}
