package fr.ibdjournal.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    val profile: StateFlow<Profile> = repository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Profile())

    val medications: StateFlow<List<Medication>> = repository.medications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checkups: StateFlow<List<Checkup>> = repository.checkups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveProfile(profile: Profile) {
        viewModelScope.launch { repository.saveProfile(profile) }
    }

    fun saveMedication(medication: Medication) {
        viewModelScope.launch { repository.saveMedication(medication) }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch { repository.deleteMedication(medication.id) }
    }

    fun saveCheckup(checkup: Checkup) {
        viewModelScope.launch { repository.saveCheckup(checkup) }
    }

    fun deleteCheckup(checkup: Checkup) {
        viewModelScope.launch { repository.deleteCheckup(checkup.id) }
    }
}
