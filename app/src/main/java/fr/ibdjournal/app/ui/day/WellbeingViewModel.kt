package fr.ibdjournal.app.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.CareRepository
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.data.model.Sex
import fr.ibdjournal.app.data.model.WellbeingDay
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Sleep, stress, mood and period of a day, read and written from the day screen. */
@HiltViewModel
class WellbeingViewModel @Inject constructor(
    private val repository: CareRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    /** The menstrual period is only offered when the profile says female. */
    val periodAvailable: StateFlow<Boolean> = profileRepository.profile
        .map { it.sex == Sex.FEMALE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun on(date: LocalDate): Flow<WellbeingDay> = repository.wellbeingOn(date)

    /** Replaces the day's entry; an empty one removes it. */
    fun save(day: WellbeingDay) {
        viewModelScope.launch { repository.saveWellbeing(day) }
    }
}
