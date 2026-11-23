package com.vextech.smartedit.core.classlibrary

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * What the Library's owner and helper admins can do, written to the same documents the site's admin page writes:
 * the index (`library/index`, `index-1`, ...), the pieces (`libraryChunks`), the cleanup list (`library/trash`), the upload switch
 * (`library/settings`) and the review of student files (`userFiles`, with a notice to the uploader). The Firestore rules allow
 * these only for the owner and the helpers, so a non-admin gets "permission denied".
 */
@Singleton
class AdminLibraryRepository @Inject constructor(private val library: ClassLibraryRepository) {
    private val db get() = FirebaseFirestore.getInstance()
    private val trash get() = db.collection("library").document("trash")

    // ---- the admin's own files ----

    /** Adds one file. The pieces go first and the index entry last, so students never see a half-uploaded file. */
    suspend fun upload(bytes: ByteArray, meta: UploadMeta, onProgress: (Int, Int) -> Unit): String {
        (UserLibraryPolicies.checkAdminUpload(meta.name, bytes.size.toLong()) as? Check.Refused)?.let { throw UploadRefused("invalid", it.reason) }
        val chunks = UserLibraryPolicies.splitChunks(bytes)
        if (chunks.isEmpty() || chunks.size > ClassLibraryParser.MAX_PARTS + 1) throw UploadRefused("invalid", "it is empty or too large")

        // Where the entry goes. The whole index is read fresh: a missing part would make the choice wrong, so stop instead of guessing.
        val docs = readIndexDocs()
        val existing = indexFiles(docs)
        if (existing.any { sameScope(it, meta) && it.name.trim().equals(meta.name.trim(), ignoreCase = true) }) {
            throw UploadRefused("exists", "a file with this name is already in that folder. Delete it first or rename the new one")
        }
        val shard = UserLibraryPolicies.chooseShard(docs.map { JSONObject(it ?: emptyMap<String, Any>()).toString().length }, UserLibraryPolicies.entryBytesFor(meta.name))
            ?: throw UploadRefused("index-full", "the Library index has no room for more files. Delete some files first")
        val shardCount = docs.size

        val id = UserLibraryPolicies.newFileId()
        markTrash(id, chunks.size, "upload")   // before any piece is written, so a crash cannot leave pieces nothing knows about
        var indexTouched = false
        try {
            val parts = db.collection("libraryChunks").document(id).collection("parts")
            chunks.forEachIndexed { i, chunk ->
                parts.document(ClassLibraryParser.partId(i)).set(mapOf("data" to Blob.fromBytes(chunk))).await()
                onProgress(i + 1, chunks.size)
            }
            if (shard >= shardCount) {
                db.collection("library").document("index").set(mapOf("shards" to (shard + 1).toLong()), SetOptions.merge()).await()
            }
            indexTouched = true
            val entry = UserLibraryPolicies.buildLibraryEntry(meta, bytes.size.toLong(), chunks.size)
            db.collection("library").document(ClassLibraryParser.shardDocName(shard))
                .set(mapOf("files" to mapOf(id to entry)), SetOptions.merge()).await()
        } catch (e: Exception) {
            if (indexTouched) {
                // The index write may have gone through even though an error came back; the cleanup list decides later, after a fresh look.
                runCatching { markTrash(id, chunks.size, "delete") }
            } else {
                runCatching { deleteParts("libraryChunks", id, chunks.size, padded = true); clearTrash(id) }
                    .onFailure { runCatching { markTrash(id, chunks.size, "delete") } }
            }
            throw e
        }
        runCatching { clearTrash(id) }
        library.forget()
        return id
    }

    /** Deletes a file: it is listed for cleanup first, then its index entry goes (so it vanishes for students at once), then its pieces. */
    suspend fun deleteFile(file: CloudFile) {
        require(!file.student) { "Student files are removed with deleteStudentFile." }
        markTrash(file.id, file.parts, "delete")
        try {
            db.collection("library").document(ClassLibraryParser.shardDocName(maxOf(0, file.shard)))
                .set(mapOf("files" to mapOf(file.id to FieldValue.delete())), SetOptions.merge()).await()
        } catch (e: Exception) {
            runCatching { clearTrash(file.id) }
            throw e
        }
        deleteParts("libraryChunks", file.id, file.parts, padded = true)
        runCatching { clearTrash(file.id) }
        library.forget()
    }

