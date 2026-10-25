package com.vextech.smart_edit.core.repository

import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.model.DashboardSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(private val db: SmartEditDatabase) {
    val summary: Flow<DashboardSummary> = combine(
        db.documentDao().observeFileCount(),
        db.noteDao().observeActiveCount(),
        db.favoriteDao().observeCount(),
        db.jobDao().observeCompletedCount("CONVERSION"),
        db.jobDao().observeCompletedCount("COMPRESSION"),
    ) { files, notes, favorites, conversions, compressions ->
        intArrayOf(files, notes, favorites, conversions, compressions)
    }.combine(db.documentDao().observeStorageBytes()) { values, storage ->
        DashboardSummary(
            totalFiles = values[0], totalNotes = values[1], favorites = values[2],
            recentConversions = values[3], recentCompressions = values[4], storageUsedBytes = storage,
        )
    }
}
