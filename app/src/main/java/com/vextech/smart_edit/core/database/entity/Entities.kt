package com.vextech.smart_edit.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents", indices = [Index(value = ["uri"], unique = true), Index("modifiedAt")])
data class DocumentEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val parentId: String? = null,
    val isDirectory: Boolean = false,
    val source: String = "managed",
    val isTrashed: Boolean = false,
)

@Entity(tableName = "notes", indices = [Index("updatedAt"), Index("isDeleted")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val tagsCsv: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
)

@Entity(tableName = "favorites", indices = [Index(value = ["resourceType", "resourceId"], unique = true)])
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val resourceType: String,
    val resourceId: String,
    val displayName: String,
    val uri: String?,
    val createdAt: Long,
)

@Entity(tableName = "history", indices = [Index("timestamp")])
data class HistoryEntity(
    @PrimaryKey val id: String,
    val action: String,
    val description: String,
    val resourceType: String?,
    val resourceId: String?,
    val uri: String?,
    val displayName: String?,
    val timestamp: Long,
)

@Entity(tableName = "trash", indices = [Index("trashedAt")])
data class TrashEntity(
    @PrimaryKey val id: String,
    val resourceType: String,
    val resourceId: String,
    val displayName: String,
    val originalUri: String,
    val trashedUri: String,
    val sizeBytes: Long,
    val trashedAt: Long,
    val autoDeleteAt: Long? = null,
)

@Entity(tableName = "scans", indices = [Index("createdAt")])
data class ScanEntity(
    @PrimaryKey val id: String,
    val imageUri: String,
    val pdfUri: String?,
    val ocrText: String?,
    val pageCount: Int,
    val createdAt: Long,
)


@Entity(tableName = "scan_sessions", indices = [Index("updatedAt"), Index("status")])
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val exportedPdfUri: String? = null,
)

@Entity(
    tableName = "scan_pages",
    indices = [Index("sessionId"), Index(value = ["sessionId", "position"], unique = true)],
)
data class ScanPageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val position: Int,
    val originalUri: String,
    val croppedUri: String,
    val renderedUri: String,
    val cropPoints: String = "0,0;1,0;1,1;0,1",
    val rotationDegrees: Int = 0,
    val filter: String = "ORIGINAL",
    val ocrText: String? = null,
    val ocrLanguage: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "jobs", indices = [Index("createdAt"), Index("kind")])
data class ProcessingJobEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val sourceUri: String,
    val outputUri: String?,
    val sourceFormat: String,
    val targetFormat: String,
    val sourceSizeBytes: Long,
    val outputSizeBytes: Long?,
    val progress: Float,
    val status: String,
    val createdAt: Long,
    val completedAt: Long?,
    val errorMessage: String?,
)

@Entity(tableName = "backups", indices = [Index("createdAt")])
data class BackupEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val schemaVersion: Int,
)

@Fts4
@Entity(tableName = "search_index")
data class SearchIndexEntity(
    val resourceId: String,
    val resourceType: String,
    val title: String,
    val body: String,
    val uri: String?,
)
