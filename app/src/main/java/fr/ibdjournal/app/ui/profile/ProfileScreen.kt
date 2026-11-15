package fr.ibdjournal.app.ui.profile

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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import fr.ibdjournal.app.data.model.CheckupType
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.settings.settingsRowColors
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import java.time.LocalDate
import java.time.Period

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val checkups by viewModel.checkups.collectAsStateWithLifecycle()

    ProfileContent(
        profile = profile,
        medications = medications,
        checkups = checkups,
        onSaveProfile = viewModel::saveProfile,
        onSaveMedication = viewModel::saveMedication,
        onDeleteMedication = viewModel::deleteMedication,
        onSaveCheckup = viewModel::saveCheckup,
        onDeleteCheckup = viewModel::deleteCheckup,
    )
}

/** Élément en cours d'édition : null = fermé ; `item == null` = création. */
private data class Editing<T>(val item: T?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileContent(
    profile: Profile,
    medications: List<Medication>,
    checkups: List<Checkup>,
    onSaveProfile: (Profile) -> Unit,
    onSaveMedication: (Medication) -> Unit,
    onDeleteMedication: (Medication) -> Unit,
    onSaveCheckup: (Checkup) -> Unit,
    onDeleteCheckup: (Checkup) -> Unit,
) {
    val context = LocalContext.current
    var editProfile by remember { mutableStateOf(false) }
    var editMedication by remember { mutableStateOf<Editing<Medication>?>(null) }
    var editCheckup by remember { mutableStateOf<Editing<Checkup>?>(null) }

    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }
    // Demande l'autorisation au moment où l'utilisateur active un rappel.
    val requestPermissionIfNeeded = { schedule: ReminderSchedule ->
        if (schedule.enabled && !notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val anyReminder = medications.any { it.schedule.enabled } || checkups.any { it.schedule.enabled }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
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
            IdentityCard(profile = profile, onEdit = { editProfile = true })

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
                    dateLine = DateFormats.dayMonthYear(checkup.schedule.startDate),
                    onClick = { editCheckup = Editing(checkup) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editProfile) {
        ProfileEditDialog(
            profile = profile,
            onDismiss = { editProfile = false },
            onSave = onSaveProfile,
        )
    }
    editMedication?.let { editing ->
        MedicationEditorSheet(
            initial = editing.item,
            onDismiss = { editMedication = null },
            onSave = { onSaveMedication(it); requestPermissionIfNeeded(it.schedule) },
            onDelete = onDeleteMedication,
        )
    }
    editCheckup?.let { editing ->
        CheckupEditorSheet(
            initial = editing.item,
            onDismiss = { editCheckup = null },
            onSave = { onSaveCheckup(it); requestPermissionIfNeeded(it.schedule) },
            onDelete = onDeleteCheckup,
        )
    }
}

@Composable
private fun IdentityCard(profile: Profile, onEdit: () -> Unit) {
    val fullName = listOf(profile.firstName, profile.lastName).filter { it.isNotBlank() }.joinToString(" ")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = fullName.ifEmpty { stringResource(R.string.profile_empty_name) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = profile.birthDate?.let { birthDate ->
                        val age = Period.between(birthDate, LocalDate.now()).years
                        stringResource(
                            R.string.profile_born_on,
                            DateFormats.dayMonthYear(birthDate),
                            pluralStringResource(R.plurals.profile_age, age, age),
                        )
                    } ?: stringResource(R.string.profile_birth_date_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.modify)) }
        }
    }
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
    dateLine: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        overlineContent = if (dateLine == null) null else {
            { Text(dateLine) }
        },
        supportingContent = { Text(scheduleStatus(schedule)) },
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
private fun ProfilePreview() {
    val today = LocalDate.now()
    IBDJournalTheme {
        ProfileContent(
            profile = Profile("Camille", "Martin", LocalDate.of(1990, 4, 12)),
            medications = listOf(
                Medication(1, "Mesalazine", "2 g", ReminderSchedule(startDate = today)),
                Medication(
                    2, "Adalimumab", "40 mg",
                    ReminderSchedule(startDate = today, repeatEvery = 2, repeatUnit = RepeatUnit.WEEK),
                ),
            ),
            checkups = listOf(
                Checkup(1, CheckupType.STOOL_TEST, "Calprotectine", ReminderSchedule(startDate = today.plusDays(10), repeatUnit = RepeatUnit.ONCE)),
            ),
            onSaveProfile = {},
            onSaveMedication = {},
            onDeleteMedication = {},
            onSaveCheckup = {},
            onDeleteCheckup = {},
        )
    }
}
