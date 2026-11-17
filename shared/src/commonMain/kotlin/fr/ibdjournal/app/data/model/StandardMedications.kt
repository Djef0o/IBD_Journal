package fr.ibdjournal.app.data.model

enum class MedicationCategory {
    AMINOSALICYLATES,
    CORTICOSTEROIDS,
    IMMUNOSUPPRESSANTS,
    BIOLOGICS,
    SMALL_MOLECULES,
    ANTIBIOTICS,
    SUPPORTIVE,
}

enum class Indication { CROHN, UC }

/** Supportive treatments whose name is translated (unlike INNs). */
enum class SupportiveMedication { IRON, VITAMIN_D, VITAMIN_B12, FOLIC_ACID, CALCIUM, LOPERAMIDE }

/**
 * Medication from the standard list: an INN ([inn], identical across languages) or a
 * supportive treatment ([supportive]) whose label is translated by the platform.
 */
class StandardMedication private constructor(
    val category: MedicationCategory,
    val indications: Set<Indication>,
    val inn: String?,
    val supportive: SupportiveMedication?,
) {
    companion object {
        fun inn(category: MedicationCategory, name: String, indications: Set<Indication>) =
            StandardMedication(category, indications, inn = name, supportive = null)

        fun supportive(medication: SupportiveMedication) =
            StandardMedication(MedicationCategory.SUPPORTIVE, emptySet(), inn = null, supportive = medication)
    }
}

/** Usual treatments for Crohn's disease and ulcerative colitis. */
object StandardMedications {

    private val BOTH = setOf(Indication.CROHN, Indication.UC)
    private val CROHN = setOf(Indication.CROHN)
    private val UC = setOf(Indication.UC)

    val all: List<StandardMedication> = buildList {
        fun inn(category: MedicationCategory, name: String, indications: Set<Indication>) =
            add(StandardMedication.inn(category, name, indications))

        with(MedicationCategory.AMINOSALICYLATES) {
            inn(this, "Mesalazine", BOTH)
            inn(this, "Sulfasalazine", BOTH)
            inn(this, "Olsalazine", UC)
            inn(this, "Balsalazide", UC)
        }
        with(MedicationCategory.CORTICOSTEROIDS) {
            inn(this, "Prednisone", BOTH)
            inn(this, "Prednisolone", BOTH)
            inn(this, "Methylprednisolone", BOTH)
            inn(this, "Budesonide", BOTH)
            inn(this, "Beclometasone", UC)
            inn(this, "Hydrocortisone", BOTH)
        }
        with(MedicationCategory.IMMUNOSUPPRESSANTS) {
            inn(this, "Azathioprine", BOTH)
            inn(this, "Mercaptopurine", BOTH)
            inn(this, "Methotrexate", CROHN)
            inn(this, "Ciclosporin", UC)
            inn(this, "Tacrolimus", UC)
        }
        with(MedicationCategory.BIOLOGICS) {
            inn(this, "Infliximab", BOTH)
            inn(this, "Adalimumab", BOTH)
            inn(this, "Golimumab", UC)
            inn(this, "Certolizumab pegol", CROHN)
            inn(this, "Vedolizumab", BOTH)
            inn(this, "Ustekinumab", BOTH)
            inn(this, "Risankizumab", BOTH)
            inn(this, "Mirikizumab", BOTH)
            inn(this, "Guselkumab", BOTH)
        }
        with(MedicationCategory.SMALL_MOLECULES) {
            inn(this, "Tofacitinib", UC)
            inn(this, "Upadacitinib", BOTH)
            inn(this, "Filgotinib", UC)
            inn(this, "Ozanimod", UC)
            inn(this, "Etrasimod", UC)
        }
        with(MedicationCategory.ANTIBIOTICS) {
            inn(this, "Metronidazole", CROHN)
            inn(this, "Ciprofloxacin", CROHN)
        }
        SupportiveMedication.entries.forEach { add(StandardMedication.supportive(it)) }
    }
}
