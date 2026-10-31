package com.vextech.smart_edit.feature.pdf

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ads.ExportInterstitialEffect
import com.vextech.smart_edit.core.ads.UnityAdsService
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.feature.pdf.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import android.text.format.DateUtils
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.TileRow
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import java.io.File

enum class PdfCanvasTool { NONE, CROP, HIGHLIGHT, UNDERLINE, STRIKEOUT, TEXT, NOTE, LINK, RECTANGLE, INK, SIGNATURE, REDACT }
private enum class PdfDialog { SAVE_AS, WATERMARK, SEARCH, TEXT, NOTE, LINK, REDACT_CONFIRM, FORMS, METADATA, BOOKMARKS, ANNOTATIONS, EXTRACTED_TEXT }

/** Which row of canvas tools is open above the dock. */
private enum class ToolPanel { ANNOTATE, SIGN }

/** The full screens inside an open document. */
private enum class EditorMode { EDIT, PAGES, PROTECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onBack: () -> Unit,
    share: ShareService,
    ads: UnityAdsService,
    initialUri: String? = null,
    vm: PdfEditorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(initialUri) { initialUri?.let { vm.openInitial(android.net.Uri.parse(it)) } }
    val context = LocalContext.current
    var tool by remember { mutableStateOf(PdfCanvasTool.NONE) }
    var panel by remember { mutableStateOf<ToolPanel?>(null) }
    var mode by remember { mutableStateOf(EditorMode.EDIT) }
    var moreOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PdfDialog?>(null) }
    var pendingRect by remember { mutableStateOf<PdfRect?>(null) }
    var confirmExit by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val events = remember { UiEventChannel() }
    CollectUiEvents(events.events, snackbarHostState)

    val requestExit: () -> Unit = {
        if (state.session?.dirty == true) confirmExit = true
        else vm.close(discard = true, onClosed = onBack)
    }

    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::open) }
    val mergePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.size >= 2) vm.merge(it) }
    val insertPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.insertPages(it, (state.selectedPages.minOrNull() ?: (state.pages.size - 1)).plus(1).coerceAtLeast(0)) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val rect = pendingRect
        if (uri != null && rect != null) vm.image(rect, uri)
        pendingRect = null
        tool = PdfCanvasTool.NONE
    }

    LaunchedEffect(state.shareFile?.absolutePath) {
        state.shareFile?.let { file -> runCatching { share.share(file, "application/pdf") }; vm.consumeShare() }
    }
    LaunchedEffect(state.printFile?.absolutePath) {
        state.printFile?.let { file -> runCatching { printPdf(context, file) }; vm.consumePrint() }
    }
    ExportInterstitialEffect(state.exportedFiles.lastOrNull()?.absolutePath, ads)

    // One place for messages: the snackbar. The password prompt has its own dialog, so those two stay out of it.
    val messageText = state.message?.let { pdfMessage(it) }
    LaunchedEffect(state.message, state.detail) {
        val key = state.message ?: return@LaunchedEffect
        if (key != PdfEditorMessageKey.PASSWORD_REQUIRED && key != PdfEditorMessageKey.WRONG_PASSWORD) {
            events.message(UiText.dynamic(listOfNotNull(messageText, state.detail?.takeIf { it.isNotBlank() }).joinToString(" — ")))
        }
        vm.clearMessage()
    }
    state.passwordRequiredFor?.let { uri ->
        PasswordDialog(
            wrong = state.passwordWasWrong,
            onDismiss = vm::dismissPasswordPrompt,
            onSubmit = { password -> vm.open(uri, password) },
        )
    }

    if (state.session == null) {
        FeatureScaffold(title = stringResource(R.string.pdf_editor_title), onBack = onBack, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StartTile(Icons.Rounded.PictureAsPdf, stringResource(R.string.pdf_open_document), stringResource(R.string.pdf_open_desc), Modifier.weight(1f)) {
                        openPicker.launch(arrayOf("application/pdf"))
                    }
                    StartTile(Icons.AutoMirrored.Rounded.CallMerge, stringResource(R.string.pdf_merge_documents), stringResource(R.string.pdf_merge_desc), Modifier.weight(1f)) {
                        mergePicker.launch(arrayOf("application/pdf"))
                    }
                }
                ExportedFiles(state.exportedFiles, share)
            }
        }
        return
    }

    val currentPage = state.selectedPages.minOrNull()?.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0)) ?: 0
    LaunchedEffect(currentPage, state.session?.id) { if (state.pages.isNotEmpty()) vm.loadPreview(currentPage) }

    BackHandler(enabled = state.session != null) {
        when {
            mode != EditorMode.EDIT -> mode = EditorMode.EDIT
            moreOpen -> moreOpen = false
            tool != PdfCanvasTool.NONE || panel != null -> { tool = PdfCanvasTool.NONE; panel = null }
            else -> requestExit()
        }
    }

    when (mode) {
        EditorMode.PAGES -> PdfPagesScreen(state, vm, onBack = { mode = EditorMode.EDIT }, snackbarHostState = snackbarHostState)
        EditorMode.PROTECT -> PdfProtectScreen(
            state = state.security,
            onBack = { mode = EditorMode.EDIT },
            onProtect = vm::protect,
            onRemove = vm::removeProtection,
            snackbarHostState = snackbarHostState,
        )
        EditorMode.EDIT -> FeatureScaffold(
            title = state.session?.sourceName ?: stringResource(R.string.pdf_editor_title),
            onBack = requestExit,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            actions = {
                IconButton(onClick = { vm.undo() }, enabled = state.session?.canUndo == true && !state.busy) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.pdf_undo))
                }
                IconButton(onClick = { vm.redo() }, enabled = state.session?.canRedo == true && !state.busy) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.pdf_redo))
                }
                Button(onClick = { vm.save() }, enabled = !state.busy, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(R.string.pdf_save)) }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                PageStrip(state, vm, Modifier.fillMaxWidth().height(104.dp))
                if (state.pages.isNotEmpty()) {
                    val info = state.pages[currentPage]
                    PdfPageCanvas(
                        previewPath = state.previews[currentPage],
                        pageInfo = info,
                        tool = tool,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                        onRect = { rect ->
                            when (tool) {
                                PdfCanvasTool.CROP -> vm.cropSelected(rect)
                                PdfCanvasTool.HIGHLIGHT -> vm.highlight(rect)
                                PdfCanvasTool.UNDERLINE -> vm.underline(rect)
                                PdfCanvasTool.STRIKEOUT -> vm.strikeout(rect)
                                PdfCanvasTool.TEXT -> { pendingRect = rect; dialog = PdfDialog.TEXT }
                                PdfCanvasTool.NOTE -> { pendingRect = rect; dialog = PdfDialog.NOTE }
                                PdfCanvasTool.LINK -> { pendingRect = rect; dialog = PdfDialog.LINK }
                                PdfCanvasTool.RECTANGLE -> vm.rectangle(rect)
                                PdfCanvasTool.SIGNATURE -> { pendingRect = rect; imagePicker.launch("image/*") }
                                PdfCanvasTool.REDACT -> { pendingRect = rect; dialog = PdfDialog.REDACT_CONFIRM }
                                else -> Unit
                            }
                            if (tool != PdfCanvasTool.INK && tool != PdfCanvasTool.TEXT && tool != PdfCanvasTool.NOTE && tool != PdfCanvasTool.LINK && tool != PdfCanvasTool.REDACT && tool != PdfCanvasTool.SIGNATURE) tool = PdfCanvasTool.NONE
                        },
                        onInk = { points -> vm.ink(points); tool = PdfCanvasTool.NONE },
                    )
                } else Spacer(Modifier.weight(1f))

                EditorDock(
                    panel = panel,
                    tool = tool,
                    onTool = { tool = if (tool == it) PdfCanvasTool.NONE else it },
                    onDone = { tool = PdfCanvasTool.NONE; panel = null },
                    onPages = { tool = PdfCanvasTool.NONE; panel = null; mode = EditorMode.PAGES },
                    onAnnotate = { tool = PdfCanvasTool.NONE; panel = if (panel == ToolPanel.ANNOTATE) null else ToolPanel.ANNOTATE },
                    onSign = { tool = PdfCanvasTool.NONE; panel = if (panel == ToolPanel.SIGN) null else ToolPanel.SIGN },
                    onProtect = { tool = PdfCanvasTool.NONE; panel = null; mode = EditorMode.PROTECT },
                    onMore = { moreOpen = true },
                )
            }
        }
    }

    if (moreOpen) {
        ModalBottomSheet(onDismissRequest = { moreOpen = false }) {
            fun pick(action: () -> Unit): () -> Unit = { moreOpen = false; action() }
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoreGroup(
                    stringResource(R.string.pdf_group_find),
                    listOf(
                        MoreAction(Icons.Rounded.Search, R.string.search) { dialog = PdfDialog.SEARCH },
                        MoreAction(Icons.AutoMirrored.Rounded.TextSnippet, R.string.pdf_extract_text) { vm.extractText(); dialog = PdfDialog.EXTRACTED_TEXT },
                        MoreAction(Icons.Rounded.Bookmarks, R.string.pdf_bookmarks) { dialog = PdfDialog.BOOKMARKS },
                        MoreAction(Icons.AutoMirrored.Rounded.Comment, R.string.pdf_annotations) { dialog = PdfDialog.ANNOTATIONS },
                    ),
                    ::pick,
                )
                MoreGroup(
                    stringResource(R.string.pdf_group_edit),
                    listOf(
                        MoreAction(Icons.Rounded.DynamicForm, R.string.pdf_forms) { dialog = PdfDialog.FORMS },
                        MoreAction(Icons.AutoMirrored.Rounded.NoteAdd, R.string.pdf_insert_pages) { insertPicker.launch(arrayOf("application/pdf")) },
                        MoreAction(Icons.Rounded.PostAdd, R.string.pdf_insert_blank_page) { vm.insertBlankPage() },
                        MoreAction(Icons.Rounded.Tag, R.string.pdf_page_numbers) { vm.addPageNumbers() },
                        MoreAction(Icons.Rounded.WaterDrop, R.string.pdf_watermark) { dialog = PdfDialog.WATERMARK },
                    ),
                    ::pick,
                )
                MoreGroup(
                    stringResource(R.string.pdf_group_output),
                    listOf(
                        MoreAction(Icons.Rounded.Save, R.string.pdf_save_as) { dialog = PdfDialog.SAVE_AS },
                        MoreAction(Icons.Rounded.Share, R.string.share) { vm.shareWorkingCopy() },
                        MoreAction(Icons.Rounded.Print, R.string.pdf_print, enabled = state.security?.canPrint != false) { vm.printWorkingCopy() },
                        MoreAction(Icons.Rounded.Info, R.string.pdf_properties) { dialog = PdfDialog.METADATA },
                    ),
                    ::pick,
                )
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.pdf_unsaved_title)) },
            text = { Text(stringResource(R.string.pdf_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    vm.close(discard = true, onClosed = onBack)
                }) { Text(stringResource(R.string.pdf_discard_changes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.pdf_keep_editing)) }
            },
        )
    }

    when (dialog) {
        PdfDialog.SAVE_AS -> TextInputDialog(stringResource(R.string.pdf_save_as), stringResource(R.string.pdf_filename), onDismiss = { dialog = null }) { vm.save(it); dialog = null }
        PdfDialog.WATERMARK -> TextInputDialog(stringResource(R.string.pdf_watermark), stringResource(R.string.pdf_watermark_text), onDismiss = { dialog = null }) { vm.watermark(it); dialog = null }
        PdfDialog.SEARCH -> SearchDialog(state.searchQuery, state.searchHits, { dialog = null }, vm::search) { page -> vm.selectPage(page); dialog = null }
        PdfDialog.TEXT -> TextInputDialog(stringResource(R.string.pdf_add_text), stringResource(R.string.pdf_text), onDismiss = { dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }) { text -> pendingRect?.let { vm.addText(it, text) }; dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }
        PdfDialog.NOTE -> TextInputDialog(stringResource(R.string.pdf_add_note), stringResource(R.string.pdf_note), onDismiss = { dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }) { text -> pendingRect?.let { vm.note(it, text) }; dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }
        PdfDialog.LINK -> TextInputDialog(stringResource(R.string.pdf_add_link), stringResource(R.string.pdf_link_url), onDismiss = { dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }) { url -> pendingRect?.let { vm.link(it, url) }; dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }
        PdfDialog.REDACT_CONFIRM -> ConfirmRedactionDialog({ dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }) { pendingRect?.let(vm::redact); dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }
        PdfDialog.FORMS -> FormsDialog(state.forms, { dialog = null }, vm::setFormField, vm::flattenForms)
        PdfDialog.METADATA -> state.metadata?.let { MetadataDialog(it, { dialog = null }) { value -> vm.updateMetadata(value); dialog = null } }
        PdfDialog.BOOKMARKS -> BookmarksDialog(state.bookmarks, currentPage, { dialog = null }, vm::addBookmark, vm::removeBookmark) { vm.selectPage(it); dialog = null }
        PdfDialog.ANNOTATIONS -> AnnotationsDialog(state.annotations, { dialog = null }, vm::removeAnnotation) { vm.selectPage(it); dialog = null }
        PdfDialog.EXTRACTED_TEXT -> TextViewerDialog(stringResource(R.string.pdf_extracted_text), state.extractedText, onDismiss = { dialog = null })
        null -> Unit
    }
}

