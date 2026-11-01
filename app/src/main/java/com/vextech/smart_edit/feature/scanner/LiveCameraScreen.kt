package com.vextech.smart_edit.feature.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.core.ui.FeatureScaffold
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class CaptureResolution(val label: String, val size: Size) {
    HD("HD", Size(1280, 720)),
    FULL_HD("Full HD", Size(1920, 1080)),
    HIGH("High", Size(2560, 1440)),
}

private suspend fun cameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        runCatching { future.get() }
            .onSuccess { if (continuation.isActive) continuation.resume(it) }
            .onFailure { if (continuation.isActive) continuation.resumeWithException(it) }
    }, ContextCompat.getMainExecutor(context))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCameraScreen(sessionId: String, onBack: () -> Unit, vm: LiveCameraViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val message by vm.message.collectAsState()
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var resolution by remember { mutableStateOf(CaptureResolution.FULL_HD) }
    var showGrid by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var resolutionMenu by remember { mutableStateOf(false) }

    LaunchedEffect(permissionGranted, lensFacing, resolution) {
        if (!permissionGranted) return@LaunchedEffect
        runCatching {
            val cp = provider ?: cameraProvider(context).also { provider = it }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        resolution.size,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    )
                )
                .build()
            val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build().also {
                it.setSurfaceProvider { request -> surfaceRequest = request }
            }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.flashMode = flashMode }
            cp.unbindAll()
            camera = cp.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            capture = imageCapture
        }.onFailure { vm.error(it.message ?: "Unable to start camera") }
    }

    LaunchedEffect(camera) { if (camera?.cameraInfo?.hasFlashUnit() != true) flashMode = ImageCapture.FLASH_MODE_OFF }
    LaunchedEffect(flashMode, capture) { capture?.flashMode = flashMode }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }

    fun takePhoto() {
        val imageCapture = capture ?: return
        val file = vm.createOutputFile()
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { vm.registerCapture(sessionId, file) }
                override fun onError(exception: ImageCaptureException) { file.delete(); vm.error(exception.message ?: "Capture failed") }
            },
        )
    }

    FeatureScaffold("Live camera", onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!permissionGranted) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera access is required for live capture.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                }
            } else {
                surfaceRequest?.let { request -> CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize(), pinchToZoomEnabled = true) }
                if (showGrid) CameraGrid(Modifier.fillMaxSize())
                Row(
                    Modifier.align(Alignment.TopCenter).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledTonalIconButton(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) {
                        Icon(Icons.Rounded.Cameraswitch, "Switch camera")
                    }
                    FilledTonalIconButton(
                        enabled = camera?.cameraInfo?.hasFlashUnit() == true,
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        },
                    ) {
                        Icon(
                            when (flashMode) { ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto; ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn; else -> Icons.Rounded.FlashOff },
                            "Flash mode",
                        )
                    }
                    FilledTonalIconButton(onClick = { showGrid = !showGrid }) { Icon(Icons.Rounded.GridOn, "Grid") }
                    Box {
                        FilledTonalButton(onClick = { resolutionMenu = true }) { Text(resolution.label) }
                        DropdownMenu(expanded = resolutionMenu, onDismissRequest = { resolutionMenu = false }) {
                            CaptureResolution.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = { resolution = option; resolutionMenu = false })
                            }
                        }
                    }
                }
                Button(onClick = ::takePhoto, enabled = capture != null, modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp)) { Text("Capture") }
                message?.let { Text(it, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 82.dp), color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
    }
}

@Composable
private fun CameraGrid(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.dp.toPx()
        val color = Color.White.copy(alpha = 0.45f)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(size.width / 3f, 0f), end = androidx.compose.ui.geometry.Offset(size.width / 3f, size.height), strokeWidth = stroke)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, 0f), end = androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, size.height), strokeWidth = stroke)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, size.height / 3f), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 3f), strokeWidth = stroke)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, size.height * 2f / 3f), end = androidx.compose.ui.geometry.Offset(size.width, size.height * 2f / 3f), strokeWidth = stroke)
    }
}
