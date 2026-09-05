package com.sellernest.poreceiving.scan.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.sellernest.poreceiving.scan.ScanSource
import com.sellernest.poreceiving.scan.compose.rememberScanDispatcher
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone
import java.util.concurrent.Executors

/**
 * §4.1, §4.3, §2.2. Opened only by explicit user action (never auto-opened --
 * enforced by callers, not this composable itself, which simply *is* the
 * camera once placed in composition) and torn down -- including the camera
 * binding -- the moment it leaves composition.
 */
@Composable
fun CameraScannerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    when {
        hasPermission -> CameraPreviewWithScanning(onClose = onClose)
        permissionDenied -> CameraPermissionDeniedContent(onClose = onClose)
        // Waiting on the system permission dialog: render nothing yet.
        else -> Unit
    }
}

/** Internal, not private: exercised directly by
 *  `CameraScannerScreenInstrumentedTest` without needing camera permission
 *  or hardware. */
@Composable
internal fun CameraPermissionDeniedContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateBadge(
            tone = StateTone.Error,
            icon = Icons.Filled.Warning,
            label = "Camera permission denied. Hardware trigger scanning is still fully available.",
        )
        PrimaryButton(text = "CLOSE", onClick = onClose)
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreviewWithScanning(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dispatcher = rememberScanDispatcher()
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    allowedCameraBarcodeFormats.first(),
                    *allowedCameraBarcodeFormats.drop(1).toIntArray(),
                )
                .build(),
        )
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val cameraProvider = context.awaitCameraProvider()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysisUseCase ->
                analysisUseCase.setAnalyzer(analysisExecutor) { imageProxy ->
                    analyzeFrameForBarcode(barcodeScanner, imageProxy) { code ->
                        dispatcher.dispatch(code, ScanSource.CAMERA)
                    }
                }
            }

        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            // Camera is released the moment the scanner leaves composition.
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            analysisExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close scanner")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                IconButton(
                    onClick = {
                        torchOn = !torchOn
                        camera?.cameraControl?.enableTorch(torchOn)
                    },
                ) {
                    Icon(imageVector = Icons.Filled.FlashOn, contentDescription = "Toggle torch")
                }
                Text(text = "ZOOM", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = zoomRatio,
                    onValueChange = { ratio ->
                        zoomRatio = ratio
                        camera?.cameraControl?.setLinearZoom(ratio)
                    },
                    valueRange = 0f..1f,
                )
            }
        }
    }
}
