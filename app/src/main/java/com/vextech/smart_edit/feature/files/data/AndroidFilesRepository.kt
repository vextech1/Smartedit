package com.vextech.smart_edit.feature.files.data

import android.net.Uri
import androidx.room.withTransaction
import com.vextech.smart_edit.core.analytics.AnalyticsService
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.DocumentEntity
import com.vextech.smart_edit.core.database.entity.FavoriteEntity
import com.vextech.smart_edit.core.database.entity.HistoryEntity
import com.vextech.smart_edit.core.database.entity.SearchIndexEntity
import com.vextech.smart_edit.core.database.entity.TrashEntity
import com.vextech.smart_edit.core.di.IoDispatcher
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.files.UriMetadataReader
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.feature.files.domain.FileItem
import com.vextech.smart_edit.feature.files.domain.FileOperationError
import com.vextech.smart_edit.feature.files.domain.FileOperationException
import com.vextech.smart_edit.feature.files.domain.FileNamePolicy
import com.vextech.smart_edit.feature.files.domain.FileTreePolicy
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

@Singleton
class AndroidFilesRepository @Inject constructor(
    private val db: SmartEditDatabase,
    private val store: DocumentStore,
    private val metadataReader: UriMetadataReader,
    private val analytics: AnalyticsService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FilesRepository {

    override fun observeFiles(): Flow<List<FileItem>> = combine(
        db.documentDao().observeAll(),
        db.favoriteDao().observeAll(),
    ) { documents, favorites ->
        val favoriteIds = favorites.asSequence()
            .filter { it.resourceType == ResourceType.DOCUMENT.name }
            .map { it.resourceId }
            .toSet()
        documents.map { it.toFileItem(it.id in favoriteIds) }
    }

    override suspend fun get(id: String): FileItem? = onIo {
        val document = db.documentDao().get(id)?.takeUnless { it.isTrashed } ?: return@onIo null
        document.toFileItem(isFavorite(id))
    }

    override suspend fun importDocuments(uris: List<Uri>, parentId: String?): List<FileItem> = onIo {
        if (uris.isEmpty()) return@onIo emptyList()
        val parentDirectory = resolveParentDirectory(parentId)
        val copied = mutableListOf<Pair<File, UriMetadataReader.Metadata>>()

        try {
            uris.distinct().forEach { uri ->
                val metadata = metadataReader.read(uri)
                copied += store.copyIntoManaged(uri, metadata.name, parentDirectory) to metadata
            }
        } catch (error: Throwable) {
            copied.forEach { (file, _) -> file.deleteRecursively() }
            throw error
        }

        val now = System.currentTimeMillis()
        val documents = copied.map { (file, metadata) ->
            DocumentEntity(
                id = UUID.randomUUID().toString(),
                displayName = file.name,
                uri = file.toURI().toString(),
                mimeType = metadata.mimeType,
                sizeBytes = file.length(),
                createdAt = now,
                modifiedAt = file.lastModified().takeIf { it > 0 } ?: now,
                parentId = parentId,
                source = "import",
            )
        }

        try {
            db.withTransaction {
                db.documentDao().upsertAll(documents)
                documents.forEach { document ->
                    reindex(document)
                    insertHistory(HistoryAction.CREATED, "Imported ${document.displayName}", document)
                }
            }
        } catch (error: Throwable) {
            copied.forEach { (file, _) -> file.deleteRecursively() }
            throw error
        }

        analytics.event("files_imported", mapOf("count" to documents.size.toString()))
        documents.map { it.toFileItem(false) }
    }

    override suspend fun importClipboard(text: String, parentId: String?): FileItem = onIo {
        if (text.isBlank()) throw FileOperationException(FileOperationError.EMPTY_CLIPBOARD)
        val parentDirectory = resolveParentDirectory(parentId)
        val trimmed = text.trim()
        val looksJson = (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        val extension = if (looksJson) "json" else "txt"
        val file = store.uniqueFile(parentDirectory, "Clipboard_${System.currentTimeMillis()}.$extension")
        file.writeText(text)

        val now = System.currentTimeMillis()
        val document = DocumentEntity(
            id = UUID.randomUUID().toString(),
            displayName = file.name,
            uri = file.toURI().toString(),
            mimeType = if (looksJson) "application/json" else "text/plain",
            sizeBytes = file.length(),
            createdAt = now,
            modifiedAt = file.lastModified().takeIf { it > 0 } ?: now,
            parentId = parentId,
            source = "clipboard",
        )

        try {
            db.withTransaction {
                db.documentDao().upsert(document)
                db.searchDao().deleteResource(document.id)
                db.searchDao().insert(
                    SearchIndexEntity(
                        resourceId = document.id,
                        resourceType = ResourceType.DOCUMENT.name,
                        title = document.displayName,
                        body = text.take(10_000),
                        uri = document.uri,
                    ),
                )
                insertHistory(HistoryAction.CREATED, "Imported clipboard as ${document.displayName}", document)
            }
        } catch (error: Throwable) {
            file.delete()
            throw error
        }

        analytics.event("clipboard_imported", mapOf("format" to extension))
        document.toFileItem(false)
    }

    override suspend fun createFolder(parentId: String?, name: String): FileItem = onIo {
        val validName = FileNamePolicy.validateUserName(name)
        val parentDirectory = resolveParentDirectory(parentId)
        val directory = File(parentDirectory, validName)
        if (directory.exists()) throw FileOperationException(FileOperationError.NAME_CONFLICT, validName)
        require(directory.mkdir()) { "Unable to create folder." }

        val now = System.currentTimeMillis()
        val document = DocumentEntity(
            id = UUID.randomUUID().toString(),
            displayName = validName,
            uri = directory.toURI().toString(),
            mimeType = "inode/directory",
            sizeBytes = 0,
            createdAt = now,
            modifiedAt = now,
            parentId = parentId,
            isDirectory = true,
            source = "folder",
        )

        try {
            db.withTransaction {
                db.documentDao().upsert(document)
                reindex(document)
                insertHistory(HistoryAction.CREATED, "Created folder ${document.displayName}", document)
            }
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }

        analytics.event("folder_created")
        document.toFileItem(false)
    }

    override suspend fun rename(id: String, newName: String): FileItem = onIo {
        val validName = FileNamePolicy.validateUserName(newName)
        val document = requireActiveDocument(id)
        if (document.displayName == validName) return@onIo document.toFileItem(isFavorite(id))

        val source = requireExistingManagedPath(document)
        val target = File(requireNotNull(source.parentFile), validName)
        if (target.exists()) throw FileOperationException(FileOperationError.NAME_CONFLICT, validName)
        val all = db.documentDao().activeSnapshot()
        val descendants = if (document.isDirectory) descendantsOf(document.id, all) else emptyList()

        ManagedFileOperator.relocate(source, target)
        val now = System.currentTimeMillis()
        val updated = relocatedRecords(document, descendants, source, target, now)
            .toMutableList()
            .also { records -> records[0] = records.first().copy(displayName = validName) }

        try {
            db.withTransaction {
                updateDocumentsAndIndexes(updated)
                insertHistory(HistoryAction.EDITED, "Renamed ${document.displayName} to $validName", updated.first())
            }
        } catch (error: Throwable) {
            rollbackPreserving(error, target, source)
        }

        analytics.event("file_renamed", mapOf("directory" to document.isDirectory.toString()))
        updated.first().toFileItem(isFavorite(id))
    }

    override suspend fun move(id: String, destinationFolderId: String?): FileItem = onIo {
        val document = requireActiveDocument(id)
        if (document.parentId == destinationFolderId) return@onIo document.toFileItem(isFavorite(id))

        val all = db.documentDao().activeSnapshot()
        if (destinationFolderId != null) {
            if (destinationFolderId == id) throw FileOperationException(FileOperationError.MOVE_INTO_SELF)
            if (document.isDirectory) {
                val items = all.map { it.toFileItem(false) }
                if (FileTreePolicy.isDescendant(items, destinationFolderId, id)) {
                    throw FileOperationException(FileOperationError.MOVE_INTO_DESCENDANT)
                }
            }
        }

        val destinationDirectory = resolveParentDirectory(destinationFolderId)
        val source = requireExistingManagedPath(document)
        val target = File(destinationDirectory, source.name)
        if (target.exists()) throw FileOperationException(FileOperationError.NAME_CONFLICT, source.name)
        val descendants = if (document.isDirectory) descendantsOf(document.id, all) else emptyList()

        ManagedFileOperator.relocate(source, target)
        val now = System.currentTimeMillis()
        val updated = relocatedRecords(document, descendants, source, target, now)
            .toMutableList()
            .also { records -> records[0] = records.first().copy(parentId = destinationFolderId) }

        try {
            db.withTransaction {
                updateDocumentsAndIndexes(updated)
                insertHistory(HistoryAction.EDITED, "Moved ${document.displayName}", updated.first())
            }
        } catch (error: Throwable) {
            rollbackPreserving(error, target, source)
        }

        analytics.event("file_moved", mapOf("directory" to document.isDirectory.toString()))
        updated.first().toFileItem(isFavorite(id))
    }

    override suspend fun moveToTrash(id: String, autoDeleteDays: Int) = onIo {
        val document = requireActiveDocument(id)
        val source = requireExistingManagedPath(document)
        val all = db.documentDao().activeSnapshot()
        val descendants = if (document.isDirectory) descendantsOf(id, all) else emptyList()
        val target = store.uniqueFile(store.trashDir, source.name)

        ManagedFileOperator.relocate(source, target)
        val now = System.currentTimeMillis()
        val affected = relocatedRecords(document, descendants, source, target, now)
            .map { it.copy(isTrashed = true) }
        val trash = TrashEntity(
            id = UUID.randomUUID().toString(),
            resourceType = ResourceType.DOCUMENT.name,
            resourceId = document.id,
            displayName = document.displayName,
            originalUri = document.uri,
            trashedUri = target.toURI().toString(),
            sizeBytes = if (document.isDirectory) recursiveSize(target) else target.length(),
            trashedAt = now,
            autoDeleteAt = now + autoDeleteDays.coerceAtLeast(1) * MILLIS_PER_DAY,
        )

        try {
            db.withTransaction {
                affected.forEach { item ->
                    db.documentDao().upsert(item)
                    db.searchDao().deleteResource(item.id)
                    db.favoriteDao().delete(ResourceType.DOCUMENT.name, item.id)
                }
                db.trashDao().upsert(trash)
                insertHistory(HistoryAction.DELETED, "Moved ${document.displayName} to trash", document)
            }
        } catch (error: Throwable) {
            rollbackPreserving(error, target, source)
        }

        analytics.event("file_trashed", mapOf("directory" to document.isDirectory.toString()))
    }

    override suspend fun toggleFavorite(id: String) = onIo {
        val document = requireActiveDocument(id)
        if (document.isDirectory) throw FileOperationException(FileOperationError.FOLDER_CANNOT_BE_FAVORITE)

        db.withTransaction {
            val existing = db.favoriteDao().find(ResourceType.DOCUMENT.name, id)
            if (existing == null) {
                db.favoriteDao().upsert(
                    FavoriteEntity(
                        id = UUID.randomUUID().toString(),
                        resourceType = ResourceType.DOCUMENT.name,
                        resourceId = id,
                        displayName = document.displayName,
                        uri = document.uri,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                insertHistory(HistoryAction.FAVORITED, "Favorited ${document.displayName}", document)
            } else {
                db.favoriteDao().delete(ResourceType.DOCUMENT.name, id)
            }
        }
    }

    override suspend fun refreshMetadata() = onIo {
        val active = db.documentDao().activeSnapshot()
        val missing = mutableListOf<DocumentEntity>()
        val changed = mutableListOf<DocumentEntity>()

        active.forEach { document ->
            val file = managedPath(document)
            if (file == null || !file.exists()) {
                missing += document
                return@forEach
            }
            val size = if (document.isDirectory) 0 else file.length()
            val modified = file.lastModified().takeIf { it > 0 } ?: document.modifiedAt
            if (size != document.sizeBytes || modified != document.modifiedAt) {
                changed += document.copy(sizeBytes = size, modifiedAt = modified)
            }
        }

        if (missing.isEmpty() && changed.isEmpty()) return@onIo
        db.withTransaction {
            changed.forEach(db.documentDao()::upsert)
            missing.forEach { document ->
                db.documentDao().delete(document.id)
                db.searchDao().deleteResource(document.id)
                db.favoriteDao().delete(ResourceType.DOCUMENT.name, document.id)
            }
        }
    }

    private suspend fun resolveParentDirectory(parentId: String?): File {
        if (parentId == null) return store.documentsDir
        val parent = requireActiveDocument(parentId)
        if (!parent.isDirectory) throw FileOperationException(FileOperationError.DESTINATION_NOT_FOLDER)
        val directory = requireExistingManagedPath(parent)
        if (!directory.isDirectory) throw FileOperationException(FileOperationError.DESTINATION_NOT_FOLDER)
        return directory
    }

    private suspend fun requireActiveDocument(id: String): DocumentEntity =
        db.documentDao().get(id)?.takeUnless { it.isTrashed }
            ?: throw FileOperationException(FileOperationError.FILE_NOT_FOUND)

    private fun managedPath(document: DocumentEntity): File? = store.asFile(document.uri)

    private fun requireExistingManagedPath(document: DocumentEntity): File {
        val file = managedPath(document) ?: throw FileOperationException(FileOperationError.NOT_MANAGED)
        if (!file.exists()) throw FileOperationException(FileOperationError.FILE_MISSING, document.displayName)
        return file
    }

    private suspend fun isFavorite(id: String): Boolean =
        db.favoriteDao().find(ResourceType.DOCUMENT.name, id) != null

    private fun relocatedRecords(
        root: DocumentEntity,
        descendants: List<DocumentEntity>,
        oldRoot: File,
        newRoot: File,
        modifiedAt: Long,
    ): List<DocumentEntity> = buildList {
        add(root.copy(uri = newRoot.toURI().toString(), modifiedAt = modifiedAt))
        descendants.forEach { child ->
            val childPath = requireNotNull(managedPath(child)) { "Invalid managed child path." }
            val relative = childPath.relativeTo(oldRoot).path
            add(child.copy(uri = File(newRoot, relative).toURI().toString(), modifiedAt = modifiedAt))
        }
    }

    private suspend fun updateDocumentsAndIndexes(documents: List<DocumentEntity>) {
        documents.forEach { document ->
            db.documentDao().upsert(document)
            reindex(document)
            db.favoriteDao().find(ResourceType.DOCUMENT.name, document.id)?.let { favorite ->
                db.favoriteDao().upsert(favorite.copy(displayName = document.displayName, uri = document.uri))
            }
        }
    }

    private suspend fun reindex(document: DocumentEntity) {
        db.searchDao().deleteResource(document.id)
        db.searchDao().insert(
            SearchIndexEntity(
                resourceId = document.id,
                resourceType = resourceTypeFor(document).name,
                title = document.displayName,
                body = document.displayName,
                uri = document.uri,
            ),
        )
    }

    private suspend fun insertHistory(action: HistoryAction, description: String, document: DocumentEntity) {
        db.historyDao().insert(
            HistoryEntity(
                id = UUID.randomUUID().toString(),
                action = action.name,
                description = description,
                resourceType = ResourceType.DOCUMENT.name,
                resourceId = document.id,
                uri = document.uri,
                displayName = document.displayName,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    private fun resourceTypeFor(document: DocumentEntity): ResourceType =
        runCatching { ResourceType.valueOf(document.source.uppercase()) }
            .getOrDefault(ResourceType.DOCUMENT)

    private fun descendantsOf(parentId: String, all: List<DocumentEntity>): List<DocumentEntity> {
        val byParent = all.groupBy(DocumentEntity::parentId)
        val result = mutableListOf<DocumentEntity>()
        fun visit(id: String) {
            byParent[id].orEmpty().forEach { child ->
                result += child
                if (child.isDirectory) visit(child.id)
            }
        }
        visit(parentId)
        return result
    }

    private fun rollbackPreserving(error: Throwable, current: File, original: File): Nothing {
        runCatching { ManagedFileOperator.rollback(current, original) }
            .exceptionOrNull()
            ?.let(error::addSuppressed)
        throw error
    }

    private fun recursiveSize(file: File): Long = when {
        !file.exists() -> 0
        file.isFile -> file.length()
        else -> file.listFiles().orEmpty().sumOf(::recursiveSize)
    }

    private fun DocumentEntity.toFileItem(favorite: Boolean): FileItem = FileItem(
        id = id,
        name = displayName,
        uri = uri,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        parentId = parentId,
        isDirectory = isDirectory,
        source = source,
        isFavorite = favorite,
    )

    private suspend fun <T> onIo(block: suspend () -> T): T = withContext(ioDispatcher) { block() }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
