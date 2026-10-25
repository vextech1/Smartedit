package com.vextech.smart_edit.feature.ocr

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.di.IoDispatcher
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.files.UriMetadataReader
import com.vextech.smart_edit.core.library.OcrPolicies
import com.vextech.smart_edit.core.library.OcrScript
import com.vextech.smart_edit.core.library.OcrStats
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.core.ocr.OcrService
import com.vextech.smart_edit.core.processing.ImageProcessor
import com.vextech.smart_edit.core.repository.NotesRepository
import com.vextech.smart_edit.core.repository.SmartEditRepository
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OcrSource(val uri: String, val name: String)

data class OcrUiState(
    val source: OcrSource? = null,
    val preview: Bitmap? = null,
    val script: OcrScript = OcrScript.LATIN,
    val running: Boolean = false,
    val hasResult: Boolean = false,
    val detectedLanguage: String? = null,
    val suggestedScript: OcrScript? = null,
    val stats: OcrStats = OcrStats(0, 0, 0),
)

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val ocr: OcrService,
    private val notes: NotesRepository,
    private val repository: SmartEditRepository,
    private val store: DocumentStore,
    private val metadataReader: UriMetadataReader,
    private val processor: ImageProcessor,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()
    private val events = UiEventChannel()
    val uiEvents = events.events

    /** The recognized text. It lives in Compose state so editing never waits on a flow round trip. */
    var text by mutableStateOf("")
        private set

    private var job: Job? = null
    private var initialHandled = false

    fun preselect(uri: Uri) {
        if (initialHandled) return
        initialHandled = true
        pick(uri)
    }

    fun pick(uri: Uri) {
        if (_state.value.running) return
        viewModelScope.launch {
            try {
                val (source, preview) = withContext(io) {
                    metadataReader.persistReadPermission(uri)
                    val meta = metadataReader.read(uri)
                    val staged = store.stageContent(uri, ".img")
                    try {
                        OcrSource(uri.toString(), meta.name) to processor.decodeOriented(staged, PREVIEW_MAX_DIMENSION)
                    } finally {
                        staged.delete()
                    }
                }
                text = ""
                _state.update { OcrUiState(source = source, preview = preview, script = it.script) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                events.message(UiText.res(R.string.ocr_failed))
            }
        }
    }

    fun onText(value: String) {
        text = value
        _state.update { it.copy(stats = OcrPolicies.stats(value)) }
    }

    fun setScript(script: OcrScript) { if (!_state.value.running) _state.update { it.copy(script = script) } }

    fun extract() {
        val current = _state.value
        val source = current.source ?: return
        if (current.running) return
        job = viewModelScope.launch {
            _state.update { it.copy(running = true, hasResult = false, suggestedScript = null) }
            try {
                val result = ocr.recognize(Uri.parse(source.uri), current.script)
                val cleaned = OcrPolicies.clean(result.text)
                text = cleaned
                _state.update {
                    it.copy(
                        hasResult = true,
                        detectedLanguage = result.language,
                        suggestedScript = OcrPolicies.suggestedRetry(current.script, result.language),
                        stats = OcrPolicies.stats(cleaned),
                    )
                }
                if (cleaned.isBlank()) events.message(UiText.res(R.string.ocr_no_text))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                events.message(UiText.res(R.string.ocr_failed))
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }

    fun cancel() { job?.cancel() }

    fun retryWithSuggestion() {
        val suggestion = _state.value.suggestedScript ?: return
        setScript(suggestion)
        extract()
    }

    fun copied() = events.message(UiText.res(R.string.ocr_copied))

    fun saveAsNote() = guarded {
        val saved = notes.save(
            id = null, title = OcrPolicies.suggestedTitle(text), content = text, tagsRaw = TAG_OCR, recordActivity = true,
        )
        events.message(UiText.res(if (saved != null) R.string.ocr_saved_note else R.string.ocr_no_text))
    }

    fun saveAsTextFile() = guarded {
        val name = OcrPolicies.textFileName(OcrPolicies.suggestedTitle(text), System.currentTimeMillis().toString())
        val file = withContext(io) {
            val target = store.uniqueFile(store.documentsDir, name)
            target.writeText(text)
            target
        }
        val entity = repository.registerFile(file, ResourceType.DOCUMENT, "text/plain")
        repository.recordHistory(HistoryAction.CREATED, "Created ${file.name}", ResourceType.DOCUMENT, entity.id, file.toURI().toString(), file.name)
        events.message(UiText.res(R.string.ocr_saved_file, file.name))
    }

    private fun guarded(block: suspend () -> Unit) {
        if (text.isBlank()) {
            events.message(UiText.res(R.string.ocr_no_text))
            return
        }
        viewModelScope.launch {
            try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { events.message(UiText.res(R.string.ocr_save_failed)) }
        }
    }

    private companion object {
        const val PREVIEW_MAX_DIMENSION = 1024
        const val TAG_OCR = "ocr"
    }
}
