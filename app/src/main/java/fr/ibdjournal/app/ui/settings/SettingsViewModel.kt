package fr.ibdjournal.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.NotificationSettings
import fr.ibdjournal.app.notification.NotificationHelper
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPrefs: AppPrefs,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {

    val settings: StateFlow<NotificationSettings> = appPrefs.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationSettings())

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            appPrefs.updateNotificationSettings(enabled, current.hour, current.minute)
            if (enabled) {
                notificationHelper.scheduleDailyReminder(
                    current.hour,
                    current.minute,
                    ExistingPeriodicWorkPolicy.UPDATE,
                )
            } else {
                notificationHelper.cancelDailyReminder()
            }
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val current = settings.value
            appPrefs.updateNotificationSettings(current.enabled, hour, minute)
            if (current.enabled) {
                notificationHelper.scheduleDailyReminder(
                    hour,
                    minute,
                    ExistingPeriodicWorkPolicy.UPDATE,
                )
            }
        }
    }
}
