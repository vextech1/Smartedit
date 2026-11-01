package com.vextech.smart_edit.core.database.dao

import androidx.room.*
import com.vextech.smart_edit.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isTrashed = 0 ORDER BY isDirectory DESC, displayName COLLATE NOCASE ASC") fun observeAll(): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE id = :id") suspend fun get(id: String): DocumentEntity?
    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1") suspend fun getByUri(uri: String): DocumentEntity?
    @Query("SELECT * FROM documents WHERE isTrashed = 0 ORDER BY modifiedAt DESC LIMIT :limit") fun observeRecent(limit: Int = 20): Flow<List<DocumentEntity>>
    @Query("SELECT COUNT(*) FROM documents WHERE isDirectory = 0 AND isTrashed = 0") fun observeFileCount(): Flow<Int>
    @Query("SELECT COALESCE(SUM(sizeBytes),0) FROM documents WHERE isDirectory = 0 AND isTrashed = 0") fun observeStorageBytes(): Flow<Long>
    @Upsert suspend fun upsert(entity: DocumentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<DocumentEntity>)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM documents") suspend fun clear()
    @Query("SELECT * FROM documents") suspend fun snapshot(): List<DocumentEntity>
    @Query("SELECT * FROM documents WHERE isTrashed = 0") suspend fun activeSnapshot(): List<DocumentEntity>
    @Query("SELECT * FROM documents WHERE parentId IS :parentId AND isTrashed = 0 ORDER BY isDirectory DESC, displayName COLLATE NOCASE ASC") suspend fun children(parentId: String?): List<DocumentEntity>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC") fun observeActive(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedAt DESC") fun observeTrash(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun get(id: String): NoteEntity?
    @Query("SELECT * FROM notes WHERE id = :id") fun observe(id: String): Flow<NoteEntity?>
    @Query("SELECT * FROM notes WHERE isDeleted = 1 AND deletedAt IS NOT NULL AND deletedAt <= :cutoff") suspend fun deletedBefore(cutoff: Long): List<NoteEntity>
    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0") fun observeActiveCount(): Flow<Int>
    @Upsert suspend fun upsert(note: NoteEntity)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteForever(id: String)
    @Query("DELETE FROM notes") suspend fun clear()
    @Query("SELECT * FROM notes") suspend fun snapshot(): List<NoteEntity>
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC") fun observeAll(): Flow<List<FavoriteEntity>>
    @Query("SELECT COUNT(*) FROM favorites") fun observeCount(): Flow<Int>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE resourceType=:type AND resourceId=:resourceId)") fun observeIsFavorite(type: String, resourceId: String): Flow<Boolean>
    @Query("SELECT * FROM favorites WHERE resourceType=:type AND resourceId=:resourceId LIMIT 1") suspend fun find(type: String, resourceId: String): FavoriteEntity?
    @Upsert suspend fun upsert(entity: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE resourceType=:type AND resourceId=:resourceId") suspend fun delete(type: String, resourceId: String)
    @Query("DELETE FROM favorites") suspend fun clear()
    @Query("SELECT * FROM favorites") suspend fun snapshot(): List<FavoriteEntity>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC") fun observeAll(): Flow<List<HistoryEntity>>
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit") fun observeRecent(limit: Int = 50): Flow<List<HistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: HistoryEntity)
    @Query("DELETE FROM history") suspend fun clear()
    @Query("SELECT * FROM history") suspend fun snapshot(): List<HistoryEntity>
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY trashedAt DESC") fun observeAll(): Flow<List<TrashEntity>>
    @Query("SELECT * FROM trash WHERE id=:id") suspend fun get(id: String): TrashEntity?
    @Upsert suspend fun upsert(entity: TrashEntity)
    @Query("DELETE FROM trash WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM trash") suspend fun clear()
    @Query("SELECT * FROM trash") suspend fun snapshot(): List<TrashEntity>
    @Query("SELECT * FROM trash WHERE autoDeleteAt IS NOT NULL AND autoDeleteAt <= :now") suspend fun expired(now: Long): List<TrashEntity>
}

