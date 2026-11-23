package com.vextech.editpro.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.editpro.core.backup.BackupService
import com.vextech.editpro.core.files.DocumentStore
import com.vextech.editpro.core.repository.EditProRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BackupStatus { CREATED, RESTORED, FAILED }

/** [error] is only set for FAILED and holds the technical reason; the screen shows its own wording around it. */
data class BackupUiState(
    val busy: Boolean = false,
    val status: BackupStatus? = null,
    val error: String? = null,
    val latest: File? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val service: BackupService,
    private val store: DocumentStore,
    repo: EditProRepository,
) : ViewModel() {
    val backups = repo.backups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun create() {
        viewModelScope.launch {
            _state.value = BackupUiState(busy = true)
            runCatching { withContext(Dispatchers.IO) { service.createBackup() } }
                .onSuccess { _state.value = BackupUiState(status = BackupStatus.CREATED, latest = it) }
                .onFailure { _state.value = BackupUiState(status = BackupStatus.FAILED, error = it.message) }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState(busy = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val staged = store.stageContent(uri, ".editprobackup")
                    try { service.restoreBackup(staged) } finally { staged.delete() }
                }
            }
                .onSuccess { _state.value = BackupUiState(status = BackupStatus.RESTORED) }
                .onFailure { _state.value = BackupUiState(status = BackupStatus.FAILED, error = it.message) }
        }
    }
}
