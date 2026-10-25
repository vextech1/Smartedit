package com.vextech.smart_edit.core.library

import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType

/** Domain note. Screens and ViewModels never see the Room entity. */
data class Note(
    val id: String,
    val title: String,
    val content: String,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val deletedAt: Long? = null,
)

data class HistoryEntry(
    val id: String,
    val action: HistoryAction?,
    val rawAction: String,
    val description: String,
    val resourceType: ResourceType?,
    val resourceId: String?,
    val uri: String?,
    val displayName: String?,
    val timestamp: Long,
)

enum class TrashKind { FILE, NOTE }

/** One row of the unified trash screen. [daysLeft] is null when the item never auto-deletes. */
data class TrashItem(
    val id: String,
    val kind: TrashKind,
    val name: String,
    val sizeBytes: Long?,
    val trashedAt: Long,
    val daysLeft: Int?,
    val isDirectory: Boolean = false,
)

data class FavoriteItem(
    val id: String,
    val type: ResourceType?,
    val resourceId: String,
    val displayName: String,
    val uri: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val createdAt: Long,
) {
    val isNote: Boolean get() = type == ResourceType.NOTE
}

/** Minimal projections used to resolve favorites against live data. */
data class FavoriteRef(
    val id: String,
    val rawType: String,
    val resourceId: String,
    val displayName: String,
    val uri: String?,
    val createdAt: Long,
)

data class LiveDocument(val displayName: String, val uri: String, val mimeType: String, val sizeBytes: Long)
data class LiveNote(val title: String)
