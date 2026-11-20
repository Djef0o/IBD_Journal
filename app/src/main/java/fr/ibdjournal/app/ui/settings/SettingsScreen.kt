package fr.ibdjournal.app.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.Appearance
import fr.ibdjournal.app.data.NotificationSettings
import fr.ibdjournal.app.data.ThemeMode
import fr.ibdjournal.app.data.ThemePalette
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import fr.ibdjournal.app.domain.localDateTimeAt
import fr.ibdjournal.app.domain.isBackupOverdue
import fr.ibdjournal.app.domain.today
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()

    SettingsContent(
        settings = settings,
        appearance = appearance,
        onBack = onBack,
        onThemeModeChange = viewModel::setThemeMode,
        onPaletteChange = viewModel::setThemePalette,
        onReminderEnabled = viewModel::setReminderEnabled,
        onReminderTimeChanged = viewModel::setReminderTime,
        onSendTestNotification = viewModel::sendTestNotification,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: NotificationSettings,
    appearance: Appearance,
    onBack: (() -> Unit)?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPaletteChange: (ThemePalette) -> Unit,
    onReminderEnabled: (Boolean) -> Unit,
    onReminderTimeChanged: (Int, Int) -> Unit,
    onSendTestNotification: () -> Unit,
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    // On Android 12+, without "alarms and exact reminders" the reminder may arrive late.
    var exactAlarmsAllowed by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    var batteryExempt by remember { mutableStateOf(isBatteryExempt(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmsAllowed = canScheduleExactAlarms(context)
                batteryExempt = isBatteryExempt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        isPermissionGranted = isGranted
    }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(settings.enabled) {
        if (settings.enabled && !isPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.cancel),
                            )
                        }
                    }
                },
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
            ProfileSection()
            AppearanceSection(
                appearance = appearance,
                onModeChange = onThemeModeChange,
                onPaletteChange = onPaletteChange,
            )
            LanguageSection()
            SecuritySection()
            EmergencySection()

            Text(
                text = stringResource(R.string.notifications),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            if (settings.enabled && !isPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.notification_permission_warning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = { requestNotificationPermission() }) {
                            Text(stringResource(R.string.notification_permission_grant))
                        }
                    }
                }
            }

            if (settings.enabled && !exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.exact_alarm_warning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = { openExactAlarmSettings(context) }) {
                            Text(stringResource(R.string.exact_alarm_grant))
                        }
                    }
                }
            }

            if (settings.enabled && !batteryExempt) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.battery_warning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = { requestBatteryExemption(context) }) {
                            Text(stringResource(R.string.battery_grant))
                        }
                    }
                }
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.daily_reminder)) },
                supportingContent = { Text(stringResource(R.string.daily_reminder_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !isPermissionGranted) {
                                requestNotificationPermission()
                            }
                            onReminderEnabled(enabled)
                        },
                    )
                },
                colors = settingsRowColors(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.reminder_time)) },
                supportingContent = { Text(DateFormats.time(settings.hour, settings.minute)) },
                trailingContent = {
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(stringResource(R.string.modify))
                    }
                },
                colors = settingsRowColors(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.last_reminder)) },
                supportingContent = {
                    Text(
                        text = settings.lastReminderAt?.let { formatReminderMoment(it) }
                            ?: stringResource(R.string.last_reminder_never)
                    )
                },
                colors = settingsRowColors(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.test_notification)) },
                supportingContent = { Text(stringResource(R.string.test_notification_desc)) },
                leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                modifier = Modifier.clickable {
                    if (!isPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotificationPermission()
                    } else {
                        onSendTestNotification()
                    }
                },
                colors = settingsRowColors(),
            )

            CalendarSection()

            BackupSection()
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = settings.hour,
            initialMinute = settings.minute,
            is24Hour = DateFormat.is24HourFormat(context),
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

private fun isBatteryExempt(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestBatteryExemption(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        )
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

@Composable
private fun formatReminderMoment(epochMillis: Long): String {
    val date = localDateTimeAt(epochMillis).date
    val time = DateFormats.time(epochMillis)
    return when (date) {
        today() -> stringResource(R.string.today_at, time)
        today().minus(1, DateTimeUnit.DAY) -> stringResource(R.string.yesterday_at, time)
        else -> stringResource(R.string.date_at, DateFormats.dayMonth(date), time)
    }
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: true
}

/** Opens the system "Alarms and reminders" screen; some manufacturers don't expose the dedicated screen. */
private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val packageUri = Uri.parse("package:${context.packageName}")
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }
    }
}

private const val MIN_PASSWORD_LENGTH = 8

