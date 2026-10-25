package com.vextech.smart_edit.core.repository

import androidx.room.withTransaction
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.DocumentEntity
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.library.TrashItem
import com.vextech.smart_edit.core.library.TrashKind
import com.vextech.smart_edit.core.library.TrashPolicies
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Unified trash: files (moved there by the Files feature) and notes. */
@Singleton
class TrashRepository @Inject constructor(
    private val db: SmartEditDatabase,
    private val store: DocumentStore,
    private val indexer: SearchIndexer,
    private val history: HistoryRepository,
    private val notes: NotesRepository,
) {
    val items: Flow<List<TrashItem>> = combine(db.trashDao().observeAll(), db.noteDao().observeTrash()) { files, trashedNotes ->
        val now = System.currentTimeMillis()
        val fileItems = files.map { f ->
            TrashItem(f.id, TrashKind.FILE, f.displayName, f.sizeBytes, f.trashedAt, TrashPolicies.daysLeft(now, f.autoDeleteAt))
        }
        val noteItems = trashedNotes.map { n ->
            val deletedAt = n.deletedAt ?: n.updatedAt
            TrashItem(n.id, TrashKind.NOTE, n.title, null, deletedAt, TrashPolicies.daysLeft(now, TrashPolicies.noteExpiresAt(deletedAt)))
        }
        TrashPolicies.sortNewestFirst(fileItems + noteItems)
    }

    suspend fun restore(item: TrashItem) = when (item.kind) {
        TrashKind.FILE -> restoreFile(item.id)
        TrashKind.NOTE -> notes.restore(item.id)
    }

    suspend fun deleteForever(item: TrashItem) = when (item.kind) {
        TrashKind.FILE -> deleteFileForever(item.id)
        TrashKind.NOTE -> notes.deleteForever(item.id)
    }

    /** Empties files, notes, or both. Returns how many items were removed. */
    suspend fun empty(kind: TrashKind? = null): Int {
        var removed = 0
        if (kind == null || kind == TrashKind.FILE) {
            val all = db.trashDao().snapshot()
            all.forEach { deleteFileForever(it.id) }
            removed += all.size
        }
        if (kind == null || kind == TrashKind.NOTE) removed += notes.emptyTrash()
        return removed
    }

    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): Int {
        val expiredFiles = db.trashDao().expired(now)
        expiredFiles.forEach { deleteFileForever(it.id) }
        return expiredFiles.size + notes.purgeExpired(now)
    }

    suspend fun restoreFile(trashId: String) {
        val item = requireNotNull(db.trashDao().get(trashId))
        val source = requireNotNull(store.asFile(item.trashedUri)) { "Trash item is no longer available" }
        require(source.exists()) { "Trash item is no longer available" }

        val all = db.documentDao().snapshot()
        val retainedRoot = all.firstOrNull { it.id == item.resourceId && it.isTrashed }
        val retainedDescendants = retainedRoot?.takeIf { it.isDirectory }?.let { descendantsOf(it.id, all) }.orEmpty()
        val original = requireNotNull(store.asFile(item.originalUri))
        val activeParent = retainedRoot?.parentId
            ?.let { id -> all.firstOrNull { it.id == id && it.isDirectory && !it.isTrashed } }
            ?.let { store.asFile(it.uri) }
        val restoredParent = when {
            retainedRoot?.parentId == null -> original.parentFile ?: store.documentsDir
            activeParent != null -> activeParent
            else -> store.documentsDir
        }
        val requested = File(restoredParent, original.name)
        requested.parentFile?.mkdirs()
        val target = if (requested.exists()) store.uniqueFile(requested.parentFile ?: store.documentsDir, requested.name) else requested
        require(source.renameTo(target)) { "Unable to restore file" }
        val now = System.currentTimeMillis()

        db.withTransaction {
            if (retainedRoot != null) {
                val rootUpdate = retainedRoot.copy(
                    displayName = target.name,
                    uri = target.toURI().toString(),
                    parentId = retainedRoot.parentId?.takeIf { id -> all.any { it.id == id && !it.isTrashed } },
                    modifiedAt = now,
                    isTrashed = false,
                )
                db.documentDao().upsert(rootUpdate)
                indexer.replace(rootUpdate.id, ResourceType.DOCUMENT, rootUpdate.displayName, rootUpdate.displayName, rootUpdate.uri)
                retainedDescendants.forEach { child ->
                    val oldChild = requireNotNull(store.asFile(child.uri))
                    val relative = oldChild.relativeTo(source).path
                    val restoredChild = child.copy(
                        uri = File(target, relative).toURI().toString(),
                        modifiedAt = now,
                        isTrashed = false,
                    )
                    db.documentDao().upsert(restoredChild)
                    indexer.replace(restoredChild.id, ResourceType.DOCUMENT, restoredChild.displayName, restoredChild.displayName, restoredChild.uri)
                }
                history.record(HistoryAction.RESTORED, "Restored ${rootUpdate.displayName}", ResourceType.DOCUMENT, rootUpdate.id, rootUpdate.uri, rootUpdate.displayName)
            } else {
                val parentId = db.documentDao().activeSnapshot().firstOrNull { it.isDirectory && store.asFile(it.uri) == target.parentFile }?.id
                val doc = DocumentEntity(
                    id = item.resourceId,
                    displayName = target.name,
                    uri = target.toURI().toString(),
                    mimeType = if (target.isDirectory) "inode/directory" else guessMime(target),
                    sizeBytes = if (target.isDirectory) 0 else target.length(),
                    createdAt = now,
                    modifiedAt = now,
                    parentId = parentId,
                    isDirectory = target.isDirectory,
                )
                db.documentDao().upsert(doc)
                indexer.replace(doc.id, ResourceType.DOCUMENT, doc.displayName, doc.displayName, doc.uri)
                history.record(HistoryAction.RESTORED, "Restored ${doc.displayName}", ResourceType.DOCUMENT, doc.id, doc.uri, doc.displayName)
            }
            db.trashDao().delete(trashId)
        }
    }

    suspend fun deleteFileForever(trashId: String) {
        val item = db.trashDao().get(trashId) ?: return
        store.asFile(item.trashedUri)?.deleteRecursively()
        val all = db.documentDao().snapshot()
        val root = all.firstOrNull { it.id == item.resourceId && it.isTrashed }
        val affected = if (root == null) emptyList() else listOf(root) + if (root.isDirectory) descendantsOf(root.id, all) else emptyList()
        db.withTransaction {
            affected.forEach { document ->
                db.documentDao().delete(document.id)
                indexer.remove(document.id)
                db.favoriteDao().delete(ResourceType.DOCUMENT.name, document.id)
            }
            db.trashDao().delete(trashId)
        }
    }

    private fun descendantsOf(parentId: String, all: List<DocumentEntity>): List<DocumentEntity> {
        val result = mutableListOf<DocumentEntity>()
        val queue = ArrayDeque<String>()
        queue.add(parentId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            all.filter { it.parentId == current }.forEach { child ->
                result += child
                if (child.isDirectory) queue.add(child.id)
            }
        }
        return result
    }

    private fun guessMime(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "txt" -> "text/plain"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; else -> "application/octet-stream"
    }
}
