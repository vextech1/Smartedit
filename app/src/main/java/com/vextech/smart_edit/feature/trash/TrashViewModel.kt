package com.vextech.smart_edit.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.TrashItem
import com.vextech.smart_edit.core.library.TrashKind
import com.vextech.smart_edit.core.repository.TrashRepository
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val loading: Boolean = true,
    val files: List<TrashItem> = emptyList(),
    val notes: List<TrashItem> = emptyList(),
) {
    val total: Int get() = files.size + notes.size
}

@HiltViewModel
class TrashViewModel @Inject constructor(private val repository: TrashRepository) : ViewModel() {
    private val events = UiEventChannel()
    val uiEvents = events.events

    val state: StateFlow<TrashUiState> = repository.items
        .map { items -> TrashUiState(false, items.filter { it.kind == TrashKind.FILE }, items.filter { it.kind == TrashKind.NOTE }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    fun restore(item: TrashItem) = run(R.string.trash_restore_failed) {
        repository.restore(item)
        events.message(UiText.res(R.string.trash_restored))
    }

    fun deleteForever(item: TrashItem) = run(R.string.trash_delete_failed) {
        repository.deleteForever(item)
        events.message(UiText.res(R.string.trash_deleted))
    }

    fun emptyTrash() = run(R.string.trash_delete_failed) {
        repository.empty()
        events.message(UiText.res(R.string.trash_emptied))
    }

    private fun run(failure: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { events.message(UiText.res(failure)) }
        }
    }
}
