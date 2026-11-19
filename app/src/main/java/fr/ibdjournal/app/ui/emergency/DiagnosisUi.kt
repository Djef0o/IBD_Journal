package fr.ibdjournal.app.ui.emergency

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Diagnosis

/** Full name, for display. */
@get:StringRes
val Diagnosis.labelRes: Int
    get() = when (this) {
        Diagnosis.CROHN -> R.string.diagnosis_crohn
        Diagnosis.ULCERATIVE_COLITIS -> R.string.diagnosis_uc
        Diagnosis.OTHER -> R.string.diagnosis_other
    }

/** Short name, to fit in a segmented button. */
@get:StringRes
private val Diagnosis.shortLabelRes: Int
    get() = when (this) {
        Diagnosis.CROHN -> R.string.indication_crohn
        Diagnosis.ULCERATIVE_COLITIS -> R.string.indication_uc
        Diagnosis.OTHER -> R.string.diagnosis_other_short
    }

/** Crohn's / ulcerative colitis / other: shown on the emergency sheet and in the profile. */
@Composable
fun DiagnosisSelector(diagnosis: Diagnosis?, onChange: (Diagnosis) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.diagnosis_title), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Diagnosis.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = diagnosis == option,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, Diagnosis.entries.size),
                ) { Text(stringResource(option.shortLabelRes)) }
            }
        }
    }
}
