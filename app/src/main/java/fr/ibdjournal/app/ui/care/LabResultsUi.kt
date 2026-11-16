package fr.ibdjournal.app.ui.care

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.LabCategory
import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.data.model.Sex
import fr.ibdjournal.app.domain.LabStatus
import fr.ibdjournal.app.domain.ReferenceRange
import fr.ibdjournal.app.domain.reference
import fr.ibdjournal.app.domain.status
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.display
import fr.ibdjournal.app.ui.displayValue
import fr.ibdjournal.app.ui.formatLabValue
import fr.ibdjournal.app.ui.labelRes
import fr.ibdjournal.app.ui.settings.settingsRowColors
import fr.ibdjournal.app.ui.theme.bristolColor
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toJavaLocalDate

/** Saisie en cours : [result] null = nouveau résultat, [test] = analyse présélectionnée. */
internal data class LabEditing(val result: LabResult?, val test: LabTest)

/** Plus récent d'abord ; à date égale, le dernier saisi. */
internal val newestFirst: Comparator<LabResult> =
    compareByDescending<LabResult> { it.date }.thenByDescending { it.id }

/** Historique par analyse, du plus récent au plus ancien, dans l'ordre de l'énumération. */
internal fun labHistories(results: List<LabResult>): Map<LabTest, List<LabResult>> =
    results.groupBy { it.test }
        .mapValues { (_, history) -> history.sortedWith(newestFirst) }
        .toSortedMap()

/** Accepte la virgule comme le point, quel que soit le clavier ; null si pas un nombre positif. */
internal fun parseLabValue(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

/** Couleur d'un résultat : accent du thème dans la norme, erreur sinon. */
@Composable
@ReadOnlyComposable
private fun labStatusColor(status: LabStatus): Color =
    if (status == LabStatus.NORMAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

@StringRes
private fun LabStatus.labelRes(): Int? = when (this) {
    LabStatus.LOW -> R.string.lab_status_low
    LabStatus.HIGH -> R.string.lab_status_high
    LabStatus.NORMAL -> null
}

/** Pastille « Bas » / « Haut » ; rien dans la norme. */
@Composable
private fun LabStatusLabel(status: LabStatus) {
    val label = status.labelRes() ?: return
    val color = labStatusColor(status)
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Abscisse relative (0..1) proportionnelle au temps écoulé ; un point seul est centré,
 * des dates toutes égales sont réparties. [points] dans l'ordre chronologique.
 */
private fun timeFraction(points: List<LabResult>, index: Int): Float {
    if (points.size == 1) return 0.5f
    val first = points.first().date.toEpochDays()
    val span = (points.last().date.toEpochDays() - first).toFloat()
    return if (span == 0f) index.toFloat() / (points.size - 1)
    else (points[index].date.toEpochDays() - first).toFloat() / span
}

// --- Section du suivi : par analyse ou par bilan ---

private enum class LabView(@StringRes val labelRes: Int) {
    BY_TEST(R.string.lab_view_by_test),
    BY_DATE(R.string.lab_view_by_date),
}

/**
 * Résultats du suivi, au choix par analyse (dernière valeur et tendance) ou en tableau des
 * bilans. [histories] du plus récent au plus ancien, dans l'ordre de l'énumération.
 */
@Composable
internal fun LabResultsViews(
    histories: Map<LabTest, List<LabResult>>,
    sex: Sex?,
    onOpenTest: (LabTest) -> Unit,
    onEditResult: (LabResult) -> Unit,
) {
    var view by rememberSaveable { mutableStateOf(LabView.BY_TEST) }
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        LabView.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = view == option,
                onClick = { view = option },
                shape = SegmentedButtonDefaults.itemShape(index, LabView.entries.size),
            ) {
                Text(stringResource(option.labelRes))
            }
        }
    }
    when (view) {
        LabView.BY_TEST -> LabTestList(histories, sex, onOpenTest)
        LabView.BY_DATE -> LabPanelTable(histories, sex, onEditResult)
    }
}

