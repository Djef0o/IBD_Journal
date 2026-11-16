package fr.ibdjournal.app.ui

import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.domain.ReferenceRange
import java.text.NumberFormat
import java.util.Locale

/** Jusqu'à deux décimales, sans zéros inutiles : « 12 », « 0,5 », « 13,25 ». */
fun formatLabValue(value: Double, grouping: Boolean = true): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 2
        isGroupingUsed = grouping
    }.format(value)

/** « < 1 mg/L », « 12,5 mg/L » ; sans unité pour les cellules de tableau. */
fun LabResult.displayValue(withUnit: Boolean = true): String {
    val prefix = when (qualifier) {
        LabQualifier.EXACT -> ""
        LabQualifier.BELOW -> "< "
        LabQualifier.ABOVE -> "> "
    }
    val number = prefix + formatLabValue(value)
    return if (withUnit) "$number ${test.unit}" else number
}

/** « 13,4 – 16,7 », « < 5 », « > 30 ». */
fun ReferenceRange.display(): String {
    val low = low
    val high = high
    return when {
        low != null && high != null -> "${formatLabValue(low)} – ${formatLabValue(high)}"
        high != null -> "< ${formatLabValue(high)}"
        low != null -> "> ${formatLabValue(low)}"
        else -> ""
    }
}
