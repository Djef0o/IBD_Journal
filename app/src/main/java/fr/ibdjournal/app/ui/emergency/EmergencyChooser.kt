package fr.ibdjournal.app.ui.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MedicalInformation
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R

/** Two big choices: the card to show to a stranger, or the medical information sheet. */
@Composable
fun EmergencyChooserDialog(onDismiss: () -> Unit, onUrgentCard: () -> Unit, onSheet: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.emergency_chooser_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onUrgentCard, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Wc, contentDescription = null)
                    Text(stringResource(R.string.emergency_open_card), modifier = Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(onClick = onSheet, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.MedicalInformation, contentDescription = null)
                    Text(stringResource(R.string.emergency_open_sheet), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
