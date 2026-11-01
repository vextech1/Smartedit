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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ui.ImageScrim
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class CaptureResolution(val label: String, val size: Size) {
    HD("HD", Size(1280, 720)),
    FULL_HD("Full HD", Size(1920, 1080)),
    HIGH("High", Size(2560, 1440)),
}

private val Scrim = ImageScrim

private suspend fun cameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        runCatching { future.get() }
            .onSuccess { if (continuation.isActive) continuation.resume(it) }
            .onFailure { if (continuation.isActive) continuation.resumeWithException(it) }
    }, ContextCompat.getMainExecutor(context))
}

/** Full-bleed live capture. [onBack] doubles as "Done": the session screen underneath already shows every captured page. */
@Composable
fun LiveCameraScreen(sessionId: String, onBack: () -> Unit, vm: LiveCameraViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val message by vm.message.collectAsState()
    val session by remember(sessionId) { vm.observeSession(sessionId) }.collectAsState(initial = null)
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var resolution by remember { mutableStateOf(CaptureResolution.FULL_HD) }
    var showGrid by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(permissionGranted, lensFacing, resolution) {
        if (!permissionGranted) return@LaunchedEffect
        runCatching {
            val cp = provider ?: cameraProvider(context).also { provider = it }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(resolution.size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
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
            zoomRatio = 1f
        }.onFailure { vm.error(it.message ?: "Unable to start camera") }
    }

    LaunchedEffect(camera) { if (camera?.cameraInfo?.hasFlashUnit() != true) flashMode = ImageCapture.FLASH_MODE_OFF }
    LaunchedEffect(flashMode, capture) { capture?.flashMode = flashMode }
    LaunchedEffect(message) { if (message != null) { delay(2000); vm.clearMessage() } }
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

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!permissionGranted) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.scanner_camera_permission), color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.scanner_camera_allow)) }
                }
            } else {
                surfaceRequest?.let { request ->
                    CameraXViewfinder(
                        surfaceRequest = request,
                        // CameraXViewfinder has no built-in pinch-to-zoom, so drive CameraX's zoom ourselves.
                        modifier = Modifier.fillMaxSize().pointerInput(camera) {
                            val activeCamera = camera ?: return@pointerInput
                            detectTransformGestures { _, _, zoomChange, _ ->
                                if (zoomChange == 1f) return@detectTransformGestures
                                val zoom = activeCamera.cameraInfo.zoomState.value ?: return@detectTransformGestures
                                val target = (zoom.zoomRatio * zoomChange).coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
                                activeCamera.cameraControl.setZoomRatio(target)
                                zoomRatio = target
                            }
                        },
                    )
                }
                if (showGrid) CameraGrid(Modifier.fillMaxSize())
                GuideFrame(Modifier.fillMaxSize(), MaterialTheme.colorScheme.primary)
            }

            // Top bar
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                CameraIconButton(onClick = onBack) { Icon(Icons.Rounded.Close, stringResource(R.string.scanner_camera_close)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CameraIconButton(
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
                            stringResource(R.string.scanner_camera_flash),
                        )
                    }
                    CameraIconButton(onClick = { showGrid = !showGrid }, active = showGrid) { Icon(Icons.Rounded.GridOn, stringResource(R.string.scanner_camera_grid)) }
                    CameraIconButton(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) {
                        Icon(Icons.Rounded.Cameraswitch, stringResource(R.string.scanner_camera_switch))
                    }
                }
            }

            // Hint / capture feedback
            Surface(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
                shape = RoundedCornerShape(16.dp), color = Scrim,
            ) {
                Text(
                    message ?: stringResource(R.string.scanner_camera_hint),
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelLarge,
                )
            }

            // Resolution selector and zoom readout
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                Row(Modifier.align(Alignment.BottomCenter).clip(CircleShape).background(Scrim).padding(4.dp)) {
                    CaptureResolution.entries.forEach { option ->
                        val selected = option == resolution
                        Text(
                            option.label,
                            Modifier.clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { resolution = option }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Surface(Modifier.align(Alignment.BottomEnd).size(48.dp), shape = CircleShape, color = Scrim) {
                    Box(contentAlignment = Alignment.Center) { Text("%.1f×".format(zoomRatio), color = Color.White, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }

        // Bottom bar: last capture + count, shutter, done
        Row(
            Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp).semantics { session?.pages?.size?.let { contentDescription = "$it" } }) {
                session?.pages?.lastOrNull()?.let { last ->
                    ScanThumbnail(last.renderedUri, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Color.White, RoundedCornerShape(12.dp)), version = last.updatedAt, maxDim = 256)
                    Surface(Modifier.align(Alignment.TopEnd), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Text("${session?.pages?.size ?: 0}", Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp).wrapContentSize(Alignment.Center).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            val ready = capture != null
            Box(
                Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White.copy(alpha = if (ready) 1f else 0.4f), CircleShape)
                    .clickable(enabled = ready, onClick = ::takePhoto)
                    .semantics { contentDescription = "capture" },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = if (ready) 1f else 0.4f))) }
            Button(onClick = onBack, modifier = Modifier.height(48.dp)) {
                Text(stringResource(R.string.scanner_camera_done)); Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CameraIconButton(onClick: () -> Unit, enabled: Boolean = true, active: Boolean = false, content: @Composable () -> Unit) {
    FilledIconButton(
        onClick = onClick, enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Scrim,
            contentColor = if (active) MaterialTheme.colorScheme.primary else Color.White,
            disabledContainerColor = Scrim, disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        content = content,
    )
}

/** Static alignment guide (not edge detection): teal corner brackets inset from the preview edges. */
@Composable
private fun GuideFrame(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val sw = 4.dp.toPx(); val len = 34.dp.toPx()
        val l = 28.dp.toPx(); val r = size.width - l; val t = size.height * 0.16f; val b = size.height * 0.84f
        fun seg(a: Offset, c: Offset) = drawLine(color, a, c, sw, StrokeCap.Round)
        seg(Offset(l, t), Offset(l + len, t)); seg(Offset(l, t), Offset(l, t + len))
        seg(Offset(r, t), Offset(r - len, t)); seg(Offset(r, t), Offset(r, t + len))
        seg(Offset(l, b), Offset(l + len, b)); seg(Offset(l, b), Offset(l, b - len))
        seg(Offset(r, b), Offset(r - len, b)); seg(Offset(r, b), Offset(r, b - len))
    }
}

@Composable
private fun CameraGrid(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.dp.toPx()
        val color = Color.White.copy(alpha = 0.45f)
        for (i in 1..2) {
            val x = size.width * i / 3f; val y = size.height * i / 3f
            drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}
