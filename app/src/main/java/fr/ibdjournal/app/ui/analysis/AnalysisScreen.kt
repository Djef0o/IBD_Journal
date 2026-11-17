package fr.ibdjournal.app.ui.analysis

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.domain.AnalysisPeriod
import fr.ibdjournal.app.domain.AnalysisSummary
import fr.ibdjournal.app.domain.Symptom
import fr.ibdjournal.app.domain.SymptomDays
import fr.ibdjournal.app.ui.chipRes
import fr.ibdjournal.app.ui.formatDecimal
import fr.ibdjournal.app.ui.labelRes
import fr.ibdjournal.app.ui.theme.bristolColor
import fr.ibdjournal.app.report.shareReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign

@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exportingReport by viewModel.exportingReport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Activity context: the share sheet opens on top of the app.
    LaunchedEffect(viewModel) {
        viewModel.reports.collect { result ->
            result
                .onSuccess { uri -> shareReport(context, uri) }
                .onFailure { error ->
                    Log.e("AnalysisScreen", "Report generation failed", error)
                    Toast.makeText(context, R.string.report_error, Toast.LENGTH_LONG).show()
                }
        }
    }
    AnalysisContent(
        state = state,
        onPeriodChange = viewModel::setPeriod,
        exportingReport = exportingReport,
        onExportReport = viewModel::exportReport,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisContent(
    state: AnalysisSummary,
    onPeriodChange: (AnalysisPeriod) -> Unit,
    exportingReport: Boolean,
    onExportReport: () -> Unit,
) {
    val stools = state.stools
    val daysFollowed = (stools.map { it.date } + state.meals.map { it.date }).distinct().size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(state.period.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.analysis_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            // Drives the tiles and the symptoms; the chart always covers the whole history.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AnalysisPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = state.period == period,
                        onClick = { onPeriodChange(period) },
                        shape = SegmentedButtonDefaults.itemShape(index, AnalysisPeriod.entries.size),
                    ) {
                        Text(stringResource(period.chipRes))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // PDF report of the chosen period, to share with the doctor.
            OutlinedButton(
                onClick = onExportReport,
                enabled = !exportingReport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (exportingReport) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (exportingReport) R.string.report_generating else R.string.report_action),
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    value = state.realStools.size.toString(),
                    label = stringResource(R.string.analysis_stools),
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    value = state.averageBristol?.let { formatDecimal(it) } ?: "—",
                    label = stringResource(R.string.analysis_avg),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    value = state.meals.size.toString(),
                    label = stringResource(R.string.analysis_meals),
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    value = daysFollowed.toString(),
                    label = stringResource(R.string.analysis_days),
                )
            }
            MetricTile(
                modifier = Modifier.fillMaxWidth(),
                value = state.liquidPerDay?.let { formatDecimal(it) } ?: "—",
                label = stringResource(R.string.analysis_liquid_per_day),
            )

            if (state.stoolHistory.isNotEmpty()) {
                DailyStoolChart(state.stoolHistory)
            }
            if (stools.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Text(
                        text = stringResource(R.string.analysis_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            } else {
                SymptomsCard(state)
            }
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
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
}

/** Days affected per symptom over the period, with the difference from the previous period. */
@Composable
private fun SymptomsCard(state: AnalysisSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.analysis_symptoms),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.period.days?.let { stringResource(R.string.analysis_symptoms_compare, it.toInt()) }
                    ?: stringResource(R.string.analysis_symptoms_days),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            state.symptomDays.forEach { row -> SymptomRow(row, state.periodDays) }
        }
    }
}

@Composable
private fun SymptomRow(row: SymptomDays, periodDays: Int) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(row.symptom.labelRes),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(R.plurals.analysis_symptom_days, row.days, row.days),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            row.previousDays?.let { previous ->
                val delta = row.days - previous
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        delta > 0 -> "↑ $delta"
                        delta < 0 -> "↓ ${-delta}"
                        else -> "="
                    },
                    style = MaterialTheme.typography.labelLarge,
                    // More days with symptoms = worsening.
                    color = when {
                        delta > 0 -> MaterialTheme.colorScheme.error
                        delta < 0 -> bristolColor(4)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((row.days.toFloat() / periodDays).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (row.symptom == Symptom.BLOOD) bristolColor(0) else MaterialTheme.colorScheme.primary
                    ),
            )
        }
    }
}
