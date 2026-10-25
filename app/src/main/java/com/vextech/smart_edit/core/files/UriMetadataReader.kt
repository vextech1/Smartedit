package com.vextech.smart_edit.core.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class UriMetadataReader @Inject constructor(@ApplicationContext private val context: Context) {
    fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    data class Metadata(val name: String, val size: Long, val mimeType: String)
    fun read(uri: Uri): Metadata {
        if (uri.scheme == "file") {
            val file = java.io.File(requireNotNull(uri.path) { "Invalid file URI" })
            val extension = file.extension.lowercase()
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
            return Metadata(file.name, file.length(), mime)
        }
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        return Metadata(name, size.coerceAtLeast(0), context.contentResolver.getType(uri) ?: "application/octet-stream")
    }
}
