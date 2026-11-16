package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.LabResult

/** Valeurs imprimées par le laboratoire ; une borne absente n'est pas vérifiée (CRP : seulement un maximum). */
data class ReferenceRange(val low: Double?, val high: Double?)

/** Résultat hors des valeurs du laboratoire, comme le signale son compte rendu. */
enum class LabStatus { BELOW, ABOVE }

/** Valeurs du laboratoire (importées ou saisies), ou null s'il n'en donne pas : l'app n'en fournit aucune. */
fun LabResult.reference(): ReferenceRange? =
    if (referenceLow != null || referenceHigh != null) ReferenceRange(referenceLow, referenceHigh) else null

/**
 * Position par rapport aux valeurs du laboratoire ; null dedans ou sans valeurs du laboratoire.
 * Un seuil (« < 1 ») ne dit que d'un côté où est la valeur : « < 1 » n'est jamais au-dessus,
 * « > 300 » jamais en dessous ; on ne le signale que si le seuil lui-même est hors des valeurs.
 */
fun LabResult.status(): LabStatus? {
    val range = reference() ?: return null
    val low = range.low
    val high = range.high
    return when {
        qualifier != LabQualifier.ABOVE && low != null && value < low -> LabStatus.BELOW
        qualifier == LabQualifier.BELOW && low != null && value <= low -> LabStatus.BELOW
        qualifier != LabQualifier.BELOW && high != null && value > high -> LabStatus.ABOVE
        qualifier == LabQualifier.ABOVE && high != null && value >= high -> LabStatus.ABOVE
        else -> null
    }
}
