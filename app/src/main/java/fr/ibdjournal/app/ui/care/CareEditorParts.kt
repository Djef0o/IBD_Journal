package fr.ibdjournal.app.ui.care

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.ibdjournal.app.R
import fr.ibdjournal.app.ui.DateFormats
import kotlinx.datetime.LocalDate

// Pieces shared by the editor sheets of the flares, scores, vaccinations and steroid courses.

@Composable
internal fun EditorTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

/** Date with an "Edit" button opening the picker. */
@Composable
internal fun EditorDateRow(
    label: String,
    date: LocalDate,
    maxDate: LocalDate?,
    onPick: (LocalDate) -> Unit,
) {
    var pick by remember { mutableStateOf(false) }
    FieldRow(label = label, value = DateFormats.dayMonthYear(date)) {
        TextButton(onClick = { pick = true }) { Text(stringResource(R.string.modify)) }
    }
    if (pick) {
        DatePickerDialogFor(initial = date, maxDate = maxDate, onDismiss = { pick = false }, onPick = onPick)
    }
}

/**
 * Optional date: [emptyText] while null, "Add" then "Edit" button, and a clear button.
 * [suggestion] is the date the picker opens on when there is none yet.
 */
@Composable
internal fun EditorOptionalDateRow(
    label: String,
    date: LocalDate?,
    emptyText: String,
    suggestion: LocalDate,
    maxDate: LocalDate?,
    onChange: (LocalDate?) -> Unit,
) {
    var pick by remember { mutableStateOf(false) }
    FieldRow(label = label, value = date?.let { DateFormats.dayMonthYear(it) } ?: emptyText) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pick = true }) {
                Text(stringResource(if (date == null) R.string.add else R.string.modify))
            }
            if (date != null) {
                IconButton(onClick = { onChange(null) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.exam_clear))
                }
            }
        }
    }
    if (pick) {
        DatePickerDialogFor(
            initial = date ?: suggestion,
            maxDate = maxDate,
            onDismiss = { pick = false },
            onPick = onChange,
        )
    }
}

/** Delete (existing entries only) on the left, save on the right. */
@Composable
internal fun EditorFooter(canDelete: Boolean, canSave: Boolean, onDelete: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (canDelete) {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.save)) }
    }
}

/** Labelled checkbox, the whole row toggles. */
@Composable
internal fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 10.dp))
    }
}

/** Label with a switch on the right, the whole row toggles. */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
