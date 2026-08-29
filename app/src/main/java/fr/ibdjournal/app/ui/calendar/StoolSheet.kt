package fr.ibdjournal.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R

/** Feuille d'ajout d'une selle du jour, avec commentaire facultatif. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoolSheet(
    onDismiss: () -> Unit,
    onSave: (comment: String) -> Unit,
) {
    var comment by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.add_stool),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.stool_comment_hint)) },
                minLines = 2,
                maxLines = 5,
            )
            Button(
                onClick = { onSave(comment) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
