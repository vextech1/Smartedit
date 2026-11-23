package com.vextech.smartedit.feature.classlibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smartedit.R
import com.vextech.smartedit.core.auth.AdminRepository
import com.vextech.smartedit.core.auth.AdminRole
import com.vextech.smartedit.core.auth.AuthRepository
import com.vextech.smartedit.core.classlibrary.AdminLibraryRepository
import com.vextech.smartedit.core.classlibrary.UserFile
import com.vextech.smartedit.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentFilesUiState(
    val checking: Boolean = true,
    val role: AdminRole = AdminRole.NONE,
    val showPulled: Boolean = false,
    val files: List<UserFile> = emptyList(),
    val loading: Boolean = false,
    val uploadsOpen: Boolean = true,
    val message: UiText? = null,
)

/** The owner's and helpers' review of files shared by students: remove one with a reason, retry cleanup, and pause new uploads. */
@HiltViewModel
class StudentFilesViewModel @Inject constructor(
    private val admin: AdminLibraryRepository,
    private val roles: AdminRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(StudentFilesUiState())
    val ui: StateFlow<StudentFilesUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val role = runCatching { roles.role(auth.state.first { it.ready }.user) }.getOrDefault(AdminRole.NONE)
            _ui.update { it.copy(role = role, checking = false) }
            if (role != AdminRole.NONE) {
                runCatching { admin.userUploadsOpen() }.onSuccess { open -> _ui.update { it.copy(uploadsOpen = open) } }
                reload()
            }
        }
    }

    fun showPulled(on: Boolean) {
        _ui.update { it.copy(showPulled = on) }
        reload()
    }

    fun reload() {
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val files = admin.studentFiles(if (_ui.value.showPulled) "pulled" else "live")
                _ui.update { it.copy(files = files, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(loading = false, message = UiText.res(R.string.admin_change_failed)) }
            }
        }
    }

    fun setUploadsOpen(open: Boolean) {
        viewModelScope.launch {
            try {
                admin.setUserUploadsOpen(open)
                _ui.update { it.copy(uploadsOpen = open) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(message = UiText.res(R.string.admin_change_failed)) }
            }
        }
    }

    fun pull(file: UserFile, reason: String) {
        viewModelScope.launch {
            try {
                val cleaned = admin.pull(file, reason)
                _ui.update { it.copy(message = UiText.res(if (cleaned) R.string.students_pull_done else R.string.students_pull_cleanup_pending)) }
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _ui.update { it.copy(message = UiText.dynamic(e.message.orEmpty())) }
            } catch (_: Exception) {
                _ui.update { it.copy(message = UiText.res(R.string.admin_change_failed)) }
            }
        }
    }

    fun retry(file: UserFile) {
        viewModelScope.launch {
            val ok = admin.retryPieces(file)
            _ui.update { it.copy(message = UiText.res(if (ok) R.string.students_cleaned else R.string.students_pull_cleanup_pending)) }
            reload()
        }
    }

    fun deleteRecord(file: UserFile) {
        viewModelScope.launch {
            try {
                admin.deleteRecord(file)
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(message = UiText.res(R.string.admin_change_failed)) }
            }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }
}
