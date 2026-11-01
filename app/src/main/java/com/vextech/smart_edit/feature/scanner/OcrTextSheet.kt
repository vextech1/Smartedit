package com.vextech.smart_edit.feature.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.OcrPolicies
import com.vextech.smart_edit.core.library.OcrScript
import com.vextech.smart_edit.feature.scanner.domain.ScanPageDraft

@Composable
private fun scriptLabel(script: OcrScript?) = stringResource(
    when (script) {
        null -> R.string.scanner_ocr_script_auto
        OcrScript.LATIN -> R.string.scanner_ocr_script_latin
        OcrScript.CHINESE -> R.string.scanner_ocr_script_chinese
        OcrScript.DEVANAGARI -> R.string.scanner_ocr_script_devanagari
        OcrScript.JAPANESE -> R.string.scanner_ocr_script_japanese
        OcrScript.KOREAN -> R.string.scanner_ocr_script_korean
    }
)

/** Shows, copies and shares the OCR text of one page, and lets the user re-run recognition with another script. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTextSheet(page: ScanPageDraft, busy: Boolean, onRerun: (OcrScript?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = OcrPolicies.clean(page.ocrText.orEmpty())
    val stats = remember(text) { OcrPolicies.stats(text) }
    var scriptMenu by remember { mutableStateOf(false) }
    val statsLine = stringResource(R.string.scanner_ocr_stats, stats.words, stats.characters)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.scanner_ocr_text_title), style = MaterialTheme.typography.titleLarge)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (text.isEmpty()) {
                Text(
                    stringResource(if (busy) R.string.scanner_ocr_running else R.string.scanner_ocr_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    statsLine + (page.ocrLanguage?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = text.isNotEmpty(), onClick = { copyText(context, text) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.scanner_ocr_copy))
                }
                OutlinedButton(enabled = text.isNotEmpty(), onClick = { shareText(context, text) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.scanner_ocr_share))
                }
                Box {
                    IconButton(enabled = !busy, onClick = { scriptMenu = true }) { Icon(Icons.Rounded.Translate, stringResource(R.string.scanner_ocr_recognize_as)) }
                    DropdownMenu(scriptMenu, { scriptMenu = false }) {
                        Text(stringResource(R.string.scanner_ocr_recognize_as), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        (listOf<OcrScript?>(null) + OcrScript.entries).forEach { option ->
                            DropdownMenuItem(text = { Text(scriptLabel(option)) }, onClick = { scriptMenu = false; onRerun(option) })
                        }
                    }
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("OCR", text))
    Toast.makeText(context, R.string.scanner_ocr_copied, Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
