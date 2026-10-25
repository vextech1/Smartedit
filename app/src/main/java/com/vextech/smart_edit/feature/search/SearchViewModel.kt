package com.vextech.smart_edit.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.library.SearchFilter
import com.vextech.smart_edit.core.library.SearchPolicies
import com.vextech.smart_edit.core.model.SearchHit
import com.vextech.smart_edit.core.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchUiState(
    val filter: SearchFilter = SearchFilter.ALL,
    val terms: List<String> = emptyList(),
    val hits: List<SearchHit> = emptyList(),
    val failed: Boolean = false,
) {
    /** True before the user has typed anything searchable. */
    val idle: Boolean get() = terms.isEmpty() && !failed
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: SearchRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SearchFilter.ALL)

    val state: StateFlow<SearchUiState> = combine(query.debounce(250), filter) { q, f -> q to f }
        .mapLatest { (q, f) ->
            val terms = SearchPolicies.terms(q)
            if (terms.isEmpty()) {
                SearchUiState(filter = f)
            } else {
                try {
                    SearchUiState(f, terms, repository.search(q, f))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    SearchUiState(f, terms, failed = true)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQuery(value: String) { query.value = value }
    fun onFilter(value: SearchFilter) { filter.value = value }
}
