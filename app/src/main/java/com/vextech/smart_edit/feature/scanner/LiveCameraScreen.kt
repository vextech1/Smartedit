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
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.ImageScrim
import com.vextech.smart_edit.feature.scanner.domain.CropQuad
import com.vextech.smart_edit.feature.scanner.domain.NormalizedPoint
import com.vextech.smart_edit.feature.scanner.domain.PageQuality
import java.util.concurrent.Executors
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
fun LiveCameraScreen(sessionId: String, onBack: () -> Unit, share: ShareService, vm: LiveCameraViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val message by vm.message.collectAsState()
    val assist by vm.assist.collectAsState()
    val exporting by vm.exporting.collectAsState()
    val retake by vm.retake.collectAsState()
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
    var autoOn by rememberSaveable { mutableStateOf(true) }
    var shutterFlash by remember { mutableStateOf(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val pages = session?.pages.orEmpty()

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
            val useCases = mutableListOf<UseCase>(preview, imageCapture)
            if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                // Low-resolution brightness frames for edge detection. Same 16:9 shape as the preview so the outline lines up.
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(ResolutionStrategy(Size(640, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { a ->
                        a.setAnalyzer(analysisExecutor) { image ->
                            try { vm.analyze(CameraFrames.luma(image), image.imageInfo.rotationDegrees) } finally { image.close() }
                        }
                    }
                useCases += analysis
            } else vm.clearAssist()
            cp.unbindAll()
            camera = cp.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
            capture = imageCapture
            zoomRatio = 1f
        }.onFailure { vm.error(it.message ?: "Unable to start camera") }
    }

    LaunchedEffect(camera) { if (camera?.cameraInfo?.hasFlashUnit() != true) flashMode = ImageCapture.FLASH_MODE_OFF }
    LaunchedEffect(flashMode, capture) { capture?.flashMode = flashMode }
    LaunchedEffect(message) { if (message != null) { delay(2000); vm.clearMessage() } }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll(); analysisExecutor.shutdown() } }
    LaunchedEffect(autoOn) { vm.setAuto(autoOn) }
    LaunchedEffect(shutterFlash) { if (shutterFlash) { delay(120); shutterFlash = false } }

    fun takePhoto() {
        val imageCapture = capture ?: return
        val quad = vm.snapshotQuad()          // outline at the moment of the shot; the page is pre-cropped to it
        vm.noteCapture()
        val file = vm.createOutputFile()
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        shutterFlash = true
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { vm.registerCapture(sessionId, file, quad) }
                override fun onError(exception: ImageCaptureException) { file.delete(); vm.error(exception.message ?: "Capture failed") }
            },
        )
    }
    val currentTakePhoto by rememberUpdatedState({ takePhoto() })
    LaunchedEffect(Unit) { vm.autoShutter.collect { if (autoOn && !exporting) currentTakePhoto() } }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!permissionGranted) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.scanner_camera_permission), color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.scanner_camera_allow)) }
                }
            } else {
                Box(Modifier.align(Alignment.Center).aspectRatio(assist.aspect)) {
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
                    val outline = assist.quad
                    if (outline != null && lensFacing == CameraSelector.LENS_FACING_BACK) PageOutline(outline, if (autoOn) assist.progress else 0f, Modifier.fillMaxSize())
                    else GuideFrame(Modifier.fillMaxSize(), MaterialTheme.colorScheme.primary)
                }
            }

            // Top bar
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                CameraIconButton(onClick = onBack) { Icon(Icons.Rounded.Close, stringResource(R.string.scanner_camera_close)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CameraIconButton(onClick = { autoOn = !autoOn }, enabled = lensFacing == CameraSelector.LENS_FACING_BACK, active = autoOn) {
                        Icon(Icons.Rounded.AutoFixHigh, stringResource(R.string.scanner_camera_auto))
                    }
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
                    message ?: stringResource(
                        when (assist.hint) {
                            CameraHint.ALIGN -> R.string.scanner_camera_hint
                            CameraHint.MOVE_CLOSER -> R.string.scanner_camera_hint_closer
                            CameraHint.HOLD_STEADY -> R.string.scanner_camera_hint_hold
                            CameraHint.TOO_DARK -> R.string.scanner_camera_hint_dark
                        },
                    ),
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelLarge,
                )
            }

            retake?.let { problem ->
                LaunchedEffect(problem, pages.size) { delay(5_000); vm.dismissRetake() }
                Surface(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 112.dp),
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (problem == PageQuality.DARK) R.string.scanner_camera_dark else R.string.scanner_camera_blurry),
                            color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge,
                        )
                        TextButton(onClick = { vm.deleteLast(sessionId) }) { Text(stringResource(R.string.scanner_camera_retake)) }
                    }
                }
            }
            if (shutterFlash) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.45f)))
            if (exporting) CircularProgressIndicator(Modifier.align(Alignment.Center))

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

        // Batch strip: every page captured so far; the newest can be removed
        if (pages.isNotEmpty()) {
            val stripState = rememberLazyListState()
            LaunchedEffect(pages.size) { stripState.animateScrollToItem(pages.lastIndex) }
            LazyRow(
                Modifier.fillMaxWidth().background(Color.Black).padding(top = 10.dp),
                state = stripState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                    val newest = index == pages.lastIndex
                    val shape = RoundedCornerShape(8.dp)
                    Box(Modifier.size(width = 46.dp, height = 60.dp)) {
                        ScanThumbnail(
                            page.renderedUri,
                            Modifier.fillMaxSize().clip(shape).border(if (newest) 2.dp else 1.dp, if (newest) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f), shape),
                            version = page.updatedAt, maxDim = 128,
                        )
                        if (newest) {
                            Surface(onClick = { vm.deleteLast(sessionId) }, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp), shape = CircleShape, color = Scrim) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.scanner_camera_remove_last), Modifier.padding(3.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Bottom bar: quick PDF, shutter (with auto-capture progress), done
        Row(
            Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                OutlinedButton(
                    enabled = pages.isNotEmpty() && !exporting,
                    onClick = {
                        vm.exportPdf(sessionId) { file ->
                            if (file != null) { runCatching { share.share(file, "application/pdf") }; onBack() }
                            else vm.error(context.getString(R.string.scanner_error_export))
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.38f)),
                    modifier = Modifier.height(48.dp),
                ) { Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.scanner_camera_pdf)) }
            }
            val ready = capture != null && !exporting
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                if (autoOn && assist.progress > 0f) {
                    CircularProgressIndicator(progress = { assist.progress }, modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary, strokeWidth = 5.dp, trackColor = Color.Transparent)
                }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).border(4.dp, Color.White.copy(alpha = if (ready) 1f else 0.4f), CircleShape)
                        .clickable(enabled = ready, onClick = ::takePhoto)
                        .semantics { contentDescription = "capture" },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = if (ready) 1f else 0.4f))) }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Button(onClick = onBack, modifier = Modifier.height(48.dp)) {
                    Text(if (pages.isEmpty()) stringResource(R.string.scanner_camera_done) else stringResource(R.string.scanner_camera_done_count, pages.size))
                    Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

/** The detected page outline. It thickens and fills as the page is held steady, up to the auto-capture. */
@Composable
private fun PageOutline(quad: CropQuad, progress: Float, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        fun at(p: NormalizedPoint) = Offset(p.x * size.width, p.y * size.height)
        val path = Path().apply {
            moveTo(at(quad.topLeft).x, at(quad.topLeft).y)
            lineTo(at(quad.topRight).x, at(quad.topRight).y)
            lineTo(at(quad.bottomRight).x, at(quad.bottomRight).y)
            lineTo(at(quad.bottomLeft).x, at(quad.bottomLeft).y)
            close()
        }
        drawPath(path, color.copy(alpha = 0.10f + 0.20f * progress))
        drawPath(path, color, style = Stroke(width = (3f + 3f * progress).dp.toPx(), join = StrokeJoin.Round))
        listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft).forEach { drawCircle(Color.White, 5.dp.toPx(), at(it)) }
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
