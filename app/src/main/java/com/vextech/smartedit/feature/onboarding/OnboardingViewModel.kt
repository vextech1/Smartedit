package com.vextech.smartedit.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smartedit.core.preferences.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun complete(onDone: () -> Unit) = viewModelScope.launch { settings.update { it.copy(onboardingCompleted = true) }; onDone() }
}
