package com.vextech.smart_edit.feature.files

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import com.vextech.smart_edit.core.library.FileActionPolicies
import com.vextech.smart_edit.core.library.FileAction
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.ConfirmDialog
import com.vextech.smart_edit.core.ui.EmptyState
import com.vextech.smart_edit.core.ui.formatBytes
import com.vextech.smart_edit.feature.files.domain.FileItem
import com.vextech.smart_edit.feature.files.domain.FileSort
import com.vextech.smart_edit.feature.files.domain.FileTreePolicy
import com.vextech.smart_edit.feature.files.domain.FileViewMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onBack: (() -> Unit)?,
    share: ShareService,
    header: (@Composable () -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onTrash: (() -> Unit)? = null,
    onToolAction: ((FileAction, String) -> Unit)? = null,
    vm: FilesViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var createFolder by remember { mutableStateOf(false) }
    var renameItem by remember { mutableStateOf<FileItem?>(null) }
    var moveItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteItem by remember { mutableStateOf<FileItem?>(null) }
    var detailsItem by remember { mutableStateOf<FileItem?>(null) }
    var sortMenu by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importDocuments(uris)
    }

    fun navigateBack() {
        if (state.canGoUp) vm.goUp() else onBack?.invoke()
    }

    fun open(item: FileItem) {
        if (item.isDirectory) {
            vm.navigateToFolder(item.id)
        } else {
            runCatching { share.openManaged(item.uri, item.mimeType) }
                .onFailure { scope.launch { snackbar.showSnackbar(context.getString(R.string.files_open_failed)) } }
        }
    }

    BackHandler(enabled = state.canGoUp || onBack != null, onBack = ::navigateBack)

    val messageText = state.message?.let { filesMessageText(it) }
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.currentFolderId == null) stringResource(R.string.files) else state.folderName) },
                navigationIcon = {
                    if (state.canGoUp || onBack != null) {
                        IconButton(onClick = ::navigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                actions = {
                    if (onSearch != null) {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Rounded.Search, stringResource(R.string.search))
                        }
                    }
                    IconButton(onClick = vm::toggleViewMode) {
                        Icon(
                            imageVector = if (state.viewMode == FileViewMode.LIST) Icons.Rounded.GridView else Icons.Rounded.List,
                            contentDescription = stringResource(R.string.files_toggle_view),
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Rounded.Sort, stringResource(R.string.files_sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            FileSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        val suffix = if (state.sort == option) {
                                            if (state.ascending) " ↑" else " ↓"
                                        } else ""
                                        Text(sortLabel(option) + suffix)
                                    },
                                    onClick = {
                                        vm.setSort(option)
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh))
                    }
                    FilesMoreMenu(
                        onTrash = onTrash,
                        onImport = { picker.launch(arrayOf("*/*")) },
                        onNewFolder = { createFolder = true },
                        onClipboard = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            vm.importClipboard(text)
                        },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { createFolder = true }) {
                Icon(Icons.Rounded.CreateNewFolder, stringResource(R.string.files_new_folder))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            header?.invoke()
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Breadcrumbs(state = state, onNavigate = vm::navigateToBreadcrumb)
            HorizontalDivider()
            if (state.items.isEmpty() && !state.isBusy) {
                EmptyState(
                    title = stringResource(R.string.files_empty_title),
                    description = stringResource(R.string.files_empty_description),
                )
            } else if (state.viewMode == FileViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(state.items, key = FileItem::id) { item ->
                        FileListRow(
                            item = item,
                            onOpen = { open(item) },
                            onFavorite = { vm.toggleFavorite(item) },
                            onShare = {
                                runCatching { share.shareManaged(item.uri, item.mimeType) }
                                    .onFailure { scope.launch { snackbar.showSnackbar(context.getString(R.string.files_share_failed)) } }
                            },
                            onRename = { renameItem = item },
                            onMove = { moveItem = item },
                            onDetails = { detailsItem = item },
                            onTool = { action -> onToolAction?.invoke(action, item.uri) },
                            onDelete = {
                                if (state.confirmDeleteEnabled) deleteItem = item else vm.trash(item)
                            },
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(132.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = FileItem::id) { item ->
                        FileGridCard(
                            item = item,
                            onOpen = { open(item) },
                            onFavorite = { vm.toggleFavorite(item) },
                            onShare = {
                                runCatching { share.shareManaged(item.uri, item.mimeType) }
                                    .onFailure { scope.launch { snackbar.showSnackbar(context.getString(R.string.files_share_failed)) } }
                            },
                            onRename = { renameItem = item },
                            onMove = { moveItem = item },
                            onDetails = { detailsItem = item },
                            onTool = { action -> onToolAction?.invoke(action, item.uri) },
                            onDelete = {
                                if (state.confirmDeleteEnabled) deleteItem = item else vm.trash(item)
                            },
                        )
                    }
                }
            }
        }
    }

    if (createFolder) {
        NameDialog(
            title = stringResource(R.string.files_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { createFolder = false },
            onConfirm = {
                vm.createFolder(it)
                createFolder = false
            },
        )
    }

    renameItem?.let { item ->
        NameDialog(
            title = stringResource(R.string.rename),
            initial = item.name,
            confirmLabel = stringResource(R.string.rename),
            onDismiss = { renameItem = null },
            onConfirm = {
                vm.rename(item, it)
                renameItem = null
            },
        )
    }

    moveItem?.let { item ->
        MoveDialog(
            item = item,
            state = state,
            onDismiss = { moveItem = null },
            onMove = { destinationId ->
                vm.move(item, destinationId)
                moveItem = null
            },
        )
    }

    deleteItem?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.files_move_to_trash_title),
            message = stringResource(R.string.files_move_to_trash_message, item.name),
            confirmLabel = stringResource(R.string.files_move_to_trash),
            onConfirm = {
                vm.trash(item)
                deleteItem = null
            },
            onDismiss = { deleteItem = null },
        )
    }

    detailsItem?.let { item ->
        DetailsDialog(item = item, state = state, onDismiss = { detailsItem = null })
    }
}

