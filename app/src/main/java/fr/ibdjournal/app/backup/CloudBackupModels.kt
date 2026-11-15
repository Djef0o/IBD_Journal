package fr.ibdjournal.app.backup

/** Name of the single backup file kept (and overwritten) in each cloud storage. */
const val CLOUD_BACKUP_FILE_NAME = "IBD Journal backup.json"

/** Cloud storages the diary can be backed up to. [key] is the stable persistence key. */
enum class CloudProvider(val key: String) {
    GOOGLE_DRIVE("google"),
}

/**
 * Failure categories, persisted by name and mapped to localized text in the UI
 * (a stored sentence would stay in the language active when it failed).
 */
enum class CloudError {
    /** No connectivity / timeout: worth retrying later. */
    NETWORK,

    /** Authorization revoked or expired: the user must reconnect. */
    AUTH_REQUIRED,

    /** The OAuth client of this build is missing or misconfigured (developer side). */
    NOT_CONFIGURED,

    /** The user's cloud storage has no room left. */
    STORAGE_FULL,

    /** Temporary server-side failure or rate limiting: worth retrying later. */
    SERVER,

    UNKNOWN;

    val isRetryable: Boolean get() = this == NETWORK || this == SERVER
}

class CloudBackupException(
    val error: CloudError,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: error.name, cause)

enum class BackupFrequency { OFF, DAILY, WEEKLY }

data class AutoBackupSettings(
    val frequency: BackupFrequency = BackupFrequency.OFF,
    /** Only run automatic backups on an unmetered (typically Wi-Fi) network. */
    val wifiOnly: Boolean = true,
    /** Connected providers the user opted out of; every other connected provider is a target. */
    val excludedTargets: Set<CloudProvider> = emptySet(),
)

sealed interface ProviderConnection {
    data object Disconnected : ProviderConnection
    data object Connecting : ProviderConnection
    data class Connected(val accountLabel: String?) : ProviderConnection
}

data class ProviderState(
    val provider: CloudProvider,
    val connection: ProviderConnection,
    val lastBackupAt: Long?,
    val lastError: CloudError?,
    /** Whether automatic backups write to this provider (only meaningful when connected). */
    val autoBackupTarget: Boolean,
)

data class BackupOutcome(
    val succeeded: List<CloudProvider>,
    val failed: Map<CloudProvider, CloudError>,
) {
    val hasRetryableFailure: Boolean get() = failed.values.any { it.isRetryable }
}
