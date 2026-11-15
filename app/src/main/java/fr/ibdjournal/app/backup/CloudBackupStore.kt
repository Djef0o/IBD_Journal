package fr.ibdjournal.app.backup

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudBackupDataStore by preferencesDataStore(name = "cloud_backup")

/** What is persisted for one provider. */
data class StoredProvider(
    val connected: Boolean = false,
    val accountLabel: String? = null,
    val lastBackupAt: Long? = null,
    val lastError: CloudError? = null,
)

/** Dedicated DataStore for cloud backup: connection state and automatic-backup settings. */
@Singleton
class CloudBackupStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.cloudBackupDataStore

    private val data: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    private fun connectedKey(p: CloudProvider) = booleanPreferencesKey("${p.key}_connected")
    private fun accountKey(p: CloudProvider) = stringPreferencesKey("${p.key}_account")
    private fun lastBackupKey(p: CloudProvider) = longPreferencesKey("${p.key}_last_backup")
    private fun lastErrorKey(p: CloudProvider) = stringPreferencesKey("${p.key}_last_error")

    private val googleFileIdKey = stringPreferencesKey("google_file_id")
    private val frequencyKey = stringPreferencesKey("auto_frequency")
    private val wifiOnlyKey = booleanPreferencesKey("auto_wifi_only")
    private val excludedTargetsKey = stringSetPreferencesKey("auto_excluded_targets")

    val providers: Flow<Map<CloudProvider, StoredProvider>> = data.map { prefs ->
        CloudProvider.entries.associateWith { p ->
            StoredProvider(
                connected = prefs[connectedKey(p)] ?: false,
                accountLabel = prefs[accountKey(p)],
                lastBackupAt = prefs[lastBackupKey(p)],
                lastError = prefs[lastErrorKey(p)]
                    ?.let { name -> CloudError.entries.firstOrNull { it.name == name } },
            )
        }
    }

    val autoBackupSettings: Flow<AutoBackupSettings> = data.map { prefs ->
        AutoBackupSettings(
            frequency = prefs[frequencyKey]
                ?.let { name -> BackupFrequency.entries.firstOrNull { it.name == name } }
                ?: BackupFrequency.OFF,
            wifiOnly = prefs[wifiOnlyKey] ?: true,
            excludedTargets = prefs[excludedTargetsKey].orEmpty()
                .mapNotNull { key -> CloudProvider.entries.firstOrNull { it.key == key } }
                .toSet(),
        )
    }

    suspend fun provider(p: CloudProvider): StoredProvider = providers.first().getValue(p)

    /** Marks [p] connected and clears the last error. */
    suspend fun setConnected(p: CloudProvider, accountLabel: String?) {
        dataStore.edit { prefs ->
            prefs[connectedKey(p)] = true
            if (accountLabel != null) prefs[accountKey(p)] = accountLabel else prefs.remove(accountKey(p))
            prefs.remove(lastErrorKey(p))
        }
    }

    /** Forgets everything about [p]: account, history, and its automatic-backup opt-out. */
    suspend fun clearProvider(p: CloudProvider) {
        dataStore.edit { prefs ->
            prefs.remove(connectedKey(p))
            prefs.remove(accountKey(p))
            prefs.remove(lastBackupKey(p))
            prefs.remove(lastErrorKey(p))
            if (p == CloudProvider.GOOGLE_DRIVE) prefs.remove(googleFileIdKey)
            prefs[excludedTargetsKey] = prefs[excludedTargetsKey].orEmpty() - p.key
        }
    }

    suspend fun recordSuccess(p: CloudProvider, at: Long) {
        dataStore.edit { prefs ->
            prefs[lastBackupKey(p)] = at
            prefs.remove(lastErrorKey(p))
        }
    }

    suspend fun recordError(p: CloudProvider, error: CloudError) {
        dataStore.edit { prefs -> prefs[lastErrorKey(p)] = error.name }
    }

    suspend fun googleFileId(): String? = data.first()[googleFileIdKey]

    suspend fun setGoogleFileId(fileId: String) {
        dataStore.edit { prefs -> prefs[googleFileIdKey] = fileId }
    }

    suspend fun setFrequency(frequency: BackupFrequency) {
        dataStore.edit { prefs -> prefs[frequencyKey] = frequency.name }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { prefs -> prefs[wifiOnlyKey] = wifiOnly }
    }

    suspend fun setAutoBackupTarget(p: CloudProvider, included: Boolean) {
        dataStore.edit { prefs ->
            val excluded = prefs[excludedTargetsKey].orEmpty()
            prefs[excludedTargetsKey] = if (included) excluded - p.key else excluded + p.key
        }
    }
}
