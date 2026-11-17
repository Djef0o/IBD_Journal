package fr.ibdjournal.app.ui.analysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.theme.bristolColor
import fr.ibdjournal.app.domain.today
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Minimum days displayed, even with a shorter history. */
private const val MIN_DAYS = 14L
private val BAR_WIDTH = 20.dp
private val BAR_SPACING = 6.dp
private val BAR_AREA_HEIGHT = 132.dp
private val BLOOD_DOT = 7.dp

/** Categories stacked from bottom to top; type 0 ("no stool") is not a stool. */
private enum class StoolCategory(val types: IntRange, val legendRes: Int) {
    HARD(1..2, R.string.analysis_legend_hard),
    NORMAL(3..4, R.string.analysis_legend_normal),
    LOOSE(5..7, R.string.analysis_legend_loose);

    val color: Color get() = bristolColor(types.first)
}

private class DayBar(val date: LocalDate, val stools: List<Stool>) {
    val counts: Map<StoolCategory, Int> = StoolCategory.entries.associateWith { category ->
        stools.count { it.bristolType in category.types }
    }
    val total: Int = counts.values.sum()
    val blood: Boolean = stools.any { it.bloodLevel > 0 }
}

/**
 * Stools per day over the whole history: stacked bars by Bristol category, red dot
 * if blood was reported. The chart opens on today and scrolls into the past;
 * tapping a bar shows the day's detail. The scale is that of the entire history,
 * so that a bar's height does not change while scrolling.
 */
@Composable
fun DailyStoolChart(stools: List<Stool>, today: LocalDate = today()) {
    // From most recent to oldest: the reversed LazyRow starts on the right, on today.
    val days = remember(stools, today) {
        val byDate = stools.groupBy { it.date }
        val first = minOf(stools.minOfOrNull { it.date } ?: today, today.minus(MIN_DAYS - 1, DateTimeUnit.DAY))
        generateSequence(today) { it.minus(1, DateTimeUnit.DAY) }
            .takeWhile { it >= first }
            .map { DayBar(it, byDate[it].orEmpty()) }
            .toList()
    }
    val max = remember(days) { days.maxOf { it.total }.coerceAtLeast(1) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val listState = rememberLazyListState()
    // Months of the visible bars, recomputed while scrolling.
    val visibleMonths by remember(days) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            val newest = days[visible.minOfOrNull { it.index } ?: 0].date
            val oldest = days[(visible.maxOfOrNull { it.index } ?: 0).coerceAtMost(days.lastIndex)].date
            DateFormats.monthRange(oldest, newest)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.analysis_daily),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.analysis_daily_period),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = visibleMonths,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Top) {
                // Scale marker: maximum value at the top, 0 at the bottom.
                Column(
                    modifier = Modifier
                        .padding(top = BLOOD_DOT + 6.dp, end = 6.dp)
                        .height(BAR_AREA_HEIGHT),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    AxisLabel(max.toString())
                    AxisLabel("0")
                }
                LazyRow(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    reverseLayout = true,
                    horizontalArrangement = Arrangement.spacedBy(BAR_SPACING),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    items(days, key = { it.date.toEpochDays() }) { day ->
                        DayColumn(
                            day = day,
                            max = max,
                            selected = day.date == selected,
                            dimmed = selected != null && day.date != selected,
                            onClick = { selected = day.date.takeUnless { it == selected } },
                            showMonth = day.date.day == 1,
                            modifier = Modifier.width(BAR_WIDTH),
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))

            val detail = selected?.let { date -> days.first { it.date == date } }
            if (detail == null) {
                Text(
                    text = stringResource(R.string.analysis_daily_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DayDetail(detail)
            }

            Spacer(Modifier.height(12.dp))
            Legend()
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DayColumn(
    day: DayBar,
    max: Int,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    showMonth: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = if (dimmed) 0.35f else 1f
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(BLOOD_DOT), contentAlignment = Alignment.Center) {
            if (day.blood) {
                Box(
                    Modifier
                        .size(BLOOD_DOT)
                        .clip(CircleShape)
                        .background(bristolColor(0).copy(alpha = alpha)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_AREA_HEIGHT),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (day.total > 0) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(BAR_AREA_HEIGHT * (day.total.toFloat() / max))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                ) {
                    // From top to bottom: soft, normal, hard.
                    StoolCategory.entries.reversed().forEach { category ->
                        val count = day.counts.getValue(category)
                        if (count > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(count.toFloat())
                                    .background(category.color.copy(alpha = alpha)),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = DateFormats.weekdayNarrow(day.date.dayOfWeek),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = day.date.day.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Month marker on the 1st of the month (the header always shows the visible month).
        Text(
            text = if (showMonth) DateFormats.monthShort(day.date) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.wrapContentWidth(unbounded = true),
        )
    }
}

@Composable
private fun DayDetail(day: DayBar) {
    val real = day.stools.filter { it.isRealStool }
    Column {
        Text(
            text = DateFormats.weekdayDayMonth(day.date),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        val summary = buildList {
            add(
                if (real.isEmpty()) {
                    stringResource(R.string.history_no_entry)
                } else {
                    pluralStringResource(R.plurals.stools_count, real.size, real.size)
                }
            )
            if (real.isNotEmpty()) {
                add(stringResource(R.string.analysis_detail_types, real.joinToString(", ") { it.bristolType.toString() }))
            }
            // Number of false urges of the day: a sign of flare-up, outside the stool count.
            val falseUrges = day.stools.count { it.falseUrge }
            if (falseUrges > 0) add(pluralStringResource(R.plurals.false_urge_count, falseUrges, falseUrges))
            if (day.blood) add(stringResource(R.string.stool_flag_blood))
        }
        Text(
            text = summary.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StoolCategory.entries.forEach { category ->
            LegendItem(category.color, stringResource(category.legendRes), square = true)
        }
        LegendItem(bristolColor(0), stringResource(R.string.analysis_legend_blood), square = false)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, square: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(if (square) RoundedCornerShape(2.dp) else CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
