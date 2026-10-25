package com.vextech.smart_edit.core.library

import com.vextech.smart_edit.core.model.ResourceType

object FavoritePolicies {
    /**
     * Resolves stored favorites against live data. Favorites whose document or note is gone,
     * trashed or deleted are dropped. Names, URIs and sizes always come from the live item,
     * so a rename never leaves a stale favorite behind. Newest first.
     */
    fun resolve(
        favorites: List<FavoriteRef>,
        activeDocuments: Map<String, LiveDocument>,
        activeNotes: Map<String, LiveNote>,
    ): List<FavoriteItem> = favorites.mapNotNull { fav ->
        val type = runCatching { ResourceType.valueOf(fav.rawType) }.getOrNull()
        when (type) {
            ResourceType.NOTE -> activeNotes[fav.resourceId]?.let {
                FavoriteItem(fav.id, type, fav.resourceId, it.title, null, null, null, fav.createdAt)
            }
            ResourceType.DOCUMENT -> activeDocuments[fav.resourceId]?.let {
                FavoriteItem(fav.id, type, fav.resourceId, it.displayName, it.uri, it.mimeType, it.sizeBytes, fav.createdAt)
            }
            else -> FavoriteItem(fav.id, type, fav.resourceId, fav.displayName, fav.uri, null, null, fav.createdAt)
        }
    }.sortedWith(compareByDescending<FavoriteItem> { it.createdAt }.thenBy { it.id })
}
