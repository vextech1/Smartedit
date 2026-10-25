package com.vextech.smart_edit.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ui.ErrorState
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.LoadingState

@Composable
fun NoteEditorScreen(noteId: String?, onBack: () -> Unit, vm: NoteEditorViewModel = hiltViewModel()) {
    LaunchedEffect(noteId) { vm.start(noteId) }
    val leave = { vm.finish(onBack) }
    BackHandler(enabled = vm.load == EditorLoad.READY, onBack = leave)

    FeatureScaffold(
        title = stringResource(if (noteId == null) R.string.notes_new else R.string.notes_edit),
        onBack = if (vm.load == EditorLoad.READY) leave else onBack,
        actions = {
            val label = when (vm.status) {
                SaveStatus.IDLE -> null
                SaveStatus.SAVING -> R.string.notes_saving
                SaveStatus.SAVED -> R.string.notes_saved
                SaveStatus.FAILED -> R.string.notes_save_failed
            }
            if (label != null) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (vm.status == SaveStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        },
    ) { padding ->
        when (vm.load) {
            EditorLoad.LOADING -> LoadingState(Modifier.padding(padding))
            EditorLoad.MISSING -> ErrorState(stringResource(R.string.notes_load_failed), Modifier.padding(padding))
            EditorLoad.READY -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = vm.title,
                    onValueChange = vm::onTitle,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.notes_field_title)) },
                )
                OutlinedTextField(
                    value = vm.tags,
                    onValueChange = vm::onTags,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.notes_field_tags)) },
                )
                OutlinedTextField(
                    value = vm.content,
                    onValueChange = vm::onContent,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    placeholder = { Text(stringResource(R.string.notes_field_content)) },
                )
            }
        }
    }
}
