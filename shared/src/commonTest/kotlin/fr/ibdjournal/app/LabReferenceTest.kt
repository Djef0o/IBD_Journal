package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.data.model.Sex
import fr.ibdjournal.app.domain.LabStatus
import fr.ibdjournal.app.domain.status
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun usesSexSpecificDefaultsWhenTheLabGivesNone() {
        val hemoglobin = result(LabTest.HEMOGLOBIN, 12.6)
        assertEquals(LabStatus.LOW, hemoglobin.status(Sex.MALE))
        assertEquals(LabStatus.NORMAL, hemoglobin.status(Sex.FEMALE))
        // Sexe inconnu : intervalle le plus large, pas d'alerte.
        assertEquals(LabStatus.NORMAL, hemoglobin.status(null))
    }

    @Test
    fun labReferencesOverrideDefaults() {
        // 16,9 est normal pour un homme (13–17) mais dépasse les références du labo (13,4–16,7).
        assertEquals(LabStatus.NORMAL, result(LabTest.HEMOGLOBIN, 16.1, low = 13.4, high = 16.7).status(Sex.MALE))
        assertEquals(LabStatus.HIGH, result(LabTest.HEMOGLOBIN, 16.9, low = 13.4, high = 16.7).status(Sex.MALE))
        // Seuil seul : aucune borne basse vérifiée.
        assertEquals(LabStatus.NORMAL, result(LabTest.CRP, 0.5, high = 5.0).status(null))
    }

    @Test
    fun aThresholdOnlyFlagsWhatItProves() {
        // « < 1 » : sous 5, donc normal ; jamais « haut ».
        assertEquals(LabStatus.NORMAL, result(LabTest.CRP, 1.0, LabQualifier.BELOW).status(null))
        // « < 10 » ng/mL de vitamine D : forcément sous 30.
        assertEquals(LabStatus.LOW, result(LabTest.VITAMIN_D, 10.0, LabQualifier.BELOW).status(null))
        // « > 160 » mg/L de CRP : forcément au-dessus de 5.
        assertEquals(LabStatus.HIGH, result(LabTest.CRP, 160.0, LabQualifier.ABOVE).status(null))
        // « < 50 » mg/L de CRP : on ne peut pas conclure à l'anomalie.
        assertEquals(LabStatus.NORMAL, result(LabTest.CRP, 50.0, LabQualifier.BELOW).status(null))
    }
}
