package fr.ibdjournal.app.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Flare
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.calendar.BristolIcon
import fr.ibdjournal.app.ui.calendar.CalendarViewModel
import fr.ibdjournal.app.ui.calendar.DiaryEditors
import fr.ibdjournal.app.ui.calendar.stoolSummary
import fr.ibdjournal.app.ui.calendar.stoolTitle
import fr.ibdjournal.app.ui.formatDecimal
import fr.ibdjournal.app.ui.home.MealTypeSheet
import fr.ibdjournal.app.domain.activeOn
import fr.ibdjournal.app.domain.dayNumberOn
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.labelRes
import kotlinx.datetime.LocalDate

/**
 * Detail of a day: meals and stools, with add, edit and delete.
 * Replaces the former "detailed view" (month/week/day) with a single screen
 * consistent with the rest of the application.
 */
@Composable
fun DayScreen(
    onBack: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val flares by viewModel.flares.collectAsStateWithLifecycle()
    var showMealTypes by rememberSaveable { mutableStateOf(false) }

    val selected = state.selectedDate
    val meals = state.mealsByDay[selected].orEmpty()
    val stools = state.stoolsByDay[selected].orEmpty()
    val flare = flares.activeOn(selected)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        Header(
            date = selected,
            onBack = onBack,
            onPrevious = { viewModel.shiftDay(-1) },
            onNext = { viewModel.shiftDay(1) },
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (selected != today()) {
                TextButton(
                    onClick = viewModel::goToToday,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.today))
                }
            }

            if (flare != null) {
                FlareChip(flare = flare, date = selected)
            }

            SummaryCard(
                stools = stools,
                meals = meals,
            )

            if (selected <= today()) {
                DayExtrasCard(date = selected)
                DayWellbeingCard(date = selected)
            }

            SectionHeader(
                title = stringResource(R.string.meals_section),
                addLabel = stringResource(R.string.add),
                onAdd = { showMealTypes = true },
            )
            if (meals.isEmpty()) {
                EmptyCard()
            } else {
                ListCard {
                    meals.forEachIndexed { index, meal ->
                        MealRow(
                            meal = meal,
                            onClick = { viewModel.openMealEditor(meal.type, meal) },
                            onDelete = { viewModel.deleteMeal(meal) },
                        )
                        if (index != meals.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 60.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }

            SectionHeader(
                title = stringResource(R.string.stools_section),
                addLabel = stringResource(R.string.add),
                onAdd = { viewModel.openStoolSheet(null) },
            )
            if (stools.isEmpty()) {
                EmptyCard()
            } else {
                ListCard {
                    stools.forEachIndexed { index, stool ->
                        StoolRow(
                            stool = stool,
                            onClick = { viewModel.openStoolSheet(stool) },
                            onDelete = { viewModel.deleteStool(stool) },
                        )
                        if (index != stools.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 60.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMealTypes) {
        MealTypeSheet(
            onDismiss = { showMealTypes = false },
            onPick = { type ->
                showMealTypes = false
                viewModel.openMealEditor(type, null)
            },
        )
    }

    DiaryEditors(viewModel)
}

/** Banner shown when the day falls within a flare: day number since the start, hospitalization. */
@Composable
private fun FlareChip(flare: Flare, date: LocalDate) {
    val text = buildList {
        add(
            flare.dayNumberOn(date)?.let { stringResource(R.string.flaremark_day, it) }
                ?: stringResource(R.string.flaremark_chart_legend),
        )
        if (flare.hospitalized) add(stringResource(R.string.flaremark_hospitalized))
    }.joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Header(
    date: LocalDate,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.day_journal),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = DateFormats.weekdayDayMonth(date),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Outlined.ChevronLeft,
                contentDescription = stringResource(R.string.previous),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.next),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(stools: List<Stool>, meals: List<Meal>) {
    val average = stools.filter { it.isRealStool }.map { it.bristolType }.average().takeIf { !it.isNaN() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DayStat(
                value = stools.count { it.isRealStool }.toString(),
                label = stringResource(R.string.stools_section),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            DayStat(
                value = meals.size.toString(),
                label = stringResource(R.string.meals_section),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            DayStat(
                value = average?.let { formatDecimal(it) } ?: "—",
                label = stringResource(R.string.home_stat_bristol),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            )
        }
    }
}

@Composable
private fun DayStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String, addLabel: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(addLabel)
        }
    }
}

@Composable
private fun ListCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column { content() }
    }
}

@Composable
private fun EmptyCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Text(
            text = stringResource(R.string.empty_day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun MealRow(meal: Meal, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(meal.type.labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = meal.description.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun StoolRow(stool: Stool, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            BristolIcon(stool.bristolType, Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stoolTitle(stool),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = DateFormats.time(stool.createdAt) + " · " + stoolSummary(stool),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
