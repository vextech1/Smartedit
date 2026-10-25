package com.vextech.smart_edit.feature.ocr

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.OcrScript
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.ProgressCard
import com.vextech.smart_edit.core.ui.SectionHeader
import com.vextech.smart_edit.core.ui.SourcePickerRow
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import java.util.Locale

private val imageTypes = arrayOf("image/jpeg", "image/png", "image/webp")

@OptIn(ExperimentalLayoutApi::class)
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
    val clipboard = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pick) }

    FeatureScaffold(
        title = stringResource(R.string.ocr_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val source = state.source
            if (source == null) {
                SourcePickerRow(
                    title = stringResource(R.string.ocr_choose_image),
                    subtitle = stringResource(R.string.ocr_choose_image_hint),
                    icon = Icons.Rounded.AddPhotoAlternate,
                    onClick = { picker.launch(imageTypes) },
                )
            } else {
                ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.preview?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.ocr_preview_description),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(source.name, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (!state.running) TextButton(onClick = { picker.launch(imageTypes) }) { Text(stringResource(R.string.ocr_change_image)) }
                        }
                    }
                }

                SectionHeader(stringResource(R.string.ocr_script_title))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OcrScript.entries.forEach { script ->
                        FilterChip(
                            selected = state.script == script,
                            onClick = { vm.setScript(script) },
                            enabled = !state.running,
                            label = { Text(stringResource(scriptLabel(script))) },
                        )
                    }
                }

                if (state.running) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ProgressCard(label = stringResource(R.string.ocr_extracting), progress = null, onCancel = vm::cancel)
                    }
                } else {
                    Button(onClick = vm::extract, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.ocr_extract))
                    }
                }

                if (state.hasResult) {
                    state.detectedLanguage?.let { code ->
                        val language = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).ifBlank { code }
                        Text(
                            stringResource(R.string.ocr_detected_language, language),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.suggestedScript?.let { suggestion ->
                        val label = stringResource(scriptLabel(suggestion))
                        ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.ocr_try_script, label), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = vm::retryWithSuggestion) { Text(stringResource(R.string.ocr_try_script_action, label)) }
                            }
                        }
                    }
                    SectionHeader(stringResource(R.string.ocr_result_label))
                    OutlinedTextField(
                        value = vm.text,
                        onValueChange = vm::onText,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        minLines = 6,
                    )
                    Text(
                        stringResource(R.string.ocr_stats, state.stats.words, state.stats.characters),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.text)); vm.copied() }, enabled = vm.text.isNotBlank()) {
                            Text(stringResource(R.string.ocr_copy))
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching { share.shareText(vm.text) }
                                    .onFailure { screenEvents.message(UiText.res(R.string.common_share_failed)) }
                            },
                            enabled = vm.text.isNotBlank(),
                        ) { Text(stringResource(R.string.ocr_share)) }
                        OutlinedButton(onClick = vm::saveAsNote, enabled = vm.text.isNotBlank()) { Text(stringResource(R.string.ocr_save_note)) }
                        OutlinedButton(onClick = vm::saveAsTextFile, enabled = vm.text.isNotBlank()) { Text(stringResource(R.string.ocr_save_file)) }
                    }
                }
            }
        }
    }
}

private fun scriptLabel(script: OcrScript): Int = when (script) {
    OcrScript.LATIN -> R.string.ocr_script_latin
    OcrScript.CHINESE -> R.string.ocr_script_chinese
    OcrScript.DEVANAGARI -> R.string.ocr_script_devanagari
    OcrScript.JAPANESE -> R.string.ocr_script_japanese
    OcrScript.KOREAN -> R.string.ocr_script_korean
}
