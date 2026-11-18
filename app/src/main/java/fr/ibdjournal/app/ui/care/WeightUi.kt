package fr.ibdjournal.app.ui.care

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Weight
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.formatDecimal
import fr.ibdjournal.app.ui.settings.settingsRowColors
import java.util.Locale
import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/** Accepted entry range, in kg. */
private val WEIGHT_RANGE = 1.0..400.0

/** Comma or dot, whatever the keyboard; null outside [WEIGHT_RANGE]. */
private fun parseWeight(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in WEIGHT_RANGE }

private fun Weight.display(): String = "${formatDecimal(kg)} kg"

/** Most recent first; on equal dates, the last entered. */
private val newestWeightFirst: Comparator<Weight> =
    compareByDescending<Weight> { it.date }.thenByDescending { it.id }

/**
 * Care section card: latest weight, change since the previous weighing and the curve over
 * the chosen window. [weights] chronological, non-empty.
 */
@Composable
internal fun WeightCard(weights: List<Weight>, onOpenHistory: () -> Unit) {
    val latest = weights.last()
    val previous = weights.getOrNull(weights.lastIndex - 1)
    val byPeriod = remember(weights) {
        val today = today()
        ChartPeriod.entries.associateWith { period ->
            val months = period.months
            if (months == null) weights
            else today.minus(months, DateTimeUnit.MONTH).let { since -> weights.filter { it.date >= since } }
        }
    }
    var period by rememberSaveable { mutableStateOf(ChartPeriod.SIX_MONTHS) }
    // No weighing in the chosen window: whole history.
    val shown = if (byPeriod.getValue(period).isEmpty()) ChartPeriod.ALL else period

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(latest.display(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(R.string.weight_latest, DateFormats.dayMonthYear(latest.date)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (previous != null) {
                val delta = latest.kg - previous.kg
                val since = DateFormats.dayMonthYear(previous.date)
                Text(
                    // Rounded like the displayed weights: "+0,0" would read as a change.
                    text = if (abs(delta) < 0.05) stringResource(R.string.weight_unchanged, since)
                    else stringResource(R.string.weight_change, String.format(Locale.getDefault(), "%+.1f", delta), since),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartPeriod.entries.forEach { option ->
                    FilterChip(
                        selected = shown == option,
                        onClick = { period = option },
                        enabled = byPeriod.getValue(option).isNotEmpty(),
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
            WeightChart(points = byPeriod.getValue(shown))
            TextButton(onClick = onOpenHistory, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.weight_history))
            }
        }
    }
}

/**
 * Weight curve, x-positions proportional to elapsed time; the vertical axis spans the
 * window's lowest and highest weights. [points] chronological, non-empty.
 */
@Composable
private fun WeightChart(points: List<Weight>) {
    val min = points.minOf { it.kg }
    val max = points.maxOf { it.kg }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.weight_chart_description)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(160.dp)) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = if (min == max) Arrangement.Center else Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(formatDecimal(max), style = labelStyle, color = labelColor)
                if (min != max) Text(formatDecimal(min), style = labelStyle, color = labelColor)
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = description }
            ) {
                val pad = 8.dp.toPx()
                val width = size.width - 2 * pad
                val height = size.height - 2 * pad

                fun x(index: Int): Float = pad + width * timeFraction(points, index) { it.date }
                fun y(kg: Double): Float =
                    if (max == min) size.height / 2
                    else pad + height * ((max - kg) / (max - min)).toFloat()

                // Minimum and maximum markers, in subtle dotted lines.
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                listOf(y(max), y(min)).distinct().forEach { lineY ->
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, lineY),
                        end = Offset(size.width, lineY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                }
                if (points.size > 1) {
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        if (index == 0) path.moveTo(x(index), y(point.kg))
                        else path.lineTo(x(index), y(point.kg))
                    }
                    drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }
                val radius = 4.dp.toPx()
                points.forEachIndexed { index, point ->
                    drawCircle(lineColor, radius = radius, center = Offset(x(index), y(point.kg)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val first = points.first().date
            val last = points.last().date
            if (first == last) {
                Text(
                    DateFormats.dayMonthYear(first),
                    style = labelStyle,
                    color = labelColor,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(DateFormats.dayMonthYear(first), style = labelStyle, color = labelColor)
                Spacer(Modifier.weight(1f))
                Text(DateFormats.dayMonthYear(last), style = labelStyle, color = labelColor)
            }
        }
    }
}

/** All weighings, most recent first; a tap opens the entry. */
@Composable
internal fun WeightHistorySheet(
    weights: List<Weight>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Weight) -> Unit,
) {
    val newestFirst = remember(weights) { weights.sortedWith(newestWeightFirst) }
    LabSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.weight_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Column {
            newestFirst.forEach { weight ->
                ListItem(
                    headlineContent = { Text(weight.display()) },
                    supportingContent = { Text(DateFormats.dayMonthYear(weight.date)) },
                    modifier = Modifier.clickable { onEdit(weight) },
                    colors = settingsRowColors(),
                )
            }
        }
        FilledTonalButton(onClick = onAdd, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add))
        }
    }
}

/** Weight entry: [initial] null = new weighing, dated today by default. */
@Composable
internal fun WeightEditorSheet(
    initial: Weight?,
    onDismiss: () -> Unit,
    onSave: (Weight) -> Unit,
    onDelete: (Weight) -> Unit,
) {
    var kgText by remember { mutableStateOf(initial?.let { formatDecimal(it.kg) }.orEmpty()) }
    var date by remember { mutableStateOf(initial?.date ?: today()) }
    var pickDate by remember { mutableStateOf(false) }
    val kg = parseWeight(kgText)
    val invalid = kgText.isNotBlank() && kg == null

    LabSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(if (initial == null) R.string.weight_add else R.string.weight_edit),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = kgText,
            onValueChange = { kgText = it },
            label = { Text(stringResource(R.string.weight_title)) },
            suffix = { Text("kg") },
            isError = invalid,
            supportingText = if (invalid) {
                { Text(stringResource(R.string.weight_value_invalid)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.weight_date), style = MaterialTheme.typography.bodyLarge)
                Text(
                    DateFormats.dayMonthYear(date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.modify)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (initial != null) {
                TextButton(onClick = { onDelete(initial); onDismiss() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (kg != null) onSave((initial ?: Weight(date = date, kg = kg)).copy(date = date, kg = kg))
                    onDismiss()
                },
                enabled = kg != null,
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (pickDate) {
        DatePickerDialogFor(
            initial = date,
            maxDate = today(),
            onDismiss = { pickDate = false },
            onPick = { date = it },
        )
    }
}
