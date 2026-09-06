package com.sellernest.poreceiving.scan.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone
import com.sellernest.poreceiving.ui.theme.TouchTarget
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * M4.4: still-photo capture, distinct from [CameraScannerScreen] (which never
 * takes a picture -- it only decodes barcodes from the live feed). Every
 * captured file is downscaled in place via [PhotoDownscaler] before
 * [onCaptured] fires, so a caller never has to know the pre-downscale size
 * existed at all.
 */
@Composable
fun PhotoCaptureScreen(onCaptured: (localFilePath: String) -> Unit, onClose: () -> Unit) {
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
        hasPermission -> PhotoCapturePreview(onCaptured = onCaptured, onClose = onClose)
        permissionDenied -> PhotoPermissionDeniedContent(onClose = onClose)
        else -> Unit
    }
}

@Composable
private fun PhotoPermissionDeniedContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateBadge(tone = StateTone.Error, icon = Icons.Filled.Warning, label = "Camera permission denied.")
        PrimaryButton(text = "CLOSE", onClick = onClose)
    }
}

@Composable
private fun PhotoCapturePreview(onCaptured: (localFilePath: String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var capturing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val cameraProvider = context.awaitCameraProvider()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
    }

    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            captureExecutor.shutdown()
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close camera")
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                errorMessage?.let { message ->
                    StateBadge(tone = StateTone.Error, icon = Icons.Filled.Warning, label = message)
                }
                IconButton(
                    enabled = !capturing,
                    modifier = Modifier.size(TouchTarget.primary),
                    onClick = {
                        capturing = true
                        errorMessage = null
                        coroutineScope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    val captured = imageCapture.capturePhotoTo(context, captureExecutor)
                                    PhotoDownscaler.downscaleInPlace(captured)
                                    captured
                                }
                                onCaptured(file.absolutePath)
                            } catch (exception: Exception) {
                                // Covers a failed takePicture() call (ImageCaptureException)
                                // as well as a failed downscale (disk full, corrupt JPEG) --
                                // either way, the receiver just retries the shutter.
                                errorMessage = "Couldn't capture that photo. Try again."
                            } finally {
                                capturing = false
                            }
                        }
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Camera, contentDescription = "Take photo")
                }
                Text(text = if (capturing) "Saving..." else "TAP TO CAPTURE")
            }
        }
    }
}

private suspend fun ImageCapture.capturePhotoTo(context: Context, executor: Executor): File {
    val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(photosDir, "photo_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    return suspendCancellableCoroutine { continuation ->
        takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    continuation.resume(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            },
        )
    }
}
