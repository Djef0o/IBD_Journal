package fr.ibdjournal.app.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.DoseLog
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.domain.LabReportScan
import fr.ibdjournal.app.domain.parseLabReport
import fr.ibdjournal.app.labimport.LabReportReader
import android.net.Uri
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import fr.ibdjournal.app.data.model.Exam

@HiltViewModel
class CareViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val reportReader: LabReportReader,
) : ViewModel() {

    val medications: StateFlow<List<Medication>> = repository.medications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checkups: StateFlow<List<Checkup>> = repository.checkups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labResults: StateFlow<List<LabResult>> = repository.labResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Prises confirmées, pour l'observance affichée sous chaque traitement. */
    val doseLogs: StateFlow<List<DoseLog>> = repository.doseLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** Coloscopies et rectosigmoïdoscopies, la plus proche d'abord. */
    val exams: StateFlow<List<Exam>> = repository.exams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveExam(exam: Exam) {
        viewModelScope.launch { repository.saveExam(exam) }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch { repository.deleteExam(exam.id) }
    }

    fun saveLabResult(result: LabResult) {
        viewModelScope.launch { repository.saveLabResult(result) }
    }

    fun deleteLabResult(result: LabResult) {
        viewModelScope.launch { repository.deleteLabResult(result.id) }
    }

    // --- Import d'un compte rendu ---

    private val _labImport = MutableStateFlow<LabImportState>(LabImportState.Idle)
    val labImport: StateFlow<LabImportState> = _labImport.asStateFlow()

    /**
     * Lit les comptes rendus un par un sur l'appareil puis propose les valeurs trouvées à la
     * vérification. Un document illisible reste dans la liste, signalé, sans valeur.
     */
    fun importLabReports(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _labImport.value = LabImportState.Reading(done = 0, total = uris.size)
        viewModelScope.launch {
            val documents = uris.mapIndexed { index, uri ->
                _labImport.value = LabImportState.Reading(done = index, total = uris.size)
                val name = reportReader.displayName(uri)
                runCatching { parseLabReport(reportReader.read(uri)) }
                    .fold(
                        onSuccess = { ImportedDocument(name, it) },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            ImportedDocument(name, LabReportScan(null, emptyList()), failed = true)
                        },
                    )
            }
            _labImport.value = LabImportState.Review(documents)
        }
    }

    /** Enregistre les valeurs validées par l'utilisateur. */
    fun saveImportedResults(results: List<LabResult>) {
        _labImport.value = LabImportState.Idle
        viewModelScope.launch { results.forEach { repository.saveLabResult(it) } }
    }

    fun closeLabImport() {
        _labImport.value = LabImportState.Idle
    }
}

/** Compte rendu lu à l'import ; [failed] : illisible, [scan] alors vide. */
data class ImportedDocument(val name: String?, val scan: LabReportScan, val failed: Boolean = false)

sealed interface LabImportState {
    data object Idle : LabImportState
    /** [done] documents lus sur [total]. */
    data class Reading(val done: Int, val total: Int) : LabImportState
    data class Review(val documents: List<ImportedDocument>) : LabImportState
}
