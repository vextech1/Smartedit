package com.vextech.smart_edit.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.library.HistoryEntry
import com.vextech.smart_edit.core.library.HistoryPolicies
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The few most recent files the user made or changed, newest first, one entry per file. */
@HiltViewModel
class HomeViewModel @Inject constructor(repository: HistoryRepository) : ViewModel() {
    private val produced = setOf(HistoryAction.CONVERTED, HistoryAction.COMPRESSED, HistoryAction.SCANNED, HistoryAction.CREATED, HistoryAction.EDITED)

    val recent: StateFlow<List<HistoryEntry>> = repository.entries
        .map { all ->
            all
                .filter { it.action in produced && HistoryPolicies.canOpen(it) }
                .sortedByDescending { it.timestamp }
                .distinctBy { it.uri }
                .take(3)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
