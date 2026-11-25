package com.vextech.editpro.feature.imageeditor

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.editpro.R
import com.vextech.editpro.core.di.IoDispatcher
import com.vextech.editpro.core.files.DocumentStore
import com.vextech.editpro.core.files.UriMetadataReader
import com.vextech.editpro.core.library.AdjustParams
import com.vextech.editpro.core.library.CropRect
import com.vextech.editpro.core.library.EditOp
import com.vextech.editpro.core.library.EditPolicies
import com.vextech.editpro.core.library.EditStack
import com.vextech.editpro.core.library.ImageFilter
import com.vextech.editpro.core.library.ImageOutput
import com.vextech.editpro.core.model.HistoryAction
import com.vextech.editpro.core.model.ResourceType
import com.vextech.editpro.core.processing.ImageProcessor
import com.vextech.editpro.core.repository.EditProRepository
import com.vextech.editpro.core.ui.UiEventChannel
import com.vextech.editpro.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import com.vextech.editpro.core.image.ExportEstimate
import com.vextech.editpro.core.image.ExportFormat
import com.vextech.editpro.core.image.ExportMath
import com.vextech.editpro.core.image.ExportRequest
import com.vextech.editpro.core.image.FitMode
import com.vextech.editpro.core.image.ResizeFit
import com.vextech.editpro.core.image.ResizeSpec
import com.vextech.editpro.core.image.RoundShapes
import com.vextech.editpro.core.image.RoundSpec
import com.vextech.editpro.core.image.Turn
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class EditorTool { NONE, CROP, ADJUST, ROTATE, FLIP, EXPORT, RESIZE, ROUND, PRINT_SHEET }

data class EditorSource(val uri: String, val name: String)

data class SavedImage(val name: String, val uri: String, val mimeType: String, val sizeBytes: Long)

data class ImageEditorUiState(
    val loading: Boolean = false,
    val source: EditorSource? = null,
    val preview: Bitmap? = null,
    val stack: EditStack = EditStack(),
    val tool: EditorTool = EditorTool.NONE,
    val crop: CropRect = CropRect.FULL,
    val adjust: AdjustParams = AdjustParams(),
    /** The unapplied turn and flip of the Rotate and Flip sheets. */
    val turn: Turn = Turn.NONE,
    /** Size and file size the Export sheet expects for its current choices, or null while working it out. */
    val export: ExportEstimate? = null,
    /** True when the picture has see-through pixels, so JPG is not offered. */
    val hasAlpha: Boolean = false,
    /** Size the picture will have at full quality, in pixels, which the Resize sheet starts from. */
    val fullSize: Pair<Int, Int>? = null,
    /** File-size limit in KB from a form size chosen in Resize, which Export keeps to; null for none. */
    val exportLimitKB: Int? = null,
    val busy: Boolean = false,
    val saving: Boolean = false,
    val saved: SavedImage? = null,
)

/**
 * Non-destructive editor. Every edit is an operation on [EditStack]; the picture shown is the original
 * (kept at preview size) with the operations replayed. Saving replays them on a larger copy of the source.
 */
