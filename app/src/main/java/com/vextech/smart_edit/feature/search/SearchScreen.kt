package com.vextech.smart_edit.feature.search

import android.webkit.MimeTypeMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.SearchFilter
import com.vextech.smart_edit.core.library.SearchPolicies
import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.core.model.SearchHit
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.EmptyState
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.TileRowRich
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    share: ShareService,
    vm: SearchViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val events = remember { UiEventChannel() }
    CollectUiEvents(events.events, snackbarHostState)

    FeatureScaffold(
        title = stringResource(R.string.search),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; vm.onQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; vm.onQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                },
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchFilter.entries.forEach { option ->
                    FilterChip(
                        selected = state.filter == option,
                        onClick = { vm.onFilter(option) },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        SearchFilter.ALL -> R.string.search_filter_all
                                        SearchFilter.FILES -> R.string.search_filter_files
                                        SearchFilter.NOTES -> R.string.search_filter_notes
                                    }
                                )
                            )
                        },
                    )
                }
            }
            when {
                state.idle -> EmptyState(stringResource(R.string.search_prompt_title), stringResource(R.string.search_prompt_message))
                state.failed -> EmptyState(stringResource(R.string.search_failed_title), stringResource(R.string.search_failed_message))
                state.hits.isEmpty() -> EmptyState(
                    stringResource(R.string.search_no_results_title),
                    stringResource(R.string.search_no_results_message, query.trim()),
                )
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(state.hits, key = { it.type.name + ":" + it.resourceId }) { hit ->
                        HitRow(hit, state.terms) {
                            if (hit.type == ResourceType.NOTE) {
                                onOpenNote(hit.resourceId)
                            } else {
                                val uri = hit.uri
                                if (uri != null) {
                                    runCatching { share.openManaged(uri, mimeFor(hit.title)) }
                                        .onFailure { events.message(UiText.res(R.string.common_open_failed)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, terms: List<String>, onClick: () -> Unit) {
    val isNote = hit.type == ResourceType.NOTE
    val title = hit.title.ifBlank { stringResource(R.string.notes_untitled) }
    val typeLabel = stringResource(if (isNote) R.string.search_type_note else R.string.search_type_file)
    val snippet = if (isNote && hit.snippet.isNotBlank()) highlight(hit.snippet, terms) else null
    TileRowRich(
        icon = if (isNote) Icons.AutoMirrored.Rounded.Notes else Icons.Rounded.Description,
        title = highlight(title, terms),
        subtitle = buildAnnotatedString {
            append(typeLabel)
            if (snippet != null) { append(" · "); append(snippet) }
        },
        iconTint = if (isNote) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
    )
}

@Composable
private fun highlight(text: String, terms: List<String>): AnnotatedString {
    val background = MaterialTheme.colorScheme.primaryContainer
    val ranges = SearchPolicies.highlightRanges(text, terms)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { r ->
            addStyle(SpanStyle(background = background, fontWeight = FontWeight.Bold), r.first, r.last + 1)
        }
    }
}

private fun mimeFor(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"