/** Nombre de résultats tracés dans la petite courbe de chaque analyse. */
private const val SPARKLINE_POINTS = 8

/**
 * Analyses hors normes d'abord, puis celles dans la norme rangées par catégorie, dans l'ordre
 * de l'énumération ; un groupe vide est omis.
 */
@Composable
private fun LabTestList(
    histories: Map<LabTest, List<LabResult>>,
    sex: Sex?,
    onOpenTest: (LabTest) -> Unit,
) {
    val (watch, normal) = histories.entries.partition { (_, history) ->
        history.first().status(sex) != LabStatus.NORMAL
    }
    val groups = listOf(R.string.lab_group_watch to watch) +
        LabCategory.entries.map { category -> category.labelRes to normal.filter { it.key.category == category } }
    groups.forEach { (title, group) ->
        if (group.isEmpty()) return@forEach
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        group.forEach { (test, history) ->
            LabTestRow(test = test, history = history, sex = sex, onClick = { onOpenTest(test) })
        }
    }
}

/**
 * Dernière valeur d'une analyse, son statut, et la courbe de ses [SPARKLINE_POINTS] derniers
 * résultats : les bilans espacés de plusieurs mois gardent une tendance lisible.
 * [history] du plus récent au plus ancien.
 */
@Composable
private fun LabTestRow(
    test: LabTest,
    history: List<LabResult>,
    sex: Sex?,
    onClick: () -> Unit,
) {
    val latest = history.first()
    val status = latest.status(sex)
    val recent = remember(history) { history.take(SPARKLINE_POINTS).reversed() }
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(test.labelRes),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (status != LabStatus.NORMAL) {
                    Spacer(Modifier.width(8.dp))
                    LabStatusLabel(status)
                }
            }
        },
        supportingContent = {
            Text("${latest.displayValue()} · ${DateFormats.dayMonthYear(latest.date)}")
        },
        leadingContent = { Icon(Icons.Outlined.Biotech, contentDescription = null) },
        trailingContent = {
            LabSparkline(points = recent, color = labStatusColor(status), modifier = Modifier.size(80.dp, 28.dp))
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = settingsRowColors(),
    )
}

