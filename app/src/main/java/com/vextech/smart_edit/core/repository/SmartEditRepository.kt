package com.vextech.smart_edit.core.repository

import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.DocumentEntity
import com.vextech.smart_edit.core.model.DashboardSummary
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Output registrar used by the processing features (scanner, PDF, converter, compressor, image editor).
 * It delegates to the focused repositories and exposes only what those features still need.
 * Library features (notes, favorites, search, history, trash) inject their own repositories instead:
 * [NotesRepository], [FavoritesRepository], [SearchRepository], [HistoryRepository], [TrashRepository].
 */
@Singleton
class SmartEditRepository @Inject constructor(
    private val db: SmartEditDatabase,
    private val documentIndex: DocumentIndexRepository,
    private val history: HistoryRepository,
    dashboardRepository: DashboardRepository,
) {
    val documents = documentIndex.documents
    val dashboard: Flow<DashboardSummary> = dashboardRepository.summary
    val scans = db.scanDao().observeAll()
    val conversions = db.jobDao().observeByKind("CONVERSION")
    val compressions = db.jobDao().observeByKind("COMPRESSION")
    val backups = db.backupDao().observeAll()

    suspend fun registerFile(file: File, type: ResourceType, mimeType: String, displayName: String = file.name): DocumentEntity =
        documentIndex.registerFile(file, type, mimeType, displayName)

    suspend fun recordHistory(
        action: HistoryAction,
        description: String,
        type: ResourceType? = null,
        resourceId: String? = null,
        uri: String? = null,
        displayName: String? = null,
    ) = history.record(action, description, type, resourceId, uri, displayName)
}
