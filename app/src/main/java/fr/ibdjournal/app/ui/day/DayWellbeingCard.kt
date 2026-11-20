package fr.ibdjournal.app.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.WELLBEING_MAX
import fr.ibdjournal.app.data.model.WELLBEING_MIN
import fr.ibdjournal.app.data.model.WellbeingDay
import kotlinx.datetime.LocalDate

/**
 * Sleep, stress and mood of [date] on 1 to 5 scales (tap the chosen value again to clear it), and
 * the menstrual period when the profile allows it. Saved as soon as it changes.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DayWellbeingCard(
    date: LocalDate,
    modifier: Modifier = Modifier,
    viewModel: WellbeingViewModel = hiltViewModel(),
) {
    val flow = remember(date, viewModel) { viewModel.on(date) }
    val day by flow.collectAsStateWithLifecycle(initialValue = WellbeingDay(date))
    val periodAvailable by viewModel.periodAvailable.collectAsStateWithLifecycle()

    val none = stringResource(R.string.day_card_none)
    val parts = listOfNotNull(
        day.sleep?.let { stringResource(R.string.wellbeing_sleep) + " " + it },
        day.stress?.let { stringResource(R.string.wellbeing_stress) + " " + it },
        day.mood?.let { stringResource(R.string.wellbeing_mood) + " " + it },
        if (day.period) stringResource(R.string.wellbeing_period) else null,
    )

    CollapsibleCard(
        key = "wellbeing-$date",
        title = stringResource(R.string.wellbeing_card_title),
        summary = if (parts.isEmpty()) none else parts.joinToString(" · "),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.wellbeing_card_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScaleRow(R.string.wellbeing_sleep, R.string.wellbeing_sleep_scale, day.sleep) {
            viewModel.save(day.copy(sleep = it))
        }
        ScaleRow(R.string.wellbeing_stress, R.string.wellbeing_stress_scale, day.stress) {
            viewModel.save(day.copy(stress = it))
        }
        ScaleRow(R.string.wellbeing_mood, R.string.wellbeing_mood_scale, day.mood) {
            viewModel.save(day.copy(mood = it))
        }
        if (periodAvailable || day.period) {
            FlowRow(modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(
                    selected = day.period,
                    onClick = { viewModel.save(day.copy(period = !day.period)) },
                    label = { Text(stringResource(R.string.wellbeing_period)) },
                )
            }
        }
    }
}

/** One 1 to 5 scale: label, the five values, then what the ends mean. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScaleRow(labelRes: Int, scaleRes: Int, value: Int?, onChange: (Int?) -> Unit) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (level in WELLBEING_MIN..WELLBEING_MAX) {
            FilterChip(
                selected = value == level,
                onClick = { onChange(if (value == level) null else level) },
                label = { Text(level.toString()) },
            )
        }
    }
    Text(
        text = stringResource(scaleRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
