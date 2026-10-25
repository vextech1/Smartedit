package com.vextech.smart_edit.feature.files.data

import android.net.Uri
import com.vextech.smart_edit.feature.files.domain.FileItem
import kotlinx.coroutines.flow.Flow

interface FilesRepository {
    fun observeFiles(): Flow<List<FileItem>>
    suspend fun get(id: String): FileItem?
    suspend fun importDocuments(uris: List<Uri>, parentId: String?): List<FileItem>
    suspend fun importClipboard(text: String, parentId: String?): FileItem
    suspend fun createFolder(parentId: String?, name: String): FileItem
    suspend fun rename(id: String, newName: String): FileItem
    suspend fun move(id: String, destinationFolderId: String?): FileItem
    suspend fun moveToTrash(id: String, autoDeleteDays: Int = 30)
    suspend fun toggleFavorite(id: String)
    suspend fun refreshMetadata()
}
