package fr.ibdjournal.app.ui.care

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.labimport.LabReportReader

/**
 * Sous-écran de Soins : tous les résultats d'analyses (par analyse ou par bilan), saisie
 * manuelle et import de comptes rendus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabsScreen(onBack: () -> Unit, viewModel: CareViewModel = hiltViewModel()) {
    val labResults by viewModel.labResults.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val labImport by viewModel.labImport.collectAsStateWithLifecycle()
    var editLab by remember { mutableStateOf<LabEditing?>(null) }
    var labDetail by remember { mutableStateOf<LabTest?>(null) }
    val labHistories = remember(labResults) { labHistories(labResults) }
    // Le dernier résultat d'une analyse supprimé : son détail n'a plus rien à montrer.
    LaunchedEffect(labHistories, labDetail) {
        val test = labDetail
        if (test != null && test !in labHistories) labDetail = null
    }
    val reportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        viewModel::importLabReports,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lab_results_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = { editLab = LabEditing(null, labHistories.keys.firstOrNull() ?: LabTest.CRP) }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.lab_result_add))
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
        ) {
            if (labHistories.isEmpty()) {
                Text(
                    text = stringResource(R.string.lab_results_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                LabResultsViews(
                    histories = labHistories,
                    sex = sex,
                    onOpenTest = { labDetail = it },
                    onEditResult = { editLab = LabEditing(it, it.test) },
                )
            }
            // Lecture de comptes rendus PDF ou photos, sur l'appareil, puis vérification.
            OutlinedButton(
                onClick = { reportPicker.launch(arrayOf(LabReportReader.PDF_MIME, "image/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lab_import_reports_button))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Le détail s'efface pendant la saisie pour ne pas empiler deux feuilles, puis revient.
    labDetail?.let { test ->
        val history = labHistories[test]
        if (history != null && editLab == null) {
            LabResultDetailSheet(
                test = test,
                history = history,
                sex = sex,
                onDismiss = { labDetail = null },
                onAdd = { editLab = LabEditing(null, test) },
                onEdit = { editLab = LabEditing(it, it.test) },
            )
        }
    }
    editLab?.let { editing ->
        LabResultEditorSheet(
            editing = editing,
            onDismiss = { editLab = null },
            onSave = viewModel::saveLabResult,
            onDelete = viewModel::deleteLabResult,
        )
    }
    when (val state = labImport) {
        LabImportState.Idle -> Unit
        is LabImportState.Reading -> LabImportProgressDialog(done = state.done, total = state.total)
        is LabImportState.Review -> LabImportReviewSheet(
            documents = state.documents,
            existing = labResults,
            onDismiss = viewModel::closeLabImport,
            onSave = viewModel::saveImportedResults,
        )
    }
}
