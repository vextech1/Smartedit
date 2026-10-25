package com.vextech.smart_edit.core.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.vextech.smart_edit.core.di.IoDispatcher
import com.vextech.smart_edit.core.files.DocumentStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DocumentStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private fun contentUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    fun open(file: File, mime: String) {
        val uri = contentUri(file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun share(file: File, mime: String) {
        val uri = contentUri(file)
        val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openManaged(uri: String, mime: String) {
        val file = managedFile(uri)
        open(file, mime)
    }

    fun shareManaged(uri: String, mime: String) {
        val file = managedFile(uri)
        share(file, mime)
    }

    fun managedFile(uri: String): File {
        val parsed = Uri.parse(uri)
        require(parsed.scheme == null || parsed.scheme == "file") { "Only Smart Edit managed files can be opened directly." }
        val file = File(requireNotNull(parsed.path) { "Invalid managed file URI." }).canonicalFile
        val managedRoot = store.root.canonicalFile
        require(file.path == managedRoot.path || file.path.startsWith(managedRoot.path + File.separator)) {
            "The file is outside Smart Edit managed storage."
        }
        require(file.isFile) { "The managed file is missing." }
        return file
    }

    /** Copies a managed file to a user-chosen document [destination] (from the system file picker). */
    suspend fun saveManagedTo(uri: String, destination: Uri) {
        val file = managedFile(uri)
        withContext(io) {
            val out = requireNotNull(context.contentResolver.openOutputStream(destination, "w")) { "Cannot open destination." }
            out.use { sink -> file.inputStream().use { it.copyTo(sink) } }
        }
    }

    fun shareText(text: String, subject: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).apply { subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) } }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
