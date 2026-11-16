package fr.ibdjournal.app.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import fr.ibdjournal.app.domain.localDateTimeAt

private val BloodColor = Color(0xFFE05B5B)
private val MucusColor = Color(0xFFE0A83C)
private val PainColor = Color(0xFFA78BFA)
private val FatigueColor = Color(0xFF6FBF8B)

/**
 * Feuille de saisie détaillée d'une selle : Bristol, symptômes, commentaire.
 * La saisie rapide (2 gestes) reste disponible depuis l'accueil ; cette feuille
 * est le mode complet, accessible via « Plus de détails ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoolSheet(
    stool: fr.ibdjournal.app.data.model.Stool?,
    initialBristol: Int? = null,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (bristol: Int, blood: Int, mucus: Int, pain: Int, fatigue: Int, comment: String, urgency: Boolean, nocturnal: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        StoolSheetContent(
            editingStool = stool,
            initialBristol = initialBristol,
            onDelete = onDelete,
            onSave = onSave,
        )
    }
}

@Composable
fun StoolSheetContent(
    editingStool: fr.ibdjournal.app.data.model.Stool? = null,
    initialBristol: Int? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (Int, Int, Int, Int, Int, String, Boolean, Boolean) -> Unit,
) {
    var bristolType by remember(editingStool, initialBristol) {
        mutableIntStateOf(editingStool?.bristolType ?: initialBristol ?: 4)
    }
    var bloodLevel by remember(editingStool) { mutableIntStateOf(editingStool?.bloodLevel ?: 0) }
    var mucusLevel by remember(editingStool) { mutableIntStateOf(editingStool?.mucusLevel ?: 0) }
    var painLevel by remember(editingStool) { mutableIntStateOf(editingStool?.painLevel ?: 0) }
    var fatigueLevel by remember(editingStool) { mutableIntStateOf(editingStool?.fatigueLevel ?: 0) }
    var comment by remember(editingStool) { mutableStateOf(editingStool?.comment ?: "") }
    var urgency by remember(editingStool) { mutableStateOf(editingStool?.urgency ?: false) }
    var nocturnal by remember(editingStool) { mutableStateOf(editingStool?.nocturnal ?: false) }

    val timestamp = editingStool?.createdAt ?: System.currentTimeMillis()
    val moment = DateFormats.weekdayDayMonth(
        localDateTimeAt(timestamp).date
    ) + " · " + DateFormats.time(timestamp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (editingStool != null) R.string.edit_stool else R.string.new_stool
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = moment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(13.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BristolIcon(type = bristolType, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.stool_type_question),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            BristolGrid(
                selected = bristolType,
                onSelect = { bristolType = it },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.analysis_type, bristolType) +
                    " · " + stringResource(bristolDescription(bristolType)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stool_symptoms),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.level_hint_common),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LevelRow(
                title = stringResource(R.string.blood_presence),
                value = bloodLevel,
                accent = BloodColor,
                onChange = { bloodLevel = it },
            )
            LevelRow(
                title = stringResource(R.string.mucus_presence),
                value = mucusLevel,
                accent = MucusColor,
                onChange = { mucusLevel = it },
            )
            LevelRow(
                title = stringResource(R.string.pain),
                value = painLevel,
                accent = PainColor,
                onChange = { painLevel = it },
            )
            LevelRow(
                title = stringResource(R.string.fatigue),
                value = fatigueLevel,
                accent = FatigueColor,
                onChange = { fatigueLevel = it },
            )

            // Impériosité et réveil nocturne : marqueurs d'activité suivis par les scores cliniques.
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = urgency,
                    onClick = { urgency = !urgency },
                    label = { Text(stringResource(R.string.stool_urgency)) },
                    shape = RoundedCornerShape(12.dp),
                )
                FilterChip(
                    selected = nocturnal,
                    onClick = { nocturnal = !nocturnal },
                    label = { Text(stringResource(R.string.stool_nocturnal)) },
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.stool_comment_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = RoundedCornerShape(16.dp),
                minLines = 1,
                maxLines = 3,
            )
        }

        // Pied fixe : toujours visible, sans défilement.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Button(
                    onClick = {
                        onSave(bristolType, bloodLevel, mucusLevel, painLevel, fatigueLevel, comment, urgency, nocturnal)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.save),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (editingStool != null && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BristolGrid(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (column in 0 until 4) {
                    val type = row * 4 + column
                    val isSelected = selected == type
                    Surface(
                        onClick = { onSelect(type) },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            BristolIcon(type = type, modifier = Modifier.size(30.dp))
                            Text(
                                text = "$type",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelRow(
    title: String,
    value: Int,
    accent: Color,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (value == 0) {
                    stringResource(R.string.level_none)
                } else {
                    stringResource(R.string.level_out_of_five, value)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (value == 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    accent
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (level in 0..5) {
                val isSelected = value == level
                val tint = if (level == 0) MaterialTheme.colorScheme.onSurfaceVariant else accent
                Surface(
                    onClick = { onChange(level) },
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        if (level == 0) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            accent.copy(alpha = 0.18f)
                        }
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    border = if (isSelected) {
                        BorderStroke(
                            1.dp,
                            if (level == 0) MaterialTheme.colorScheme.outline else accent,
                        )
                    } else {
                        null
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = level.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun bristolDescription(type: Int): Int = when (type) {
    0 -> R.string.bristol_desc_0
    1 -> R.string.bristol_desc_1
    2 -> R.string.bristol_desc_2
    3 -> R.string.bristol_desc_3
    4 -> R.string.bristol_desc_4
    5 -> R.string.bristol_desc_5
    6 -> R.string.bristol_desc_6
    else -> R.string.bristol_desc_7
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun StoolSheetPreview() {
    IBDJournalTheme(fr.ibdjournal.app.data.Appearance(fr.ibdjournal.app.data.ThemeMode.DARK)) {
        Box(Modifier.height(760.dp)) {
            Surface {
                StoolSheetContent(onSave = { _, _, _, _, _, _, _, _ -> })
            }
        }
    }
}
