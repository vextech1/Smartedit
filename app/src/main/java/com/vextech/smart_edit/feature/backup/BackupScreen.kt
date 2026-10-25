package com.vextech.smart_edit.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.FeatureScaffold
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
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    FeatureScaffold(title = "Backup", onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::create, enabled = !state.busy) {
                    Icon(Icons.Rounded.Backup, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Create backup")
                }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !state.busy,
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Restore")
                }
            }

            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it) }
            state.latest?.let { file ->
                Button(onClick = { share.share(file, "application/zip") }) {
                    Text("Share latest backup")
                }
            }

            Text("Local backups", style = MaterialTheme.typography.titleMedium)
            if (backups.isEmpty()) {
                Text("No backups yet")
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(backups, key = { it.id }) { backup ->
                        val file = runCatching { File(URI(backup.uri)) }.getOrNull()
                        ListItem(
                            headlineContent = { Text(file?.name ?: "Backup") },
                            supportingContent = { Text(formatBytes(backup.sizeBytes)) },
                            trailingContent = {
                                TextButton(
                                    enabled = file?.exists() == true,
                                    onClick = { file?.let { share.share(it, "application/zip") } },
                                ) { Text("Share") }
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
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "Current Smart Edit files and app data will be replaced by the selected backup. " +
                        "The restore is staged and rolled back if validation fails."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = null
                        vm.restore(uri)
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) { Text("Cancel") }
            },
        )
    }
}
