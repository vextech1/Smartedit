package com.vextech.smart_edit.feature.converter

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ads.ExportInterstitialEffect
import com.vextech.smart_edit.core.ads.UnityAdsService
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.BottomAction
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.UriThumbnail
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.ImageScrim
import com.vextech.smart_edit.core.ui.ResultCard
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import com.vextech.smart_edit.core.ui.formatBytes
import com.vextech.smart_edit.core.ui.rememberSaveToDevice
import com.vextech.smart_edit.feature.converter.domain.ConversionJob
import com.vextech.smart_edit.feature.converter.domain.ConverterMessageKey
import com.vextech.smart_edit.feature.converter.domain.ConverterPageSize
import com.vextech.smart_edit.feature.converter.domain.ConverterSource
import com.vextech.smart_edit.feature.converter.domain.ConverterSourceKind
import com.vextech.smart_edit.feature.converter.domain.ConverterTarget
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Stage { HOME, SETUP, PROGRESS, RESULT }

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
    val jobs by vm.jobs.collectAsState()
    LaunchedEffect(initialUri) { initialUri?.let { vm.preselect(Uri.parse(it)) } }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.selectImages(it) }
    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addImages(it) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.selectPdf(uri) }

    // Only a result produced just now triggers the ad, not one reopened from history.
    ExportInterstitialEffect(state.outputs.firstOrNull()?.uri?.takeIf { state.freshResult }, ads)

    val snackbarHostState = remember { SnackbarHostState() }
    val localEvents = remember { UiEventChannel() }
    CollectUiEvents(localEvents.events, snackbarHostState)
    val saveToDevice = rememberSaveToDevice(share) { localEvents.message(it) }

    // One place for messages: the snackbar. A finished conversion is shown by the result screen instead.
    LaunchedEffect(state.message) {
        val key = state.message ?: return@LaunchedEffect
        if (key != ConverterMessageKey.CONVERSION_COMPLETE) localEvents.message(UiText.res(converterMessageRes(key)))
        vm.clearMessage()
    }

    val stage = when {
        state.busy -> Stage.PROGRESS
        state.outputs.isNotEmpty() -> Stage.RESULT
        state.sources.isNotEmpty() -> Stage.SETUP
        else -> Stage.HOME
    }
    val navigateBack: () -> Unit = {
        when (stage) {
            Stage.HOME -> onBack()
            Stage.SETUP -> vm.clearSelection()
            Stage.RESULT -> vm.dismissResult()
            Stage.PROGRESS -> Unit // leave only through the explicit Cancel button
        }
    }
    BackHandler(enabled = stage != Stage.HOME, onBack = navigateBack)

    FeatureScaffold(
        title = stringResource(
            when (stage) {
                Stage.HOME -> R.string.converter
                Stage.SETUP -> if (state.sourceKind == ConverterSourceKind.PDF) R.string.converter_title_pdf else R.string.converter_title_images
                Stage.PROGRESS -> R.string.converter_title_progress
                Stage.RESULT -> R.string.converter_title_done
            }
        ),
        onBack = navigateBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (stage) {
                Stage.HOME -> HomeStage(
                    jobs = jobs,
                    onImages = { imagePicker.launch(arrayOf("image/*")) },
                    onPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                    onOpenJob = vm::openJob,
                )
                Stage.SETUP -> SetupStage(
                    state = state, vm = vm,
                    onAddImages = { addPicker.launch(arrayOf("image/*")) },
                    onChangePdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                )
                Stage.PROGRESS -> ProgressStage(
                    caption = stringResource(
                        R.string.converter_progress_caption,
                        state.sources.firstOrNull()?.displayName ?: "",
                        state.target?.let { stringResource(targetLabelRes(it, state.sourceKind)) } ?: "",
                    ),
                    progress = state.progress,
                    onCancel = vm::cancel,
                )
                Stage.RESULT -> {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Column {
                                Text(stringResource(R.string.converter_complete_title), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.converter_saved_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        state.outputs.forEach { output ->
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
                            )
                        }
                    }
                    BottomAction {
                        Button(onClick = vm::clearSelection, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(Icons.Rounded.SwapHoriz, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.converter_convert_another))
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Home ─────────────────────────────

@Composable
private fun ColumnScope.HomeStage(jobs: List<ConversionJob>, onImages: () -> Unit, onPdf: () -> Unit, onOpenJob: (ConversionJob) -> Unit) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.converter_home_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceTile(Icons.Rounded.Image, stringResource(R.string.converter_home_images), stringResource(R.string.converter_home_images_desc), onImages, Modifier.weight(1f))
            SourceTile(Icons.Rounded.PictureAsPdf, stringResource(R.string.converter_home_pdf), stringResource(R.string.converter_home_pdf_desc), onPdf, Modifier.weight(1f))
        }
        if (jobs.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.converter_recent_jobs))
                jobs.take(8).forEach { RecentJobCard(it) { onOpenJob(it) } }
            }
        }
    }
}

