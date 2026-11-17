package fr.ibdjournal.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.Appearance
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val appPrefs: AppPrefs,
) : ViewModel() {

    /** null until the DataStore has responded, to avoid an onboarding flash. */
    val onboardingDone: StateFlow<Boolean?> = appPrefs.onboardingDone
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null until the DataStore has responded: avoids a flash of the wrong palette. */
    val appearance: StateFlow<Appearance?> = appPrefs.appearance
        .map { it as Appearance? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun finishOnboarding() {
        viewModelScope.launch { appPrefs.markOnboardingDone() }
    }
}
