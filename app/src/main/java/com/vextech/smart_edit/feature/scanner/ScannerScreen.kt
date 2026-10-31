package com.vextech.smart_edit.feature.scanner

import android.app.Activity
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.mlkit.vision.documentscanner.*
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.database.dao.ScanCover
import com.vextech.smart_edit.core.database.entity.ScanSessionEntity
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.ImageScrim
import com.vextech.smart_edit.core.ui.UiText
import com.vextech.smart_edit.core.ui.rememberSaveToDevice
import com.vextech.smart_edit.feature.scanner.domain.*

@Composable
fun ScannerScreen(onBack: () -> Unit, onLiveCamera: (String) -> Unit, share: ShareService, vm: ScannerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    var edit by remember { mutableStateOf<ScanPageDraft?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf<UiText?>(null) }
    var ocrSheetFor by remember { mutableStateOf<String?>(null) }
    var cameraPending by rememberSaveable { mutableStateOf(false) }
    fun openCamera(id: String) { cameraPending = true; onLiveCamera(id) }
    // Entering the scanner shows the chooser (Live camera / Import / Auto). The camera opens only when the user picks it;
    // on return, a session with no captured pages is cleaned up.
    LaunchedEffect(Unit) {
        if (cameraPending) { cameraPending = false; vm.afterCamera() }
    }
    val saveToDevice = rememberSaveToDevice(share) { localMessage = it }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.importUris(it) }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.let(vm::importScanResult)
    }
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(50)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        )
    }
    fun autoScan() { scanner.getStartScanIntent(activity).addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }.addOnFailureListener { vm.error() } }
    fun importImages() = gallery.launch(arrayOf("image/*"))

    val msg = state.message?.let { scannerMessage(it) } ?: localMessage?.asString()
    LaunchedEffect(msg) { if (msg != null) { kotlinx.coroutines.delay(2500); vm.clearMessage(); localMessage = null } }

    val session = state.session
    val editing = edit
    BackHandler(enabled = session != null && editing == null) { vm.closeSession() }

    if (editing != null) {
        ScanPageEditor(editing, state.session?.pages?.size ?: 1, { done -> vm.detectOrientation(editing.id, done) }, { edit = null }) { crop, filter, rotation, all -> vm.editPage(editing.id, crop, filter, rotation, all); edit = null }
    } else {
        FeatureScaffold(
            title = session?.title ?: stringResource(R.string.scanner_title),
            onBack = { if (session != null) vm.closeSession() else onBack() },
            actions = {
                if (session != null) {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, stringResource(R.string.scanner_rename)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.scanner_more_options)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scanner_discard)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; confirmDiscard = true },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                msg?.let {
                    Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                if (state.undoAvailable) {
                    Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.scanner_page_deleted), Modifier.weight(1f), color = MaterialTheme.colorScheme.inverseOnSurface)
                            TextButton(onClick = vm::undoDelete) { Text(stringResource(R.string.scanner_undo), color = MaterialTheme.colorScheme.inversePrimary) }
                        }
                    }
                }
                if (session == null) {
                    ScannerHome(
                        recent = state.recentSessions, covers = state.covers, busy = state.busy,
                        onNew = { vm.newSession(::openCamera) },
                        onCamera = { vm.newSession(::openCamera) },
                        onImport = ::importImages, onAuto = ::autoScan,
                        onOpen = { selectedId = null; vm.openSession(it) },
                    )
                } else {
                    val selected = session.pages.firstOrNull { it.id == selectedId } ?: session.pages.firstOrNull()
                    ScannerSession(
                        session = session, selected = selected, busy = state.busy,
                        onSelect = { selectedId = it.id },
                        onMove = { from, to -> vm.movePage(from, to) },
                        onEdit = { edit = it }, onOcr = { page -> if (page.ocrText != null) ocrSheetFor = page.id else vm.ocr(page.id) { ok -> if (ok) ocrSheetFor = page.id } }, onDelete = { vm.deletePage(it.id) },
                        onDeleteMany = vm::deletePages, onFilterMany = vm::applyFilterToPages,
                        onCamera = { openCamera(session.id) }, onImport = ::importImages, onAuto = ::autoScan,
                        onExport = vm::export,
                        onOpenPdf = { uri -> runCatching { share.openManaged(uri, "application/pdf") }.onFailure { localMessage = UiText.res(R.string.common_open_failed) } },
                        onSharePdf = { uri -> runCatching { share.shareManaged(uri, "application/pdf") }.onFailure { localMessage = UiText.res(R.string.common_share_failed) } },
                        onSavePdf = { uri -> saveToDevice.save(uri, android.net.Uri.parse(uri).lastPathSegment ?: "scan.pdf", "application/pdf") },
                    )
                }
            }
        }
    }

    val sheetPage = session?.pages?.firstOrNull { it.id == ocrSheetFor }
    if (sheetPage != null) {
        OcrTextSheet(sheetPage, state.busy, onRerun = { script -> vm.ocr(sheetPage.id, script) }, onDismiss = { ocrSheetFor = null })
    }

    if (renaming && session != null) {
        RenameDialog(session.title, { renaming = false }) { renaming = false; vm.rename(it) }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.scanner_discard)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.discard() }) { Text(stringResource(R.string.scanner_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

// ───────────────────────────── Home ─────────────────────────────

@Composable
private fun ScannerHome(
    recent: List<ScanSessionEntity>, covers: Map<String, ScanCover>, busy: Boolean,
    onNew: () -> Unit, onCamera: () -> Unit, onImport: () -> Unit, onAuto: () -> Unit, onOpen: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Button(onClick = onNew, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.scanner_new_session))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(Icons.Rounded.PhotoCamera, stringResource(R.string.scanner_live_camera), !busy, onCamera, Modifier.weight(1f))
                ActionTile(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.scanner_import), !busy, onImport, Modifier.weight(1f))
                ActionTile(Icons.Rounded.DocumentScanner, stringResource(R.string.scanner_auto), !busy, onAuto, Modifier.weight(1f))
            }
        }
        if (recent.isEmpty()) {
            item { Text(stringResource(R.string.scanner_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item { Text(stringResource(R.string.scanner_recent_scans), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(recent.take(20), key = { it.id }) { RecentScanCard(it, covers[it.id]) { onOpen(it.id) } }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 4.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun RecentScanCard(session: ScanSessionEntity, cover: ScanCover?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (cover != null) {
                ScanThumbnail(cover.renderedUri, Modifier.size(56.dp, 72.dp).clip(RoundedCornerShape(8.dp)), version = cover.updatedAt, maxDim = 256)
            } else {
                Box(Modifier.size(56.dp, 72.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val whenText = DateUtils.getRelativeTimeSpanString(session.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                Text(
                    if (cover != null) stringResource(R.string.scanner_session_summary, stringResource(R.string.scanner_pages_count, cover.pageCount), whenText) else whenText,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusChip(session.status == ScanStatus.EXPORTED.name)
            }
        }
    }
}

@Composable
private fun StatusChip(exported: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (exported) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            stringResource(if (exported) R.string.scanner_status_exported else R.string.scanner_status_draft),
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
            color = if (exported) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

// ───────────────────────────── Session ─────────────────────────────

@Composable
private fun ColumnScope.ScannerSession(
    session: ScanSessionDraft, selected: ScanPageDraft?, busy: Boolean,
    onSelect: (ScanPageDraft) -> Unit, onMove: (Int, Int) -> Unit,
    onEdit: (ScanPageDraft) -> Unit, onOcr: (ScanPageDraft) -> Unit, onDelete: (ScanPageDraft) -> Unit,
    onDeleteMany: (Set<String>) -> Unit, onFilterMany: (Set<String>, ScanFilter) -> Unit,
    onCamera: () -> Unit, onImport: () -> Unit, onAuto: () -> Unit, onExport: () -> Unit,
    onOpenPdf: (String) -> Unit, onSharePdf: (String) -> Unit, onSavePdf: (String) -> Unit,
) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(session.pages) {
        chosen = chosen.filter { id -> session.pages.any { it.id == id } }.toSet()   // pages may vanish (delete, undo timer)
        if (session.pages.isEmpty()) selecting = false
    }
    BackHandler(enabled = selecting) { selecting = false; chosen = emptySet() }

    val status = stringResource(if (session.status == ScanStatus.EXPORTED) R.string.scanner_status_exported else R.string.scanner_status_draft)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (selecting) stringResource(R.string.scanner_selected_count, chosen.size)
            else stringResource(R.string.scanner_session_summary, stringResource(R.string.scanner_pages_count, session.pages.size), status),
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (session.pages.isNotEmpty()) {
            TextButton(onClick = { selecting = !selecting; chosen = emptySet() }) {
                Text(stringResource(if (selecting) R.string.scanner_select_done else R.string.scanner_select))
            }
        }
    }
    session.exportedPdfUri?.takeIf { session.status == ScanStatus.EXPORTED }?.let { uri ->
        ExportedPdfCard(
            name = android.net.Uri.parse(uri).lastPathSegment ?: uri,
            onOpen = { onOpenPdf(uri) }, onShare = { onSharePdf(uri) }, onSave = { onSavePdf(uri) },
        )
    }
    if (session.pages.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.scanner_empty_session), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val gridState = rememberLazyGridState()
        val currentPages by rememberUpdatedState(session.pages)
        val currentOnMove by rememberUpdatedState(onMove)
        var dragId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }

        /** Grid index of the tile under the centre of the dragged tile, or null. */
        fun dropIndex(): Int? {
            val id = dragId ?: return null
            val tiles = gridState.layoutInfo.visibleItemsInfo
            val mine = tiles.firstOrNull { it.key == id } ?: return null
            val cx = mine.offset.x + mine.size.width / 2f + dragOffset.x
            val cy = mine.offset.y + mine.size.height / 2f + dragOffset.y
            return tiles.firstOrNull { cx >= it.offset.x && cx < it.offset.x + it.size.width && cy >= it.offset.y && cy < it.offset.y + it.size.height }?.index
        }

        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.weight(1f).fillMaxWidth(), state = gridState,
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(session.pages, key = { it.id }) { page ->
                val index = session.pages.indexOf(page)
                val dragging = dragId == page.id
                val dragModifier = if (!selecting && !busy) {
                    Modifier.pointerInput(page.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragId = page.id; dragOffset = Offset.Zero },
                            onDrag = { change, amount -> change.consume(); dragOffset += amount },
                            onDragEnd = {
                                val to = dropIndex()
                                val from = currentPages.indexOfFirst { it.id == page.id }
                                dragId = null; dragOffset = Offset.Zero
                                if (to != null && from >= 0 && to != from) currentOnMove(from, to)
                            },
                            onDragCancel = { dragId = null; dragOffset = Offset.Zero },
                        )
                    }
                } else Modifier
                PageTile(
                    number = index + 1, page = page, selected = !selecting && page.id == selected?.id,
                    checked = if (selecting) page.id in chosen else null,
                    hovered = dragId != null && !dragging && dropIndex() == index,
                    canLeft = index > 0, canRight = index < session.pages.lastIndex,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { if (dragging) { translationX = dragOffset.x; translationY = dragOffset.y; scaleX = 1.05f; scaleY = 1.05f; alpha = 0.92f } }
                        .then(dragModifier),
                    onClick = { if (selecting) chosen = if (page.id in chosen) chosen - page.id else chosen + page.id else onSelect(page) },
                    onLeft = { onMove(index, index - 1) }, onRight = { onMove(index, index + 1) },
                    onOcrClick = { onSelect(page); onOcr(page) },
                )
            }
        }
    }
    if (selecting) {
        SelectionBar(
            count = chosen.size, total = session.pages.size, busy = busy,
            onSelectAll = { chosen = session.pages.map { it.id }.toSet() },
            onFilter = { filter -> onFilterMany(chosen, filter) },
            onDelete = { onDeleteMany(chosen); chosen = emptySet(); selecting = false },
        )
    } else {
        SessionBar(
            enabledPageActions = selected != null && !busy, canExport = session.pages.isNotEmpty() && !busy,
            onEdit = { selected?.let(onEdit) }, onOcr = { selected?.let(onOcr) }, onDelete = { selected?.let(onDelete) },
            onCamera = onCamera, onImport = onImport, onAuto = onAuto, onExport = onExport,
        )
    }
}

/** Long-press a tile and drag to reorder. The arrows are gone, but screen readers still get "move left / right" actions. */
@Composable
private fun PageTile(
    number: Int, page: ScanPageDraft, selected: Boolean, checked: Boolean?, hovered: Boolean, canLeft: Boolean, canRight: Boolean,
    modifier: Modifier, onClick: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onOcrClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val leftLabel = stringResource(R.string.scanner_move_left)
    val rightLabel = stringResource(R.string.scanner_move_right)
    val borderColor = when {
        hovered -> MaterialTheme.colorScheme.tertiary
        selected || checked == true -> MaterialTheme.colorScheme.primary
        else -> null
    }
    Box(
        modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(shape)
            .then(if (borderColor != null) Modifier.border(if (hovered) 3.dp else 2.dp, borderColor, shape) else Modifier)
            .semantics {
                customActions = listOfNotNull(
                    if (canLeft) CustomAccessibilityAction(leftLabel) { onLeft(); true } else null,
                    if (canRight) CustomAccessibilityAction(rightLabel) { onRight(); true } else null,
                )
            }
            .clickable(onClick = onClick),
    ) {
        ScanThumbnail(page.renderedUri, Modifier.fillMaxSize(), version = page.updatedAt)
        Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = CircleShape, color = ImageScrim) {
            Text("$number", Modifier.padding(horizontal = 9.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        if (checked != null) {
            Surface(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp), shape = CircleShape,
                color = if (checked) MaterialTheme.colorScheme.primary else ImageScrim,
                border = if (checked) null else androidx.compose.foundation.BorderStroke(2.dp, Color.White),
            ) { if (checked) Icon(Icons.Rounded.Check, null, Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.onPrimary) }
        }
        if (!page.ocrText.isNullOrBlank()) {
            Surface(onClick = onOcrClick, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), shape = RoundedCornerShape(8.dp), color = ImageScrim) {
                Text(stringResource(R.string.scanner_ocr_done), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, total: Int, busy: Boolean, onSelectAll: () -> Unit, onFilter: (ScanFilter) -> Unit, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
            BarAction(Icons.Rounded.SelectAll, stringResource(R.string.scanner_select_all), !busy && count < total, onClick = onSelectAll)
            Box {
                var menu by remember { mutableStateOf(false) }
                BarAction(Icons.Rounded.Tune, stringResource(R.string.scanner_apply_filter), !busy && count > 0, onClick = { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    ScanFilter.entries.forEach { filter ->
                        DropdownMenuItem(text = { Text(stringResource(filterLabel(filter))) }, onClick = { menu = false; onFilter(filter) })
                    }
                }
            }
            BarAction(Icons.Rounded.Delete, stringResource(R.string.scanner_delete_short), !busy && count > 0, MaterialTheme.colorScheme.error, onDelete)
        }
    }
}

private fun filterLabel(filter: ScanFilter) = when (filter) {
    ScanFilter.ORIGINAL -> R.string.scanner_filter_original
    ScanFilter.GRAYSCALE -> R.string.scanner_filter_grayscale
    ScanFilter.BLACK_WHITE -> R.string.scanner_filter_black_white
    ScanFilter.ENHANCE -> R.string.scanner_filter_enhance
}

@Composable
private fun SessionBar(
    enabledPageActions: Boolean, canExport: Boolean,
    onEdit: () -> Unit, onOcr: () -> Unit, onDelete: () -> Unit,
    onCamera: () -> Unit, onImport: () -> Unit, onAuto: () -> Unit, onExport: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                BarAction(Icons.Rounded.Edit, stringResource(R.string.scanner_edit), enabledPageActions, onClick = onEdit)
                BarAction(Icons.Rounded.TextFields, stringResource(R.string.scanner_run_ocr), enabledPageActions, onClick = onOcr)
                Box {
                    var menu by remember { mutableStateOf(false) }
                    BarAction(Icons.Rounded.Add, stringResource(R.string.scanner_add_page), true, onClick = { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.scanner_live_camera)) }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = { menu = false; onCamera() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.scanner_import)) }, leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, null) }, onClick = { menu = false; onImport() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.scanner_auto)) }, leadingIcon = { Icon(Icons.Rounded.DocumentScanner, null) }, onClick = { menu = false; onAuto() })
                    }
                }
                BarAction(Icons.Rounded.Delete, stringResource(R.string.scanner_delete_short), enabledPageActions, MaterialTheme.colorScheme.error, onDelete)
            }
            Button(onClick = onExport, enabled = canExport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.scanner_export_pdf))
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, enabled: Boolean, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    val color = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column(
        Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, null, tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else color)
    }
}

@Composable
private fun scannerMessage(m: ScannerMessage) = stringResource(
    when (m) {
        ScannerMessage.SESSION_FAILED -> R.string.scanner_error_session
        ScannerMessage.IMPORT_FAILED -> R.string.scanner_error_import
        ScannerMessage.EDIT_FAILED -> R.string.scanner_error_edit
        ScannerMessage.OCR_FAILED -> R.string.scanner_error_ocr
        ScannerMessage.EXPORT_FAILED -> R.string.scanner_error_export
        ScannerMessage.PAGE_DELETED -> R.string.scanner_page_deleted
        ScannerMessage.EXPORTED -> R.string.scanner_exported
    }
)

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scanner_rename)) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                label = { Text(stringResource(R.string.scanner_rename_label)) },
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text(stringResource(R.string.scanner_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun ExportedPdfCard(name: String, onOpen: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    Surface(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.PictureAsPdf, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.scanner_pdf_ready), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen, Modifier.weight(1f)) { Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.scanner_open_pdf)) }
                OutlinedButton(onClick = onShare, Modifier.weight(1f)) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.scanner_share_pdf)) }
                IconButton(onClick = onSave) { Icon(Icons.Rounded.Download, stringResource(R.string.scanner_save_pdf), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
    }
}
