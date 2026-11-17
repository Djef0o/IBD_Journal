package fr.ibdjournal.app.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.Appearance
import fr.ibdjournal.app.data.BackupCrypto
import fr.ibdjournal.app.data.DataBackupManager
import fr.ibdjournal.app.data.NotificationSettings
import fr.ibdjournal.app.data.ThemeMode
import fr.ibdjournal.app.data.ThemePalette
import fr.ibdjournal.app.notification.NotificationHelper
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Steps of the encrypted export/import, shown as dialogs by the screen. */
sealed interface BackupState {
    data object Idle : BackupState
    /** Password choice, before the file picker. */
    data object ExportPassword : BackupState
    /** Key derivation (a few seconds) then read/write. */
    data class Working(val exporting: Boolean) : BackupState
    data class ImportPassword(val wrongPassword: Boolean = false) : BackupState
    data class Finished(@StringRes val message: Int) : BackupState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPrefs: AppPrefs,
    private val notificationHelper: NotificationHelper,
    private val backupManager: DataBackupManager,
) : ViewModel() {

    val settings: StateFlow<NotificationSettings> = appPrefs.notificationSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationSettings())

    val appearance: StateFlow<Appearance> = appPrefs.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Appearance())

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    /** Chosen password, kept for the duration of the file picker then cleared. */
    private var exportPassword: CharArray? = null
    private var importUri: Uri? = null

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appPrefs.setThemeMode(mode) }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch { appPrefs.setThemePalette(palette) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Re-read the saved preferences rather than the StateFlow: its initial
            // value is a default and may not yet reflect the DataStore.
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
        viewModelScope.launch { notificationHelper.startDailyCheckIn() }
    }

    fun startExport() {
        _backupState.value = BackupState.ExportPassword
    }

    /** Password validated: the screen then opens the file picker. */
    fun setExportPassword(password: String) {
        exportPassword = password.toCharArray()
        _backupState.value = BackupState.Idle
    }

    /** Destination file chosen (or `null` if cancelled). */
    fun exportTo(uri: Uri?) {
        val password = exportPassword ?: return
        exportPassword = null
        if (uri == null) {
            password.fill(' ')
            return
        }
        _backupState.value = BackupState.Working(exporting = true)
        viewModelScope.launch {
            _backupState.value = try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw FileNotFoundException(uri.toString())
                backupManager.exportData(output, password)
                BackupState.Finished(R.string.backup_export_done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave no incomplete file behind.
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                BackupState.Finished(R.string.backup_export_failed)
            } finally {
                password.fill(' ')
            }
        }
    }

    /** File to import chosen (or `null` if cancelled): check whether it is encrypted. */
    fun importFrom(uri: Uri?) {
        if (uri == null) return
        importUri = uri
        viewModelScope.launch {
            _backupState.value = try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException(uri.toString())
                if (backupManager.isEncryptedBackup(input)) {
                    BackupState.ImportPassword()
                } else {
                    importUri = null
                    BackupState.Finished(R.string.backup_import_unrecognized)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importUri = null
                BackupState.Finished(R.string.backup_import_failed)
            }
        }
    }

    fun importWithPassword(password: String) = runImport(password.toCharArray())

    private fun runImport(password: CharArray) {
        val uri = importUri ?: return
        _backupState.value = BackupState.Working(exporting = false)
        viewModelScope.launch {
            _backupState.value = try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException(uri.toString())
                backupManager.importData(input, password)
                importUri = null
                BackupState.Finished(R.string.backup_import_done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupCrypto.WrongPasswordException) {
                BackupState.ImportPassword(wrongPassword = true)
            } catch (e: Exception) {
                importUri = null
                BackupState.Finished(R.string.backup_import_failed)
            } finally {
                password.fill(' ')
            }
        }
    }

    /** Dialog dismissed: abandon the pending operation. */
    fun dismissBackup() {
        exportPassword?.fill(' ')
        exportPassword = null
        importUri = null
        _backupState.value = BackupState.Idle
    }
}
