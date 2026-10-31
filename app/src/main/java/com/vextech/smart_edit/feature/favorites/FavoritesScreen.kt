package com.vextech.smart_edit.feature.favorites

import android.webkit.MimeTypeMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.FavoriteItem
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.EmptyState
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.LoadingState
import com.vextech.smart_edit.core.ui.SectionHeader
import com.vextech.smart_edit.core.ui.TileRow
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import com.vextech.smart_edit.core.ui.formatBytes

@Composable
fun FavoritesScreen(
    onBack: (() -> Unit)?,
    onOpenNote: (String) -> Unit,
    share: ShareService,
    header: (@Composable () -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    vm: FavoritesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val screenEvents = remember { UiEventChannel() }
    CollectUiEvents(vm.uiEvents, snackbarHostState)
    CollectUiEvents(screenEvents.events, snackbarHostState)

    FeatureScaffold(
        title = stringResource(R.string.favorites),
        onBack = onBack,
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            header?.invoke()
            when {
                state.loading -> LoadingState(Modifier.weight(1f))
                state.isEmpty -> EmptyState(
                    stringResource(R.string.favorites_empty_title),
                    stringResource(R.string.favorites_empty_message),
                )
                else -> LazyColumn(Modifier.weight(1f)) {
                    if (state.files.isNotEmpty()) {
                        item(key = "header_files") { SectionHeader(stringResource(R.string.favorites_section_files)) }
                        items(state.files, key = FavoriteItem::id) { item ->
                            FavoriteRow(
                                item = item,
                                onClick = {
                                    val uri = item.uri
                                    if (uri == null) return@FavoriteRow
                                    runCatching { share.openManaged(uri, item.mimeType ?: mimeFor(item.displayName)) }
                                        .onFailure { screenEvents.message(UiText.res(R.string.common_open_failed)) }
                                },
                                onRemove = { vm.remove(item) },
                            )
                        }
                    }
                    if (state.notes.isNotEmpty()) {
                        item(key = "header_notes") { SectionHeader(stringResource(R.string.favorites_section_notes)) }
                        items(state.notes, key = FavoriteItem::id) { item ->
                            FavoriteRow(item = item, onClick = { onOpenNote(item.resourceId) }, onRemove = { vm.remove(item) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(item: FavoriteItem, onClick: () -> Unit, onRemove: () -> Unit) {
    TileRow(
        icon = if (item.isNote) Icons.AutoMirrored.Rounded.Notes else Icons.Rounded.Description,
        title = item.displayName.ifBlank { stringResource(R.string.notes_untitled) },
        subtitle = item.sizeBytes?.let(::formatBytes),
        iconTint = if (item.isNote) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        trailing = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.favorites_remove), tint = MaterialTheme.colorScheme.tertiary)
            }
        },
    )
}

private fun mimeFor(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"
