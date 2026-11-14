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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
        onExport = viewModel::exportData,
        onImport = viewModel::importData,
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
    onExport: (java.io.OutputStream) -> Unit,
    onImport: (java.io.InputStream) -> Unit,
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    // Sur Android 12+, sans « alarmes et rappels exacts » le rappel peut arriver en retard.
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

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.let(onExport) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.let(onImport) }
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
            AppearanceSection(
                appearance = appearance,
                onModeChange = onThemeModeChange,
                onPaletteChange = onPaletteChange,
            )
            LanguageSection()

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

            CloudBackupSection()

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.data_management),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.export_data)) },
                leadingContent = { Icon(Icons.Outlined.Download, contentDescription = null) },
                modifier = Modifier.clickable {
                    exportLauncher.launch("ibd_journal_backup.json")
                },
                colors = settingsRowColors(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.import_data)) },
                leadingContent = { Icon(Icons.Outlined.Upload, contentDescription = null) },
                modifier = Modifier.clickable {
                    importLauncher.launch(arrayOf("application/json"))
                },
                colors = settingsRowColors(),
            )
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
    val zoned = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val date = zoned.toLocalDate()
    val time = DateFormats.time(epochMillis)
    return when (date) {
        LocalDate.now() -> stringResource(R.string.today_at, time)
        LocalDate.now().minusDays(1) -> stringResource(R.string.yesterday_at, time)
        else -> stringResource(R.string.date_at, DateFormats.dayMonth(date), time)
    }
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: true
}

/** Ouvre l'écran système « Alarmes et rappels » ; certains fabricants n'exposent pas l'écran dédié. */
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
            onExport = {},
            onImport = {},
        )
    }
}
