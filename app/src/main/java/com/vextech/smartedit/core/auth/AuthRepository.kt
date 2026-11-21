package com.vextech.smartedit.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * Google sign-in through Firebase Auth, the same account system the EditPro site uses: one Google login, account chooser every time.
 * Ordinary users and admins sign in the same way; what an account may do is decided by the Firestore rules (see [AdminRepository]).
 *
 * Nothing here needs code changes to turn on. Add google-services.json (with the Google sign-in provider enabled in Firebase) and the
 * generated `default_web_client_id` is picked up at run time. Without it [enabled] is false and the screens say accounts are not available.
 */
@Singleton
class AuthRepository @Inject constructor(@ApplicationContext private val context: Context) {
    /** The OAuth web client id Firebase generates from google-services.json, or empty when Firebase is not set up. */
    private val webClientId: String = run {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id == 0) "" else context.getString(id)
    }

    val enabled: Boolean = FirebaseApp.getApps(context).isNotEmpty() && webClientId.isNotBlank()

    val state: Flow<AuthState> =
        if (!enabled) {
            flowOf(AuthState(ready = true, enabled = false, user = null))
        } else {
            callbackFlow<AuthState> {
                val auth = FirebaseAuth.getInstance()
                val listener = FirebaseAuth.AuthStateListener { trySend(AuthState(ready = true, enabled = true, user = it.currentUser?.toAuthUser())) }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }.distinctUntilChanged()
        }

    /** Shows Google's account chooser, then signs in to Firebase with the chosen account. [activityContext] must be an Activity. */
    suspend fun signIn(activityContext: Context): AuthResult {
        if (!enabled) return AuthResult.Failure(AuthFailure.DISABLED)
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                AuthResult.Success
            } else {
                AuthResult.Failure(AuthFailure.FAILED)
            }
        } catch (e: GetCredentialCancellationException) {
            AuthResult.Failure(AuthFailure.CANCELLED)
        } catch (e: GetCredentialException) {
            AuthResult.Failure(AuthFailure.FAILED)
        } catch (e: Exception) {
            AuthResult.Failure(AuthFailure.FAILED)
        }
    }

    suspend fun signOut() {
        if (!enabled) return
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    /**
     * Removes the account's own records (the `users/<uid>` document and its subcollections, which the rules let only that person delete),
     * then the sign-in itself. Google can refuse the last step if the sign-in is old; the person is asked to sign in again and retry.
     */
    suspend fun deleteAccount(): AuthResult {
        if (!enabled) return AuthResult.Failure(AuthFailure.DISABLED)
        val user = FirebaseAuth.getInstance().currentUser ?: return AuthResult.Failure(AuthFailure.FAILED)
        return try {
            val db = FirebaseFirestore.getInstance()
            val mine = db.collection("users").document(user.uid)
            for (sub in listOf("unlocked", "notices")) {
                mine.collection(sub).get().await().documents.forEach { it.reference.delete().await() }
            }
            mine.delete().await()
            user.delete().await()
            AuthResult.Success
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            AuthResult.Failure(AuthFailure.RECENT_LOGIN_REQUIRED)
        } catch (e: Exception) {
            AuthResult.Failure(AuthFailure.FAILED)
        }
    }
}

private fun FirebaseUser.toAuthUser() = AuthUser(
    uid = uid,
    name = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "User",
    email = email?.let(AdminPolicies::normalizeEmail),
    emailVerified = isEmailVerified,
)
