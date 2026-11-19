package fr.ibdjournal.app.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.DoseLog
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Weight
import fr.ibdjournal.app.data.CareRepository
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.model.ActivityScore
import fr.ibdjournal.app.data.model.Diagnosis
import fr.ibdjournal.app.data.model.Flare
import fr.ibdjournal.app.data.model.SteroidCourse
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.data.model.Vaccination
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.launch
import fr.ibdjournal.app.data.model.Exam

@HiltViewModel
class CareViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val careRepository: CareRepository,
    private val diaryRepository: DiaryRepository,
    private val reportReader: LabReportReader,
) : ViewModel() {

    val medications: StateFlow<List<Medication>> = repository.medications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checkups: StateFlow<List<Checkup>> = repository.checkups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labResults: StateFlow<List<LabResult>> = repository.labResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Confirmed doses, for the adherence shown under each treatment. */
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

    /** Colonoscopies and sigmoidoscopies, nearest first. */
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

    // --- Flares, activity scores, vaccinations, steroid courses ---

    /** Flare periods, chronological. */
    val flares: StateFlow<List<Flare>> = careRepository.flares
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveFlare(flare: Flare) {
        viewModelScope.launch { careRepository.saveFlare(flare) }
    }

    fun deleteFlare(flare: Flare) {
        viewModelScope.launch { careRepository.deleteFlare(flare.id) }
    }

    /** Activity scores, chronological. */
    val activityScores: StateFlow<List<ActivityScore>> = careRepository.activityScores
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Diagnosis of the profile: picks the questionnaire proposed by default. */
    val diagnosis: StateFlow<Diagnosis?> = repository.profile
        .map { it.diagnosis }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Stools of [date], to pre-fill a new score entry. */
    fun stoolsOn(date: LocalDate): Flow<List<Stool>> = diaryRepository.observeStools(date, date)

    fun saveActivityScore(score: ActivityScore) {
        viewModelScope.launch { careRepository.saveActivityScore(score) }
    }

    fun deleteActivityScore(score: ActivityScore) {
        viewModelScope.launch { careRepository.deleteActivityScore(score.id) }
    }

    /** Vaccinations, chronological. */
    val vaccinations: StateFlow<List<Vaccination>> = careRepository.vaccinations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveVaccination(vaccination: Vaccination) {
        viewModelScope.launch { careRepository.saveVaccination(vaccination) }
    }

    fun deleteVaccination(vaccination: Vaccination) {
        viewModelScope.launch { careRepository.deleteVaccination(vaccination.id) }
    }

    /** Corticosteroid courses, chronological. */
    val steroidCourses: StateFlow<List<SteroidCourse>> = careRepository.steroidCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveSteroidCourse(course: SteroidCourse) {
        viewModelScope.launch { careRepository.saveSteroidCourse(course) }
    }

    fun deleteSteroidCourse(course: SteroidCourse) {
        viewModelScope.launch { careRepository.deleteSteroidCourse(course.id) }
    }

    /** Body weight entries, chronological. */
    val weights: StateFlow<List<Weight>> = repository.weights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveWeight(weight: Weight) {
        viewModelScope.launch { repository.saveWeight(weight) }
    }

    fun deleteWeight(weight: Weight) {
        viewModelScope.launch { repository.deleteWeight(weight.id) }
    }

    // --- Report import ---

    private val _labImport = MutableStateFlow<LabImportState>(LabImportState.Idle)
    val labImport: StateFlow<LabImportState> = _labImport.asStateFlow()

    /**
     * Reads the reports one by one on the device, then offers the values found for
     * review. An unreadable document stays in the list, flagged, without values.
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

    /** Saves the values validated by the user. */
    fun saveImportedResults(results: List<LabResult>) {
        _labImport.value = LabImportState.Idle
        viewModelScope.launch { results.forEach { repository.saveLabResult(it) } }
    }

    fun closeLabImport() {
        _labImport.value = LabImportState.Idle
    }
}

/** Report read at import; [failed]: unreadable, [scan] then empty. */
data class ImportedDocument(val name: String?, val scan: LabReportScan, val failed: Boolean = false)

sealed interface LabImportState {
    data object Idle : LabImportState
    /** [done] documents read out of [total]. */
    data class Reading(val done: Int, val total: Int) : LabImportState
    data class Review(val documents: List<ImportedDocument>) : LabImportState
}