@Composable
private fun Breadcrumbs(state: FilesUiState, onNavigate: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.breadcrumbs.forEachIndexed { index, crumb ->
            Text(
                text = if (crumb.id == null) stringResource(R.string.files) else crumb.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (index == state.breadcrumbs.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = index != state.breadcrumbs.lastIndex) { onNavigate(crumb.id) }.padding(4.dp),
            )
            if (index != state.breadcrumbs.lastIndex) {
                Text("/", modifier = Modifier.padding(horizontal = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FileListRow(
    item: FileItem,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDetails: () -> Unit,
    onTool: (FileAction) -> Unit = {},
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (item.isDirectory) stringResource(R.string.files_folder) else "${item.mimeType} • ${formatBytes(item.sizeBytes)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { Icon(if (item.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description, null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.isDirectory) {
                    IconButton(onClick = onFavorite) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = stringResource(R.string.files_favorite),
                        )
                    }
                }
                FileItemMenu(item, onShare, onRename, onMove, onDetails, onDelete, onTool)
            }
        },
    )
}

@Composable
private fun FileGridCard(
    item: FileItem,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
    onTool: (FileAction) -> Unit = {},
) {
    ElevatedCard(onClick = onOpen) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (item.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description, null)
                Spacer(Modifier.weight(1f))
                if (!item.isDirectory) {
                    IconButton(onClick = onFavorite) {
                        Icon(if (item.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, stringResource(R.string.files_favorite))
                    }
                }
                FileItemMenu(item, onShare, onRename, onMove, onDetails, onDelete, onTool)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                if (item.isDirectory) stringResource(R.string.files_folder) else formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileItemMenu(
    item: FileItem,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
    onTool: (FileAction) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val toolActions = remember(item.name, item.mimeType, item.isDirectory) {
        FileActionPolicies.actionsFor(item.name, item.mimeType, item.isDirectory)
    }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            toolActions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(toolActionLabel(action))) },
                    leadingIcon = { Icon(toolActionIcon(action), null) },
                    onClick = { expanded = false; onTool(action) },
                )
            }
            if (toolActions.isNotEmpty()) HorizontalDivider()
            if (!item.isDirectory) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                    onClick = { expanded = false; onShare() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                onClick = { expanded = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_move)) },
                leadingIcon = { Icon(Icons.Rounded.MoveToInbox, null) },
                onClick = { expanded = false; onMove() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.details)) },
                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                onClick = { expanded = false; onDetails() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_move_to_trash)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@Composable