/** Mini-courbe décorative ; le dernier point est marqué. [points] dans l'ordre chronologique. */
@Composable
private fun LabSparkline(points: List<LabResult>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val radius = 2.5.dp.toPx()
        val width = size.width - 2 * radius
        val height = size.height - 2 * radius
        val min = points.minOf { it.value }
        val max = points.maxOf { it.value }
        fun offset(index: Int): Offset = Offset(
            radius + width * timeFraction(points, index),
            if (max == min) size.height / 2
            else radius + height * ((max - points[index].value) / (max - min)).toFloat(),
        )
        if (points.size > 1) {
            val path = Path()
            points.indices.forEach { index ->
                val point = offset(index)
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        drawCircle(color, radius = radius, center = offset(points.lastIndex))
    }
}

// --- Tableau des bilans ---

private val TABLE_NAME_WIDTH = 110.dp
private val TABLE_CELL_WIDTH = 72.dp
private val TABLE_HEADER_HEIGHT = 40.dp
private val TABLE_ROW_HEIGHT = 60.dp
private val TABLE_CATEGORY_HEIGHT = 32.dp

/**
 * Une ligne par analyse, regroupées sous une ligne de catégorie ; une colonne par date de
 * prélèvement (plus récente à gauche). La colonne des noms reste fixe ; les dates défilent
 * ensemble horizontalement.
 */
@Composable
private fun LabPanelTable(
    histories: Map<LabTest, List<LabResult>>,
    sex: Sex?,
    onEditResult: (LabResult) -> Unit,
) {
    // Catégories dans l'ordre de l'énumération, analyses aussi (clés déjà triées).
    val groups = remember(histories) { histories.keys.groupBy { it.category }.toSortedMap() }
    val dates = remember(histories) {
        histories.values.flatten().map { it.date }.distinct().sortedDescending()
    }
    // Un résultat par analyse et par date : le dernier saisi (historiques déjà triés).
    val cells = remember(histories) {
        histories.mapValues { (_, history) -> history.distinctBy { it.date }.associateBy { it.date } }
    }
    val currentYear = remember { today().year }
    val locale = Locale.getDefault()
    val dayMonth = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
    }
    val headerStyle = MaterialTheme.typography.labelMedium
    val headerColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Column(Modifier.width(TABLE_NAME_WIDTH)) {
            Box(Modifier.height(TABLE_HEADER_HEIGHT), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.lab_table_test), style = headerStyle, color = headerColor)
            }
            groups.forEach { (category, tests) ->
                Box(Modifier.height(TABLE_CATEGORY_HEIGHT), contentAlignment = Alignment.BottomStart) {
                    // Peut déborder sur la partie défilante, vide sur cette ligne.
                    Text(
                        stringResource(category.labelRes),
                        style = headerStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .padding(bottom = 4.dp),
                    )
                }
                tests.forEach { test ->
                    Column(Modifier.height(TABLE_ROW_HEIGHT), verticalArrangement = Arrangement.Center) {
                        Text(
                            stringResource(test.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(test.unit, style = MaterialTheme.typography.labelSmall, color = headerColor)
                    }
                }
            }
        }
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            dates.forEach { date ->
                Column(Modifier.width(TABLE_CELL_WIDTH)) {
                    Column(
                        Modifier
                            .height(TABLE_HEADER_HEIGHT)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(date.toJavaLocalDate().format(dayMonth), style = headerStyle, color = headerColor, maxLines = 1)
                        if (date.year != currentYear) {
                            Text(date.year.toString(), style = MaterialTheme.typography.labelSmall, color = headerColor)
                        }
                    }
                    groups.values.forEach { tests ->
                        Spacer(Modifier.height(TABLE_CATEGORY_HEIGHT))
                        tests.forEach { test ->
                            LabTableCell(result = cells[test]?.get(date), sex = sex, onClick = onEditResult)
                        }
                    }
                }
            }
        }
    }
}

