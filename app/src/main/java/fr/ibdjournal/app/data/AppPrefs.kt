package fr.ibdjournal.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    val minute: Int = 0,
    /** Horodatage du dernier rappel réellement affiché (diagnostic). */
    val lastReminderAt: Long? = null,
)

/** Préférences de l'application (Jetpack DataStore). */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.dataStore

    private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    private val KEY_NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
    private val KEY_NOTIF_HOUR = intPreferencesKey("notif_hour")
    private val KEY_NOTIF_MINUTE = intPreferencesKey("notif_minute")
    private val KEY_LAST_REMINDER = longPreferencesKey("last_reminder_at")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_THEME_PALETTE = stringPreferencesKey("theme_palette")

    /** true si l'utilisateur a déjà vu l'écran de bienvenue. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] ?: false
    }

    val notificationSettings: Flow<NotificationSettings> = dataStore.data.map { prefs ->
        NotificationSettings(
            enabled = prefs[KEY_NOTIF_ENABLED] ?: true,
            hour = prefs[KEY_NOTIF_HOUR] ?: 19,
            minute = prefs[KEY_NOTIF_MINUTE] ?: 0,
            lastReminderAt = prefs[KEY_LAST_REMINDER],
        )
    }

    /** Valeur inconnue (ancienne version, palette retirée) : on retombe sur le défaut. */
    val appearance: Flow<Appearance> = dataStore.data.map { prefs ->
        Appearance(
            mode = prefs[KEY_THEME_MODE]
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            palette = prefs[KEY_THEME_PALETTE]
                ?.let { name -> ThemePalette.entries.firstOrNull { it.name == name } }
                ?: ThemePalette.SAGE,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        dataStore.edit { prefs -> prefs[KEY_THEME_PALETTE] = palette.name }
    }

    suspend fun markOnboardingDone() {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = true }
    }

    /** Enregistre qu'un rappel vient d'être affiché (sert au diagnostic dans les réglages). */
    suspend fun markReminderShown() {
        dataStore.edit { prefs -> prefs[KEY_LAST_REMINDER] = System.currentTimeMillis() }
    }

    suspend fun updateNotificationSettings(enabled: Boolean, hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTIF_ENABLED] = enabled
            prefs[KEY_NOTIF_HOUR] = hour
            prefs[KEY_NOTIF_MINUTE] = minute
        }
    }
}

