package fr.ibdjournal.app.data.model

import androidx.annotation.StringRes
import fr.ibdjournal.app.R

enum class MedicationCategory(@StringRes val labelRes: Int) {
    AMINOSALICYLATES(R.string.med_category_aminosalicylates),
    CORTICOSTEROIDS(R.string.med_category_corticosteroids),
    IMMUNOSUPPRESSANTS(R.string.med_category_immunosuppressants),
    BIOLOGICS(R.string.med_category_biologics),
    SMALL_MOLECULES(R.string.med_category_small_molecules),
    ANTIBIOTICS(R.string.med_category_antibiotics),
    SUPPORTIVE(R.string.med_category_supportive),
}

enum class Indication(@StringRes val labelRes: Int) {
    CROHN(R.string.indication_crohn),
    UC(R.string.indication_uc),
}

/**
 * Médicament de la liste standard. Les DCI sont identiques d'une langue à l'autre ;
 * seuls les traitements d'appoint (fer, vitamines…) passent par une ressource traduite.
 */
class StandardMedication(
    val category: MedicationCategory,
    val indications: Set<Indication>,
    val inn: String? = null,
    @StringRes val labelRes: Int = 0,
)

/** Traitements usuels de la maladie de Crohn et de la rectocolite hémorragique. */
object StandardMedications {

    private val BOTH = setOf(Indication.CROHN, Indication.UC)
    private val CROHN = setOf(Indication.CROHN)
    private val UC = setOf(Indication.UC)

    private fun inn(category: MedicationCategory, name: String, indications: Set<Indication>) =
        StandardMedication(category, indications, inn = name)

    private fun res(category: MedicationCategory, @StringRes labelRes: Int) =
        StandardMedication(category, emptySet(), labelRes = labelRes)

    val all: List<StandardMedication> = buildList {
        with(MedicationCategory.AMINOSALICYLATES) {
            add(inn(this, "Mesalazine", BOTH))
            add(inn(this, "Sulfasalazine", BOTH))
            add(inn(this, "Olsalazine", UC))
            add(inn(this, "Balsalazide", UC))
        }
        with(MedicationCategory.CORTICOSTEROIDS) {
            add(inn(this, "Prednisone", BOTH))
            add(inn(this, "Prednisolone", BOTH))
            add(inn(this, "Methylprednisolone", BOTH))
            add(inn(this, "Budesonide", BOTH))
            add(inn(this, "Beclometasone", UC))
            add(inn(this, "Hydrocortisone", BOTH))
        }
        with(MedicationCategory.IMMUNOSUPPRESSANTS) {
            add(inn(this, "Azathioprine", BOTH))
            add(inn(this, "Mercaptopurine", BOTH))
            add(inn(this, "Methotrexate", CROHN))
            add(inn(this, "Ciclosporin", UC))
            add(inn(this, "Tacrolimus", UC))
        }
        with(MedicationCategory.BIOLOGICS) {
            add(inn(this, "Infliximab", BOTH))
            add(inn(this, "Adalimumab", BOTH))
            add(inn(this, "Golimumab", UC))
            add(inn(this, "Certolizumab pegol", CROHN))
            add(inn(this, "Vedolizumab", BOTH))
            add(inn(this, "Ustekinumab", BOTH))
            add(inn(this, "Risankizumab", BOTH))
            add(inn(this, "Mirikizumab", BOTH))
            add(inn(this, "Guselkumab", BOTH))
        }
        with(MedicationCategory.SMALL_MOLECULES) {
            add(inn(this, "Tofacitinib", UC))
            add(inn(this, "Upadacitinib", BOTH))
            add(inn(this, "Filgotinib", UC))
            add(inn(this, "Ozanimod", UC))
            add(inn(this, "Etrasimod", UC))
        }
        with(MedicationCategory.ANTIBIOTICS) {
            add(inn(this, "Metronidazole", CROHN))
            add(inn(this, "Ciprofloxacin", CROHN))
        }
        with(MedicationCategory.SUPPORTIVE) {
            add(res(this, R.string.med_iron))
            add(res(this, R.string.med_vitamin_d))
            add(res(this, R.string.med_vitamin_b12))
            add(res(this, R.string.med_folic_acid))
            add(res(this, R.string.med_calcium))
            add(res(this, R.string.med_loperamide))
        }
    }
}
