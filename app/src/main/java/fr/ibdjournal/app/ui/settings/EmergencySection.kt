package fr.ibdjournal.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MedicalInformation
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.EmergencyInfo
import fr.ibdjournal.app.ui.emergency.EmergencySheet
import fr.ibdjournal.app.ui.emergency.EmergencyViewModel
import fr.ibdjournal.app.ui.emergency.UrgentNeedCard

/**
 * Emergency sheet fields, lock screen access and previews of the two emergency screens.
 * Turning on the lock screen access asks for confirmation: the sheet then needs no PIN.
 */
@Composable
fun EmergencySection(modifier: Modifier = Modifier) {
    val viewModel: EmergencyViewModel = hiltViewModel()
    val info by viewModel.info.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmLockScreen by rememberSaveable { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showCard by rememberSaveable { mutableStateOf(false) }
    val filled = listOf(
        info.allergies, info.doctorName, info.doctorPhone, info.contactName, info.contactPhone, info.notes,
    ).any { it.isNotBlank() }

    Column(modifier) {
        Text(
            text = stringResource(R.string.emergency_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        ListItem(
            leadingContent = { Icon(Icons.Outlined.MedicalInformation, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.emergency_info_row)) },
            supportingContent = {
                Text(stringResource(if (filled) R.string.emergency_info_filled else R.string.emergency_info_empty))
            },
            trailingContent = {
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.modify)) }
            },
            modifier = Modifier.clickable { editing = true },
            colors = settingsRowColors(),
        )
        ListItem(
            leadingContent = { Icon(Icons.Outlined.LockOpen, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.emergency_lock_switch)) },
            supportingContent = { Text(stringResource(R.string.emergency_lock_warning)) },
            trailingContent = {
                Switch(
                    checked = info.showOnLockScreen,
                    onCheckedChange = { on ->
                        if (on) confirmLockScreen = true else viewModel.save(info.copy(showOnLockScreen = false))
                    },
                )
            },
            modifier = Modifier.clickable {
                if (info.showOnLockScreen) viewModel.save(info.copy(showOnLockScreen = false))
                else confirmLockScreen = true
            },
            colors = settingsRowColors(),
        )
        ListItem(
            leadingContent = { Icon(Icons.Outlined.MedicalInformation, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.emergency_preview_sheet)) },
            modifier = Modifier.clickable { showSheet = true },
            colors = settingsRowColors(),
        )
        ListItem(
            leadingContent = { Icon(Icons.Outlined.Wc, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.emergency_preview_card)) },
            supportingContent = { Text(stringResource(R.string.emergency_preview_card_sub)) },
            modifier = Modifier.clickable { showCard = true },
            colors = settingsRowColors(),
        )
    }

    if (editing) {
        EmergencyEditorSheet(
            info = info,
            onDismiss = { editing = false },
            onSave = { viewModel.save(it) },
        )
    }

    if (confirmLockScreen) {
        AlertDialog(
            onDismissRequest = { confirmLockScreen = false },
            title = { Text(stringResource(R.string.emergency_lock_confirm_title)) },
            text = { Text(stringResource(R.string.emergency_lock_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLockScreen = false
                    viewModel.save(info.copy(showOnLockScreen = true))
                }) { Text(stringResource(R.string.emergency_lock_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLockScreen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showSheet) EmergencySheet(data = data, onDismiss = { showSheet = false })
    if (showCard) UrgentNeedCard(onDismiss = { showCard = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyEditorSheet(info: EmergencyInfo, onDismiss: () -> Unit, onSave: (EmergencyInfo) -> Unit) {
    var allergies by remember { mutableStateOf(info.allergies) }
    var doctorName by remember { mutableStateOf(info.doctorName) }
    var doctorPhone by remember { mutableStateOf(info.doctorPhone) }
    var contactName by remember { mutableStateOf(info.contactName) }
    var contactPhone by remember { mutableStateOf(info.contactPhone) }
    var notes by remember { mutableStateOf(info.notes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                text = stringResource(R.string.emergency_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.emergency_edit_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = allergies,
                onValueChange = { allergies = it },
                label = { Text(stringResource(R.string.emergency_allergies)) },
                placeholder = { Text(stringResource(R.string.emergency_allergies_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = doctorName,
                onValueChange = { doctorName = it },
                label = { Text(stringResource(R.string.emergency_doctor_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = doctorPhone,
                onValueChange = { doctorPhone = it },
                label = { Text(stringResource(R.string.emergency_doctor_phone)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = contactName,
                onValueChange = { contactName = it },
                label = { Text(stringResource(R.string.emergency_contact_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { contactPhone = it },
                label = { Text(stringResource(R.string.emergency_contact_phone)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.emergency_notes)) },
                placeholder = { Text(stringResource(R.string.emergency_notes_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSave(
                        info.copy(
                            allergies = allergies.trim(),
                            doctorName = doctorName.trim(),
                            doctorPhone = doctorPhone.trim(),
                            contactName = contactName.trim(),
                            contactPhone = contactPhone.trim(),
                            notes = notes.trim(),
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
