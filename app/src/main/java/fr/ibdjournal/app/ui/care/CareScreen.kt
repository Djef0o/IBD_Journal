package fr.ibdjournal.app.ui.care

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.DoseLog
import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.data.model.ExamType
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.CheckupType
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.data.model.Weight
import fr.ibdjournal.app.ui.settings.settingsRowColors
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import fr.ibdjournal.app.domain.adherence
import fr.ibdjournal.app.domain.ExamStep
import fr.ibdjournal.app.domain.anesthesiaMissing
import fr.ibdjournal.app.domain.steps
import fr.ibdjournal.app.domain.now
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.labelRes
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.domain.status
import kotlin.math.roundToInt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.atTime

/** Adherence window shown under each treatment, today included. */
private const val ADHERENCE_DAYS = 30

@Composable
fun CareScreen(onOpenLabs: () -> Unit, viewModel: CareViewModel = hiltViewModel()) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val checkups by viewModel.checkups.collectAsStateWithLifecycle()
    val labResults by viewModel.labResults.collectAsStateWithLifecycle()
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    val doseLogs by viewModel.doseLogs.collectAsStateWithLifecycle()
    val weights by viewModel.weights.collectAsStateWithLifecycle()

    CareContent(
        medications = medications,
        checkups = checkups,
        labResults = labResults,
        doseLogs = doseLogs,
        onSaveMedication = viewModel::saveMedication,
        onDeleteMedication = viewModel::deleteMedication,
        onSaveCheckup = viewModel::saveCheckup,
        onDeleteCheckup = viewModel::deleteCheckup,
        exams = exams,
        onSaveExam = viewModel::saveExam,
        onDeleteExam = viewModel::deleteExam,
        onOpenLabs = onOpenLabs,
        weights = weights,
        onSaveWeight = viewModel::saveWeight,
        onDeleteWeight = viewModel::deleteWeight,
    )
}

