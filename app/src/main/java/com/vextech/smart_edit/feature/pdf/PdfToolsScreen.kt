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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PdfCanvasTool { NONE, CROP, HIGHLIGHT, UNDERLINE, STRIKEOUT, TEXT, NOTE, LINK, RECTANGLE, INK, SIGNATURE, REDACT }
private enum class PdfDialog { SAVE_AS, REORDER, WATERMARK, SEARCH, TEXT, NOTE, LINK, REDACT_CONFIRM, FORMS, METADATA, BOOKMARKS, SECURITY, ANNOTATIONS, EXTRACTED_TEXT }

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
    var dialog by remember { mutableStateOf<PdfDialog?>(null) }
    var pendingRect by remember { mutableStateOf<PdfRect?>(null) }
    var confirmExit by remember { mutableStateOf(false) }

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

    val message = state.message?.let { pdfMessage(it) }
    state.passwordRequiredFor?.let { uri ->
        PasswordDialog(
            wrong = state.passwordWasWrong,
            onDismiss = vm::dismissPasswordPrompt,
            onSubmit = { password -> vm.open(uri, password) },
        )
    }

    if (state.session == null) {
        FeatureScaffold(title = stringResource(R.string.pdf_editor_title), onBack = onBack) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.pdf_editor_intro), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { openPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pdf_open_document))
                }
                OutlinedButton(onClick = { mergePicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pdf_merge_documents))
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!message.isNullOrBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                state.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                ExportedFiles(state.exportedFiles, share)
            }
        }
        return
    }

    val currentPage = state.selectedPages.minOrNull()?.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0)) ?: 0
    LaunchedEffect(currentPage, state.session?.id) { if (state.pages.isNotEmpty()) vm.loadPreview(currentPage) }

    BackHandler(enabled = state.session != null) { requestExit() }

    FeatureScaffold(
        title = state.session?.sourceName ?: stringResource(R.string.pdf_editor_title),
        onBack = requestExit,
        actions = {
            TextButton(onClick = { vm.undo() }, enabled = state.session?.canUndo == true && !state.busy) { Text(stringResource(R.string.pdf_undo)) }
            TextButton(onClick = { vm.redo() }, enabled = state.session?.canRedo == true && !state.busy) { Text(stringResource(R.string.pdf_redo)) }
            TextButton(onClick = { vm.save() }, enabled = !state.busy) { Text(stringResource(R.string.pdf_save)) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!message.isNullOrBlank() || !state.detail.isNullOrBlank()) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        message?.let { Text(it) }
                        state.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            PageStrip(state, vm, Modifier.fillMaxWidth().height(118.dp))
            PdfEditorToolbar(
                tool = tool,
                onTool = { tool = if (tool == it) PdfCanvasTool.NONE else it },
                onSaveAs = { dialog = PdfDialog.SAVE_AS },
                onShare = vm::shareWorkingCopy,
                onPrint = vm::printWorkingCopy,
                printEnabled = state.security?.canPrint != false,
                onInsert = { insertPicker.launch(arrayOf("application/pdf")) },
                onInsertBlank = vm::insertBlankPage,
                onPageNumbers = { vm.addPageNumbers() },
                onDelete = vm::deleteSelected,
                onDuplicate = vm::duplicateSelected,
                onRotate = { vm.rotateSelected(90) },
                onExtract = vm::extractSelected,
                onSplit = vm::split,
                onReorder = { dialog = PdfDialog.REORDER },
                onWatermark = { dialog = PdfDialog.WATERMARK },
                onSearch = { dialog = PdfDialog.SEARCH },
                onForms = { dialog = PdfDialog.FORMS },
                onMetadata = { dialog = PdfDialog.METADATA },
                onBookmarks = { dialog = PdfDialog.BOOKMARKS },
                onSecurity = { dialog = PdfDialog.SECURITY },
                onAnnotations = { dialog = PdfDialog.ANNOTATIONS },
                onTextExtract = { vm.extractText(); dialog = PdfDialog.EXTRACTED_TEXT },
            )

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
            }
            ExportedFiles(state.exportedFiles, share)
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
        PdfDialog.REORDER -> ReorderDialog(state.pages.size, onDismiss = { dialog = null }) { vm.reorder(it); dialog = null }
        PdfDialog.WATERMARK -> TextInputDialog(stringResource(R.string.pdf_watermark), stringResource(R.string.pdf_watermark_text), onDismiss = { dialog = null }) { vm.watermark(it); dialog = null }
        PdfDialog.SEARCH -> SearchDialog(state.searchQuery, state.searchHits, { dialog = null }, vm::search) { page -> vm.selectPage(page); dialog = null }
        PdfDialog.TEXT -> TextInputDialog(stringResource(R.string.pdf_add_text), stringResource(R.string.pdf_text), onDismiss = { dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }) { text -> pendingRect?.let { vm.addText(it, text) }; dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }
        PdfDialog.NOTE -> TextInputDialog(stringResource(R.string.pdf_add_note), stringResource(R.string.pdf_note), onDismiss = { dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }) { text -> pendingRect?.let { vm.note(it, text) }; dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }
        PdfDialog.LINK -> TextInputDialog(stringResource(R.string.pdf_add_link), stringResource(R.string.pdf_link_url), onDismiss = { dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }) { url -> pendingRect?.let { vm.link(it, url) }; dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }
        PdfDialog.REDACT_CONFIRM -> ConfirmRedactionDialog({ dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }) { pendingRect?.let(vm::redact); dialog = null; pendingRect = null; tool = PdfCanvasTool.NONE }
        PdfDialog.FORMS -> FormsDialog(state.forms, { dialog = null }, vm::setFormField, vm::flattenForms)
        PdfDialog.METADATA -> state.metadata?.let { MetadataDialog(it, { dialog = null }) { value -> vm.updateMetadata(value); dialog = null } }
        PdfDialog.BOOKMARKS -> BookmarksDialog(state.bookmarks, currentPage, { dialog = null }, vm::addBookmark, vm::removeBookmark) { vm.selectPage(it); dialog = null }
        PdfDialog.SECURITY -> SecurityDialog(state.security, { dialog = null }, { vm.protect(it); dialog = null }, { vm.removeProtection(it); dialog = null })
        PdfDialog.ANNOTATIONS -> AnnotationsDialog(state.annotations, { dialog = null }, vm::removeAnnotation) { vm.selectPage(it); dialog = null }
        PdfDialog.EXTRACTED_TEXT -> TextViewerDialog(stringResource(R.string.pdf_extracted_text), state.extractedText, onDismiss = { dialog = null })
        null -> Unit
    }
}

