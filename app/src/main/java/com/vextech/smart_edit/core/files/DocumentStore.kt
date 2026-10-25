package com.vextech.smart_edit.core.files

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.vextech.smart_edit.core.util.CorePolicies
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver
    val root: File get() = File(context.filesDir, "smartedit").apply { mkdirs() }
    val documentsDir: File get() = File(root, "documents").apply { mkdirs() }
    val scansDir: File get() = File(root, "scans").apply { mkdirs() }
    val convertedDir: File get() = File(root, "converted").apply { mkdirs() }
    val compressedDir: File get() = File(root, "compressed").apply { mkdirs() }
    val editedDir: File get() = File(root, "edited").apply { mkdirs() }
    val backupsDir: File get() = File(root, "backups").apply { mkdirs() }
    val trashDir: File get() = File(root, "trash").apply { mkdirs() }

    fun uniqueFile(dir: File, preferredName: String): File {
        val clean = sanitizeName(preferredName)
        val ext = clean.substringAfterLast('.', "")
        val stem = if (ext.isEmpty()) clean else clean.removeSuffix(".$ext")
        var candidate = File(dir, clean)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "$stem ($index)" else "$stem ($index).$ext")
            index++
        }
        return candidate
    }

    fun sanitizeName(name: String): String = CorePolicies.safeManagedName(name, "SmartEdit_${System.currentTimeMillis()}")

    fun copyIntoManaged(source: Uri, preferredName: String, dir: File = documentsDir): File {
        val dest = uniqueFile(dir, preferredName)
        resolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Unable to open $source" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    fun copyFile(source: File, targetDir: File, preferredName: String = source.name): File {
        val dest = uniqueFile(targetDir, preferredName)
        source.copyTo(dest)
        return dest
    }

    fun writeBitmap(bitmap: Bitmap, dir: File, name: String, format: Bitmap.CompressFormat, quality: Int): File {
        val file = uniqueFile(dir, name)
        FileOutputStream(file).use { require(bitmap.compress(format, quality, it)) { "Bitmap compression failed" } }
        return file
    }

    fun temporaryFile(preferredName: String): File = uniqueFile(File(context.cacheDir, "exports").apply { mkdirs() }, preferredName)
    fun temporaryDirectory(prefix: String = "work"): File = File(context.cacheDir, "smartedit_${prefix}_${UUID.randomUUID()}").apply { mkdirs() }

    fun shareUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    fun uri(file: File): Uri = Uri.fromFile(file)
    fun asFile(uri: String): File? = runCatching {
        val parsed = Uri.parse(uri)
        when (parsed.scheme) {
            null, "file" -> File(parsed.path!!)
            else -> null
        }
    }.getOrNull()

    fun stageContent(uri: Uri, suffix: String = ".tmp"): File {
        val file = File(context.cacheDir, "stage_${UUID.randomUUID()}$suffix")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to read $uri" }
            file.outputStream().use(input::copyTo)
        }
        return file
    }
}
