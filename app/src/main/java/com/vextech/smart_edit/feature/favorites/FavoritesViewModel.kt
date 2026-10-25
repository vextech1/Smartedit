package com.vextech.smart_edit.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.FavoriteItem
import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.core.repository.FavoritesRepository
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

data class FavoritesUiState(
    val loading: Boolean = true,
    val notes: List<FavoriteItem> = emptyList(),
    val files: List<FavoriteItem> = emptyList(),
) {
    val isEmpty: Boolean get() = notes.isEmpty() && files.isEmpty()
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(private val repository: FavoritesRepository) : ViewModel() {
    private val events = UiEventChannel()
    val uiEvents = events.events

    val state: StateFlow<FavoritesUiState> = repository.items
        .map { items -> FavoritesUiState(false, items.filter { it.isNote }, items.filterNot { it.isNote }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    fun remove(item: FavoriteItem) {
        val type = item.type ?: ResourceType.DOCUMENT
        viewModelScope.launch {
            try {
                repository.set(type, item.resourceId, favorite = false, displayName = item.displayName, uri = item.uri)
                events.message(UiText.res(R.string.favorites_removed))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                events.message(UiText.res(R.string.favorites_action_failed))
            }
        }
    }
}
