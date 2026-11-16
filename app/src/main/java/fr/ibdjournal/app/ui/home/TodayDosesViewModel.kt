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
 * Prises prévues aujourd'hui, avec leur statut. Permet de confirmer depuis l'accueil,
 * y compris en mode calendrier où aucune notification de l'app n'est envoyée.
 */
@HiltViewModel
class TodayDosesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    // today() est relu à chaque émission : la liste suit le changement de jour au prochain log.
    val doses: StateFlow<List<DoseDue>> = combine(
        profileRepository.medications,
        profileRepository.doseLogs,
    ) { medications, logs -> dosesOn(today(), medications, logs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Re-toucher le statut déjà choisi l'annule (null). */
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
