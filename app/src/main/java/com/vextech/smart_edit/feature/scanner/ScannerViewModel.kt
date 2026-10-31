package com.vextech.smart_edit.feature.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.vextech.smart_edit.core.library.OcrScript
import com.vextech.smart_edit.feature.scanner.data.ScannerRepository
import com.vextech.smart_edit.feature.scanner.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

enum class ScannerMessage { SESSION_FAILED, IMPORT_FAILED, EDIT_FAILED, OCR_FAILED, EXPORT_FAILED, PAGE_DELETED, EXPORTED }

data class ScannerUiState(
    val session: ScanSessionDraft? = null,
    val recentSessions: List<com.vextech.smart_edit.core.database.entity.ScanSessionEntity> = emptyList(),
    val covers: Map<String, com.vextech.smart_edit.core.database.dao.ScanCover> = emptyMap(),
    val busy: Boolean = false,
    val message: ScannerMessage? = null,
    /** A page delete can still be undone. */
    val undoAvailable: Boolean = false,
)

private data class PendingDelete(val sessionId: String, val pageIds: Set<String>)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScannerViewModel @Inject constructor(private val repository: ScannerRepository) : ViewModel() {
    private companion object {
        const val UNDO_WINDOW_MS = 5_000L
        const val STARTUP_PURGE_GRACE_MS = 60_000L
    }

    private val activeId = MutableStateFlow<String?>(null)
    private val inFlight = MutableStateFlow(0)
    private val message = MutableStateFlow<ScannerMessage?>(null)
    private val pending = MutableStateFlow<PendingDelete?>(null)
    private var undoTimer: Job? = null
    /** Serialises every repository mutation so a pending delete is always committed before the next change. */
    private val lock = Mutex()

