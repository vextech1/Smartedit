package com.vextech.smart_edit.core.repository

import androidx.room.withTransaction
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.DocumentEntity
import com.vextech.smart_edit.core.model.ResourceType
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Registers files produced by any feature in the Files index and the search index. */
@Singleton
class DocumentIndexRepository @Inject constructor(
    private val db: SmartEditDatabase,
    private val indexer: SearchIndexer,
) {
    val documents = db.documentDao().observeAll()

    suspend fun registerFile(file: File, type: ResourceType, mimeType: String, displayName: String = file.name): DocumentEntity {
        val now = System.currentTimeMillis()
        val existing = db.documentDao().getByUri(file.toURI().toString())
        val entity = DocumentEntity(
            id = existing?.id ?: UUID.randomUUID().toString(), displayName = displayName,
            uri = file.toURI().toString(), mimeType = mimeType, sizeBytes = file.length(),
            createdAt = existing?.createdAt ?: now, modifiedAt = now, source = type.name.lowercase(),
        )
        db.withTransaction {
            db.documentDao().upsert(entity)
            indexer.replace(entity.id, type, entity.displayName, entity.displayName, entity.uri)
        }
        return entity
    }
}
