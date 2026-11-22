package com.vextech.smartedit.core.classlibrary

import android.content.Context
import android.os.SystemClock
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Reads the Class Library from Firestore: the catalog (`library/structure`), the file index (`library/index`, `index-1`, ...) and the pieces of
 * each file (`libraryChunks/<id>/parts/<0000...>`). It only ever reads; uploads and admin changes are not part of the app yet.
 * The list is kept for [CACHE_MS] so switching tabs does not read it again.
 */
@Singleton
class ClassLibraryRepository @Inject constructor(@ApplicationContext private val context: Context) {
    /** False until the Firebase side is set up (google-services.json). The Library then says it is not available instead of failing. */
    val enabled: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    private val db get() = FirebaseFirestore.getInstance()
    private val lock = Mutex()
    private var cached: LibraryData? = null
    private var cachedAt = 0L

    suspend fun load(force: Boolean = false): LibraryData = lock.withLock {
        val now = SystemClock.elapsedRealtime()
        val kept = cached
        if (!force && kept != null && now - cachedAt < CACHE_MS) return kept
        val fresh = fetch()
        cached = fresh
        cachedAt = now
        fresh
    }

    private suspend fun fetch(): LibraryData = coroutineScope {
        val library = db.collection("library")
        val firstDoc = async { library.document(ClassLibraryParser.shardDocName(0)).get().await().data }
        val structureDoc = async { library.document("structure").get().await().data }
        val first = firstDoc.await()
        val more = (1 until ClassLibraryParser.parseShardCount(first)).map { n ->
            async { runCatching { library.document(ClassLibraryParser.shardDocName(n)).get().await().data } }
        }.awaitAll()
        val seen = HashSet<String>()
        val files = ArrayList<CloudFile>()
        (listOf(first) + more.map { it.getOrNull() }).forEachIndexed { shard, data ->
            for (file in ClassLibraryParser.parseIndex(data, shard)) if (seen.add(file.id)) files += file
        }
        LibraryData(ClassLibraryParser.parseStructure(structureDoc.await()), files, partial = more.any { it.isFailure })
    }

    /** Downloads every piece of [file] (a few at a time) and joins them. [onProgress] gets (pieces done, total). */
    suspend fun download(file: CloudFile, onProgress: (Int, Int) -> Unit): ByteArray = coroutineScope {
        val pieces = arrayOfNulls<ByteArray>(file.parts)
        val next = AtomicInteger(0)
        val done = AtomicInteger(0)
        val parts = db.collection("libraryChunks").document(file.id).collection("parts")
        // Each worker takes the next piece until none are left. They are awaited before the pieces are joined, and a failed piece
        // cancels the others and is thrown from here.
        List(minOf(PARALLEL, file.parts)) {
            async {
                while (true) {
                    val i = next.getAndIncrement()
                    if (i >= file.parts) break
                    val snapshot = parts.document(ClassLibraryParser.partId(i)).get().await()
                    val blob = snapshot.getBlob("data") ?: throw IOException("Piece $i of ${file.id} is missing.")
                    pieces[i] = blob.toBytes()
                    onProgress(done.incrementAndGet(), file.parts)
                }
            }
        }.awaitAll()
        ClassLibraryParser.joinChunks(pieces.map { it ?: throw IOException("A piece of ${file.id} is missing.") })
    }

    private companion object {
        const val CACHE_MS = 5 * 60 * 1000L
        const val PARALLEL = 4
    }
}
