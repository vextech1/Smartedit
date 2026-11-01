package com.vextech.smart_edit.core.backup

import androidx.room.withTransaction
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.*
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.model.AppSettings
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ThemePreference
import com.vextech.smart_edit.core.repository.SmartEditRepository
import com.vextech.smart_edit.core.preferences.SettingsRepository
import com.vextech.smart_edit.core.work.WorkScheduler
import com.vextech.smart_edit.core.util.CorePolicies
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupService @Inject constructor(
    private val db: SmartEditDatabase,
    private val store: DocumentStore,
    private val repository: SmartEditRepository,
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler,
) {
    companion object { const val SCHEMA_VERSION = 2 }

    suspend fun createBackup(): File {
        val now = System.currentTimeMillis()
        val out = store.uniqueFile(store.backupsDir, "SmartEdit_$now.smarteditbackup")
        val data = snapshotJson()
        val checksums = JSONObject()
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            putText(zip, "manifest.json", JSONObject().put("schemaVersion", SCHEMA_VERSION).put("createdAt", now).put("app", "Smart Edit").toString())
            putText(zip, "metadata/state.json", data.toString())
            val dirs = listOf("documents" to store.documentsDir, "scans" to store.scansDir, "converted" to store.convertedDir, "compressed" to store.compressedDir, "edited" to store.editedDir, "trash" to store.trashDir)
            dirs.forEach { (prefix, dir) ->
                dir.walkTopDown().filter(File::isFile).forEach { file ->
                    val rel = file.relativeTo(dir).invariantSeparatorsPath
                    val entryName = "files/$prefix/$rel"
                    zip.putNextEntry(ZipEntry(entryName)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    checksums.put(entryName, sha256(file))
                }
            }
            putText(zip, "checksums.json", checksums.toString())
        }
        db.backupDao().upsert(BackupEntity(UUID.randomUUID().toString(), out.toURI().toString(), out.length(), now, SCHEMA_VERSION))
        repository.recordHistory(HistoryAction.BACKED_UP, "Created backup ${out.name}", displayName = out.name, uri = out.toURI().toString())
        return out
    }

    suspend fun restoreBackup(archive: File) {
        require(archive.exists())
        val previousState = snapshotJson()
        val staging = File(store.root.parentFile, "smartedit_restore_${UUID.randomUUID()}").apply { mkdirs() }
        val previous = File(store.root.parentFile, "smartedit_previous_${UUID.randomUUID()}")
        try {
            ZipFile(archive).use { zip ->
                val manifest = JSONObject(zip.getInputStream(requireNotNull(zip.getEntry("manifest.json"))).bufferedReader().readText())
                require(manifest.getInt("schemaVersion") <= SCHEMA_VERSION) { "Backup comes from a newer Smart Edit version" }
                val checksums = JSONObject(zip.getInputStream(requireNotNull(zip.getEntry("checksums.json"))).bufferedReader().readText())
                zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("files/") }.forEach { entry ->
                    val relative = entry.name.removePrefix("files/")
                    require(CorePolicies.isSafeArchiveEntry(relative)) { "Unsafe backup entry: ${entry.name}" }
                    val target = File(staging, relative)
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
                    val expected = checksums.optString(entry.name, "")
                    require(expected.isBlank() || sha256(target) == expected) { "Backup checksum failed for ${entry.name}" }
                }
                val state = JSONObject(zip.getInputStream(requireNotNull(zip.getEntry("metadata/state.json"))).bufferedReader().readText())

                // Stage file replacement first, while keeping a rollback copy.
                previous.mkdirs()
                listOf("documents","scans","converted","compressed","edited","trash").forEach { name ->
                    val current = File(store.root, name)
                    if (current.exists()) require(current.renameTo(File(previous, name))) { "Unable to stage current $name" }
                    val incoming = File(staging, name)
                    if (incoming.exists()) require(incoming.renameTo(File(store.root, name))) { "Unable to restore $name" }
                    else File(store.root, name).mkdirs()
                }
                try {
                    restoreJson(state)
                    previous.deleteRecursively()
                } catch (t: Throwable) {
                    listOf("documents","scans","converted","compressed","edited","trash").forEach { name ->
                        File(store.root, name).deleteRecursively()
                        val old = File(previous, name)
                        if (old.exists()) old.renameTo(File(store.root, name))
                    }
                    try { restoreJson(previousState) } catch (_: Throwable) { /* preserve original restore failure */ }
                    throw t
                }
            }
        } finally {
            staging.deleteRecursively()
            previous.deleteRecursively()
        }
    }

    private suspend fun snapshotJson(): JSONObject = JSONObject().apply {
        put("documents", JSONArray(db.documentDao().snapshot().map { JSONObject().put("id",it.id).put("displayName",it.displayName).put("uri",it.uri).put("mimeType",it.mimeType).put("sizeBytes",it.sizeBytes).put("createdAt",it.createdAt).put("modifiedAt",it.modifiedAt).put("parentId",it.parentId).put("isDirectory",it.isDirectory).put("source",it.source) }))
        put("notes", JSONArray(db.noteDao().snapshot().map { JSONObject().put("id",it.id).put("title",it.title).put("content",it.content).put("tagsCsv",it.tagsCsv).put("createdAt",it.createdAt).put("updatedAt",it.updatedAt).put("isPinned",it.isPinned).put("isFavorite",it.isFavorite).put("isDeleted",it.isDeleted).put("deletedAt",it.deletedAt) }))
        put("favorites", JSONArray(db.favoriteDao().snapshot().map { JSONObject().put("id",it.id).put("resourceType",it.resourceType).put("resourceId",it.resourceId).put("displayName",it.displayName).put("uri",it.uri).put("createdAt",it.createdAt) }))
        put("history", JSONArray(db.historyDao().snapshot().map { JSONObject().put("id",it.id).put("action",it.action).put("description",it.description).put("resourceType",it.resourceType).put("resourceId",it.resourceId).put("uri",it.uri).put("displayName",it.displayName).put("timestamp",it.timestamp) }))
        put("trash", JSONArray(db.trashDao().snapshot().map { JSONObject().put("id",it.id).put("resourceType",it.resourceType).put("resourceId",it.resourceId).put("displayName",it.displayName).put("originalUri",it.originalUri).put("trashedUri",it.trashedUri).put("sizeBytes",it.sizeBytes).put("trashedAt",it.trashedAt).put("autoDeleteAt",it.autoDeleteAt) }))
        put("scans", JSONArray(db.scanDao().snapshot().map { JSONObject().put("id",it.id).put("imageUri",it.imageUri).put("pdfUri",it.pdfUri).put("ocrText",it.ocrText).put("pageCount",it.pageCount).put("createdAt",it.createdAt) }))
        put("jobs", JSONArray(db.jobDao().snapshot().map { JSONObject().put("id",it.id).put("kind",it.kind).put("sourceUri",it.sourceUri).put("outputUri",it.outputUri).put("sourceFormat",it.sourceFormat).put("targetFormat",it.targetFormat).put("sourceSizeBytes",it.sourceSizeBytes).put("outputSizeBytes",it.outputSizeBytes).put("progress",it.progress.toDouble()).put("status",it.status).put("createdAt",it.createdAt).put("completedAt",it.completedAt).put("errorMessage",it.errorMessage) }))
        val appSettings = settingsRepository.settings.first()
        put("settings", JSONObject().put("theme", appSettings.theme.name).put("language", appSettings.language).put("notificationsEnabled", appSettings.notificationsEnabled).put("autoBackupEnabled", appSettings.autoBackupEnabled).put("confirmDeleteEnabled", appSettings.confirmDeleteEnabled).put("defaultExportFormat", appSettings.defaultExportFormat).put("onboardingCompleted", appSettings.onboardingCompleted))
    }

    private suspend fun restoreJson(state: JSONObject) {
        db.withTransaction {
            db.searchDao().clear(); db.favoriteDao().clear(); db.historyDao().clear(); db.trashDao().clear(); db.scanDao().clear(); db.jobDao().clear(); db.noteDao().clear(); db.documentDao().clear()
            state.getJSONArray("documents").forEachObject { o -> db.documentDao().upsert(DocumentEntity(o.s("id"),o.s("displayName"),rewriteUri(o.s("uri")),o.s("mimeType"),o.l("sizeBytes"),o.l("createdAt"),o.l("modifiedAt"),o.ns("parentId"),o.b("isDirectory"),o.s("source"))) }
            state.getJSONArray("notes").forEachObject { o -> db.noteDao().upsert(NoteEntity(o.s("id"),o.s("title"),o.s("content"),o.s("tagsCsv"),o.l("createdAt"),o.l("updatedAt"),o.b("isPinned"),o.b("isFavorite"),o.b("isDeleted"),o.nl("deletedAt"))) }
            state.getJSONArray("favorites").forEachObject { o -> db.favoriteDao().upsert(FavoriteEntity(o.s("id"),o.s("resourceType"),o.s("resourceId"),o.s("displayName"),o.ns("uri")?.let(::rewriteUri),o.l("createdAt"))) }
            state.getJSONArray("history").forEachObject { o -> db.historyDao().insert(HistoryEntity(o.s("id"),o.s("action"),o.s("description"),o.ns("resourceType"),o.ns("resourceId"),o.ns("uri")?.let(::rewriteUri),o.ns("displayName"),o.l("timestamp"))) }
            state.getJSONArray("trash").forEachObject { o -> db.trashDao().upsert(TrashEntity(o.s("id"),o.s("resourceType"),o.s("resourceId"),o.s("displayName"),rewriteUri(o.s("originalUri")),rewriteUri(o.s("trashedUri")),o.l("sizeBytes"),o.l("trashedAt"),o.nl("autoDeleteAt"))) }
            state.getJSONArray("scans").forEachObject { o -> db.scanDao().upsert(ScanEntity(o.s("id"),rewriteUri(o.s("imageUri")),o.ns("pdfUri")?.let(::rewriteUri),o.ns("ocrText"),o.i("pageCount"),o.l("createdAt"))) }
            state.getJSONArray("jobs").forEachObject { o -> db.jobDao().upsert(ProcessingJobEntity(o.s("id"),o.s("kind"),rewriteUri(o.s("sourceUri")),o.ns("outputUri")?.let(::rewriteUri),o.s("sourceFormat"),o.s("targetFormat"),o.l("sourceSizeBytes"),o.nl("outputSizeBytes"),o.d("progress").toFloat(),o.s("status"),o.l("createdAt"),o.nl("completedAt"),o.ns("errorMessage"))) }
            db.documentDao().snapshot().forEach { db.searchDao().insert(SearchIndexEntity(it.id, "DOCUMENT", it.displayName, it.displayName, it.uri)) }
            db.noteDao().snapshot().filterNot { it.isDeleted }.forEach { db.searchDao().insert(SearchIndexEntity(it.id, "NOTE", it.title, "${it.content} ${it.tagsCsv}", null)) }
        }
        state.optJSONObject("settings")?.let { o ->
            val restored = AppSettings(
                theme = runCatching { ThemePreference.valueOf(o.optString("theme", "SYSTEM")) }.getOrDefault(ThemePreference.SYSTEM),
                language = o.optString("language", "en"),
                notificationsEnabled = o.optBoolean("notificationsEnabled", true),
                autoBackupEnabled = o.optBoolean("autoBackupEnabled", false),
                confirmDeleteEnabled = o.optBoolean("confirmDeleteEnabled", true),
                defaultExportFormat = o.optString("defaultExportFormat", "pdf"),
                onboardingCompleted = o.optBoolean("onboardingCompleted", true),
            )
            settingsRepository.replace(restored)
            workScheduler.setAutoBackup(restored.autoBackupEnabled)
        }
    }

    private fun rewriteUri(uri: String): String {
        if (!uri.startsWith("file:")) return uri
        val old = runCatching { File(java.net.URI(uri)) }.getOrNull() ?: return uri
        val marker = "/smartedit/"
        val idx = old.absolutePath.lastIndexOf(marker)
        if (idx < 0) return uri
        return File(store.root, old.absolutePath.substring(idx + marker.length)).toURI().toString()
    }

    private fun putText(zip: ZipOutputStream, name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { md -> file.inputStream().use { input -> val b=ByteArray(64*1024); while(true){ val n=input.read(b); if(n<0) break; md.update(b,0,n) } }; md.digest().joinToString("") { "%02x".format(it) } }
    private inline fun JSONArray.forEachObject(block: (JSONObject)->Unit) { for(i in 0 until length()) block(getJSONObject(i)) }
    private fun JSONObject.s(k:String)=getString(k)
    private fun JSONObject.ns(k:String)=if(!has(k) || isNull(k)) null else getString(k)
    private fun JSONObject.l(k:String)=getLong(k)
    private fun JSONObject.nl(k:String)=if(isNull(k)) null else getLong(k)
    private fun JSONObject.i(k:String)=getInt(k)
    private fun JSONObject.d(k:String)=getDouble(k)
    private fun JSONObject.b(k:String)=getBoolean(k)
}
