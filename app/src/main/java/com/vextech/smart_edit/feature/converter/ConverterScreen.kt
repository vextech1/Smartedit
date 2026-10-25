package com.vextech.smart_edit.feature.converter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ads.ExportInterstitialEffect
import com.vextech.smart_edit.core.ads.UnityAdsService
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.ProgressCard
import com.vextech.smart_edit.core.ui.ResultCard
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import com.vextech.smart_edit.core.ui.formatBytes
import com.vextech.smart_edit.core.ui.rememberSaveToDevice
import com.vextech.smart_edit.feature.converter.domain.ConversionJob
import com.vextech.smart_edit.feature.converter.domain.ConverterMessageKey
import com.vextech.smart_edit.feature.converter.domain.ConverterSourceKind
import com.vextech.smart_edit.feature.converter.domain.ConverterTarget
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterScreen(
    onBack: () -> Unit,
    share: ShareService,
    ads: UnityAdsService,
    initialUri: String? = null,
    vm: ConverterViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(initialUri) { initialUri?.let { vm.preselect(android.net.Uri.parse(it)) } }
    val jobs by vm.jobs.collectAsState()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.selectImages(uris)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.selectPdf(uri)
    }

    ExportInterstitialEffect(state.outputs.firstOrNull()?.uri, ads)

    val snackbarHostState = remember { SnackbarHostState() }
    val localEvents = remember { UiEventChannel() }
    CollectUiEvents(localEvents.events, snackbarHostState)
    val saveToDevice = rememberSaveToDevice(share) { localEvents.message(it) }

    FeatureScaffold(
        title = stringResource(R.string.converter),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.converter_intro), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { imagePicker.launch(arrayOf("image/*")) },
                            enabled = !state.busy,
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(stringResource(R.string.converter_choose_images))
                        }
                        OutlinedButton(
                            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                            enabled = !state.busy,
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(stringResource(R.string.converter_choose_pdf))
                        }
                    }
                }
            }

            if (state.sources.isNotEmpty()) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (state.sourceKind == ConverterSourceKind.IMAGES) {
                                            stringResource(R.string.converter_images_selected, state.sources.size)
                                        } else state.sources.first().displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(formatBytes(state.sources.sumOf { it.sizeBytes }), style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = vm::clearSelection, enabled = !state.busy) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                                }
                            }
                            if (state.sources.size <= 5) {
                                state.sources.forEach { Text(it.displayName, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.converter_output_format), style = MaterialTheme.typography.titleMedium)
                        TargetChips(state.sourceKind, state.target, vm::setTarget)
                    }
                }

                if (state.sourceKind == ConverterSourceKind.PDF) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.pdfPassword,
                                onValueChange = vm::setPdfPassword,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.converter_pdf_password)) },
                                supportingText = { Text(stringResource(R.string.converter_pdf_password_hint)) },
                                singleLine = true,
                                enabled = !state.busy,
                            )
                        }
                    }
                }

                if (state.sourceKind == ConverterSourceKind.PDF && state.target in setOf(ConverterTarget.PDF_IMAGES_ZIP, ConverterTarget.PNG, ConverterTarget.WEBP)) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.converter_page_options), style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = state.pageRange,
                                onValueChange = vm::setPageRange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.converter_page_range)) },
                                supportingText = { Text(stringResource(R.string.converter_page_range_hint)) },
                                enabled = !state.busy,
                                singleLine = true,
                            )
                            Text(stringResource(R.string.converter_resolution, state.dpi))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(96, 144, 200, 300).forEach { dpi ->
                                    FilterChip(
                                        selected = state.dpi == dpi,
                                        onClick = { vm.setDpi(dpi) },
                                        label = { Text("$dpi DPI") },
                                        enabled = !state.busy,
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.target in setOf(ConverterTarget.PDF, ConverterTarget.JPEG, ConverterTarget.WEBP, ConverterTarget.PDF_IMAGES_ZIP)) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.converter_quality, state.quality))
                            Slider(
                                value = state.quality.toFloat(),
                                onValueChange = { vm.setQuality(it.roundToInt()) },
                                valueRange = 40f..100f,
                                enabled = !state.busy,
                            )
                        }
                    }
                }

                if (state.sourceKind == ConverterSourceKind.PDF && state.target == ConverterTarget.DOCX) {
                    item {
                        Text(
                            stringResource(R.string.converter_docx_note),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.busy) {
                            ProgressCard(
                                label = stringResource(R.string.converter_progress, (state.progress * 100).roundToInt()),
                                progress = state.progress,
                                onCancel = vm::cancel,
                                cancelLabel = stringResource(R.string.converter_cancel_conversion),
                            )
                        } else {
                            Button(onClick = vm::convert, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                                Spacer(Modifier.padding(4.dp))
                                Text(stringResource(R.string.converter_start))
                            }
                        }
                        state.message?.let { key ->
                            Text(
                                converterMessage(key),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (key == ConverterMessageKey.CONVERSION_COMPLETE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (state.outputs.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.converter_outputs),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(state.outputs, key = { it.uri }) { output ->
                    ResultCard(
                        name = output.name,
                        sizeBytes = output.sizeBytes,
                        onOpen = {
                            runCatching { share.openManaged(output.uri, output.mimeType) }
                                .onFailure { localEvents.message(UiText.res(R.string.common_open_failed)) }
                        },
                        onShare = {
                            runCatching { share.shareManaged(output.uri, output.mimeType) }
                                .onFailure { localEvents.message(UiText.res(R.string.common_share_failed)) }
                        },
                        onSaveToDevice = { saveToDevice.save(output.uri, output.name, output.mimeType) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            if (jobs.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.converter_recent_jobs),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(jobs.take(8), key = ConversionJob::id) { job ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text("${job.sourceFormat.uppercase()} → ${job.targetFormat.uppercase()}", fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(converterJobStatus(job.status)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetChips(
    sourceKind: ConverterSourceKind?,
    selected: ConverterTarget?,
    onTarget: (ConverterTarget) -> Unit,
) {
    val options = when (sourceKind) {
        ConverterSourceKind.IMAGES -> listOf(
            ConverterTarget.PDF to R.string.converter_target_pdf,
            ConverterTarget.JPEG to R.string.converter_target_jpeg,
            ConverterTarget.PNG to R.string.converter_target_png,
            ConverterTarget.WEBP to R.string.converter_target_webp,
        )
        ConverterSourceKind.PDF -> listOf(
            ConverterTarget.PDF_IMAGES_ZIP to R.string.converter_target_jpeg_zip,
            ConverterTarget.PNG to R.string.converter_target_png_zip,
            ConverterTarget.WEBP to R.string.converter_target_webp_zip,
            ConverterTarget.DOCX to R.string.converter_target_docx,
            ConverterTarget.TEXT to R.string.converter_target_text,
        )
        null -> emptyList()
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (target, label) ->
            FilterChip(
                selected = selected == target,
                onClick = { onTarget(target) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun converterMessage(key: ConverterMessageKey): String = stringResource(
    when (key) {
        ConverterMessageKey.SOURCE_REQUIRED -> R.string.converter_error_source_required
        ConverterMessageKey.IMAGE_SOURCE_REQUIRED -> R.string.converter_error_image_required
        ConverterMessageKey.PDF_SOURCE_REQUIRED -> R.string.converter_error_pdf_required
        ConverterMessageKey.PAGE_RANGE_INVALID -> R.string.converter_error_page_range
        ConverterMessageKey.PAGE_RANGE_EMPTY -> R.string.converter_error_page_range
        ConverterMessageKey.PASSWORD_REQUIRED_OR_INCORRECT -> R.string.converter_error_password
        ConverterMessageKey.CONVERSION_FAILED -> R.string.converter_error_failed
        ConverterMessageKey.CONVERSION_CANCELLED -> R.string.converter_cancelled
        ConverterMessageKey.CONVERSION_COMPLETE -> R.string.converter_complete
    }
)

private fun converterJobStatus(status: String): Int = when (status.uppercase()) {
    "COMPLETED" -> R.string.converter_job_completed
    "FAILED" -> R.string.converter_job_failed
    "CANCELLED" -> R.string.converter_job_cancelled
    else -> R.string.converter_job_running
}
