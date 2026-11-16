package fr.ibdjournal.app.ui.care

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.domain.DetectedLabValue
import fr.ibdjournal.app.domain.ReferenceRange
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.display
import fr.ibdjournal.app.ui.formatLabValue
import fr.ibdjournal.app.ui.labelRes
import kotlinx.datetime.LocalDate

/** Valeur proposée à l'import, modifiable avant enregistrement. */
private data class ImportDraft(
    val detected: DetectedLabValue,
    val valueText: String,
    val selected: Boolean,
) {
    val test: LabTest get() = detected.test
}

/** Doublon d'une valeur proposée : déjà enregistrée, ou lue sur un compte rendu précédent de l'import. */
private enum class Duplicate { SAVED, IN_IMPORT }

/** Vérification d'un compte rendu : date de prélèvement modifiable et valeurs proposées. */
private class DocumentReview(val document: ImportedDocument) {
    var date by mutableStateOf(document.scan.sampleDate ?: today())
    val drafts = mutableStateListOf<ImportDraft>()
}

/**
 * [test] du compte rendu [index] figure-t-il déjà, à sa date, dans les résultats enregistrés ou
 * sur un compte rendu précédent de l'import (deux bilans du même jour) ?
 */
private fun duplicateOf(
    reviews: List<DocumentReview>,
    index: Int,
    test: LabTest,
    existing: List<LabResult>,
): Duplicate? {
    val date = reviews[index].date
    return when {
        existing.any { it.test == test && it.date == date } -> Duplicate.SAVED
        (0 until index).any { earlier ->
            reviews[earlier].date == date && reviews[earlier].drafts.any { it.test == test }
        } -> Duplicate.IN_IMPORT
        else -> null
    }
}

private fun qualifierSign(qualifier: LabQualifier): String? = when (qualifier) {
    LabQualifier.EXACT -> null
    LabQualifier.BELOW -> "<"
    LabQualifier.ABOVE -> ">"
}

