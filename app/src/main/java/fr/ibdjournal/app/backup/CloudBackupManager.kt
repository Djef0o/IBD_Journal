package fr.ibdjournal.app.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import fr.ibdjournal.app.data.DataBackupManager
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Entry point of cloud backup: per-provider state, connection flows, uploads (manual and
 * automatic) and scheduling of the periodic work.
 */
@Singleton
class CloudBackupManager @Inject constructor(
    private val store: CloudBackupStore,
    private val dataBackupManager: DataBackupManager,
    private val scheduler: CloudBackupScheduler,
    private val googleDrive: GoogleDriveBackup,
    private val dropbox: DropboxBackup,
    private val oneDrive: OneDriveBackup,
) {
    /** Outlives screens: an upload or OAuth exchange must not die with a ViewModel. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val backupMutex = Mutex()

    private val targets: Map<CloudProvider, CloudBackupTarget> =
        listOf(googleDrive, dropbox, oneDrive).associateBy { it.provider }

    private val connecting = MutableStateFlow<Set<CloudProvider>>(emptySet())
    private val backingUp = MutableStateFlow(false)

    /** True while any backup (manual or automatic) is uploading. */
    val isBackingUp: StateFlow<Boolean> = backingUp.asStateFlow()

    val autoBackupSettings: Flow<AutoBackupSettings> = store.autoBackupSettings

    val providerStates: Flow<List<ProviderState>> = combine(
        store.providers,
        store.autoBackupSettings,
        connecting,
    ) { stored, auto, inProgress ->
        CloudProvider.entries.map { p ->
            val state = stored.getValue(p)
            ProviderState(
                provider = p,
                connection = when {
                    !target(p).isConfigured -> ProviderConnection.NotConfigured
                    p in inProgress -> ProviderConnection.Connecting
                    state.connected -> ProviderConnection.Connected(state.accountLabel)
                    else -> ProviderConnection.Disconnected
                },
                lastBackupAt = state.lastBackupAt,
                lastError = state.lastError,
                autoBackupTarget = state.connected && p !in auto.excludedTargets,
            )
        }
    }

    /** Synchronous placeholder until the DataStore has been read. */
    fun initialProviderStates(): List<ProviderState> = CloudProvider.entries.map { p ->
        ProviderState(
            provider = p,
            connection = if (target(p).isConfigured) {
                ProviderConnection.Disconnected
            } else {
                ProviderConnection.NotConfigured
            },
            lastBackupAt = null,
            lastError = null,
            autoBackupTarget = false,
        )
    }

    // ---- Google Drive ------------------------------------------------------------------

    /** @return the consent screen to launch, or null if already granted (or failed). */
    suspend fun beginGoogleConnect(): PendingIntent? = connectStep(CloudProvider.GOOGLE_DRIVE) {
        when (val step = googleDrive.beginConnect()) {
            is GoogleDriveBackup.ConnectStep.NeedsUserConsent -> step.pendingIntent
            GoogleDriveBackup.ConnectStep.Connected -> {
                reschedule()
                null
            }
        }
    }

    suspend fun completeGoogleConnect(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return // The user backed out.
        connectStep(CloudProvider.GOOGLE_DRIVE) {
            googleDrive.completeConnect(data)
            reschedule()
        }
    }

    // ---- Dropbox -----------------------------------------------------------------------

    suspend fun prepareDropboxConnect() {
        dropbox.markAuthorizationPending()
    }

    fun launchDropboxAuthorization(activity: Activity) {
        try {
            dropbox.launchAuthorization(activity)
        } catch (e: IllegalStateException) {
            // Thrown by the SDK when the db-<APP_KEY> AuthActivity entry doesn't match.
            Log.w(TAG, "Dropbox authorization could not start: ${e.message}")
            scope.launch {
                store.setDropboxAuthPending(false)
                store.recordError(CloudProvider.DROPBOX, CloudError.NOT_CONFIGURED)
            }
        }
    }

    /** To call whenever the settings screen resumes (AuthActivity has then finished). */
    suspend fun completeDropboxConnectIfPending() {
        if (!dropbox.isConfigured || !store.isDropboxAuthPending()) return
        connectStep(CloudProvider.DROPBOX) {
            if (dropbox.completeAuthorizationIfPending()) reschedule()
        }
    }

    // ---- OneDrive ----------------------------------------------------------------------

    suspend fun createOneDriveAuthorizationUri(): Uri? =
        connectStep(CloudProvider.ONEDRIVE) { oneDrive.createAuthorizationUri() }

    fun onOneDriveRedirect(redirect: Uri) {
        scope.launch {
            connectStep(CloudProvider.ONEDRIVE) {
                if (oneDrive.completeAuthorization(redirect)) reschedule()
            }
        }
    }

    // ---- Common ------------------------------------------------------------------------

    /** The browser / provider app could not be opened at all. */
    fun reportAuthorizationLaunchFailure(provider: CloudProvider) {
        scope.launch { store.recordError(provider, CloudError.UNKNOWN) }
    }

    suspend fun disconnect(provider: CloudProvider) {
        target(provider).disconnect()
        reschedule()
    }

    /** Backs up to every connected provider; keeps running if the caller goes away. */
    fun startManualBackup(): Deferred<BackupOutcome> = scope.async {
        backup(connectedProviders())
    }

    /** Called by [CloudBackupWorker]. */
    suspend fun runAutomaticBackup(): BackupOutcome {
        val settings = store.autoBackupSettings.first()
        if (settings.frequency == BackupFrequency.OFF) return BackupOutcome(emptyList(), emptyMap())
        return backup(connectedProviders().filter { it !in settings.excludedTargets })
    }

    suspend fun setFrequency(frequency: BackupFrequency) {
        store.setFrequency(frequency)
        reschedule()
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        store.setWifiOnly(wifiOnly)
        reschedule()
    }

    suspend fun setAutoBackupTarget(provider: CloudProvider, included: Boolean) {
        store.setAutoBackupTarget(provider, included)
        reschedule()
    }

    private suspend fun backup(providers: List<CloudProvider>): BackupOutcome = backupMutex.withLock {
        if (providers.isEmpty()) return@withLock BackupOutcome(emptyList(), emptyMap())
        backingUp.value = true
        try {
            val content = try {
                ByteArrayOutputStream().also { dataBackupManager.exportData(it) }.toByteArray()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Export failed: ${e.javaClass.simpleName}")
                providers.forEach { store.recordError(it, CloudError.UNKNOWN) }
                return@withLock BackupOutcome(emptyList(), providers.associateWith { CloudError.UNKNOWN })
            }

            val succeeded = mutableListOf<CloudProvider>()
            val failed = mutableMapOf<CloudProvider, CloudError>()
            for (provider in providers) {
                val error = try {
                    target(provider).upload(content)
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CloudBackupException) {
                    e.error
                } catch (e: Exception) {
                    // Class name only: SDK messages may echo request details.
                    Log.w(TAG, "${provider.key}: unexpected ${e.javaClass.simpleName}")
                    CloudError.UNKNOWN
                }
                if (error == null) {
                    store.recordSuccess(provider, System.currentTimeMillis())
                    succeeded += provider
                } else {
                    Log.w(TAG, "${provider.key}: backup failed ($error)")
                    store.recordError(provider, error)
                    failed[provider] = error
                }
            }
            BackupOutcome(succeeded, failed)
        } finally {
            backingUp.value = false
        }
    }

    /**
     * Runs a connection step with the "connecting" indicator; failures are recorded as the
     * provider's last error (shown in settings) and yield null.
     */
    private suspend fun <T> connectStep(provider: CloudProvider, block: suspend () -> T): T? {
        connecting.update { it + provider }
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CloudBackupException) {
            Log.w(TAG, "${provider.key}: connection failed (${e.error}: ${e.message})")
            store.recordError(provider, e.error)
            null
        } catch (e: Exception) {
            Log.w(TAG, "${provider.key}: connection failed (${e.javaClass.simpleName})")
            store.recordError(provider, CloudError.UNKNOWN)
            null
        } finally {
            connecting.update { it - provider }
        }
    }

    private suspend fun connectedProviders(): List<CloudProvider> =
        store.providers.first()
            .filter { (provider, state) -> state.connected && target(provider).isConfigured }
            .keys
            .toList()

    private suspend fun reschedule() {
        val settings = store.autoBackupSettings.first()
        val hasTargets = connectedProviders().any { it !in settings.excludedTargets }
        scheduler.apply(settings, hasTargets)
    }

    private fun target(provider: CloudProvider): CloudBackupTarget = targets.getValue(provider)

    private companion object {
        const val TAG = "CloudBackup"
    }
}
