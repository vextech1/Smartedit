package com.vextech.smart_edit.core.repository

import androidx.room.withTransaction
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.FavoriteEntity
import com.vextech.smart_edit.core.library.FavoriteItem
import com.vextech.smart_edit.core.library.FavoritePolicies
import com.vextech.smart_edit.core.library.LiveDocument
import com.vextech.smart_edit.core.library.LiveNote
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val db: SmartEditDatabase,
    private val history: HistoryRepository,
) {
    /** Favorites resolved against live files and notes: stale names fixed, missing or trashed items dropped. */
    val items: Flow<List<FavoriteItem>> = combine(
        db.favoriteDao().observeAll(),
        db.documentDao().observeAll(),
        db.noteDao().observeActive(),
    ) { favorites, documents, notes ->
        FavoritePolicies.resolve(
            favorites = favorites.map { it.toRef() },
            activeDocuments = documents.associate { it.id to LiveDocument(it.displayName, it.uri, it.mimeType, it.sizeBytes) },
            activeNotes = notes.associate { it.id to LiveNote(it.title) },
        )
    }

    fun observeIsFavorite(type: ResourceType, resourceId: String): Flow<Boolean> =
        db.favoriteDao().observeIsFavorite(type.name, resourceId)

    /** Idempotent. Keeps the note's own flag in step with the favorites table. Returns true if something changed. */
    suspend fun set(type: ResourceType, resourceId: String, favorite: Boolean, displayName: String, uri: String?): Boolean =
        db.withTransaction {
            val existing = db.favoriteDao().find(type.name, resourceId)
            val changed = (existing != null) != favorite
            if (favorite && existing == null) {
                db.favoriteDao().upsert(FavoriteEntity(UUID.randomUUID().toString(), type.name, resourceId, displayName, uri, System.currentTimeMillis()))
                history.record(HistoryAction.FAVORITED, "Favorited $displayName", type, resourceId, uri, displayName)
            } else if (!favorite && existing != null) {
                db.favoriteDao().delete(type.name, resourceId)
            }
            if (type == ResourceType.NOTE) {
                db.noteDao().get(resourceId)?.let { note ->
                    if (note.isFavorite != favorite) db.noteDao().upsert(note.copy(isFavorite = favorite))
                }
            }
            changed
        }

    /** Flips the favorite state and returns the new state. */
    suspend fun toggle(type: ResourceType, resourceId: String, displayName: String, uri: String?): Boolean {
        val now = db.favoriteDao().find(type.name, resourceId) == null
        set(type, resourceId, now, displayName, uri)
        return now
    }
}
