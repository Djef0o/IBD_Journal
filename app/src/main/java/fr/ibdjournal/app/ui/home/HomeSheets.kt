package fr.ibdjournal.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.labelRes
import fr.ibdjournal.app.ui.calendar.BristolIcon

/**
 * Quick stool entry: the 8 Bristol types or a false urge, one button.
 * "More details" switches to the full sheet (blood, mucus, pain, comment).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickStoolSheet(
    onDismiss: () -> Unit,
    onDetails: (type: Int, falseUrge: Boolean) -> Unit,
    onSave: (type: Int, falseUrge: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableIntStateOf(4) }
    val subtitle = stringResource(R.string.nav_home) + " · " + DateFormats.time(System.currentTimeMillis())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.quick_stool_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.quick_stool_question),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))

            for (row in 0 until 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (column in 0 until 4) {
                        val type = row * 4 + column
                        QuickTypeTile(
                            type = type,
                            selected = selected == type,
                            modifier = Modifier.weight(1f),
                            onClick = { selected = type },
                        )
                    }
                }
                if (row == 0) Spacer(Modifier.height(8.dp))
            }

            // False urge: no stool, but a toilet visit to note (flares).
            Spacer(Modifier.height(8.dp))
            QuickFalseUrgeTile(
                selected = selected == FALSE_URGE_CHOICE,
                onClick = { selected = FALSE_URGE_CHOICE },
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(selected.coerceAtLeast(0), selected == FALSE_URGE_CHOICE) },
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
            TextButton(
                onClick = { onDetails(selected.coerceAtLeast(0), selected == FALSE_URGE_CHOICE) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.quick_stool_details),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "False urge" choice of quick entry: outside the 8 Bristol types. */
private const val FALSE_URGE_CHOICE = -1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickFalseUrgeTile(selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text = stringResource(R.string.false_urge),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTypeTile(
    type: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BristolIcon(type = type, modifier = Modifier.size(32.dp))
            Text(
                text = stringResource(R.string.analysis_type, type),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Choice of meal time before opening the full editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTypeSheet(
    onDismiss: () -> Unit,
    onPick: (MealType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.add_meal),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            MealType.entries.forEach { type ->
                ListItem(
                    headlineContent = { Text(stringResource(type.labelRes)) },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable { onPick(type) },
                )
            }
        }
    }
}
