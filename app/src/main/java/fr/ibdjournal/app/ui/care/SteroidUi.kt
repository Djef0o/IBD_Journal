package fr.ibdjournal.app.ui.care

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.SteroidCourse
import fr.ibdjournal.app.data.model.SteroidStep
import fr.ibdjournal.app.domain.cumulativeMgThrough
import fr.ibdjournal.app.domain.doseOn
import fr.ibdjournal.app.domain.end
import fr.ibdjournal.app.domain.isActiveOn
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.domain.totalDays
import fr.ibdjournal.app.domain.totalMg
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.formatDecimal
import fr.ibdjournal.app.ui.settings.settingsRowColors
import kotlin.math.roundToLong

/** Upper bounds of the entry, far above any prescription; they keep the totals and dates sane. */
private const val MAX_DOSE_MG = 2000.0
private const val MAX_STEP_DAYS = 365

/** Drug names offered as chips in the editor. */
private val drugSuggestions = listOf(
    R.string.steroid_drug_prednisone,
    R.string.steroid_drug_prednisolone,
    R.string.steroid_drug_methylprednisolone,
    R.string.steroid_drug_budesonide,
)

/** "20" for a whole dose, "2,5" otherwise. */
private fun formatMg(mg: Double): String =
    if (mg == mg.roundToLong().toDouble()) mg.roundToLong().toString() else formatDecimal(mg)

/** Comma or dot, whatever the keyboard; null unless above 0 and within [MAX_DOSE_MG]. */
private fun parseDose(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= MAX_DOSE_MG }

private fun parseDays(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_STEP_DAYS }

/** Step being typed: raw field contents. */
private data class StepDraft(val dose: String = "", val days: String = "") {
    val valid: Boolean get() = parseDose(dose) != null && parseDays(days) != null

    /** Something is typed, but not a valid value. */
    val rejected: Boolean
        get() = (dose.isNotBlank() && parseDose(dose) == null) || (days.isNotBlank() && parseDays(days) == null)
}

/**
 * Care section: the course under way today as a card, then the other courses, plus the
 * editor sheet. [courses] chronological.
 */
