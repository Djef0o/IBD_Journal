package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.data.model.Sex

/** Valeurs de référence ; une borne absente n'est pas vérifiée (CRP : seulement un maximum). */
data class ReferenceRange(val low: Double?, val high: Double?)

/** Position d'un résultat par rapport aux valeurs de référence. */
enum class LabStatus { LOW, NORMAL, HIGH }

/**
 * Valeurs usuelles de l'adulte, dans l'unité de l'app, quand le compte rendu n'en donne pas.
 * Sans sexe renseigné, les analyses qui en dépendent prennent l'intervalle le plus large
 * (borne basse des femmes, borne haute des hommes) : moins d'alertes, pas de fausse alarme.
 */
fun defaultReference(test: LabTest, sex: Sex?): ReferenceRange = when (test) {
    LabTest.CRP -> below(5.0)
    LabTest.CALPROTECTIN -> below(50.0)

    LabTest.RED_BLOOD_CELLS -> bySex(sex, female = 4.0 to 5.4, male = 4.5 to 5.9)
    LabTest.HEMOGLOBIN -> bySex(sex, female = 12.0 to 16.0, male = 13.0 to 17.0)
    LabTest.HEMATOCRIT -> bySex(sex, female = 36.0 to 47.0, male = 40.0 to 52.0)
    LabTest.MCV -> ReferenceRange(80.0, 100.0)
    LabTest.MCH -> ReferenceRange(27.0, 33.0)
    LabTest.MCHC -> ReferenceRange(32.0, 36.0)
    LabTest.WHITE_BLOOD_CELLS -> ReferenceRange(4.0, 10.0)
    LabTest.NEUTROPHILS -> ReferenceRange(1.8, 7.5)
    LabTest.LYMPHOCYTES -> ReferenceRange(1.0, 4.0)
    LabTest.MONOCYTES -> ReferenceRange(0.2, 1.0)
    LabTest.EOSINOPHILS -> below(0.5)
    LabTest.BASOPHILS -> below(0.1)
    LabTest.PLATELETS -> ReferenceRange(150.0, 400.0)

    LabTest.FERRITIN -> bySex(sex, female = 15.0 to 150.0, male = 30.0 to 400.0)
    LabTest.IRON -> bySex(sex, female = 50.0 to 170.0, male = 65.0 to 175.0)
    LabTest.TRANSFERRIN -> ReferenceRange(2.0, 3.6)
    LabTest.IRON_BINDING_CAPACITY -> ReferenceRange(250.0, 450.0)
    LabTest.TRANSFERRIN_SATURATION -> ReferenceRange(20.0, 40.0)

    LabTest.CREATININE -> bySex(sex, female = 45.0 to 90.0, male = 60.0 to 110.0)
    LabTest.EGFR -> ReferenceRange(60.0, null)
    LabTest.SODIUM -> ReferenceRange(136.0, 145.0)
    LabTest.POTASSIUM -> ReferenceRange(3.5, 5.1)
    LabTest.CHLORIDE -> ReferenceRange(98.0, 107.0)
    LabTest.CALCIUM -> ReferenceRange(2.2, 2.6)
    LabTest.URINE_PROTEIN -> below(0.15)

    LabTest.AST -> below(40.0)
    LabTest.ALT -> below(40.0)
    LabTest.GGT -> bySex(sex, female = null to 40.0, male = null to 60.0)
    LabTest.ALKALINE_PHOSPHATASE -> ReferenceRange(40.0, 130.0)
    LabTest.BILIRUBIN_TOTAL -> below(21.0)
    LabTest.BILIRUBIN_DIRECT -> below(5.0)
    LabTest.BILIRUBIN_INDIRECT -> below(17.0)

    LabTest.GLUCOSE -> ReferenceRange(0.7, 1.1)
    LabTest.CHOLESTEROL_TOTAL -> below(2.0)
    LabTest.CHOLESTEROL_HDL -> ReferenceRange(0.4, null)
    LabTest.CHOLESTEROL_LDL -> below(1.6)
    LabTest.TRIGLYCERIDES -> below(1.5)

    LabTest.VITAMIN_D -> ReferenceRange(30.0, 100.0)
    LabTest.ALBUMIN -> ReferenceRange(35.0, 50.0)

    LabTest.PROTHROMBIN_TIME -> ReferenceRange(70.0, null)
    LabTest.PSA -> below(4.0)
}

private fun below(high: Double) = ReferenceRange(null, high)

/** Intervalle selon le sexe ; sexe inconnu → borne basse la plus basse, borne haute la plus haute. */
private fun bySex(sex: Sex?, female: Pair<Double?, Double?>, male: Pair<Double?, Double?>): ReferenceRange =
    when (sex) {
        Sex.FEMALE -> ReferenceRange(female.first, female.second)
        Sex.MALE -> ReferenceRange(male.first, male.second)
        null -> ReferenceRange(
            low = listOfNotNull(female.first, male.first).minOrNull(),
            high = listOfNotNull(female.second, male.second).maxOrNull(),
        )
    }

/** Les valeurs du laboratoire priment : elles tiennent compte de sa méthode de dosage. */
fun LabResult.reference(sex: Sex?): ReferenceRange =
    if (referenceLow != null || referenceHigh != null) ReferenceRange(referenceLow, referenceHigh)
    else defaultReference(test, sex)

/**
 * Un seuil (« < 1 ») ne dit que d'un côté où est la valeur : « < 1 » n'est jamais haut,
 * « > 300 » jamais bas ; on ne conclut à l'anomalie que si le seuil lui-même est hors normes.
 */
fun LabResult.status(sex: Sex?): LabStatus {
    val range = reference(sex)
    val low = range.low
    val high = range.high
    return when {
        qualifier != LabQualifier.ABOVE && low != null && value < low -> LabStatus.LOW
        qualifier == LabQualifier.BELOW && low != null && value <= low -> LabStatus.LOW
        qualifier != LabQualifier.BELOW && high != null && value > high -> LabStatus.HIGH
        qualifier == LabQualifier.ABOVE && high != null && value >= high -> LabStatus.HIGH
        else -> LabStatus.NORMAL
    }
}
