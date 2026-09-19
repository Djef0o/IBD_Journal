package fr.ibdjournal.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.NotificationSettings
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsContent(
        settings = settings,
        onBack = onBack,
        onReminderEnabled = viewModel::setReminderEnabled,
        onReminderTimeChanged = viewModel::setReminderTime,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: NotificationSettings,
    onBack: () -> Unit,
    onReminderEnabled: (Boolean) -> Unit,
    onReminderTimeChanged: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    // Android 13+ : la permission POST_NOTIFICATIONS est demandée à l'activation du rappel.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val requestNotificationPermission = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.daily_reminder)) },
                supportingContent = { Text(stringResource(R.string.daily_reminder_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled) requestNotificationPermission()
                            onReminderEnabled(enabled)
                        },
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.reminder_time)) },
                supportingContent = { Text(formatTime(settings.hour, settings.minute)) },
                trailingContent = {
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(stringResource(R.string.modify))
                    }
                },
            )
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = settings.hour,
            initialMinute = settings.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReminderTimeChanged(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.reminder_time),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                }
            },
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    IBDJournalTheme {
        SettingsContent(
            settings = NotificationSettings(),
            onBack = {},
            onReminderEnabled = {},
            onReminderTimeChanged = { _, _ -> },
        )
    }
}
