package fr.ibdjournal.app.ui.calendar

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.AppPrefs
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.Product
import fr.ibdjournal.app.data.model.Stool
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

/**
 * Niveau de zoom du calendrier.
 * L'ordre définit l'ordre des pages du pager : glisser à gauche zoome (mois → semaine → jour).
 */
enum class ZoomLevel(@StringRes val labelRes: Int) {
    MOIS(R.string.zoom_month),
    SEMAINE(R.string.zoom_week),
    JOUR(R.string.zoom_day),
}

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val zoom: ZoomLevel = ZoomLevel.JOUR,
    val displayedMonth: YearMonth = YearMonth.now(),
    val displayedWeekStart: LocalDate = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val mealsByDay: Map<LocalDate, List<Meal>> = emptyMap(),
    val stoolsByDay: Map<LocalDate, List<Stool>> = emptyMap(),
    val showMealEditor: Boolean = false,
    val editorSessionId: Int = 0,
    val editingMeal: Meal? = null,
    val editorMealType: MealType = MealType.BREAKFAST,
    val showStoolSheet: Boolean = false,
    val showScanner: Boolean = false,
    val pendingProduct: Product? = null,
    val onboardingDone: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val appPrefs: AppPrefs,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val zoom = MutableStateFlow(ZoomLevel.JOUR)
    private val monthOffset = MutableStateFlow(0)
    private val weekOffset = MutableStateFlow(0)
    private val dayOffset = MutableStateFlow(0)

    private val showMealEditor = MutableStateFlow(false)
    private val editorSessionId = MutableStateFlow(0)
    private val editingMeal = MutableStateFlow<Meal?>(null)
    private val editorMealType = MutableStateFlow(MealType.BREAKFAST)
    private val showStoolSheet = MutableStateFlow(false)
    private val showScanner = MutableStateFlow(false)
    private val pendingProduct = MutableStateFlow<Product?>(null)

    // --- Regroupements intermédiaires (combine ne prend que 5 flux typés) ---

    private data class NavState(
        val selectedDate: LocalDate,
        val zoom: ZoomLevel,
        val monthOffset: Int,
        val weekOffset: Int,
    )

    private data class EditorState(
        val showMealEditor: Boolean,
        val editorSessionId: Int,
        val editingMeal: Meal?,
        val editorMealType: MealType,
        val showStoolSheet: Boolean,
    )

    private data class ScannerState(
        val showScanner: Boolean,
        val pendingProduct: Product?,
    )

    private data class SheetState(
        val showMealEditor: Boolean,
        val editorSessionId: Int,
        val editingMeal: Meal?,
        val editorMealType: MealType,
        val showStoolSheet: Boolean,
        val showScanner: Boolean,
        val pendingProduct: Product?,
    )

    /** Fenêtre affichée par la vue courante (mois, semaine ou jour). */
    private val window: Flow<Pair<LocalDate, LocalDate>> =
        combine(zoom, monthOffset, weekOffset, dayOffset) { z, mo, wo, doff ->
            when (z) {
                ZoomLevel.MOIS -> YearMonth.now().plusMonths(mo.toLong())
                    .let { it.atDay(1) to it.atEndOfMonth() }

                ZoomLevel.SEMAINE -> {
                    val start = LocalDate.now()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .plusWeeks(wo.toLong())
                    start to start.plusDays(6)
                }

                ZoomLevel.JOUR -> LocalDate.now().plusDays(doff.toLong()).let { it to it }
            }
        }.distinctUntilChanged()

    /** Fenêtre de requête : la vue courante, étendue à la date sélectionnée. */
    private val queryRange: Flow<Pair<LocalDate, LocalDate>> =
        combine(selectedDate, window) { sel, (from, to) -> minOf(sel, from) to maxOf(sel, to) }
            .distinctUntilChanged()

    private val navState: Flow<NavState> =
        combine(selectedDate, zoom, monthOffset, weekOffset) { sel, z, mo, wo ->
            NavState(sel, z, mo, wo)
        }

    private val editorState: Flow<EditorState> =
        combine(showMealEditor, editorSessionId, editingMeal, editorMealType, showStoolSheet) {
                showEditor, session, edMeal, edType, stoolSheet ->
            EditorState(showEditor, session, edMeal, edType, stoolSheet)
        }

    private val scannerState: Flow<ScannerState> =
        combine(showScanner, pendingProduct) { scanner, product ->
            ScannerState(scanner, product)
        }

    private val sheetState: Flow<SheetState> =
        combine(editorState, scannerState) { editor, scanner ->
            SheetState(
                showMealEditor = editor.showMealEditor,
                editorSessionId = editor.editorSessionId,
                editingMeal = editor.editingMeal,
                editorMealType = editor.editorMealType,
                showStoolSheet = editor.showStoolSheet,
                showScanner = scanner.showScanner,
                pendingProduct = scanner.pendingProduct,
            )
        }

    val uiState: StateFlow<CalendarUiState> = combine(
        navState,
        queryRange.flatMapLatest { (from, to) -> repository.observeMeals(from, to) },
        queryRange.flatMapLatest { (from, to) -> repository.observeStools(from, to) },
        sheetState,
        appPrefs.onboardingDone,
    ) { nav, meals, stools, sheet, onboarding ->
        CalendarUiState(
            selectedDate = nav.selectedDate,
            zoom = nav.zoom,
            displayedMonth = YearMonth.now().plusMonths(nav.monthOffset.toLong()),
            displayedWeekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(nav.weekOffset.toLong()),
            mealsByDay = meals.groupBy { it.date },
            stoolsByDay = stools.groupBy { it.date },
            showMealEditor = sheet.showMealEditor,
            editorSessionId = sheet.editorSessionId,
            editingMeal = sheet.editingMeal,
            editorMealType = sheet.editorMealType,
            showStoolSheet = sheet.showStoolSheet,
            showScanner = sheet.showScanner,
            pendingProduct = sheet.pendingProduct,
            onboardingDone = onboarding,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    // --- Navigation dans le calendrier ---

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun setZoom(level: ZoomLevel) {
        zoom.value = level
    }

    /** -1 = précédent, +1 = suivant (mois, semaine ou jour selon la vue). */
    fun navigateTo(direction: Int) {
        when (zoom.value) {
            ZoomLevel.MOIS -> monthOffset.update { it + direction }
            ZoomLevel.SEMAINE -> weekOffset.update { it + direction }
            ZoomLevel.JOUR -> {
                dayOffset.update { it + direction }
                selectedDate.value = LocalDate.now().plusDays(dayOffset.value.toLong())
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

    fun openMealEditor(type: MealType, meal: Meal? = null) {
        editorMealType.value = type
        editingMeal.value = meal
        pendingProduct.value = null
        editorSessionId.update { it + 1 }
        showMealEditor.value = true
    }

    fun closeMealEditor() {
        showMealEditor.value = false
        editingMeal.value = null
    }

    fun saveMeal(
        description: String,
        photoUri: Uri?,
        removePhoto: Boolean,
        product: Product?,
    ) {
        val editing = editingMeal.value
        val type = editorMealType.value
        viewModelScope.launch {
            val existingPath = editing?.photoPath
            val newPath = when {
                removePhoto -> null
                photoUri != null -> repository.importPhoto(photoUri)
                else -> existingPath
            }
            repository.saveMeal(
                Meal(
                    id = editing?.id ?: 0,
                    date = editing?.date ?: selectedDate.value,
                    type = type,
                    description = description.trim(),
                    photoPath = newPath,
                    productBarcode = product?.barcode ?: editing?.productBarcode,
                    productName = product?.name ?: editing?.productName,
                )
            )
            if (removePhoto && existingPath != null) repository.deletePhoto(existingPath)
            closeMealEditor()
        }
    }

    fun deleteMeal(meal: Meal) {
        viewModelScope.launch {
            repository.deleteMeal(meal)
            if (editingMeal.value?.id == meal.id) closeMealEditor()
        }
    }

    // --- Selles ---

    fun openStoolSheet() {
        showStoolSheet.value = true
    }

    fun closeStoolSheet() {
        showStoolSheet.value = false
    }

    fun addStool(comment: String) {
        viewModelScope.launch {
            repository.addStool(selectedDate.value, comment)
            showStoolSheet.value = false
        }
    }

    fun deleteStool(stool: Stool) {
        viewModelScope.launch { repository.deleteStool(stool) }
    }

    // --- Scanner de code-barres ---

    fun openScanner() {
        pendingProduct.value = null
        showScanner.value = true
    }

    fun closeScanner() {
        showScanner.value = false
    }

    /** Un produit a été scanné : on le transmet à l'éditeur de repas ouvert. */
    fun onScannedProduct(product: Product) {
        pendingProduct.value = product
        showScanner.value = false
    }

    // --- Bienvenue ---

    fun finishOnboarding() {
        viewModelScope.launch { appPrefs.markOnboardingDone() }
    }
}
