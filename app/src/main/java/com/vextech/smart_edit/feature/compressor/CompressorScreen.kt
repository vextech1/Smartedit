package com.vextech.smart_edit.feature.compressor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ads.ExportInterstitialEffect
import com.vextech.smart_edit.core.ads.UnityAdsService
import com.vextech.smart_edit.core.library.CompressionKind
import com.vextech.smart_edit.core.library.CompressionPolicies
import com.vextech.smart_edit.core.library.CompressionPreset
import com.vextech.smart_edit.core.library.CompressionVerdict
import com.vextech.smart_edit.core.library.PdfCompressionMode
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.ErrorText
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.ProgressCard
import com.vextech.smart_edit.core.ui.ResultCard
import com.vextech.smart_edit.core.ui.SectionHeader
import com.vextech.smart_edit.core.ui.SourcePickerRow
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.files.isEmpty()) {
                SourcePickerRow(
                    title = stringResource(R.string.compressor_choose_files),
                    subtitle = stringResource(R.string.compressor_supported_types),
                    icon = Icons.Rounded.NoteAdd,
                    onClick = { picker.launch(pickerTypes) },
                )
            } else {
                SectionHeader(stringResource(R.string.compressor_selected_files))
                state.files.forEach { file ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (file.kind == CompressionKind.PDF) Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                            contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(file.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (file.sizeBytes > 0) Text(formatBytes(file.sizeBytes), style = MaterialTheme.typography.bodySmall)
                        }
                        if (!state.running) {
                            IconButton(onClick = { vm.remove(file) }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.compressor_remove_file))
                            }
                        }
                    }
                }
                if (!state.running) {
                    OutlinedButton(
                        onClick = { picker.launch(pickerTypes) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) { Text(stringResource(R.string.compressor_add_more)) }
                }
            }

            SectionHeader(stringResource(R.string.compressor_quality_title))
            CompressionPreset.entries.forEach { preset ->
                PresetRow(preset, selected = state.preset == preset, enabled = !state.running) { vm.setPreset(preset) }
            }

            if (state.hasPdf) {
                SectionHeader(stringResource(R.string.compressor_pdf_mode_title))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.pdfMode == PdfCompressionMode.MAXIMUM) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.running) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    ProgressCard(
                        label = stringResource(R.string.compressor_progress, state.currentIndex + 1, state.files.size, state.currentName.orEmpty()),
                        progress = state.progress,
                        onCancel = vm::cancel,
                    )
                }
            } else if (state.files.isNotEmpty() && !state.finished) {
                Button(
                    onClick = vm::start,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) { Text(stringResource(R.string.compressor_start)) }
            }

            if (state.results.isNotEmpty()) {
                SectionHeader(stringResource(R.string.compressor_results_title))
                state.results.forEach { result -> ResultBlock(result, share, saveToDevice, screenEvents) }
                if (state.finished) {
                    if (state.keptResults.isNotEmpty()) {
                        Text(
                            stringResource(R.string.compressor_total_saved, formatBytes(state.totalSaved)),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    OutlinedButton(
                        onClick = vm::reset,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(stringResource(R.string.compressor_compress_more)) }
                }
            }
        }
    }
}

@Composable
private fun PresetRow(preset: CompressionPreset, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val (title, description) = when (preset) {
        CompressionPreset.HIGH -> R.string.compressor_preset_high to R.string.compressor_preset_high_desc
        CompressionPreset.BALANCED -> R.string.compressor_preset_balanced to R.string.compressor_preset_balanced_desc
        CompressionPreset.SMALLER -> R.string.compressor_preset_smaller to R.string.compressor_preset_smaller_desc
    }
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultBlock(
    result: CompressorResult,
    share: ShareService,
    saveToDevice: com.vextech.smart_edit.core.ui.SaveToDeviceAction,
    events: UiEventChannel,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            result.error != null -> {
                Text(result.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                ErrorText(result.error.asString())
            }
            result.outputUri == null -> ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(result.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.compressor_already_optimized), style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                val mime = result.outputMime.orEmpty()
                val uri = result.outputUri
                val name = result.outputName.orEmpty()
                ResultCard(
                    name = name,
                    sizeBytes = result.compressedBytes,
                    onOpen = {
                        runCatching { share.openManaged(uri, mime) }
                            .onFailure { events.message(UiText.res(R.string.common_open_failed)) }
                    },
                    onShare = {
                        runCatching { share.shareManaged(uri, mime) }
                            .onFailure { events.message(UiText.res(R.string.common_share_failed)) }
                    },
                    onSaveToDevice = { saveToDevice.save(uri, name, mime) },
                )
                val percent = CompressionPolicies.percentSaved(result.originalBytes, result.compressedBytes)
                Text(
                    if (result.verdict == CompressionVerdict.UNCHANGED) stringResource(R.string.compressor_barely_smaller, percent)
                    else stringResource(R.string.compressor_saved, percent, formatBytes(result.originalBytes), formatBytes(result.compressedBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
