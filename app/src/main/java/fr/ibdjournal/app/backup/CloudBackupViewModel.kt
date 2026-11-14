package fr.ibdjournal.app.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of the last "Back up now" tapped on this screen. */
sealed interface ManualBackupResult {
    data object Success : ManualBackupResult
    data class Failure(val providers: List<CloudProvider>) : ManualBackupResult
}

data class CloudBackupUiState(
    val providers: List<ProviderState>,
    val autoBackup: AutoBackupSettings = AutoBackupSettings(),
    val backingUp: Boolean = false,
    val lastManualResult: ManualBackupResult? = null,
) {
    val connectedProviders: List<ProviderState>
        get() = providers.filter { it.connection is ProviderConnection.Connected }

    /** Most recent successful upload, any provider. */
    val lastBackupAt: Long?
        get() = providers.mapNotNull { it.lastBackupAt }.maxOrNull()
}

/** One-shot UI actions that need an Activity. */
sealed interface CloudBackupEvent {
    data class RequestGoogleConsent(val pendingIntent: PendingIntent) : CloudBackupEvent
    data object StartDropboxAuthorization : CloudBackupEvent
    data class OpenOneDriveAuthorization(val uri: Uri) : CloudBackupEvent
}

@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    private val manager: CloudBackupManager,
) : ViewModel() {

    private val lastManualResult = MutableStateFlow<ManualBackupResult?>(null)

    private val eventChannel = Channel<CloudBackupEvent>(Channel.BUFFERED)
    val events: Flow<CloudBackupEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<CloudBackupUiState> = combine(
        manager.providerStates,
        manager.autoBackupSettings,
        manager.isBackingUp,
        lastManualResult,
    ) { providers, auto, backingUp, lastResult ->
        CloudBackupUiState(providers, auto, backingUp, lastResult)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CloudBackupUiState(providers = manager.initialProviderStates()),
    )

    fun connect(provider: CloudProvider) {
        viewModelScope.launch {
            when (provider) {
                CloudProvider.GOOGLE_DRIVE -> manager.beginGoogleConnect()
                    ?.let { eventChannel.send(CloudBackupEvent.RequestGoogleConsent(it)) }
                CloudProvider.DROPBOX -> {
                    manager.prepareDropboxConnect()
                    eventChannel.send(CloudBackupEvent.StartDropboxAuthorization)
                }
                CloudProvider.ONEDRIVE -> manager.createOneDriveAuthorizationUri()
                    ?.let { eventChannel.send(CloudBackupEvent.OpenOneDriveAuthorization(it)) }
            }
        }
    }

    fun onGoogleConsentResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch { manager.completeGoogleConnect(resultCode, data) }
    }

    fun launchDropboxAuthorization(activity: Activity) {
        manager.launchDropboxAuthorization(activity)
    }

    fun onAuthorizationLaunchFailed(provider: CloudProvider) {
        manager.reportAuthorizationLaunchFailure(provider)
    }

    /** Dropbox hands its credential back through a static result read on resume. */
    fun onResume() {
        viewModelScope.launch { manager.completeDropboxConnectIfPending() }
    }

    fun disconnect(provider: CloudProvider) {
        viewModelScope.launch { manager.disconnect(provider) }
    }

    fun backUpNow() {
        lastManualResult.value = null
        val backup = manager.startManualBackup()
        viewModelScope.launch {
            val outcome = backup.await()
            lastManualResult.value = if (outcome.failed.isEmpty()) {
                ManualBackupResult.Success
            } else {
                ManualBackupResult.Failure(outcome.failed.keys.toList())
            }
        }
    }

    fun setFrequency(frequency: BackupFrequency) {
        viewModelScope.launch { manager.setFrequency(frequency) }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { manager.setWifiOnly(wifiOnly) }
    }

    fun setAutoBackupTarget(provider: CloudProvider, included: Boolean) {
        viewModelScope.launch { manager.setAutoBackupTarget(provider, included) }
    }
}
