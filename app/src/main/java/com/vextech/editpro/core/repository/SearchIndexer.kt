package com.vextech.editpro.core.repository

import com.vextech.editpro.core.database.EditProDatabase
import com.vextech.editpro.core.database.entity.SearchIndexEntity
import com.vextech.editpro.core.model.ResourceType
import javax.inject.Inject
import javax.inject.Singleton

/** The only writer of the full-text index outside the Files feature. Always replaces, never duplicates. */
@Singleton
class SearchIndexer @Inject constructor(private val db: EditProDatabase) {
    suspend fun replace(resourceId: String, type: ResourceType, title: String, body: String, uri: String?) {
        db.searchDao().deleteResource(resourceId)
        db.searchDao().insert(SearchIndexEntity(resourceId, type.name, title, body, uri))
    }

    suspend fun remove(resourceId: String) = db.searchDao().deleteResource(resourceId)
}
