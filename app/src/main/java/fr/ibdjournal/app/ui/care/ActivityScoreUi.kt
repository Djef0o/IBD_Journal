package fr.ibdjournal.app.ui.care

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.ActivityScore
import fr.ibdjournal.app.data.model.Diagnosis
import fr.ibdjournal.app.data.model.ExtraSymptom
import fr.ibdjournal.app.ui.day.ExtraSymptomsViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import fr.ibdjournal.app.data.model.ScoreKind
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.domain.ActivityLevel
import fr.ibdjournal.app.domain.level
import fr.ibdjournal.app.domain.prefilledAnswers
import fr.ibdjournal.app.domain.questionCount
import fr.ibdjournal.app.domain.suggestedScore
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.domain.total
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.settings.settingsRowColors
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

/** Largest number of stools the stepper accepts. */
private const val MAX_STOOLS = 99

private val ScoreKind.nameRes: Int
    @StringRes get() = when (this) {
        ScoreKind.HBI -> R.string.score_hbi
        ScoreKind.SCCAI -> R.string.score_sccai
    }

private val ScoreKind.fullNameRes: Int
    @StringRes get() = when (this) {
        ScoreKind.HBI -> R.string.score_hbi_full
        ScoreKind.SCCAI -> R.string.score_sccai_full
    }

private val ActivityLevel.labelRes: Int
    @StringRes get() = when (this) {
        ActivityLevel.REMISSION -> R.string.score_level_remission
        ActivityLevel.MILD -> R.string.score_level_mild
        ActivityLevel.MODERATE -> R.string.score_level_moderate
        ActivityLevel.SEVERE -> R.string.score_level_severe
        ActivityLevel.ACTIVE -> R.string.score_level_active
    }

/** Most recent first; on equal dates, the last entered. */
private val newestScoreFirst: Comparator<ActivityScore> =
    compareByDescending<ActivityScore> { it.date }.thenByDescending { it.id }

// --- Questionnaires (layout documented in domain/Activity.kt) ---

private sealed interface ScoreQuestion {
    val title: Int
}

/** One answer among [options], whose index is the answer (points). */
private data class ScoreChoice(@StringRes override val title: Int, @ArrayRes val options: Int) : ScoreQuestion

/** A number. */
private data class ScoreCount(@StringRes override val title: Int) : ScoreQuestion

/** Bit mask: bit i set = item i checked. */
private data class ScoreFlags(@StringRes override val title: Int, val items: List<Int>) : ScoreQuestion

private val hbiQuestions = listOf(
    ScoreChoice(R.string.score_q_wellbeing, R.array.score_options_wellbeing),
    ScoreChoice(R.string.score_q_pain, R.array.score_options_pain),
    ScoreCount(R.string.score_q_liquid_stools),
    ScoreChoice(R.string.score_q_mass, R.array.score_options_mass),
    ScoreFlags(
        R.string.score_q_complications,
        listOf(
            R.string.score_flag_arthralgia,
            R.string.score_flag_uveitis,
            R.string.score_flag_erythema_nodosum,
            R.string.score_flag_aphthous,
            R.string.score_flag_pyoderma,
            R.string.score_flag_fissure,
            R.string.score_flag_fistula,
            R.string.score_flag_abscess,
        ),
    ),
)

private val sccaiQuestions = listOf(
    ScoreChoice(R.string.score_q_stools_day, R.array.score_options_stools_day),
    ScoreChoice(R.string.score_q_stools_night, R.array.score_options_stools_night),
    ScoreChoice(R.string.score_q_urgency, R.array.score_options_urgency),
    ScoreChoice(R.string.score_q_blood, R.array.score_options_blood),
    ScoreChoice(R.string.score_q_wellbeing, R.array.score_options_wellbeing),
    ScoreFlags(
        R.string.score_q_extracolonic,
        listOf(
            R.string.score_flag_joint_pains,
            R.string.score_flag_pyoderma,
            R.string.score_flag_erythema_nodosum,
            R.string.score_flag_uveitis,
        ),
    ),
)

