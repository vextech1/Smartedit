package com.vextech.smart_edit.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vextech.smart_edit.core.database.dao.*
import com.vextech.smart_edit.core.database.entity.*

@Database(
    entities = [
        DocumentEntity::class, NoteEntity::class, FavoriteEntity::class, HistoryEntity::class,
        TrashEntity::class, ScanEntity::class, ProcessingJobEntity::class, BackupEntity::class,
        SearchIndexEntity::class, ScanSessionEntity::class, ScanPageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class SmartEditDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun noteDao(): NoteDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun trashDao(): TrashDao
    abstract fun scanDao(): ScanDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun scanPageDao(): ScanPageDao
    abstract fun jobDao(): JobDao
    abstract fun backupDao(): BackupDao
    abstract fun searchDao(): SearchDao
}
