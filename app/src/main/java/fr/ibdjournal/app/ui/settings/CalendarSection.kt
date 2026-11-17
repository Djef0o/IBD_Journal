package fr.ibdjournal.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.R
import fr.ibdjournal.app.calendar.PhoneCalendar
import fr.ibdjournal.app.calendar.WritableCalendar
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.CalendarSyncSettings
import fr.ibdjournal.app.notification.TreatmentReminderScheduler
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    prefs: AppPrefs,
    private val phoneCalendar: PhoneCalendar,
    private val scheduler: TreatmentReminderScheduler,
) : ViewModel() {

    val settings: StateFlow<CalendarSyncSettings> = prefs.calendarSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarSyncSettings())

    private val _calendars = MutableStateFlow<List<WritableCalendar>>(emptyList())
    val calendars: StateFlow<List<WritableCalendar>> = _calendars.asStateFlow()

    private val _permission = MutableStateFlow(phoneCalendar.hasPermission())
    val permission: StateFlow<Boolean> = _permission.asStateFlow()

    /** On every return to the screen: the permission and calendars may have changed. */
    fun refresh() {
        _permission.value = phoneCalendar.hasPermission()
        viewModelScope.launch(Dispatchers.IO) { _calendars.value = phoneCalendar.writableCalendars() }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduler.setCalendarMode(enabled)
            refresh()
        }
    }

    fun chooseCalendar(calendarId: Long) {
        viewModelScope.launch(Dispatchers.IO) { scheduler.setCalendar(calendarId) }
    }
}

/**
 * "Reminders in the phone's calendar" setting: doses, orders and checkups become
 * calendar events instead of app notifications.
 */
@Composable
fun CalendarSection(modifier: Modifier = Modifier) {
    val viewModel: CalendarSettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val permission by viewModel.permission.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    var confirmingPrivacy by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.refresh()
        if (results.values.all { it }) viewModel.setEnabled(true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val current = calendars.firstOrNull { it.id == settings.calendarId }
        ?: calendars.firstOrNull { it.primary }
        ?: calendars.firstOrNull()

    Column(modifier) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.calendar_section_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (settings.enabled && !permission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.calendar_permission_lost),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { permissionLauncher.launch(PhoneCalendar.PERMISSIONS) }) {
                        Text(stringResource(R.string.notification_permission_grant))
                    }
                }
            }
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.calendar_sync)) },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.calendar_sync_desc))
                    if (settings.enabled) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.calendar_privacy_note),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            leadingContent = { Icon(Icons.Outlined.EventAvailable, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { enabled ->
                        // Enabling: first warn that treatment and exam names leave the app.
                        if (enabled) confirmingPrivacy = true else viewModel.setEnabled(false)
                    },
                )
            },
            colors = settingsRowColors(),
        )

        if (settings.enabled && permission) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.calendar_target)) },
                supportingContent = {
                    Text(current?.let { "${it.name} · ${it.account}" } ?: stringResource(R.string.calendar_none))
                },
                trailingContent = if (calendars.size > 1) {
                    { TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.modify)) } }
                } else {
                    null
                },
                colors = settingsRowColors(),
            )
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.calendar_target)) },
            text = {
                Column {
                    calendars.forEach { calendar ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.chooseCalendar(calendar.id)
                                    choosing = false
                                },
                        ) {
                            RadioButton(selected = calendar.id == current?.id, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    calendar.account,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmingPrivacy) {
        AlertDialog(
            onDismissRequest = { confirmingPrivacy = false },
            title = { Text(stringResource(R.string.calendar_privacy_title)) },
            text = {
                Text(
                    stringResource(R.string.calendar_privacy_message),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingPrivacy = false
                        if (permission) {
                            viewModel.setEnabled(true)
                        } else {
                            permissionLauncher.launch(PhoneCalendar.PERMISSIONS)
                        }
                    },
                ) { Text(stringResource(R.string.calendar_privacy_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingPrivacy = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
