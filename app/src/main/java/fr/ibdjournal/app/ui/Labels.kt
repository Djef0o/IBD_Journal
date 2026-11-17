package fr.ibdjournal.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.CheckupType
import fr.ibdjournal.app.data.model.ExamType
import fr.ibdjournal.app.data.model.Indication
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.LabCategory
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.data.model.MedicationCategory
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.data.model.Sex
import fr.ibdjournal.app.data.model.StandardMedication
import fr.ibdjournal.app.data.model.SupportiveMedication
import fr.ibdjournal.app.domain.AnalysisPeriod
import fr.ibdjournal.app.domain.ExamStep
import fr.ibdjournal.app.domain.Symptom

/*
 * Android labels for the shared module's enumerations, which does not know the
 * platform resources.
 */

@get:StringRes
val MealType.labelRes: Int
    get() = when (this) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
    }

@get:StringRes
val RepeatUnit.labelRes: Int
    get() = when (this) {
        RepeatUnit.ONCE -> R.string.repeat_unit_once
        RepeatUnit.DAY -> R.string.repeat_unit_day
        RepeatUnit.WEEK -> R.string.repeat_unit_week
        RepeatUnit.MONTH -> R.string.repeat_unit_month
    }

@get:StringRes
val CheckupType.labelRes: Int
    get() = when (this) {
        CheckupType.BLOOD_TEST -> R.string.checkup_blood_test
        CheckupType.STOOL_TEST -> R.string.checkup_stool_test
    }

@get:StringRes
val ExamType.labelRes: Int
    get() = when (this) {
        ExamType.COLONOSCOPY -> R.string.exam_colonoscopy
        ExamType.RECTOSIGMOIDOSCOPY -> R.string.exam_rectosigmoidoscopy
    }

@get:StringRes
val ExamStep.labelRes: Int
    get() = when (this) {
        ExamStep.ANESTHESIA -> R.string.exam_step_anesthesia
        ExamStep.DIET -> R.string.exam_step_diet
        ExamStep.PREP_FIRST -> R.string.exam_step_prep_first
        ExamStep.PREP_SECOND -> R.string.exam_step_prep_second
        ExamStep.EXAM -> R.string.exam_step_exam
    }

@get:StringRes
val MedicationCategory.labelRes: Int
    get() = when (this) {
        MedicationCategory.AMINOSALICYLATES -> R.string.med_category_aminosalicylates
        MedicationCategory.CORTICOSTEROIDS -> R.string.med_category_corticosteroids
        MedicationCategory.IMMUNOSUPPRESSANTS -> R.string.med_category_immunosuppressants
        MedicationCategory.BIOLOGICS -> R.string.med_category_biologics
        MedicationCategory.SMALL_MOLECULES -> R.string.med_category_small_molecules
        MedicationCategory.ANTIBIOTICS -> R.string.med_category_antibiotics
        MedicationCategory.SUPPORTIVE -> R.string.med_category_supportive
    }

@get:StringRes
val Indication.labelRes: Int
    get() = when (this) {
        Indication.CROHN -> R.string.indication_crohn
        Indication.UC -> R.string.indication_uc
    }

@get:StringRes
private val SupportiveMedication.labelRes: Int
    get() = when (this) {
        SupportiveMedication.IRON -> R.string.med_iron
        SupportiveMedication.VITAMIN_D -> R.string.med_vitamin_d
        SupportiveMedication.VITAMIN_B12 -> R.string.med_vitamin_b12
        SupportiveMedication.FOLIC_ACID -> R.string.med_folic_acid
        SupportiveMedication.CALCIUM -> R.string.med_calcium
        SupportiveMedication.LOPERAMIDE -> R.string.med_loperamide
    }

/** INN as is, or translated name of an adjunct treatment. */
@Composable
fun StandardMedication.displayName(): String =
    inn ?: stringResource(requireNotNull(supportive).labelRes)

@get:StringRes
val Symptom.labelRes: Int
    get() = when (this) {
        Symptom.BLOOD -> R.string.analysis_blood
        Symptom.MUCUS -> R.string.analysis_mucus
        Symptom.STRONG_PAIN -> R.string.analysis_pain_strong
        Symptom.STRONG_FATIGUE -> R.string.analysis_fatigue_strong
        Symptom.URGENCY -> R.string.analysis_urgency
        Symptom.NOCTURNAL -> R.string.analysis_nocturnal
        Symptom.FALSE_URGE -> R.string.analysis_false_urge
    }

