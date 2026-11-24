package com.vextech.editpro.feature.pdf

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vextech.editpro.R
import com.vextech.editpro.core.pdf.ImageExtraction
import com.vextech.editpro.core.sharing.ShareService
import com.vextech.editpro.core.ui.sheet.SheetCheck
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.ToolSheet
import java.io.File

/**
 * The site's "Pictures" sheet: save the pictures stored inside this PDF, one by one or as a ZIP. The PDF does not change.
 * JPEG pictures come out exactly as they are. The tick runs the search; the result stays in the sheet to share from.
 */
@Composable
fun ExtractImagesSheet(
    extraction: ImageExtraction?,
    busy: Boolean,
    share: ShareService,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onApply: (skipSmall: Boolean) -> Unit,
) {
    var skipSmall by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current

    ToolSheet(
        title = stringResource(R.string.extract_title),
        onCancel = onCancel,
        onReset = { skipSmall = true; onClear() },
        canReset = !skipSmall || extraction != null,
        onApply = { onApply(skipSmall) },
        busy = busy,
    ) {
        SheetHint(stringResource(R.string.extract_hint))
        SheetCheck(stringResource(R.string.extract_skip_small), skipSmall, { skipSmall = it; onClear() })
        if (extraction != null) {
            val skips = extraction.skips
            if (extraction.total == 0) {
                Text(stringResource(R.string.extract_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.extract_found, extraction.total, extraction.pages), style = MaterialTheme.typography.bodyMedium)
            }
            if (skips.tiny > 0) SheetHint(stringResource(R.string.extract_skipped_tiny, skips.tiny))
            if (skips.masks > 0) SheetHint(stringResource(R.string.extract_skipped_masks, skips.masks))
            if (skips.unsupported > 0) SheetHint(stringResource(R.string.extract_skipped_unsupported, skips.unsupported))
            if (extraction.truncated) SheetHint(stringResource(R.string.extract_truncated, extraction.total))
            if (extraction.cmykJpegs > 0) SheetHint(stringResource(R.string.extract_note_cmyk, extraction.cmykJpegs))
            if (extraction.alphaLost > 0) SheetHint(stringResource(R.string.extract_note_alpha, extraction.alphaLost))
            if (extraction.total == 0) {
                SheetHint(stringResource(R.string.extract_none_hint))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    extraction.zip?.let { zip ->
                        FileRow(stringResource(R.string.extract_all_zip), Formatter.formatShortFileSize(context, zip.length())) { share.share(zip, "application/zip") }
                    }
                    extraction.listed.forEach { image ->
                        FileRow(
                            stringResource(R.string.extract_page_picture, image.page),
                            "${image.width}×${image.height} · ${Formatter.formatShortFileSize(context, image.file.length())}",
                        ) { share.share(image.file, mimeOf(image.file)) }
                    }
                    if (extraction.total > extraction.listed.size) {
                        SheetHint(stringResource(R.string.extract_showing_first, extraction.listed.size, extraction.total))
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(title: String, subtitle: String, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { runCatching { onShare() } }) { Text(stringResource(R.string.share)) }
    }
}

/** The MIME type to share a saved file as. */
internal fun mimeOf(file: File): String = when (file.extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "jp2" -> "image/jp2"
    "zip" -> "application/zip"
    else -> "application/pdf"
}
