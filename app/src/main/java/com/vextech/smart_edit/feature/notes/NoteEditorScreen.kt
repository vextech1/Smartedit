package com.vextech.smart_edit.feature.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.NotePolicies
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.ErrorState
import com.vextech.smart_edit.core.ui.LoadingState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(noteId: String?, onBack: () -> Unit, share: ShareService, vm: NoteEditorViewModel = hiltViewModel()) {
    LaunchedEffect(noteId) { vm.start(noteId) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val copiedText = stringResource(R.string.notes_copied)
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var addTag by remember { mutableStateOf(false) }
    val leave = { vm.finish(onBack) }
    BackHandler(enabled = vm.load == EditorLoad.READY, onBack = leave)
    val hasText = vm.content.isNotBlank() || vm.title.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val label = when (vm.status) {
                        SaveStatus.IDLE -> null
                        SaveStatus.SAVING -> R.string.notes_saving
                        SaveStatus.SAVED -> R.string.notes_saved
                        SaveStatus.FAILED -> R.string.notes_save_failed
                    }
                    if (label != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (vm.status == SaveStatus.SAVED) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(label), style = MaterialTheme.typography.labelLarge,
                                color = if (vm.status == SaveStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (vm.load == EditorLoad.READY) leave() else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (vm.load == EditorLoad.READY) {
                        IconButton(onClick = vm::togglePin, enabled = vm.isStored) {
                            Icon(
                                Icons.Rounded.PushPin, stringResource(if (vm.isPinned) R.string.notes_unpin else R.string.notes_pin),
                                tint = if (vm.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = vm::toggleFavorite, enabled = vm.isStored) {
                            Icon(
                                if (vm.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                stringResource(if (vm.isFavorite) R.string.notes_unfavorite else R.string.notes_favorite),
                                tint = if (vm.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.notes_more_actions)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (vm.load) {
            EditorLoad.LOADING -> LoadingState(Modifier.padding(padding))
            EditorLoad.MISSING -> ErrorState(stringResource(R.string.notes_load_failed), Modifier.padding(padding))
            EditorLoad.READY -> Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                Column(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlainField(
                        value = vm.title, onChange = vm::onTitle, placeholder = stringResource(R.string.notes_field_title),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        NotePolicies.parseTags(vm.tags).forEach { tag ->
                            InputChip(
                                selected = false, onClick = {},
                                label = { Text("#$tag") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close, stringResource(R.string.notes_remove_tag, tag),
                                        Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).clickable { vm.removeTag(tag) },
                                    )
                                },
                            )
                        }
                        AssistChip(
                            onClick = { addTag = true },
                            label = { Text(stringResource(R.string.notes_add_tag)) },
                            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                        )
                    }
                    PlainField(
                        value = vm.content, onChange = vm::onContent, placeholder = stringResource(R.string.notes_field_content),
                        textStyle = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                HorizontalDivider()
                val words = remember(vm.content) { vm.content.trim().split(Regex("\\s+")).count { it.isNotEmpty() } }
                val wordsText = pluralStringResource(R.plurals.notes_word_count, words, words)
                Text(
                    if (vm.updatedAt > 0L) stringResource(
                        R.string.notes_footer_edited, wordsText,
                        DateUtils.getRelativeTimeSpanString(vm.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    ) else wordsText,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }

    if (menu) {
        ModalBottomSheet(onDismissRequest = { menu = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                val text = listOf(vm.title, vm.content).filter { it.isNotBlank() }.joinToString("\n\n")
                MenuRow(Icons.Rounded.Share, stringResource(R.string.notes_share), hasText) {
                    menu = false
                    runCatching { share.shareText(text, vm.title.ifBlank { null }) }
                }
                MenuRow(Icons.Rounded.ContentCopy, stringResource(R.string.notes_copy), hasText) {
                    menu = false
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", vm.content))
                    scope.launch { snackbar.showSnackbar(copiedText) }
                }
                MenuRow(Icons.Rounded.PushPin, stringResource(if (vm.isPinned) R.string.notes_unpin else R.string.notes_pin), vm.isStored) { menu = false; vm.togglePin() }
                MenuRow(
                    if (vm.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    stringResource(if (vm.isFavorite) R.string.notes_unfavorite else R.string.notes_favorite), vm.isStored,
                ) { menu = false; vm.toggleFavorite() }
                MenuRow(Icons.Rounded.Delete, stringResource(R.string.notes_move_to_trash), hasText || vm.isStored, danger = true) { menu = false; vm.trash(onBack) }
            }
        }
    }

    if (addTag) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addTag = false },
            title = { Text(stringResource(R.string.notes_add_tag_title)) },
            text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, label = { Text(stringResource(R.string.notes_tag_hint)) }) },
            confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { vm.addTag(value); addTag = false }) { Text(stringResource(R.string.notes_add_tag)) } },
            dismissButton = { TextButton(onClick = { addTag = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A text field with no box, so the note reads like a page. */
@Composable
private fun PlainField(
    value: String, onChange: (String) -> Unit, placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, singleLine: Boolean = false,
) {
    TextField(
        value = value, onValueChange = onChange, modifier = modifier, singleLine = singleLine, textStyle = textStyle,
        placeholder = { Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = if (danger || !enabled) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
