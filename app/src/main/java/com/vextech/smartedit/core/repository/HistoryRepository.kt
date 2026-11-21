package com.vextech.smartedit.core.repository

import com.vextech.smartedit.core.analytics.AnalyticsService
import com.vextech.smartedit.core.database.SmartEditDatabase
import com.vextech.smartedit.core.database.entity.HistoryEntity
import com.vextech.smartedit.core.library.HistoryEntry
import com.vextech.smartedit.core.model.HistoryAction
import com.vextech.smartedit.core.model.ResourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val db: SmartEditDatabase,
    private val analytics: AnalyticsService,
) {
    val entries: Flow<List<HistoryEntry>> = db.historyDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun record(
        action: HistoryAction,
        description: String,
        type: ResourceType? = null,
        resourceId: String? = null,
        uri: String? = null,
        displayName: String? = null,
    ) {
        db.historyDao().insert(
            HistoryEntity(UUID.randomUUID().toString(), action.name, description, type?.name, resourceId, uri, displayName, System.currentTimeMillis())
        )
        analytics.event(
            "smartedit_action",
            mapOf("action" to action.name.lowercase(), "resource_type" to (type?.name?.lowercase() ?: "none")),
        )
    }

    suspend fun clear() = db.historyDao().clear()
}
