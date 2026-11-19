package fr.ibdjournal.app.ui.calendar

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.CareRepository
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.NotificationSettings
import fr.ibdjournal.app.data.model.Flare
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

/** Displayed data: changes only with the database, the date or the settings. */
data class CalendarUiState(
    val selectedDate: LocalDate = today(),
    val mealsByDay: Map<LocalDate, List<Meal>> = emptyMap(),
    val stoolsByDay: Map<LocalDate, List<Stool>> = emptyMap(),
    val reminder: NotificationSettings = NotificationSettings(),
)

/**
 * Meal editor draft. Separate from [CalendarUiState]: typing in the
 * description only recomposes the sheet, without regrouping meals and stools.
 */
data class MealEditorState(
    val date: LocalDate,
    val editingMeal: Meal?,
    val type: MealType,
    val description: String = "",
    /** Newly picked photo (picker URI), not yet copied into the app. */
    val newPhotoUri: Uri? = null,
    val removePhoto: Boolean = false,
    val product: Product? = null,
    /** Saved photo, decrypted (or legacy URI); null while loading or without photo. */
    val savedPhoto: Any? = null,
)

/** Open stool entry sheet; [stool] is null for an addition. */
data class StoolEditorState(val stool: Stool?, val initialBristol: Int? = null, val initialFalseUrge: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: DiaryRepository,
    appPrefs: AppPrefs,
    careRepository: CareRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Open day: `day/{date}` navigation argument (epoch day), otherwise today. */
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
     * A single subscription per date: the date and its data are emitted together,
     * never a new date with the data of the old range.
     * The ±7 day margin covers the home weekly summary.
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

    /** Flare periods, chronological: Home and the day screen mark the days they cover. */
    val flares: StateFlow<List<Flare>> = careRepository.flares
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Navigation ---

    fun shiftDay(direction: Int) {
        selectedDate.update { it.plus(direction, DateTimeUnit.DAY) }
    }

    fun goToToday() {
        selectedDate.value = today()
    }

    // --- Meals ---

    fun openMealEditor(type: MealType, meal: Meal?) {
        _mealEditor.value = MealEditorState(
            date = selectedDate.value,
            editingMeal = meal,
            type = type,
            description = meal?.description.orEmpty(),
            product = meal?.product,
        )
        val path = meal?.photoUri ?: return
        viewModelScope.launch {
            // Photo stored encrypted, except for very old meals pointing to a URI.
            val photo: Any? = if ("://" in path) path else repository.readPhoto(path)
            _mealEditor.update { editor -> editor?.takeIf { it.editingMeal?.id == meal.id }?.copy(savedPhoto = photo) ?: editor }
        }
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

    /** Removes the displayed photo: the new one if it exists, otherwise the one already saved. */
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
            // Copies the picked photo into the app storage: the picker URI
            // loses its access rights when the process restarts.
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

    // --- Stools ---

    fun openStoolSheet(stool: Stool?, initialBristol: Int? = null, initialFalseUrge: Boolean = false) {
        _stoolEditor.value = StoolEditorState(stool, initialBristol, initialFalseUrge)
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
        urgency: Boolean = false,
        nocturnal: Boolean = false,
        falseUrge: Boolean = false,
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
                    urgency = urgency,
                    nocturnal = nocturnal,
                    falseUrge = falseUrge,
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
        /** Name of the `day/{date}` route argument. */
        const val ARG_DATE = "date"
    }
}