@Dao
interface ScanDao {
    @Query("SELECT * FROM scans ORDER BY createdAt DESC") fun observeAll(): Flow<List<ScanEntity>>
    @Query("SELECT * FROM scans WHERE id=:id") suspend fun get(id: String): ScanEntity?
    @Upsert suspend fun upsert(entity: ScanEntity)
    @Query("DELETE FROM scans") suspend fun clear()
    @Query("SELECT * FROM scans") suspend fun snapshot(): List<ScanEntity>
}


@Dao
interface ScanSessionDao {
    @Query("SELECT * FROM scan_sessions ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ScanSessionEntity>>
    @Query("SELECT * FROM scan_sessions WHERE id=:id LIMIT 1") fun observe(id: String): Flow<ScanSessionEntity?>
    @Query("SELECT * FROM scan_sessions WHERE id=:id LIMIT 1") suspend fun get(id: String): ScanSessionEntity?
    @Upsert suspend fun upsert(entity: ScanSessionEntity)
    @Query("DELETE FROM scan_sessions WHERE id=:id") suspend fun delete(id: String)
    /** Draft sessions last touched before [before] that own no pages. */
    @Query("SELECT id FROM scan_sessions WHERE status='DRAFT' AND updatedAt < :before AND id NOT IN (SELECT DISTINCT sessionId FROM scan_pages)")
    suspend fun emptyDraftIds(before: Long): List<String>
}

/** First page (cover) and page count of a scan session, derived from scan_pages (no schema change). */
data class ScanCover(val sessionId: String, val renderedUri: String, val pageCount: Int, val updatedAt: Long)

@Dao
interface ScanPageDao {
    @Query("SELECT p.sessionId AS sessionId, p.renderedUri AS renderedUri, (SELECT COUNT(*) FROM scan_pages c WHERE c.sessionId = p.sessionId) AS pageCount, p.updatedAt AS updatedAt FROM scan_pages p WHERE p.position = (SELECT MIN(m.position) FROM scan_pages m WHERE m.sessionId = p.sessionId)")
    fun observeCovers(): Flow<List<ScanCover>>
    @Query("SELECT * FROM scan_pages WHERE sessionId=:sessionId ORDER BY position") fun observeForSession(sessionId: String): Flow<List<ScanPageEntity>>
    @Query("SELECT * FROM scan_pages WHERE sessionId=:sessionId ORDER BY position") suspend fun forSession(sessionId: String): List<ScanPageEntity>
    @Query("SELECT * FROM scan_pages WHERE id=:id LIMIT 1") suspend fun get(id: String): ScanPageEntity?
    @Upsert suspend fun upsert(entity: ScanPageEntity)
    @Upsert suspend fun upsertAll(entities: List<ScanPageEntity>)
    @Query("DELETE FROM scan_pages WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM scan_pages WHERE sessionId=:sessionId") suspend fun deleteForSession(sessionId: String)
}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC") fun observeAll(): Flow<List<ProcessingJobEntity>>
    @Query("SELECT * FROM jobs WHERE kind=:kind ORDER BY createdAt DESC") fun observeByKind(kind: String): Flow<List<ProcessingJobEntity>>
    @Query("SELECT COUNT(*) FROM jobs WHERE kind=:kind AND status='COMPLETED'") fun observeCompletedCount(kind: String): Flow<Int>
    @Upsert suspend fun upsert(entity: ProcessingJobEntity)
    @Query("DELETE FROM jobs") suspend fun clear()
    @Query("SELECT * FROM jobs") suspend fun snapshot(): List<ProcessingJobEntity>
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM backups ORDER BY createdAt DESC") fun observeAll(): Flow<List<BackupEntity>>
    @Upsert suspend fun upsert(entity: BackupEntity)
    @Query("DELETE FROM backups") suspend fun clear()
    @Query("SELECT * FROM backups") suspend fun snapshot(): List<BackupEntity>
}

@Dao
interface SearchDao {
    @Query("SELECT * FROM search_index WHERE search_index MATCH :query") suspend fun search(query: String): List<SearchIndexEntity>
    @Insert suspend fun insert(entity: SearchIndexEntity)
    @Query("DELETE FROM search_index WHERE resourceId=:resourceId AND resourceType=:resourceType") suspend fun delete(resourceId: String, resourceType: String)
    @Query("DELETE FROM search_index WHERE resourceId=:resourceId") suspend fun deleteResource(resourceId: String)
    @Query("DELETE FROM search_index") suspend fun clear()
}
