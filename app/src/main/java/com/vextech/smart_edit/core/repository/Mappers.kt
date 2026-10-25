package com.vextech.smart_edit.core.repository

import com.vextech.smart_edit.core.database.entity.FavoriteEntity
import com.vextech.smart_edit.core.database.entity.HistoryEntity
import com.vextech.smart_edit.core.database.entity.NoteEntity
import com.vextech.smart_edit.core.library.FavoriteRef
import com.vextech.smart_edit.core.library.HistoryEntry
import com.vextech.smart_edit.core.library.HistoryPolicies
import com.vextech.smart_edit.core.library.Note
import com.vextech.smart_edit.core.library.NotePolicies
import com.vextech.smart_edit.core.model.ResourceType

internal fun NoteEntity.toDomain() = Note(
    id = id, title = title, content = content, tags = NotePolicies.decodeTags(tagsCsv),
    createdAt = createdAt, updatedAt = updatedAt, isPinned = isPinned, isFavorite = isFavorite, deletedAt = deletedAt,
)

internal fun HistoryEntity.toDomain() = HistoryEntry(
    id = id, action = HistoryPolicies.parseAction(action), rawAction = action, description = description,
    resourceType = resourceType?.let { runCatching { ResourceType.valueOf(it) }.getOrNull() },
    resourceId = resourceId, uri = uri, displayName = displayName, timestamp = timestamp,
)

internal fun FavoriteEntity.toRef() = FavoriteRef(id, resourceType, resourceId, displayName, uri, createdAt)
