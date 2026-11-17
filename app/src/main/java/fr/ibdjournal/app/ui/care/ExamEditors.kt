package fr.ibdjournal.app.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.data.model.ExamType
import fr.ibdjournal.app.domain.APPOINTMENT_REMINDER_LEAD
import fr.ibdjournal.app.domain.DIET_REMINDER_TIME
import fr.ibdjournal.app.domain.dietStart
import fr.ibdjournal.app.domain.needsAnesthesia
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.labelRes
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/** Bound of the diet-days picker. */
private const val MAX_DIET_DAYS = 7

/** New exam: in two weeks, in the morning. */
private const val NEW_EXAM_DAYS_AHEAD = 14
private val NEW_EXAM_TIME = LocalTime(9, 0)

/** "Thursday, December 4 at 09:30". */
@Composable
internal fun dateTimeLabel(at: LocalDateTime): String = stringResource(
    R.string.date_at,
    DateFormats.weekdayDayMonth(at.date),
    DateFormats.time(at.hour, at.minute),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamEditorSheet(
    initial: Exam?,
    onDismiss: () -> Unit,
    onSave: (Exam) -> Unit,
    onDelete: (Exam) -> Unit,
) {
    var exam by remember {
        mutableStateOf(
            initial ?: Exam(
                type = ExamType.COLONOSCOPY,
                at = today().plus(NEW_EXAM_DAYS_AHEAD, DateTimeUnit.DAY).atTime(NEW_EXAM_TIME),
            ),
        )
    }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }

    val draft = exam.copy(
        id = initial?.id ?: 0,
        note = note.trim(),
        anesthesiaAt = exam.anesthesiaAt.takeIf { exam.type.needsAnesthesia },
    )

    EditorSheet(
        title = stringResource(if (initial == null) R.string.exam_add else R.string.exam_edit),
        canSave = true,
        onDismiss = onDismiss,
        onSave = {
            onSave(draft)
            onDismiss()
        },
        onDelete = initial?.let { { onDelete(it); onDismiss() } },
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ExamType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = exam.type == type,
                    onClick = { exam = exam.copy(type = type) },
                    shape = SegmentedButtonDefaults.itemShape(index, ExamType.entries.size),
                ) { Text(stringResource(type.labelRes)) }
            }
        }
        DateTimeField(
            label = stringResource(R.string.exam_date),
            value = exam.at,
            onChange = { exam = exam.copy(at = it) },
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.exam_note)) },
            placeholder = { Text(stringResource(R.string.exam_note_hint)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        if (exam.type.needsAnesthesia) {
            HorizontalDivider()
            AnesthesiaEditor(exam = exam, onChange = { exam = exam.copy(anesthesiaAt = it) })
        }

        HorizontalDivider()
        PreparationEditor(exam = exam, onChange = { exam = it })

        HorizontalDivider()
        FieldRow(
            label = stringResource(R.string.exam_reminders),
            value = stringResource(
                R.string.exam_reminders_desc,
                APPOINTMENT_REMINDER_LEAD.inWholeHours.toInt(),
                DateFormats.time(DIET_REMINDER_TIME.hour, DIET_REMINDER_TIME.minute),
            ),
        ) {
            Switch(checked = exam.remindersEnabled, onCheckedChange = { exam = exam.copy(remindersEnabled = it) })
        }
    }
}

/** Low-residue diet and preparation doses, as prescribed by the doctor. */
@Composable
private fun PreparationEditor(exam: Exam, onChange: (Exam) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.exam_preparation),
            style = MaterialTheme.typography.titleMedium,
        )
        Hint(stringResource(R.string.exam_preparation_desc))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (exam.dietDays == 0) {
                        stringResource(R.string.exam_diet_none)
                    } else {
                        pluralStringResource(R.plurals.exam_diet_days, exam.dietDays, exam.dietDays)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                exam.dietStart()?.let { start ->
                    Hint(stringResource(R.string.exam_diet_start, DateFormats.weekdayDayMonth(start)))
                }
            }
            IconButton(
                onClick = { onChange(exam.copy(dietDays = exam.dietDays - 1)) },
                enabled = exam.dietDays > 0,
            ) { Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.decrease)) }
            Text(exam.dietDays.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(
                onClick = { onChange(exam.copy(dietDays = exam.dietDays + 1)) },
                enabled = exam.dietDays < MAX_DIET_DAYS,
            ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.increase)) }
        }
        OptionalDateTimeField(
            label = stringResource(R.string.exam_step_prep_first),
            value = exam.prepFirstAt,
            initial = exam.at,
            onChange = { onChange(exam.copy(prepFirstAt = it)) },
        )
        // Optional second dose, once the first is noted.
        if (exam.prepFirstAt != null || exam.prepSecondAt != null) {
            OptionalDateTimeField(
                label = stringResource(R.string.exam_step_prep_second),
                value = exam.prepSecondAt,
                initial = exam.prepFirstAt ?: exam.at,
                onChange = { onChange(exam.copy(prepSecondAt = it)) },
            )
        }
    }
}

/** Anesthesia consultation (colonoscopy). */
@Composable
private fun AnesthesiaEditor(exam: Exam, onChange: (LocalDateTime?) -> Unit) {
    OptionalDateTimeField(
        label = stringResource(R.string.exam_step_anesthesia),
        value = exam.anesthesiaAt,
        initial = exam.at,
        onChange = onChange,
    )
}

/** Required date and time. */
@Composable
private fun DateTimeField(label: String, value: LocalDateTime, onChange: (LocalDateTime) -> Unit) {
    var pick by remember { mutableStateOf(false) }
    FieldRow(label = label, value = dateTimeLabel(value)) {
        TextButton(onClick = { pick = true }) { Text(stringResource(R.string.modify)) }
    }
    if (pick) {
        DateTimePicker(initial = value, onDismiss = { pick = false }, onPick = onChange)
    }
}

/** Optional date and time: "Not set yet" until they are added. */
@Composable
private fun OptionalDateTimeField(
    label: String,
    value: LocalDateTime?,
    initial: LocalDateTime,
    onChange: (LocalDateTime?) -> Unit,
) {
    var pick by remember { mutableStateOf(false) }
    FieldRow(
        label = label,
        value = value?.let { dateTimeLabel(it) } ?: stringResource(R.string.exam_not_set),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pick = true }) {
                Text(stringResource(if (value == null) R.string.add else R.string.modify))
            }
            if (value != null) {
                IconButton(onClick = { onChange(null) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.exam_clear))
                }
            }
        }
    }
    if (pick) {
        DateTimePicker(initial = value ?: initial, onDismiss = { pick = false }, onPick = onChange)
    }
}

/** Pick the date then the time; without a confirmed time, the date keeps the previous time. */
@Composable
private fun DateTimePicker(initial: LocalDateTime, onDismiss: () -> Unit, onPick: (LocalDateTime) -> Unit) {
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val picked = date
    if (picked == null) {
        DatePickerDialogFor(
            initial = initial.date,
            maxDate = null,
            onDismiss = { if (date == null) onDismiss() },
            onPick = { date = it },
        )
    } else {
        TimePickerDialogFor(
            hour = initial.hour,
            minute = initial.minute,
            onDismiss = {
                // Cancelled: keep the chosen date; confirmed: onPick has already applied everything.
                date?.let { onPick(it.atTime(initial.time)) }
                onDismiss()
            },
            onPick = { h, m ->
                onPick(picked.atTime(h, m))
                date = null
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
