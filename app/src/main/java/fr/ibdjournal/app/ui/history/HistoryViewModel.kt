package fr.ibdjournal.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.CareRepository
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.model.Flare
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.domain.today
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

data class HistoryUiState(
    val month: YearMonth = today().yearMonth,
    val stoolsByDay: Map<LocalDate, List<Stool>> = emptyMap(),
    /** All flares, chronological: the calendar marks the days they cover. */
    val flares: List<Flare> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: DiaryRepository,
    careRepository: CareRepository,
) : ViewModel() {

    private val monthOffset = MutableStateFlow(0)

    val uiState: StateFlow<HistoryUiState> = monthOffset
        .flatMapLatest { offset ->
            val month = today().yearMonth.plus(offset, DateTimeUnit.MONTH)
            combine(
                repository.observeStools(month.firstDay, month.lastDay),
                careRepository.flares,
            ) { stools, flares ->
                HistoryUiState(month = month, stoolsByDay = stools.groupBy { it.date }, flares = flares)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun previousMonth() {
        monthOffset.value -= 1
    }

    fun nextMonth() {
        monthOffset.value += 1
    }
}
