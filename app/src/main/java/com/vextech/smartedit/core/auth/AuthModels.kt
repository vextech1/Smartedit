package com.vextech.smartedit.core.auth

/** The signed-in Google account. [emailVerified] must be true for the Firestore rules to treat the account as an admin. */
data class AuthUser(
    val uid: String,
    val name: String,
    val email: String?,
    val emailVerified: Boolean,
)

/**
 * [enabled] is false until the Firebase side is set up (google-services.json with Google sign-in turned on). The app then works exactly as
 * before, with no account, the same as the site does while its Firebase settings are empty. [ready] is true once the first answer is in.
 */
data class AuthState(
    val ready: Boolean = false,
    val enabled: Boolean = false,
    val user: AuthUser? = null,
)

enum class AuthFailure {
    /** Firebase is not set up in this build. */
    DISABLED,

    /** The person closed the account chooser. Not an error to show. */
    CANCELLED,

    /** Deleting the account needs a fresh sign-in first. */
    RECENT_LOGIN_REQUIRED,

    /** No Google account on the device, or no network, or anything else that went wrong. */
    FAILED,
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val reason: AuthFailure) : AuthResult
}
