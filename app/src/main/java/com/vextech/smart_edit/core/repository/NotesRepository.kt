package com.vextech.smart_edit.core.repository

import androidx.room.withTransaction
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.NoteEntity
import com.vextech.smart_edit.core.library.Note
import com.vextech.smart_edit.core.library.NotePolicies
import com.vextech.smart_edit.core.library.TrashPolicies
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val db: SmartEditDatabase,
    private val indexer: SearchIndexer,
    private val history: HistoryRepository,
    private val favorites: FavoritesRepository,
) {
    val active: Flow<List<Note>> = db.noteDao().observeActive().map { rows -> rows.map { it.toDomain() } }
    val trashed: Flow<List<Note>> = db.noteDao().observeTrash().map { rows -> rows.map { it.toDomain() } }

    suspend fun get(id: String): Note? = db.noteDao().get(id)?.toDomain()

    /**
     * Creates or updates a note. A blank title stays blank (screens show a localized placeholder).
     * [recordActivity] is false for autosaves so the Activity feed is not flooded.
     * Returns null when a new note is completely empty and nothing was stored.
     */
    suspend fun save(id: String?, title: String, content: String, tagsRaw: String, recordActivity: Boolean): Note? {
        val old = id?.let { db.noteDao().get(it) }
        if (old == null && NotePolicies.isEmptyNote(title, content, tagsRaw)) return null
        val now = System.currentTimeMillis()
        val tags = NotePolicies.parseTags(tagsRaw)
        val entity = NoteEntity(
            id = old?.id ?: id ?: UUID.randomUUID().toString(),
            title = NotePolicies.normalizeTitle(title),
            content = content,
            tagsCsv = NotePolicies.encodeTags(tags),
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            isPinned = old?.isPinned ?: false,
            isFavorite = old?.isFavorite ?: false,
            isDeleted = old?.isDeleted ?: false,
            deletedAt = old?.deletedAt,
        )
        db.withTransaction {
            db.noteDao().upsert(entity)
            if (!entity.isDeleted) indexer.replace(entity.id, ResourceType.NOTE, entity.title, "${entity.content} ${entity.tagsCsv}", null)
            if (recordActivity) {
                history.record(
                    if (old == null) HistoryAction.CREATED else HistoryAction.EDITED,
                    "Saved note ${entity.title}", ResourceType.NOTE, entity.id, null, entity.title.ifBlank { null },
                )
            }
        }
        return entity.toDomain()
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val note = db.noteDao().get(id) ?: return
        if (note.isPinned != pinned) db.noteDao().upsert(note.copy(isPinned = pinned))
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        val note = db.noteDao().get(id) ?: return
        favorites.set(ResourceType.NOTE, id, favorite, note.title, null)
    }

    suspend fun moveToTrash(id: String) {
        val note = db.noteDao().get(id) ?: return
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.noteDao().upsert(note.copy(isDeleted = true, isFavorite = false, deletedAt = now))
            indexer.remove(id)
            db.favoriteDao().delete(ResourceType.NOTE.name, id)
            history.record(HistoryAction.DELETED, "Moved note ${note.title} to trash", ResourceType.NOTE, id, null, note.title.ifBlank { null })
        }
    }

    suspend fun restore(id: String) {
        val note = db.noteDao().get(id) ?: return
        val restored = note.copy(isDeleted = false, deletedAt = null, updatedAt = System.currentTimeMillis())
        db.withTransaction {
            db.noteDao().upsert(restored)
            indexer.replace(restored.id, ResourceType.NOTE, restored.title, "${restored.content} ${restored.tagsCsv}", null)
            history.record(HistoryAction.RESTORED, "Restored note ${restored.title}", ResourceType.NOTE, id, null, restored.title.ifBlank { null })
        }
    }

    suspend fun deleteForever(id: String) {
        db.withTransaction {
            db.noteDao().deleteForever(id)
            indexer.remove(id)
            db.favoriteDao().delete(ResourceType.NOTE.name, id)
        }
    }

    suspend fun emptyTrash(): Int {
        val ids = db.noteDao().snapshot().filter { it.isDeleted }.map { it.id }
        ids.forEach { deleteForever(it) }
        return ids.size
    }

    /** Permanently deletes notes that have been in the trash longer than the retention period. */
    suspend fun purgeExpired(now: Long = System.currentTimeMillis(), retentionDays: Int = TrashPolicies.DEFAULT_RETENTION_DAYS): Int {
        val cutoff = now - retentionDays.coerceAtLeast(1) * TrashPolicies.MILLIS_PER_DAY
        val expired = db.noteDao().deletedBefore(cutoff)
        expired.forEach { deleteForever(it.id) }
        return expired.size
    }
}
