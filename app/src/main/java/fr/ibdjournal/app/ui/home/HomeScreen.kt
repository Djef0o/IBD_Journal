package fr.ibdjournal.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Flare
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.data.model.DoseStatus
import fr.ibdjournal.app.domain.DoseDue
import fr.ibdjournal.app.ui.calendar.BristolIcon
import fr.ibdjournal.app.ui.calendar.CalendarUiState
import fr.ibdjournal.app.ui.calendar.CalendarViewModel
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.calendar.DiaryEditors
import fr.ibdjournal.app.ui.calendar.stoolSummary
import fr.ibdjournal.app.ui.calendar.stoolTitle
import androidx.compose.material.icons.outlined.MedicalInformation
import fr.ibdjournal.app.ui.emergency.EmergencyChooserDialog
import fr.ibdjournal.app.ui.emergency.EmergencySheet
import fr.ibdjournal.app.ui.emergency.EmergencyViewModel
import fr.ibdjournal.app.ui.emergency.UrgentNeedCard
import fr.ibdjournal.app.ui.formatDecimal
import fr.ibdjournal.app.domain.localDateTimeAt
import fr.ibdjournal.app.domain.dayNumberOn
import fr.ibdjournal.app.domain.ongoingOn
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.labelRes
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Dashboard: day summary, quick actions, day feed and reminder. */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
    dosesViewModel: TodayDosesViewModel = hiltViewModel(),
    emergencyViewModel: EmergencyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val flares by viewModel.flares.collectAsStateWithLifecycle()
    val doses by dosesViewModel.doses.collectAsStateWithLifecycle()
    var showQuickStool by rememberSaveable { mutableStateOf(false) }
    var showMealTypes by rememberSaveable { mutableStateOf(false) }
    var showEmergencyChooser by rememberSaveable { mutableStateOf(false) }
    var showUrgentCard by rememberSaveable { mutableStateOf(false) }
    var showEmergencySheet by rememberSaveable { mutableStateOf(false) }
    val emergencyData by emergencyViewModel.data.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        flares = flares,
        onOpenSettings = onOpenSettings,
        onAddMeal = { showMealTypes = true },
        onAddStool = { showQuickStool = true },
        onEditMeal = { meal -> viewModel.openMealEditor(meal.type, meal) },
        onEditStool = { stool -> viewModel.openStoolSheet(stool) },
        doses = doses,
        onEmergency = { showEmergencyChooser = true },
        onDose = dosesViewModel::toggle,
    )

    if (showMealTypes) {
        MealTypeSheet(
            onDismiss = { showMealTypes = false },
            onPick = { type ->
                showMealTypes = false
                viewModel.openMealEditor(type, null)
            },
        )
    }

    if (showQuickStool) {
        QuickStoolSheet(
            onDismiss = { showQuickStool = false },
            onDetails = { type, falseUrge ->
                showQuickStool = false
                viewModel.openStoolSheet(null, initialBristol = type, initialFalseUrge = falseUrge)
            },
            onSave = { type, falseUrge ->
                showQuickStool = false
                viewModel.saveStool(type, 0, 0, 0, 0, "", falseUrge = falseUrge)
            },
        )
    }

    if (showEmergencyChooser) {
        EmergencyChooserDialog(
            onDismiss = { showEmergencyChooser = false },
            onUrgentCard = {
                showEmergencyChooser = false
                showUrgentCard = true
            },
            onSheet = {
                showEmergencyChooser = false
                showEmergencySheet = true
            },
        )
    }
    if (showUrgentCard) UrgentNeedCard(onDismiss = { showUrgentCard = false })
    if (showEmergencySheet) EmergencySheet(data = emergencyData, onDismiss = { showEmergencySheet = false })

    DiaryEditors(viewModel)
}

