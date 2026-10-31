package com.vextech.smart_edit.feature.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.OcrScript
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.BottomAction
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.ProgressCard
import com.vextech.smart_edit.core.ui.SourcePickerRow
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import java.util.Locale

private val imageTypes = arrayOf("image/jpeg", "image/png", "image/webp")

@Composable
fun OcrScreen(
    onBack: () -> Unit,
    share: ShareService,
    initialUri: String? = null,
    vm: OcrViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(initialUri) { initialUri?.let { vm.preselect(android.net.Uri.parse(it)) } }
    val snackbarHostState = remember { SnackbarHostState() }
    val screenEvents = remember { UiEventChannel() }
    CollectUiEvents(vm.uiEvents, snackbarHostState)
    CollectUiEvents(screenEvents.events, snackbarHostState)
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pick) }
    val source = state.source

    FeatureScaffold(
        title = stringResource(R.string.ocr_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (source == null) {
                    SourcePickerRow(
                        title = stringResource(R.string.ocr_choose_image),
                        subtitle = stringResource(R.string.ocr_choose_image_hint),
                        icon = Icons.Rounded.AddPhotoAlternate,
                        onClick = { picker.launch(imageTypes) },
                    )
                } else {
                    SourceCard(state, source.name, onChange = { picker.launch(imageTypes) })

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ocr_script_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OcrScript.entries.forEach { script ->
                                FilterChip(
                                    selected = state.script == script,
                                    onClick = { vm.setScript(script) },
                                    enabled = !state.running,
                                    label = { Text(stringResource(scriptLabel(script))) },
                                )
                            }
                        }
                    }

                    if (state.hasResult) {
                        state.suggestedScript?.let { suggestion ->
                            val label = stringResource(scriptLabel(suggestion))
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.ocr_try_script, label), Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    TextButton(onClick = vm::retryWithSuggestion) { Text(stringResource(R.string.ocr_try_script_action, label)) }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = vm.text,
                            onValueChange = vm::onText,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                            label = { Text(stringResource(R.string.ocr_result_label)) },
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.ocr_stats, state.stats.words, state.stats.characters),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // Changing the writing system does not re-read by itself, so offer it right here.
                            TextButton(onClick = vm::extract, enabled = !state.running) { Text(stringResource(R.string.ocr_extract)) }
                        }
                    }
                }
            }

            if (source != null) {
                BottomAction {
                    when {
                        state.running -> ProgressCard(label = stringResource(R.string.ocr_extracting), progress = null, onCancel = vm::cancel)
                        !state.hasResult -> Button(onClick = vm::extract, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(Icons.Rounded.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ocr_extract))
                        }
                        else -> {
                            val hasText = vm.text.isNotBlank()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { copyToClipboard(context, vm.text); vm.copied() },
                                    enabled = hasText, modifier = Modifier.weight(1f).height(56.dp),
                                ) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ocr_copy)) }
                                ActionIcon(Icons.Rounded.Share, stringResource(R.string.ocr_share), hasText) {
                                    runCatching { share.shareText(vm.text) }.onFailure { screenEvents.message(UiText.res(R.string.common_share_failed)) }
                                }
                                ActionIcon(Icons.AutoMirrored.Rounded.NoteAdd, stringResource(R.string.ocr_save_note), hasText, vm::saveAsNote)
                                ActionIcon(Icons.Rounded.Save, stringResource(R.string.ocr_save_file), hasText, vm::saveAsTextFile)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(state: OcrUiState, name: String, onChange: () -> Unit) {
    val preview = remember(state.preview) { state.preview?.asImageBitmap() }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(64.dp)) {
                preview?.let { Image(it, stringResource(R.string.ocr_preview_description), Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                val code = state.detectedLanguage
                if (state.hasResult && code != null) {
                    val language = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).ifBlank { code }
                    Text(stringResource(R.string.ocr_detected_language, language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!state.running) TextButton(onClick = onChange) { Text(stringResource(R.string.ocr_change_image)) }
        }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) { Icon(icon, description) }
}

private fun scriptLabel(script: OcrScript): Int = when (script) {
    OcrScript.LATIN -> R.string.ocr_script_latin
    OcrScript.CHINESE -> R.string.ocr_script_chinese
    OcrScript.DEVANAGARI -> R.string.ocr_script_devanagari
    OcrScript.JAPANESE -> R.string.ocr_script_japanese
    OcrScript.KOREAN -> R.string.ocr_script_korean
}

private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", text))
}
