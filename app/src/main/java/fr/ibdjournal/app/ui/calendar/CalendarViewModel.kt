package fr.ibdjournal.app.ui.calendar

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.NotificationSettings
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.Product
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.domain.today
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Données affichées : change seulement avec la base, la date ou les réglages. */
data class CalendarUiState(
    val selectedDate: LocalDate = today(),
    val mealsByDay: Map<LocalDate, List<Meal>> = emptyMap(),
    val stoolsByDay: Map<LocalDate, List<Stool>> = emptyMap(),
    val reminder: NotificationSettings = NotificationSettings(),
)

/**
 * Brouillon de l'éditeur de repas. Séparé de [CalendarUiState] : une frappe dans la
 * description ne recompose que la feuille, sans regrouper à nouveau repas et selles.
 */
data class MealEditorState(
    val date: LocalDate,
    val editingMeal: Meal?,
    val type: MealType,
    val description: String = "",
    /** Nouvelle photo choisie (URI du sélecteur), pas encore copiée dans l'app. */
    val newPhotoUri: Uri? = null,
    val removePhoto: Boolean = false,
    val product: Product? = null,
)

/** Feuille de saisie d'une selle ouverte ; [stool] est null pour un ajout. */
data class StoolEditorState(val stool: Stool?, val initialBristol: Int? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: DiaryRepository,
    appPrefs: AppPrefs,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Journée ouverte : argument de navigation `day/{date}` (jour epoch), sinon aujourd'hui. */
    private val selectedDate = MutableStateFlow(
        savedStateHandle.get<String>(ARG_DATE)?.toLongOrNull()?.let { LocalDate.fromEpochDays(it) }
            ?: today()
    )

    private val _mealEditor = MutableStateFlow<MealEditorState?>(null)
    val mealEditor: StateFlow<MealEditorState?> = _mealEditor.asStateFlow()

    private val _stoolEditor = MutableStateFlow<StoolEditorState?>(null)
    val stoolEditor: StateFlow<StoolEditorState?> = _stoolEditor.asStateFlow()

    private val _showScanner = MutableStateFlow(false)
    val showScanner: StateFlow<Boolean> = _showScanner.asStateFlow()

    /**
     * Une seule souscription par date : la date et ses données sont émises ensemble,
     * jamais une nouvelle date avec les données de l'ancienne plage.
     * La marge de ±7 jours couvre le résumé hebdomadaire de l'accueil.
     */
    val uiState: StateFlow<CalendarUiState> = selectedDate
        .flatMapLatest { date ->
            val from = date.minus(7, DateTimeUnit.DAY)
            val to = date.plus(7, DateTimeUnit.DAY)
            combine(
                repository.observeMeals(from, to),
                repository.observeStools(from, to),
                appPrefs.notificationSettings,
            ) { meals, stools, reminder ->
                CalendarUiState(
                    selectedDate = date,
                    mealsByDay = meals.groupBy { it.date },
                    stoolsByDay = stools.groupBy { it.date },
                    reminder = reminder,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    // --- Navigation ---

    fun shiftDay(direction: Int) {
        selectedDate.update { it.plus(direction, DateTimeUnit.DAY) }
    }

    fun goToToday() {
        selectedDate.value = today()
    }

    // --- Repas ---

    fun openMealEditor(type: MealType, meal: Meal?) {
        _mealEditor.value = MealEditorState(
            date = selectedDate.value,
            editingMeal = meal,
            type = type,
            description = meal?.description.orEmpty(),
            product = meal?.product,
        )
    }

    fun closeMealEditor() {
        _mealEditor.value = null
    }

    fun onEditorDescriptionChanged(description: String) {
        _mealEditor.update { it?.copy(description = description) }
    }

    fun onEditorTypeChanged(type: MealType) {
        _mealEditor.update { it?.copy(type = type) }
    }

    fun onEditorPhotoPicked(uri: Uri) {
        _mealEditor.update { it?.copy(newPhotoUri = uri, removePhoto = false) }
    }

    /** Retire la photo affichée : la nouvelle si elle existe, sinon celle déjà enregistrée. */
    fun onEditorPhotoRemoved() {
        _mealEditor.update { it?.copy(newPhotoUri = null, removePhoto = true) }
    }

    fun onEditorProductChanged(product: Product?) {
        _mealEditor.update { it?.copy(product = product) }
    }

    fun saveMeal() {
        val editor = _mealEditor.value ?: return
        viewModelScope.launch {
            val existingPath = editor.editingMeal?.photoUri
            // Copie la photo choisie dans le stockage de l'app : l'URI du sélecteur
            // perd ses droits d'accès au redémarrage du processus.
            val importedPath = editor.newPhotoUri?.let { repository.importPhoto(it) }
            val photoPath = when {
                importedPath != null -> importedPath
                editor.removePhoto -> null
                else -> existingPath
            }
            if (existingPath != null && existingPath != photoPath) {
                repository.deletePhoto(existingPath)
            }
            repository.saveMeal(
                Meal(
                    id = editor.editingMeal?.id ?: 0,
                    date = editor.date,
                    type = editor.type,
                    description = editor.description,
                    photoUri = photoPath,
                    product = editor.product,
                )
            )
            _mealEditor.value = null
        }
    }

    fun deleteMeal(meal: Meal) {
        viewModelScope.launch {
            repository.deleteMeal(meal)
            _mealEditor.value = null
        }
    }

    // --- Selles ---

    fun openStoolSheet(stool: Stool?, initialBristol: Int? = null) {
        _stoolEditor.value = StoolEditorState(stool, initialBristol)
    }

    fun closeStoolSheet() {
        _stoolEditor.value = null
    }

    fun saveStool(
        bristolType: Int,
        bloodLevel: Int,
        mucusLevel: Int,
        painLevel: Int,
        fatigueLevel: Int,
        comment: String,
    ) {
        val existing = _stoolEditor.value?.stool
        val date = selectedDate.value
        viewModelScope.launch {
            repository.saveStool(
                Stool(
                    id = existing?.id ?: 0,
                    date = date,
                    bristolType = bristolType,
                    bloodLevel = bloodLevel,
                    mucusLevel = mucusLevel,
                    painLevel = painLevel,
                    fatigueLevel = fatigueLevel,
                    comment = comment,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
            )
            _stoolEditor.value = null
        }
    }

    fun deleteStool(stool: Stool) {
        viewModelScope.launch { repository.deleteStool(stool) }
    }

    // --- Scanner ---

    fun openScanner() {
        _showScanner.value = true
    }

    fun closeScanner() {
        _showScanner.value = false
    }

    fun onScannedProduct(product: Product) {
        onEditorProductChanged(product)
        _showScanner.value = false
    }

    companion object {
        /** Nom de l'argument de la route `day/{date}`. */
        const val ARG_DATE = "date"
    }
}
