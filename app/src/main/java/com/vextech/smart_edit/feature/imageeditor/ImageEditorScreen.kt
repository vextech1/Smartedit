package com.vextech.smart_edit.feature.imageeditor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ads.ExportInterstitialEffect
import com.vextech.smart_edit.core.ads.UnityAdsService
import com.vextech.smart_edit.core.library.AdjustParams
import com.vextech.smart_edit.core.library.ImageFilter
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.BottomAction
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.CropOverlay
import com.vextech.smart_edit.core.ui.CropRect
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.ImageScrim
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import com.vextech.smart_edit.core.ui.UriThumbnail
import com.vextech.smart_edit.core.ui.formatBytes
import com.vextech.smart_edit.core.ui.rememberSaveToDevice
import com.vextech.smart_edit.core.ui.withAspect
import kotlin.math.roundToInt

private val imageTypes = arrayOf("image/jpeg", "image/png", "image/webp")

/** The strip shown above the dock: filter previews or the two rotate buttons. */
private enum class EditorPanel { FILTERS, ROTATE }

/** Crop shapes; [ratio] is width/height of the cropped picture. */
private enum class CropAspect(val ratio: Float?) { FREE(null), SQUARE(1f), R4_3(4f / 3f), R3_4(3f / 4f), R16_9(16f / 9f) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    onBack: () -> Unit,
    share: ShareService,
    ads: UnityAdsService,
    initialUri: String? = null,
    vm: ImageEditorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(initialUri) { initialUri?.let { vm.preselect(android.net.Uri.parse(it)) } }
    val snackbarHostState = remember { SnackbarHostState() }
    val screenEvents = remember { UiEventChannel() }
    CollectUiEvents(vm.uiEvents, snackbarHostState)
    CollectUiEvents(screenEvents.events, snackbarHostState)
    val saveToDevice = rememberSaveToDevice(share) { screenEvents.message(it) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::load) }
    ExportInterstitialEffect(state.saved?.uri, ads)

    var panel by remember { mutableStateOf(EditorPanel.FILTERS) }
    var comparing by remember { mutableStateOf(false) }
    var aspect by remember(state.tool) { mutableStateOf(CropAspect.FREE) }
    BackHandler(enabled = state.tool != EditorTool.NONE) { vm.closeTool() }

    val editing = state.source != null
    val toolOpen = state.tool != EditorTool.NONE
    val enabled = !state.busy && !state.saving

    FeatureScaffold(
        title = when (state.tool) {
            EditorTool.CROP -> stringResource(R.string.ie_crop)
            EditorTool.ADJUST -> stringResource(R.string.ie_adjust)
            EditorTool.NONE -> state.source?.name ?: stringResource(R.string.ie_title)
        },
        onBack = { if (toolOpen) vm.closeTool() else onBack() },
        actions = {
            if (editing && !toolOpen) {
                IconButton(onClick = vm::undo, enabled = state.stack.canUndo && !state.saving) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.ie_undo))
                }
                IconButton(onClick = vm::redo, enabled = state.stack.canRedo && !state.saving) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.ie_redo))
                }
                Button(onClick = vm::save, enabled = !state.stack.isEmpty && !state.saving, modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(R.string.ie_save))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy || state.saving || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val bitmap = state.preview
            if (!editing) {
                StartPicker(enabled = !state.loading) { picker.launch(imageTypes) }
            } else if (bitmap != null) {
                val shown = if (comparing) vm.originalPreview() ?: bitmap else bitmap
                val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = stringResource(R.string.ie_preview_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    if (state.tool == EditorTool.CROP) {
                        CropOverlay(
                            crop = CropRect(state.crop.left, state.crop.top, state.crop.right, state.crop.bottom),
                            imageAspect = imageAspect,
                            onCropChange = { vm.setCrop(it.left, it.top, it.right, it.bottom) },
                            modifier = Modifier.fillMaxSize(),
                            aspectLock = aspect.ratio,
                        )
                    } else {
                        CompareButton(Modifier.align(Alignment.BottomEnd)) { comparing = it }
                    }
                }
                when (state.tool) {
                    EditorTool.CROP -> CropPanel(
                        aspect = aspect,
                        onAspect = { next ->
                            aspect = next
                            next.ratio?.let { ratio ->
                                val fitted = CropRect(state.crop.left, state.crop.top, state.crop.right, state.crop.bottom).withAspect(ratio / imageAspect)
                                vm.setCrop(fitted.left, fitted.top, fitted.right, fitted.bottom)
                            }
                        },
                        applyEnabled = !state.crop.isFull,
                        onApply = vm::applyCrop, onCancel = vm::closeTool,
                        onReset = { aspect = CropAspect.FREE; vm.setCrop(0f, 0f, 1f, 1f) },
                    )
                    EditorTool.ADJUST -> AdjustPanel(state.adjust, vm::setAdjust, vm::applyAdjust, vm::closeTool)
                    EditorTool.NONE -> EditorBottom(
                        state = state, vm = vm, panel = panel, enabled = enabled,
                        onPanel = { panel = it },
                    )
                }
            }
        }
    }

    state.saved?.let { saved ->
        ModalBottomSheet(onDismissRequest = vm::dismissSaved) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UriThumbnail(saved.uri, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), maxDim = 200)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.ie_saved), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(saved.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(formatBytes(saved.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { runCatching { share.openManaged(saved.uri, saved.mimeType) }.onFailure { screenEvents.message(UiText.res(R.string.common_open_failed)) } },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.open)) }
                    FilledTonalButton(
                        onClick = { runCatching { share.shareManaged(saved.uri, saved.mimeType) }.onFailure { screenEvents.message(UiText.res(R.string.common_share_failed)) } },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share)) }
                    FilledTonalButton(onClick = { saveToDevice.save(saved.uri, saved.name, saved.mimeType) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ie_save))
                    }
                }
                TextButton(onClick = vm::dismissSaved, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ie_keep_editing)) }
            }
        }
    }
}

