package com.vextech.smart_edit.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vextech.smart_edit.R
import kotlin.math.roundToInt

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(label ?: stringResource(R.string.common_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (onRetry != null) Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

/**
 * Standard confirmation dialog. Pass already-localized strings.
 * [destructive] colors the confirm action as an error action.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Progress for a long-running job. [progress] null means indeterminate. */
@Composable
fun ProgressCard(
    label: String,
    progress: Float?,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    cancelLabel: String = stringResource(R.string.cancel),
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress != null) {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.common_progress_percent, (progress.coerceIn(0f, 1f) * 100).roundToInt()))
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(label, style = MaterialTheme.typography.bodySmall)
        if (onCancel != null) OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(cancelLabel) }
    }
}

/** A finished output file: name, size, open, share and optional save-to-device. */
@Composable
fun ResultCard(
    name: String,
    sizeBytes: Long,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveToDevice: (() -> Unit)? = null,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium)
                Text(formatBytes(sizeBytes), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
            if (onSaveToDevice != null) {
                IconButton(onClick = onSaveToDevice) {
                    Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.common_save_to_device))
                }
            }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share)) }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

/** A tappable row that starts a pick action (choose images, choose PDF, ...). */
@Composable
fun SourcePickerRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
    )
}
