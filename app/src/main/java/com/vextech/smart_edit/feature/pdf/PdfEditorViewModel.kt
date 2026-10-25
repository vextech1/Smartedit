package com.vextech.smart_edit.feature.pdf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.feature.pdf.data.PdfEditorRepository
import com.vextech.smart_edit.feature.pdf.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class PdfEditorUiState(
    val session: PdfEditorSession? = null,
    val pages: List<PdfPageInfo> = emptyList(),
    val selectedPages: Set<Int> = emptySet(),
    val previews: Map<Int, String> = emptyMap(),
    val busy: Boolean = false,
    val message: PdfEditorMessageKey? = null,
    val detail: String? = null,
    val passwordRequiredFor: Uri? = null,
    val passwordWasWrong: Boolean = false,
    val shareFile: File? = null,
    val printFile: File? = null,
    val exportedFiles: List<File> = emptyList(),
    val searchQuery: String = "",
    val searchHits: List<PdfSearchHit> = emptyList(),
    val extractedText: String = "",
    val forms: List<PdfFormField> = emptyList(),
    val metadata: PdfDocumentMetadata? = null,
    val bookmarks: List<PdfBookmark> = emptyList(),
    val annotations: List<PdfAnnotationInfo> = emptyList(),
    val security: PdfSecurityState? = null,
)

