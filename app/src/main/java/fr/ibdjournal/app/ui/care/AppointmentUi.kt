package fr.ibdjournal.app.ui.care

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Appointment
import fr.ibdjournal.app.data.model.AppointmentKind
import fr.ibdjournal.app.domain.now
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.settings.settingsRowColors
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/** New appointment: in a week, in the morning. */
private const val NEW_APPOINTMENT_DAYS_AHEAD = 7
private val NEW_APPOINTMENT_TIME = LocalTime(9, 0)

/**
 * Care section: upcoming appointments (soonest first), then a collapsed group of past ones
 * (most recent first), plus the editor sheet.
 */
@Composable
internal fun AppointmentsSection(
    appointments: List<Appointment>,
    onSave: (Appointment) -> Unit,
    onDelete: (Appointment) -> Unit,
) {
    var editing by remember { mutableStateOf<Editing<Appointment>?>(null) }
    var showPast by remember { mutableStateOf(false) }
    val current = now()
    val upcoming = appointments.filter { it.at >= current }.sortedBy { it.at }
    val past = appointments.filter { it.at < current }.sortedByDescending { it.at }

    SectionHeader(
        title = stringResource(R.string.appointmentui_title),
        addLabel = stringResource(R.string.appointmentui_add),
        onAdd = { editing = Editing(null) },
    )
    if (appointments.isEmpty()) {
        EmptyLine(stringResource(R.string.appointmentui_empty))
    }
    upcoming.forEach { appointment ->
        AppointmentRow(appointment = appointment, upcoming = true, onClick = { editing = Editing(appointment) })
    }
    if (past.isNotEmpty()) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.appointmentui_past, past.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Icon(
                    if (showPast) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(
                        if (showPast) R.string.appointmentui_hide_past else R.string.appointmentui_show_past
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.clickable { showPast = !showPast },
            colors = settingsRowColors(),
        )
        if (showPast) {
            past.forEach { appointment ->
                AppointmentRow(appointment = appointment, upcoming = false, onClick = { editing = Editing(appointment) })
            }
        }
    }

    editing?.let { currentEdit ->
        AppointmentEditorSheet(
            initial = currentEdit.item,
            onDismiss = { editing = null },
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

/** Kind, date and time, place and note; a bell when reminders are on. A past appointment is muted. */
@Composable
private fun AppointmentRow(appointment: Appointment, upcoming: Boolean, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // The year only matters for appointments already behind us.
    val whenLine = if (upcoming) {
        dateTimeLabel(appointment.at)
    } else {
        stringResource(
            R.string.date_at,
            DateFormats.dayMonthYear(appointment.at.date),
            DateFormats.time(appointment.at.hour, appointment.at.minute),
        )
    }
    val lines = listOfNotNull(appointment.place.ifBlank { null }, appointment.note.ifBlank { null })
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(appointment.kind.nameRes()),
                color = if (upcoming) Color.Unspecified else muted,
            )
        },
        supportingContent = {
            Column {
                Text(whenLine, color = if (upcoming) LocalContentColor.current else muted)
                if (lines.isNotEmpty()) Text(lines.joinToString("\n"), color = muted)
            }
        },
        leadingContent = {
            Icon(
                Icons.Outlined.Event,
                contentDescription = null,
                tint = if (upcoming) LocalContentColor.current else muted,
            )
        },
        trailingContent = if (!upcoming || !appointment.remindersEnabled) null else {
            {
                Icon(
                    Icons.Outlined.NotificationsActive,
                    contentDescription = stringResource(R.string.reminder_enabled),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = settingsRowColors(),
    )
}

/** [initial] null = new appointment, a week ahead by default. Past dates are allowed to log a visit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppointmentEditorSheet(
    initial: Appointment?,
    onDismiss: () -> Unit,
    onSave: (Appointment) -> Unit,
    onDelete: (Appointment) -> Unit,
) {
    var kind by remember { mutableStateOf(initial?.kind ?: AppointmentKind.GASTROENTEROLOGIST) }
    var at by remember {
        mutableStateOf(
            initial?.at ?: today().plus(NEW_APPOINTMENT_DAYS_AHEAD, DateTimeUnit.DAY).atTime(NEW_APPOINTMENT_TIME),
        )
    }
    var place by remember { mutableStateOf(initial?.place.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var reminders by remember { mutableStateOf(initial?.remindersEnabled ?: true) }
    // Until the switch is touched (or for an existing appointment), reminders follow the date: off once past.
    var remindersTouched by remember { mutableStateOf(initial != null) }
    var pickTime by remember { mutableStateOf(false) }

    fun changeAt(value: LocalDateTime) {
        at = value
        if (!remindersTouched) reminders = value >= now()
    }

    LabSheet(onDismiss = onDismiss) {
        EditorTitle(stringResource(if (initial == null) R.string.appointmentui_add else R.string.appointmentui_edit))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppointmentKind.entries.forEach { option ->
                FilterChip(
                    selected = kind == option,
                    onClick = { kind = option },
                    label = { Text(stringResource(option.nameRes())) },
                )
            }
        }
        EditorDateRow(
            label = stringResource(R.string.appointmentui_date),
            date = at.date,
            maxDate = null,
            onPick = { changeAt(it.atTime(at.time)) },
        )
        FieldRow(
            label = stringResource(R.string.appointmentui_time),
            value = DateFormats.time(at.hour, at.minute),
        ) {
            TextButton(onClick = { pickTime = true }) { Text(stringResource(R.string.modify)) }
        }
        if (pickTime) {
            TimePickerDialogFor(
                hour = at.hour,
                minute = at.minute,
                onDismiss = { pickTime = false },
                onPick = { hour, minute -> changeAt(at.date.atTime(hour, minute)) },
            )
        }
        OutlinedTextField(
            value = place,
            onValueChange = { place = it },
            label = { Text(stringResource(R.string.appointmentui_place)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.appointmentui_note)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(
            label = stringResource(R.string.appointmentui_reminders),
            checked = reminders,
            onChange = { reminders = it; remindersTouched = true },
        )
        Text(
            text = stringResource(R.string.appointmentui_reminders_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EditorFooter(
            canDelete = initial != null,
            canSave = true,
            onDelete = { initial?.let(onDelete); onDismiss() },
            onSave = {
                onSave(
                    (initial ?: Appointment(kind = kind, at = at)).copy(
                        kind = kind,
                        at = at,
                        place = place.trim(),
                        note = note.trim(),
                        remindersEnabled = reminders,
                    ),
                )
                onDismiss()
            },
        )
    }
}