@HiltViewModel
class ImageEditorViewModel @Inject constructor(
    private val store: DocumentStore,
    private val processor: ImageProcessor,
    private val repository: EditProRepository,
    private val metadataReader: UriMetadataReader,
    private val pdf: com.vextech.editpro.core.pdf.PdfEngine,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(ImageEditorUiState())
    val state: StateFlow<ImageEditorUiState> = _state.asStateFlow()
    private val events = UiEventChannel()
    val uiEvents = events.events

    private var original: Bitmap? = null
    private var staged: File? = null
    private var sourceHasAlpha = false
    private var committed: Bitmap? = null
    private var adjustBase: Bitmap? = null
    private var renderJob: Job? = null
    private var liveJob: Job? = null
    private var estimateJob: Job? = null
    private var sourceLongSide = 0
    private var initialHandled = false

    fun preselect(uri: Uri) {
        if (initialHandled) return
        initialHandled = true
        load(uri)
    }

    fun load(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImageEditorUiState(loading = true)
            try {
                val (name, file, bitmap) = withContext(io) {
                    metadataReader.persistReadPermission(uri)
                    val meta = metadataReader.read(uri)
                    val copy = store.stageContent(uri, ".img")
                    Triple(meta.name, copy, processor.decodeOriented(copy, PREVIEW_MAX_DIMENSION))
                }
                staged?.delete()
                staged = file
                original = bitmap
                committed = bitmap
                sourceHasAlpha = bitmap.hasAlpha()
                sourceLongSide = withContext(io) {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                    maxOf(bounds.outWidth, bounds.outHeight)
                }
                _state.value = ImageEditorUiState(source = EditorSource(uri.toString(), name), preview = bitmap)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = ImageEditorUiState()
                events.message(UiText.res(R.string.ie_load_failed))
            }
        }
    }

    // ---- committed edits

    fun rotateLeft() = commit(EditOp.Rotate(-1))
    fun rotateRight() = commit(EditOp.Rotate(1))
    fun removeBackground() = commit(EditOp.RemoveBackground)
    fun setFilter(filter: ImageFilter) = commit(EditOp.Filter(filter))

    fun undo() = replaceStack(_state.value.stack.undo())
    fun redo() = replaceStack(_state.value.stack.redo())

    private fun commit(op: EditOp) = replaceStack(_state.value.stack.push(op))

    private fun replaceStack(next: EditStack) {
        if (_state.value.source == null || next === _state.value.stack) return
        liveJob?.cancel()
        estimateJob?.cancel()
        _state.update { it.copy(stack = next, tool = EditorTool.NONE, crop = CropRect.FULL, adjust = AdjustParams(), turn = Turn.NONE, export = null, saved = null) }
        scheduleRender()
    }

    private fun scheduleRender() {
        val source = original ?: return
        val ops = _state.value.stack.done
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val rendered = withContext(io) { render(source, ops) }
                committed = rendered
                _state.update { it.copy(preview = rendered, busy = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(busy = false) }
                events.message(UiText.res(R.string.ie_edit_failed))
            }
        }
    }

    // ---- tools with live parameters

    fun openCrop() { _state.update { it.copy(tool = EditorTool.CROP, crop = CropRect.FULL) } }

    fun setCrop(left: Float, top: Float, right: Float, bottom: Float) {
        _state.update { it.copy(crop = CropRect.of(left, top, right, bottom)) }
    }

    fun applyCrop() = commit(EditOp.Crop(_state.value.crop))

    fun openAdjust() {
        val source = original ?: return
        val ops = _state.value.stack.done
        val replacing = ops.lastOrNull() as? EditOp.Adjust
        val opsBefore = if (replacing != null) ops.dropLast(1) else ops
        viewModelScope.launch {
            _state.update { it.copy(tool = EditorTool.ADJUST, adjust = replacing?.params ?: AdjustParams()) }
            try {
                adjustBase = withContext(io) { render(source, opsBefore) }
                previewAdjust()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(tool = EditorTool.NONE) }
            }
        }
    }

    fun setAdjust(params: AdjustParams) {
        _state.update { it.copy(adjust = params.coerced()) }
        previewAdjust()
    }

    private fun previewAdjust() {
        val base = adjustBase ?: return
        val params = _state.value.adjust
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            delay(LIVE_PREVIEW_DELAY_MS)
            val live = withContext(io) { processor.adjust(base, params.brightness, params.contrast, params.saturation, params.temperature, params.tint) }
            _state.update { if (it.tool == EditorTool.ADJUST) it.copy(preview = live) else it }
        }
    }

    fun applyAdjust() = commit(EditOp.Adjust(_state.value.adjust))

    // ---- rotate and flip sheets

    fun openTurn(tool: EditorTool) {
        require(tool == EditorTool.ROTATE || tool == EditorTool.FLIP)
        _state.update { it.copy(tool = tool, turn = Turn.NONE) }
    }

    /** Shows [turn] on the picture at once; nothing is recorded until [applyTurn]. */
    fun setTurn(turn: Turn) {
        _state.update { it.copy(turn = turn) }
        val base = committed ?: return
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            val shown = withContext(io) { processor.turn(base, turn) }
            _state.update { if (it.tool == EditorTool.ROTATE || it.tool == EditorTool.FLIP) it.copy(preview = shown) else it }
        }
    }

    /** Records the pending turn: mirrors first, then the quarter turns, as one undo step per edit. */
    fun applyTurn() {
        val turn = _state.value.turn
        var next = _state.value.stack
        if (turn.flipH) next = next.push(EditOp.Flip(horizontal = true))
        if (turn.flipV) next = next.push(EditOp.Flip(horizontal = false))
        next = next.push(EditOp.Rotate(turn.turns))
        replaceStack(next)
    }

    // ---- resize sheet

    fun openResize() {
        val base = committed ?: return
        _state.update { it.copy(tool = EditorTool.RESIZE, fullSize = fullSizeOf(base)) }
    }

    private fun fullSizeOf(base: Bitmap): Pair<Int, Int> {
        val k = fullScale(base)
        return maxOf(1, (base.width * k).roundToInt()) to maxOf(1, (base.height * k).roundToInt())
    }

    /** Shows [spec] on the picture a moment after the last change; null puts the unresized picture back. Nothing is recorded until [applyResize]. */
    fun previewResize(spec: ResizeSpec?) {
        val base = committed ?: return
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            delay(LIVE_PREVIEW_DELAY_MS)
            val shown = withContext(io) {
                if (spec == null) base else {
                    val (w, h) = previewDims(spec.width, spec.height)
                    processor.resizeFit(base, w, h, spec.mode, spec.zoom, spec.panX, spec.panY, spec.background)
                }
            }
            _state.update { if (it.tool == EditorTool.RESIZE) it.copy(preview = shown) else it }
        }
    }

    fun applyResize(spec: ResizeSpec) = commit(EditOp.Resize(spec))

    // ---- round sheet

    fun openRound() {
        if (committed == null) return
        _state.update { it.copy(tool = EditorTool.ROUND) }
    }

    /** Shows [spec] on the picture a moment after the last change; null puts the unrounded picture back. Nothing is recorded until [applyRound]. */
    fun previewRound(spec: RoundSpec?) {
        val base = committed ?: return
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            delay(LIVE_PREVIEW_DELAY_MS)
            val shown = withContext(io) { if (spec == null) base else roundOf(base, spec) }
            _state.update { if (it.tool == EditorTool.ROUND) it.copy(preview = shown) else it }
        }
    }

    /** Size of the picture Round starts from (the edits so far), in preview pixels. */
    fun roundBaseSize(): Pair<Int, Int>? = committed?.let { it.width to it.height }

    fun applyRound(spec: RoundSpec) = commit(EditOp.Round(spec))

    private fun roundOf(source: Bitmap, spec: RoundSpec): Bitmap {
        val (w, h) = RoundShapes.outputSize(source.width, source.height, spec.ratio, maxOf(source.width, source.height))
        return processor.roundCorners(source, w, h, spec.radiusPct, spec.zoom, spec.panX, spec.panY)
    }

    // ---- photo sheet

    fun openPrintSheet() {
        if (committed == null || _state.value.saving) return
        _state.update { it.copy(tool = EditorTool.PRINT_SHEET) }
    }

    /** Makes the one-page PDF: the picture, cropped to the photo size, repeated on the paper. */
    fun savePrintSheet(form: com.vextech.editpro.core.image.PrintSheetForm) {
        val file = staged ?: return
        val source = _state.value.source ?: return
        val (cellW, cellH) = form.cellMm() ?: return
        val layout = form.layout()?.takeIf { it.count > 0 } ?: return
        val photos = com.vextech.editpro.core.image.PrintSheets.photosOn(layout, form.copies)
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, tool = EditorTool.NONE) }
            try {
                val ops = _state.value.stack.done
                val saved = withContext(io) {
                    val full = processor.decodeOriented(file, SAVE_MAX_DIMENSION)
                    val rendered = render(full, ops, exact = true)
                    // The photo is cut to the photo's shape at 300 dpi, on white where it has see-through corners.
                    val dpi = com.vextech.editpro.core.image.SizeMath.DEFAULT_DPI
                    val pxW = com.vextech.editpro.core.image.SizeMath.toPx(cellW, com.vextech.editpro.core.image.SizeUnit.MM, dpi)
                    val pxH = com.vextech.editpro.core.image.SizeMath.toPx(cellH, com.vextech.editpro.core.image.SizeUnit.MM, dpi)
                    val cell = processor.resizeFit(rendered, pxW, pxH, FitMode.FILL, 1.0, 0.0, 0.0, android.graphics.Color.WHITE)
                    val flat = processor.onWhite(cell)
                    try {
                        val out = java.io.File(store.editedDir, "${source.name.substringBeforeLast('.')}-sheet-${form.paperId}.pdf")
                        pdf.photoSheet(flat, layout, cellW, cellH, photos, form.guides, out)
                        out
                    } finally {
                        if (flat !== cell) flat.recycle()
                        cell.recycle()
                        if (rendered !== full) rendered.recycle()
                        full.recycle()
                    }
                }
                val mime = "application/pdf"
                val entity = repository.registerFile(saved, ResourceType.PDF, mime)
                repository.recordHistory(HistoryAction.EDITED, "Made print sheet ${saved.name}", ResourceType.PDF, entity.id, saved.toURI().toString(), saved.name)
                _state.update { it.copy(saved = SavedImage(saved.name, saved.toURI().toString(), mime, saved.length())) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                events.message(UiText.res(R.string.ie_save_failed))
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    // ---- export sheet

    fun openExport() {
        if (_state.value.source == null || _state.value.saving) return
        val hasAlpha = sourceHasAlpha || EditPolicies.makesTransparency(_state.value.stack.done)
        val limit = (_state.value.stack.done.lastOrNull { it is EditOp.Resize } as? EditOp.Resize)?.spec?.maxBytes?.let { (it / 1024).toInt() }
        _state.update { it.copy(tool = EditorTool.EXPORT, hasAlpha = hasAlpha, exportLimitKB = limit, export = null) }
        estimateExport(ExportRequest(if (limit != null) ExportFormat.JPEG else ExportMath.startFormat(hasAlpha), ExportMath.DEFAULT_QUALITY, 0))
    }

    /** Works out the size and about how large the file will be for [request], a moment after the last change. */
    fun estimateExport(request: ExportRequest) {
        val base = committed ?: return
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            delay(150)
            val estimate = withContext(io) { estimate(base, request) }
            _state.update { if (it.tool == EditorTool.EXPORT) it.copy(export = estimate) else it }
        }
    }

    /** Pixels at full quality per pixel of the preview: a chosen resize fixes the long side, otherwise it is the save size over the preview size. */
    private fun fullScale(base: Bitmap): Double {
        val resize = _state.value.stack.done.lastOrNull { it is EditOp.Resize } as? EditOp.Resize
        if (resize != null) return maxOf(resize.spec.width, resize.spec.height).toDouble() / maxOf(base.width, base.height)
        return if (sourceLongSide > 0) minOf(SAVE_MAX_DIMENSION, sourceLongSide).toDouble() / minOf(PREVIEW_MAX_DIMENSION, sourceLongSide) else 1.0
    }

    private fun previewDims(w: Int, h: Int): Pair<Int, Int> {
        val k = minOf(1.0, PREVIEW_MAX_DIMENSION.toDouble() / maxOf(w, h))
        return maxOf(1, (w * k).roundToInt()) to maxOf(1, (h * k).roundToInt())
    }

    private fun estimate(base: Bitmap, request: ExportRequest): ExportEstimate {
        val k = fullScale(base)
        val (fullW, fullH) = (base.width * k).roundToInt().coerceAtLeast(1) to (base.height * k).roundToInt().coerceAtLeast(1)
        val (finalW, finalH) = ExportMath.limitSide(fullW, fullH, request.longestSide)
        val (sampleW, sampleH) = ExportMath.sampleSize(finalW, finalH, PREVIEW_MAX_DIMENSION)
        val sample = if (sampleW == base.width && sampleH == base.height) base else Bitmap.createScaledBitmap(base, sampleW, sampleH, true)
        val bytes = try {
            val out = java.io.ByteArrayOutputStream()
            sample.compress(compressFormat(request.format), request.quality.coerceIn(ExportMath.MIN_QUALITY, 100), out)
            out.size().toLong()
        } finally {
            if (sample !== base) sample.recycle()
        }
        return ExportEstimate(finalW, finalH, ExportMath.scaleBytes(bytes, sampleW.toLong() * sampleH, finalW.toLong() * finalH))
    }

    @Suppress("DEPRECATION")
    private fun compressFormat(format: ExportFormat): Bitmap.CompressFormat = when (format) {
        ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        ExportFormat.WEBP -> if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    }

    fun closeTool() {
        liveJob?.cancel()
        estimateJob?.cancel()
        adjustBase = null
        _state.update { it.copy(tool = EditorTool.NONE, crop = CropRect.FULL, adjust = AdjustParams(), turn = Turn.NONE, export = null, exportLimitKB = null, preview = committed ?: it.preview) }
    }

    // ---- saving

    /** Saves the edited picture in the format, quality and size chosen in the Export sheet. */
    fun save(request: ExportRequest) {
        val current = _state.value
        val file = staged ?: return
        val source = current.source ?: return
        if (current.saving) return
        estimateJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(saving = true, tool = EditorTool.NONE, export = null) }
            try {
                val ops = current.stack.done
                val limit = (ops.lastOrNull { it is EditOp.Resize } as? EditOp.Resize)?.spec?.maxBytes
                // A form size with a file-size limit is always saved as JPG, at the highest quality that stays under the limit.
                val format = if (limit != null) ExportFormat.JPEG else request.format
                val saved = withContext(io) {
                    val full = processor.decodeOriented(file, SAVE_MAX_DIMENSION)
                    val rendered = render(full, ops, exact = true)
                    val (w, h) = ExportMath.limitSide(rendered.width, rendered.height, request.longestSide)
                    val scaled = if (w != rendered.width || h != rendered.height) Bitmap.createScaledBitmap(rendered, w, h, true) else rendered
                    val sized = if (format == ExportFormat.JPEG) processor.onWhite(scaled) else scaled
                    try {
                        val quality = when {
                            format == ExportFormat.PNG -> 100
                            limit != null -> ResizeFit.bestQuality(limit) { q ->
                                java.io.ByteArrayOutputStream().also { sized.compress(Bitmap.CompressFormat.JPEG, q, it) }.size().toLong()
                            }.first
                            else -> request.quality.coerceIn(ExportMath.MIN_QUALITY, 100)
                        }
                        store.writeBitmap(sized, store.editedDir, ExportMath.fileName(source.name, format), compressFormat(format), quality)
                    } finally {
                        if (sized !== scaled) sized.recycle()
                        if (scaled !== rendered) scaled.recycle()
                        if (rendered !== full) rendered.recycle()
                        full.recycle()
                    }
                }
                val mime = format.mimeType
                val entity = repository.registerFile(saved, ResourceType.IMAGE, mime)
                repository.recordHistory(HistoryAction.EDITED, "Edited ${saved.name}", ResourceType.IMAGE, entity.id, saved.toURI().toString(), saved.name)
                _state.update { it.copy(saved = SavedImage(saved.name, saved.toURI().toString(), mime, saved.length())) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                events.message(UiText.res(R.string.ie_save_failed))
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    /** The untouched preview, shown while the user holds "compare". */
    fun originalPreview(): Bitmap? = original

    fun dismissSaved() { _state.update { it.copy(saved = null) } }

    /** Replays [ops] on [source]. The source is never modified or recycled; intermediate bitmaps are. */
    /** [exact] is true when saving: a resize then makes exactly the size asked for. In the preview a resize keeps its shape but is capped at preview size. */
    private fun render(source: Bitmap, ops: List<EditOp>, exact: Boolean = false): Bitmap {
        var current = source
        for (op in ops) {
            val next = when (op) {
                is EditOp.Rotate -> processor.rotate(current, 90f * op.quarterTurns)
                is EditOp.Crop -> processor.crop(current, op.rect.left, op.rect.top, op.rect.right, op.rect.bottom)
                is EditOp.Resize -> {
                    val (w, h) = if (exact) op.spec.width to op.spec.height else previewDims(op.spec.width, op.spec.height)
                    processor.resizeFit(current, w, h, op.spec.mode, op.spec.zoom, op.spec.panX, op.spec.panY, op.spec.background)
                }
                is EditOp.Round -> roundOf(current, op.spec)
                is EditOp.Flip -> if (op.horizontal) processor.flipHorizontal(current) else processor.flipVertical(current)
                is EditOp.Adjust -> processor.adjust(current, op.params.brightness, op.params.contrast, op.params.saturation, op.params.temperature, op.params.tint)
                is EditOp.Filter -> when (op.filter) {
                    ImageFilter.GRAYSCALE -> processor.grayscale(current)
                    ImageFilter.BLACK_WHITE -> processor.blackAndWhite(current)
                    ImageFilter.CLEAN_DOCUMENT -> processor.cleanDocument(current)
                    ImageFilter.NONE -> current
                }
                EditOp.RemoveBackground -> processor.removePlainBackground(current)
            }
            if (current !== source && next !== current) current.recycle()
            current = next
        }
        return current
    }

    override fun onCleared() {
        staged?.delete()
        super.onCleared()
    }

    private companion object {
        const val PREVIEW_MAX_DIMENSION = 1600
        const val SAVE_MAX_DIMENSION = 4096
        const val JPEG_QUALITY = 92
        const val LIVE_PREVIEW_DELAY_MS = 30L
    }
}
