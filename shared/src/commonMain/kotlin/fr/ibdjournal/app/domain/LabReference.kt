package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.LabResult

/** Values printed by the laboratory; a missing bound is not checked (CRP: only a maximum). */
data class ReferenceRange(val low: Double?, val high: Double?)

/** Result outside the laboratory's values, as flagged by its report. */
enum class LabStatus { BELOW, ABOVE }

/** Laboratory values (imported or entered), or null if it gives none: the app provides none. */
fun LabResult.reference(): ReferenceRange? =
    if (referenceLow != null || referenceHigh != null) ReferenceRange(referenceLow, referenceHigh) else null

/**
 * Position relative to the laboratory's values; null if inside or without laboratory values.
 * A threshold ("< 1") only says on one side where the value is: "< 1" is never above,
 * "> 300" never below; it is only flagged if the threshold itself is outside the values.
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
