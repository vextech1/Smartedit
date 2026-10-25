package com.vextech.smart_edit.feature.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StartViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val completed = settings.settings.map { it.onboardingCompleted }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
