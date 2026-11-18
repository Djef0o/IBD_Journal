package fr.ibdjournal.app.ui.calendar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetState
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.Product
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.ui.labelRes

/**
 * Meal edit sheet: description, photo (photo picker), barcode
 * (scanner + Open Food Facts).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEditorSheet(
    state: MealEditorState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (Meal) -> Unit,
    onScan: () -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onPhotoRemoved: () -> Unit,
    onProductChanged: (Product?) -> Unit,
    onTypeChanged: (MealType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(onPhotoPicked)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        MealEditorContent(
            state = state,
            onDescriptionChanged = onDescriptionChanged,
            onPhotoRemoved = onPhotoRemoved,
            onPickPhoto = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onProductChanged = onProductChanged,
            onTypeChanged = onTypeChanged,
            onSave = onSave,
            onDelete = onDelete,
            onScan = onScan,
        )
    }
}

/**
 * Meal editor content, separated so it can be previewed without ModalBottomSheet
 * or ActivityResultLauncher (which do not work in Preview).
 */
@Composable
fun MealEditorContent(
    state: MealEditorState,
    onDescriptionChanged: (String) -> Unit,
    onPhotoRemoved: () -> Unit,
    onPickPhoto: () -> Unit,
    onProductChanged: (Product?) -> Unit,
    onTypeChanged: (MealType) -> Unit,
    onSave: () -> Unit,
    onDelete: (Meal) -> Unit,
    onScan: () -> Unit,
) {
    val editing = state.editingMeal

    val type = state.type
    val description = state.description
    val photoUri = state.newPhotoUri
    val removePhoto = state.removePhoto
    val draftProduct = state.product

    // Photo already saved: decrypted by the ViewModel.
    val existingPhoto: Any? = state.savedPhoto

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (editing != null) {
                stringResource(R.string.edit_meal)
            } else {
                stringResource(R.string.add_meal)
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = DateFormats.fullDate(state.date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { candidate ->
                FilterChip(
                    selected = type == candidate,
                    onClick = { onTypeChanged(candidate) },
                    label = { Text(stringResource(candidate.labelRes)) },
                )
            }
        }

        // Description.
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.meal_description_hint)) },
            minLines = 3,
            maxLines = 6,
        )

        // Photo.
        when {
            photoUri != null -> {
                AsyncImage(
                    model = photoUri,
                    contentDescription = stringResource(R.string.photo),
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                TextButton(onClick = onPhotoRemoved) {
                    Text(stringResource(R.string.remove_photo))
                }
            }

            !removePhoto && existingPhoto != null -> {
                AsyncImage(
                    model = existingPhoto,
                    contentDescription = stringResource(R.string.photo),
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                TextButton(onClick = onPhotoRemoved) {
                    Text(stringResource(R.string.remove_photo))
                }
            }

            else -> {
                OutlinedButton(onClick = onPickPhoto) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_photo))
                }
            }
        }

        // Barcode scanner.
        OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.scan_barcode))
        }

        // Scanned product.
        draftProduct?.let { product ->
            ScannedProductCard(product, onRemove = { onProductChanged(null) })
        }

        // Actions.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing != null) {
                OutlinedButton(
                    onClick = { onDelete(editing) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = description.isNotBlank() ||
                    photoUri != null ||
                    draftProduct != null ||
                    (!removePhoto && editing?.photoUri != null),
            ) {
                Text(stringResource(R.string.save))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Card for the product found via Open Food Facts. */
@Composable
fun ScannedProductCard(product: Product, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (product.imageUrl != null) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = product.name ?: product.barcode,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                product.brand?.let { brand ->
                    Text(
                        text = brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                product.nutriscore?.let { score ->
                    Text(
                        text = stringResource(R.string.nutriscore, score.uppercase()),
                        style = MaterialTheme.typography.labelMedium,
                        color = nutriscoreColor(score),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.remove_product),
                )
            }
        }
    }
}

/** Nutri-Score color: A/B green, C yellow, D orange, E red. */
@Composable
fun nutriscoreColor(score: String): Color = when (score.uppercase().firstOrNull()) {
    'A', 'B' -> Color(0xFF2E7D32)
    'C' -> Color(0xFFB8860B)
    'D' -> Color(0xFFEF6C00)
    'E' -> Color(0xFFC62828)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Preview(showBackground = true)
@Composable
private fun MealEditorSheetPreview() {
    IBDJournalTheme {
        Surface {
            MealEditorContent(
                state = MealEditorState(
                    date = today(),
                    editingMeal = Meal(
                        id = 1,
                        date = today(),
                        type = MealType.LUNCH,
                        description = "Salade composée et poulet grillé",
                    ),
                    type = MealType.LUNCH,
                    description = "Salade composée et poulet grillé",
                ),
                onDescriptionChanged = {},
                onPhotoRemoved = {},
                onPickPhoto = {},
                onProductChanged = {},
                onTypeChanged = {},
                onSave = {},
                onDelete = {},
                onScan = {},
            )
        }
    }
}
