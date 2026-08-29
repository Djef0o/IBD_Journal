package fr.ibdjournal.app.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.onboarding.OnboardingScreen
import fr.ibdjournal.app.ui.scanner.ScannerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showChooser by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = ZoomLevel.JOUR.ordinal) {
        ZoomLevel.entries.size
    }

    // Glissement dans le pager → changement de zoom (mois → semaine → jour).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.setZoom(ZoomLevel.entries[page])
        }
    }
    // Changement de zoom (boutons) → alignement du pager.
    LaunchedEffect(state.zoom) {
        if (pagerState.currentPage != state.zoom.ordinal) {
            pagerState.animateScrollToPage(state.zoom.ordinal)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(headerTitle(state)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(-1) }) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronLeft,
                            contentDescription = stringResource(R.string.previous),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.navigateTo(1) }) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = stringResource(R.string.next),
                        )
                    }
                    TextButton(onClick = viewModel::goToToday) {
                        Text(stringResource(R.string.today))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showChooser = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_meal)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ZoomSelector(state.zoom, viewModel::setZoom)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (ZoomLevel.entries[page]) {
                    ZoomLevel.MOIS -> MonthGrid(
                        month = state.displayedMonth,
                        selected = state.selectedDate,
                        mealsByDay = state.mealsByDay,
                        stoolsByDay = state.stoolsByDay,
                        onSelect = viewModel::selectDate,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    )

                    ZoomLevel.SEMAINE -> WeekRow(
                        weekStart = state.displayedWeekStart,
                        selected = state.selectedDate,
                        mealsByDay = state.mealsByDay,
                        stoolsByDay = state.stoolsByDay,
                        onSelect = viewModel::selectDate,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    )

                    ZoomLevel.JOUR -> DayPage(
                        date = state.selectedDate,
                        mealsByDay = state.mealsByDay,
                        stoolsByDay = state.stoolsByDay,
                        onOpenMeal = { type, meal -> viewModel.openMealEditor(type, meal) },
                        onOpenStoolSheet = viewModel::openStoolSheet,
                        onDeleteMeal = viewModel::deleteMeal,
                        onDeleteStool = viewModel::deleteStool,
                    )
                }
            }
            if (state.zoom != ZoomLevel.JOUR) {
                DaySummaryCard(
                    date = state.selectedDate,
                    meals = state.mealsByDay[state.selectedDate].orEmpty(),
                    stools = state.stoolsByDay[state.selectedDate].orEmpty(),
                    onOpenMeal = { type, meal -> viewModel.openMealEditor(type, meal) },
                    onOpenStoolSheet = viewModel::openStoolSheet,
                    onOpenDay = { viewModel.setZoom(ZoomLevel.JOUR) },
                )
            }
        }
    }

    // Menu d'ajout rapide (choix du type).
    if (showChooser) {
        ModalBottomSheet(onDismissRequest = { showChooser = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    text = stringResource(R.string.add_meal),
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                MealType.entries.forEach { type ->
                    ListItem(
                        headlineContent = { Text(stringResource(type.labelRes)) },
                        leadingContent = {
                            Icon(
                                imageVector = mealIcon(type),
                                contentDescription = null,
                                tint = mealColor(type),
                            )
                        },
                        modifier = Modifier.clickable {
                            showChooser = false
                            viewModel.openMealEditor(type)
                        },
                    )
                }
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.add_stool)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.WaterDrop,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.clickable {
                        showChooser = false
                        viewModel.openStoolSheet()
                    },
                )
            }
        }
    }

    // Éditeur de repas.
    if (state.showMealEditor) {
        MealEditorSheet(
            state = state,
            onDismiss = viewModel::closeMealEditor,
            onSave = viewModel::saveMeal,
            onDelete = viewModel::deleteMeal,
            onScan = viewModel::openScanner,
        )
    }

    // Ajout d'une selle.
    if (state.showStoolSheet) {
        StoolSheet(
            onDismiss = viewModel::closeStoolSheet,
            onSave = viewModel::addStool,
        )
    }

    // Scanner de code-barres plein écran.
    if (state.showScanner) {
        ScannerScreen(
            onClose = viewModel::closeScanner,
            onProductScanned = viewModel::onScannedProduct,
        )
    }

    // Écran de bienvenue au premier lancement.
    if (!state.onboardingDone) {
        OnboardingScreen(onFinish = viewModel::finishOnboarding)
    }
}

@Composable
private fun ZoomSelector(zoom: ZoomLevel, onZoomChange: (ZoomLevel) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ZoomLevel.entries.forEachIndexed { index, level ->
            SegmentedButton(
                selected = zoom == level,
                onClick = { onZoomChange(level) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ZoomLevel.entries.size),
                label = { Text(stringResource(level.labelRes)) },
            )
        }
    }
}

@Composable
private fun headerTitle(state: CalendarUiState): String = when (state.zoom) {
    ZoomLevel.MOIS -> DateFormats.monthYear(state.displayedMonth)
    ZoomLevel.SEMAINE -> DateFormats.weekRange(state.displayedWeekStart)
    ZoomLevel.JOUR -> DateFormats.fullDate(state.selectedDate)
}
