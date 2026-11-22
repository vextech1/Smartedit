package com.vextech.smartedit.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smartedit.core.library.HistoryEntry
import com.vextech.smartedit.core.library.HistoryPolicies
import com.vextech.smartedit.core.model.HistoryAction
import com.vextech.smartedit.core.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Feeds the two lists on Home, newest first, one entry per file, three at most:
 * [continueEditing] is files you edited (reopen them in their editor), [recent] is files you made
 * (scanned, converted, compressed or created). "Clear all" hides everything older than now from [recent].
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: HistoryRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val clearedAt = MutableStateFlow(prefs.getLong(KEY_CLEARED_AT, 0L))

    private val made = setOf(HistoryAction.CONVERTED, HistoryAction.COMPRESSED, HistoryAction.SCANNED, HistoryAction.CREATED)

    private val openable = repository.entries.map { all ->
        all.filter { HistoryPolicies.canOpen(it) }.sortedByDescending { it.timestamp }
    }

    val continueEditing: StateFlow<List<HistoryEntry>> = openable
        .map { all -> all.filter { it.action == HistoryAction.EDITED }.distinctBy { it.uri }.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<HistoryEntry>> = combine(openable, clearedAt) { all, since ->
        all.filter { it.action in made && it.timestamp > since }.distinctBy { it.uri }.take(3)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearRecent() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_CLEARED_AT, now).apply()
        clearedAt.value = now
    }

    private companion object {
        const val KEY_CLEARED_AT = "recent_cleared_at"
    }
}
