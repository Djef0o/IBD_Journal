package fr.ibdjournal.app.ui.profile

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.CheckupType
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.data.model.StandardMedication
import fr.ibdjournal.app.data.model.StandardMedications
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.settings.settingsRowColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

private const val MAX_REPEAT_EVERY = 99

// --- Identité ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit,
) {
    var firstName by remember { mutableStateOf(profile.firstName) }
    var lastName by remember { mutableStateOf(profile.lastName) }
    var birthDate by remember { mutableStateOf(profile.birthDate) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text(stringResource(R.string.profile_first_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text(stringResource(R.string.profile_last_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldRow(
                    label = stringResource(R.string.profile_birth_date),
                    value = birthDate?.let(DateFormats::dayMonthYear) ?: stringResource(R.string.profile_not_set),
                ) {
                    TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.modify)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(Profile(firstName.trim(), lastName.trim(), birthDate))
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (pickDate) {
        DatePickerDialogFor(
            initial = birthDate ?: LocalDate.now().minusYears(30),
            maxDate = LocalDate.now(),
            onDismiss = { pickDate = false },
            onPick = { birthDate = it },
        )
    }
}

// --- Traitement ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditorSheet(
    initial: Medication?,
    onDismiss: () -> Unit,
    onSave: (Medication) -> Unit,
    onDelete: (Medication) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var dosage by remember { mutableStateOf(initial?.dosage.orEmpty()) }
    var schedule by remember { mutableStateOf(initial?.schedule ?: defaultSchedule(RepeatUnit.DAY)) }
    var showPicker by remember { mutableStateOf(initial == null) }

    EditorSheet(
        title = stringResource(if (initial == null) R.string.treatment_add else R.string.treatment_edit),
        canSave = name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(Medication(id = initial?.id ?: 0, name = name.trim(), dosage = dosage.trim(), schedule = schedule))
            onDismiss()
        },
        onDelete = initial?.let { { onDelete(it); onDismiss() } },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.treatment_name)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { showPicker = true }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.List,
                        contentDescription = stringResource(R.string.treatment_choose),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = dosage,
            onValueChange = { dosage = it },
            label = { Text(stringResource(R.string.treatment_dosage)) },
            placeholder = { Text(stringResource(R.string.treatment_dosage_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ScheduleEditor(
            schedule = schedule,
            dateLabel = stringResource(R.string.treatment_start_date),
            onChange = { schedule = it },
        )
    }

    if (showPicker) {
        MedicationPickerDialog(
            initialQuery = name,
            onDismiss = { showPicker = false },
            onPick = { name = it; showPicker = false },
        )
    }
}

/** Liste standard filtrable ; « Autre » garde le texte saisi tel quel. */
@Composable
private fun MedicationPickerDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    val labels = StandardMedications.all.associateWith { it.displayName() }
    val matches = StandardMedications.all.filter { labels.getValue(it).contains(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.treatment_choose)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.treatment_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        ListItem(
                            headlineContent = {
                                Text(
                                    if (query.isBlank()) {
                                        stringResource(R.string.treatment_other)
                                    } else {
                                        stringResource(R.string.treatment_other_named, query.trim())
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            supportingContent = { Text(stringResource(R.string.treatment_other_desc)) },
                            modifier = Modifier.clickable {
                                if (query.isBlank()) onDismiss() else onPick(query.trim())
                            },
                            colors = settingsRowColors(),
                        )
                    }
                    matches.groupBy { it.category }.forEach { (category, meds) ->
                        item(key = category.name) {
                            HorizontalDivider()
                            Text(
                                text = stringResource(category.labelRes),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(meds, key = { labels.getValue(it) }) { med ->
                            val label = labels.getValue(med)
                            val indications = med.indications.map { stringResource(it.labelRes) }.joinToString(" · ")
                            ListItem(
                                headlineContent = { Text(label) },
                                supportingContent = if (indications.isEmpty()) null else {
                                    { Text(indications) }
                                },
                                modifier = Modifier.clickable { onPick(label) },
                                colors = settingsRowColors(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun StandardMedication.displayName(): String = inn ?: stringResource(labelRes)

// --- Contrôle (prise de sang, selles) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckupEditorSheet(
    initial: Checkup?,
    onDismiss: () -> Unit,
    onSave: (Checkup) -> Unit,
    onDelete: (Checkup) -> Unit,
) {
    var type by remember { mutableStateOf(initial?.type ?: CheckupType.BLOOD_TEST) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var schedule by remember { mutableStateOf(initial?.schedule ?: defaultSchedule(RepeatUnit.ONCE)) }

    EditorSheet(
        title = stringResource(if (initial == null) R.string.checkup_add else R.string.checkup_edit),
        canSave = true,
        onDismiss = onDismiss,
        onSave = {
            onSave(Checkup(id = initial?.id ?: 0, type = type, note = note.trim(), schedule = schedule))
            onDismiss()
        },
        onDelete = initial?.let { { onDelete(it); onDismiss() } },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckupType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.checkup_note)) },
            placeholder = { Text(stringResource(R.string.checkup_note_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ScheduleEditor(
            schedule = schedule,
            dateLabel = stringResource(R.string.checkup_date),
            onChange = { schedule = it },
        )
    }
}

// --- Éléments communs ---

private fun defaultSchedule(unit: RepeatUnit) = ReminderSchedule(startDate = LocalDate.now(), repeatUnit = unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheet(
    title: String,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            content()
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

/** Libellé, valeur et action, aligné sur les champs de saisie (sans le retrait d'un ListItem). */
@Composable
private fun FieldRow(label: String, value: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** Date, heure, périodicité et activation de la notification. */
@Composable
private fun ScheduleEditor(
    schedule: ReminderSchedule,
    dateLabel: String,
    onChange: (ReminderSchedule) -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldRow(label = dateLabel, value = DateFormats.dayMonthYear(schedule.startDate)) {
            TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.modify)) }
        }
        FieldRow(label = stringResource(R.string.hour), value = DateFormats.time(schedule.hour, schedule.minute)) {
            TextButton(onClick = { pickTime = true }) { Text(stringResource(R.string.modify)) }
        }
        Text(
            text = stringResource(R.string.repeat_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            RepeatUnit.entries.forEach { unit ->
                FilterChip(
                    selected = schedule.repeatUnit == unit,
                    onClick = { onChange(schedule.copy(repeatUnit = unit)) },
                    label = { Text(stringResource(unit.labelRes)) },
                )
            }
        }
        if (schedule.repeatUnit != RepeatUnit.ONCE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = repeatSummary(schedule),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onChange(schedule.copy(repeatEvery = schedule.repeatEvery - 1)) },
                    enabled = schedule.repeatEvery > 1,
                ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.decrease)) }
                Text(schedule.repeatEvery.toString(), style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { onChange(schedule.copy(repeatEvery = schedule.repeatEvery + 1)) },
                    enabled = schedule.repeatEvery < MAX_REPEAT_EVERY,
                ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.increase)) }
            }
        }
        FieldRow(
            label = stringResource(R.string.reminder_enabled),
            value = stringResource(R.string.reminder_enabled_desc),
        ) {
            Switch(checked = schedule.enabled, onCheckedChange = { onChange(schedule.copy(enabled = it)) })
        }
    }

    if (pickDate) {
        DatePickerDialogFor(
            initial = schedule.startDate,
            maxDate = null,
            onDismiss = { pickDate = false },
            onPick = { onChange(schedule.copy(startDate = it)) },
        )
    }
    if (pickTime) {
        TimePickerDialogFor(
            hour = schedule.hour,
            minute = schedule.minute,
            onDismiss = { pickTime = false },
            onPick = { h, m -> onChange(schedule.copy(hour = h, minute = m)) },
        )
    }
}

/** « Tous les jours », « Toutes les 2 semaines », « Une fois ». */
@Composable
fun repeatSummary(schedule: ReminderSchedule): String {
    val n = schedule.repeatEvery
    return when (schedule.repeatUnit) {
        RepeatUnit.ONCE -> stringResource(R.string.repeat_once)
        RepeatUnit.DAY -> pluralStringResource(R.plurals.repeat_every_days, n, n)
        RepeatUnit.WEEK -> pluralStringResource(R.plurals.repeat_every_weeks, n, n)
        RepeatUnit.MONTH -> pluralStringResource(R.plurals.repeat_every_months, n, n)
    }
}

/**
 * Ligne d'état : périodicité et prochaine échéance (l'icône de cloche indique si elle sera
 * notifiée). Une échéance unique passée affiche simplement sa date.
 */
@Composable
fun scheduleStatus(schedule: ReminderSchedule): String {
    val repeat = repeatSummary(schedule)
    val next = schedule.nextOccurrence(LocalDateTime.now())
    val date = if (next == null) {
        DateFormats.dayMonthYear(schedule.startDate)
    } else {
        stringResource(
            R.string.schedule_next,
            stringResource(
                R.string.date_at,
                DateFormats.weekdayDayMonth(next.toLocalDate()),
                DateFormats.time(next.hour, next.minute),
            ),
        )
    }
    return "$repeat · $date"
}

/** Le DatePicker travaille en millisecondes UTC à minuit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogFor(
    initial: LocalDate,
    maxDate: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val maxMillis = maxDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = maxMillis == null || utcTimeMillis <= maxMillis
            override fun isSelectableYear(year: Int) = maxDate == null || year <= maxDate.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogFor(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onPick: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour, state.minute); onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
            }
        },
    )
}