    // ---- students' files ----

    suspend fun studentFiles(status: String): List<UserFile> {
        val out = ArrayList<UserFile>()
        var cursor: com.google.firebase.firestore.DocumentSnapshot? = null
        while (true) {
            var q: Query = db.collection("userFiles").whereEqualTo("status", status).limit(200)
            if (cursor != null) q = q.startAfter(cursor)
            val snap = q.get().await()
            snap.documents.mapNotNullTo(out) { UserLibraryPolicies.parseUserFile(it.id, it.data) }
            if (snap.size() < 200) break
            cursor = snap.documents.last()
        }
        return out.sortedByDescending { it.added }
    }

    /** Removes a live student file and tells its uploader why. Returns whether the pieces were also cleaned up. */
    suspend fun pull(file: UserFile, reason: String): Boolean {
        val (ok, text) = UserLibraryPolicies.checkReason(reason)
        require(ok) { text }
        require(file.status == "live") { "Only a live file can be pulled." }
        val noticeId = UserLibraryPolicies.newFileId()
        val batch = db.batch()
        batch.update(db.collection("userFiles").document(file.id), mapOf("status" to "pulled", "pulledReason" to text))
        batch.set(
            db.collection("users").document(file.uploader).collection("notices").document(noticeId),
            mapOf("kind" to "pulled", "title" to file.title.take(120), "reason" to text, "at" to Instant.now().toString()),
        )
        batch.commit().await()
        library.forget()
        return retryPieces(file)
    }

    suspend fun retryPieces(file: UserFile): Boolean = runCatching {
        deleteParts("userChunks", file.id, file.parts, padded = false)
        db.collection("userFiles").document(file.id).update("cleaned", true).await()
    }.isSuccess

    suspend fun deleteRecord(file: UserFile) {
        deleteParts("userChunks", file.id, file.parts, padded = false)
        db.collection("userFiles").document(file.id).delete().await()
        library.forget()
    }

    // ---- the upload switch ----

    suspend fun userUploadsOpen(): Boolean =
        db.collection("library").document("settings").get().await().data?.get("userUploads") != "closed"

    suspend fun setUserUploadsOpen(open: Boolean) {
        db.collection("library").document("settings").set(mapOf("userUploads" to if (open) "open" else "closed"), SetOptions.merge()).await()
    }

    // ---- helpers ----

    private fun sameScope(f: CloudFile, m: UploadMeta) = f.sectionId == m.sectionId && f.classId == m.classId && (f.subjectId ?: "") == (m.subjectId ?: "")

    private suspend fun readIndexDocs(): List<Map<String, Any?>?> {
        val first = db.collection("library").document(ClassLibraryParser.shardDocName(0)).get().await().data
        val count = ClassLibraryParser.parseShardCount(first)
        val rest = (1 until count).map { n ->
            db.collection("library").document(ClassLibraryParser.shardDocName(n)).get().await().data
        }
        return listOf(first) + rest
    }

    private fun indexFiles(docs: List<Map<String, Any?>?>): List<CloudFile> =
        docs.flatMapIndexed { shard, d -> ClassLibraryParser.parseIndex(d, shard) }

    private suspend fun markTrash(id: String, parts: Int, kind: String) {
        trash.set(mapOf("pending" to mapOf(id to mapOf("parts" to parts.toLong(), "kind" to kind, "at" to System.currentTimeMillis()))), SetOptions.merge()).await()
    }

    private suspend fun clearTrash(id: String) {
        trash.set(mapOf("pending" to mapOf(id to FieldValue.delete())), SetOptions.merge()).await()
    }

    private suspend fun deleteParts(collection: String, id: String, count: Int, padded: Boolean) {
        val parts = db.collection(collection).document(id).collection("parts")
        fun key(i: Int) = if (padded) ClassLibraryParser.partId(i) else i.toString()
        for (i in 0 until count) parts.document(key(i)).delete().await()
        var i = count
        while (i < count + ClassLibraryParser.MAX_PARTS + 20 && parts.document(key(i)).get().await().exists()) {
            parts.document(key(i)).delete().await()
            i++
        }
    }
}
