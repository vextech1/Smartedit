package com.vextech.editpro.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.editpro.core.model.DashboardSummary
import com.vextech.editpro.core.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(repository: DashboardRepository) : ViewModel() {
    val summary: StateFlow<DashboardSummary> =
        repository.summary.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardSummary())
}
