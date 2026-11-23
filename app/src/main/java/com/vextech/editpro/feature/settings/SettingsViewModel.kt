package com.vextech.editpro.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.editpro.core.library.OcrScript
import com.vextech.editpro.core.model.AppSettings
import com.vextech.editpro.core.model.ThemePreference
import com.vextech.editpro.core.preferences.SettingsRepository
import com.vextech.editpro.core.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val work: WorkScheduler,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun theme(v: ThemePreference) = viewModelScope.launch { repo.update { it.copy(theme = v) } }
    fun language(v: String) = viewModelScope.launch { repo.update { it.copy(language = v) } }
    fun notifications(v: Boolean) = viewModelScope.launch { repo.update { it.copy(notificationsEnabled = v) } }
    fun autoBackup(v: Boolean) = viewModelScope.launch { repo.update { it.copy(autoBackupEnabled = v) }; work.setAutoBackup(v) }
    fun confirmDelete(v: Boolean) = viewModelScope.launch { repo.update { it.copy(confirmDeleteEnabled = v) } }
    fun exportFormat(v: String) = viewModelScope.launch { repo.update { it.copy(defaultExportFormat = v) } }
    fun ocrScript(v: OcrScript) = viewModelScope.launch { repo.update { it.copy(ocrScript = v.name) } }
}
