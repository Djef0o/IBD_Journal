package fr.ibdjournal.app.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.domain.AnalysisPeriod
import fr.ibdjournal.app.domain.AnalysisSummary
import fr.ibdjournal.app.domain.analyze
import fr.ibdjournal.app.domain.startFrom
import fr.ibdjournal.app.domain.today
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/** Écran Tendances : les calculs sont dans le module partagé ([analyze]). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    repository: DiaryRepository,
) : ViewModel() {

    private val today = today()
    private val period = MutableStateFlow(AnalysisPeriod.LAST_30)

    val uiState: StateFlow<AnalysisSummary> = period.flatMapLatest { selected ->
        // Sans borne de départ (« Tout »), tous les repas depuis le début.
        val mealsFrom = selected.startFrom(today) ?: LocalDate.fromEpochDays(0)
        combine(
            repository.observeAllStools(),
            repository.observeMeals(mealsFrom, today),
        ) { history, meals ->
            analyze(history, meals, today, selected)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisSummary())

    fun setPeriod(selected: AnalysisPeriod) {
        period.value = selected
    }
}
