package com.vextech.editpro.core.classlibrary

import android.content.Context
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Why an upload was not made, in words the person can read. [reason] finishes the sentence "This file was not added because ...". */
class UploadRefused(val code: String, val reason: String) : Exception(reason)

/**
 * A signed-in student's own Library files, written to the same Firestore documents the EditPro site uses:
 * `userFiles/<id>` (the record), `userChunks/<id>/parts/<n>` (the pieces) and `users/<uid>/notices` (messages from admins).
 * The order is the site's: record first (status `uploading`), then the pieces, then the status becomes `live`.
 */
@Singleton
class UserLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: ClassLibraryRepository,
) {
    val enabled: Boolean get() = library.enabled
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun upload(uid: String, bytes: ByteArray, meta: UploadMeta, onProgress: (Int, Int) -> Unit): String {
        (UserLibraryPolicies.checkUserUpload(meta.name, bytes.size.toLong()) as? Check.Refused)?.let { throw UploadRefused("invalid", it.reason) }
        if (!UserLibraryPolicies.looksLikeExt(UserLibraryPolicies.extOf(meta.name), bytes.copyOf(minOf(512, bytes.size)))) {
            throw UploadRefused("not-what-it-says", "its content is not a real .${UserLibraryPolicies.extOf(meta.name)} file")
        }
        val sha = UserLibraryPolicies.sha256Hex(bytes)
        val duplicate = db.collection("userFiles").whereEqualTo("sha", sha).whereEqualTo("status", "live").limit(1).get().await()
        if (!duplicate.isEmpty) throw UploadRefused("duplicate", "this document has already been shared. Please upload a different one")
        val settings = runCatching { db.collection("library").document("settings").get().await().data }.getOrNull()
        if (settings?.get("userUploads") == "closed") throw UploadRefused("closed", "uploads are paused right now. Please try again later")

        // Room: the same estimate as the site, so the project's free storage is never pushed over its limit.
        val adminUsed = UserLibraryPolicies.storageUsed(library.load(force = true).files)
        val live = db.collection("userFiles").whereEqualTo("status", "live")
        val aggregate = runCatching {
            live.aggregate(AggregateField.sum("size"), AggregateField.count()).get(AggregateSource.SERVER).await()
        }.getOrNull()
        val studentBytes = aggregate?.getDouble(AggregateField.sum("size"))?.toLong() ?: 0L
        val studentCount = aggregate?.get(AggregateField.count()) ?: 0L
        (UserLibraryPolicies.checkLibraryRoom(adminUsed, studentBytes, studentCount, bytes.size.toLong()) as? Check.Refused)
            ?.let { throw UploadRefused("storage-full", it.reason) }

        val chunks = UserLibraryPolicies.splitChunks(bytes)
        if (chunks.size > UserLibraryPolicies.userMaxParts()) throw UploadRefused("invalid", "it is too large")
        val id = UserLibraryPolicies.newFileId()
        val file = db.collection("userFiles").document(id)
        var created = false
        try {
            file.set(UserLibraryPolicies.buildUserEntry(uid, meta, bytes.size.toLong(), chunks.size, sha)).await()
            created = true
            chunks.forEachIndexed { i, chunk ->
                db.collection("userChunks").document(id).collection("parts").document(i.toString())
                    .set(mapOf("data" to Blob.fromBytes(chunk))).await()
                onProgress(i + 1, chunks.size)
            }
            file.update("status", "live").await()
        } catch (e: Exception) {
            if (created) runCatching { deleteParts(id, chunks.size); file.delete().await() }
            throw e
        }
        library.forget()
        return id
    }

    /** This account's files, newest first. Abandoned unfinished uploads are cleaned up on the way. */
    suspend fun myFiles(uid: String): List<UserFile> {
        val all = ArrayList<UserFile>()
        var cursor: com.google.firebase.firestore.DocumentSnapshot? = null
        while (true) {
            var q: Query = db.collection("userFiles").whereEqualTo("uploader", uid).limit(300)
            if (cursor != null) q = q.startAfter(cursor)
            val snap = q.get().await()
            snap.documents.mapNotNullTo(all) { UserLibraryPolicies.parseUserFile(it.id, it.data) }
            if (snap.size() < 300) break
            cursor = snap.documents.last()
        }
        val stale = UserLibraryPolicies.staleUploads(all)
        for (f in stale) runCatching { remove(f) }
        val gone = stale.map { it.id }.toSet()
        return all.filter { it.id !in gone }.sortedByDescending { it.added }
    }

    suspend fun remove(file: UserFile) {
        deleteParts(file.id, file.parts)
        db.collection("userFiles").document(file.id).delete().await()
        library.forget()
    }

    suspend fun notices(uid: String): List<Notice> =
        db.collection("users").document(uid).collection("notices").limit(50).get().await()
            .documents.mapNotNull { UserLibraryPolicies.parseNotice(it.id, it.data) }

    suspend fun dismissNotice(uid: String, id: String) {
        db.collection("users").document(uid).collection("notices").document(id).delete().await()
    }

    /** Deletes pieces 0 until [count], then any further pieces that exist, so a wrong count cannot strand data. */
    private suspend fun deleteParts(id: String, count: Int) {
        val parts = db.collection("userChunks").document(id).collection("parts")
        for (i in 0 until count) parts.document(i.toString()).delete().await()
        var i = count
        while (i < count + ClassLibraryParser.MAX_PARTS + 20 && parts.document(i.toString()).get().await().exists()) {
            parts.document(i.toString()).delete().await()
            i++
        }
    }
}