    private val activeSession = activeId.flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeSession(id) }
    private val visibleSession = combine(activeSession, pending) { session, p ->
        if (session == null) null
        else session.copy(pages = ScannerPolicies.withoutPending(session.pages, p?.takeIf { it.sessionId == session.id }?.pageIds.orEmpty()) { it.id })
    }
    private val messageAndUndo = combine(message, pending) { m, p -> m to (p != null) }

    val state: StateFlow<ScannerUiState> = combine(visibleSession, repository.observeSessions(), repository.observeCovers(), inFlight, messageAndUndo) { session, recent, covers, n, mu ->
        ScannerUiState(session, recent, covers.associateBy { it.sessionId }, n > 0, mu.first, mu.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScannerUiState())

    init {
        // Drafts that never received a page (older than the grace period) are leftovers; remove them.
        viewModelScope.launch { lock.withLock { runCatching { repository.purgeEmptySessions(activeId.value, STARTUP_PURGE_GRACE_MS) } } }
    }

    fun clearMessage() { message.value = null }
    fun openSession(id: String) { activeId.value = id }

    fun closeSession() {
        activeId.value = null
        viewModelScope.launch {
            lock.withLock { runCatching { commitPendingLocked(); repository.purgeEmptySessions(activeId.value) } }
        }
    }

    /** Back from the camera: a session that received no page is dropped so no empty draft is left behind. */
    fun afterCamera() {
        val id = activeId.value ?: return
        viewModelScope.launch {
            lock.withLock {
                runCatching {
                    if (repository.observeSession(id).first()?.pages.isNullOrEmpty()) { activeId.value = null; repository.purgeEmptySessions(null) }
                }
            }
        }
    }

    fun rename(title: String) = activeId.value?.let { id -> task(ScannerMessage.EDIT_FAILED) { repository.rename(id, title) } }
    fun newSession(onReady: (String) -> Unit = {}) = task(ScannerMessage.SESSION_FAILED) { repository.createSession().also { activeId.value = it; onReady(it) } }
    fun importUris(uris: List<Uri>, autoFilter: Boolean = true) { ensureSession { id -> task(ScannerMessage.IMPORT_FAILED) { commitPendingLocked(); repository.addPages(id, uris, autoFilter = autoFilter) } } }
    /** The ML Kit scanner already cleans and filters its pages, so they are not auto-filtered again. */
    fun importScanResult(result: GmsDocumentScanningResult) { importUris(result.pages.orEmpty().map { it.imageUri }, autoFilter = false) }

    /** Hides the pages now and deletes them for real after [UNDO_WINDOW_MS] unless [undoDelete] is called. */
    fun deletePages(ids: Set<String>) {
        val sessionId = activeId.value ?: return
        if (ids.isEmpty()) return
        viewModelScope.launch {
            lock.withLock {
                runCatching { commitPendingLocked() }.onFailure { message.value = ScannerMessage.EDIT_FAILED }
                pending.value = PendingDelete(sessionId, ids)
            }
            val target = PendingDelete(sessionId, ids)
            undoTimer?.cancel()
            undoTimer = launch {
                delay(UNDO_WINDOW_MS)
                // Only commit if this exact delete is still pending (it may have been undone or committed already).
                lock.withLock { if (pending.value == target) runCatching { commitPendingLocked() }.onFailure { message.value = ScannerMessage.EDIT_FAILED } }
            }
        }
    }

    fun deletePage(id: String) = deletePages(setOf(id))

    fun undoDelete() { undoTimer?.cancel(); pending.value = null }

    fun movePage(from: Int, to: Int) = activeId.value?.let { task(ScannerMessage.EDIT_FAILED) { commitPendingLocked(); repository.reorder(it, from, to) } }

    fun editPage(id: String, crop: CropQuad, filter: ScanFilter, rotation: Int, applyToAll: Boolean = false) = task(ScannerMessage.EDIT_FAILED) {
        val sessionId = activeId.value
        if (applyToAll && sessionId != null) { commitPendingLocked(); repository.applyEditToAll(sessionId, id, crop, filter, rotation) }
        else repository.applyEdit(id, crop, filter, rotation)
    }

    /** Re-renders the chosen pages with [filter], keeping each page's own crop and rotation. */
    fun applyFilterToPages(ids: Set<String>, filter: ScanFilter) = activeId.value?.let { sid ->
        task(ScannerMessage.EDIT_FAILED) { commitPendingLocked(); repository.applyFilterToPages(sid, ids, filter) }
    }

    /** Asks the OCR engine which turn makes the page upright. [onResult] gets 0/90/180/270, or null on failure. */
    fun detectOrientation(id: String, onResult: (Int?) -> Unit) {
        viewModelScope.launch {
            inFlight.update { it + 1 }
            val turn = lock.withLock { runCatching { repository.detectOrientation(id) }.getOrNull() }
            inFlight.update { it - 1 }
            onResult(turn)
        }
    }

    /** Saves the page as a transparent PNG. [onResult] gets the file, or null if it failed. */
    fun extractCutout(pageId: String, mode: CutoutMode, onResult: (java.io.File?) -> Unit) {
        viewModelScope.launch {
            inFlight.update { it + 1 }; message.value = null
            val file = lock.withLock { runCatching { repository.extractCutout(pageId, mode) }.getOrNull() }
            if (file == null) message.value = ScannerMessage.EDIT_FAILED
            inFlight.update { it - 1 }
            onResult(file)
        }
    }

    /** [script] null = auto-detect. [onDone] gets true when text recognition finished. */
    fun ocr(id: String, script: OcrScript? = null, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            inFlight.update { it + 1 }; message.value = null
            val ok = lock.withLock { runCatching { repository.runOcr(id, script) }.isSuccess }
            if (!ok) message.value = ScannerMessage.OCR_FAILED
            inFlight.update { it - 1 }
            onDone(ok)
        }
    }

    fun export() = activeId.value?.let { task(ScannerMessage.EXPORT_FAILED) { commitPendingLocked(); repository.exportPdf(it); message.value = ScannerMessage.EXPORTED } }
    fun discard() = activeId.value?.let { id -> task(ScannerMessage.EDIT_FAILED) { undoTimer?.cancel(); pending.value = null; repository.discard(id); activeId.value = null } }
    fun error() { message.value = ScannerMessage.SESSION_FAILED }

    private fun ensureSession(block: (String) -> Unit) { activeId.value?.let(block) ?: newSession(block) }

    /** Must be called with [lock] held. */
    private suspend fun commitPendingLocked() {
        val p = pending.value ?: return
        try { p.pageIds.forEach { repository.deletePage(p.sessionId, it) } } finally { pending.value = null }
    }

    private fun task(failure: ScannerMessage, block: suspend () -> Unit) = viewModelScope.launch {
        inFlight.update { it + 1 }; message.value = null
        runCatching { lock.withLock { block() } }.onFailure { message.value = failure }
        inFlight.update { it - 1 }
    }
}
