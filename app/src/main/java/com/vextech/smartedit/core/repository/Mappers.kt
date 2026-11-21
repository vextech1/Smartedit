package com.vextech.smartedit.core.repository

import com.vextech.smartedit.core.database.entity.FavoriteEntity
import com.vextech.smartedit.core.database.entity.HistoryEntity
import com.vextech.smartedit.core.database.entity.NoteEntity
import com.vextech.smartedit.core.library.FavoriteRef
import com.vextech.smartedit.core.library.HistoryEntry
import com.vextech.smartedit.core.library.HistoryPolicies
import com.vextech.smartedit.core.library.Note
import com.vextech.smartedit.core.library.NotePolicies
import com.vextech.smartedit.core.model.ResourceType

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
