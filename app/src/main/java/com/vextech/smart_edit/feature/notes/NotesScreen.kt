package com.vextech.smart_edit.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.Note
import com.vextech.smart_edit.core.library.NotePolicies
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.EmptyState
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.LoadingState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(
    onBack: (() -> Unit)?,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onTrash: () -> Unit,
    header: (@Composable () -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    vm: NotesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    CollectUiEvents(vm.uiEvents, snackbarHostState, onAction = vm::onAction)
    var query by rememberSaveable { mutableStateOf("") }

    FeatureScaffold(
        title = stringResource(R.string.notes),
        onBack = onBack,
        actions = {
            if (onSearch != null) {
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search)) }
            }
            IconButton(onClick = onTrash) { Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.trash)) }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.notes_new)) }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            header?.invoke()
            if (state.loading) {
                LoadingState(Modifier.weight(1f))
                return@Column
            }
            if (state.totalCount > 0) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; vm.onQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.notes_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; vm.onQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    },
                )
                if (state.tags.isNotEmpty()) {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = state.selectedTag == null,
                                onClick = { vm.onTag(null) },
                                label = { Text(stringResource(R.string.notes_all_tags)) },
                            )
                        }
                        items(state.tags, key = { it }) { tag ->
                            FilterChip(
                                selected = state.selectedTag.equals(tag, ignoreCase = true),
                                onClick = { vm.onTag(tag) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
            when {
                state.totalCount == 0 -> EmptyState(stringResource(R.string.notes_empty_title), stringResource(R.string.notes_empty_message))
                state.notes.isEmpty() -> EmptyState(stringResource(R.string.notes_no_match_title), stringResource(R.string.notes_no_match_message))
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.notes, key = Note::id) { note ->
                        NoteCard(
                            note = note,
                            onOpen = { onOpen(note.id) },
                            onTogglePin = { vm.togglePin(note) },
                            onToggleFavorite = { vm.toggleFavorite(note) },
                            onTrash = { vm.moveToTrash(note) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(
    note: Note,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTrash: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.isPinned) Icon(Icons.Rounded.PushPin, contentDescription = stringResource(R.string.notes_pinned), tint = MaterialTheme.colorScheme.primary)
                    if (note.isFavorite) Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.notes_favorite_marker), tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        note.title.ifBlank { stringResource(R.string.notes_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val preview = NotePolicies.preview(note.content)
                if (preview.isNotEmpty()) {
                    Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (note.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        note.tags.forEach { tag ->
                            Text("#$tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.notes_more_actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (note.isPinned) R.string.notes_unpin else R.string.notes_pin)) },
                    onClick = { menuOpen = false; onTogglePin() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (note.isFavorite) R.string.notes_unfavorite else R.string.notes_favorite)) },
                    onClick = { menuOpen = false; onToggleFavorite() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notes_move_to_trash)) },
                    onClick = { menuOpen = false; onTrash() },
                )
            }
        }
    }
}
