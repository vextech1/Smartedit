package com.vextech.smartedit.feature.classlibrary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smartedit.R
import com.vextech.smartedit.core.auth.AdminRole
import com.vextech.smartedit.core.classlibrary.UserFile
import com.vextech.smartedit.core.classlibrary.UserLibraryPolicies
import com.vextech.smartedit.core.ui.FeatureScaffold
import com.vextech.smartedit.core.ui.formatBytes

/** Admin review of the files students shared: remove one (the student is told why), clean up, and pause new uploads. */
@Composable
fun StudentFilesScreen(onBack: () -> Unit, vm: StudentFilesViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val message = ui.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.clearMessage()
        }
    }
    var pulling by remember { mutableStateOf<UserFile?>(null) }

    FeatureScaffold(title = stringResource(R.string.students_title), onBack = onBack, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.checking -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ui.role == AdminRole.NONE -> Centered(stringResource(R.string.upload_not_admin))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.students_uploads_open), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = ui.uploadsOpen, onCheckedChange = vm::setUploadsOpen)
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !ui.showPulled, onClick = { vm.showPulled(false) }, label = { Text(stringResource(R.string.students_tab_live)) })
                            FilterChip(selected = ui.showPulled, onClick = { vm.showPulled(true) }, label = { Text(stringResource(R.string.students_tab_pulled)) })
                        }
                    }
                    if (ui.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (!ui.loading && ui.files.isEmpty()) item { Text(stringResource(R.string.students_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(ui.files, key = { it.id }) { f ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(f.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${f.name} · ${formatBytes(f.size)} · ${f.added}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                f.pulledReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (f.status == "live") {
                                        TextButton(onClick = { pulling = f }) { Text(stringResource(R.string.students_pull)) }
                                    } else {
                                        if (!f.cleaned) TextButton(onClick = { vm.retry(f) }) { Text(stringResource(R.string.students_retry)) }
                                        TextButton(onClick = { vm.deleteRecord(f) }) { Text(stringResource(R.string.students_delete_record)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pulling?.let { f ->
        var reason by remember(f.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pulling = null },
            title = { Text(stringResource(R.string.students_pull_title, f.title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    UserLibraryPolicies.REASON_CHOICES.forEach { choice ->
                        Row(Modifier.fillMaxWidth().selectable(selected = reason == choice, onClick = { reason = choice }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = reason == choice, onClick = { reason = choice })
                            Text(choice, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedTextField(value = reason, onValueChange = { reason = it.take(UserLibraryPolicies.MAX_REASON) }, label = { Text(stringResource(R.string.students_pull_reason)) })
                }
            },
            confirmButton = { TextButton(onClick = { vm.pull(f, reason); pulling = null }, enabled = reason.trim().length >= 5) { Text(stringResource(R.string.students_pull)) } },
            dismissButton = { TextButton(onClick = { pulling = null }) { Text(stringResource(R.string.library_gate_cancel)) } },
        )
    }
}
