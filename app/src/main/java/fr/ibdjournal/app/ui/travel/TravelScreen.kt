package fr.ibdjournal.app.ui.travel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.domain.TravelLine
import fr.ibdjournal.app.report.TravelLanguage
import fr.ibdjournal.app.report.shareReport
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.care.DatePickerDialogFor
import fr.ibdjournal.app.ui.settings.settingsRowColors
import java.util.Locale
import kotlinx.datetime.LocalDate

/**
 * Travel pack: dates, destination and language of the documents, the medication to take along
 * (the schedule over the trip plus a safety margin, compared with the counted stock), and the PDF
 * with the list, a letter for the doctor to sign and the urgent-need card in that language.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TravelScreen(onBack: () -> Unit, viewModel: TravelViewModel = hiltViewModel()) {
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickDeparture by remember { mutableStateOf(false) }
    var pickReturn by remember { mutableStateOf(false) }

    val failedMessage = stringResource(R.string.travel_failed)
    val subject = stringResource(R.string.travel_share_subject)
    LaunchedEffect(viewModel) {
        viewModel.pdfs.collect { result ->
            result.onSuccess { shareReport(context, it, subject) }
                .onFailure { android.widget.Toast.makeText(context, failedMessage, android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.travel_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            DateRow(R.string.travel_departure, trip.departure) { pickDeparture = true }
            DateRow(R.string.travel_return, trip.returnDate) { pickReturn = true }

            ListItem(
                headlineContent = { Text(stringResource(R.string.travel_margin)) },
                supportingContent = { Text(stringResource(R.string.travel_margin_hint)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.setMargin(trip.marginDays - 1) }, enabled = trip.marginDays > 0) {
                            Icon(Icons.Outlined.Remove, contentDescription = null)
                        }
                        Text(
                            text = pluralStringResource(R.plurals.travel_margin_days, trip.marginDays, trip.marginDays),
                            modifier = Modifier.width(64.dp),
                        )
                        IconButton(
                            onClick = { viewModel.setMargin(trip.marginDays + 1) },
                            enabled = trip.marginDays < MAX_TRAVEL_MARGIN_DAYS,
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                        }
                    }
                },
                colors = settingsRowColors(),
            )

            OutlinedTextField(
                value = trip.destination,
                onValueChange = viewModel::setDestination,
                label = { Text(stringResource(R.string.travel_destination)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Text(
                text = stringResource(R.string.travel_language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TravelLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = trip.language == language,
                        onClick = { viewModel.setLanguage(language) },
                        label = { Text(languageName(language)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.travel_list_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            if (plan.isEmpty()) {
                Text(
                    text = stringResource(R.string.travel_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            plan.forEach { LineRow(it) }

            NoteCard(R.string.travel_vaccines)
            NoteCard(R.string.travel_disclaimer)

            Button(
                onClick = viewModel::create,
                enabled = !creating,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (creating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.travel_creating))
                } else {
                    Text(stringResource(R.string.travel_create))
                }
            }
        }
    }

    if (pickDeparture) {
        DatePickerDialogFor(
            initial = trip.departure,
            maxDate = null,
            onDismiss = { pickDeparture = false },
            onPick = viewModel::setDeparture,
        )
    }
    if (pickReturn) {
        DatePickerDialogFor(
            initial = trip.returnDate,
            maxDate = null,
            onDismiss = { pickReturn = false },
            onPick = viewModel::setReturn,
        )
    }
}

@Composable
private fun DateRow(labelRes: Int, date: LocalDate, onPick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        supportingContent = { Text(DateFormats.dayMonthYear(date)) },
        trailingContent = { TextButton(onClick = onPick) { Text(stringResource(R.string.modify)) } },
        modifier = Modifier.clickable(onClick = onPick),
        colors = settingsRowColors(),
    )
}

@Composable
private fun LineRow(line: TravelLine) {
    val medication = line.medication
    ListItem(
        headlineContent = {
            Text(listOf(medication.name, medication.dosage).filter { it.isNotBlank() }.joinToString(" · "))
        },
        supportingContent = {
            Column {
                Text(
                    text = pluralStringResource(R.plurals.travel_intakes, line.total, line.total) + " · " +
                        stringResource(R.string.travel_line_detail, line.onTrip, line.margin),
                )
                val missing = line.missing
                if (missing != null && missing > 0) {
                    Text(
                        text = stringResource(R.string.travel_line_missing, medication.stockDoses ?: 0, missing),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        colors = settingsRowColors(),
    )
}

@Composable
private fun NoteCard(textRes: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/** "Français", "English", "Español", "Deutsch": each language in its own language. */
private fun languageName(language: TravelLanguage): String {
    val locale = Locale.forLanguageTag(language.tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}