@Composable
private fun SourceTile(icon: ImageVector, title: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp).heightIn(min = 118.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentJobCard(job: ConversionJob, onClick: () -> Unit) {
    val completed = job.status.equals("COMPLETED", ignoreCase = true) && job.outputUri != null
    val target = job.targetFormat.lowercase()
    val icon = when {
        target.endsWith("_zip") -> Icons.Rounded.FolderZip
        target == "pdf" -> Icons.Rounded.PictureAsPdf
        target == "docx" || target == "txt" -> Icons.Rounded.Description
        else -> Icons.Rounded.Image
    }
    Surface(
        onClick = onClick, enabled = completed, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${job.sourceFormat.uppercase()} → ${job.targetFormat.uppercase().replace('_', ' ')}", style = MaterialTheme.typography.titleMedium)
                Text(
                    DateUtils.getRelativeTimeSpanString(job.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusChip(job.status)
            }
            if (completed) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val ok = status.equals("COMPLETED", ignoreCase = true)
    val bad = status.equals("FAILED", ignoreCase = true)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when { ok -> MaterialTheme.colorScheme.secondaryContainer; bad -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceContainerHighest },
    ) {
        Text(
            stringResource(converterJobStatus(status)), Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall,
            color = when { ok -> MaterialTheme.colorScheme.onSecondaryContainer; bad -> MaterialTheme.colorScheme.onErrorContainer; else -> MaterialTheme.colorScheme.onSurfaceVariant },
        )
    }
}

// ───────────────────────────── Setup ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SetupStage(state: ConverterUiState, vm: ConverterViewModel, onAddImages: () -> Unit, onChangePdf: () -> Unit) {
    val isPdf = state.sourceKind == ConverterSourceKind.PDF
    val locked = isPdf && state.pdfProtected && state.pageCount == null
    val target = state.target
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (isPdf) {
            val file = state.sources.first()
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(file.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            state.pageCount?.let { stringResource(R.string.converter_pdf_summary, it, formatBytes(file.sizeBytes)) } ?: formatBytes(file.sizeBytes),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onChangePdf) { Text(stringResource(R.string.converter_change)) }
                }
            }
            if (locked) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(stringResource(R.string.converter_protected_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        OutlinedTextField(
                            value = state.pdfPassword, onValueChange = vm::setPdfPassword, modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.converter_pdf_password)) },
                            supportingText = { Text(stringResource(if (state.passwordError) R.string.converter_error_password else R.string.converter_protected_hint)) },
                            isError = state.passwordError, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                }
            }
        } else {
            ImagesGrid(state.sources, onAddImages, vm::removeImage, vm::moveImage)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.converter_convert_to))
            if (isPdf) {
                listOf(
                    Triple(ConverterTarget.PDF_IMAGES_ZIP, R.string.converter_target_jpeg_zip, R.string.converter_target_jpeg_zip_desc),
                    Triple(ConverterTarget.PNG, R.string.converter_target_png_zip, R.string.converter_target_png_zip_desc),
                    Triple(ConverterTarget.WEBP, R.string.converter_target_webp_zip, R.string.converter_target_webp_zip_desc),
                    Triple(ConverterTarget.DOCX, R.string.converter_target_docx, R.string.converter_target_docx_desc),
                    Triple(ConverterTarget.TEXT, R.string.converter_target_text, R.string.converter_target_text_desc),
                ).forEach { (option, label, description) -> TargetRow(stringResource(label), stringResource(description), target == option) { vm.setTarget(option) } }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ConverterTarget.PDF to R.string.converter_target_pdf, ConverterTarget.JPEG to R.string.converter_target_jpeg, ConverterTarget.PNG to R.string.converter_target_png, ConverterTarget.WEBP to R.string.converter_target_webp)
                        .forEach { (option, label) -> FilterChip(selected = target == option, onClick = { vm.setTarget(option) }, label = { Text(stringResource(label)) }) }
                }
            }
        }

        if (!isPdf && target == ConverterTarget.PDF) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.converter_page_size))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ConverterPageSize.FIT_IMAGE to R.string.converter_page_size_fit, ConverterPageSize.A4 to R.string.converter_page_size_a4, ConverterPageSize.LETTER to R.string.converter_page_size_letter)
                        .forEach { (size, label) -> FilterChip(selected = state.pageSize == size, onClick = { vm.setPageSize(size) }, label = { Text(stringResource(label)) }) }
                }
            }
        }

        if (isPdf && target in setOf(ConverterTarget.PDF_IMAGES_ZIP, ConverterTarget.PNG, ConverterTarget.WEBP)) {
            var custom by remember { mutableStateOf(state.pageRange.trim().lowercase() !in setOf("", "all", "*")) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.converter_page_range))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !custom, onClick = { custom = false; vm.setPageRange("all") }, label = { Text(stringResource(R.string.converter_pages_all)) })
                    FilterChip(selected = custom, onClick = { custom = true; vm.setPageRange("") }, label = { Text(stringResource(R.string.converter_pages_custom)) })
                }
                if (custom) {
                    OutlinedTextField(
                        value = state.pageRange, onValueChange = vm::setPageRange, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.converter_page_range)) },
                        supportingText = { Text(stringResource(R.string.converter_page_range_hint)) },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.converter_resolution, state.dpi))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(96, 144, 200, 300).forEach { dpi -> FilterChip(selected = state.dpi == dpi, onClick = { vm.setDpi(dpi) }, label = { Text("$dpi DPI") }) }
                }
            }
        }

        if (target in setOf(ConverterTarget.PDF, ConverterTarget.JPEG, ConverterTarget.WEBP, ConverterTarget.PDF_IMAGES_ZIP)) {
            Column {
                SectionLabel(stringResource(R.string.converter_quality, state.quality))
                Slider(value = state.quality.toFloat(), onValueChange = { vm.setQuality(it.roundToInt()) }, valueRange = 40f..100f)
            }
        }
        if (isPdf && target == ConverterTarget.DOCX) {
            Text(stringResource(R.string.converter_docx_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    BottomAction {
        Button(onClick = vm::convert, enabled = target != null, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Rounded.SwapHoriz, null); Spacer(Modifier.width(8.dp))
            Text(
                if (locked) stringResource(R.string.converter_unlock_convert)
                else stringResource(R.string.converter_convert_to_format, target?.let { stringResource(targetLabelRes(it, state.sourceKind)) } ?: ""),
            )
        }
    }
}

@Composable
private fun TargetRow(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = null)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ───────────────────────────── Images grid ─────────────────────────────

@Composable
private fun ImagesGrid(sources: List<ConverterSource>, onAdd: () -> Unit, onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit) {
    var selected by remember { mutableIntStateOf(-1) }
    if (selected >= sources.size) selected = -1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.converter_images_hint, sources.size, formatBytes(sources.sumOf { it.sizeBytes })),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The "add" tile counts as the last cell of the grid.
        (sources.indices.toList() + (-1)).chunked(3).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowCells.forEach { index ->
                    if (index == -1) {
                        Surface(
                            onClick = onAdd, shape = RoundedCornerShape(12.dp), color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, stringResource(R.string.converter_add_images), tint = MaterialTheme.colorScheme.primary) } }
                    } else {
                        ImageTile(
                            index = index, uri = sources[index].uri, selected = selected == index,
                            canLeft = index > 0, canRight = index < sources.lastIndex,
                            onClick = { selected = if (selected == index) -1 else index },
                            onRemove = { onRemove(index) },
                            onLeft = { onMove(index, index - 1); selected = index - 1 },
                            onRight = { onMove(index, index + 1); selected = index + 1 },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(3 - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ImageTile(
    index: Int, uri: String, selected: Boolean, canLeft: Boolean, canRight: Boolean,
    onClick: () -> Unit, onRemove: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.aspectRatio(1f).clip(shape)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        UriThumbnail(uri, Modifier.fillMaxSize())
        Surface(Modifier.align(Alignment.TopStart).padding(6.dp), shape = CircleShape, color = ImageScrim) {
            Text("${index + 1}", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Surface(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp), shape = CircleShape, color = ImageScrim) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, stringResource(R.string.converter_remove_image), Modifier.size(16.dp), tint = Color.White) }
        }
        if (selected && (canLeft || canRight)) {
            Row(Modifier.align(Alignment.BottomCenter).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canLeft) TileArrow(Icons.Rounded.ChevronLeft, stringResource(R.string.converter_move_image_left), onLeft)
                if (canRight) TileArrow(Icons.Rounded.ChevronRight, stringResource(R.string.converter_move_image_right), onRight)
            }
        }
    }
}

