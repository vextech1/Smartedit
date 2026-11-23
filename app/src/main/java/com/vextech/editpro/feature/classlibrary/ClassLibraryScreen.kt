@file:OptIn(ExperimentalMaterial3Api::class)

package com.vextech.editpro.feature.classlibrary

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.editpro.R
import com.vextech.editpro.core.classlibrary.AccessPolicies
import com.vextech.editpro.core.classlibrary.ClassLibraryFilters
import com.vextech.editpro.core.classlibrary.ClassLibraryLinks
import com.vextech.editpro.core.classlibrary.CloudFile
import com.vextech.editpro.core.classlibrary.LibraryCategory
import com.vextech.editpro.core.ui.FeatureScaffold
import com.vextech.editpro.core.ui.formatBytes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class Choice(val key: String, val text: String, val selected: Boolean, val onClick: () -> Unit)

/** The Library tab: the same sections, classes, subjects and categories as the site, with search, download and share. */
@Composable
fun ClassLibraryScreen(onUpload: () -> Unit = {}, vm: ClassLibraryViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val message = ui.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.clearMessage()
        }
    }

    FeatureScaffold(
        title = stringResource(R.string.nav_library), onBack = null,
        actions = { TextButton(onClick = onUpload) { Text(stringResource(R.string.library_upload_action)) } },
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.unavailable -> Notice(stringResource(R.string.library_unavailable), null)
                ui.failed -> Notice(stringResource(R.string.library_load_failed)) { vm.load(force = true) }
                else -> LibraryContent(ui, vm)
            }
        }
    }

    ui.selected?.let { file -> FileSheet(file, ui.progress, onDownload = { vm.download(file) }, onShare = { vm.share(file) }, onDismiss = vm::dismissDetails) }

    ui.gate?.let { gate ->
        val signIn = gate.access == AccessPolicies.Access.SIGN_IN
        AlertDialog(
            onDismissRequest = vm::dismissGate,
            title = { Text(stringResource(if (signIn) R.string.library_gate_signin_title else R.string.library_gate_upload_title)) },
            text = { Text(stringResource(if (signIn) R.string.library_gate_signin_body else R.string.library_gate_upload_body)) },
            confirmButton = {
                TextButton(onClick = {
                    if (signIn) {
                        vm.dismissGate()
                        vm.signInAndRetry(context, gate.file)
                    } else {
                        vm.dismissGate()
                        onUpload()
                    }
                }) { Text(stringResource(if (signIn) R.string.account_sign_in else R.string.library_gate_upload_action)) }
            },
            dismissButton = { TextButton(onClick = vm::dismissGate) { Text(stringResource(R.string.library_gate_cancel)) } },
        )
    }
}

@Composable
private fun Notice(text: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.library_retry)) }
        }
    }
}

@Composable
private fun LibraryContent(ui: ClassLibraryUiState, vm: ClassLibraryViewModel) {
    val scoped = ui.scoped
    val chips = ui.chips
    val visible = ui.visible
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        if (ui.sections.size > 1) {
            item(key = "sections") { ChoiceRow(ui.sections.map { Choice(it.id, it.name, it.id == ui.sectionId) { vm.selectSection(it.id) } }) }
        }
        val classes = ui.section?.classes.orEmpty()
        if (classes.isNotEmpty()) {
            item(key = "classes") { ChoiceRow(classes.map { Choice(it.id, it.name, it.id == ui.classId) { vm.selectClass(it.id) } }) }
        }
        val subjects = classes.firstOrNull { it.id == ui.classId }?.subjects.orEmpty()
        if (subjects.isNotEmpty()) {
            item(key = "subjects") {
                val all = Choice("all", stringResource(R.string.library_all_subjects), ui.subjectId == null) { vm.selectSubject(null) }
                ChoiceRow(listOf(all) + subjects.map { Choice(it.id, it.name, it.id == ui.subjectId) { vm.selectSubject(it.id) } })
            }
        }
        if (chips.isNotEmpty()) {
            item(key = "categories") {
                val all = Choice(ClassLibraryFilters.ALL, stringResource(R.string.library_all), ui.categoryKey == ClassLibraryFilters.ALL) { vm.selectCategory(ClassLibraryFilters.ALL) }
                val each = chips.map { chip ->
                    Choice(chip.key, "${categoryText(chip.category, chip.label)} (${chip.count})", ui.categoryKey == chip.key) { vm.selectCategory(chip.key) }
                }
                ChoiceRow(listOf(all) + each)
            }
        }
        if (scoped.isNotEmpty()) {
            item(key = "search") {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.library_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (ui.partial) {
            item(key = "partial") { Text(stringResource(R.string.library_partial), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        when {
            scoped.isEmpty() -> item(key = "empty") { Text(stringResource(R.string.library_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            visible.isEmpty() -> item(key = "nomatch") { Text(stringResource(R.string.library_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> items(visible, key = { it.id }) { file -> FileRow(file, onOpen = { vm.open(file) }, onShare = { vm.share(file) }) }
        }
    }
}

@Composable
private fun ChoiceRow(choices: List<Choice>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices, key = { it.key }) { choice ->
            FilterChip(selected = choice.selected, onClick = choice.onClick, label = { Text(choice.text, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

@Composable
private fun FileRow(file: CloudFile, onOpen: () -> Unit, onShare: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ExtBadge(file.ext)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(file.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(meta(file), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.library_share)) }
        }
    }
}

@Composable
private fun ExtBadge(ext: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(44.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(ext.uppercase().take(4), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun FileSheet(file: CloudFile, progress: DownloadProgress?, onDownload: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    val mine = progress?.takeIf { it.fileId == file.id }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(file.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(meta(file), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mine != null) {
                LinearProgressIndicator(progress = { if (mine.total == 0) 0f else mine.done.toFloat() / mine.total }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.library_downloading, mine.done, mine.total), style = MaterialTheme.typography.bodySmall)
            } else {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.library_download))
                }
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.library_share))
            }
        }
    }
}

/** "PDF · 1.2 MB · 5 Jan 2026 · Notes", leaving out whatever the entry does not have. */
@Composable
private fun meta(file: CloudFile): String {
    val date = runCatching { LocalDate.parse(file.added).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull()
    return listOfNotNull(file.ext.uppercase(), formatBytes(file.size), date, categoryText(file.category, file.label)).joinToString(" · ")
}

/** An admin's own label as typed, or the built-in category's name. */
@Composable
private fun categoryText(category: LibraryCategory?, label: String?): String = when {
    !label.isNullOrBlank() -> label
    category == LibraryCategory.NOTES -> stringResource(R.string.library_cat_notes)
    category == LibraryCategory.ASSIGNMENTS -> stringResource(R.string.library_cat_assignments)
    category == LibraryCategory.PAST_PAPERS -> stringResource(R.string.library_cat_past_papers)
    else -> stringResource(R.string.library_cat_other)
}
