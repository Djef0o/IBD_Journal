package fr.ibdjournal.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class NotificationSettings(
    val enabled: Boolean = true,
    val hour: Int = 19,
    val minute: Int = 0
)

/** Préférences de l'application (Jetpack DataStore). */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.dataStore

    private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    private val KEY_NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
    private val KEY_NOTIF_HOUR = intPreferencesKey("notif_hour")
    private val KEY_NOTIF_MINUTE = intPreferencesKey("notif_minute")

    /** true si l'utilisateur a déjà vu l'écran de bienvenue. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] ?: false
    }

    val notificationSettings: Flow<NotificationSettings> = dataStore.data.map { prefs ->
        NotificationSettings(
            enabled = prefs[KEY_NOTIF_ENABLED] ?: true,
            hour = prefs[KEY_NOTIF_HOUR] ?: 19,
            minute = prefs[KEY_NOTIF_MINUTE] ?: 0
        )
    }

    suspend fun markOnboardingDone() {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = true }
    }

    suspend fun updateNotificationSettings(enabled: Boolean, hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTIF_ENABLED] = enabled
            prefs[KEY_NOTIF_HOUR] = hour
            prefs[KEY_NOTIF_MINUTE] = minute
        }
    }
}

