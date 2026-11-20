package fr.ibdjournal.app.ui.care

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.DoctorQuestion
import fr.ibdjournal.app.ui.settings.settingsRowColors

/**
 * Care section: notes to raise at the next appointment. A ticked question is muted and struck
 * through; [questions] come open first. Tapping the text opens the editor sheet.
 */
@Composable
internal fun QuestionsSection(
    questions: List<DoctorQuestion>,
    onSave: (DoctorQuestion) -> Unit,
    onDelete: (DoctorQuestion) -> Unit,
) {
    var editing by remember { mutableStateOf<Editing<DoctorQuestion>?>(null) }

    SectionHeader(
        title = stringResource(R.string.question_title),
        addLabel = stringResource(R.string.question_add),
        onAdd = { editing = Editing(null) },
    )
    if (questions.isEmpty()) {
        EmptyLine(stringResource(R.string.question_empty))
    }
    questions.forEach { question ->
        QuestionRow(
            question = question,
            onToggle = { onSave(question.copy(done = it)) },
            onEdit = { editing = Editing(question) },
        )
    }
    if (questions.any { it.done }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { questions.filter { it.done }.forEach(onDelete) }) {
                Text(stringResource(R.string.question_clear_done))
            }
        }
    }

    editing?.let { current ->
        QuestionEditorSheet(
            initial = current.item,
            onDismiss = { editing = null },
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun QuestionRow(question: DoctorQuestion, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = question.text,
                color = if (question.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (question.done) TextDecoration.LineThrough else null,
            )
        },
        leadingContent = { Checkbox(checked = question.done, onCheckedChange = onToggle) },
        modifier = Modifier.clickable(onClick = onEdit),
        colors = settingsRowColors(),
    )
}

/** [initial] null = new question. */
@Composable
private fun QuestionEditorSheet(
    initial: DoctorQuestion?,
    onDismiss: () -> Unit,
    onSave: (DoctorQuestion) -> Unit,
    onDelete: (DoctorQuestion) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }

    LabSheet(onDismiss = onDismiss) {
        EditorTitle(stringResource(if (initial == null) R.string.question_add else R.string.question_edit))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.question_field)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        EditorFooter(
            canDelete = initial != null,
            canSave = text.isNotBlank(),
            onDelete = { initial?.let(onDelete); onDismiss() },
            onSave = {
                onSave((initial ?: DoctorQuestion(text = "")).copy(text = text.trim()))
                onDismiss()
            },
        )
    }
}
