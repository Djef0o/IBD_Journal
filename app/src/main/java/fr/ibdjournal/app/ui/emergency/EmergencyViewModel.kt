package fr.ibdjournal.app.ui.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.CareRepository
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.data.model.EmergencyInfo
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Emergency fields and the data of the sheet, assembled from the unlocked journal. */
@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val care: CareRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    val info: StateFlow<EmergencyInfo> = care.emergencyInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyInfo())

    val data: StateFlow<EmergencyData> =
        combine(profiles.profile, care.emergencyInfo, profiles.medications) { profile, info, medications ->
            EmergencyData(profile, info, medications)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyData())

    fun save(info: EmergencyInfo) {
        viewModelScope.launch { care.saveEmergencyInfo(info) }
    }
}