@Composable
private fun TileArrow(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = ImageScrim, modifier = Modifier.size(32.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(20.dp), tint = Color.White) }
    }
}

// ───────────────────────────── Progress ─────────────────────────────

@Composable
private fun ColumnScope.ProgressStage(caption: String, progress: Float, onCancel: () -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.size(160.dp), strokeWidth = 10.dp,
                strokeCap = StrokeCap.Round, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.headlineLarge)
        }
        Spacer(Modifier.height(24.dp))
        Text(caption, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    BottomAction {
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.converter_cancel_conversion)) }
    }
}

// ───────────────────────────── Shared ─────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun targetLabelRes(target: ConverterTarget, sourceKind: ConverterSourceKind?): Int = when (target) {
    ConverterTarget.PDF -> R.string.converter_target_pdf
    ConverterTarget.JPEG -> R.string.converter_target_jpeg
    ConverterTarget.PNG -> if (sourceKind == ConverterSourceKind.PDF) R.string.converter_target_png_zip else R.string.converter_target_png
    ConverterTarget.WEBP -> if (sourceKind == ConverterSourceKind.PDF) R.string.converter_target_webp_zip else R.string.converter_target_webp
    ConverterTarget.PDF_IMAGES_ZIP -> R.string.converter_target_jpeg_zip
    ConverterTarget.DOCX -> R.string.converter_target_docx
    ConverterTarget.TEXT -> R.string.converter_target_text
}

