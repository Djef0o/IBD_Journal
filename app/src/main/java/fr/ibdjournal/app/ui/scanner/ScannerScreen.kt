package fr.ibdjournal.app.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Product
import fr.ibdjournal.app.ui.calendar.ScannedProductCard
import java.util.concurrent.Executors

/**
 * Full-screen scanner: CameraX + ZXing, then product lookup
 * on Open Food Facts.
 *
 * Shown in a [Dialog]: the meal edit sheet is itself a window
 * above the activity. Drawn inside the screen, the scanner would end up beneath it, and
 * tapping the camera would close the sheet (and its draft) via its scrim.
 */
@Composable
fun ScannerScreen(
    onClose: () -> Unit,
    onProductScanned: (Product) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        onDispose { viewModel.reset() }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        ScannerContent(
            state = state,
            onClose = onClose,
            onProductScanned = onProductScanned,
            onBarcodeDetected = viewModel::onBarcodeDetected,
            cameraPreview = { onDetected ->
                CameraPreview(
                    onBarcodeDetected = onDetected,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        )
    }
}

@Composable
fun ScannerContent(
    state: ScanState,
    onClose: () -> Unit,
    onProductScanned: (Product) -> Unit,
    onBarcodeDetected: (String) -> Unit,
    cameraPreview: @Composable (onBarcodeDetected: (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        cameraPreview(onBarcodeDetected)

        // Top bar.
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.scan_close),
                    tint = Color.White,
                )
            }
            Text(
                text = stringResource(R.string.scan_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(48.dp))
        }

        // Scan frame.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .border(2.dp, Color.White, RoundedCornerShape(16.dp)),
        )

        // Hint.
        Text(
            text = stringResource(R.string.scan_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
                .padding(horizontal = 32.dp),
        )

        // Result.
        when (val scanState = state) {
            ScanState.Waiting -> Unit

            ScanState.Scanning -> ResultCard(Modifier.align(Alignment.BottomCenter)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.scanning))
                }
            }

            is ScanState.Found -> ResultCard(Modifier.align(Alignment.BottomCenter)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.product_found),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ScannedProductCard(product = scanState.product, onRemove = {})
                    Button(
                        onClick = { onProductScanned(scanState.product) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.use_product))
                    }
                }
            }

            is ScanState.NotFound -> ResultCard(Modifier.align(Alignment.BottomCenter)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.product_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = scanState.barcode,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(
                        onClick = {
                            onProductScanned(
                                Product(scanState.barcode, null, null, null, null, null)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.use_barcode_anyway))
                    }
                }
            }
        }
    }
}

@ComposePreview
@Composable
private fun ScannerContentPreview() {
    IBDJournalTheme {
        ScannerContent(
            state = ScanState.Found(
                Product(
                    barcode = "3017620422003",
                    name = "Nutella",
                    brand = "Ferrero",
                    nutriscore = "e",
                    imageUrl = null,
                    ingredients = null
                )
            ),
            onClose = {},
            onProductScanned = {},
            onBarcodeDetected = {},
            cameraPreview = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aperçu Caméra (Preview)", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun ResultCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

/** CameraX camera preview wired to ZXing barcode analysis. */
@Composable
private fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!permissionGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.camera_permission_needed),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.grant_permission))
            }
        }
        return
    }

    val currentOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)
    val previewView = remember { PreviewView(context) }

    // The camera is bound to the activity lifecycle, not to this composable: without
    // unbindAll() on exit, it would stay on (and analysis active) after
    // the scanner closes, until the activity stops.
    DisposableEffect(lifecycleOwner) {
        // Analysis off the main thread; the result is posted back to main.
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener(
            {
                if (disposed) return@addListener
                val cameraProvider = providerFuture.get().also { provider = it }
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(
                    analysisExecutor,
                    BarcodeAnalyzer { code ->
                        mainExecutor.execute { if (!disposed) currentOnBarcodeDetected(code) }
                    },
                )
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            provider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )
}
