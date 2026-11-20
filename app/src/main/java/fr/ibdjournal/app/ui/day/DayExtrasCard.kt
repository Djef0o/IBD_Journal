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
import fr.ibdjournal.app.data.model.ExtraSymptom
import kotlinx.datetime.LocalDate

/** Extra-intestinal symptoms of [date]: one chip per manifestation, saved as soon as it is toggled. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DayExtrasCard(
    date: LocalDate,
    modifier: Modifier = Modifier,
    viewModel: ExtraSymptomsViewModel = hiltViewModel(),
) {
    val flow = remember(date, viewModel) { viewModel.on(date) }
    val selected by flow.collectAsStateWithLifecycle(initialValue = emptySet())

    val none = stringResource(R.string.day_card_none)
    val names = ExtraSymptom.entries.map { it to stringResource(it.labelRes()) }
    val summary = names.filter { it.first in selected }.joinToString(", ") { it.second }.ifEmpty { none }

    CollapsibleCard(
        key = "extras-$date",
        title = stringResource(R.string.extra_card_title),
        summary = summary,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.extra_card_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ExtraSymptom.entries.forEach { symptom ->
                val isOn = symptom in selected
                FilterChip(
                    selected = isOn,
                    onClick = { viewModel.save(date, if (isOn) selected - symptom else selected + symptom) },
                    label = { Text(stringResource(symptom.labelRes())) },
                )
            }
        }
    }
}