@get:StringRes
val LabTest.labelRes: Int
    get() = when (this) {
        LabTest.CRP -> R.string.lab_crp
        LabTest.CALPROTECTIN -> R.string.lab_calprotectin
        LabTest.RED_BLOOD_CELLS -> R.string.lab_red_blood_cells
        LabTest.HEMOGLOBIN -> R.string.lab_hemoglobin
        LabTest.HEMATOCRIT -> R.string.lab_hematocrit
        LabTest.MCV -> R.string.lab_mcv
        LabTest.MCH -> R.string.lab_mch
        LabTest.MCHC -> R.string.lab_mchc
        LabTest.WHITE_BLOOD_CELLS -> R.string.lab_white_blood_cells
        LabTest.NEUTROPHILS -> R.string.lab_neutrophils
        LabTest.LYMPHOCYTES -> R.string.lab_lymphocytes
        LabTest.MONOCYTES -> R.string.lab_monocytes
        LabTest.EOSINOPHILS -> R.string.lab_eosinophils
        LabTest.BASOPHILS -> R.string.lab_basophils
        LabTest.PLATELETS -> R.string.lab_platelets
        LabTest.FERRITIN -> R.string.lab_ferritin
        LabTest.IRON -> R.string.lab_iron
        LabTest.TRANSFERRIN -> R.string.lab_transferrin
        LabTest.IRON_BINDING_CAPACITY -> R.string.lab_iron_binding_capacity
        LabTest.TRANSFERRIN_SATURATION -> R.string.lab_transferrin_saturation
        LabTest.CREATININE -> R.string.lab_creatinine
        LabTest.EGFR -> R.string.lab_egfr
        LabTest.SODIUM -> R.string.lab_sodium
        LabTest.POTASSIUM -> R.string.lab_potassium
        LabTest.CHLORIDE -> R.string.lab_chloride
        LabTest.CALCIUM -> R.string.lab_calcium
        LabTest.URINE_PROTEIN -> R.string.lab_urine_protein
        LabTest.AST -> R.string.lab_ast
        LabTest.ALT -> R.string.lab_alt
        LabTest.GGT -> R.string.lab_ggt
        LabTest.ALKALINE_PHOSPHATASE -> R.string.lab_alkaline_phosphatase
        LabTest.BILIRUBIN_TOTAL -> R.string.lab_bilirubin_total
        LabTest.BILIRUBIN_DIRECT -> R.string.lab_bilirubin_direct
        LabTest.BILIRUBIN_INDIRECT -> R.string.lab_bilirubin_indirect
        LabTest.GLUCOSE -> R.string.lab_glucose
        LabTest.CHOLESTEROL_TOTAL -> R.string.lab_cholesterol_total
        LabTest.CHOLESTEROL_HDL -> R.string.lab_cholesterol_hdl
        LabTest.CHOLESTEROL_LDL -> R.string.lab_cholesterol_ldl
        LabTest.TRIGLYCERIDES -> R.string.lab_triglycerides
        LabTest.VITAMIN_D -> R.string.lab_vitamin_d
        LabTest.ALBUMIN -> R.string.lab_albumin
        LabTest.PROTHROMBIN_TIME -> R.string.lab_prothrombin_time
        LabTest.PSA -> R.string.lab_psa
    }

/** Label of the analysis family (group header). */
@get:StringRes
val LabCategory.labelRes: Int
    get() = when (this) {
        LabCategory.INFLAMMATION -> R.string.lab_category_inflammation
        LabCategory.BLOOD_COUNT -> R.string.lab_category_blood_count
        LabCategory.IRON -> R.string.lab_category_iron
        LabCategory.KIDNEY -> R.string.lab_category_kidney
        LabCategory.LIVER -> R.string.lab_category_liver
        LabCategory.METABOLIC -> R.string.lab_category_metabolic
        LabCategory.NUTRITION -> R.string.lab_category_nutrition
        LabCategory.OTHER -> R.string.lab_category_other
    }

/** Short label of the period selector ("30 days"). */
@get:StringRes
val AnalysisPeriod.chipRes: Int
    get() = when (this) {
        AnalysisPeriod.LAST_30 -> R.string.analysis_period_30_short
        AnalysisPeriod.LAST_90 -> R.string.analysis_period_90_short
        AnalysisPeriod.ALL -> R.string.analysis_period_all_short
    }

/** Full label of the period ("last 30 days"). */
@get:StringRes
val AnalysisPeriod.labelRes: Int
    get() = when (this) {
        AnalysisPeriod.LAST_30 -> R.string.analysis_period_30
        AnalysisPeriod.LAST_90 -> R.string.analysis_period_90
        AnalysisPeriod.ALL -> R.string.analysis_period_all
    }

@get:StringRes
val Sex.labelRes: Int
    get() = when (this) {
        Sex.FEMALE -> R.string.sex_female
        Sex.MALE -> R.string.sex_male
    }
