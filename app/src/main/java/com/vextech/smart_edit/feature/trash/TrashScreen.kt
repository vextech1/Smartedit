package com.vextech.smart_edit.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.TrashItem
import com.vextech.smart_edit.core.library.TrashKind
import com.vextech.smart_edit.core.library.TrashPolicies
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.ConfirmDialog
import com.vextech.smart_edit.core.ui.EmptyState
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.LoadingState
import com.vextech.smart_edit.core.ui.SectionHeader
import com.vextech.smart_edit.core.ui.TileRow
import com.vextech.smart_edit.core.ui.UiText
import com.vextech.smart_edit.core.ui.formatBytes

@Composable
fun TrashScreen(onBack: () -> Unit, vm: TrashViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    CollectUiEvents(vm.uiEvents, snackbarHostState)
    var pendingDelete by remember { mutableStateOf<TrashItem?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = stringResource(R.string.trash),
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmEmpty = true }, enabled = state.total > 0) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.trash_empty_action))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.total == 0 -> EmptyState(
                stringResource(R.string.trash_empty_title),
                stringResource(R.string.trash_empty_message, TrashPolicies.DEFAULT_RETENTION_DAYS),
                Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item(key = "retention_note") {
                    Surface(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.trash_empty_message, TrashPolicies.DEFAULT_RETENTION_DAYS),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.files.isNotEmpty()) {
                    item(key = "header_files") { SectionHeader(stringResource(R.string.trash_section_files)) }
                    items(state.files, key = { "file_" + it.id }) { item ->
                        TrashRow(item, onRestore = { vm.restore(item) }, onDelete = { pendingDelete = item })
                    }
                }
                if (state.notes.isNotEmpty()) {
                    item(key = "header_notes") { SectionHeader(stringResource(R.string.trash_section_notes)) }
                    items(state.notes, key = { "note_" + it.id }) { item ->
                        TrashRow(item, onRestore = { vm.restore(item) }, onDelete = { pendingDelete = item })
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.trash_delete_confirm_title),
            message = stringResource(R.string.trash_delete_confirm_message, item.name.ifBlank { stringResource(R.string.notes_untitled) }),
            confirmLabel = stringResource(R.string.trash_delete_forever),
            destructive = true,
            onConfirm = { vm.deleteForever(item); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.trash_empty_confirm_title),
            message = stringResource(R.string.trash_empty_confirm_message),
            confirmLabel = stringResource(R.string.trash_empty_action),
            destructive = true,
            onConfirm = { vm.emptyTrash(); confirmEmpty = false },
            onDismiss = { confirmEmpty = false },
        )
    }
}

@Composable
private fun TrashRow(item: TrashItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    val expiry = item.daysLeft?.let { days ->
        if (days <= 0) UiText.res(R.string.trash_expires_today).asString()
        else UiText.plural(R.plurals.trash_days_left, days, days).asString()
    }
    val detail = listOfNotNull(item.sizeBytes?.let(::formatBytes), expiry).joinToString(" · ")
    TileRow(
        icon = when {
            item.kind == TrashKind.NOTE -> Icons.AutoMirrored.Rounded.Notes
            item.isDirectory -> Icons.Rounded.Folder
            else -> Icons.Rounded.Description
        },
        title = item.name.ifBlank { stringResource(R.string.notes_untitled) },
        subtitle = detail.ifEmpty { null },
        iconTint = if (item.kind == TrashKind.NOTE) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        trailing = {
            Row {
                IconButton(onClick = onRestore) { Icon(Icons.Rounded.RestoreFromTrash, contentDescription = stringResource(R.string.trash_restore)) }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteForever, contentDescription = stringResource(R.string.trash_delete_forever), tint = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
