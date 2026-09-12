package fr.ibdjournal.app.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import fr.ibdjournal.app.ui.theme.IBDJournalTheme
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Product
import fr.ibdjournal.app.ui.calendar.ScannedProductCard

/**
 * Scanner plein écran : CameraX + ML Kit, puis recherche du produit
 * sur Open Food Facts.
 */
@Composable
fun ScannerScreen(
    onClose: () -> Unit,
    onProductScanned: (Product) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onClose)

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

        // Barre supérieure.
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

        // Cadre de scan.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .border(2.dp, Color.White, RoundedCornerShape(16.dp)),
        )

        // Indication.
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

        // Résultat.
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

/** Aperçu caméra CameraX branché sur l'analyse ML Kit des codes-barres. */
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

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                )
                .build()
        )
    }
    DisposableEffect(Unit) {
        onDispose { scanner.close() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener(
                {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val input =
                                InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(input)
                                .addOnSuccessListener { barcodes ->
                                    barcodes.firstOrNull()?.rawValue?.let(onBarcodeDetected)
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                },
                executor,
            )
            previewView
        },
    )
}
