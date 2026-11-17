package fr.ibdjournal.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.data.model.DoseStatus
import fr.ibdjournal.app.domain.DoseDue
import fr.ibdjournal.app.domain.dosesOn
import fr.ibdjournal.app.domain.today
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Doses scheduled for today, with their status. Lets the user confirm from the home screen,
 * including in calendar mode where the app sends no notification.
 */
@HiltViewModel
class TodayDosesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    // today() is re-read on every emission: the list follows the day change on the next log.
    val doses: StateFlow<List<DoseDue>> = combine(
        profileRepository.medications,
        profileRepository.doseLogs,
    ) { medications, logs -> dosesOn(today(), medications, logs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tapping the already-chosen status again clears it (null). */
    fun toggle(dose: DoseDue, status: DoseStatus) {
        viewModelScope.launch {
            profileRepository.logDose(
                dose.medication.id,
                dose.scheduledAt,
                if (dose.status == status) null else status,
            )
        }
    }
}
