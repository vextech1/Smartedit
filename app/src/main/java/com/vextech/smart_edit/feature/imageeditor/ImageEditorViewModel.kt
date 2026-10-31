package com.vextech.smart_edit.feature.imageeditor

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.di.IoDispatcher
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.files.UriMetadataReader
import com.vextech.smart_edit.core.library.AdjustParams
import com.vextech.smart_edit.core.library.CropRect
import com.vextech.smart_edit.core.library.EditOp
import com.vextech.smart_edit.core.library.EditPolicies
import com.vextech.smart_edit.core.library.EditStack
import com.vextech.smart_edit.core.library.ImageFilter
import com.vextech.smart_edit.core.library.ImageOutput
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.core.processing.ImageProcessor
import com.vextech.smart_edit.core.repository.SmartEditRepository
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class EditorTool { NONE, CROP, ADJUST }

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
    private val repository: SmartEditRepository,
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
        _state.update { it.copy(stack = next, tool = EditorTool.NONE, crop = CropRect.FULL, adjust = AdjustParams(), saved = null) }
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
            val live = withContext(io) { processor.adjust(base, params.brightness, params.contrast, params.saturation) }
            _state.update { if (it.tool == EditorTool.ADJUST) it.copy(preview = live) else it }
        }
    }

    fun applyAdjust() = commit(EditOp.Adjust(_state.value.adjust))

    fun closeTool() {
        liveJob?.cancel()
        adjustBase = null
        _state.update { it.copy(tool = EditorTool.NONE, crop = CropRect.FULL, adjust = AdjustParams(), preview = committed ?: it.preview) }
    }

    // ---- saving

    fun save() {
        val current = _state.value
        val file = staged ?: return
        val source = current.source ?: return
        if (current.stack.isEmpty || current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                val ops = current.stack.done
                val output = EditPolicies.outputFormat(sourceHasAlpha, ops)
                val saved = withContext(io) {
                    val full = processor.decodeOriented(file, SAVE_MAX_DIMENSION)
                    val rendered = render(full, ops)
                    try {
                        store.writeBitmap(
                            rendered, store.editedDir, EditPolicies.outputName(source.name, output),
                            if (output == ImageOutput.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, JPEG_QUALITY,
                        )
                    } finally {
                        if (rendered !== full) rendered.recycle()
                        full.recycle()
                    }
                }
                val entity = repository.registerFile(saved, ResourceType.IMAGE, output.mimeType)
                repository.recordHistory(HistoryAction.EDITED, "Edited ${saved.name}", ResourceType.IMAGE, entity.id, saved.toURI().toString(), saved.name)
                _state.update { it.copy(saved = SavedImage(saved.name, saved.toURI().toString(), output.mimeType, saved.length())) }
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
                is EditOp.Adjust -> processor.adjust(current, op.params.brightness, op.params.contrast, op.params.saturation)
                is EditOp.Filter -> when (op.filter) {
                    ImageFilter.GRAYSCALE -> processor.grayscale(current)
                    ImageFilter.BLACK_WHITE -> processor.blackAndWhite(current)
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
