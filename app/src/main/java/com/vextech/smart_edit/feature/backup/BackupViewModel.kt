package com.vextech.smart_edit.feature.backup
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.backup.BackupService
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.repository.SmartEditRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class BackupUiState(val busy:Boolean=false,val message:String?=null,val latest:File?=null)
@HiltViewModel class BackupViewModel @Inject constructor(private val service:BackupService,private val store:DocumentStore,repo:SmartEditRepository):ViewModel(){val backups=repo.backups.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList());private val _state=MutableStateFlow(BackupUiState());val state=_state.asStateFlow();fun create()=viewModelScope.launch{_state.value=BackupUiState(busy=true);runCatching{withContext(Dispatchers.IO){service.createBackup()}}.onSuccess{_state.value=BackupUiState(message="Backup created",latest=it)}.onFailure{_state.value=BackupUiState(message=it.message)}};fun restore(uri:Uri)=viewModelScope.launch{_state.value=BackupUiState(busy=true);runCatching{withContext(Dispatchers.IO){val staged=store.stageContent(uri,".smarteditbackup");try{service.restoreBackup(staged)}finally{staged.delete()}}}.onSuccess{_state.value=BackupUiState(message="Backup restored")}.onFailure{_state.value=BackupUiState(message=it.message)}}}
