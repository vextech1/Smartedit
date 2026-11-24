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
import com.vextech.editpro.core.image.Turn
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class EditorTool { NONE, CROP, ADJUST, ROTATE, FLIP, EXPORT }

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

    // ---- export sheet

    fun openExport() {
        if (_state.value.source == null || _state.value.saving) return
        val hasAlpha = sourceHasAlpha || _state.value.stack.done.any { it == EditOp.RemoveBackground }
        _state.update { it.copy(tool = EditorTool.EXPORT, hasAlpha = hasAlpha, export = null) }
        estimateExport(ExportRequest(ExportMath.startFormat(hasAlpha), ExportMath.DEFAULT_QUALITY, 0))
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

    private fun estimate(base: Bitmap, request: ExportRequest): ExportEstimate {
        val k = if (sourceLongSide > 0) minOf(SAVE_MAX_DIMENSION, sourceLongSide).toDouble() / minOf(PREVIEW_MAX_DIMENSION, sourceLongSide) else 1.0
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
        _state.update { it.copy(tool = EditorTool.NONE, crop = CropRect.FULL, adjust = AdjustParams(), turn = Turn.NONE, export = null, preview = committed ?: it.preview) }
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
                val saved = withContext(io) {
                    val full = processor.decodeOriented(file, SAVE_MAX_DIMENSION)
                    val rendered = render(full, ops)
                    val (w, h) = ExportMath.limitSide(rendered.width, rendered.height, request.longestSide)
                    val sized = if (w != rendered.width || h != rendered.height) Bitmap.createScaledBitmap(rendered, w, h, true) else rendered
                    try {
                        store.writeBitmap(
                            sized, store.editedDir, ExportMath.fileName(source.name, request.format),
                            compressFormat(request.format), if (request.format == ExportFormat.PNG) 100 else request.quality.coerceIn(ExportMath.MIN_QUALITY, 100),
                        )
                    } finally {
                        if (sized !== rendered) sized.recycle()
                        if (rendered !== full) rendered.recycle()
                        full.recycle()
                    }
                }
                val mime = request.format.mimeType
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
    private fun render(source: Bitmap, ops: List<EditOp>): Bitmap {
        var current = source
        for (op in ops) {
            val next = when (op) {
                is EditOp.Rotate -> processor.rotate(current, 90f * op.quarterTurns)
                is EditOp.Crop -> processor.crop(current, op.rect.left, op.rect.top, op.rect.right, op.rect.bottom)
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