private fun FilesMoreMenu(onImport: () -> Unit, onNewFolder: () -> Unit, onClipboard: () -> Unit, onTrash: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_import)) },
                leadingIcon = { Icon(Icons.Rounded.UploadFile, null) },
                onClick = { expanded = false; onImport() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_new_folder)) },
                leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                onClick = { expanded = false; onNewFolder() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_import_clipboard)) },
                leadingIcon = { Icon(Icons.Rounded.ContentPaste, null) },
                onClick = { expanded = false; onClipboard() },
            )
            if (onTrash != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trash)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { expanded = false; onTrash() },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MoveDialog(
    item: FileItem,
    state: FilesUiState,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
) {
    val targets = state.moveTargets(item.id)
    val rootLabel = stringResource(R.string.files)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_move_title, item.name)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                if (item.parentId != null) {
                    item(key = "root") {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.files_root)) },
                            leadingContent = { Icon(Icons.Rounded.Folder, null) },
                            modifier = Modifier.clickable { onMove(null) },
                        )
                    }
                }
                items(targets.filter { it.id != item.parentId }, key = FileItem::id) { folder ->
                    val path = FileTreePolicy.breadcrumbs(state.allItems, folder.id).joinToString(" / ") { if (it.id == null) rootLabel else it.name }
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        supportingContent = { Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Rounded.Folder, null) },
                        modifier = Modifier.clickable { onMove(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DetailsDialog(item: FileItem, state: FilesUiState, onDismiss: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val rootLabel = stringResource(R.string.files)
    val folderPath = FileTreePolicy.breadcrumbs(state.allItems, item.parentId).joinToString(" / ") { if (it.id == null) rootLabel else it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailLine(stringResource(R.string.files_type), if (item.isDirectory) stringResource(R.string.files_folder) else item.mimeType)
                if (!item.isDirectory) DetailLine(stringResource(R.string.files_size), formatBytes(item.sizeBytes))
                DetailLine(stringResource(R.string.files_location), folderPath)
                DetailLine(stringResource(R.string.files_created), dateFormat.format(Date(item.createdAt)))
                DetailLine(stringResource(R.string.files_modified), dateFormat.format(Date(item.modifiedAt)))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun sortLabel(sort: FileSort): String = when (sort) {
    FileSort.NAME -> stringResource(R.string.files_sort_name)
    FileSort.MODIFIED -> stringResource(R.string.files_sort_modified)
    FileSort.SIZE -> stringResource(R.string.files_sort_size)
    FileSort.TYPE -> stringResource(R.string.files_sort_type)
}
@Composable
private fun filesMessageText(message: FilesUiMessage): String = when (message.key) {
    FilesMessageKey.IMPORT_FAILED -> stringResource(R.string.files_error_import)
    FilesMessageKey.CLIPBOARD_IMPORT_FAILED -> stringResource(R.string.files_error_clipboard_import)
    FilesMessageKey.CREATE_FOLDER_FAILED -> stringResource(R.string.files_error_create_folder)
    FilesMessageKey.RENAME_FAILED -> stringResource(R.string.files_error_rename)
    FilesMessageKey.MOVE_FAILED -> stringResource(R.string.files_error_move)
    FilesMessageKey.TRASH_FAILED -> stringResource(R.string.files_error_trash)
    FilesMessageKey.FAVORITE_FAILED -> stringResource(R.string.files_error_favorite)
    FilesMessageKey.REFRESH_FAILED -> stringResource(R.string.files_error_refresh)
    FilesMessageKey.IMPORTED_ONE -> stringResource(R.string.files_notice_imported_one, message.value.orEmpty())
    FilesMessageKey.IMPORTED_MANY -> stringResource(R.string.files_notice_imported_many, message.count ?: 0)
    FilesMessageKey.CLIPBOARD_IMPORTED -> stringResource(R.string.files_notice_clipboard_imported, message.value.orEmpty())
    FilesMessageKey.FOLDER_CREATED -> stringResource(R.string.files_notice_folder_created)
    FilesMessageKey.RENAMED -> stringResource(R.string.files_notice_renamed, message.value.orEmpty())
    FilesMessageKey.MOVED -> stringResource(R.string.files_notice_moved, message.value.orEmpty())
    FilesMessageKey.TRASHED -> stringResource(R.string.files_notice_trashed, message.value.orEmpty())
    FilesMessageKey.REFRESHED -> stringResource(R.string.files_notice_refreshed)
    FilesMessageKey.NAME_EMPTY -> stringResource(R.string.files_error_name_empty)
    FilesMessageKey.NAME_DOT -> stringResource(R.string.files_error_name_dot)
    FilesMessageKey.NAME_SEPARATOR -> stringResource(R.string.files_error_name_separator)
    FilesMessageKey.NAME_DOUBLE_DOT -> stringResource(R.string.files_error_name_double_dot)
    FilesMessageKey.NAME_CONTROL -> stringResource(R.string.files_error_name_control)
    FilesMessageKey.NAME_RESERVED -> stringResource(R.string.files_error_name_reserved)
    FilesMessageKey.NAME_TOO_LONG -> stringResource(R.string.files_error_name_too_long)
    FilesMessageKey.CLIPBOARD_EMPTY -> stringResource(R.string.files_error_clipboard_empty)
    FilesMessageKey.NAME_CONFLICT -> stringResource(R.string.files_error_name_conflict, message.value.orEmpty())
    FilesMessageKey.FILE_NOT_FOUND -> stringResource(R.string.files_error_file_not_found)
    FilesMessageKey.DESTINATION_NOT_FOLDER -> stringResource(R.string.files_error_destination_not_folder)
    FilesMessageKey.MOVE_INTO_SELF -> stringResource(R.string.files_error_move_into_self)
    FilesMessageKey.MOVE_INTO_DESCENDANT -> stringResource(R.string.files_error_move_into_descendant)
    FilesMessageKey.FOLDER_CANNOT_BE_FAVORITE -> stringResource(R.string.files_error_folder_favorite)
    FilesMessageKey.NOT_MANAGED -> stringResource(R.string.files_error_not_managed)
    FilesMessageKey.FILE_MISSING -> stringResource(R.string.files_error_file_missing, message.value.orEmpty())
}


private fun toolActionLabel(action: FileAction): Int = when (action) {
    FileAction.EDIT_PDF -> R.string.file_action_edit_pdf
    FileAction.EDIT_IMAGE -> R.string.file_action_edit_image
    FileAction.EXTRACT_TEXT -> R.string.file_action_extract_text
    FileAction.CONVERT -> R.string.file_action_convert
    FileAction.COMPRESS -> R.string.file_action_compress
}

private fun toolActionIcon(action: FileAction): ImageVector = when (action) {
    FileAction.EDIT_PDF -> Icons.Rounded.PictureAsPdf
    FileAction.EDIT_IMAGE -> Icons.Rounded.Tune
    FileAction.EXTRACT_TEXT -> Icons.Rounded.TextSnippet
    FileAction.CONVERT -> Icons.Rounded.SwapHoriz
    FileAction.COMPRESS -> Icons.Rounded.Compress
}
