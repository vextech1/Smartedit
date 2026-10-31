package com.vextech.smart_edit.feature.files

import android.content.ClipboardManager
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.FileAction
import com.vextech.smart_edit.core.library.FileActionPolicies
import com.vextech.smart_edit.core.library.FileKind
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.ConfirmDialog
import com.vextech.smart_edit.core.ui.EmptyState
import com.vextech.smart_edit.core.ui.ImageScrim
import com.vextech.smart_edit.core.ui.UriThumbnail
import com.vextech.smart_edit.core.ui.formatBytes
import com.vextech.smart_edit.feature.files.domain.FileItem
import com.vextech.smart_edit.feature.files.domain.FileSort
import com.vextech.smart_edit.feature.files.domain.FileTreePolicy
import com.vextech.smart_edit.feature.files.domain.FileViewMode
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var addSheet by remember { mutableStateOf(false) }
    var itemSheet by remember { mutableStateOf<FileItem?>(null) }
    var renameItem by remember { mutableStateOf<FileItem?>(null) }
    var moveItems by remember { mutableStateOf<List<FileItem>?>(null) }
    var deleteItems by remember { mutableStateOf<List<FileItem>?>(null) }
    var detailsItem by remember { mutableStateOf<FileItem?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    val selected = state.items.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty()
    // Leaving a folder (or an item disappearing) must never leave a stale selection behind.
    LaunchedEffect(state.currentFolderId) { selectedIds = emptySet() }
    LaunchedEffect(state.items) { selectedIds = selectedIds.filterTo(mutableSetOf()) { id -> state.items.any { it.id == id } } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importDocuments(uris)
    }
    val childCounts = remember(state.allItems) { state.allItems.groupingBy { it.parentId }.eachCount() }

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

    fun shareItem(item: FileItem) {
        runCatching { share.shareManaged(item.uri, item.mimeType) }
            .onFailure { scope.launch { snackbar.showSnackbar(context.getString(R.string.files_share_failed)) } }
    }

    fun requestTrash(items: List<FileItem>) {
        if (state.confirmDeleteEnabled) deleteItems = items
        else if (items.size == 1) vm.trash(items.first()) else vm.trashMany(items)
    }

    fun toggleSelected(item: FileItem) {
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
    }

    BackHandler(enabled = selecting || state.canGoUp || onBack != null) {
        if (selecting) selectedIds = emptySet() else navigateBack()
    }

    val messageText = state.message?.let { filesMessageText(it) }
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.clearMessage()
    }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(stringResource(R.string.files_selected_count, selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) { Icon(Icons.Rounded.Close, stringResource(R.string.files_cancel_selection)) }
                    },
                    actions = {
                        TextButton(onClick = { selectedIds = state.items.map { it.id }.toSet() }) { Text(stringResource(R.string.files_select_all)) }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(if (state.currentFolderId == null) stringResource(R.string.files) else state.folderName) },
                    navigationIcon = {
                        if (state.canGoUp || onBack != null) {
                            IconButton(onClick = ::navigateBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        if (onSearch != null) {
                            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
                        }
                        IconButton(onClick = vm::toggleViewMode) {
                            Icon(
                                imageVector = if (state.viewMode == FileViewMode.LIST) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                                contentDescription = stringResource(R.string.files_toggle_view),
                            )
                        }
                        // Trash lives here because it is a place, not a file action; refresh is now pull-to-refresh.
                        if (onTrash != null) {
                            IconButton(onClick = onTrash) { Icon(Icons.Rounded.Delete, stringResource(R.string.trash)) }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(onClick = { addSheet = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.files_add_title)) }
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionBar(
                    canShare = selected.size == 1 && !selected.first().isDirectory,
                    allFavorite = selected.filter { !it.isDirectory }.let { files -> files.isNotEmpty() && files.all { it.isFavorite } },
                    hasFiles = selected.any { !it.isDirectory },
                    onShare = { shareItem(selected.first()) },
                    onMove = { moveItems = selected },
                    onFavorite = {
                        val makeFavorite = !selected.filter { !it.isDirectory }.all { it.isFavorite }
                        vm.setFavorite(selected, makeFavorite)
                    },
                    onTrash = { requestTrash(selected) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            header?.invoke()
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Breadcrumbs(state = state, onNavigate = vm::navigateToBreadcrumb)
            SortRow(
                count = state.items.size, sort = state.sort, ascending = state.ascending,
                menuOpen = sortMenu, onMenu = { sortMenu = it }, onSort = vm::setSort,
            )
            PullToRefreshBox(isRefreshing = state.isBusy, onRefresh = vm::refresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.items.isEmpty() && !state.isBusy) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        EmptyState(
                            title = stringResource(R.string.files_empty_title),
                            description = stringResource(R.string.files_empty_description),
                        )
                    }
                } else if (state.viewMode == FileViewMode.LIST) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.items, key = FileItem::id) { item ->
                            FileRow(
                                item = item,
                                folderChildren = childCounts[item.id] ?: 0,
                                selecting = selecting,
                                selected = item.id in selectedIds,
                                onClick = { if (selecting) toggleSelected(item) else open(item) },
                                onLongClick = { toggleSelected(item) },
                                onFavorite = { vm.toggleFavorite(item) },
                                onMore = { itemSheet = item },
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(state.items, key = FileItem::id) { item ->
                            FileCard(
                                item = item,
                                folderChildren = childCounts[item.id] ?: 0,
                                selecting = selecting,
                                selected = item.id in selectedIds,
                                onClick = { if (selecting) toggleSelected(item) else open(item) },
                                onLongClick = { toggleSelected(item) },
                                onFavorite = { vm.toggleFavorite(item) },
                                onMore = { itemSheet = item },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── sheets and dialogs ──

    if (addSheet) {
        ModalBottomSheet(onDismissRequest = { addSheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.files_add_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                SheetAction(Icons.Rounded.CreateNewFolder, stringResource(R.string.files_new_folder)) { addSheet = false; createFolder = true }
                SheetAction(Icons.Rounded.UploadFile, stringResource(R.string.files_import)) { addSheet = false; picker.launch(arrayOf("*/*")) }
                SheetAction(Icons.Rounded.ContentPaste, stringResource(R.string.files_import_clipboard)) {
                    addSheet = false
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    vm.importClipboard(text)
                }
            }
        }
    }

    itemSheet?.let { item ->
        val toolActions = remember(item.name, item.mimeType, item.isDirectory) { FileActionPolicies.actionsFor(item.name, item.mimeType, item.isDirectory) }
        ModalBottomSheet(onDismissRequest = { itemSheet = null }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                val kindLabel = kindLabel(item)
                Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FileLead(item)
                    Column {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (item.isDirectory) pluralStringResource(R.plurals.files_folder_items, childCounts[item.id] ?: 0, childCounts[item.id] ?: 0)
                            else stringResource(R.string.files_row_detail, kindLabel, formatBytes(item.sizeBytes), shortDate(item.modifiedAt)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (toolActions.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        toolActions.forEach { action ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { itemSheet = null; onToolAction?.invoke(action, item.uri) }.padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(toolActionIcon(action), null, tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(toolActionLabel(action)), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
                if (!item.isDirectory) SheetAction(Icons.Rounded.Share, stringResource(R.string.share)) { itemSheet = null; shareItem(item) }
                SheetAction(Icons.Rounded.DriveFileRenameOutline, stringResource(R.string.rename)) { itemSheet = null; renameItem = item }
                SheetAction(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.files_move)) { itemSheet = null; moveItems = listOf(item) }
                if (!item.isDirectory) {
                    SheetAction(
                        if (item.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        stringResource(if (item.isFavorite) R.string.files_remove_favorite else R.string.files_add_favorite),
                    ) { itemSheet = null; vm.toggleFavorite(item) }
                }
                SheetAction(Icons.Rounded.Info, stringResource(R.string.details)) { itemSheet = null; detailsItem = item }
                SheetAction(Icons.Rounded.Delete, stringResource(R.string.files_move_to_trash), danger = true) { itemSheet = null; requestTrash(listOf(item)) }
            }
        }
    }

    if (createFolder) {
        NameDialog(
            title = stringResource(R.string.files_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { createFolder = false },
            onConfirm = { vm.createFolder(it); createFolder = false },
        )
    }

    renameItem?.let { item ->
        NameDialog(
            title = stringResource(R.string.rename),
            initial = item.name,
            confirmLabel = stringResource(R.string.rename),
            onDismiss = { renameItem = null },
            onConfirm = { vm.rename(item, it); renameItem = null },
        )
    }

    moveItems?.let { items ->
        MoveSheet(
            items = items,
            state = state,
            onDismiss = { moveItems = null },
            onMove = { destinationId ->
                if (items.size == 1) vm.move(items.first(), destinationId) else vm.moveMany(items, destinationId)
                moveItems = null
                selectedIds = emptySet()
            },
        )
    }

    deleteItems?.let { items ->
        ConfirmDialog(
            title = stringResource(R.string.files_move_to_trash_title),
            message = if (items.size == 1) stringResource(R.string.files_move_to_trash_message, items.first().name)
            else stringResource(R.string.files_move_to_trash_many_message, items.size),
            confirmLabel = stringResource(R.string.files_move_to_trash),
            onConfirm = {
                if (items.size == 1) vm.trash(items.first()) else vm.trashMany(items)
                deleteItems = null
                selectedIds = emptySet()
            },
            onDismiss = { deleteItems = null },
        )
    }

    detailsItem?.let { item ->
        DetailsDialog(item = item, state = state, onDismiss = { detailsItem = null })
    }
}

// ───────────────────────────── Browsing pieces ─────────────────────────────

@Composable
private fun Breadcrumbs(state: FilesUiState, onNavigate: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        state.breadcrumbs.forEachIndexed { index, crumb ->
            val last = index == state.breadcrumbs.lastIndex
            Surface(
                onClick = { onNavigate(crumb.id) }, enabled = !last, shape = CircleShape,
                color = if (last) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = if (crumb.id == null) stringResource(R.string.files) else crumb.name,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (last) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (!last) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SortRow(count: Int, sort: FileSort, ascending: Boolean, menuOpen: Boolean, onMenu: (Boolean) -> Unit, onSort: (FileSort) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            pluralStringResource(R.plurals.files_items_count, count, count),
            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            AssistChip(
                onClick = { onMenu(true) },
                label = { Text(sortLabel(sort) + if (ascending) " ↑" else " ↓") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                FileSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(option) + if (sort == option) (if (ascending) " ↑" else " ↓") else "") },
                        onClick = { onSort(option); onMenu(false) },
                    )
                }
            }
        }
    }
}

private fun kindOf(item: FileItem): FileKind = FileActionPolicies.kindOf(item.name, item.mimeType)

private fun iconFor(item: FileItem): ImageVector {
    val mime = item.mimeType.lowercase()
    return when {
        item.isDirectory -> Icons.Rounded.Folder
        kindOf(item) == FileKind.PDF -> Icons.Rounded.PictureAsPdf
        mime.startsWith("image/") -> Icons.Rounded.Image
        item.extension == "zip" || mime.contains("zip") -> Icons.Rounded.FolderZip
        mime.startsWith("text/") -> Icons.AutoMirrored.Rounded.TextSnippet
        mime.startsWith("audio/") -> Icons.Rounded.AudioFile
        mime.startsWith("video/") -> Icons.Rounded.VideoFile
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
    }
}

@Composable
private fun kindLabel(item: FileItem): String {
    val mime = item.mimeType.lowercase()
    return when {
        kindOf(item) == FileKind.PDF -> "PDF"
        item.extension == "zip" || mime.contains("zip") -> "ZIP"
        mime.startsWith("image/") -> stringResource(R.string.files_kind_image)
        mime.startsWith("text/") -> stringResource(R.string.files_kind_text)
        mime.startsWith("audio/") -> stringResource(R.string.files_kind_audio)
        mime.startsWith("video/") -> stringResource(R.string.files_kind_video)
        item.extension in setOf("doc", "docx", "odt", "rtf", "xls", "xlsx", "ppt", "pptx") -> stringResource(R.string.files_kind_document)
        else -> item.extension.uppercase().ifEmpty { stringResource(R.string.files_kind_file) }
    }
}

@Composable
private fun shortDate(millis: Long): String {
    val context = LocalContext.current
    return DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
}

/** Photos show themselves; everything else shows an icon in a rounded tile. */
@Composable
private fun FileLead(item: FileItem, size: androidx.compose.ui.unit.Dp = 44.dp) {
    if (!item.isDirectory && kindOf(item) == FileKind.IMAGE) {
        UriThumbnail(item.uri, Modifier.size(size).clip(RoundedCornerShape(12.dp)), maxDim = 160)
    } else {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center) { Icon(iconFor(item), null, tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun SelectCircle(selected: Boolean) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileItem, folderChildren: Int, selecting: Boolean, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onFavorite: () -> Unit, onMore: () -> Unit,
) {
    val detail = if (item.isDirectory) pluralStringResource(R.plurals.files_folder_items, folderChildren, folderChildren)
    else stringResource(R.string.files_row_detail, kindLabel(item), formatBytes(item.sizeBytes), shortDate(item.modifiedAt))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = if (selecting) 12.dp else 0.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selecting) SelectCircle(selected)
            FileLead(item)
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!selecting) {
                if (!item.isDirectory) {
                    IconButton(onClick = onFavorite) {
                        Icon(
                            if (item.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            stringResource(R.string.files_favorite),
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileCard(
    item: FileItem, folderChildren: Int, selecting: Boolean, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onFavorite: () -> Unit, onMore: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
            Box(Modifier.fillMaxWidth().height(104.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                if (!item.isDirectory && kindOf(item) == FileKind.IMAGE) {
                    UriThumbnail(item.uri, Modifier.fillMaxSize(), maxDim = 320)
                } else {
                    Icon(iconFor(item), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (selecting) {
                    Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { SelectCircle(selected) }
                }
            }
            Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (item.isDirectory) pluralStringResource(R.plurals.files_items_count, folderChildren, folderChildren) else formatBytes(item.sizeBytes),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!selecting) {
                    if (!item.isDirectory) {
                        IconButton(onClick = onFavorite) {
                            Icon(
                                if (item.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, stringResource(R.string.files_favorite),
                                tint = if (item.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    canShare: Boolean, allFavorite: Boolean, hasFiles: Boolean,
    onShare: () -> Unit, onMove: () -> Unit, onFavorite: () -> Unit, onTrash: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            BarAction(Icons.Rounded.Share, R.string.share, canShare, onClick = onShare)
            BarAction(Icons.AutoMirrored.Rounded.DriveFileMove, R.string.files_move, true, onClick = onMove)
            BarAction(if (allFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, R.string.files_favorite, hasFiles, onClick = onFavorite)
            BarAction(Icons.Rounded.Delete, R.string.files_move_to_trash_short, true, MaterialTheme.colorScheme.error, onTrash)
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: Int, enabled: Boolean, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    val color = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).defaultMinSize(minWidth = 64.dp, minHeight = 56.dp).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else color, maxLines = 1)
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

// ───────────────────────────── Move ─────────────────────────────

/** One or several items: pick a folder, then "Move here". Folders an item cannot go into are never offered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(items: List<FileItem>, state: FilesUiState, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
    val rootLabel = stringResource(R.string.files)
    val targets = remember(items, state.allItems) {
        items.map { item -> state.moveTargets(item.id).map { it.id }.toSet() }.reduce { a, b -> a intersect b }
            .let { allowed -> state.allItems.filter { it.isDirectory && it.id in allowed }.sortedBy { it.name.lowercase() } }
    }
    val commonParent = items.map { it.parentId }.distinct().singleOrNull()
    var chosen by remember { mutableStateOf<Choice?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                if (items.size == 1) stringResource(R.string.files_move_title, items.first().name) else stringResource(R.string.files_move_many_title, items.size),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 360.dp)) {
                if (commonParent != null) {
                    item(key = "root") { DestinationRow(stringResource(R.string.files_root), rootLabel, 0, chosen == Choice(null)) { chosen = Choice(null) } }
                }
                items(targets.filter { it.id != commonParent }, key = FileItem::id) { folder ->
                    val crumbs = FileTreePolicy.breadcrumbs(state.allItems, folder.id)
                    val path = crumbs.joinToString(" / ") { if (it.id == null) rootLabel else it.name }
                    DestinationRow(folder.name, path, (crumbs.size - 2).coerceAtLeast(0), chosen == Choice(folder.id)) { chosen = Choice(folder.id) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { chosen?.let { onMove(it.folderId) } }, enabled = chosen != null, modifier = Modifier.weight(1.4f).height(52.dp)) {
                    Text(stringResource(R.string.files_move_here))
                }
            }
        }
    }
}

private data class Choice(val folderId: String?)

@Composable
private fun DestinationRow(title: String, path: String, depth: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).heightIn(min = 52.dp).padding(start = (12 + depth * 20).dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (path != title) Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

// ───────────────────────────── Kept from the old screen ─────────────────────────────

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
    FilesMessageKey.MOVED_MANY -> stringResource(R.string.files_notice_moved_many, message.count ?: 0)
    FilesMessageKey.TRASHED_MANY -> stringResource(R.string.files_notice_trashed_many, message.count ?: 0)
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
    FileAction.EXTRACT_TEXT -> Icons.AutoMirrored.Rounded.TextSnippet
    FileAction.CONVERT -> Icons.Rounded.SwapHoriz
    FileAction.COMPRESS -> Icons.Rounded.Compress
}