/** Item being edited: null = closed; `item == null` = creation. */
private data class Editing<T>(val item: T?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareContent(
    medications: List<Medication>,
    checkups: List<Checkup>,
    labResults: List<LabResult>,
    doseLogs: List<DoseLog>,
    onSaveMedication: (Medication) -> Unit,
    onDeleteMedication: (Medication) -> Unit,
    onSaveCheckup: (Checkup) -> Unit,
    onDeleteCheckup: (Checkup) -> Unit,
    onOpenLabs: () -> Unit = {},
    exams: List<Exam> = emptyList(),
    onSaveExam: (Exam) -> Unit = {},
    onDeleteExam: (Exam) -> Unit = {},
    weights: List<Weight> = emptyList(),
    onSaveWeight: (Weight) -> Unit = {},
    onDeleteWeight: (Weight) -> Unit = {},
) {
    val context = LocalContext.current
    var editMedication by remember { mutableStateOf<Editing<Medication>?>(null) }
    var editCheckup by remember { mutableStateOf<Editing<Checkup>?>(null) }
    var editExam by remember { mutableStateOf<Editing<Exam>?>(null) }
    var editWeight by remember { mutableStateOf<Editing<Weight>?>(null) }
    var weightHistory by remember { mutableStateOf(false) }

    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }
    // Asks for permission when the user enables a reminder.
    val requestPermissionIfNeeded = { notifies: Boolean ->
        if (notifies && !notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val current = now()
    // Upcoming: nearest first; past ones after, from most recent to oldest.
    val upcomingExams = exams.filter { it.at >= current }.sortedBy { it.at }
    val pastExams = exams.filter { it.at < current }.sortedByDescending { it.at }
    val anyReminder = medications.any { it.schedule.enabled || it.refillAlert } ||
        checkups.any { it.schedule.enabled } ||
        upcomingExams.any { it.remindersEnabled }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.care_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (anyReminder && !notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(onGrant = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }

            SectionHeader(
                title = stringResource(R.string.treatments_title),
                addLabel = stringResource(R.string.treatment_add),
                onAdd = { editMedication = Editing(null) },
            )
            if (medications.isEmpty()) {
                EmptyLine(stringResource(R.string.treatments_empty))
            }
            medications.forEach { medication ->
                ScheduledRow(
                    icon = Icons.Outlined.Medication,
                    title = listOf(medication.name, medication.dosage).filter { it.isNotBlank() }.joinToString(" · "),
                    schedule = medication.schedule,
                    extra = listOfNotNull(refillStatus(medication), adherenceStatus(medication, doseLogs))
                        .joinToString("\n")
                        .ifEmpty { null },
                    onClick = { editMedication = Editing(medication) },
                )
            }

            SectionHeader(
                title = stringResource(R.string.checkups_title),
                addLabel = stringResource(R.string.checkup_add),
                onAdd = { editCheckup = Editing(null) },
            )
            if (checkups.isEmpty()) {
                EmptyLine(stringResource(R.string.checkups_empty))
            }
            checkups.forEach { checkup ->
                val type = stringResource(checkup.type.labelRes)
                ScheduledRow(
                    icon = when (checkup.type) {
                        CheckupType.BLOOD_TEST -> Icons.Outlined.Bloodtype
                        CheckupType.STOOL_TEST -> Icons.Outlined.Science
                    },
                    title = if (checkup.note.isBlank()) type else "$type · ${checkup.note}",
                    schedule = checkup.schedule,
                    onClick = { editCheckup = Editing(checkup) },
                )
            }

            SectionHeader(
                title = stringResource(R.string.exams_title),
                addLabel = stringResource(R.string.exam_add),
                onAdd = { editExam = Editing(null) },
            )
            if (exams.isEmpty()) {
                EmptyLine(stringResource(R.string.exams_empty))
            }
            upcomingExams.forEach { exam ->
                ExamRow(exam = exam, upcoming = true, onClick = { editExam = Editing(exam) })
            }
            pastExams.forEach { exam ->
                ExamRow(exam = exam, upcoming = false, onClick = { editExam = Editing(exam) })
            }

            SectionHeader(
                title = stringResource(R.string.weight_title),
                addLabel = stringResource(R.string.weight_add),
                onAdd = { editWeight = Editing(null) },
            )
            if (weights.isEmpty()) {
                EmptyLine(stringResource(R.string.weights_empty))
            } else {
                WeightCard(weights = weights, onOpenHistory = { weightHistory = true })
            }

            Text(
                text = stringResource(R.string.lab_results_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            LabSummaryCard(labResults = labResults, onOpen = onOpenLabs)
            Spacer(Modifier.height(24.dp))
        }
    }

    editMedication?.let { editing ->
        MedicationEditorSheet(
            initial = editing.item,
            onDismiss = { editMedication = null },
            onSave = { onSaveMedication(it); requestPermissionIfNeeded(it.schedule.enabled || it.refillAlert) },
            onDelete = onDeleteMedication,
        )
    }
    editCheckup?.let { editing ->
        CheckupEditorSheet(
            initial = editing.item,
            onDismiss = { editCheckup = null },
            onSave = { onSaveCheckup(it); requestPermissionIfNeeded(it.schedule.enabled) },
            onDelete = onDeleteCheckup,
        )
    }
    editExam?.let { editing ->
        ExamEditorSheet(
            initial = editing.item,
            onDismiss = { editExam = null },
            onSave = { onSaveExam(it); requestPermissionIfNeeded(it.remindersEnabled) },
            onDelete = onDeleteExam,
        )
    }
    // The history hides during entry to avoid stacking two sheets, then comes back.
    if (weightHistory && weights.isNotEmpty() && editWeight == null) {
        WeightHistorySheet(
            weights = weights,
            onDismiss = { weightHistory = false },
            onAdd = { editWeight = Editing(null) },
            onEdit = { editWeight = Editing(it) },
        )
    }
    editWeight?.let { editing ->
        WeightEditorSheet(
            initial = editing.item,
            onDismiss = { editWeight = null },
            onSave = onSaveWeight,
            onDelete = onDeleteWeight,
        )
    }
}

/**
 * Lab results summary: latest panel and tests outside the lab's reference values;
 * the detail is in the [LabsScreen] sub-screen.
 */
@Composable
private fun LabSummaryCard(labResults: List<LabResult>, onOpen: () -> Unit) {
    val histories = remember(labResults) { labHistories(labResults) }
    val latest = labResults.maxOfOrNull { it.date }
    val outside = histories.values.count { it.first().status() != null }
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Biotech, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (latest == null) stringResource(R.string.lab_results_empty)
                    else stringResource(R.string.lab_summary_latest, DateFormats.dayMonthYear(latest)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (histories.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(R.plurals.lab_summary_tests, histories.size, histories.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (outside > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.lab_summary_outside, outside, outside),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** "Adherence 30 d: 26/28 doses (93 %)", or null while no dose has been confirmed. */
@Composable
private fun adherenceStatus(medication: Medication, logs: List<DoseLog>): String? {
    val result = adherence(medication, logs, today().minus(ADHERENCE_DAYS - 1, DateTimeUnit.DAY), now())
    // Without any answer, "0 %" would suggest missed doses.
    if (result.expected == 0 || result.taken + result.missed == 0) return null
    val percent = (result.taken * 100.0 / result.expected).roundToInt()
    return pluralStringResource(
        R.plurals.treatment_adherence,
        result.expected,
        result.taken,
        result.expected,
        percent,
    )
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.notification_permission_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onGrant) { Text(stringResource(R.string.notification_permission_grant)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, addLabel: String, onAdd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = addLabel, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ScheduledRow(
    icon: ImageVector,
    title: String,
    schedule: ReminderSchedule,
    onClick: () -> Unit,
    extra: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(listOfNotNull(scheduleStatus(schedule), extra).joinToString("\n"))
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Icon(
                if (schedule.enabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                contentDescription = stringResource(
                    if (schedule.enabled) R.string.reminder_enabled else R.string.reminder_off
                ),
                tint = if (schedule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = settingsRowColors(),
    )
}

/**
 * Exam: date, next preparation step and, before the exam, the anesthesia
 * appointment to book. A past exam is greyed out.
 */
@Composable
private fun ExamRow(exam: Exam, upcoming: Boolean, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val current = now()
    val next = if (upcoming) exam.steps().firstOrNull { it.at >= current && it.step != ExamStep.EXAM } else null
    val nextLine = next?.let {
        stringResource(
            R.string.exam_next_step,
            stringResource(it.step.labelRes),
            // The diet starts on a given day: the reminder time adds nothing.
            if (it.step == ExamStep.DIET) DateFormats.weekdayDayMonth(it.at.date) else dateTimeLabel(it.at),
        )
    }
    val warning = when {
        !upcoming -> null
        exam.anesthesiaMissing() -> stringResource(R.string.exam_anesthesia_missing)
        else -> null
    }
    val lines = listOfNotNull(exam.note.ifBlank { null }, nextLine)
    ListItem(
        headlineContent = {
            Text(
                text = "${stringResource(exam.type.labelRes)} · ${dateTimeLabel(exam.at)}",
                color = if (upcoming) Color.Unspecified else muted,
            )
        },
        supportingContent = if (lines.isEmpty() && warning == null) null else {
            {
                Column {
                    if (lines.isNotEmpty()) Text(lines.joinToString("\n"), color = muted)
                    warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        leadingContent = {
            Icon(
                Icons.Outlined.MedicalServices,
                contentDescription = null,
                tint = if (upcoming) LocalContentColor.current else muted,
            )
        },
        trailingContent = if (!upcoming) null else {
            {
                Icon(
                    if (exam.remindersEnabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                    contentDescription = stringResource(
                        if (exam.remindersEnabled) R.string.reminder_enabled else R.string.reminder_off
                    ),
                    tint = if (exam.remindersEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = settingsRowColors(),
    )
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@Preview(showBackground = true)
@Composable
private fun CarePreview() {
    val today = today()
    IBDJournalTheme {
        CareContent(
            medications = listOf(
                Medication(1, "Mesalazine", "2 g", ReminderSchedule(startDate = today)),
                Medication(
                    2, "Adalimumab", "40 mg",
                    ReminderSchedule(startDate = today, repeatEvery = 2, repeatUnit = RepeatUnit.WEEK),
                ),
            ),
            checkups = listOf(
                Checkup(1, CheckupType.STOOL_TEST, "Calprotectine", ReminderSchedule(startDate = today.plus(10, DateTimeUnit.DAY), repeatUnit = RepeatUnit.ONCE)),
            ),
            exams = listOf(
                Exam(1, ExamType.COLONOSCOPY, today.plus(18, DateTimeUnit.DAY).atTime(9, 30)),
            ),
            labResults = listOf(
                LabResult(1, LabTest.CRP, 18.0, today.minus(40, DateTimeUnit.DAY)),
                LabResult(2, LabTest.CRP, 6.5, today.minus(5, DateTimeUnit.DAY), referenceHigh = 5.0),
                LabResult(3, LabTest.CALPROTECTIN, 250.0, today.minus(12, DateTimeUnit.DAY)),
                LabResult(4, LabTest.CALPROTECTIN, 30.0, today.minus(5, DateTimeUnit.DAY), qualifier = LabQualifier.BELOW),
                LabResult(5, LabTest.HEMOGLOBIN, 11.2, today.minus(40, DateTimeUnit.DAY)),
                LabResult(6, LabTest.HEMOGLOBIN, 12.4, today.minus(5, DateTimeUnit.DAY)),
                LabResult(7, LabTest.FERRITIN, 9.0, today.minus(5, DateTimeUnit.DAY)),
            ),
            doseLogs = emptyList(),
            onSaveMedication = {},
            onDeleteMedication = {},
            onSaveCheckup = {},
            onDeleteCheckup = {},
            weights = listOf(
                Weight(1, today.minus(60, DateTimeUnit.DAY), 68.4),
                Weight(2, today.minus(30, DateTimeUnit.DAY), 66.9),
                Weight(3, today.minus(2, DateTimeUnit.DAY), 67.5),
            ),
        )
    }
}