/** Valeur sans unité ; hors normes sur fond teinté ; « – » sans résultat. */
@Composable
private fun LabTableCell(result: LabResult?, sex: Sex?, onClick: (LabResult) -> Unit) {
    Box(
        Modifier
            .height(TABLE_ROW_HEIGHT)
            .fillMaxWidth()
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (result == null) {
            Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            val status = result.status(sex)
            val abnormal = status != LabStatus.NORMAL
            val color = labStatusColor(status)
            val statusText = status.labelRes()?.let { stringResource(it) }
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (abnormal) Modifier.background(color.copy(alpha = 0.12f)) else Modifier)
                    .clickable { onClick(result) }
                    .semantics { if (statusText != null) stateDescription = statusText },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    result.displayValue(withUnit = false),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (abnormal) FontWeight.SemiBold else null,
                    color = if (abnormal) color else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

// --- Saisie ---

private val LabQualifier.symbol: String
    get() = when (this) {
        LabQualifier.EXACT -> "="
        LabQualifier.BELOW -> "<"
        LabQualifier.ABOVE -> ">"
    }

@get:StringRes
private val LabQualifier.descriptionRes: Int
    get() = when (this) {
        LabQualifier.EXACT -> R.string.lab_qualifier_exact
        LabQualifier.BELOW -> R.string.lab_qualifier_below
        LabQualifier.ABOVE -> R.string.lab_qualifier_above
    }

/**
 * Choix de l'analyse : menu déroulant groupé par catégorie, nom et unité ; une quarantaine
 * d'analyses ne tient plus en rangée de puces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabTestPicker(test: LabTest, onSelect: (LabTest) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(test.labelRes),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.lab_result_test)) },
            suffix = { Text(test.unit) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LabCategory.entries.forEach { category ->
                val options = LabTest.entries.filter { it.category == category }
                if (options.isEmpty()) return@forEach
                Text(
                    text = stringResource(category.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        trailingIcon = {
                            Text(
                                option.unit,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabResultEditorSheet(
    editing: LabEditing,
    onDismiss: () -> Unit,
    onSave: (LabResult) -> Unit,
    onDelete: (LabResult) -> Unit,
) {
    val initial = editing.result
    var test by remember { mutableStateOf(initial?.test ?: editing.test) }
    var qualifier by remember { mutableStateOf(initial?.qualifier ?: LabQualifier.EXACT) }
    var valueText by remember { mutableStateOf(initial?.let { formatLabValue(it.value, grouping = false) }.orEmpty()) }
    var date by remember { mutableStateOf(initial?.date ?: today()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    val value = parseLabValue(valueText)
    val invalid = valueText.isNotBlank() && value == null

    LabSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(if (initial == null) R.string.lab_result_add else R.string.lab_result_edit),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        LabTestPicker(test = test, onSelect = { test = it })
        // Résultat exact, ou seulement borné par le seuil de mesure du laboratoire.
        SingleChoiceSegmentedButtonRow(Modifier.width(180.dp)) {
            LabQualifier.entries.forEachIndexed { index, option ->
                val description = stringResource(option.descriptionRes)
                SegmentedButton(
                    selected = qualifier == option,
                    onClick = { qualifier = option },
                    shape = SegmentedButtonDefaults.itemShape(index, LabQualifier.entries.size),
                    icon = {},
                ) {
                    Text(option.symbol, modifier = Modifier.clearAndSetSemantics { contentDescription = description })
                }
            }
        }
        OutlinedTextField(
            value = valueText,
            onValueChange = { valueText = it },
            label = { Text(stringResource(R.string.lab_result_value)) },
            prefix = if (qualifier == LabQualifier.EXACT) null else {
                { Text("${qualifier.symbol} ") }
            },
            suffix = { Text(test.unit) },
            isError = invalid,
            supportingText = if (invalid) {
                { Text(stringResource(R.string.lab_result_value_invalid)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lab_result_date), style = MaterialTheme.typography.bodyLarge)
                Text(
                    DateFormats.dayMonthYear(date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { pickDate = true }) { Text(stringResource(R.string.modify)) }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.lab_result_note)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (initial != null) {
                TextButton(onClick = { onDelete(initial); onDismiss() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (value != null) {
                        val base = initial ?: LabResult(test = test, value = value, date = date)
                        // Les bornes du laboratoire valent pour l'analyse d'origine seulement.
                        val sameTest = base.test == test
                        onSave(
                            base.copy(
                                test = test,
                                value = value,
                                date = date,
                                note = note.trim(),
                                qualifier = qualifier,
                                referenceLow = base.referenceLow.takeIf { sameTest },
                                referenceHigh = base.referenceHigh.takeIf { sameTest },
                            )
                        )
                    }
                    onDismiss()
                },
                enabled = value != null,
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

// --- Détail d'une analyse ---

/** Fenêtre de la courbe ; [months] null = tout l'historique. */
private enum class LabPeriod(val months: Int?, @StringRes val labelRes: Int) {
    SIX_MONTHS(6, R.string.lab_period_6_months),
    YEAR(12, R.string.lab_period_1_year),
    ALL(null, R.string.lab_period_all),
}

/** Courbe puis liste des résultats d'une analyse ; [history] du plus récent au plus ancien. */
@Composable
internal fun LabResultDetailSheet(
    test: LabTest,
    history: List<LabResult>,
    sex: Sex?,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (LabResult) -> Unit,
) {
    // Les valeurs de référence du dernier compte rendu servent de bande « normale ».
    val reference = history.first().reference(sex)
    val byPeriod = remember(history) {
        val today = today()
        val chronological = history.reversed()
        LabPeriod.entries.associateWith { period ->
            val months = period.months
            if (months == null) chronological
            else today.minus(months, DateTimeUnit.MONTH).let { since -> chronological.filter { it.date >= since } }
        }
    }
    var period by rememberSaveable { mutableStateOf(LabPeriod.YEAR) }
    // Aucun point dans la fenêtre choisie : tout l'historique.
    val shown = if (byPeriod.getValue(period).isEmpty()) LabPeriod.ALL else period

    LabSheet(onDismiss = onDismiss) {
        Text(
            text = "${stringResource(test.labelRes)} (${test.unit})",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabPeriod.entries.forEach { option ->
                FilterChip(
                    selected = shown == option,
                    onClick = { period = option },
                    enabled = byPeriod.getValue(option).isNotEmpty(),
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        LabChart(test = test, points = byPeriod.getValue(shown), reference = reference, sex = sex)
        val range = reference.display()
        if (range.isNotEmpty()) {
            Text(
                stringResource(R.string.lab_reference, "$range ${test.unit}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            history.forEach { result ->
                val status = result.status(sex)
                ListItem(
                    headlineContent = { Text(result.displayValue()) },
                    supportingContent = {
                        Text(
                            listOf(DateFormats.dayMonthYear(result.date), result.note)
                                .filter { it.isNotBlank() }
                                .joinToString("\n")
                        )
                    },
                    trailingContent = if (status == LabStatus.NORMAL) null else {
                        { LabStatusLabel(status) }
                    },
                    modifier = Modifier.clickable { onEdit(result) },
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

/**
 * Courbe des valeurs, abscisses proportionnelles au temps écoulé, sur la bande des valeurs
 * de référence. L'axe vertical englobe valeurs et bornes ; sans borne basse (CRP), il part
 * de zéro. Points pleins colorés selon le statut, creux pour un seuil (« < 1 »).
 * [points] dans l'ordre chronologique, non vide.
 */
@Composable
private fun LabChart(test: LabTest, points: List<LabResult>, reference: ReferenceRange, sex: Sex?) {
    val low = reference.low
    val high = reference.high
    val valuesMin = points.minOf { it.value }
    val min = when {
        low != null -> minOf(valuesMin, low)
        high != null -> 0.0
        else -> valuesMin
    }
    val max = listOfNotNull(points.maxOf { it.value }, low, high).max()
    val lineColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val bandColor = bristolColor(3).copy(alpha = 0.15f) // vert « normal » de l'app
    val normalColor = labStatusColor(LabStatus.NORMAL)
    val abnormalColor = labStatusColor(LabStatus.HIGH)
    val holeColor = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.lab_chart_description, stringResource(test.labelRes))

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(160.dp)) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = if (min == max) Arrangement.Center else Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(formatLabValue(max), style = labelStyle, color = labelColor)
                if (min != max) Text(formatLabValue(min), style = labelStyle, color = labelColor)
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

                fun x(index: Int): Float = pad + width * timeFraction(points, index)
                fun y(value: Double): Float =
                    if (max == min) size.height / 2
                    else pad + height * ((max - value) / (max - min)).toFloat()

                // Bande des valeurs normales ; une borne absente la prolonge jusqu'au bord.
                if (low != null || high != null) {
                    val top = high?.let { y(it) } ?: 0f
                    val bottom = low?.let { y(it) } ?: size.height
                    drawRect(bandColor, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
                }
                // Repères du minimum et du maximum, en pointillés discrets.
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
                        if (index == 0) path.moveTo(x(index), y(point.value))
                        else path.lineTo(x(index), y(point.value))
                    }
                    drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }
                val radius = 4.dp.toPx()
                points.forEachIndexed { index, point ->
                    val center = Offset(x(index), y(point.value))
                    val color = if (point.status(sex) == LabStatus.NORMAL) normalColor else abnormalColor
                    if (point.qualifier == LabQualifier.EXACT) {
                        drawCircle(color, radius = radius, center = center)
                    } else {
                        drawCircle(holeColor, radius = radius, center = center)
                        drawCircle(color, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
                    }
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

/** Feuille modale défilante, sur le modèle des éditeurs du profil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}
