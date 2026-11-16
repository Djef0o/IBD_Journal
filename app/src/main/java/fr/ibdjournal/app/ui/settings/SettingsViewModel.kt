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
import fr.ibdjournal.app.data.BackupFormat
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

/** Étapes de l'export/import chiffré, affichées en dialogues par l'écran. */
sealed interface BackupState {
    data object Idle : BackupState
    /** Choix du mot de passe, avant le sélecteur de fichier. */
    data object ExportPassword : BackupState
    /** Calcul de clé (quelques secondes) puis lecture/écriture. */
    data class Working(val exporting: Boolean) : BackupState
    data class ImportPassword(val wrongPassword: Boolean = false) : BackupState
    /** Ancien export non chiffré : confirmation avant import. */
    data object ImportPlainConfirm : BackupState
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

    /** Mot de passe choisi, gardé le temps du sélecteur de fichier puis effacé. */
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
        viewModelScope.launch { notificationHelper.startDailyCheckIn() }
    }

    fun startExport() {
        _backupState.value = BackupState.ExportPassword
    }

    /** Mot de passe validé : l'écran ouvre ensuite le sélecteur de fichier. */
    fun setExportPassword(password: String) {
        exportPassword = password.toCharArray()
        _backupState.value = BackupState.Idle
    }

    /** Fichier de destination choisi (ou `null` si annulé). */
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
                // Pas de fichier incomplet laissé derrière soi.
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                BackupState.Finished(R.string.backup_export_failed)
            } finally {
                password.fill(' ')
            }
        }
    }

    /** Fichier à importer choisi (ou `null` si annulé) : on regarde s'il est chiffré. */
    fun importFrom(uri: Uri?) {
        if (uri == null) return
        importUri = uri
        viewModelScope.launch {
            _backupState.value = try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException(uri.toString())
                when (backupManager.detectFormat(input)) {
                    BackupFormat.Encrypted -> BackupState.ImportPassword()
                    BackupFormat.PlainJson -> BackupState.ImportPlainConfirm
                    BackupFormat.Unknown -> {
                        importUri = null
                        BackupState.Finished(R.string.backup_import_unrecognized)
                    }
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

    fun confirmPlainImport() = runImport(null)

    private fun runImport(password: CharArray?) {
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
                password?.fill(' ')
            }
        }
    }

    /** Fermeture d'un dialogue : abandon de l'opération en attente. */
    fun dismissBackup() {
        exportPassword?.fill(' ')
        exportPassword = null
        importUri = null
        _backupState.value = BackupState.Idle
    }
}
