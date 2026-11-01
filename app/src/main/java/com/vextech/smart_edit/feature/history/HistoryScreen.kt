package com.vextech.smart_edit.feature.history

import android.webkit.MimeTypeMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.DayBucket
import com.vextech.smart_edit.core.library.HistoryDayGroup
import com.vextech.smart_edit.core.library.HistoryEntry
import com.vextech.smart_edit.core.library.HistoryFilter
import com.vextech.smart_edit.core.library.HistoryPolicies
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.ConfirmDialog
import com.vextech.smart_edit.core.ui.EmptyState
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.LoadingState
import com.vextech.smart_edit.core.ui.SectionHeader
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(onBack: (() -> Unit)?, share: ShareService, vm: HistoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val screenEvents = remember { UiEventChannel() }
    CollectUiEvents(vm.uiEvents, snackbarHostState)
    CollectUiEvents(screenEvents.events, snackbarHostState)
    var exportMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val pdfTitle = stringResource(R.string.history_pdf_title)

    LaunchedEffect(vm) {
        vm.exports.collect { exported ->
            runCatching { share.share(exported.file, exported.mimeType) }
                .onFailure { screenEvents.message(UiText.res(R.string.common_share_failed)) }
        }
    }

    FeatureScaffold(
        title = stringResource(R.string.nav_activity),
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { exportMenu = true }, enabled = state.totalCount > 0) {
                    Icon(Icons.Rounded.IosShare, contentDescription = stringResource(R.string.history_export))
                }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    vm.exportFormats.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_export_as, format.name)) },
                            onClick = { exportMenu = false; vm.export(format, pdfTitle) },
                        )
                    }
                }
            }
            IconButton(onClick = { confirmClear = true }, enabled = state.totalCount > 0) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.history_clear))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.totalCount == 0 -> EmptyState(
                stringResource(R.string.history_empty_title),
                stringResource(R.string.history_empty_message),
                Modifier.padding(padding),
            )
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HistoryFilter.entries.toList(), key = { it.name }) { option ->
                        FilterChip(
                            selected = state.filter == option,
                            onClick = { vm.onFilter(option) },
                            label = { Text(stringResource(filterLabel(option))) },
                        )
                    }
                }
                if (state.groups.isEmpty()) {
                    EmptyState(stringResource(R.string.history_filter_empty_title), stringResource(R.string.history_filter_empty_message))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        state.groups.forEach { group ->
                            item(key = "day_${group.date}") { DayHeader(dayTitle(group)) }
                            items(group.entries, key = HistoryEntry::id) { entry ->
                                EntryRow(entry) {
                                    val uri = entry.uri
                                    if (uri != null) {
                                        if (isMissingFile(uri)) {
                                            screenEvents.message(UiText.res(R.string.history_file_missing))
                                        } else {
                                            runCatching { share.openManaged(uri, mimeFor(entry.displayName ?: uri)) }
                                                .onFailure { screenEvents.message(UiText.res(R.string.common_open_failed)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            message = stringResource(R.string.history_clear_message),
            confirmLabel = stringResource(R.string.history_clear),
            destructive = true,
            onConfirm = { confirmClear = false; vm.clear() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EntryRow(entry: HistoryEntry, onOpen: () -> Unit) {
    val openable = HistoryPolicies.canOpen(entry)
    val detail = entry.displayName?.takeIf { it.isNotBlank() } ?: entry.description
    Row(
        Modifier.fillMaxWidth().then(if (openable) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(actionIcon(entry.action), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(actionLabel(entry.action)), style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotBlank()) {
                Text(detail, maxLines = 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(timeText(entry.timestamp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Managed outputs are file:// URIs; anything else (content://) is left to the opening app to judge. */
private fun isMissingFile(uri: String): Boolean =
    uri.startsWith("file:") && runCatching { !File(java.net.URI(uri)).exists() }.getOrDefault(false)

@Composable
private fun dayTitle(group: HistoryDayGroup): String = when (group.bucket) {
    DayBucket.TODAY -> stringResource(R.string.history_today)
    DayBucket.YESTERDAY -> stringResource(R.string.history_yesterday)
    DayBucket.OTHER -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(group.date)
}

private fun timeText(timestamp: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

private fun filterLabel(filter: HistoryFilter): Int = when (filter) {
    HistoryFilter.ALL -> R.string.history_filter_all
    HistoryFilter.CREATED_EDITED -> R.string.history_filter_created_edited
    HistoryFilter.PROCESSED -> R.string.history_filter_processed
    HistoryFilter.FAVORITES -> R.string.history_filter_favorites
    HistoryFilter.DELETED_RESTORED -> R.string.history_filter_deleted_restored
    HistoryFilter.BACKUPS -> R.string.history_filter_backups
}

private fun actionLabel(action: HistoryAction?): Int = when (action) {
    HistoryAction.OPENED -> R.string.history_action_opened
    HistoryAction.CONVERTED -> R.string.history_action_converted
    HistoryAction.COMPRESSED -> R.string.history_action_compressed
    HistoryAction.BACKED_UP -> R.string.history_action_backed_up
    HistoryAction.SCANNED -> R.string.history_action_scanned
    HistoryAction.FAVORITED -> R.string.history_action_favorited
    HistoryAction.DELETED -> R.string.history_action_deleted
    HistoryAction.EDITED -> R.string.history_action_edited
    HistoryAction.CREATED -> R.string.history_action_created
    HistoryAction.RESTORED -> R.string.history_action_restored
    null -> R.string.history_action_other
}

private fun actionIcon(action: HistoryAction?): ImageVector = when (action) {
    HistoryAction.OPENED -> Icons.AutoMirrored.Rounded.OpenInNew
    HistoryAction.CONVERTED -> Icons.Rounded.SwapHoriz
    HistoryAction.COMPRESSED -> Icons.Rounded.Compress
    HistoryAction.BACKED_UP -> Icons.Rounded.Backup
    HistoryAction.SCANNED -> Icons.Rounded.DocumentScanner
    HistoryAction.FAVORITED -> Icons.Rounded.Star
    HistoryAction.DELETED -> Icons.Rounded.Delete
    HistoryAction.EDITED -> Icons.Rounded.Edit
    HistoryAction.CREATED -> Icons.Rounded.Add
    HistoryAction.RESTORED -> Icons.Rounded.RestoreFromTrash
    null -> Icons.Rounded.History
}

private fun mimeFor(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"
