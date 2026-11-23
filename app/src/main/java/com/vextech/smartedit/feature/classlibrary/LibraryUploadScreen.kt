package com.vextech.smartedit.feature.classlibrary

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smartedit.R
import com.vextech.smartedit.core.auth.AdminRole
import com.vextech.smartedit.core.classlibrary.LibraryCategory
import com.vextech.smartedit.core.classlibrary.Notice
import com.vextech.smartedit.core.classlibrary.UserFile
import com.vextech.smartedit.core.classlibrary.UserLibraryPolicies
import com.vextech.smartedit.core.ui.FeatureScaffold
import com.vextech.smartedit.core.ui.formatBytes

/** Share a document (students) or add a file to the Library (admins). Same fields, limits and rules as the EditPro site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryUploadScreen(admin: Boolean, onBack: () -> Unit, vm: LibraryUploadViewModel = hiltViewModel()) {
    LaunchedEffect(admin) { vm.start(admin) }
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
    val allowed = if (admin) com.vextech.smartedit.core.classlibrary.ClassLibraryParser.ALLOWED_EXT else UserLibraryPolicies.USER_EXT.toSet()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pick) }
    var deleting by remember { mutableStateOf<UserFile?>(null) }

    FeatureScaffold(
        title = stringResource(if (admin) R.string.upload_admin_title else R.string.upload_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.checking -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                !ui.auth.enabled -> Centered(stringResource(R.string.account_unavailable))
                ui.auth.user == null -> SignInPrompt(stringResource(R.string.upload_sign_in_body)) { vm.signIn(context) }
                admin && ui.role == AdminRole.NONE -> Centered(stringResource(R.string.upload_not_admin))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(stringResource(if (admin) R.string.upload_intro_admin else R.string.upload_intro_student), style = MaterialTheme.typography.bodyMedium) }
                    item {
                        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = ui.progress == null, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.upload_choose_file))
                        }
                        Text(
                            ui.picked?.let { "${it.name} · ${formatBytes(it.size)}" } ?: stringResource(R.string.upload_no_file),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = ui.title, onValueChange = vm::setTitle, label = { Text(stringResource(R.string.upload_field_title)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (ui.sections.isEmpty()) {
                        item { Text(stringResource(R.string.upload_no_structure), color = MaterialTheme.colorScheme.error) }
                    } else {
                        val section = ui.sections.firstOrNull { it.id == ui.sectionId }
                        val cls = section?.classes?.firstOrNull { it.id == ui.classId }
                        item {
                            Picker(stringResource(R.string.upload_field_section), section?.name.orEmpty(), ui.sections.map { it.name to { vm.setSection(it.id) } })
                        }
                        item {
                            Picker(stringResource(R.string.upload_field_class), cls?.name.orEmpty(), section?.classes.orEmpty().map { it.name to { vm.setClass(it.id) } })
                        }
                        item {
                            val none = stringResource(R.string.upload_no_subject)
                            val options = listOf(none to { vm.setSubject(null) }) + cls?.subjects.orEmpty().map { it.name to { vm.setSubject(it.id) } }
                            Picker(stringResource(R.string.upload_field_subject), cls?.subjects?.firstOrNull { it.id == ui.subjectId }?.name ?: none, options)
                        }
                    }
                    item {
                        Text(stringResource(R.string.upload_field_category), style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibraryCategory.entries.forEach { c ->
                                FilterChip(selected = ui.category == c, onClick = { vm.setCategory(c) }, label = { Text(stringResource(categoryName(c))) })
                            }
                        }
                    }
                    if (ui.category == LibraryCategory.OTHER) {
                        item {
                            OutlinedTextField(
                                value = ui.label, onValueChange = vm::setLabel, label = { Text(stringResource(R.string.upload_field_label)) },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.upload_allowed, allowed.sorted().joinToString(", ") { ".$it" }),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        val progress = ui.progress
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress.first / progress.second.toFloat().coerceAtLeast(1f) }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.upload_progress, progress.first, progress.second), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Button(onClick = vm::upload, enabled = ui.canUpload, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Text(stringResource(R.string.upload_button))
                            }
                        }
                    }
                    if (!admin) {
                        items(ui.notices, key = { "n-${it.id}" }) { n -> NoticeCard(n) { vm.dismissNotice(n) } }
                        item { Text(stringResource(R.string.upload_my_files), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                        if (ui.myFiles.isEmpty()) {
                            item { Text(stringResource(R.string.upload_none_yet), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(ui.myFiles, key = { "f-${it.id}" }) { f -> MyFileRow(f) { deleting = f } }
                    }
                }
            }
        }
    }

    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.upload_delete)) },
            text = { Text(stringResource(R.string.upload_delete_confirm, f.title)) },
            confirmButton = { TextButton(onClick = { vm.remove(f); deleting = null }) { Text(stringResource(R.string.upload_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.library_gate_cancel)) } },
        )
    }
}

@Composable
internal fun SignInPrompt(text: String, onSignIn: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
        Button(onClick = onSignIn) { Text(stringResource(R.string.account_sign_in)) }
    }
}

@Composable
internal fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A label, the current choice, and a menu of choices. */
@Composable
private fun Picker(label: String, current: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(current.ifEmpty { "—" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (name, action) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; action() }) }
            }
        }
    }
}

@Composable
private fun NoticeCard(n: Notice, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.upload_notice_pulled, n.title, n.reason), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.upload_dismiss)) }
        }
    }
}

@Composable
private fun MyFileRow(f: UserFile, onDelete: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(f.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${stringResource(statusName(f.status))} · ${formatBytes(f.size)} · ${f.added}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.upload_delete)) }
        }
    }
}

private fun statusName(status: String): Int = when (status) {
    "live" -> R.string.upload_status_live
    "pulled" -> R.string.upload_status_pulled
    else -> R.string.upload_status_uploading
}

private fun categoryName(c: LibraryCategory): Int = when (c) {
    LibraryCategory.NOTES -> R.string.library_cat_notes
    LibraryCategory.ASSIGNMENTS -> R.string.library_cat_assignments
    LibraryCategory.PAST_PAPERS -> R.string.library_cat_past_papers
    LibraryCategory.OTHER -> R.string.library_cat_other
}