/** Export is always password-encrypted; import handles encrypted backups and old plain JSON. */
@Composable
private fun BackupSection(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.backupState.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
        viewModel::exportTo,
    )
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        viewModel::importFrom,
    )
    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
        viewModel::exportCsvTo,
    )

    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.data_management),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    val backup by viewModel.backupSettings.collectAsStateWithLifecycle()
    val lastBackup = backup.lastBackup
    val overdue = isBackupOverdue(lastBackup, today())
    ListItem(
        headlineContent = { Text(stringResource(R.string.backup_status_title)) },
        supportingContent = {
            Text(
                text = if (lastBackup == null) {
                    stringResource(R.string.backup_status_never)
                } else {
                    stringResource(R.string.backup_status_last, DateFormats.dayMonthYear(lastBackup))
                },
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                Icons.Outlined.Backup,
                contentDescription = null,
                tint = if (overdue) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
        },
        colors = settingsRowColors(),
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.backup_reminder_switch)) },
        supportingContent = { Text(stringResource(R.string.backup_reminder_desc)) },
        trailingContent = {
            Switch(checked = backup.reminderEnabled, onCheckedChange = viewModel::setBackupReminderEnabled)
        },
        colors = settingsRowColors(),
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.export_data)) },
        supportingContent = { Text(stringResource(R.string.backup_export_desc)) },
        leadingContent = { Icon(Icons.Outlined.Download, contentDescription = null) },
        modifier = Modifier.clickable { viewModel.startExport() },
        colors = settingsRowColors(),
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.import_data)) },
        supportingContent = { Text(stringResource(R.string.backup_import_desc)) },
        leadingContent = { Icon(Icons.Outlined.Upload, contentDescription = null) },
        // Any file type: .ibdj files have no recognized MIME type.
        modifier = Modifier.clickable { importLauncher.launch(arrayOf("*/*")) },
        colors = settingsRowColors(),
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.csv_export_title)) },
        supportingContent = { Text(stringResource(R.string.csv_export_desc)) },
        leadingContent = { Icon(Icons.Outlined.TableChart, contentDescription = null) },
        modifier = Modifier.clickable { viewModel.startCsvExport() },
        colors = settingsRowColors(),
    )

    when (val current = state) {
        BackupState.Idle -> Unit
        BackupState.CsvWarning -> AlertDialog(
            onDismissRequest = viewModel::dismissBackup,
            title = { Text(stringResource(R.string.csv_warning_title)) },
            text = { Text(stringResource(R.string.csv_warning_text)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCsvWarning) { Text(stringResource(R.string.csv_warning_continue)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBackup) { Text(stringResource(R.string.cancel)) }
            },
        )
        is BackupState.ExportPassword -> ExportPasswordDialog(
            csv = current.csv,
            onConfirm = { password ->
                viewModel.setExportPassword(password)
                if (current.csv) {
                    csvExportLauncher.launch("ibd-journal-data-${today()}.zip")
                } else {
                    exportLauncher.launch("ibd-journal-backup-${today()}.ibdj")
                }
            },
            onDismiss = viewModel::dismissBackup,
        )
        is BackupState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(
                            if (current.exporting) R.string.backup_exporting else R.string.backup_importing
                        )
                    )
                }
            },
        )
        is BackupState.ImportPassword -> ImportPasswordDialog(
            wrongPassword = current.wrongPassword,
            onConfirm = viewModel::importWithPassword,
            onDismiss = viewModel::dismissBackup,
        )
        is BackupState.Finished -> AlertDialog(
            onDismissRequest = viewModel::dismissBackup,
            text = { Text(stringResource(current.message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissBackup) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@Composable
private fun ExportPasswordDialog(csv: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val tooShort = password.length < MIN_PASSWORD_LENGTH
    val mismatch = confirmation.isNotEmpty() && confirmation != password
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (csv) R.string.csv_password_title else R.string.backup_export_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(if (csv) R.string.csv_password_message else R.string.backup_export_message))
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.backup_password),
                    isError = password.isNotEmpty() && tooShort,
                    supportingText = stringResource(R.string.backup_password_min_length, MIN_PASSWORD_LENGTH),
                )
                PasswordField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = stringResource(R.string.backup_password_confirm),
                    isError = mismatch,
                    supportingText = if (mismatch) stringResource(R.string.backup_password_mismatch) else null,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(if (csv) R.string.csv_password_warning else R.string.backup_password_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !tooShort && confirmation == password) {
                Text(stringResource(R.string.backup_export_choose_file))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ImportPasswordDialog(wrongPassword: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_title)) },
        text = {
            Column {
                Text(stringResource(R.string.backup_import_message))
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.backup_password),
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) stringResource(R.string.backup_import_wrong_password) else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.backup_import_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Masked password field, with a show/hide button. */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    supportingText: String?,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.backup_password_hide else R.string.backup_password_show
                    ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    IBDJournalTheme {
        SettingsContent(
            settings = NotificationSettings(),
            appearance = Appearance(),
            onBack = null,
            onThemeModeChange = {},
            onPaletteChange = {},
            onReminderEnabled = {},
            onReminderTimeChanged = { _, _ -> },
            onSendTestNotification = {},
        )
    }
}