/**
 * Vérification obligatoire des valeurs lues sur les comptes rendus : rien n'est enregistré sans
 * validation. Un bloc par document, du prélèvement le plus ancien au plus récent (date inconnue
 * en dernier). Chaque valeur est modifiable et montre la ligne d'où elle vient ; un doublon
 * (même analyse à la même date) est décoché.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabImportReviewSheet(
    documents: List<ImportedDocument>,
    existing: List<LabResult>,
    onDismiss: () -> Unit,
    onSave: (List<LabResult>) -> Unit,
) {
    // Ordre figé à l'ouverture : modifier une date ne déplace pas les blocs.
    val reviews = remember(documents) {
        val sorted = documents
            .sortedWith(compareBy<ImportedDocument, LocalDate?>(nullsLast()) { it.scan.sampleDate })
            .map(::DocumentReview)
        sorted.forEachIndexed { index, review ->
            review.document.scan.values.forEach { value ->
                review.drafts += ImportDraft(
                    detected = value,
                    valueText = formatLabValue(value.value, grouping = false),
                    selected = duplicateOf(sorted, index, value.test, existing) == null,
                )
            }
        }
        sorted
    }
    var pickDateFor by remember { mutableStateOf<Int?>(null) }

    // Une date modifiée change les doublons de ce compte rendu et des suivants : seules les
    // valeurs dont le statut change sont décochées ou recochées.
    fun changeDate(index: Int, date: LocalDate) {
        val following = index until reviews.size
        val before = following.map { k -> reviews[k].drafts.map { duplicateOf(reviews, k, it.test, existing) != null } }
        reviews[index].date = date
        following.forEachIndexed { position, k ->
            val drafts = reviews[k].drafts
            for (j in drafts.indices) {
                val duplicate = duplicateOf(reviews, k, drafts[j].test, existing) != null
                if (duplicate != before[position][j]) drafts[j] = drafts[j].copy(selected = !duplicate)
            }
        }
    }

    val toSave = reviews.flatMap { review -> review.drafts.filter { it.selected }.map { review.date to it } }
    val allValid = toSave.all { (_, draft) -> parseLabValue(draft.valueText) != null }
    val nothingFound = reviews.all { it.drafts.isEmpty() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = stringResource(R.string.lab_import_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        // Liste paresseuse : une quinzaine de comptes rendus font plusieurs centaines de valeurs.
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(if (nothingFound) R.string.lab_import_all_nothing else R.string.lab_import_check_reports),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            reviews.forEachIndexed { index, review ->
                item(key = "document-$index") {
                    DocumentHeader(
                        review = review,
                        number = index + 1,
                        divider = index > 0,
                        onPickDate = { pickDateFor = index },
                    )
                }
                itemsIndexed(review.drafts, key = { position, _ -> "draft-$index-$position" }) { position, draft ->
                    DraftRow(
                        draft = draft,
                        duplicate = duplicateOf(reviews, index, draft.test, existing),
                        onChange = { review.drafts[position] = it },
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        ) {
            if (nothingFound) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = toSave.isNotEmpty() && allValid,
                    onClick = {
                        onSave(
                            toSave.map { (date, draft) ->
                                LabResult(
                                    test = draft.test,
                                    value = requireNotNull(parseLabValue(draft.valueText)),
                                    date = date,
                                    qualifier = draft.detected.qualifier,
                                    referenceLow = draft.detected.referenceLow,
                                    referenceHigh = draft.detected.referenceHigh,
                                )
                            }
                        )
                    },
                ) { Text(stringResource(R.string.lab_import_save, toSave.size)) }
            }
        }
    }

    pickDateFor?.let { index ->
        DatePickerDialogFor(
            initial = reviews[index].date,
            maxDate = today(),
            onDismiss = { pickDateFor = null },
            onPick = { picked -> changeDate(index, picked) },
        )
    }
}

/** Nom du fichier puis date de prélèvement, ou la raison pour laquelle rien n'est proposé. */
@Composable
private fun DocumentHeader(review: DocumentReview, number: Int, divider: Boolean, onPickDate: () -> Unit) {
    val document = review.document
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (divider) HorizontalDivider(Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text = document.name ?: stringResource(R.string.lab_import_document_untitled, number),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            document.failed -> Text(
                text = stringResource(R.string.lab_import_document_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            review.drafts.isEmpty() -> Text(
                text = stringResource(R.string.lab_import_document_nothing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lab_result_date), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = DateFormats.dayMonthYear(review.date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (document.scan.sampleDate == null) {
                        Text(
                            text = stringResource(R.string.lab_import_no_date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onPickDate) { Text(stringResource(R.string.modify)) }
            }
        }
    }
}

@Composable
private fun DraftRow(draft: ImportDraft, duplicate: Duplicate?, onChange: (ImportDraft) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = draft.selected, onCheckedChange = { onChange(draft.copy(selected = it)) })
                Text(
                    text = stringResource(draft.test.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.valueText,
                    onValueChange = { onChange(draft.copy(valueText = it)) },
                    singleLine = true,
                    isError = draft.selected && parseLabValue(draft.valueText) == null,
                    prefix = qualifierSign(draft.detected.qualifier)?.let { sign ->
                        @Composable { Text("$sign ") }
                    },
                    suffix = { Text(draft.test.unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(150.dp),
                )
            }
            val duplicateRes = when (duplicate) {
                Duplicate.SAVED -> R.string.lab_import_already_saved
                Duplicate.IN_IMPORT -> R.string.lab_import_duplicate_in_import
                null -> null
            }
            if (duplicateRes != null) {
                Text(
                    text = stringResource(duplicateRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val reference = ReferenceRange(draft.detected.referenceLow, draft.detected.referenceHigh)
            if (reference.low != null || reference.high != null) {
                Text(
                    text = stringResource(R.string.lab_reference, "${reference.display()} ${draft.test.unit}"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Ligne lue sur le compte rendu : de quoi vérifier d'un coup d'œil.
            Text(
                text = draft.detected.sourceText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** Lecture en cours : texte de chaque PDF, lu sur l'appareil. */
@Composable
internal fun LabImportProgressDialog(done: Int, total: Int) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                // Numéro du document en cours de lecture.
                Text(stringResource(R.string.lab_import_reading_progress, minOf(done + 1, total), total))
            }
        },
    )
}
