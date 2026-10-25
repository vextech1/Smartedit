package com.vextech.smart_edit.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.Note
import com.vextech.smart_edit.core.library.NotePolicies
import com.vextech.smart_edit.core.repository.NotesRepository
import com.vextech.smart_edit.core.ui.UiAction
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
    val loading: Boolean = true,
    val tags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val notes: List<Note> = emptyList(),
    val totalCount: Int = 0,
)

@HiltViewModel
class NotesViewModel @Inject constructor(private val repository: NotesRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val tag = MutableStateFlow<String?>(null)
    private val events = UiEventChannel()
    val uiEvents = events.events

    val state: StateFlow<NotesUiState> = combine(repository.active, query, tag) { notes, q, selected ->
        val sorted = NotePolicies.sort(notes)
        val tags = NotePolicies.allTags(sorted)
        // A tag filter that no longer exists (last note with it was deleted) is dropped.
        val activeTag = selected?.takeIf { s -> tags.any { it.equals(s, ignoreCase = true) } }
        NotesUiState(false, tags, activeTag, NotePolicies.filter(sorted, q, activeTag), notes.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun onQuery(value: String) { query.value = value }
    fun onTag(value: String?) { tag.value = value }

    fun togglePin(note: Note) = run(R.string.notes_action_failed) { repository.setPinned(note.id, !note.isPinned) }
    fun toggleFavorite(note: Note) = run(R.string.notes_action_failed) { repository.setFavorite(note.id, !note.isFavorite) }

    fun moveToTrash(note: Note) = run(R.string.notes_action_failed) {
        repository.moveToTrash(note.id)
        events.message(UiText.res(R.string.notes_trashed), UiAction(UiText.res(R.string.notes_undo), UNDO_PREFIX + note.id))
    }

    fun onAction(id: String) {
        if (id.startsWith(UNDO_PREFIX)) run(R.string.notes_action_failed) { repository.restore(id.removePrefix(UNDO_PREFIX)) }
    }

    private fun run(failure: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { events.message(UiText.res(failure)) }
        }
    }

    private companion object { const val UNDO_PREFIX = "undo_trash:" }
}
