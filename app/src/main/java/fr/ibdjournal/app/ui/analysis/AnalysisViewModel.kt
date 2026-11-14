package fr.ibdjournal.app.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.Stool
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalysisUiState(
    val stools: List<Stool> = emptyList(),
    val meals: List<Meal> = emptyList(),
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    repository: DiaryRepository,
) : ViewModel() {

    private val today = LocalDate.now()
    private val start = today.minusDays(29)

    val uiState: StateFlow<AnalysisUiState> = combine(
        repository.observeStools(start, today),
        repository.observeMeals(start, today),
    ) { stools, meals ->
        AnalysisUiState(stools = stools, meals = meals)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState())
}
