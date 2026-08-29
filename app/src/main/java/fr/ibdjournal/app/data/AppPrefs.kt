package fr.ibdjournal.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Préférences de l'application (Jetpack DataStore). */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val dataStore = context.dataStore

    private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    /** true si l'utilisateur a déjà vu l'écran de bienvenue. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] ?: false
    }

    suspend fun markOnboardingDone() {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = true }
    }
}
