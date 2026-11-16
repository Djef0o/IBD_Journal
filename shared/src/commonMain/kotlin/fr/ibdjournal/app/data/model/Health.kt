package fr.ibdjournal.app.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Familles d'analyses, dans l'ordre d'affichage. */
@Serializable
enum class LabCategory { INFLAMMATION, BLOOD_COUNT, IRON, KIDNEY, LIVER, METABOLIC, NUTRITION, OTHER }

/**
 * Analyses reconnues, avec l'unité de saisie : celle des comptes rendus français les plus
 * fréquents (les autres unités sont converties à l'import). Noms traduits côté plateforme.
 * L'ordre de l'énumération est l'ordre d'affichage ; la base stocke le nom, pas le rang.
 */
@Serializable
enum class LabTest(val unit: String, val category: LabCategory) {
    CRP("mg/L", LabCategory.INFLAMMATION),
    CALPROTECTIN("µg/g", LabCategory.INFLAMMATION),

    RED_BLOOD_CELLS("T/L", LabCategory.BLOOD_COUNT),
    HEMOGLOBIN("g/dL", LabCategory.BLOOD_COUNT),
    HEMATOCRIT("%", LabCategory.BLOOD_COUNT),
    MCV("fL", LabCategory.BLOOD_COUNT),
    MCH("pg", LabCategory.BLOOD_COUNT),
    MCHC("g/dL", LabCategory.BLOOD_COUNT),
    WHITE_BLOOD_CELLS("G/L", LabCategory.BLOOD_COUNT),
    NEUTROPHILS("G/L", LabCategory.BLOOD_COUNT),
    LYMPHOCYTES("G/L", LabCategory.BLOOD_COUNT),
    MONOCYTES("G/L", LabCategory.BLOOD_COUNT),
    EOSINOPHILS("G/L", LabCategory.BLOOD_COUNT),
    BASOPHILS("G/L", LabCategory.BLOOD_COUNT),
    PLATELETS("G/L", LabCategory.BLOOD_COUNT),

    FERRITIN("µg/L", LabCategory.IRON),
    IRON("µg/dL", LabCategory.IRON),
    TRANSFERRIN("g/L", LabCategory.IRON),
    IRON_BINDING_CAPACITY("µg/dL", LabCategory.IRON),
    TRANSFERRIN_SATURATION("%", LabCategory.IRON),

    CREATININE("µmol/L", LabCategory.KIDNEY),
    EGFR("mL/min/1,73 m²", LabCategory.KIDNEY),
    SODIUM("mmol/L", LabCategory.KIDNEY),
    POTASSIUM("mmol/L", LabCategory.KIDNEY),
    CHLORIDE("mmol/L", LabCategory.KIDNEY),
    CALCIUM("mmol/L", LabCategory.KIDNEY),
    URINE_PROTEIN("g/L", LabCategory.KIDNEY),

    AST("U/L", LabCategory.LIVER),
    ALT("U/L", LabCategory.LIVER),
    GGT("U/L", LabCategory.LIVER),
    ALKALINE_PHOSPHATASE("U/L", LabCategory.LIVER),
    BILIRUBIN_TOTAL("µmol/L", LabCategory.LIVER),
    BILIRUBIN_DIRECT("µmol/L", LabCategory.LIVER),
    BILIRUBIN_INDIRECT("µmol/L", LabCategory.LIVER),

    GLUCOSE("g/L", LabCategory.METABOLIC),
    CHOLESTEROL_TOTAL("g/L", LabCategory.METABOLIC),
    CHOLESTEROL_HDL("g/L", LabCategory.METABOLIC),
    CHOLESTEROL_LDL("g/L", LabCategory.METABOLIC),
    TRIGLYCERIDES("g/L", LabCategory.METABOLIC),

    VITAMIN_D("ng/mL", LabCategory.NUTRITION),
    ALBUMIN("g/L", LabCategory.NUTRITION),

    PROTHROMBIN_TIME("%", LabCategory.OTHER),
    PSA("µg/L", LabCategory.OTHER),
}

/**
 * Résultat d'analyse. [qualifier] : résultat donné comme seuil (« < 1,0 » : sous le seuil de
 * mesure). [referenceLow]/[referenceHigh] : valeurs de référence du laboratoire, lues sur le
 * compte rendu ou saisies ; null si le compte rendu n'en donne pas (l'app n'en fournit pas).
 */
@Serializable
data class LabResult(
    val id: Long = 0,
    val test: LabTest,
    val value: Double,
    val date: LocalDate,
    val note: String = "",
    val qualifier: LabQualifier = LabQualifier.EXACT,
    val referenceLow: Double? = null,
    val referenceHigh: Double? = null,
)

/** Résultat exact, ou seulement borné : « < 1,0 » ([BELOW]), « > 300 » ([ABOVE]). */
@Serializable
enum class LabQualifier { EXACT, BELOW, ABOVE }

@Serializable
enum class DoseStatus { TAKEN, MISSED }

/**
 * Prise prévue confirmée (prise ou oubliée). [scheduledAt] : horodatage local de l'occurrence
 * prévue, en millisecondes ; une seule entrée par traitement et par occurrence.
 */
@Serializable
data class DoseLog(
    val id: Long = 0,
    val medicationId: Long,
    val scheduledAt: Long,
    val status: DoseStatus,
    val loggedAt: Long = nowEpochMillis(),
)
