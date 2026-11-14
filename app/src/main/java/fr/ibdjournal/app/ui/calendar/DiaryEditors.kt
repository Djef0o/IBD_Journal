package fr.ibdjournal.app.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.ui.scanner.ScannerScreen

/**
 * Feuilles d'édition (repas, selle) et scanner partagés par l'accueil et la journée.
 * Composable distinct : la saisie dans l'éditeur ne recompose que cette portée,
 * pas l'écran qui l'héberge.
 */
@Composable
fun DiaryEditors(viewModel: CalendarViewModel) {
    val mealEditor by viewModel.mealEditor.collectAsStateWithLifecycle()
    val stoolEditor by viewModel.stoolEditor.collectAsStateWithLifecycle()
    val showScanner by viewModel.showScanner.collectAsStateWithLifecycle()

    mealEditor?.let { editor ->
        MealEditorSheet(
            state = editor,
            onDismiss = viewModel::closeMealEditor,
            onSave = viewModel::saveMeal,
            onDelete = viewModel::deleteMeal,
            onScan = viewModel::openScanner,
            onDescriptionChanged = viewModel::onEditorDescriptionChanged,
            onPhotoPicked = viewModel::onEditorPhotoPicked,
            onPhotoRemoved = viewModel::onEditorPhotoRemoved,
            onProductChanged = viewModel::onEditorProductChanged,
            onTypeChanged = viewModel::onEditorTypeChanged,
        )
    }

    stoolEditor?.let { editor ->
        StoolSheet(
            stool = editor.stool,
            initialBristol = editor.initialBristol,
            onDismiss = viewModel::closeStoolSheet,
            onDelete = editor.stool?.let { stool ->
                {
                    viewModel.deleteStool(stool)
                    viewModel.closeStoolSheet()
                }
            },
            onSave = viewModel::saveStool,
        )
    }

    if (showScanner) {
        ScannerScreen(
            onClose = viewModel::closeScanner,
            onProductScanned = viewModel::onScannedProduct,
        )
    }
}
