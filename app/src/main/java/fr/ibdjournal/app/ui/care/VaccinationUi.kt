package fr.ibdjournal.app.ui.care

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Vaccination
import fr.ibdjournal.app.data.model.VaccineType
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.settings.settingsRowColors
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

@StringRes
internal fun VaccineType.nameRes(): Int = when (this) {
    VaccineType.INFLUENZA -> R.string.vaccine_type_influenza
    VaccineType.PNEUMOCOCCAL -> R.string.vaccine_type_pneumococcal
    VaccineType.COVID19 -> R.string.vaccine_type_covid19
    VaccineType.HEPATITIS_B -> R.string.vaccine_type_hepatitis_b
    VaccineType.HPV -> R.string.vaccine_type_hpv
    VaccineType.ZOSTER -> R.string.vaccine_type_zoster
    VaccineType.TETANUS -> R.string.vaccine_type_tetanus
    VaccineType.OTHER -> R.string.vaccine_type_other
}

/** Most recent first; on equal dates, the last entered. */
private val newestVaccinationFirst: Comparator<Vaccination> =
    compareByDescending<Vaccination> { it.date }.thenByDescending { it.id }

/**
 * Care section: one row per vaccine with its latest dose and next due date, expandable to
 * every dose, plus the editor sheet. [vaccinations] chronological.
 */
@Composable
internal fun VaccinationsSection(
    vaccinations: List<Vaccination>,
    onSave: (Vaccination) -> Unit,
    onDelete: (Vaccination) -> Unit,
) {
    var editing by remember { mutableStateOf<Editing<Vaccination>?>(null) }
    var expanded by remember { mutableStateOf(emptySet<VaccineType>()) }
    // Vaccines with the most recent dose first; doses of a vaccine most recent first.
    val groups = remember(vaccinations) {
        vaccinations.groupBy { it.type }.values
            .map { doses -> doses.sortedWith(newestVaccinationFirst) }
            .sortedByDescending { it.first().date }
    }

    SectionHeader(
        title = stringResource(R.string.vaccine_title),
        addLabel = stringResource(R.string.vaccine_add),
        onAdd = { editing = Editing(null) },
    )
    LiveVaccineNotice()
    if (vaccinations.isEmpty()) {
        EmptyLine(stringResource(R.string.vaccine_empty))
    }
    groups.forEach { doses ->
        val type = doses.first().type
        VaccineGroup(
            type = type,
            doses = doses,
            expanded = type in expanded,
            onToggle = { expanded = if (type in expanded) expanded - type else expanded + type },
            onEdit = { editing = Editing(it) },
        )
    }

    editing?.let { current ->
        VaccinationEditorSheet(
            initial = current.item,
            onDismiss = { editing = null },
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun LiveVaccineNotice() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.vaccine_live_notice),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Latest dose of a vaccine, its next due date (in red once overdue) and, expanded, all doses. [doses] newest first. */
@Composable
private fun VaccineGroup(
    type: VaccineType,
    doses: List<Vaccination>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: (Vaccination) -> Unit,
) {
    val latest = doses.first()
    val nextDue = latest.nextDue
    val overdue = nextDue != null && nextDue < today()

    ListItem(
        headlineContent = { Text(stringResource(type.nameRes())) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.vaccine_last, DateFormats.dayMonthYear(latest.date)))
                if (nextDue != null) {
                    Text(
                        text = stringResource(
                            if (overdue) R.string.vaccine_overdue else R.string.vaccine_next,
                            DateFormats.dayMonthYear(nextDue),
                        ),
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (latest.note.isNotBlank()) Text(latest.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = { Icon(Icons.Outlined.Vaccines, contentDescription = null) },
        trailingContent = if (doses.size < 2 && !latest.reminderEnabled) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (latest.reminderEnabled) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = stringResource(R.string.reminder_enabled),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (doses.size >= 2) {
                        Text(
                            text = pluralStringResource(R.plurals.vaccine_doses, doses.size, doses.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = onToggle) {
                            Icon(
                                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = stringResource(
                                    if (expanded) R.string.vaccine_hide_doses else R.string.vaccine_show_doses
                                ),
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier.clickable { onEdit(latest) },
        colors = settingsRowColors(),
    )
    if (expanded) {
        doses.forEach { dose ->
            ListItem(
                headlineContent = { Text(DateFormats.dayMonthYear(dose.date)) },
                supportingContent = if (dose.note.isBlank()) null else {
                    { Text(dose.note) }
                },
                modifier = Modifier
                    .padding(start = 40.dp)
                    .clickable { onEdit(dose) },
                colors = settingsRowColors(),
            )
        }
    }
}

/** [initial] null = new vaccination, given today by default. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaccinationEditorSheet(
    initial: Vaccination?,
    onDismiss: () -> Unit,
    onSave: (Vaccination) -> Unit,
    onDelete: (Vaccination) -> Unit,
) {
    val today = today()
    var type by remember { mutableStateOf(initial?.type ?: VaccineType.INFLUENZA) }
    var date by remember { mutableStateOf(initial?.date ?: today) }
    var nextDue by remember { mutableStateOf(initial?.nextDue) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var reminder by remember { mutableStateOf(initial?.reminderEnabled == true) }
    val nextBeforeDate = nextDue?.let { it <= date } == true

    LabSheet(onDismiss = onDismiss) {
        EditorTitle(stringResource(if (initial == null) R.string.vaccine_add else R.string.vaccine_edit))
        Text(stringResource(R.string.vaccine_type), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VaccineType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = { Text(stringResource(option.nameRes())) },
                )
            }
        }
        EditorDateRow(
            label = stringResource(R.string.vaccine_date),
            date = date,
            maxDate = today,
            onPick = { date = it },
        )
        EditorOptionalDateRow(
            label = stringResource(R.string.vaccine_next_due),
            date = nextDue,
            emptyText = stringResource(R.string.vaccine_next_none),
            suggestion = date.plus(1, DateTimeUnit.YEAR),
            maxDate = null,
            onChange = { nextDue = it },
        )
        if (nextBeforeDate) {
            Text(
                text = stringResource(R.string.vaccine_next_before_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.vaccine_note)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        if (nextDue != null) {
            SwitchRow(
                label = stringResource(R.string.vaccine_reminder_switch),
                checked = reminder,
                onChange = { reminder = it },
            )
            Text(
                text = stringResource(R.string.vaccine_reminder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EditorFooter(
            canDelete = initial != null,
            canSave = !nextBeforeDate,
            onDelete = { initial?.let(onDelete); onDismiss() },
            onSave = {
                onSave(
                    (initial ?: Vaccination(type = type, date = date)).copy(
                        type = type,
                        date = date,
                        nextDue = nextDue,
                        note = note.trim(),
                        reminderEnabled = reminder && nextDue != null,
                    ),
                )
                onDismiss()
            },
        )
    }
}
