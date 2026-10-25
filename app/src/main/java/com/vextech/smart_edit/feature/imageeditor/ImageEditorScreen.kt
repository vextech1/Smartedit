package com.vextech.smart_edit.feature.imageeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ads.ExportInterstitialEffect
import com.vextech.smart_edit.core.ads.UnityAdsService
import com.vextech.smart_edit.core.library.AdjustParams
import com.vextech.smart_edit.core.library.CropRect
import com.vextech.smart_edit.core.library.ImageFilter
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.ResultCard
import com.vextech.smart_edit.core.ui.SectionHeader
import com.vextech.smart_edit.core.ui.SourcePickerRow
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import com.vextech.smart_edit.core.ui.rememberSaveToDevice

private val imageTypes = arrayOf("image/jpeg", "image/png", "image/webp")

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

    FeatureScaffold(
        title = stringResource(R.string.ie_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::undo, enabled = state.stack.canUndo && !state.saving) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.ie_undo))
            }
            IconButton(onClick = vm::redo, enabled = state.stack.canRedo && !state.saving) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.ie_redo))
            }
            IconButton(onClick = vm::save, enabled = !state.stack.isEmpty && !state.saving && state.tool == EditorTool.NONE) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.ie_save))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy || state.saving || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val bitmap = state.preview
            if (state.source == null) {
                SourcePickerRow(
                    title = stringResource(R.string.ie_choose_image),
                    subtitle = stringResource(R.string.ie_choose_image_hint),
                    icon = Icons.Rounded.AddPhotoAlternate,
                    enabled = !state.loading,
                    onClick = { picker.launch(imageTypes) },
                )
            } else if (bitmap != null) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.ie_preview_description),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                        )
                        if (state.tool == EditorTool.CROP) CropOverlay(state.crop, Modifier.fillMaxSize())
                    }
                }
                state.saved?.let { saved ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        ResultCard(
                            name = saved.name,
                            sizeBytes = saved.sizeBytes,
                            onOpen = {
                                runCatching { share.openManaged(saved.uri, saved.mimeType) }
                                    .onFailure { screenEvents.message(UiText.res(R.string.common_open_failed)) }
                            },
                            onShare = {
                                runCatching { share.shareManaged(saved.uri, saved.mimeType) }
                                    .onFailure { screenEvents.message(UiText.res(R.string.common_share_failed)) }
                            },
                            onSaveToDevice = { saveToDevice.save(saved.uri, saved.name, saved.mimeType) },
                        )
                    }
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (state.tool) {
                        EditorTool.CROP -> CropPanel(state.crop, onChange = vm::setCrop, onApply = vm::applyCrop, onCancel = vm::closeTool)
                        EditorTool.ADJUST -> AdjustPanel(state.adjust, onChange = vm::setAdjust, onApply = vm::applyAdjust, onCancel = vm::closeTool)
                        EditorTool.NONE -> ToolBar(state, vm, enabled = !state.busy && !state.saving)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolBar(state: ImageEditorUiState, vm: ImageEditorViewModel, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolButton(Icons.Rounded.RotateLeft, R.string.ie_rotate_left, enabled, vm::rotateLeft)
            ToolButton(Icons.Rounded.RotateRight, R.string.ie_rotate_right, enabled, vm::rotateRight)
            ToolButton(Icons.Rounded.Crop, R.string.ie_crop, enabled, vm::openCrop)
            ToolButton(Icons.Rounded.Tune, R.string.ie_adjust, enabled, vm::openAdjust)
            ToolButton(Icons.Rounded.AutoFixHigh, R.string.ie_remove_background, enabled, vm::removeBackground)
        }
        SectionHeader(stringResource(R.string.ie_filters_title))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val current = state.stack.currentFilter()
            listOf(
                ImageFilter.NONE to R.string.ie_filter_original,
                ImageFilter.GRAYSCALE to R.string.ie_filter_grayscale,
                ImageFilter.BLACK_WHITE to R.string.ie_filter_bw,
            ).forEach { (filter, label) ->
                FilterChip(selected = current == filter, onClick = { vm.setFilter(filter) }, enabled = enabled, label = { Text(stringResource(label)) })
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CropOverlay(crop: CropRect, modifier: Modifier) {
    val shade = Color.Black.copy(alpha = 0.55f)
    val line = Color.White
    Canvas(modifier) {
        val l = size.width * crop.left
        val t = size.height * crop.top
        val r = size.width * crop.right
        val b = size.height * crop.bottom
        drawRect(shade, Offset.Zero, Size(size.width, t))
        drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
        drawRect(shade, Offset(0f, t), Size(l, b - t))
        drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
        drawRect(line, Offset(l, t), Size(r - l, b - t), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun CropPanel(crop: CropRect, onChange: (Float, Float, Float, Float) -> Unit, onApply: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LabeledSlider(R.string.ie_crop_left, crop.left, 0f..1f) { onChange(it, crop.top, crop.right, crop.bottom) }
        LabeledSlider(R.string.ie_crop_top, crop.top, 0f..1f) { onChange(crop.left, it, crop.right, crop.bottom) }
        LabeledSlider(R.string.ie_crop_right, crop.right, 0f..1f) { onChange(crop.left, crop.top, it, crop.bottom) }
        LabeledSlider(R.string.ie_crop_bottom, crop.bottom, 0f..1f) { onChange(crop.left, crop.top, crop.right, it) }
        PanelButtons(onApply, onCancel, onReset = { onChange(0f, 0f, 1f, 1f) }, applyEnabled = !crop.isFull)
    }
}

@Composable
private fun AdjustPanel(adjust: AdjustParams, onChange: (AdjustParams) -> Unit, onApply: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LabeledSlider(R.string.ie_brightness, adjust.brightness, AdjustParams.BRIGHTNESS_RANGE) { onChange(adjust.copy(brightness = it)) }
        LabeledSlider(R.string.ie_contrast, adjust.contrast, AdjustParams.CONTRAST_RANGE) { onChange(adjust.copy(contrast = it)) }
        LabeledSlider(R.string.ie_saturation, adjust.saturation, AdjustParams.SATURATION_RANGE) { onChange(adjust.copy(saturation = it)) }
        PanelButtons(onApply, onCancel, onReset = { onChange(AdjustParams()) }, applyEnabled = true)
    }
}

@Composable
private fun LabeledSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun PanelButtons(onApply: () -> Unit, onCancel: () -> Unit, onReset: () -> Unit, applyEnabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ie_reset)) }
        Button(onClick = onApply, enabled = applyEnabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ie_apply)) }
    }
}
