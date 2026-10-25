package com.vextech.smart_edit.feature.compressor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.compression.CompressionService
import com.vextech.smart_edit.core.di.IoDispatcher
import com.vextech.smart_edit.core.files.UriMetadataReader
import com.vextech.smart_edit.core.library.CompressionKind
import com.vextech.smart_edit.core.library.CompressionPolicies
import com.vextech.smart_edit.core.library.CompressionPreset
import com.vextech.smart_edit.core.library.CompressionVerdict
import com.vextech.smart_edit.core.library.PdfCompressionMode
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

data class CompressorFile(val uri: String, val name: String, val mimeType: String, val sizeBytes: Long, val kind: CompressionKind)

/** One finished file. [outputUri] is null when nothing was kept (error, or the result was not smaller). */
data class CompressorResult(
    val name: String,
    val originalBytes: Long,
    val compressedBytes: Long,
    val verdict: CompressionVerdict?,
    val outputName: String?,
    val outputUri: String?,
    val outputMime: String?,
    val error: UiText?,
)

data class CompressorUiState(
    val files: List<CompressorFile> = emptyList(),
    val preset: CompressionPreset = CompressionPreset.BALANCED,
    val pdfMode: PdfCompressionMode = PdfCompressionMode.STANDARD,
    val running: Boolean = false,
    val currentIndex: Int = 0,
    val currentName: String? = null,
    val progress: Float? = null,
    val results: List<CompressorResult> = emptyList(),
) {
    val hasPdf: Boolean get() = files.any { it.kind == CompressionKind.PDF }
    val finished: Boolean get() = !running && results.isNotEmpty()
    val keptResults: List<CompressorResult> get() = results.filter { it.outputUri != null }
    val totalSaved: Long get() = keptResults.sumOf { it.originalBytes - it.compressedBytes }
}

@HiltViewModel
class CompressorViewModel @Inject constructor(
    private val service: CompressionService,
    private val metadataReader: UriMetadataReader,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(CompressorUiState())
    val state: StateFlow<CompressorUiState> = _state.asStateFlow()
    private val events = UiEventChannel()
    val uiEvents = events.events
    private var job: Job? = null
    private var initialHandled = false

    fun preselect(uri: Uri) {
        if (initialHandled) return
        initialHandled = true
        addFiles(listOf(uri))
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.running) return
        viewModelScope.launch {
            val read = withContext(io) {
                uris.mapNotNull { uri ->
                    metadataReader.persistReadPermission(uri)
                    runCatching { metadataReader.read(uri) }.getOrNull()?.let { meta ->
                        CompressorFile(uri.toString(), meta.name, meta.mimeType, meta.size, CompressionPolicies.kindOf(meta.name, meta.mimeType))
                    }
                }
            }
            val supported = read.filter { it.kind != CompressionKind.UNSUPPORTED }
            val skipped = uris.size - supported.size
            _state.update { current ->
                val known = current.files.map { it.uri }.toSet()
                val merged = (current.files + supported.filter { it.uri !in known }).take(MAX_FILES)
                current.copy(files = merged, results = emptyList())
            }
            if (skipped > 0) events.message(UiText.plural(R.plurals.compressor_skipped_unsupported, skipped, skipped))
            if (_state.value.files.size >= MAX_FILES && supported.isNotEmpty()) {
                events.message(UiText.res(R.string.compressor_limit_reached, MAX_FILES))
            }
        }
    }

    fun remove(file: CompressorFile) { if (!_state.value.running) _state.update { it.copy(files = it.files - file, results = emptyList()) } }
    fun setPreset(preset: CompressionPreset) { _state.update { it.copy(preset = preset) } }
    fun setPdfMode(mode: PdfCompressionMode) { _state.update { it.copy(pdfMode = mode) } }

    /** Starts over with an empty selection after results were shown. */
    fun reset() { if (!_state.value.running) _state.value = CompressorUiState(preset = _state.value.preset, pdfMode = _state.value.pdfMode) }

    fun cancel() { job?.cancel() }

    fun start() {
        val snapshot = _state.value
        if (snapshot.running || snapshot.files.isEmpty()) return
        job = viewModelScope.launch {
            _state.update { it.copy(running = true, results = emptyList(), progress = null, currentIndex = 0) }
            val collected = ArrayList<CompressorResult>()
            try {
                snapshot.files.forEachIndexed { index, file ->
                    _state.update { it.copy(currentIndex = index, currentName = file.name, progress = null) }
                    collected += compressOne(file, snapshot)
                    _state.update { it.copy(results = collected.toList()) }
                }
            } catch (cancelled: CancellationException) {
                events.message(UiText.res(R.string.compressor_cancelled))
            } finally {
                _state.update { it.copy(running = false, currentName = null, progress = null, results = collected.toList()) }
            }
        }
    }

    private suspend fun compressOne(file: CompressorFile, snapshot: CompressorUiState): CompressorResult = try {
        val result = service.compress(Uri.parse(file.uri), file.name, file.mimeType, snapshot.preset, snapshot.pdfMode) { progress ->
            _state.update { it.copy(progress = progress) }
        }
        CompressorResult(
            name = file.name, originalBytes = result.originalBytes, compressedBytes = result.compressedBytes, verdict = result.verdict,
            outputName = result.output?.name, outputUri = result.output?.toURI()?.toString(), outputMime = result.outputMimeType, error = null,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        val protectedPdf = error::class.simpleName == "InvalidPasswordException"
        CompressorResult(file.name, file.sizeBytes, file.sizeBytes, null, null, null, null,
            UiText.res(if (protectedPdf) R.string.compressor_error_protected else R.string.compressor_error_failed))
    }

    private companion object { const val MAX_FILES = 20 }
}