private fun converterMessageRes(key: ConverterMessageKey): Int = when (key) {
    ConverterMessageKey.SOURCE_REQUIRED -> R.string.converter_error_source_required
    ConverterMessageKey.IMAGE_SOURCE_REQUIRED -> R.string.converter_error_image_required
    ConverterMessageKey.PDF_SOURCE_REQUIRED -> R.string.converter_error_pdf_required
    ConverterMessageKey.PAGE_RANGE_INVALID, ConverterMessageKey.PAGE_RANGE_EMPTY -> R.string.converter_error_page_range
    ConverterMessageKey.PASSWORD_REQUIRED_OR_INCORRECT -> R.string.converter_error_password
    ConverterMessageKey.CONVERSION_FAILED -> R.string.converter_error_failed
    ConverterMessageKey.CONVERSION_CANCELLED -> R.string.converter_cancelled
    ConverterMessageKey.CONVERSION_COMPLETE -> R.string.converter_complete
    ConverterMessageKey.OUTPUT_MISSING -> R.string.converter_error_output_missing
}

private fun converterJobStatus(status: String): Int = when (status.uppercase()) {
    "COMPLETED" -> R.string.converter_job_completed
    "FAILED" -> R.string.converter_job_failed
    "CANCELLED" -> R.string.converter_job_cancelled
    else -> R.string.converter_job_running
}
