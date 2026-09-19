package fr.ibdjournal.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Feuille d'ajout d'une selle avec échelle de Bristol et niveaux de symptômes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoolSheet(
    stool: fr.ibdjournal.app.data.model.Stool?,
    onDismiss: () -> Unit,
    onSave: (bristol: Int, blood: Int, mucus: Int, pain: Int, fatigue: Int, comment: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        StoolSheetContent(
            editingStool = stool,
            onSave = onSave
        )
    }
}

@Composable
fun StoolSheetContent(
    editingStool: fr.ibdjournal.app.data.model.Stool? = null,
    onSave: (Int, Int, Int, Int, Int, String) -> Unit,
) {
    var bristolType by remember(editingStool) { mutableIntStateOf(editingStool?.bristolType ?: 4) }
    var bloodLevel by remember(editingStool) { mutableIntStateOf(editingStool?.bloodLevel ?: 0) }
    var mucusLevel by remember(editingStool) { mutableIntStateOf(editingStool?.mucusLevel ?: 0) }
    var painLevel by remember(editingStool) { mutableIntStateOf(editingStool?.painLevel ?: 0) }
    var fatigueLevel by remember(editingStool) { mutableIntStateOf(editingStool?.fatigueLevel ?: 0) }
    var comment by remember(editingStool) { mutableStateOf(editingStool?.comment ?: "") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // En-tête
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (editingStool != null) stringResource(R.string.edit_stool) else stringResource(R.string.new_stool),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            BristolIcon(type = bristolType, modifier = Modifier.size(32.dp))
        }

        // Heure
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.hour),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val time = editingStool?.createdAt ?: System.currentTimeMillis()
                    Text(
                        text = java.time.Instant.ofEpochMilli(time)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime()
                            .format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.tap_to_modify),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Échelle de Bristol
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.bristol_scale),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Text(
                text = stringResource(R.string.stool_type_question),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items((0..7).toList()) { type ->
                    BristolItem(
                        type = type,
                        isSelected = bristolType == type,
                        onClick = { bristolType = type }
                    )
                }
            }
        }

        // Présence de sang
        LevelSelector(
            title = stringResource(R.string.blood_presence),
            subtitle = stringResource(R.string.level_hint),
            currentLevel = bloodLevel,
            onLevelSelected = { bloodLevel = it },
            icon = Icons.Outlined.WaterDrop,
            iconColor = Color(0xFFD32F2F)
        )

        // Présence de glaires
        LevelSelector(
            title = stringResource(R.string.mucus_presence),
            subtitle = stringResource(R.string.level_hint),
            currentLevel = mucusLevel,
            onLevelSelected = { mucusLevel = it },
            icon = Icons.Outlined.WaterDrop,
            iconColor = Color(0xFFFBC02D)
        )

        // Douleur
        LevelSelector(
            title = stringResource(R.string.pain),
            subtitle = stringResource(R.string.level_hint_pain),
            currentLevel = painLevel,
            onLevelSelected = { painLevel = it },
            icon = Icons.Outlined.Close,
            iconColor = Color(0xFF7B1FA2)
        )

        // Fatigue
        LevelSelector(
            title = stringResource(R.string.fatigue),
            subtitle = stringResource(R.string.level_hint_fatigue),
            currentLevel = fatigueLevel,
            onLevelSelected = { fatigueLevel = it },
            icon = Icons.Outlined.Close,
            iconColor = Color(0xFF388E3C)
        )

        // Commentaire
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.stool_comment_hint)) },
            minLines = 2,
            maxLines = 5,
        )

        Button(
            onClick = { onSave(bristolType, bloodLevel, mucusLevel, painLevel, fatigueLevel, comment) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.save), modifier = Modifier.padding(vertical = 4.dp))
        }
        
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun BristolItem(type: Int, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
    
    Card(
        modifier = Modifier
            .size(width = 100.dp, height = 140.dp)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = type.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                BristolIcon(type = type, modifier = Modifier.size(40.dp))
            }

            Text(
                text = when(type) {
                    0 -> "Rien"
                    1 -> "Dur"
                    2 -> "Bosselé"
                    3 -> "Fissuré"
                    4 -> "Lisse"
                    5 -> "Fragments"
                    6 -> "Floconneux"
                    7 -> "Liquide"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun LevelSelector(
    title: String,
    subtitle: String,
    currentLevel: Int,
    onLevelSelected: (Int) -> Unit,
    icon: ImageVector,
    iconColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onLevelSelected(0) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (currentLevel == 0) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            
            (1..5).forEach { level ->
                IconButton(
                    onClick = { onLevelSelected(level) },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (currentLevel == level) iconColor.copy(alpha = 0.2f) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (currentLevel >= level) iconColor else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Text(
                text = "$currentLevel / 5",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StoolSheetPreview() {
    IBDJournalTheme {
        Surface {
            StoolSheetContent(onSave = { _, _, _, _, _, _ -> })
        }
    }
}
