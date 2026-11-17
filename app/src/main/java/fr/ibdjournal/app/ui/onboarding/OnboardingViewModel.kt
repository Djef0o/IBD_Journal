package fr.ibdjournal.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** First launch: the profile and treatments entered are saved as the steps progress. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    val medications: StateFlow<List<Medication>> = repository.medications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveProfile(profile: Profile) {
        viewModelScope.launch { repository.saveProfile(profile) }
    }

    fun saveMedication(medication: Medication) {
        viewModelScope.launch { repository.saveMedication(medication) }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch { repository.deleteMedication(medication.id) }
    }
}
