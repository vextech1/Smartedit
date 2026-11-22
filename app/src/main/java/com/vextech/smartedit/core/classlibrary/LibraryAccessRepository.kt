package com.vextech.smartedit.core.classlibrary

import android.content.Context
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * The numbers behind [AccessPolicies]: the free download, remembered on this device, and, for a signed-in account, the files it has
 * unlocked, how many downloads it has used and how many of its uploads are live. These are the same Firestore documents the site uses
 * (`users/<uid>`, `users/<uid>/unlocked/<file id>` and `userFiles`), so an account's credits are shared between the site and the app.
 */
@Singleton
class LibraryAccessRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("class_library_access", Context.MODE_PRIVATE)
    private val db get() = FirebaseFirestore.getInstance()

    data class State(val unlocked: Boolean, val liveUploads: Int, val used: Int)

    /** The id of the file this device used its free download on, or null if it has not used it. */
    fun freeFileId(): String? = prefs.getString(KEY_FREE, null)?.takeIf { it.isNotBlank() }

    fun markFreeUsed(fileId: String) {
        if (freeFileId() == null) prefs.edit().putString(KEY_FREE, fileId).apply()
    }

    suspend fun state(uid: String, fileId: String): State = coroutineScope {
        val profile = db.collection("users").document(uid)
        val unlocked = async { profile.collection("unlocked").document(fileId).get().await().exists() }
        val used = async { (profile.get().await().getLong("used") ?: 0L).toInt() }
        val live = async {
            db.collection("userFiles").whereEqualTo("uploader", uid).whereEqualTo("status", "live")
                .count().get(AggregateSource.SERVER).await().count.toInt()
        }
        State(unlocked.await(), live.await(), used.await())
    }

    /** Records one download against the account, once per file: the unlock record and the counter change together, or not at all. */
    suspend fun unlock(uid: String, fileId: String) {
        val profile = db.collection("users").document(uid)
        val unlocked = profile.collection("unlocked").document(fileId)
        db.runTransaction { transaction ->
            if (!transaction.get(unlocked).exists()) {
                val used = transaction.get(profile).getLong("used") ?: 0L
                transaction.set(unlocked, mapOf("at" to System.currentTimeMillis()))
                transaction.set(profile, mapOf("used" to used + 1), SetOptions.merge())
            }
            null
        }.await()
    }

    private companion object {
        const val KEY_FREE = "free_file_id"
    }
}
