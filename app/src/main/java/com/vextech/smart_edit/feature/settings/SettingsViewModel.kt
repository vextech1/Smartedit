package com.vextech.smart_edit.feature.settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.model.ThemePreference
import com.vextech.smart_edit.core.preferences.SettingsRepository
import com.vextech.smart_edit.core.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel class SettingsViewModel @Inject constructor(private val repo:SettingsRepository,private val work:WorkScheduler):ViewModel(){val settings=repo.settings.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),com.vextech.smart_edit.core.model.AppSettings());fun theme(v:ThemePreference)=viewModelScope.launch{repo.update{it.copy(theme=v)}};fun language(v:String)=viewModelScope.launch{repo.update{it.copy(language=v)}};fun notifications(v:Boolean)=viewModelScope.launch{repo.update{it.copy(notificationsEnabled=v)}};fun autoBackup(v:Boolean)=viewModelScope.launch{repo.update{it.copy(autoBackupEnabled=v)};work.setAutoBackup(v)};fun confirmDelete(v:Boolean)=viewModelScope.launch{repo.update{it.copy(confirmDeleteEnabled=v)}};fun exportFormat(v:String)=viewModelScope.launch{repo.update{it.copy(defaultExportFormat=v)}}}