@Composable
private fun PageStrip(state: PdfEditorUiState, vm: PdfEditorViewModel, modifier: Modifier = Modifier) {
    LazyRow(modifier, contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(state.pages, key = { _, page -> page.index }) { index, page ->
            LaunchedEffect(state.session?.id, index) { vm.loadPreview(index) }
            val selected = index in state.selectedPages
            Surface(
                onClick = { vm.selectPage(index) },
                modifier = Modifier.width(84.dp).fillMaxHeight().border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                tonalElevation = if (selected) 4.dp else 0.dp,
            ) {
                Box {
                    PdfPreviewImage(state.previews[index], Modifier.fillMaxSize().padding(4.dp))
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { vm.selectPage(index, toggle = true) },
                        modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                    )
                    Text("${page.index + 1}", modifier = Modifier.align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.surface.copy(alpha = .8f)).padding(horizontal = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun PdfEditorToolbar(
    tool: PdfCanvasTool,
    onTool: (PdfCanvasTool) -> Unit,
    onSaveAs: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    printEnabled: Boolean,
    onInsert: () -> Unit,
    onInsertBlank: () -> Unit,
    onPageNumbers: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onRotate: () -> Unit,
    onExtract: () -> Unit,
    onSplit: () -> Unit,
    onReorder: () -> Unit,
    onWatermark: () -> Unit,
    onSearch: () -> Unit,
    onForms: () -> Unit,
    onMetadata: () -> Unit,
    onBookmarks: () -> Unit,
    onSecurity: () -> Unit,
    onAnnotations: () -> Unit,
    onTextExtract: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 164.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { ToolChip(stringResource(R.string.pdf_save_as), onClick = onSaveAs) }
                item { ToolChip(stringResource(R.string.share), onClick = onShare) }
                item { ToolChip(stringResource(R.string.pdf_print), enabled = printEnabled, onClick = onPrint) }
                item { ToolChip(stringResource(R.string.pdf_insert_pages), onClick = onInsert) }
                item { ToolChip(stringResource(R.string.pdf_insert_blank_page), onClick = onInsertBlank) }
                item { ToolChip(stringResource(R.string.pdf_page_numbers), onClick = onPageNumbers) }
                item { ToolChip(stringResource(R.string.pdf_delete_pages), onClick = onDelete) }
                item { ToolChip(stringResource(R.string.pdf_duplicate_pages), onClick = onDuplicate) }
                item { ToolChip(stringResource(R.string.pdf_rotate_pages), onClick = onRotate) }
                item { ToolChip(stringResource(R.string.pdf_reorder_pages), onClick = onReorder) }
                item { ToolChip(stringResource(R.string.pdf_extract_pages), onClick = onExtract) }
                item { ToolChip(stringResource(R.string.pdf_split), onClick = onSplit) }
                item { ToolChip(stringResource(R.string.pdf_watermark), onClick = onWatermark) }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { ToolChip(stringResource(R.string.pdf_crop), selected = tool == PdfCanvasTool.CROP) { onTool(PdfCanvasTool.CROP) } }
                item { ToolChip(stringResource(R.string.pdf_highlight), selected = tool == PdfCanvasTool.HIGHLIGHT) { onTool(PdfCanvasTool.HIGHLIGHT) } }
                item { ToolChip(stringResource(R.string.pdf_underline), selected = tool == PdfCanvasTool.UNDERLINE) { onTool(PdfCanvasTool.UNDERLINE) } }
                item { ToolChip(stringResource(R.string.pdf_strikeout), selected = tool == PdfCanvasTool.STRIKEOUT) { onTool(PdfCanvasTool.STRIKEOUT) } }
                item { ToolChip(stringResource(R.string.pdf_add_text), selected = tool == PdfCanvasTool.TEXT) { onTool(PdfCanvasTool.TEXT) } }
                item { ToolChip(stringResource(R.string.pdf_draw), selected = tool == PdfCanvasTool.INK) { onTool(PdfCanvasTool.INK) } }
                item { ToolChip(stringResource(R.string.pdf_rectangle), selected = tool == PdfCanvasTool.RECTANGLE) { onTool(PdfCanvasTool.RECTANGLE) } }
                item { ToolChip(stringResource(R.string.pdf_add_note), selected = tool == PdfCanvasTool.NOTE) { onTool(PdfCanvasTool.NOTE) } }
                item { ToolChip(stringResource(R.string.pdf_add_link), selected = tool == PdfCanvasTool.LINK) { onTool(PdfCanvasTool.LINK) } }
                item { ToolChip(stringResource(R.string.pdf_signature), selected = tool == PdfCanvasTool.SIGNATURE) { onTool(PdfCanvasTool.SIGNATURE) } }
                item { ToolChip(stringResource(R.string.pdf_redact), selected = tool == PdfCanvasTool.REDACT) { onTool(PdfCanvasTool.REDACT) } }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { ToolChip(stringResource(R.string.search), onClick = onSearch) }
                item { ToolChip(stringResource(R.string.pdf_forms), onClick = onForms) }
                item { ToolChip(stringResource(R.string.pdf_bookmarks), onClick = onBookmarks) }
                item { ToolChip(stringResource(R.string.pdf_annotations), onClick = onAnnotations) }
                item { ToolChip(stringResource(R.string.pdf_properties), onClick = onMetadata) }
                item { ToolChip(stringResource(R.string.pdf_security), onClick = onSecurity) }
                item { ToolChip(stringResource(R.string.pdf_extract_text), onClick = onTextExtract) }
            }
        }
    }
}

@Composable private fun ToolChip(text: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    FilterChip(selected = selected, enabled = enabled, onClick = onClick, label = { Text(text) })
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
private fun ExportedFiles(files: List<File>, share: ShareService) {
    if (files.isEmpty()) return
    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(files, key = { it.absolutePath }) { file ->
            AssistChip(onClick = { share.share(file, "application/pdf") }, label = { Text("${file.name} · ${stringResource(R.string.share)}") })
        }
    }
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
private fun ReorderDialog(pageCount: Int, onDismiss: () -> Unit, onSubmit: (List<Int>) -> Unit) {
    var text by remember(pageCount) { mutableStateOf((1..pageCount).joinToString(",")) }
    val parsed = remember(text, pageCount) { runCatching { text.split(',').map { it.trim().toInt() - 1 }.also { PdfEditorPolicies.validateReorder(pageCount, it) } }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_reorder_pages)) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.pdf_page_order)) }) },
        confirmButton = { TextButton(onClick = { parsed?.let(onSubmit) }, enabled = parsed != null) { Text(stringResource(R.string.pdf_apply)) } },
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
private fun SecurityDialog(state: PdfSecurityState?, onDismiss: () -> Unit, onProtect: (PdfSecurityOptions) -> Unit, onRemove: (String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var allowPrint by remember(state) { mutableStateOf(state?.canPrint ?: true) }
    var allowCopy by remember(state) { mutableStateOf(state?.canCopy ?: true) }
    var allowModify by remember(state) { mutableStateOf(state?.canModify ?: true) }
    var allowAssemble by remember(state) { mutableStateOf(state?.canAssemble ?: true) }
    var allowAnnotate by remember(state) { mutableStateOf(state?.canAnnotate ?: true) }
    var allowForms by remember(state) { mutableStateOf(state?.canFillForms ?: true) }
    var removePassword by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_security)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (state?.encrypted == true) stringResource(R.string.pdf_encrypted) else stringResource(R.string.pdf_not_encrypted))
                OutlinedTextField(
                    user,
                    { user = it },
                    label = { Text(stringResource(R.string.pdf_user_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    owner,
                    { owner = it },
                    label = { Text(stringResource(R.string.pdf_owner_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                SecurityCheck(stringResource(R.string.pdf_allow_print), allowPrint) { allowPrint = it }
                SecurityCheck(stringResource(R.string.pdf_allow_copy), allowCopy) { allowCopy = it }
                SecurityCheck(stringResource(R.string.pdf_allow_modify), allowModify) { allowModify = it }
                SecurityCheck(stringResource(R.string.pdf_allow_assemble), allowAssemble) { allowAssemble = it }
                SecurityCheck(stringResource(R.string.pdf_allow_annotations), allowAnnotate) { allowAnnotate = it }
                SecurityCheck(stringResource(R.string.pdf_allow_forms), allowForms) { allowForms = it }
                Button(
                    onClick = {
                        onProtect(
                            PdfSecurityOptions(
                                userPassword = user,
                                ownerPassword = owner,
                                allowPrint = allowPrint,
                                allowCopy = allowCopy,
                                allowModify = allowModify,
                                allowAssemble = allowAssemble,
                                allowAnnotations = allowAnnotate,
                                allowFillForms = allowForms,
                            )
                        )
                    },
                    enabled = owner.length >= 4 && state?.isOwner != false,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.pdf_apply_security)) }
                if (state?.encrypted == true) {
                    HorizontalDivider()
                    OutlinedTextField(
                        removePassword,
                        { removePassword = it },
                        label = { Text(stringResource(R.string.pdf_owner_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { onRemove(removePassword) }, enabled = removePassword.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pdf_remove_security))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable private fun SecurityCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label) }
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