private val ScoreKind.questions: List<ScoreQuestion>
    get() = when (this) {
        ScoreKind.HBI -> hbiQuestions
        ScoreKind.SCCAI -> sccaiQuestions
    }

// --- Care section ---

/**
 * Care section: activity scores (card, history, editor). [scores] chronological;
 * [stoolsOn] gives the stools of a day, to pre-fill a new entry; [diagnosis] picks the default questionnaire.
 */
@Composable
internal fun ActivityScoreSection(
    scores: List<ActivityScore>,
    diagnosis: Diagnosis?,
    stoolsOn: (LocalDate) -> Flow<List<Stool>>,
    onSave: (ActivityScore) -> Unit,
    onDelete: (ActivityScore) -> Unit,
) {
    var editing by remember { mutableStateOf<Editing<ActivityScore>?>(null) }
    var history by remember { mutableStateOf(false) }

    SectionHeader(
        title = stringResource(R.string.score_title),
        addLabel = stringResource(R.string.score_add),
        onAdd = { editing = Editing(null) },
    )
    if (scores.isEmpty()) {
        EmptyLine(stringResource(R.string.score_empty))
    } else {
        ScoreCard(scores = scores, onOpenHistory = { history = true })
    }

    // The history hides during entry to avoid stacking two sheets, then comes back.
    if (history && scores.isNotEmpty() && editing == null) {
        ScoreHistorySheet(
            scores = scores,
            onDismiss = { history = false },
            onAdd = { editing = Editing(null) },
            onEdit = { editing = Editing(it) },
        )
    }
    editing?.let { current ->
        ScoreEditorSheet(
            initial = current.item,
            diagnosis = diagnosis,
            stoolsOn = stoolsOn,
            onDismiss = { editing = null },
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

/** "HBI: 4". */
@Composable
private fun ActivityScore.headline(): String = stringResource(R.string.score_headline, stringResource(kind.nameRes), total)

/** Latest total, its level, change since the previous entry of the same questionnaire and the curve. */
@Composable
private fun ScoreCard(scores: List<ActivityScore>, onOpenHistory: () -> Unit) {
    val latest = scores.last()
    // Totals of different questionnaires are not comparable: change and curve stay within one.
    val sameKind = remember(scores, latest.kind) { scores.filter { it.kind == latest.kind } }
    val previous = sameKind.getOrNull(sameKind.lastIndex - 1)
    val dates = remember(sameKind) { sameKind.map { it.date } }
    val values = remember(sameKind) { sameKind.map { it.total.toDouble() } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(latest.headline(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            latest.level?.let { level ->
                Text(stringResource(level.labelRes), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = stringResource(R.string.score_latest, DateFormats.dayMonthYear(latest.date)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (previous != null) {
                val delta = latest.total - previous.total
                val since = DateFormats.dayMonthYear(previous.date)
                Text(
                    text = if (delta == 0) stringResource(R.string.score_unchanged, since)
                    else stringResource(R.string.score_change, String.format(Locale.getDefault(), "%+d", delta), since),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PeriodTimeSeries(
                dates = dates,
                values = values,
                formatValue = { it.toInt().toString() },
                description = stringResource(R.string.score_chart_description, stringResource(latest.kind.nameRes)),
            )
            Text(
                text = stringResource(R.string.score_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenHistory, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.score_history))
            }
        }
    }
}

/** All entries, most recent first; a tap opens the entry. */
@Composable
private fun ScoreHistorySheet(
    scores: List<ActivityScore>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ActivityScore) -> Unit,
) {
    val newestFirst = remember(scores) { scores.sortedWith(newestScoreFirst) }
    LabSheet(onDismiss = onDismiss) {
        EditorTitle(stringResource(R.string.score_title))
        Column {
            newestFirst.forEach { score ->
                val level = score.level?.let { stringResource(it.labelRes) }
                ListItem(
                    headlineContent = { Text(score.headline()) },
                    supportingContent = {
                        Text(listOfNotNull(DateFormats.dayMonthYear(score.date), level).joinToString(" · "))
                    },
                    modifier = Modifier.clickable { onEdit(score) },
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

// --- Editor ---

/**
 * Questionnaire entry. [initial] null = new entry: today, the questionnaire suggested by
 * [diagnosis], answers pre-filled from the day's stools until the user answers.
 */
@Composable
private fun ScoreEditorSheet(
    initial: ActivityScore?,
    diagnosis: Diagnosis?,
    stoolsOn: (LocalDate) -> Flow<List<Stool>>,
    onDismiss: () -> Unit,
    onSave: (ActivityScore) -> Unit,
    onDelete: (ActivityScore) -> Unit,
) {
    val today = today()
    var kind by remember { mutableStateOf(initial?.kind ?: diagnosis.suggestedScore ?: ScoreKind.HBI) }
    var date by remember { mutableStateOf(initial?.date ?: today) }
    var answers by remember {
        mutableStateOf(List(kind.questionCount) { index -> initial?.answers?.getOrNull(index) ?: 0 })
    }
    var touched by remember { mutableStateOf(initial != null) }

    // Null until the day's stools and noted symptoms are loaded. An existing entry keeps its answers.
    val extrasViewModel: ExtraSymptomsViewModel = hiltViewModel()
    val dayStools by remember(date) { if (initial == null) stoolsOn(date) else emptyFlow<List<Stool>>() }
        .collectAsState<List<Stool>, List<Stool>?>(initial = null)
    val dayExtras by remember(date) { if (initial == null) extrasViewModel.on(date) else emptyFlow<Set<ExtraSymptom>>() }
        .collectAsState<Set<ExtraSymptom>, Set<ExtraSymptom>>(initial = emptySet())
    LaunchedEffect(kind, dayStools, dayExtras) {
        val stools = dayStools
        if (!touched && stools != null) answers = prefilledAnswers(kind, stools, dayExtras)
    }

    val draft = ActivityScore(id = initial?.id ?: 0, date = date, kind = kind, answers = answers)
    val level = draft.level

    fun answer(index: Int, value: Int) {
        answers = answers.toMutableList().also { it[index] = value }
        touched = true
    }

    LabSheet(onDismiss = onDismiss) {
        EditorTitle(stringResource(if (initial == null) R.string.score_add else R.string.score_edit))
        if (initial == null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ScoreKind.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = kind == option,
                        onClick = {
                            if (kind != option) {
                                kind = option
                                touched = false
                                answers = List(option.questionCount) { 0 }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, ScoreKind.entries.size),
                    ) { Text(stringResource(option.nameRes)) }
                }
            }
        }
        Text(
            text = stringResource(kind.fullNameRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EditorDateRow(
            label = stringResource(R.string.score_date),
            date = date,
            maxDate = today,
            onPick = { date = it },
        )
        if (initial == null && !touched && !dayStools.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.score_prefilled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        kind.questions.forEachIndexed { index, question ->
            Text(stringResource(question.title), style = MaterialTheme.typography.titleSmall)
            val value = answers.getOrElse(index) { 0 }
            when (question) {
                is ScoreChoice -> ChoiceAnswer(question, value) { answer(index, it) }
                is ScoreCount -> StoolCountAnswer(value) { answer(index, it) }
                is ScoreFlags -> question.items.forEachIndexed { bit, label ->
                    CheckRow(
                        label = stringResource(label),
                        checked = value and (1 shl bit) != 0,
                        onChange = { answer(index, value xor (1 shl bit)) },
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.score_total, draft.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (level != null) Text(stringResource(level.labelRes), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.score_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        EditorFooter(
            canDelete = initial != null,
            canSave = true,
            onDelete = { initial?.let(onDelete); onDismiss() },
            onSave = { onSave(draft); onDismiss() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceAnswer(question: ScoreChoice, value: Int, onChange: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        stringArrayResource(question.options).forEachIndexed { index, label ->
            FilterChip(
                selected = value == index,
                onClick = { onChange(index) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun StoolCountAnswer(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }, enabled = value > 0) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.score_stools_less))
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onChange((value + 1).coerceAtMost(MAX_STOOLS)) }, enabled = value < MAX_STOOLS) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.score_stools_more))
        }
    }
}
