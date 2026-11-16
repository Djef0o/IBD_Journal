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
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import fr.ibdjournal.app.labimport.LabReportReader
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.Sex
import fr.ibdjournal.app.data.model.CheckupType
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.ui.settings.settingsRowColors
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import fr.ibdjournal.app.domain.adherence
import fr.ibdjournal.app.domain.now
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.labelRes
import kotlin.math.roundToInt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Fenêtre de l'observance affichée sous chaque traitement, aujourd'hui compris. */
private const val ADHERENCE_DAYS = 30

@Composable
fun CareScreen(viewModel: CareViewModel = hiltViewModel()) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val checkups by viewModel.checkups.collectAsStateWithLifecycle()
    val labResults by viewModel.labResults.collectAsStateWithLifecycle()
    val doseLogs by viewModel.doseLogs.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val labImport by viewModel.labImport.collectAsStateWithLifecycle()

    val reportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        viewModel::importLabReports,
    )

    CareContent(
        medications = medications,
        checkups = checkups,
        labResults = labResults,
        doseLogs = doseLogs,
        onSaveMedication = viewModel::saveMedication,
        onDeleteMedication = viewModel::deleteMedication,
        onSaveCheckup = viewModel::saveCheckup,
        onDeleteCheckup = viewModel::deleteCheckup,
        onSaveLabResult = viewModel::saveLabResult,
        onDeleteLabResult = viewModel::deleteLabResult,
        onImportLabReport = { reportPicker.launch(arrayOf(LabReportReader.PDF_MIME, "image/*")) },
        sex = sex,
    )

    when (val state = labImport) {
        LabImportState.Idle -> Unit
        is LabImportState.Reading -> LabImportProgressDialog(done = state.done, total = state.total)
        is LabImportState.Review -> LabImportReviewSheet(
            documents = state.documents,
            existing = labResults,
            onDismiss = viewModel::closeLabImport,
            onSave = viewModel::saveImportedResults,
        )
    }
}

/** Élément en cours d'édition : null = fermé ; `item == null` = création. */
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
    onSaveLabResult: (LabResult) -> Unit,
    onDeleteLabResult: (LabResult) -> Unit,
    onImportLabReport: () -> Unit = {},
    sex: Sex? = null,
) {
    val context = LocalContext.current
    var editMedication by remember { mutableStateOf<Editing<Medication>?>(null) }
    var editCheckup by remember { mutableStateOf<Editing<Checkup>?>(null) }
    var editLab by remember { mutableStateOf<LabEditing?>(null) }
    var labDetail by remember { mutableStateOf<LabTest?>(null) }
    // Historique par analyse, du plus récent au plus ancien, dans l'ordre de l'énumération.
    val labHistories = remember(labResults) {
        labResults.groupBy { it.test }
            .mapValues { (_, results) -> results.sortedWith(newestFirst) }
            .toSortedMap()
    }
    // Le dernier résultat d'une analyse supprimé : son détail n'a plus rien à montrer.
    LaunchedEffect(labHistories, labDetail) {
        val test = labDetail
        if (test != null && test !in labHistories) labDetail = null
    }

    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }
    // Demande l'autorisation au moment où l'utilisateur active un rappel.
    val requestPermissionIfNeeded = { notifies: Boolean ->
        if (notifies && !notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val anyReminder = medications.any { it.schedule.enabled || it.refillAlert } ||
        checkups.any { it.schedule.enabled }

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
                title = stringResource(R.string.lab_results_title),
                addLabel = stringResource(R.string.lab_result_add),
                onAdd = { editLab = LabEditing(null, labHistories.keys.firstOrNull() ?: LabTest.CRP) },
            )
            if (labHistories.isEmpty()) {
                EmptyLine(stringResource(R.string.lab_results_empty))
            } else {
                LabResultsViews(
                    histories = labHistories,
                    sex = sex,
                    onOpenTest = { labDetail = it },
                    onEditResult = { editLab = LabEditing(it, it.test) },
                )
            }
            // Lecture de comptes rendus PDF ou photos, sur l'appareil, puis vérification.
            OutlinedButton(
                onClick = onImportLabReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lab_import_reports_button))
            }
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
    // Le détail s'efface pendant la saisie pour ne pas empiler deux feuilles, puis revient.
    labDetail?.let { test ->
        val history = labHistories[test]
        if (history != null && editLab == null) {
            LabResultDetailSheet(
                test = test,
                history = history,
                sex = sex,
                onDismiss = { labDetail = null },
                onAdd = { editLab = LabEditing(null, test) },
                onEdit = { editLab = LabEditing(it, it.test) },
            )
        }
    }
    editLab?.let { editing ->
        LabResultEditorSheet(
            editing = editing,
            onDismiss = { editLab = null },
            onSave = onSaveLabResult,
            onDelete = onDeleteLabResult,
        )
    }
}

/** « Observance 30 j : 26/28 prises (93 %) », ou null tant qu'aucune prise n'a été confirmée. */
@Composable
private fun adherenceStatus(medication: Medication, logs: List<DoseLog>): String? {
    val result = adherence(medication, logs, today().minus(ADHERENCE_DAYS - 1, DateTimeUnit.DAY), now())
    // Sans aucune réponse, « 0 % » laisserait croire à des oublis.
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
            labResults = listOf(
                LabResult(1, LabTest.CRP, 18.0, today.minus(40, DateTimeUnit.DAY)),
                LabResult(2, LabTest.CRP, 6.5, today.minus(5, DateTimeUnit.DAY)),
                LabResult(3, LabTest.CALPROTECTIN, 250.0, today.minus(12, DateTimeUnit.DAY)),
                LabResult(4, LabTest.CALPROTECTIN, 30.0, today.minus(5, DateTimeUnit.DAY), qualifier = LabQualifier.BELOW),
                LabResult(5, LabTest.HEMOGLOBIN, 11.2, today.minus(40, DateTimeUnit.DAY)),
                LabResult(6, LabTest.HEMOGLOBIN, 12.4, today.minus(5, DateTimeUnit.DAY)),
                LabResult(7, LabTest.FERRITIN, 9.0, today.minus(5, DateTimeUnit.DAY)),
            ),
            sex = Sex.FEMALE,
            doseLogs = emptyList(),
            onSaveMedication = {},
            onDeleteMedication = {},
            onSaveCheckup = {},
            onDeleteCheckup = {},
            onSaveLabResult = {},
            onDeleteLabResult = {},
        )
    }
}
