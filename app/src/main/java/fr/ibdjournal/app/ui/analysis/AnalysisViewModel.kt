package fr.ibdjournal.app.ui.analysis

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.domain.AnalysisPeriod
import fr.ibdjournal.app.domain.AnalysisSummary
import fr.ibdjournal.app.domain.analyze
import fr.ibdjournal.app.domain.startFrom
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.report.ReportGenerator
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Écran Tendances : les calculs sont dans le module partagé ([analyze]). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    repository: DiaryRepository,
    private val reportGenerator: ReportGenerator,
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

    private val _exportingReport = MutableStateFlow(false)

    /** Vrai pendant la génération du rapport PDF. */
    val exportingReport: StateFlow<Boolean> = _exportingReport.asStateFlow()

    private val reportResults = Channel<Result<Uri>>(Channel.BUFFERED)

    /** Rapports prêts à partager (URI du FileProvider) ou échecs, consommés une seule fois. */
    val reports: Flow<Result<Uri>> = reportResults.receiveAsFlow()

    /** Rapport pour le médecin sur la période affichée. */
    fun exportReport() {
        if (_exportingReport.value) return
        _exportingReport.value = true
        val selected = period.value
        viewModelScope.launch {
            val result = try {
                Result.success(reportGenerator.generate(selected))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                _exportingReport.value = false
            }
            reportResults.send(result)
        }
    }
}
