package com.vextech.smart_edit.feature.backup

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.TileRow
import com.vextech.smart_edit.core.ui.formatBytes
import java.io.File
import java.net.URI

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    share: ShareService,
    vm: BackupViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val backups by vm.backups.collectAsState()
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) confirmRestore = uri }

    FeatureScaffold(title = stringResource(R.string.backup), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = vm::create, enabled = !state.busy, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Rounded.Backup, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.backup_create))
                }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !state.busy, modifier = Modifier.weight(1f).height(52.dp),
                ) { Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.backup_restore)) }
            }

            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            StatusCard(state, share)

            Text(stringResource(R.string.backup_local_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (backups.isEmpty()) {
                Text(stringResource(R.string.backup_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(backups, key = { it.id }) { backup ->
                        val file = runCatching { File(URI(backup.uri)) }.getOrNull()
                        val whenText = DateUtils.getRelativeTimeSpanString(backup.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                        TileRow(
                            icon = Icons.Rounded.FolderZip,
                            title = file?.name ?: stringResource(R.string.backup),
                            subtitle = stringResource(R.string.backup_detail, formatBytes(backup.sizeBytes), whenText),
                            trailing = {
                                TextButton(enabled = file?.exists() == true, onClick = { file?.let { share.share(it, "application/zip") } }) {
                                    Text(stringResource(R.string.backup_share))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_message)) },
            confirmButton = { TextButton(onClick = { confirmRestore = null; vm.restore(uri) }) { Text(stringResource(R.string.backup_restore)) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun StatusCard(state: BackupUiState, share: ShareService) {
    val status = state.status ?: return
    val failed = status == BackupStatus.FAILED
    Surface(
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle, null,
                tint = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    when (status) {
                        BackupStatus.CREATED -> stringResource(R.string.backup_created)
                        BackupStatus.RESTORED -> stringResource(R.string.backup_restored)
                        BackupStatus.FAILED -> state.error ?: stringResource(R.string.common_error_generic)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                )
                state.latest?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            state.latest?.let { file -> TextButton(onClick = { share.share(file, "application/zip") }) { Text(stringResource(R.string.backup_share)) } }
        }
    }
}