@Composable
internal fun SteroidSection(
    courses: List<SteroidCourse>,
    onSave: (SteroidCourse) -> Unit,
    onDelete: (SteroidCourse) -> Unit,
) {
    var editing by remember { mutableStateOf<Editing<SteroidCourse>?>(null) }
    val today = today()
    val (active, others) = remember(courses, today) {
        courses.partition { it.isActiveOn(today) }
            .let { (now, rest) -> now.sortedByDescending { it.start } to rest.sortedByDescending { it.start } }
    }

    SectionHeader(
        title = stringResource(R.string.steroid_title),
        addLabel = stringResource(R.string.steroid_add),
        onAdd = { editing = Editing(null) },
    )
    if (courses.isEmpty()) {
        EmptyLine(stringResource(R.string.steroid_empty))
    }
    active.forEach { course ->
        ActiveSteroidCard(course = course, onClick = { editing = Editing(course) })
    }
    others.forEach { course ->
        SteroidRow(course = course, upcoming = course.start > today, onClick = { editing = Editing(course) })
    }

    editing?.let { current ->
        SteroidEditorSheet(
            initial = current.item,
            onDismiss = { editing = null },
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

/** Course under way: today's dose, cumulative dose, planned end and total. */
@Composable
private fun ActiveSteroidCard(course: SteroidCourse, onClick: () -> Unit) {
    val today = today()
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(course.drug, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                text = stringResource(R.string.steroid_today_dose, formatMg(course.doseOn(today) ?: 0.0)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = stringResource(R.string.steroid_cumulative, formatMg(course.cumulativeMgThrough(today))),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
            Text(
                text = stringResource(R.string.steroid_planned_end, DateFormats.dayMonthYear(course.end)),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.steroid_planned_total,
                    course.totalDays,
                    formatMg(course.totalMg),
                    course.totalDays,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
            if (course.note.isNotBlank()) {
                Text(course.note, style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
    }
}

/** Finished or not yet started course: period and total dose. */
@Composable
private fun SteroidRow(course: SteroidCourse, upcoming: Boolean, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        headlineContent = { Text(course.drug) },
        supportingContent = {
            Column {
                Text(
                    stringResource(
                        R.string.steroid_row_summary,
                        DateFormats.dayMonthYear(course.start),
                        DateFormats.dayMonthYear(course.end),
                        formatMg(course.totalMg),
                    ),
                    color = muted,
                )
                if (upcoming) Text(stringResource(R.string.steroid_not_started), color = MaterialTheme.colorScheme.primary)
                if (course.note.isNotBlank()) Text(course.note, color = muted)
            }
        },
        leadingContent = { Icon(Icons.Outlined.Medication, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = settingsRowColors(),
    )
}

/** [initial] null = new course, starting today by default. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SteroidEditorSheet(
    initial: SteroidCourse?,
    onDismiss: () -> Unit,
    onSave: (SteroidCourse) -> Unit,
    onDelete: (SteroidCourse) -> Unit,
) {
    var drug by remember { mutableStateOf(initial?.drug.orEmpty()) }
    var start by remember { mutableStateOf(initial?.start ?: today()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    val steps = remember {
        mutableStateListOf<StepDraft>().apply {
            val saved = initial?.steps.orEmpty()
            if (saved.isEmpty()) add(StepDraft())
            else saved.forEach { add(StepDraft(formatMg(it.doseMg), it.days.toString())) }
        }
    }

    val valid = steps.isNotEmpty() && steps.all { it.valid }
    val course = if (valid) {
        SteroidCourse(
            id = initial?.id ?: 0,
            drug = drug.trim(),
            start = start,
            steps = steps.map { SteroidStep(parseDose(it.dose)!!, parseDays(it.days)!!) },
            note = note.trim(),
        )
    } else {
        null
    }

    LabSheet(onDismiss = onDismiss) {
        EditorTitle(stringResource(if (initial == null) R.string.steroid_add else R.string.steroid_edit))
        OutlinedTextField(
            value = drug,
            onValueChange = { drug = it },
            label = { Text(stringResource(R.string.steroid_drug)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            drugSuggestions.forEach { res ->
                val name = stringResource(res)
                SuggestionChip(onClick = { drug = name }, label = { Text(name) })
            }
        }
        EditorDateRow(
            label = stringResource(R.string.steroid_start),
            date = start,
            maxDate = null,
            onPick = { start = it },
        )

        Text(stringResource(R.string.steroid_steps), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.steroid_steps_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = step.dose,
                    onValueChange = { steps[index] = step.copy(dose = it) },
                    label = { Text(stringResource(R.string.steroid_dose)) },
                    suffix = { Text(stringResource(R.string.steroid_mg)) },
                    isError = step.dose.isNotBlank() && parseDose(step.dose) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = step.days,
                    onValueChange = { text -> steps[index] = step.copy(days = text.filter(Char::isDigit)) },
                    label = { Text(stringResource(R.string.steroid_days)) },
                    isError = step.days.isNotBlank() && parseDays(step.days) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { steps.removeAt(index) },
                    enabled = steps.size > 1,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.steroid_remove_step))
                }
            }
        }
        if (steps.any { it.rejected }) {
            Text(
                text = stringResource(R.string.steroid_steps_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FilledTonalButton(onClick = { steps.add(StepDraft()) }, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.steroid_add_step))
        }

        if (course != null) {
            Text(
                text = pluralStringResource(
                    R.plurals.steroid_summary,
                    course.totalDays,
                    formatMg(course.totalMg),
                    course.totalDays,
                    DateFormats.dayMonthYear(course.end),
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.steroid_note)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        EditorFooter(
            canDelete = initial != null,
            canSave = course != null && course.drug.isNotEmpty(),
            onDelete = { initial?.let(onDelete); onDismiss() },
            onSave = {
                if (course != null) onSave(course)
                onDismiss()
            },
        )
    }
}