// ───────────────────────────── Start ─────────────────────────────

@Composable
private fun StartTile(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp).heightIn(min = 112.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExportedFiles(files: List<File>, share: ShareService) {
    if (files.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.pdf_saved_files_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        files.asReversed().forEach { file ->
            TileRow(
                icon = Icons.Rounded.PictureAsPdf,
                title = file.name,
                subtitle = DateUtils.getRelativeTimeSpanString(file.lastModified(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                trailing = {
                    IconButton(onClick = { runCatching { share.share(file, "application/pdf") } }) { Icon(Icons.Rounded.Share, stringResource(R.string.share)) }
                },
            )
        }
    }
}

// ───────────────────────────── Editor pieces ─────────────────────────────

@Composable
private fun PageStrip(state: PdfEditorUiState, vm: PdfEditorViewModel, modifier: Modifier = Modifier) {
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(state.pages, key = { _, page -> page.index }) { index, page ->
            LaunchedEffect(state.session?.id, index) { vm.loadPreview(index) }
            val selected = index in state.selectedPages
            Surface(
                onClick = { vm.selectPage(index) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.width(64.dp).fillMaxHeight().border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
            ) {
                Box {
                    PdfPreviewImage(state.previews[index], Modifier.fillMaxSize().padding(3.dp))
                    Text(
                        "${page.index + 1}",
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private val annotateTools = listOf(
    PdfCanvasTool.HIGHLIGHT, PdfCanvasTool.UNDERLINE, PdfCanvasTool.STRIKEOUT, PdfCanvasTool.TEXT, PdfCanvasTool.INK,
    PdfCanvasTool.RECTANGLE, PdfCanvasTool.NOTE, PdfCanvasTool.LINK, PdfCanvasTool.CROP,
)
private val signTools = listOf(PdfCanvasTool.SIGNATURE, PdfCanvasTool.REDACT)

private fun toolIcon(tool: PdfCanvasTool): ImageVector = when (tool) {
    PdfCanvasTool.HIGHLIGHT -> Icons.Rounded.Highlight
    PdfCanvasTool.UNDERLINE -> Icons.Rounded.FormatUnderlined
    PdfCanvasTool.STRIKEOUT -> Icons.Rounded.FormatStrikethrough
    PdfCanvasTool.TEXT -> Icons.Rounded.TextFields
    PdfCanvasTool.INK -> Icons.Rounded.Draw
    PdfCanvasTool.RECTANGLE -> Icons.Rounded.CropSquare
    PdfCanvasTool.NOTE -> Icons.AutoMirrored.Rounded.StickyNote2
    PdfCanvasTool.LINK -> Icons.Rounded.Link
    PdfCanvasTool.CROP -> Icons.Rounded.Crop
    PdfCanvasTool.SIGNATURE -> Icons.Rounded.Gesture
    PdfCanvasTool.REDACT -> Icons.Rounded.VisibilityOff
    PdfCanvasTool.NONE -> Icons.Rounded.Edit
}

private fun toolLabel(tool: PdfCanvasTool): Int = when (tool) {
    PdfCanvasTool.HIGHLIGHT -> R.string.pdf_highlight
    PdfCanvasTool.UNDERLINE -> R.string.pdf_underline
    PdfCanvasTool.STRIKEOUT -> R.string.pdf_strikeout
    PdfCanvasTool.TEXT -> R.string.pdf_add_text
    PdfCanvasTool.INK -> R.string.pdf_draw
    PdfCanvasTool.RECTANGLE -> R.string.pdf_rectangle
    PdfCanvasTool.NOTE -> R.string.pdf_add_note
    PdfCanvasTool.LINK -> R.string.pdf_add_link
    PdfCanvasTool.CROP -> R.string.pdf_crop
    PdfCanvasTool.SIGNATURE -> R.string.pdf_signature
    PdfCanvasTool.REDACT -> R.string.pdf_redact
    PdfCanvasTool.NONE -> R.string.pdf_editor_title
}

/** Five groups instead of thirty chips. Annotate and Sign open a row of canvas tools; the rest open a screen or sheet. */
@Composable
private fun EditorDock(
    panel: ToolPanel?,
    tool: PdfCanvasTool,
    onTool: (PdfCanvasTool) -> Unit,
    onDone: () -> Unit,
    onPages: () -> Unit,
    onAnnotate: () -> Unit,
    onSign: () -> Unit,
    onProtect: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (panel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(if (panel == ToolPanel.ANNOTATE) annotateTools else signTools) { option ->
                            val selected = tool == option
                            Column(
                                Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { onTool(option) }
                                    .defaultMinSize(minWidth = 64.dp, minHeight = 56.dp)
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(toolIcon(option), null, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(toolLabel(option)), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                    TextButton(onClick = onDone) { Text(stringResource(R.string.pdf_tools_done)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                DockItem(Icons.Rounded.GridView, R.string.pdf_dock_pages, false, onPages)
                DockItem(Icons.Rounded.Edit, R.string.pdf_dock_annotate, panel == ToolPanel.ANNOTATE, onAnnotate)
                DockItem(Icons.Rounded.Gesture, R.string.pdf_dock_sign, panel == ToolPanel.SIGN, onSign)
                DockItem(Icons.Rounded.Lock, R.string.pdf_dock_protect, false, onProtect)
                DockItem(Icons.Rounded.MoreHoriz, R.string.pdf_dock_more, false, onMore)
            }
        }
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: Int, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).defaultMinSize(minWidth = 64.dp, minHeight = 56.dp).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

private class MoreAction(val icon: ImageVector, val label: Int, val enabled: Boolean = true, val run: () -> Unit)

@Composable
private fun MoreGroup(title: String, actions: List<MoreAction>, pick: (() -> Unit) -> (() -> Unit)) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    actions.chunked(4).forEach { rowActions ->
        Row(Modifier.fillMaxWidth()) {
            rowActions.forEach { action ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = action.enabled, onClick = pick(action.run)).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                action.icon, null,
                                tint = if (action.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                    }
                    Text(stringResource(action.label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
            repeat(4 - rowActions.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ───────────────────────────── Pages (organize) ─────────────────────────────

@Composable
private fun PdfPagesScreen(state: PdfEditorUiState, vm: PdfEditorViewModel, onBack: () -> Unit, snackbarHostState: SnackbarHostState) {
    val selected = state.selectedPages
    val enabled = selected.isNotEmpty() && !state.busy
    FeatureScaffold(
        title = stringResource(R.string.pdf_pages_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            if (selected.isNotEmpty()) {
                Text(stringResource(R.string.pdf_selected_count, selected.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItemsIndexed(state.pages, key = { _, page -> page.index }) { index, page ->
                    LaunchedEffect(state.session?.id, index) { vm.loadPreview(index) }
                    val isSelected = index in selected
                    val single = selected.size == 1 && isSelected
                    PageGridTile(
                        number = index + 1, previewPath = state.previews[index], selected = isSelected,
                        canLeft = single && index > 0, canRight = single && index < state.pages.lastIndex,
                        onToggle = { vm.selectPage(index, toggle = true) },
                        onLeft = { vm.reorder(movedOrder(state.pages.size, index, index - 1)) },
                        onRight = { vm.reorder(movedOrder(state.pages.size, index, index + 1)) },
                    )
                }
            }
            Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    PagesAction(Icons.AutoMirrored.Rounded.RotateRight, R.string.pdf_rotate_pages, enabled) { vm.rotateSelected(90) }
                    PagesAction(Icons.Rounded.ContentCopy, R.string.pdf_duplicate_pages, enabled) { vm.duplicateSelected() }
                    PagesAction(Icons.Rounded.IosShare, R.string.pdf_extract_pages, enabled) { vm.extractSelected() }
                    PagesAction(Icons.AutoMirrored.Rounded.CallSplit, R.string.pdf_split, !state.busy) { vm.split() }
                    PagesAction(Icons.Rounded.Delete, R.string.pdf_delete_pages, enabled, MaterialTheme.colorScheme.error) { vm.deleteSelected() }
                }
            }
        }
    }
}

/** The page order after moving the page at [from] to [to]: each entry is an old page index, as `vm.reorder` expects. */
private fun movedOrder(count: Int, from: Int, to: Int): List<Int> =
    (0 until count).toMutableList().also { it.add(to.coerceIn(0, count - 1), it.removeAt(from)) }

@Composable
private fun PageGridTile(
    number: Int, previewPath: String?, selected: Boolean, canLeft: Boolean, canRight: Boolean,
    onToggle: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(shape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onToggle),
    ) {
        PdfPreviewImage(previewPath, Modifier.fillMaxSize())
        Surface(Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp), shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else com.vextech.smart_edit.core.ui.ImageScrim) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(6.dp), shape = CircleShape, color = com.vextech.smart_edit.core.ui.ImageScrim) {
            Text("$number", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White)
        }
        if (canLeft || canRight) {
            Row(Modifier.align(Alignment.BottomEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canLeft) PageArrow(Icons.Rounded.ChevronLeft, R.string.pdf_move_earlier, onLeft)
                if (canRight) PageArrow(Icons.Rounded.ChevronRight, R.string.pdf_move_later, onRight)
            }
        }
    }
}

@Composable
private fun PageArrow(icon: ImageVector, description: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = com.vextech.smart_edit.core.ui.ImageScrim, modifier = Modifier.size(32.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, stringResource(description), Modifier.size(20.dp), tint = androidx.compose.ui.graphics.Color.White) }
    }
}

@Composable
private fun PagesAction(icon: ImageVector, label: Int, enabled: Boolean, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    val color = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).defaultMinSize(minWidth = 60.dp, minHeight = 56.dp).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color)
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else color, maxLines = 1)
    }
}

// ───────────────────────────── Protect ─────────────────────────────

@Composable
private fun PdfProtectScreen(
    state: PdfSecurityState?,
    onBack: () -> Unit,
    onProtect: (PdfSecurityOptions) -> Unit,
    onRemove: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var user by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var allowPrint by remember(state) { mutableStateOf(state?.canPrint ?: true) }
    var allowCopy by remember(state) { mutableStateOf(state?.canCopy ?: true) }
    var allowModify by remember(state) { mutableStateOf(state?.canModify ?: true) }
    var allowAssemble by remember(state) { mutableStateOf(state?.canAssemble ?: true) }
    var allowAnnotate by remember(state) { mutableStateOf(state?.canAnnotate ?: true) }
    var allowForms by remember(state) { mutableStateOf(state?.canFillForms ?: true) }
    var removePassword by remember { mutableStateOf("") }
    val encrypted = state?.encrypted == true

    FeatureScaffold(title = stringResource(R.string.pdf_security), onBack = onBack, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(stringResource(if (encrypted) R.string.pdf_encrypted else R.string.pdf_not_encrypted), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(if (encrypted) R.string.pdf_protect_desc_on else R.string.pdf_protect_desc_off),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.pdf_user_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(owner, { owner = it }, label = { Text(stringResource(R.string.pdf_owner_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                }
                Column {
                    Text(stringResource(R.string.pdf_permissions_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PermissionRow(stringResource(R.string.pdf_allow_print), allowPrint) { allowPrint = it }
                    PermissionRow(stringResource(R.string.pdf_allow_copy), allowCopy) { allowCopy = it }
                    PermissionRow(stringResource(R.string.pdf_allow_modify), allowModify) { allowModify = it }
                    PermissionRow(stringResource(R.string.pdf_allow_assemble), allowAssemble) { allowAssemble = it }
                    PermissionRow(stringResource(R.string.pdf_allow_annotations), allowAnnotate) { allowAnnotate = it }
                    PermissionRow(stringResource(R.string.pdf_allow_forms), allowForms) { allowForms = it }
                }
                if (encrypted) {
                    HorizontalDivider()
                    OutlinedTextField(removePassword, { removePassword = it }, label = { Text(stringResource(R.string.pdf_owner_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { onRemove(removePassword) }, enabled = removePassword.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(R.string.pdf_remove_security))
                    }
                }
            }
            com.vextech.smart_edit.core.ui.BottomAction {
                Button(
                    onClick = {
                        onProtect(
                            PdfSecurityOptions(
                                userPassword = user, ownerPassword = owner,
                                allowPrint = allowPrint, allowCopy = allowCopy, allowModify = allowModify,
                                allowAssemble = allowAssemble, allowAnnotations = allowAnnotate, allowFillForms = allowForms,
                            )
                        )
                    },
                    enabled = owner.length >= 4 && state?.isOwner != false,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.pdf_apply_security)) }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PdfPageCanvas(
    previewPath: String?,
    pageInfo: PdfPageInfo,
    tool: PdfCanvasTool,
    modifier: Modifier,
    onRect: (PdfRect) -> Unit,
    onInk: (List<PdfPoint>) -> Unit,
) {
    val ratio = if (pageInfo.rotation == 90 || pageInfo.rotation == 270) pageInfo.heightPoints / pageInfo.widthPoints else pageInfo.widthPoints / pageInfo.heightPoints
    var size by remember { mutableStateOf(IntSize.Zero) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    val ink = remember { mutableStateListOf<PdfPoint>() }

    fun point(offset: Offset): PdfPoint? {
        if (size.width <= 0 || size.height <= 0) return null
        return runCatching { PdfPoint((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f)) }.getOrNull()
    }

    val gesture = if (tool == PdfCanvasTool.NONE) Modifier else Modifier.pointerInput(tool, size) {
        detectDragGestures(
            onDragStart = { offset ->
                start = offset; end = offset; ink.clear()
                if (tool == PdfCanvasTool.INK) point(offset)?.let(ink::add)
            },
            onDrag = { change, _ ->
                end = change.position
                if (tool == PdfCanvasTool.INK) point(change.position)?.let(ink::add)
            },
            onDragEnd = {
                if (tool == PdfCanvasTool.INK) {
                    if (ink.size >= 2) onInk(ink.toList())
                } else {
                    val a = start; val b = end
                    if (a != null && b != null && size.width > 0 && size.height > 0) {
                        runCatching { PdfEditorPolicies.normalizedRect(a.x / size.width, a.y / size.height, b.x / size.width, b.y / size.height) }.getOrNull()?.let(onRect)
                    }
                }
                start = null; end = null; ink.clear()
            },
            onDragCancel = { start = null; end = null; ink.clear() },
        )
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio.coerceAtLeast(.1f)).background(Color.White).onSizeChanged { size = it }.then(gesture),
        ) {
            PdfPreviewImage(previewPath, Modifier.fillMaxSize())
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val a = start; val b = end
                if (a != null && b != null && tool != PdfCanvasTool.INK) {
                    drawRect(
                        color = if (tool == PdfCanvasTool.REDACT) Color.Red else Color(0xFF1976D2),
                        topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                        size = androidx.compose.ui.geometry.Size(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y)),
                        style = Stroke(width = 3f),
                    )
                }
                if (tool == PdfCanvasTool.INK && ink.size > 1) {
                    for (i in 1 until ink.size) {
                        val p1 = ink[i - 1]; val p2 = ink[i]
                        drawLine(Color.Red, Offset(p1.x * size.width, p1.y * size.height), Offset(p2.x * size.width, p2.y * size.height), strokeWidth = 3f)
                    }
                }
            }
        }
        if (tool != PdfCanvasTool.NONE) {
            Text(stringResource(R.string.pdf_drag_instruction), modifier = Modifier.align(Alignment.TopCenter).background(MaterialTheme.colorScheme.surface.copy(alpha = .85f)).padding(6.dp))
        }
    }
}

@Composable
private fun PdfPreviewImage(path: String?, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    if (image != null) Image(image!!, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
}

@Composable
private fun TextInputDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var value by remember(title, initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onSubmit(value.trim()) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.pdf_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PasswordDialog(wrong: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (wrong) R.string.pdf_wrong_password else R.string.pdf_password_required)) },
        text = { OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.pdf_password)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text(stringResource(R.string.pdf_open_document)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SearchDialog(query: String, hits: List<PdfSearchHit>, onDismiss: () -> Unit, onSearch: (String) -> Unit, onPage: (Int) -> Unit) {
    var text by remember(query) { mutableStateOf(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_search_document)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.search)) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onSearch(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.search)) }
                LazyColumn { items(hits) { hit -> TextButton(onClick = { onPage(hit.pageIndex) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pdf_search_hit, hit.pageIndex + 1, hit.snippet)) } } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun FormsDialog(fields: List<PdfFormField>, onDismiss: () -> Unit, onSet: (String, String) -> Unit, onFlatten: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_forms)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                if (fields.isEmpty()) Text(stringResource(R.string.pdf_no_forms))
                else LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(fields, key = { it.name }) { field ->
                        var value by remember(field.name, field.value) { mutableStateOf(field.value) }
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(field.name, style = MaterialTheme.typography.labelMedium)
                            OutlinedTextField(value, { value = it }, enabled = !field.readOnly, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { onSet(field.name, value) }, enabled = !field.readOnly && value != field.value) { Text(stringResource(R.string.pdf_update_field)) }
                        }
                    }
                }
                if (fields.isNotEmpty()) OutlinedButton(onClick = onFlatten, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pdf_flatten_forms)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun MetadataDialog(metadata: PdfDocumentMetadata, onDismiss: () -> Unit, onSave: (PdfDocumentMetadata) -> Unit) {
    var title by remember(metadata) { mutableStateOf(metadata.title) }
    var author by remember(metadata) { mutableStateOf(metadata.author) }
    var subject by remember(metadata) { mutableStateOf(metadata.subject) }
    var keywords by remember(metadata) { mutableStateOf(metadata.keywords) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_properties)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.pdf_title)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text(stringResource(R.string.pdf_author)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subject, { subject = it }, label = { Text(stringResource(R.string.pdf_subject)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(keywords, { keywords = it }, label = { Text(stringResource(R.string.pdf_keywords)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(metadata.copy(title = title, author = author, subject = subject, keywords = keywords)) }) { Text(stringResource(R.string.pdf_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun BookmarksDialog(bookmarks: List<PdfBookmark>, currentPage: Int, onDismiss: () -> Unit, onAdd: (String, Int) -> Unit, onRemove: (String) -> Unit, onPage: (Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_bookmarks)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.pdf_bookmark_title)) }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (title.isNotBlank()) { onAdd(title, currentPage); title = "" } }) { Text(stringResource(R.string.pdf_add)) }
                }
                LazyColumn { items(bookmarks, key = { it.id }) { item -> ListItem(headlineContent = { Text(item.title) }, supportingContent = { Text(stringResource(R.string.pdf_page_number, item.pageIndex + 1)) }, trailingContent = { TextButton(onClick = { onRemove(item.id) }) { Text(stringResource(R.string.pdf_remove)) } }, modifier = Modifier.fillMaxWidth()) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun AnnotationsDialog(items: List<PdfAnnotationInfo>, onDismiss: () -> Unit, onRemove: (String) -> Unit, onPage: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_annotations)) },
        text = {
            if (items.isEmpty()) Text(stringResource(R.string.pdf_no_annotations)) else LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(items, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.type) },
                        supportingContent = { Text(stringResource(R.string.pdf_annotation_summary, item.pageIndex + 1, item.contents)) },
                        trailingContent = { TextButton(onClick = { onRemove(item.id) }) { Text(stringResource(R.string.pdf_remove)) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun ConfirmRedactionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_redact)) },
        text = { Text(stringResource(R.string.pdf_redaction_warning)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.pdf_redact)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TextViewerDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text.ifBlank { stringResource(R.string.pdf_no_text) }, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun pdfMessage(key: PdfEditorMessageKey): String = when (key) {
    PdfEditorMessageKey.OPEN_FAILED -> stringResource(R.string.pdf_error_open)
    PdfEditorMessageKey.PASSWORD_REQUIRED -> stringResource(R.string.pdf_password_required)
    PdfEditorMessageKey.WRONG_PASSWORD -> stringResource(R.string.pdf_wrong_password)
    PdfEditorMessageKey.SAVE_FAILED -> stringResource(R.string.pdf_error_save)
    PdfEditorMessageKey.SAVED -> stringResource(R.string.pdf_saved)
    PdfEditorMessageKey.SHARE_FAILED -> stringResource(R.string.pdf_error_share)
    PdfEditorMessageKey.OPERATION_FAILED -> stringResource(R.string.pdf_error_operation)
    PdfEditorMessageKey.INVALID_PAGE_SELECTION -> stringResource(R.string.pdf_invalid_selection)
    PdfEditorMessageKey.SEARCH_EMPTY -> stringResource(R.string.pdf_no_search_results)
    PdfEditorMessageKey.REDACTION_WARNING -> stringResource(R.string.pdf_redaction_warning)
    PdfEditorMessageKey.REDACTION_REQUIRES_UNPROTECTED -> stringResource(R.string.pdf_redaction_requires_unprotected)
    PdfEditorMessageKey.SECURITY_APPLIED -> stringResource(R.string.pdf_security_applied)
    PdfEditorMessageKey.SECURITY_REMOVED -> stringResource(R.string.pdf_security_removed)
    PdfEditorMessageKey.PRINT_NOT_ALLOWED -> stringResource(R.string.pdf_print_not_allowed)
}