// ───────────────────────────── Start ─────────────────────────────

@Composable
private fun StartPicker(enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            onClick = onClick, enabled = enabled, shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(260.dp),
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(64.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Text(stringResource(R.string.ie_choose_image), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ie_choose_image_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.ie_start_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

// ───────────────────────────── Editing ─────────────────────────────

/** Pressing and holding shows the untouched picture; letting go returns to the edit. */
@Composable
private fun CompareButton(modifier: Modifier, onHold: (Boolean) -> Unit) {
    Surface(
        modifier = modifier.padding(4.dp).pointerInput(Unit) {
            detectTapGestures(onPress = {
                onHold(true)
                try { tryAwaitRelease() } finally { onHold(false) }
            })
        },
        shape = CircleShape, color = ImageScrim,
    ) {
        Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Compare, null, Modifier.size(18.dp), tint = Color.White)
            Text(stringResource(R.string.ie_hold_compare), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

/** Approximation shown in the filter previews; the saved picture uses the real filter. */
private fun filterPreview(filter: ImageFilter): ColorFilter? = when (filter) {
    ImageFilter.NONE -> null
    ImageFilter.GRAYSCALE -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    ImageFilter.BLACK_WHITE -> {
        val c = 2.2f
        val t = (-0.5f * c + 0.5f) * 255f
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    c * .213f, c * .715f, c * .072f, 0f, t,
                    c * .213f, c * .715f, c * .072f, 0f, t,
                    c * .213f, c * .715f, c * .072f, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }
}

@Composable
private fun EditorBottom(state: ImageEditorUiState, vm: ImageEditorViewModel, panel: EditorPanel, enabled: Boolean, onPanel: (EditorPanel) -> Unit) {
    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding()) {
            when (panel) {
                EditorPanel.FILTERS -> {
                    val current = state.stack.currentFilter()
                    val source = remember(state.source?.uri, state.preview) { vm.originalPreview()?.asImageBitmap() }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            ImageFilter.NONE to R.string.ie_filter_original,
                            ImageFilter.GRAYSCALE to R.string.ie_filter_grayscale,
                            ImageFilter.BLACK_WHITE to R.string.ie_filter_bw,
                        ).forEach { (filter, label) ->
                            val selected = current == filter
                            Column(
                                Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) { vm.setFilter(filter) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp), modifier = Modifier.size(72.dp),
                                    border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ) {
                                    source?.let { Image(it, null, Modifier.fillMaxSize().padding(if (selected) 3.dp else 0.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop, colorFilter = filterPreview(filter)) }
                                }
                                Text(
                                    stringResource(label), style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                EditorPanel.ROTATE -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = vm::rotateLeft, enabled = enabled, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.RotateLeft, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ie_rotate_left))
                    }
                    FilledTonalButton(onClick = vm::rotateRight, enabled = enabled, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ie_rotate_right))
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
                DockItem(Icons.Rounded.PhotoFilter, R.string.ie_filters_title, panel == EditorPanel.FILTERS, enabled) { onPanel(EditorPanel.FILTERS) }
                DockItem(Icons.Rounded.Tune, R.string.ie_adjust, false, enabled, vm::openAdjust)
                DockItem(Icons.Rounded.Crop, R.string.ie_crop, false, enabled, vm::openCrop)
                DockItem(Icons.Rounded.Rotate90DegreesCw, R.string.ie_rotate_title, panel == EditorPanel.ROTATE, enabled) { onPanel(EditorPanel.ROTATE) }
                DockItem(Icons.Rounded.AutoFixHigh, R.string.ie_remove_background, false, enabled, vm::removeBackground)
            }
        }
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: Int, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).defaultMinSize(minHeight = 56.dp).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ───────────────────────────── Crop and adjust panels ─────────────────────────────

@Composable
private fun CropPanel(aspect: CropAspect, onAspect: (CropAspect) -> Unit, applyEnabled: Boolean, onApply: () -> Unit, onCancel: () -> Unit, onReset: () -> Unit) {
    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CropAspect.entries.forEach { option ->
                    FilterChip(
                        selected = aspect == option, onClick = { onAspect(option) },
                        label = {
                            Text(
                                when (option) {
                                    CropAspect.FREE -> stringResource(R.string.ie_aspect_free)
                                    CropAspect.SQUARE -> "1:1"
                                    CropAspect.R4_3 -> "4:3"
                                    CropAspect.R3_4 -> "3:4"
                                    CropAspect.R16_9 -> "16:9"
                                }
                            )
                        },
                    )
                }
            }
            PanelButtons(onApply, onCancel, onReset, applyEnabled)
        }
    }
}

