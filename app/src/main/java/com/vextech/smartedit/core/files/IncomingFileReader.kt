package com.vextech.smartedit.core.files

import android.content.Context
import android.net.Uri
import com.vextech.smartedit.core.library.IncomingFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns picked or shared [Uri]s into [IncomingFile]s the tools can open later. */
@Singleton
class IncomingFileReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadata: UriMetadataReader,
) {
    /**
     * Files chosen with the system picker. The read grant is made lasting, so a tool can still open the file after the app
     * is closed and reopened (a plain picker grant ends with the screen that received it).
     */
    suspend fun describePicked(uris: List<Uri>): List<IncomingFile> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            metadata.persistReadPermission(uri)
            runCatching { metadata.read(uri) }.getOrNull()?.let { IncomingFile(uri.toString(), it.name, it.mimeType) }
        }
    }

    /**
     * Files shared to the app or opened with it. Their grant ends when the sending app's request ends and cannot be made lasting,
     * so each one is copied into the app's own cache first and the tools open that copy.
     */
    suspend fun importShared(uris: List<Uri>): List<IncomingFile> = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, INCOMING_DIR)
        pruneOld(root)
        uris.mapNotNull { uri ->
            runCatching {
                val meta = metadata.read(uri)
                val folder = File(root, System.nanoTime().toString()).apply { mkdirs() }
                val target = File(folder, safeName(meta.name))
                requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open $uri" }.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
                IncomingFile(Uri.fromFile(target).toString(), target.name, meta.mimeType)
            }.getOrNull()
        }
    }

    private fun pruneOld(root: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    companion object {
        private const val INCOMING_DIR = "incoming"
        private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

        fun safeName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().ifEmpty { "file" }.take(120)
    }
}
