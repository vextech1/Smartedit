package com.vextech.smartedit.core.auth

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Admin checks and the helper list, using the same collections and rules as the EditPro site (firestore.rules).
 *
 * The app cannot read the rules, so it asks the server what this account is allowed to do, with read-only questions:
 *  - the owner may read the `libraryAdmins` collection, nobody else may;
 *  - any admin (owner or helper) may list uploads that are not live yet, an ordinary account may not.
 * A "permission denied" answer means no. The server is asked every time (not the phone's cache) so a stale answer cannot grant anything.
 * The rules, not this code, are what actually protect the data.
 */
@Singleton
class AdminRepository @Inject constructor(private val auth: AuthRepository) {
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun role(user: AuthUser?): AdminRole {
        if (!auth.enabled || user == null || !user.emailVerified) return AdminRole.NONE
        if (allowed { db.collection(AdminPolicies.ADMINS_COLLECTION).limit(1).get(Source.SERVER).await() }) return AdminRole.OWNER
        if (allowed { db.collection("userFiles").whereEqualTo("status", "uploading").limit(1).get(Source.SERVER).await() }) return AdminRole.ADMIN
        return AdminRole.NONE
    }

    /** The helper admins, or null when this account is not the owner (only the owner may see or change the list). */
    suspend fun helpers(): List<String>? = try {
        val current = db.collection(AdminPolicies.ADMINS_COLLECTION).get(Source.SERVER).await().documents.map { it.id }
        val legacy = db.collection(AdminPolicies.LEGACY_ADMINS_COLLECTION).get(Source.SERVER).await().documents.map { it.id }
        AdminPolicies.sortedAdmins(current + legacy)
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
    }

    suspend fun addHelper(email: String) {
        db.collection(AdminPolicies.ADMINS_COLLECTION).document(AdminPolicies.normalizeEmail(email))
            .set(mapOf("added" to LocalDate.now().toString())).await()
    }

    suspend fun removeHelper(email: String) {
        val id = AdminPolicies.normalizeEmail(email)
        db.collection(AdminPolicies.ADMINS_COLLECTION).document(id).delete().await()
        db.collection(AdminPolicies.LEGACY_ADMINS_COLLECTION).document(id).delete().await()
    }

    /** True when the call worked, false on "permission denied". Any other failure (offline, ...) is passed on, never read as an answer. */
    private suspend fun allowed(call: suspend () -> Unit): Boolean = try {
        call()
        true
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) false else throw e
    }
}
