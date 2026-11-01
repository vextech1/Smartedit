package com.vextech.smart_edit.feature.compressor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ads.ExportInterstitialEffect
import com.vextech.smart_edit.core.ads.UnityAdsService
import com.vextech.smart_edit.core.library.CompressionKind
import com.vextech.smart_edit.core.library.CompressionPolicies
import com.vextech.smart_edit.core.library.CompressionPreset
import com.vextech.smart_edit.core.library.CompressionVerdict
import com.vextech.smart_edit.core.library.PdfCompressionMode
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.BottomAction
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.ProgressCard
import com.vextech.smart_edit.core.ui.SaveToDeviceAction
import com.vextech.smart_edit.core.ui.SourcePickerRow
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import com.vextech.smart_edit.core.ui.UriThumbnail
import com.vextech.smart_edit.core.ui.formatBytes
import com.vextech.smart_edit.core.ui.rememberSaveToDevice

private val pickerTypes = arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressorScreen(
    onBack: () -> Unit,
    share: ShareService,
    ads: UnityAdsService,
    initialUri: String? = null,
    vm: CompressorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(initialUri) { initialUri?.let { vm.preselect(android.net.Uri.parse(it)) } }
    val snackbarHostState = remember { SnackbarHostState() }
    val screenEvents = remember { UiEventChannel() }
    CollectUiEvents(vm.uiEvents, snackbarHostState)
    CollectUiEvents(screenEvents.events, snackbarHostState)
    val saveToDevice = rememberSaveToDevice(share) { screenEvents.message(it) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addFiles(it) }
    ExportInterstitialEffect(state.keptResults.firstOrNull()?.outputUri, ads)

    FeatureScaffold(
        title = stringResource(R.string.compressor),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when {
                    state.files.isEmpty() -> SourcePickerRow(
                        title = stringResource(R.string.compressor_choose_files),
                        subtitle = stringResource(R.string.compressor_supported_types),
                        icon = Icons.AutoMirrored.Rounded.NoteAdd,
                        onClick = { picker.launch(pickerTypes) },
                    )
                    state.finished -> ResultsContent(state, share, saveToDevice, screenEvents)
                    else -> SetupContent(state, vm, onAdd = { picker.launch(pickerTypes) })
                }
            }
            when {
                state.running -> BottomAction {
                    ProgressCard(
                        label = stringResource(R.string.compressor_progress, state.currentIndex + 1, state.files.size, state.currentName.orEmpty()),
                        progress = state.progress,
                        onCancel = vm::cancel,
                    )
                }
                state.finished -> BottomAction {
                    Button(onClick = vm::reset, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(Icons.Rounded.Compress, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.compressor_compress_more))
                    }
                }
                state.files.isNotEmpty() -> BottomAction {
                    Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(Icons.Rounded.Compress, null); Spacer(Modifier.width(8.dp))
                        Text(pluralStringResource(R.plurals.compressor_start_files, state.files.size, state.files.size))
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Setup ─────────────────────────────

@Composable
private fun SetupContent(state: CompressorUiState, vm: CompressorViewModel, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.compressor_files_summary, state.files.size, formatBytes(state.files.sumOf { it.sizeBytes })))
        state.files.forEach { file -> FileCard(file, removable = !state.running) { vm.remove(file) } }
        if (!state.running) {
            Surface(
                onClick = onAdd, shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.Color.Transparent,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.compressor_add_more), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringResource(R.string.compressor_quality_title))
        CompressionPreset.entries.forEach { preset ->
            PresetRow(preset, selected = state.preset == preset, enabled = !state.running) { vm.setPreset(preset) }
        }
    }

    if (state.hasPdf) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.compressor_pdf_mode_title))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PdfCompressionMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.pdfMode == mode,
                        onClick = { vm.setPdfMode(mode) },
                        enabled = !state.running,
                        shape = SegmentedButtonDefaults.itemShape(index, PdfCompressionMode.entries.size),
                    ) {
                        Text(stringResource(if (mode == PdfCompressionMode.STANDARD) R.string.compressor_mode_standard else R.string.compressor_mode_maximum))
                    }
                }
            }
            Text(
                stringResource(if (state.pdfMode == PdfCompressionMode.STANDARD) R.string.compressor_mode_standard_hint else R.string.compressor_mode_maximum_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.pdfMode == PdfCompressionMode.MAXIMUM) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileCard(file: CompressorFile, removable: Boolean, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FileThumb(file.kind, file.uri)
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                if (file.sizeBytes > 0) Text(formatBytes(file.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (removable) {
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.compressor_remove_file), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** A PDF shows an icon; an image shows its own preview. */
@Composable
private fun FileThumb(kind: CompressionKind, uri: String) {
    if (kind == CompressionKind.PDF) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary) }
        }
    } else {
        UriThumbnail(uri, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
    }
}

@Composable
private fun PresetRow(preset: CompressionPreset, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val (title, description) = when (preset) {
        CompressionPreset.HIGH -> R.string.compressor_preset_high to R.string.compressor_preset_high_desc
        CompressionPreset.BALANCED -> R.string.compressor_preset_balanced to R.string.compressor_preset_balanced_desc
        CompressionPreset.SMALLER -> R.string.compressor_preset_smaller to R.string.compressor_preset_smaller_desc
    }
    Surface(
        onClick = onSelect, enabled = enabled, shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ───────────────────────────── Results ─────────────────────────────

@Composable
private fun ResultsContent(state: CompressorUiState, share: ShareService, saveToDevice: SaveToDeviceAction, events: UiEventChannel) {
    if (state.keptResults.isNotEmpty()) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
                Column {
                    Text(stringResource(R.string.compressor_total_saved_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatBytes(state.totalSaved), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.results.forEach { result -> ResultBlock(result, share, saveToDevice, events) }
    }
}

@Composable
private fun ResultBlock(result: CompressorResult, share: ShareService, saveToDevice: SaveToDeviceAction, events: UiEventChannel) {
    val kept = result.outputUri != null
    val failed = result.error != null
    val barely = kept && result.verdict == CompressionVerdict.UNCHANGED
    val percent = if (kept) CompressionPolicies.percentSaved(result.originalBytes, result.compressedBytes) else 0
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(result.outputName ?: result.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        when {
                            failed -> result.error.asString()
                            !kept -> stringResource(R.string.compressor_already_optimized)
                            else -> stringResource(R.string.compressor_size_change, formatBytes(result.originalBytes), formatBytes(result.compressedBytes))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ResultChip(
                    text = when {
                        failed -> stringResource(R.string.compressor_chip_failed)
                        !kept -> stringResource(R.string.compressor_chip_skipped)
                        else -> stringResource(R.string.compressor_percent_saved, percent)
                    },
                    container = when { failed -> MaterialTheme.colorScheme.errorContainer; barely || !kept -> MaterialTheme.colorScheme.tertiaryContainer; else -> MaterialTheme.colorScheme.secondaryContainer },
                    content = when { failed -> MaterialTheme.colorScheme.onErrorContainer; barely || !kept -> MaterialTheme.colorScheme.onTertiaryContainer; else -> MaterialTheme.colorScheme.onSecondaryContainer },
                )
            }
            if (kept) {
                val uri = result.outputUri
                val mime = result.outputMime.orEmpty()
                val name = result.outputName.orEmpty()
                if (barely) Text(stringResource(R.string.compressor_barely_smaller, percent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { runCatching { share.openManaged(uri, mime) }.onFailure { events.message(UiText.res(R.string.common_open_failed)) } },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.compressor_open)) }
                    FilledTonalButton(
                        onClick = { runCatching { share.shareManaged(uri, mime) }.onFailure { events.message(UiText.res(R.string.common_share_failed)) } },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.compressor_share)) }
                    FilledTonalButton(onClick = { saveToDevice.save(uri, name, mime) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.compressor_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultChip(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
