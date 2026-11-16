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
        // L'app ne fournit aucune valeur de référence, quelle que soit la valeur.
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
        // Seuil seul : aucune borne basse vérifiée.
        assertNull(result(LabTest.CRP, 0.5, high = 5.0).status())
    }

    @Test
    fun aThresholdOnlyFlagsWhatItProves() {
        // « < 1 » sous un maximum de 5 : dedans ; jamais au-dessus.
        assertNull(result(LabTest.CRP, 1.0, LabQualifier.BELOW, high = 5.0).status())
        // « < 10 » sous un minimum de 30 : forcément en dessous.
        assertEquals(LabStatus.BELOW, result(LabTest.VITAMIN_D, 10.0, LabQualifier.BELOW, low = 30.0).status())
        // « > 160 » au-dessus d'un maximum de 5 : forcément au-dessus.
        assertEquals(LabStatus.ABOVE, result(LabTest.CRP, 160.0, LabQualifier.ABOVE, high = 5.0).status())
        // « < 50 » avec un maximum de 5 : on ne peut pas conclure.
        assertNull(result(LabTest.CRP, 50.0, LabQualifier.BELOW, high = 5.0).status())
    }
}
