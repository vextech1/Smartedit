package com.vextech.editpro.feature.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.editpro.R
import com.vextech.editpro.core.auth.AdminPolicies
import com.vextech.editpro.core.auth.AdminRepository
import com.vextech.editpro.core.auth.AdminRole
import com.vextech.editpro.core.auth.AuthFailure
import com.vextech.editpro.core.auth.AuthRepository
import com.vextech.editpro.core.auth.AuthResult
import com.vextech.editpro.core.auth.AuthState
import com.vextech.editpro.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val busy: Boolean = false,
    /** True while the server is being asked what this account may do. */
    val checking: Boolean = false,
    val role: AdminRole = AdminRole.NONE,
    /** The helper admins. Null for anyone but the owner, who alone may see or change the list. */
    val helpers: List<String>? = null,
    val message: UiText? = null,
    /** True when the check could not be completed (offline, ...). Not the same as "not an admin". */
    val checkFailed: Boolean = false,
)

/**
 * The Admin screen's logic, mirroring the site's admin page: sign in with Google, find out what the account is allowed to do,
 * and (owner only) add and remove helper admins.
 */
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val admins: AdminRepository,
) : ViewModel() {
    val authState: StateFlow<AuthState> = auth.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState())

    private val _ui = MutableStateFlow(AdminUiState())
    val ui: StateFlow<AdminUiState> = _ui.asStateFlow()

    private var checkJob: Job? = null
    private var checkedUid: String? = null

    /** Call whenever the signed-in account may have changed. Asks the server once per account. */
    fun onAccount(state: AuthState) {
        val uid = state.user?.uid
        if (uid == checkedUid) return
        checkedUid = uid
        checkJob?.cancel()
        if (state.user == null) {
            _ui.value = AdminUiState()
            return
        }
        refresh(state)
    }

    fun refresh(state: AuthState = authState.value) {
        val user = state.user ?: return
        checkedUid = user.uid
        checkJob?.cancel()
        _ui.update { it.copy(checking = true, checkFailed = false, message = null) }
        checkJob = viewModelScope.launch {
            try {
                val role = admins.role(user)
                val helpers = if (role == AdminRole.OWNER) admins.helpers() else null
                _ui.update { it.copy(checking = false, role = role, helpers = helpers) }
            } catch (e: Exception) {
                _ui.update { it.copy(checking = false, checkFailed = true, role = AdminRole.NONE, helpers = null) }
            }
        }
    }

    fun signIn(activityContext: Context) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = when (val result = auth.signIn(activityContext)) {
                is AuthResult.Success -> null
                is AuthResult.Failure -> when (result.reason) {
                    AuthFailure.CANCELLED -> null
                    AuthFailure.DISABLED -> UiText.res(R.string.account_unavailable)
                    else -> UiText.res(R.string.account_signin_failed)
                }
            }
            _ui.update { it.copy(busy = false, message = message) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            checkedUid = null
            _ui.value = AdminUiState()
        }
    }

    fun addHelper(email: String, onAdded: () -> Unit) {
        val id = AdminPolicies.normalizeEmail(email)
        val current = _ui.value.helpers ?: return
        when {
            !AdminPolicies.isValidEmail(id) -> _ui.update { it.copy(message = UiText.res(R.string.admin_email_invalid)) }
            id in current -> _ui.update { it.copy(message = UiText.res(R.string.admin_already_admin, id)) }
            else -> changeHelpers(UiText.res(R.string.admin_added, id), onAdded) { admins.addHelper(id) }
        }
    }

    fun removeHelper(email: String) = changeHelpers(UiText.res(R.string.admin_removed, email)) { admins.removeHelper(email) }

    private fun changeHelpers(done: UiText, onDone: () -> Unit = {}, change: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                change()
                val helpers = admins.helpers()
                _ui.update { it.copy(busy = false, helpers = helpers, message = done) }
                onDone()
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = UiText.res(R.string.admin_change_failed)) }
            }
        }
    }
}
