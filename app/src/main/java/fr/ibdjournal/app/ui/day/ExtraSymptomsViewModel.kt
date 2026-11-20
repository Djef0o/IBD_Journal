package fr.ibdjournal.app.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.CareRepository
import fr.ibdjournal.app.data.model.ExtraSymptom
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Extra-intestinal symptoms of a day, read and written from the day screen and the score editor. */
@HiltViewModel
class ExtraSymptomsViewModel @Inject constructor(
    private val repository: CareRepository,
) : ViewModel() {

    /** Symptoms noted on [date]; empty when none. */
    fun on(date: LocalDate): Flow<Set<ExtraSymptom>> = repository.extraSymptomsOn(date)

    /** Replaces the day's symptoms; an empty set removes the entry. */
    fun save(date: LocalDate, symptoms: Set<ExtraSymptom>) {
        viewModelScope.launch { repository.saveExtraSymptoms(date, symptoms) }
    }
}
