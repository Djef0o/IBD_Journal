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
import fr.ibdjournal.app.domain.MIN_ANESTHESIA_LEAD
import fr.ibdjournal.app.domain.anesthesiaTooLate
import fr.ibdjournal.app.domain.dietStart
import fr.ibdjournal.app.domain.needsAnesthesia
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.domain.withDefaultPreparation
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.labelRes
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Régime sans résidu : au plus une semaine avant l'examen. */
private const val MAX_DIET_DAYS = 7

/** Nouvel examen : dans deux semaines, le matin. */
private const val NEW_EXAM_DAYS_AHEAD = 14
private val NEW_EXAM_TIME = LocalTime(9, 0)

/** Consultation d'anesthésie proposée : une semaine avant, en matinée. */
private const val ANESTHESIA_DAYS_BEFORE = 7
private val ANESTHESIA_DEFAULT_TIME = LocalTime(10, 0)

/** « Jeudi 4 décembre à 09:30 ». */
@Composable
internal fun dateTimeLabel(at: LocalDateTime): String = stringResource(
    R.string.date_at,
    DateFormats.weekdayDayMonth(at.date),
    DateFormats.time(at.hour, at.minute),
)

/** La préparation enregistrée est celle proposée par défaut (donc recalculable). */
private fun Exam.hasDefaultPreparation(): Boolean {
    val default = withDefaultPreparation()
    return dietDays == default.dietDays && prepFirstAt == default.prepFirstAt && prepSecondAt == default.prepSecondAt
}

/** Seconde prise par défaut, même pour une rectosigmoïdoscopie : celle d'une coloscopie. */
private fun Exam.defaultSecondDose(): LocalDateTime? =
    copy(type = ExamType.COLONOSCOPY).withDefaultPreparation().prepSecondAt

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
            ).withDefaultPreparation(),
        )
    }
    // Préparation retouchée à la main : on ne la recalcule plus quand l'examen bouge.
    var prepEdited by remember { mutableStateOf(initial?.hasDefaultPreparation() == false) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }

    // Date, heure ou type : la préparation par défaut suit, l'anesthésie reste.
    val changeExam = { updated: Exam -> exam = if (prepEdited) updated else updated.withDefaultPreparation() }
    val editPrep = { updated: Exam -> exam = updated; prepEdited = true }
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
                    onClick = { changeExam(exam.copy(type = type)) },
                    shape = SegmentedButtonDefaults.itemShape(index, ExamType.entries.size),
                ) { Text(stringResource(type.labelRes)) }
            }
        }
        DateTimeField(
            label = stringResource(R.string.exam_date),
            value = exam.at,
            onChange = { changeExam(exam.copy(at = it)) },
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
        PreparationEditor(exam = exam, onChange = editPrep)
        if (prepEdited) {
            TextButton(onClick = { exam = exam.withDefaultPreparation(); prepEdited = false }) {
                Text(stringResource(R.string.exam_reset_preparation))
            }
        }

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

/** Régime sans résidu et prises de la préparation à boire. */
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
            initial = exam.withDefaultPreparation().prepFirstAt ?: exam.at,
            onChange = { onChange(exam.copy(prepFirstAt = it)) },
        )
        FieldRow(
            label = stringResource(R.string.exam_prep_split),
            value = stringResource(R.string.exam_prep_split_desc),
        ) {
            Switch(
                checked = exam.prepSecondAt != null,
                onCheckedChange = { on -> onChange(exam.copy(prepSecondAt = if (on) exam.defaultSecondDose() else null)) },
            )
        }
        exam.prepSecondAt?.let { second ->
            DateTimeField(
                label = stringResource(R.string.exam_step_prep_second),
                value = second,
                onChange = { onChange(exam.copy(prepSecondAt = it)) },
            )
        }
    }
}

/** Consultation d'anesthésie (coloscopie) : à fixer au moins 48 h avant l'examen. */
@Composable
private fun AnesthesiaEditor(exam: Exam, onChange: (LocalDateTime?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OptionalDateTimeField(
            label = stringResource(R.string.exam_step_anesthesia),
            value = exam.anesthesiaAt,
            initial = exam.at.date.minus(ANESTHESIA_DAYS_BEFORE, DateTimeUnit.DAY).atTime(ANESTHESIA_DEFAULT_TIME),
            onChange = onChange,
        )
        if (exam.anesthesiaTooLate()) {
            Text(
                text = stringResource(R.string.exam_anesthesia_too_late),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Hint(stringResource(R.string.exam_anesthesia_hint, MIN_ANESTHESIA_LEAD.inWholeHours.toInt()))
        }
    }
}

/** Date et heure obligatoires. */
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

/** Date et heure facultatives : « Pas encore fixée » tant qu'elles ne sont pas choisies. */
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
                Text(stringResource(if (value == null) R.string.exam_set else R.string.modify))
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

/** Choix de la date puis de l'heure ; sans heure validée, la date garde l'heure d'avant. */
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
                // Annulé : on garde la date choisie ; validé : onPick a déjà tout appliqué.
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
