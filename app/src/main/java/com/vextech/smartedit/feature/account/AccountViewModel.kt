package com.vextech.smartedit.feature.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smartedit.R
import com.vextech.smartedit.core.auth.AuthFailure
import com.vextech.smartedit.core.auth.AuthRepository
import com.vextech.smartedit.core.auth.AuthResult
import com.vextech.smartedit.core.auth.AuthState
import com.vextech.smartedit.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountUiState(val busy: Boolean = false, val message: UiText? = null)

/** Sign in, sign out and delete for an ordinary account. The same Google sign-in is used by admins (see the Admin screen). */
@HiltViewModel
class AccountViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    val authState: StateFlow<AuthState> = auth.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState())

    private val _ui = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = _ui.asStateFlow()

    /** [activityContext] must be an Activity: Google's account chooser is shown on top of it. */
    fun signIn(activityContext: Context) = launchUi {
        when (val result = auth.signIn(activityContext)) {
            is AuthResult.Success -> AccountUiState()
            is AuthResult.Failure -> when (result.reason) {
                AuthFailure.CANCELLED -> AccountUiState()
                AuthFailure.DISABLED -> AccountUiState(message = UiText.res(R.string.account_unavailable))
                else -> AccountUiState(message = UiText.res(R.string.account_signin_failed))
            }
        }
    }

    fun signOut() = launchUi {
        auth.signOut()
        AccountUiState(message = UiText.res(R.string.account_signed_out))
    }

    fun deleteAccount() = launchUi {
        when (val result = auth.deleteAccount()) {
            is AuthResult.Success -> AccountUiState(message = UiText.res(R.string.account_deleted))
            is AuthResult.Failure -> AccountUiState(
                message = UiText.res(if (result.reason == AuthFailure.RECENT_LOGIN_REQUIRED) R.string.account_delete_recent_login else R.string.account_delete_failed),
            )
        }
    }

    /** Shows "busy" while [block] runs, then shows what it returns. A second tap while busy is ignored. */
    private fun launchUi(block: suspend () -> AccountUiState) {
        if (_ui.value.busy) return
        _ui.value = AccountUiState(busy = true)
        viewModelScope.launch { _ui.value = block() }
    }
}
