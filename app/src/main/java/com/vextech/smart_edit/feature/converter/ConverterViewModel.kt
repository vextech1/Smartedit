package com.vextech.smart_edit.feature.converter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.di.IoDispatcher
import com.vextech.smart_edit.core.files.UriMetadataReader
import com.vextech.smart_edit.feature.converter.data.ConverterRepository
import com.vextech.smart_edit.feature.converter.domain.ConversionJob
import com.vextech.smart_edit.feature.converter.domain.ConversionRequest
import com.vextech.smart_edit.feature.converter.domain.ConverterFailure
import com.vextech.smart_edit.feature.converter.domain.ConverterMessageKey
import com.vextech.smart_edit.feature.converter.domain.ConverterOptions
import com.vextech.smart_edit.feature.converter.domain.ConverterOutput
import com.vextech.smart_edit.feature.converter.domain.ConverterSource
import com.vextech.smart_edit.feature.converter.domain.ConverterSourceKind
import com.vextech.smart_edit.feature.converter.domain.ConverterTarget
import com.vextech.smart_edit.feature.converter.domain.ImageTargetFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConverterUiState(
    val sourceKind: ConverterSourceKind? = null,
    val sources: List<ConverterSource> = emptyList(),
    val target: ConverterTarget? = null,
    val quality: Int = 92,
    val dpi: Int = 144,
    val pageRange: String = "all",
    val pdfPassword: String = "",
    val busy: Boolean = false,
    val progress: Float = 0f,
    val outputs: List<ConverterOutput> = emptyList(),
    val message: ConverterMessageKey? = null,
)

@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val repository: ConverterRepository,
    private val metadataReader: UriMetadataReader,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(ConverterUiState())
    val state: StateFlow<ConverterUiState> = _state.asStateFlow()

    val jobs: StateFlow<List<ConversionJob>> = repository.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var conversionJob: Job? = null

    private var initialHandled = false

    /** Loads a file chosen elsewhere (for example the Files menu). Runs once per screen instance. */
    fun preselect(uri: Uri) {
        if (initialHandled) return
        initialHandled = true
        if (uri.lastPathSegment.orEmpty().endsWith(".pdf", ignoreCase = true)) selectPdf(uri) else selectImages(listOf(uri))
    }

    fun selectImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val sources = withContext(io) {
                uris.mapNotNull { uri ->
                    metadataReader.persistReadPermission(uri)
                    runCatching { metadataReader.read(uri) }.getOrNull()?.let { meta ->
                        if (meta.mimeType.startsWith("image/")) {
                            ConverterSource(uri.toString(), meta.name, meta.mimeType, meta.size)
                        } else null
                    }
                }
            }
            _state.value = if (sources.isEmpty()) {
                _state.value.copy(message = ConverterMessageKey.IMAGE_SOURCE_REQUIRED)
            } else {
                ConverterUiState(
                    sourceKind = ConverterSourceKind.IMAGES,
                    sources = sources,
                    target = ConverterTarget.PDF,
                )
            }
        }
    }

    fun selectPdf(uri: Uri) {
        viewModelScope.launch {
            val source = runCatching {
                withContext(io) {
                    metadataReader.persistReadPermission(uri)
                    val meta = metadataReader.read(uri)
                    ConverterSource(uri.toString(), meta.name, meta.mimeType, meta.size)
                }
            }.getOrNull()
            if (source == null || (source.mimeType != "application/pdf" && !source.displayName.endsWith(".pdf", true))) {
                _state.value = _state.value.copy(message = ConverterMessageKey.PDF_SOURCE_REQUIRED)
            } else {
                _state.value = ConverterUiState(
                    sourceKind = ConverterSourceKind.PDF,
                    sources = listOf(source),
                    target = ConverterTarget.PDF_IMAGES_ZIP,
                )
            }
        }
    }

    fun setTarget(target: ConverterTarget) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(target = target, outputs = emptyList(), message = null)
    }

    fun setQuality(value: Int) {
        _state.value = _state.value.copy(quality = value.coerceIn(10, 100))
    }

    fun setDpi(value: Int) {
        _state.value = _state.value.copy(dpi = value.coerceIn(72, 300))
    }

    fun setPageRange(value: String) {
        _state.value = _state.value.copy(pageRange = value)
    }

    fun setPdfPassword(value: String) {
        _state.value = _state.value.copy(pdfPassword = value)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun clearSelection() {
        if (_state.value.busy) return
        _state.value = ConverterUiState()
    }

    fun cancel() {
        conversionJob?.cancel()
    }

    fun convert() {
        if (_state.value.busy) return
        val request = buildRequest() ?: run {
            _state.value = _state.value.copy(message = ConverterMessageKey.SOURCE_REQUIRED)
            return
        }
        conversionJob = viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, progress = 0f, outputs = emptyList(), message = null)
            try {
                val result = repository.convert(request) { progress ->
                    _state.value = _state.value.copy(progress = progress.coerceIn(0f, 1f))
                }
                _state.value = _state.value.copy(
                    busy = false,
                    progress = 1f,
                    outputs = result.outputs,
                    message = ConverterMessageKey.CONVERSION_COMPLETE,
                )
            } catch (_: CancellationException) {
                _state.value = _state.value.copy(
                    busy = false,
                    progress = 0f,
                    message = ConverterMessageKey.CONVERSION_CANCELLED,
                )
            } catch (failure: ConverterFailure) {
                _state.value = _state.value.copy(busy = false, progress = 0f, message = failure.key)
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    busy = false,
                    progress = 0f,
                    message = ConverterMessageKey.CONVERSION_FAILED,
                )
            } finally {
                conversionJob = null
            }
        }
    }

    private fun buildRequest(): ConversionRequest? {
        val current = _state.value
        val target = current.target ?: return null
        val options = ConverterOptions(
            quality = current.quality,
            dpi = current.dpi,
            pageRange = current.pageRange,
            pdfPassword = current.pdfPassword,
        )
        return when (current.sourceKind) {
            ConverterSourceKind.IMAGES -> when (target) {
                ConverterTarget.PDF -> ConversionRequest.ImagesToPdf(current.sources, options)
                ConverterTarget.JPEG -> ConversionRequest.ImagesToFormat(current.sources, ImageTargetFormat.JPEG, options)
                ConverterTarget.PNG -> ConversionRequest.ImagesToFormat(current.sources, ImageTargetFormat.PNG, options)
                ConverterTarget.WEBP -> ConversionRequest.ImagesToFormat(current.sources, ImageTargetFormat.WEBP, options)
                else -> null
            }
            ConverterSourceKind.PDF -> {
                val source = current.sources.singleOrNull() ?: return null
                when (target) {
                    ConverterTarget.PDF_IMAGES_ZIP -> ConversionRequest.PdfToImages(source, ImageTargetFormat.JPEG, options)
                    ConverterTarget.PNG -> ConversionRequest.PdfToImages(source, ImageTargetFormat.PNG, options)
                    ConverterTarget.WEBP -> ConversionRequest.PdfToImages(source, ImageTargetFormat.WEBP, options)
                    ConverterTarget.DOCX -> ConversionRequest.PdfToDocx(source, options)
                    ConverterTarget.TEXT -> ConversionRequest.PdfToText(source, options)
                    else -> null
                }
            }
            null -> null
        }
    }
}
