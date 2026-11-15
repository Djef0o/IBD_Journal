package fr.ibdjournal.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.CheckupType
import fr.ibdjournal.app.data.model.Indication
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.MedicationCategory
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.data.model.StandardMedication
import fr.ibdjournal.app.data.model.SupportiveMedication
import fr.ibdjournal.app.domain.AnalysisPeriod
import fr.ibdjournal.app.domain.Symptom

/*
 * Libellés Android des énumérations du module partagé, qui ne connaît pas les ressources
 * de la plateforme.
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

/** DCI telle quelle, ou nom traduit d'un traitement d'appoint. */
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
    }

/** Libellé court du sélecteur de période (« 30 jours »). */
@get:StringRes
val AnalysisPeriod.chipRes: Int
    get() = when (this) {
        AnalysisPeriod.LAST_30 -> R.string.analysis_period_30_short
        AnalysisPeriod.LAST_90 -> R.string.analysis_period_90_short
        AnalysisPeriod.ALL -> R.string.analysis_period_all_short
    }

/** Libellé complet de la période (« 30 derniers jours »). */
@get:StringRes
val AnalysisPeriod.labelRes: Int
    get() = when (this) {
        AnalysisPeriod.LAST_30 -> R.string.analysis_period_30
        AnalysisPeriod.LAST_90 -> R.string.analysis_period_90
        AnalysisPeriod.ALL -> R.string.analysis_period_all
    }
