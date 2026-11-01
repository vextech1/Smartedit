package com.vextech.smart_edit.feature.notes

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.Note
import com.vextech.smart_edit.core.library.NotePolicies
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.EmptyState
import com.vextech.smart_edit.core.ui.LoadingState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    val selected = state.notes.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty()
    // A note that disappears (trashed, undone elsewhere) must not stay selected.
    LaunchedEffect(state.notes) { selectedIds = selectedIds.filterTo(mutableSetOf()) { id -> state.notes.any { it.id == id } } }
    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    fun toggle(note: Note) {
        selectedIds = if (note.id in selectedIds) selectedIds - note.id else selectedIds + note.id
    }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(stringResource(R.string.notes_selected_count, selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Rounded.Close, stringResource(R.string.notes_cancel_selection)) }
                    },
                    actions = { TextButton(onClick = { selectedIds = state.notes.map { it.id }.toSet() }) { Text(stringResource(R.string.notes_select_all)) } },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.notes)) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                    },
                    actions = {
                        if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
                        IconButton(onClick = onTrash) { Icon(Icons.Rounded.Delete, stringResource(R.string.trash)) }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!selecting) FloatingActionButton(onClick = onNew) { Icon(Icons.Rounded.Add, stringResource(R.string.notes_new)) }
        },
        bottomBar = {
            if (selecting) {
                val allPinned = selected.all { it.isPinned }
                val allFavorite = selected.all { it.isFavorite }
                Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        BarAction(Icons.Rounded.PushPin, if (allPinned) R.string.notes_unpin else R.string.notes_pin) {
                            vm.setPinned(selected, !allPinned); selectedIds = emptySet()
                        }
                        BarAction(if (allFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (allFavorite) R.string.notes_unfavorite else R.string.notes_favorite) {
                            vm.setFavorite(selected, !allFavorite); selectedIds = emptySet()
                        }
                        BarAction(Icons.Rounded.Delete, R.string.trash, MaterialTheme.colorScheme.error) {
                            vm.moveToTrash(selected); selectedIds = emptySet()
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            header?.invoke()
            when {
                state.loading -> LoadingState()
                state.totalCount == 0 -> EmptyState(title = stringResource(R.string.notes_empty_title), description = stringResource(R.string.notes_empty_message))
                else -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; vm.onQuery(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; vm.onQuery("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.search_clear)) }
                        },
                        placeholder = { Text(stringResource(R.string.notes_search_hint)) },
                    )
                    if (state.tags.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = state.selectedTag == null, onClick = { vm.onTag(null) }, label = { Text(stringResource(R.string.notes_all_tags)) })
                            state.tags.forEach { tag ->
                                FilterChip(selected = state.selectedTag == tag, onClick = { vm.onTag(if (state.selectedTag == tag) null else tag) }, label = { Text(tag) })
                            }
                        }
                    }
                    if (state.notes.isEmpty()) {
                        EmptyState(title = stringResource(R.string.notes_no_match_title), description = stringResource(R.string.notes_no_match_message))
                    } else {
                        val pinned = state.notes.filter { it.isPinned }
                        val others = state.notes.filterNot { it.isPinned }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (pinned.isNotEmpty()) {
                                item(key = "header_pinned") { SectionLabel(stringResource(R.string.notes_section_pinned)) }
                                items(pinned, key = { it.id }) { note -> NoteCard(note, selecting, note.id in selectedIds, { if (selecting) toggle(note) else onOpen(note.id) }, { toggle(note) }) }
                            }
                            if (others.isNotEmpty()) {
                                if (pinned.isNotEmpty()) item(key = "header_earlier") { SectionLabel(stringResource(R.string.notes_section_earlier)) }
                                items(others, key = { it.id }) { note -> NoteCard(note, selecting, note.id in selectedIds, { if (selecting) toggle(note) else onOpen(note.id) }, { toggle(note) }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(note: Note, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        note.title.ifBlank { stringResource(R.string.notes_untitled) },
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (note.isPinned) Icon(Icons.Rounded.PushPin, stringResource(R.string.notes_pinned), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    if (note.isFavorite) Icon(Icons.Rounded.Star, stringResource(R.string.notes_favorite_marker), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        DateUtils.getRelativeTimeSpanString(note.updatedAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val preview = NotePolicies.preview(note.content)
                if (preview.isNotBlank()) {
                    Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (note.tags.isNotEmpty()) {
                    Text(note.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (selecting) {
                Surface(
                    shape = CircleShape, modifier = Modifier.size(24.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    border = if (selected) null else androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: Int, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).defaultMinSize(minWidth = 72.dp, minHeight = 56.dp).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