@HiltViewModel
class PdfEditorViewModel @Inject constructor(
    private val repository: PdfEditorRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PdfEditorUiState())
    val state = _state.asStateFlow()

    private var initialHandled = false

    /** Opens a file chosen elsewhere (for example the Files menu). Runs once per screen instance. */
    fun openInitial(uri: Uri) {
        if (initialHandled) return
        initialHandled = true
        open(uri)
    }

    fun open(uri: Uri, password: String? = null) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null, detail = null) }
        runCatching { repository.open(uri, password) }
            .onSuccess { session ->
                val pages = repository.pages(session.id)
                _state.value = PdfEditorUiState(
                    session = session,
                    pages = pages,
                    selectedPages = if (pages.isEmpty()) emptySet() else setOf(0),
                    busy = false,
                )
                loadPreview(0)
                refreshInspectorData()
            }
            .onFailure { error ->
                val passwordError = error.javaClass.simpleName.contains("Password", ignoreCase = true)
                _state.update {
                    it.copy(
                        busy = false,
                        passwordRequiredFor = if (passwordError) uri else null,
                        passwordWasWrong = passwordError && !password.isNullOrEmpty(),
                        message = if (passwordError) (if (password.isNullOrEmpty()) PdfEditorMessageKey.PASSWORD_REQUIRED else PdfEditorMessageKey.WRONG_PASSWORD) else PdfEditorMessageKey.OPEN_FAILED,
                        detail = if (passwordError) null else error.message,
                    )
                }
            }
    }

    fun close(discard: Boolean = true, onClosed: () -> Unit = {}) = viewModelScope.launch {
        try {
            _state.value.session?.id?.let { repository.close(it, discard) }
            _state.value = PdfEditorUiState()
        } finally {
            onClosed()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, detail = null) }
    fun dismissPasswordPrompt() = _state.update { it.copy(passwordRequiredFor = null, passwordWasWrong = false) }
    fun consumeShare() = _state.update { it.copy(shareFile = null) }
    fun consumePrint() = _state.update { it.copy(printFile = null) }

    fun selectPage(index: Int, toggle: Boolean = false) {
        val pageCount = _state.value.pages.size
        if (index !in 0 until pageCount) return
        _state.update { current ->
            val selection = if (!toggle) setOf(index) else current.selectedPages.toMutableSet().apply {
                if (!add(index)) remove(index)
                if (isEmpty()) add(index)
            }
            current.copy(selectedPages = selection)
        }
        loadPreview(index)
    }

    fun loadPreview(index: Int, force: Boolean = false) = viewModelScope.launch {
        val id = _state.value.session?.id ?: return@launch
        if (!force && _state.value.previews[index]?.let(::File)?.isFile == true) return@launch
        runCatching { repository.preview(id, index, force) }
            .onSuccess { file -> _state.update { it.copy(previews = it.previews + (index to file.absolutePath)) } }
    }

    fun undo() = mutateSimple { repository.undo(it) }
    fun redo() = mutateSimple { repository.redo(it) }

    fun save(saveAsName: String? = null) = viewModelScope.launch {
        val id = _state.value.session?.id ?: return@launch
        busyRun(PdfEditorMessageKey.SAVE_FAILED) {
            val file = repository.save(id, saveAsName)
            val session = repository.session(id)
            _state.update { it.copy(session = session, message = PdfEditorMessageKey.SAVED, exportedFiles = listOf(file)) }
        }
    }

    fun shareWorkingCopy() = viewModelScope.launch {
        val id = _state.value.session?.id ?: return@launch
        busyRun(PdfEditorMessageKey.SHARE_FAILED) {
            _state.update { it.copy(shareFile = repository.temporaryShareCopy(id)) }
        }
    }

    fun printWorkingCopy() = viewModelScope.launch {
        val current = _state.value
        val id = current.session?.id ?: return@launch
        if (current.security?.canPrint == false) {
            _state.update { it.copy(message = PdfEditorMessageKey.PRINT_NOT_ALLOWED, detail = null) }
            return@launch
        }
        busyRun(PdfEditorMessageKey.OPERATION_FAILED) {
            _state.update { it.copy(printFile = repository.temporaryShareCopy(id)) }
        }
    }

    fun reorder(order: List<Int>) = mutate { id -> repository.reorder(id, order) }
    fun deleteSelected() = selectedMutation { id, pages -> repository.deletePages(id, pages) }
    fun duplicateSelected() = selectedMutation { id, pages -> repository.duplicatePages(id, pages) }
    fun rotateSelected(degrees: Int) = selectedMutation { id, pages -> repository.rotatePages(id, pages, degrees) }
    fun cropSelected(rect: PdfRect) = selectedMutation { id, pages -> repository.cropPages(id, pages, rect) }
    fun watermark(text: String) = mutate { id -> repository.watermark(id, text) }
    fun addPageNumbers(startNumber: Int = 1) = mutate { id -> repository.addPageNumbers(id, startNumber) }

    fun insertBlankPage() {
        val current = _state.value
        val pageIndex = current.selectedPages.minOrNull() ?: 0
        val page = current.pages.getOrNull(pageIndex) ?: return
        mutate { id -> repository.insertBlankPage(id, (pageIndex + 1).coerceAtMost(current.pages.size), page.widthPoints, page.heightPoints) }
    }

    fun insertPages(source: Uri, insertAt: Int, sourcePassword: String? = null) = mutate { id -> repository.insertPages(id, insertAt, source, emptyList(), sourcePassword) }

    fun extractSelected() = viewModelScope.launch {
        val current = _state.value
        val id = current.session?.id ?: return@launch
        val pages = current.selectedPages.toList()
        busyRun(PdfEditorMessageKey.OPERATION_FAILED) {
            val out = repository.extractToManaged(id, pages)
            _state.update { it.copy(exportedFiles = listOf(out)) }
        }
    }

    fun split() = viewModelScope.launch {
        val id = _state.value.session?.id ?: return@launch
        busyRun(PdfEditorMessageKey.OPERATION_FAILED) {
            _state.update { it.copy(exportedFiles = repository.splitToManaged(id)) }
        }
    }

    fun merge(uris: List<Uri>) = viewModelScope.launch {
        busyRun(PdfEditorMessageKey.OPERATION_FAILED) {
            _state.update { it.copy(exportedFiles = listOf(repository.mergeToManaged(uris))) }
        }
    }

    fun addText(rect: PdfRect, text: String, size: Float = 14f) = currentPageMutation { id, page -> repository.addText(id, page, rect, text, size) }
    fun highlight(rect: PdfRect) = currentPageMutation { id, page -> repository.highlight(id, page, rect) }
    fun underline(rect: PdfRect) = currentPageMutation { id, page -> repository.underline(id, page, rect) }
    fun strikeout(rect: PdfRect) = currentPageMutation { id, page -> repository.strikeout(id, page, rect) }
    fun rectangle(rect: PdfRect) = currentPageMutation { id, page -> repository.drawRectangle(id, page, rect) }
    fun ink(points: List<PdfPoint>) = currentPageMutation { id, page -> repository.drawInk(id, page, points) }
    fun note(rect: PdfRect, text: String) = currentPageMutation { id, page -> repository.addNote(id, page, rect, text) }
    fun link(rect: PdfRect, url: String) = currentPageMutation { id, page -> repository.addLink(id, page, rect, url) }
    fun image(rect: PdfRect, uri: Uri) = currentPageMutation { id, page -> repository.addImage(id, page, rect, uri) }
    fun redact(rect: PdfRect) {
        if (_state.value.security?.encrypted == true) {
            _state.update { it.copy(message = PdfEditorMessageKey.REDACTION_REQUIRES_UNPROTECTED, detail = null) }
            return
        }
        currentPageMutation { id, page -> repository.redact(id, page, rect) }
    }

    fun removeAnnotation(id: String) = mutate { sessionId -> repository.removeAnnotation(sessionId, id) }

    fun search(query: String) = viewModelScope.launch {
        val id = _state.value.session?.id ?: return@launch
        if (query.isBlank()) { _state.update { it.copy(searchQuery = query, searchHits = emptyList()) }; return@launch }
        busyRun(PdfEditorMessageKey.OPERATION_FAILED) {
            _state.update { it.copy(searchQuery = query, searchHits = repository.search(id, query)) }
        }
    }

    fun extractText() = viewModelScope.launch {
        val id = _state.value.session?.id ?: return@launch
        busyRun(PdfEditorMessageKey.OPERATION_FAILED) { _state.update { it.copy(extractedText = repository.extractText(id)) } }
    }

    fun setFormField(name: String, value: String) = mutate { id -> repository.setFormField(id, name, value) }
    fun flattenForms() = mutate { id -> repository.flattenForms(id) }
    fun updateMetadata(metadata: PdfDocumentMetadata) = mutate { id -> repository.updateMetadata(id, metadata) }
    fun addBookmark(title: String, page: Int = currentPage()) = mutate { id -> repository.addBookmark(id, title, page) }
    fun removeBookmark(bookmarkId: String) = mutate { id -> repository.removeBookmark(id, bookmarkId) }

    fun protect(options: PdfSecurityOptions) = mutate(successMessage = PdfEditorMessageKey.SECURITY_APPLIED) { id -> repository.protect(id, options) }
    fun removeProtection(password: String) = mutate(successMessage = PdfEditorMessageKey.SECURITY_REMOVED) { id -> repository.removeProtection(id, password) }

    private fun currentPage(): Int = _state.value.selectedPages.minOrNull() ?: 0

    private fun currentPageMutation(block: suspend (String, Int) -> PdfEditorSession) = mutate { id -> block(id, currentPage()) }

    private fun selectedMutation(block: suspend (String, List<Int>) -> PdfEditorSession) {
        val pages = _state.value.selectedPages.toList()
        if (pages.isEmpty()) { _state.update { it.copy(message = PdfEditorMessageKey.INVALID_PAGE_SELECTION) }; return }
        mutate { id -> block(id, pages) }
    }

    private fun mutate(successMessage: PdfEditorMessageKey? = null, block: suspend (String) -> PdfEditorSession) = viewModelScope.launch {
        val id = _state.value.session?.id ?: return@launch
        busyRun(PdfEditorMessageKey.OPERATION_FAILED) {
            val session = block(id)
            refreshAfterMutation(session, successMessage)
        }
    }

    private fun mutateSimple(block: suspend (String) -> PdfEditorSession) = mutate(block = block)

    private suspend fun refreshAfterMutation(session: PdfEditorSession, message: PdfEditorMessageKey? = null) {
        val pages = repository.pages(session.id)
        val validSelection = _state.value.selectedPages.filter { it in pages.indices }.toSet().ifEmpty { if (pages.isNotEmpty()) setOf(0) else emptySet() }
        _state.update { it.copy(session = session, pages = pages, selectedPages = validSelection, previews = emptyMap(), message = message) }
        validSelection.minOrNull()?.let { loadPreview(it, force = true) }
        refreshInspectorData()
    }

    private suspend fun refreshInspectorData() {
        val id = _state.value.session?.id ?: return
        val forms = runCatching { repository.forms(id) }.getOrDefault(emptyList())
        val metadata = runCatching { repository.metadata(id) }.getOrNull()
        val bookmarks = runCatching { repository.bookmarks(id) }.getOrDefault(emptyList())
        val annotations = runCatching { repository.annotations(id) }.getOrDefault(emptyList())
        val security = runCatching { repository.security(id) }.getOrNull()
        _state.update { it.copy(forms = forms, metadata = metadata, bookmarks = bookmarks, annotations = annotations, security = security) }
    }

    private suspend fun busyRun(failure: PdfEditorMessageKey, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, message = null, detail = null) }
        runCatching { block() }
            .onFailure { error -> _state.update { it.copy(message = failure, detail = error.message) } }
        _state.update { it.copy(busy = false) }
    }
}
