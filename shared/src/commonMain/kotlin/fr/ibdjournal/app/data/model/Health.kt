package fr.ibdjournal.app.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Lab test families, in display order. */
@Serializable
enum class LabCategory { INFLAMMATION, BLOOD_COUNT, IRON, KIDNEY, LIVER, METABOLIC, NUTRITION, DRUG_MONITORING, OTHER }

/**
 * Recognized lab tests, with the entry unit: that of the most common French lab reports
 * (other units are converted on import). Names translated on the platform side.
 * The enum order is the display order; the database stores the name, not the rank.
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

    // Biologic drug monitoring: trough level and anti-drug antibodies. Entered by hand (assays and
    // units vary by laboratory; the antibody unit may be AU/mL on some reports: note it in the comment).
    INFLIXIMAB_LEVEL("µg/mL", LabCategory.DRUG_MONITORING),
    ADALIMUMAB_LEVEL("µg/mL", LabCategory.DRUG_MONITORING),
    ANTI_INFLIXIMAB_ANTIBODIES("ng/mL", LabCategory.DRUG_MONITORING),
    ANTI_ADALIMUMAB_ANTIBODIES("ng/mL", LabCategory.DRUG_MONITORING),

    PROTHROMBIN_TIME("%", LabCategory.OTHER),
    PSA("µg/L", LabCategory.OTHER),
}

/**
 * Lab result. [qualifier]: result given as a threshold ("< 1,0": below the measurement
 * threshold). [referenceLow]/[referenceHigh]: the lab's reference values, read from the
 * report or entered; null if the report gives none (the app does not supply any).
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

/** Exact result, or only bounded: "< 1,0" ([BELOW]), "> 300" ([ABOVE]). */
@Serializable
enum class LabQualifier { EXACT, BELOW, ABOVE }

@Serializable
enum class DoseStatus { TAKEN, MISSED }

/**
 * Confirmed scheduled dose (taken or missed). [scheduledAt]: local timestamp of the scheduled
 * occurrence, in milliseconds; a single entry per treatment and per occurrence.
 */
@Serializable
data class DoseLog(
    val id: Long = 0,
    val medicationId: Long,
    val scheduledAt: Long,
    val status: DoseStatus,
    val loggedAt: Long = nowEpochMillis(),
)

/** Body weight in kilograms on [date]; several entries the same day are allowed. */
@Serializable
data class Weight(
    val id: Long = 0,
    val date: LocalDate,
    val kg: Double,
)