@Composable
private fun AdjustPanel(adjust: AdjustParams, onChange: (AdjustParams) -> Unit, onApply: () -> Unit, onCancel: () -> Unit) {
    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LabeledSlider(R.string.ie_brightness, adjust.brightness, AdjustParams.BRIGHTNESS_RANGE, shown = signed(adjust.brightness.roundToInt())) { onChange(adjust.copy(brightness = it)) }
            LabeledSlider(R.string.ie_contrast, adjust.contrast, AdjustParams.CONTRAST_RANGE, shown = signed(((adjust.contrast - 1f) * 100).roundToInt())) { onChange(adjust.copy(contrast = it)) }
            LabeledSlider(R.string.ie_saturation, adjust.saturation, AdjustParams.SATURATION_RANGE, shown = signed(((adjust.saturation - 1f) * 100).roundToInt())) { onChange(adjust.copy(saturation = it)) }
            Spacer(Modifier.height(8.dp))
            PanelButtons(onApply, onCancel, onReset = { onChange(AdjustParams()) }, applyEnabled = true)
        }
    }
}

private fun signed(value: Int): String = when {
    value > 0 -> "+$value"
    value < 0 -> "−${-value}"
    else -> "0"
}

@Composable
private fun LabeledSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            Text(shown, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun PanelButtons(onApply: () -> Unit, onCancel: () -> Unit, onReset: () -> Unit, applyEnabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.cancel)) }
        FilledTonalButton(onClick = onReset, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.ie_reset)) }
        Button(onClick = onApply, enabled = applyEnabled, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.ie_apply)) }
    }
}