@Composable
fun HomeContent(
    state: CalendarUiState,
    onOpenSettings: () -> Unit,
    onAddMeal: () -> Unit,
    onAddStool: () -> Unit,
    onEditMeal: (Meal) -> Unit = {},
    onEditStool: (Stool) -> Unit = {},
    doses: List<DoseDue> = emptyList(),
    onDose: (DoseDue, DoseStatus) -> Unit = { _, _ -> },
    onEmergency: () -> Unit = {},
    flares: List<Flare> = emptyList(),
) {
    val today = today()
    val ongoingFlare = flares.ongoingOn(today)
    val meals = state.mealsByDay[today].orEmpty()
    val stools = state.stoolsByDay[today].orEmpty()

    val weekStart = today.minus(6, DateTimeUnit.DAY)
    val weekStools = (0..6).flatMap { state.stoolsByDay[weekStart.plus(it, DateTimeUnit.DAY)].orEmpty() }
    val weekMeals = (0..6).flatMap { state.mealsByDay[weekStart.plus(it, DateTimeUnit.DAY)].orEmpty() }
    val weekAverage = weekStools.filter { it.isRealStool }.map { it.bristolType }.average().takeIf { !it.isNaN() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        Header(onOpenSettings = onOpenSettings, onEmergency = onEmergency)

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (ongoingFlare != null) {
                FlareCard(flare = ongoingFlare, today = today)
            }

            SummaryCard(stools = stools, meals = meals)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.home_action_meal),
                    subtitle = stringResource(R.string.home_action_meal_sub),
                    icon = { Icon(Icons.Outlined.Restaurant, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    accent = MaterialTheme.colorScheme.tertiary,
                    container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                    onClick = onAddMeal,
                )
                ActionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.home_action_stool),
                    subtitle = stringResource(R.string.home_action_stool_sub),
                    icon = { Icon(Icons.Outlined.WaterDrop, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    accent = MaterialTheme.colorScheme.primary,
                    container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    onClick = onAddStool,
                )
            }

            if (doses.isNotEmpty()) {
                DosesCard(doses = doses, onDose = onDose)
            }

            Text(
                text = stringResource(R.string.home_feed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )

            if (meals.isEmpty() && stools.isEmpty()) {
                EmptyFeed()
            } else {
                Feed(meals = meals, stools = stools, onEditMeal = onEditMeal, onEditStool = onEditStool)
            }

            WeekCard(
                stools = weekStools,
                meals = weekMeals,
                average = weekAverage,
            )

            if (state.reminder.enabled) {
                ReminderCard(hour = state.reminder.hour, minute = state.reminder.minute)
            }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit, onEmergency: () -> Unit) {
    val date = DateFormats.weekdayDayMonth(today())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = date,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onEmergency) {
            Icon(
                Icons.Outlined.MedicalInformation,
                contentDescription = stringResource(R.string.emergency_chooser_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.nav_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Reminder that a flare is in progress; informational only. */
@Composable
private fun FlareCard(flare: Flare, today: LocalDate) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringResource(
                R.string.flaremark_home,
                DateFormats.dayMonthYear(flare.start),
                flare.dayNumberOn(today) ?: 1,
            ),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun SummaryCard(stools: List<Stool>, meals: List<Meal>) {
    val average = stools.filter { it.isRealStool }.map { it.bristolType }.average().takeIf { !it.isNaN() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_today).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val calm = stools.isNotEmpty() && stools.all {
                    it.bloodLevel == 0 && it.mucusLevel == 0 && it.painLevel == 0
                }
                if (calm) {
                    Pill(text = stringResource(R.string.home_calm))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val realCount = stools.count { it.isRealStool }
                Stat(
                    value = realCount.toString(),
                    label = pluralStringResource(R.plurals.stools_label, realCount, realCount),
                    modifier = Modifier.weight(1f),
                )
                VerticalSeparator()
                Stat(
                    value = meals.size.toString(),
                    label = pluralStringResource(R.plurals.meals_label, meals.size, meals.size),
                    modifier = Modifier.weight(1f),
                )
                VerticalSeparator()
                Stat(
                    value = average?.let { formatDecimal(it) } ?: "—",
                    label = stringResource(R.string.home_stat_bristol),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VerticalSeparator() {
    Box(
        Modifier
            .padding(horizontal = 10.dp)
            .width(1.dp)
            .height(34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun Pill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    accent: Color,
    container: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(118.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides accent
                ) { icon() }
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyFeed() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Text(
            text = stringResource(R.string.home_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp),
        )
    }
}

private sealed interface FeedEntry {
    val sortKey: Int

    data class MealEntry(val meal: Meal) : FeedEntry {
        override val sortKey: Int
            get() = when (meal.type) {
                MealType.BREAKFAST -> 8 * 60
                MealType.LUNCH -> 13 * 60
                MealType.DINNER -> 19 * 60 + 30
            }
    }

    data class StoolEntry(val stool: Stool) : FeedEntry {
        override val sortKey: Int
            get() {
                val time = localDateTimeAt(stool.createdAt)
                return time.hour * 60 + time.minute
            }
    }
}

@Composable
private fun Feed(
    meals: List<Meal>,
    stools: List<Stool>,
    onEditMeal: (Meal) -> Unit,
    onEditStool: (Stool) -> Unit,
) {
    val entries: List<FeedEntry> =
        (meals.map { FeedEntry.MealEntry(it) } + stools.map { FeedEntry.StoolEntry(it) })
            .sortedBy { it.sortKey }

    Box {
        if (entries.size > 1) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(start = 9.dp, top = 18.dp, bottom = 46.dp),
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Column {
            entries.forEach { entry ->
                FeedRow(
                    entry = entry,
                    onClick = {
                        when (entry) {
                            is FeedEntry.MealEntry -> onEditMeal(entry.meal)
                            is FeedEntry.StoolEntry -> onEditStool(entry.stool)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FeedRow(entry: FeedEntry, onClick: () -> Unit) {
    val dotColor = when (entry) {
        is FeedEntry.MealEntry -> MaterialTheme.colorScheme.tertiary
        is FeedEntry.StoolEntry -> MaterialTheme.colorScheme.primary
    }

    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(bottom = 18.dp)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (entry) {
                        is FeedEntry.MealEntry -> stringResource(entry.meal.type.labelRes)
                        is FeedEntry.StoolEntry -> stoolTitle(entry.stool)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val subtitle = when (entry) {
                    is FeedEntry.MealEntry -> entry.meal.description.ifBlank { "—" }
                    is FeedEntry.StoolEntry -> stoolSummary(entry.stool)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (entry is FeedEntry.StoolEntry) {
                Text(
                    text = DateFormats.time(entry.stool.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when (entry) {
                    is FeedEntry.MealEntry -> Icon(
                        Icons.Outlined.Restaurant,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    is FeedEntry.StoolEntry -> BristolIcon(entry.stool.bristolType, Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.edit),
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun WeekCard(stools: List<Stool>, meals: List<Meal>, average: Double?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.home_week).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Days without a stool (type 0) and false urges are not stools.
                val realCount = stools.count { it.isRealStool }
                Stat(
                    value = realCount.toString(),
                    label = pluralStringResource(R.plurals.stools_label, realCount, realCount),
                    modifier = Modifier.weight(1f),
                )
                VerticalSeparator()
                Stat(
                    value = average?.let { formatDecimal(it) } ?: "—",
                    label = stringResource(R.string.home_week_avg),
                    modifier = Modifier.weight(1f),
                )
                VerticalSeparator()
                Stat(
                    value = meals.size.toString(),
                    label = stringResource(R.string.home_week_meals),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReminderCard(hour: Int, minute: Int) {
    val time = DateFormats.time(hour, minute)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.home_reminder, time),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.home_reminder_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Today's doses: quick confirmation, tapping the active status again cancels it. */
@Composable
private fun DosesCard(doses: List<DoseDue>, onDose: (DoseDue, DoseStatus) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.home_doses_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            doses.forEach { dose ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = listOf(dose.medication.name, dose.medication.dosage)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = DateFormats.time(dose.at.hour, dose.at.minute),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = dose.status == DoseStatus.TAKEN,
                        onClick = { onDose(dose, DoseStatus.TAKEN) },
                        label = { Text(stringResource(R.string.dose_taken)) },
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = dose.status == DoseStatus.MISSED,
                        onClick = { onDose(dose, DoseStatus.MISSED) },
                        label = { Text(stringResource(R.string.dose_missed)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                }
            }
        }
    }
}
