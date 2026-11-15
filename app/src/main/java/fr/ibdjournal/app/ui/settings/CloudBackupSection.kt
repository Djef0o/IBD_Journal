package fr.ibdjournal.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddToDrive
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.backup.BackupFrequency
import fr.ibdjournal.app.backup.CloudBackupEvent
import fr.ibdjournal.app.backup.CloudBackupViewModel
import fr.ibdjournal.app.backup.CloudError
import fr.ibdjournal.app.backup.CloudProvider
import fr.ibdjournal.app.backup.ManualBackupResult
import fr.ibdjournal.app.backup.ProviderConnection
import fr.ibdjournal.app.backup.ProviderState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "Cloud backup" block of the settings screen: one row per provider (connect / disconnect),
 * "Back up now", and the automatic backup settings. Meant for a scrollable Column.
 */
@Composable
fun CloudBackupSection(modifier: Modifier = Modifier) {
    val viewModel: CloudBackupViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = rememberDateTimeFormatter()

    var showFrequencyDialog by remember { mutableStateOf(false) }
    var pendingDisconnect by remember { mutableStateOf<CloudProvider?>(null) }

    val googleConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onGoogleConsentResult(result.resultCode, result.data)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CloudBackupEvent.RequestGoogleConsent -> googleConsentLauncher.launch(
                    IntentSenderRequest.Builder(event.pendingIntent).build()
                )
            }
        }
    }

    Column(modifier) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.cloud_backup_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        state.providers.forEach { provider ->
            ProviderRow(
                state = provider,
                formatter = formatter,
                onConnect = { viewModel.connect(provider.provider) },
                onDisconnect = { pendingDisconnect = provider.provider },
            )
        }

        val hasConnected = state.connectedProviders.isNotEmpty()
        val lastResult = state.lastManualResult
        ListItem(
            headlineContent = { Text(stringResource(R.string.cloud_backup_now)) },
            supportingContent = {
                when {
                    state.backingUp -> Text(stringResource(R.string.cloud_backup_running))
                    !hasConnected -> Text(stringResource(R.string.cloud_backup_now_needs_connection))
                    lastResult is ManualBackupResult.Success ->
                        Text(stringResource(R.string.cloud_backup_done))
                    lastResult is ManualBackupResult.Failure -> Text(
                        text = stringResource(
                            R.string.cloud_backup_failed_for,
                            lastResult.providers.map { stringResource(it.nameRes) }.joinToString(),
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text(stringResource(R.string.cloud_backup_now_desc))
                }
            },
            leadingContent = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
            trailingContent = if (state.backingUp) {
                { SmallProgress() }
            } else {
                null
            },
            modifier = Modifier.clickable(
                enabled = hasConnected && !state.backingUp,
                onClick = viewModel::backUpNow,
            ),
            colors = settingsRowColors(),
        )

        val auto = state.autoBackup
        ListItem(
            headlineContent = { Text(stringResource(R.string.cloud_auto_title)) },
            supportingContent = { Text(stringResource(auto.frequency.labelRes)) },
            leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            trailingContent = {
                TextButton(onClick = { showFrequencyDialog = true }) {
                    Text(stringResource(R.string.cloud_change))
                }
            },
            colors = settingsRowColors(),
        )
        if (auto.frequency != BackupFrequency.OFF) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.cloud_auto_wifi_only)) },
                supportingContent = { Text(stringResource(R.string.cloud_auto_wifi_only_desc)) },
                leadingContent = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
                trailingContent = {
                    Switch(checked = auto.wifiOnly, onCheckedChange = viewModel::setWifiOnly)
                },
                colors = settingsRowColors(),
            )
            if (!hasConnected) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.cloud_auto_no_target),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    colors = settingsRowColors(),
                )
            }
            state.connectedProviders.forEach { target ->
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                R.string.cloud_auto_target,
                                stringResource(target.provider.nameRes),
                            )
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = target.autoBackupTarget,
                            onCheckedChange = { included ->
                                viewModel.setAutoBackupTarget(target.provider, included)
                            },
                        )
                    },
                    colors = settingsRowColors(),
                )
            }
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.cloud_last_backup)) },
            supportingContent = {
                Text(
                    state.lastBackupAt?.let { formatter.formatEpoch(it) }
                        ?: stringResource(R.string.cloud_last_backup_never)
                )
            },
            colors = settingsRowColors(),
        )
    }

    if (showFrequencyDialog) {
        FrequencyDialog(
            current = state.autoBackup.frequency,
            onSelect = { frequency ->
                viewModel.setFrequency(frequency)
                showFrequencyDialog = false
            },
            onDismiss = { showFrequencyDialog = false },
        )
    }

    pendingDisconnect?.let { provider ->
        val name = stringResource(provider.nameRes)
        AlertDialog(
            onDismissRequest = { pendingDisconnect = null },
            title = { Text(stringResource(R.string.cloud_disconnect_title, name)) },
            text = { Text(stringResource(R.string.cloud_disconnect_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect(provider)
                        pendingDisconnect = null
                    }
                ) {
                    Text(stringResource(R.string.cloud_disconnect))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDisconnect = null }) {
                    Text(stringResource(R.string.cloud_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProviderRow(
    state: ProviderState,
    formatter: DateTimeFormatter,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connection = state.connection

    ListItem(
        headlineContent = { Text(stringResource(state.provider.nameRes)) },
        supportingContent = {
            Column {
                Text(
                    when (connection) {
                        ProviderConnection.Disconnected ->
                            stringResource(R.string.cloud_status_disconnected)
                        ProviderConnection.Connecting ->
                            stringResource(R.string.cloud_status_connecting)
                        is ProviderConnection.Connected -> connection.accountLabel
                            ?.let { stringResource(R.string.cloud_status_connected_as, it) }
                            ?: stringResource(R.string.cloud_status_connected)
                    }
                )
                if (connection is ProviderConnection.Connected) {
                    Text(
                        state.lastBackupAt
                            ?.let {
                                stringResource(R.string.cloud_status_last_backup, formatter.formatEpoch(it))
                            }
                            ?: stringResource(R.string.cloud_status_no_backup_yet)
                    )
                }
                state.lastError?.let { error ->
                    Text(
                        text = stringResource(error.messageRes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        leadingContent = { Icon(state.provider.icon, contentDescription = null) },
        trailingContent = {
            when (connection) {
                is ProviderConnection.Connected -> TextButton(onClick = onDisconnect) {
                    Text(stringResource(R.string.cloud_disconnect))
                }
                ProviderConnection.Connecting -> SmallProgress()
                ProviderConnection.Disconnected -> TextButton(onClick = onConnect) {
                    Text(stringResource(R.string.cloud_connect))
                }
            }
        },
        colors = settingsRowColors(),
    )
}

@Composable
private fun FrequencyDialog(
    current: BackupFrequency,
    onSelect: (BackupFrequency) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_auto_title)) },
        text = {
            Column {
                BackupFrequency.entries.forEach { frequency ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = frequency == current,
                                onClick = { onSelect(frequency) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = frequency == current, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(frequency.labelRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cloud_cancel))
            }
        },
    )
}

@Composable
private fun SmallProgress() {
    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
}

@Composable
private fun rememberDateTimeFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    }
}

private fun DateTimeFormatter.formatEpoch(epochMillis: Long): String =
    format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

@get:StringRes
private val CloudProvider.nameRes: Int
    get() = when (this) {
        CloudProvider.GOOGLE_DRIVE -> R.string.cloud_provider_google_drive
    }

private val CloudProvider.icon: ImageVector
    get() = when (this) {
        CloudProvider.GOOGLE_DRIVE -> Icons.Outlined.AddToDrive
    }

@get:StringRes
private val BackupFrequency.labelRes: Int
    get() = when (this) {
        BackupFrequency.OFF -> R.string.cloud_auto_off
        BackupFrequency.DAILY -> R.string.cloud_auto_daily
        BackupFrequency.WEEKLY -> R.string.cloud_auto_weekly
    }

@get:StringRes
private val CloudError.messageRes: Int
    get() = when (this) {
        CloudError.NETWORK -> R.string.cloud_error_network
        CloudError.AUTH_REQUIRED -> R.string.cloud_error_auth_required
        CloudError.NOT_CONFIGURED -> R.string.cloud_error_google_not_configured
        CloudError.STORAGE_FULL -> R.string.cloud_error_storage_full
        CloudError.SERVER -> R.string.cloud_error_server
        CloudError.UNKNOWN -> R.string.cloud_error_unknown
    }
