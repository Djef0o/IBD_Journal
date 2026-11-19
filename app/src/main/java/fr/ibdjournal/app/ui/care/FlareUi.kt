package fr.ibdjournal.app.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Flare
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.DateFormats
import kotlinx.datetime.daysUntil

/** Ongoing flares first, then the most recent. */
private val flareOrder: Comparator<Flare> =
    compareByDescending<Flare> { it.end == null }.thenByDescending { it.start }.thenByDescending { it.id }

/** Care section: flare periods, with the editor sheet. [flares] chronological. */
@Composable
internal fun FlaresSection(flares: List<Flare>, onSave: (Flare) -> Unit, onDelete: (Flare) -> Unit) {
    var editing by remember { mutableStateOf<Editing<Flare>?>(null) }
    val sorted = remember(flares) { flares.sortedWith(flareOrder) }

    SectionHeader(
        title = stringResource(R.string.flare_title),
        addLabel = stringResource(R.string.flare_add),
        onAdd = { editing = Editing(null) },
    )
    if (flares.isEmpty()) {
        EmptyLine(stringResource(R.string.flare_empty))
    }
    sorted.forEach { flare ->
        FlareRow(flare = flare, onClick = { editing = Editing(flare) })
    }

    editing?.let { current ->
        FlareEditorSheet(
            initial = current.item,
            onDismiss = { editing = null },
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

/** "12 March – 20 March 2026 (9 days)" or "Since 12 March 2026 (ongoing)"; an ongoing flare stands out. */
@Composable
private fun FlareRow(flare: Flare, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val end = flare.end
    val ongoing = end == null
    val content = if (ongoing) colors.onErrorContainer else Color.Unspecified
    val title = if (end == null) {
        stringResource(R.string.flare_ongoing_since, DateFormats.dayMonthYear(flare.start))
    } else {
        val days = flare.start.daysUntil(end) + 1
        pluralStringResource(
            R.plurals.flare_range_days,
            days,
            DateFormats.dayMonthYear(flare.start),
            DateFormats.dayMonthYear(end),
            days,
        )
    }
    val muted = if (ongoing) colors.onErrorContainer else colors.onSurfaceVariant

    ListItem(
        headlineContent = { Text(title, fontWeight = if (ongoing) FontWeight.SemiBold else null) },
        supportingContent = if (flare.treatment.isBlank() && flare.note.isBlank() && !flare.hospitalized) null else {
            {
                Column {
                    if (flare.treatment.isNotBlank()) Text(flare.treatment)
                    if (flare.note.isNotBlank()) Text(flare.note)
                    if (flare.hospitalized) {
                        Text(
                            text = stringResource(R.string.flare_hospitalized),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (ongoing) colors.onErrorContainer else colors.error,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .background(
                                    (if (ongoing) colors.onErrorContainer else colors.error).copy(alpha = 0.12f),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        },
        leadingContent = { Icon(Icons.Outlined.Whatshot, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = if (ongoing) {
            ListItemDefaults.colors(
                containerColor = colors.errorContainer,
                headlineColor = content,
                supportingColor = muted,
                leadingIconColor = content,
            )
        } else {
            ListItemDefaults.colors(containerColor = Color.Transparent)
        },
    )
}

/** [initial] null = new flare, ongoing since today by default. */
@Composable
private fun FlareEditorSheet(
    initial: Flare?,
    onDismiss: () -> Unit,
    onSave: (Flare) -> Unit,
    onDelete: (Flare) -> Unit,
) {
    val today = today()
    var start by remember { mutableStateOf(initial?.start ?: today) }
    var ongoing by remember { mutableStateOf(initial?.end == null) }
    var end by remember { mutableStateOf(initial?.end ?: today) }
    var treatment by remember { mutableStateOf(initial?.treatment.orEmpty()) }
    var hospitalized by remember { mutableStateOf(initial?.hospitalized ?: false) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    val endBeforeStart = !ongoing && end < start

    LabSheet(onDismiss = onDismiss) {
        EditorTitle(stringResource(if (initial == null) R.string.flare_add else R.string.flare_edit))
        EditorDateRow(
            label = stringResource(R.string.flare_start),
            date = start,
            maxDate = today,
            onPick = { start = it },
        )
        CheckRow(label = stringResource(R.string.flare_ongoing), checked = ongoing, onChange = { ongoing = it })
        if (!ongoing) {
            EditorDateRow(
                label = stringResource(R.string.flare_end),
                date = end,
                maxDate = today,
                onPick = { end = it },
            )
            if (endBeforeStart) {
                Text(
                    text = stringResource(R.string.flare_end_before_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        OutlinedTextField(
            value = treatment,
            onValueChange = { treatment = it },
            label = { Text(stringResource(R.string.flare_treatment)) },
            placeholder = { Text(stringResource(R.string.flare_treatment_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(
            label = stringResource(R.string.flare_hospitalized),
            checked = hospitalized,
            onChange = { hospitalized = it },
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.flare_note)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        EditorFooter(
            canDelete = initial != null,
            canSave = !endBeforeStart,
            onDelete = { initial?.let(onDelete); onDismiss() },
            onSave = {
                onSave(
                    (initial ?: Flare(start = start)).copy(
                        start = start,
                        end = if (ongoing) null else end,
                        treatment = treatment.trim(),
                        hospitalized = hospitalized,
                        note = note.trim(),
                    ),
                )
                onDismiss()
            },
        )
    }
}
