package com.vextech.smart_edit.core.model

enum class ResourceType { DOCUMENT, NOTE, SCAN, IMAGE, PDF, CONVERSION, COMPRESSION, BACKUP }
enum class HistoryAction { OPENED, CONVERTED, COMPRESSED, BACKED_UP, SCANNED, FAVORITED, DELETED, EDITED, CREATED, RESTORED }
enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class JobStatus { PENDING, RUNNING, COMPLETED, FAILED }

data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val language: String = "en",
    val notificationsEnabled: Boolean = true,
    val autoBackupEnabled: Boolean = false,
    val confirmDeleteEnabled: Boolean = true,
    val defaultExportFormat: String = "pdf",
    val onboardingCompleted: Boolean = false,
)

data class ManagedResource(
    val id: String,
    val type: ResourceType,
    val displayName: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val parentId: String? = null,
    val isDirectory: Boolean = false,
)

data class SearchHit(
    val resourceId: String,
    val type: ResourceType,
    val title: String,
    val snippet: String,
    val uri: String?,
)

data class DashboardSummary(
    val totalFiles: Int = 0,
    val totalNotes: Int = 0,
    val favorites: Int = 0,
    val recentConversions: Int = 0,
    val recentCompressions: Int = 0,
    val storageUsedBytes: Long = 0,
)
