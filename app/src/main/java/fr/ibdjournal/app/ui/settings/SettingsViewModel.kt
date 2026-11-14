package fr.ibdjournal.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.Appearance
import fr.ibdjournal.app.data.DataBackupManager
import fr.ibdjournal.app.data.NotificationSettings
import fr.ibdjournal.app.data.ThemeMode
import fr.ibdjournal.app.data.ThemePalette
import fr.ibdjournal.app.notification.NotificationHelper
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPrefs: AppPrefs,
    private val notificationHelper: NotificationHelper,
    private val backupManager: DataBackupManager,
) : ViewModel() {

    val settings: StateFlow<NotificationSettings> = appPrefs.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationSettings())

    val appearance: StateFlow<Appearance> = appPrefs.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Appearance())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appPrefs.setThemeMode(mode) }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch { appPrefs.setThemePalette(palette) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // On relit les préférences enregistrées plutôt que le StateFlow : sa valeur
            // initiale est un défaut et peut ne pas encore refléter le DataStore.
            val current = appPrefs.notificationSettings.first()
            appPrefs.updateNotificationSettings(enabled, current.hour, current.minute)
            if (enabled) {
                notificationHelper.cancelLegacyWorkManagerReminder()
                notificationHelper.scheduleDailyReminder(current.hour, current.minute)
            } else {
                notificationHelper.cancelLegacyWorkManagerReminder()
                notificationHelper.cancelDailyReminder()
            }
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val current = appPrefs.notificationSettings.first()
            appPrefs.updateNotificationSettings(current.enabled, hour, minute)
            if (current.enabled) {
                notificationHelper.scheduleDailyReminder(hour, minute)
            }
        }
    }

    fun sendTestNotification() {
        notificationHelper.showStoolReminder()
    }

    fun exportData(outputStream: OutputStream) {
        viewModelScope.launch {
            backupManager.exportData(outputStream)
        }
    }

    fun importData(inputStream: InputStream) {
        viewModelScope.launch {
            backupManager.importData(inputStream)
        }
    }
}
