package fr.ibdjournal.app.ui.calendar

import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.Product
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class ZoomLevel(val labelRes: Int) {
    MOIS(fr.ibdjournal.app.R.string.zoom_month),
    SEMAINE(fr.ibdjournal.app.R.string.zoom_week),
    JOUR(fr.ibdjournal.app.R.string.zoom_day)
}

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val zoom: ZoomLevel = ZoomLevel.JOUR,
    val displayedMonth: YearMonth = YearMonth.now(),
    val displayedWeekStart: LocalDate = LocalDate.now(),
    val mealsByDay: Map<LocalDate, List<Meal>> = emptyMap(),
    val stoolsByDay: Map<LocalDate, List<Stool>> = emptyMap(),
    val showMealEditor: Boolean = false,
    val editorSessionId: Int = 0,
    val editingMeal: Meal? = null,
    val editorMealType: MealType = MealType.BREAKFAST,
    val editorDescription: String = "",
    val editorPhotoUri: Uri? = null,
    val editorRemovePhoto: Boolean = false,
    val showStoolSheet: Boolean = false,
    val editingStool: Stool? = null,
    val showScanner: Boolean = false,
    val pendingProduct: Product? = null,
    val onboardingDone: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val appPrefs: AppPrefs,
) : ViewModel() {

    private val selectedDate = MutableStateFlow<LocalDate?>(LocalDate.now())
    private val zoom = MutableStateFlow(ZoomLevel.JOUR)
    private val monthOffset = MutableStateFlow(0)
    private val weekOffset = MutableStateFlow(0)
    private val dayOffset = MutableStateFlow(0)

    private val showMealEditor = MutableStateFlow(false)
    private val editorSessionId = MutableStateFlow(0)
    private val editingMeal = MutableStateFlow<Meal?>(null)
    private val editorMealType = MutableStateFlow(MealType.BREAKFAST)
    private val editorDescription = MutableStateFlow("")
    private val editorPhotoUri = MutableStateFlow<Uri?>(null)
    private val editorRemovePhoto = MutableStateFlow(false)
    private val showStoolSheet = MutableStateFlow(false)
    private val editingStool = MutableStateFlow<Stool?>(null)
    private val showScanner = MutableStateFlow(false)
    private val pendingProduct = MutableStateFlow<Product?>(null)

    private val window: Flow<Pair<LocalDate, LocalDate>> = combine(zoom, monthOffset, weekOffset, dayOffset) { z, m, w, d ->
        when (z) {
            ZoomLevel.MOIS -> {
                val month = YearMonth.now().plusMonths(m.toLong())
                month.atDay(1) to month.atEndOfMonth()
            }
            ZoomLevel.SEMAINE -> {
                val start = LocalDate.now().plusWeeks(w.toLong()).with(java.time.DayOfWeek.MONDAY)
                start to start.plusDays(6)
            }
            ZoomLevel.JOUR -> {
                val day = LocalDate.now().plusDays(d.toLong())
                day to day
            }
        }
    }

    private val queryRange: Flow<Pair<LocalDate, LocalDate>> = window.map { (start, end) ->
        start.minusDays(7) to end.plusDays(7)
    }

    private val mealsFlow = queryRange.flatMapLatest { (start, end) ->
        repository.observeMeals(start, end)
    }

    private val stoolsFlow = queryRange.flatMapLatest { (start, end) ->
        repository.observeStools(start, end)
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        selectedDate, zoom, monthOffset, weekOffset,
        mealsFlow,
        stoolsFlow,
        showMealEditor, editingMeal, editorMealType, editorDescription, editorPhotoUri, editorRemovePhoto,
        showStoolSheet, editingStool, showScanner, pendingProduct,
        appPrefs.onboardingDone,
    ) { params ->
        val date = params[0] as LocalDate
        val z = params[1] as ZoomLevel
        val m = params[2] as Int
        val w = params[3] as Int
        val meals = params[4] as List<Meal>
        val stools = params[5] as List<Stool>
        
        CalendarUiState(
            selectedDate = date,
            zoom = z,
            displayedMonth = YearMonth.now().plusMonths(m.toLong()),
            displayedWeekStart = LocalDate.now().plusWeeks(w.toLong()).with(java.time.DayOfWeek.MONDAY),
            mealsByDay = meals.groupBy { it.date },
            stoolsByDay = stools.groupBy { it.date },
            showMealEditor = params[6] as Boolean,
            editingMeal = params[7] as Meal?,
            editorMealType = params[8] as MealType,
            editorDescription = params[9] as String,
            editorPhotoUri = params[10] as Uri?,
            editorRemovePhoto = params[11] as Boolean,
            showStoolSheet = params[12] as Boolean,
            editingStool = params[13] as Stool?,
            showScanner = params[14] as Boolean,
            pendingProduct = params[15] as Product?,
            onboardingDone = params[16] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    // --- Actions ---

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun setZoom(level: ZoomLevel) {
        zoom.value = level
    }

    fun navigateTo(direction: Int) {
        when (zoom.value) {
            ZoomLevel.MOIS -> monthOffset.value += direction
            ZoomLevel.SEMAINE -> weekOffset.value += direction
            ZoomLevel.JOUR -> {
                dayOffset.value += direction
                selectedDate.value = selectedDate.value?.plusDays(direction.toLong())
            }
        }
    }

    fun goToToday() {
        monthOffset.value = 0
        weekOffset.value = 0
        dayOffset.value = 0
        selectedDate.value = LocalDate.now()
    }

    // --- Repas ---

    fun openMealEditor(type: MealType, meal: Meal?) {
        editingMeal.value = meal
        editorMealType.value = type
        editorDescription.value = meal?.description ?: ""
        editorPhotoUri.value = meal?.photoUri?.let { Uri.parse(it) }
        editorRemovePhoto.value = false
        pendingProduct.value = meal?.product
        showMealEditor.value = true
    }

    fun closeMealEditor() {
        showMealEditor.value = false
    }

    fun saveMeal(
        description: String,
        photoUri: Uri?,
        removePhoto: Boolean,
        product: Product?
    ) {
        viewModelScope.launch {
            val existing = editingMeal.value
            val existingPath = existing?.photoUri
            
            val newPhotoUri = if (removePhoto) {
                if (existingPath != null) repository.deletePhoto(existingPath)
                null
            } else {
                photoUri?.toString() ?: existingPath
            }

            val meal = Meal(
                id = existing?.id ?: 0,
                date = selectedDate.value ?: LocalDate.now(),
                type = editorMealType.value,
                description = description,
                photoUri = newPhotoUri,
                product = product,
            )
            repository.saveMeal(meal)
            showMealEditor.value = false
        }
    }

    fun deleteMeal(meal: Meal) {
        viewModelScope.launch {
            repository.deleteMeal(meal)
            showMealEditor.value = false
        }
    }

    // --- Selles ---

    fun openStoolSheet(stool: Stool?) {
        editingStool.value = stool
        showStoolSheet.value = true
    }

    fun closeStoolSheet() {
        showStoolSheet.value = false
    }

    fun saveStool(
        bristolType: Int,
        bloodLevel: Int,
        mucusLevel: Int,
        painLevel: Int,
        fatigueLevel: Int,
        comment: String,
    ) {
        viewModelScope.launch {
            val existing = editingStool.value
            repository.saveStool(
                Stool(
                    id = existing?.id ?: 0,
                    date = selectedDate.value ?: LocalDate.now(),
                    bristolType = bristolType,
                    bloodLevel = bloodLevel,
                    mucusLevel = mucusLevel,
                    painLevel = painLevel,
                    fatigueLevel = fatigueLevel,
                    comment = comment,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
            )
            showStoolSheet.value = false
        }
    }

    fun deleteStool(stool: Stool) {
        viewModelScope.launch { repository.deleteStool(stool) }
    }

    // --- Scanner ---

    fun openScanner() {
        showScanner.value = true
    }

    fun closeScanner() {
        showScanner.value = false
    }

    fun onScannedProduct(product: Product) {
        pendingProduct.value = product
        showScanner.value = false
        // L'UI réagit via le state pendingProduct dans la sheet
    }

    // --- Callbacks éditeur ---

    fun onEditorDescriptionChanged(desc: String) {
        editorDescription.value = desc
    }

    fun onEditorPhotoUriChanged(uri: Uri?) {
        editorPhotoUri.value = uri
    }

    fun onEditorRemovePhotoChanged(remove: Boolean) {
        editorRemovePhoto.value = remove
    }

    fun onEditorProductChanged(product: Product?) {
        pendingProduct.value = product
    }

    fun onEditorTypeChanged(type: MealType) {
        editorMealType.value = type
    }

    // --- Bienvenue ---

    fun finishOnboarding() {
        viewModelScope.launch { appPrefs.markOnboardingDone() }
    }
}

@Preview(showBackground = true)
@Composable
private fun StoolSheetContentPreview() {
    IBDJournalTheme {
        Surface {
            StoolSheetContent(onSave = { _, _, _, _, _, _ -> })
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CalendarScreenPreview() {
    IBDJournalTheme {
        // Mock UI state for preview if needed
    }
}
