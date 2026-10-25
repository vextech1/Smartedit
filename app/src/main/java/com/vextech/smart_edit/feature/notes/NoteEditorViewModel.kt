package com.vextech.smart_edit.feature.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.library.NotePolicies
import com.vextech.smart_edit.core.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SaveStatus { IDLE, SAVING, SAVED, FAILED }
enum class EditorLoad { LOADING, READY, MISSING }

private data class Draft(val title: String, val content: String, val tags: String)

/**
 * Autosaving note editor. Text lives in Compose state so typing never waits on a flow round trip.
 * Autosaves do not touch the Activity feed; a new note records one "created" entry and an existing note
 * records one "edited" entry when the editor is closed after changes.
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(private val repository: NotesRepository) : ViewModel() {
    var title by mutableStateOf("")
        private set
    var content by mutableStateOf("")
        private set
    var tags by mutableStateOf("")
        private set
    var status by mutableStateOf(SaveStatus.IDLE)
        private set
    var load by mutableStateOf(EditorLoad.LOADING)
        private set

    private var started = false
    private var noteId: String? = null
    private var lastSaved: Draft? = null
    private var unrecordedEdit = false
    private var createdThisSession = false
    private val saveLock = Mutex() // autosave and finish must never create two notes

    fun onTitle(value: String) { title = value }
    fun onContent(value: String) { content = value }
    fun onTags(value: String) { tags = value }

    fun start(id: String?) {
        if (started) return
        started = true
        noteId = id
        if (id == null) {
            lastSaved = Draft("", "", "")
            load = EditorLoad.READY
            watchForChanges()
            return
        }
        viewModelScope.launch {
            val note = try { repository.get(id) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            if (note == null || note.deletedAt != null) {
                load = EditorLoad.MISSING
                return@launch
            }
            title = note.title
            content = note.content
            tags = NotePolicies.tagsForEditing(note.tags)
            lastSaved = Draft(title, content, tags)
            load = EditorLoad.READY
            watchForChanges()
        }
    }

    @OptIn(FlowPreview::class)
    private fun watchForChanges() {
        viewModelScope.launch {
            snapshotFlow { Draft(title, content, tags) }.debounce(AUTOSAVE_DELAY_MS).collect { persist(it, finishing = false) }
        }
    }

    /** Saves any pending change, records activity once, then calls [onDone]. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            if (load == EditorLoad.READY) persist(Draft(title, content, tags), finishing = true)
            onDone()
        }
    }

    private suspend fun persist(draft: Draft, finishing: Boolean) = saveLock.withLock {
        val changed = draft != lastSaved
        val needsEditedEntry = finishing && unrecordedEdit
        if (!changed && !needsEditedEntry) return@withLock
        status = SaveStatus.SAVING
        try {
            val isFirstSave = noteId == null
            val record = isFirstSave || (finishing && !createdThisSession)
            val saved = repository.save(
                id = noteId, title = draft.title, content = draft.content, tagsRaw = draft.tags,
                recordActivity = record,
            )
            if (saved == null) {
                status = SaveStatus.IDLE // brand-new note is still empty; nothing stored
                return@withLock
            }
            if (isFirstSave) createdThisSession = true
            unrecordedEdit = if (record) false else unrecordedEdit || (!isFirstSave && !createdThisSession)
            noteId = saved.id
            lastSaved = draft
            status = SaveStatus.SAVED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            status = SaveStatus.FAILED
        }
    }

    private companion object { const val AUTOSAVE_DELAY_MS = 800L }
}
