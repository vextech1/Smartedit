package com.vextech.smart_edit.feature.files

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.model.AppSettings
import com.vextech.smart_edit.core.preferences.SettingsRepository
import com.vextech.smart_edit.feature.files.data.FilesRepository
import com.vextech.smart_edit.feature.files.domain.FileBreadcrumb
import com.vextech.smart_edit.feature.files.domain.FileItem
import com.vextech.smart_edit.feature.files.domain.FileSort
import com.vextech.smart_edit.feature.files.domain.FileNameError
import com.vextech.smart_edit.feature.files.domain.FileOperationError
import com.vextech.smart_edit.feature.files.domain.FileOperationException
import com.vextech.smart_edit.feature.files.domain.InvalidFileNameException
import com.vextech.smart_edit.feature.files.domain.FileTreePolicy
import com.vextech.smart_edit.feature.files.domain.FileViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

enum class FilesMessageKey {
    IMPORT_FAILED, CLIPBOARD_IMPORT_FAILED, CREATE_FOLDER_FAILED, RENAME_FAILED, MOVE_FAILED, TRASH_FAILED,
    FAVORITE_FAILED, REFRESH_FAILED, IMPORTED_ONE, IMPORTED_MANY, CLIPBOARD_IMPORTED, FOLDER_CREATED,
    RENAMED, MOVED, TRASHED, REFRESHED, NAME_EMPTY, NAME_DOT, NAME_SEPARATOR, NAME_DOUBLE_DOT,
    NAME_CONTROL, NAME_RESERVED, NAME_TOO_LONG, CLIPBOARD_EMPTY, NAME_CONFLICT, FILE_NOT_FOUND,
    DESTINATION_NOT_FOLDER, MOVE_INTO_SELF, MOVE_INTO_DESCENDANT, FOLDER_CANNOT_BE_FAVORITE,
    NOT_MANAGED, FILE_MISSING,
}

data class FilesUiMessage(
    val key: FilesMessageKey,
    val value: String? = null,
    val count: Int? = null,
)

data class FilesUiState(
    val items: List<FileItem> = emptyList(),
    val allItems: List<FileItem> = emptyList(),
    val breadcrumbs: List<FileBreadcrumb> = listOf(FileBreadcrumb(null, "")),
    val currentFolderId: String? = null,
    val viewMode: FileViewMode = FileViewMode.LIST,
    val sort: FileSort = FileSort.NAME,
    val ascending: Boolean = true,
    val confirmDeleteEnabled: Boolean = true,
    val isBusy: Boolean = false,
    val message: FilesUiMessage? = null,
) {
    val canGoUp: Boolean get() = currentFolderId != null
    val folderName: String get() = breadcrumbs.lastOrNull()?.name.orEmpty()

    fun moveTargets(movingId: String): List<FileItem> = FileTreePolicy.moveTargets(allItems, movingId)
}

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: FilesRepository,
    settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val currentFolderId = MutableStateFlow(savedStateHandle.get<String>(KEY_FOLDER_ID))
    private val viewMode = MutableStateFlow(FileViewMode.LIST)
    private val sort = MutableStateFlow(FileSort.NAME)
    private val ascending = MutableStateFlow(true)
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<FilesUiMessage?>(null)
    private val operationMutex = Mutex()

    private data class BrowserInputs(
        val files: List<FileItem>,
        val settings: AppSettings,
        val folderId: String?,
    )

    private data class DisplayInputs(
        val mode: FileViewMode,
        val sort: FileSort,
        val ascending: Boolean,
    )

    private val browserInputs = combine(
        repository.observeFiles(),
        settingsRepository.settings,
        currentFolderId,
    ) { files, settings, folderId -> BrowserInputs(files, settings, folderId) }

    private val displayInputs = combine(viewMode, sort, ascending) { mode, sortMode, isAscending ->
        DisplayInputs(mode, sortMode, isAscending)
    }


    val uiState = combine(browserInputs, displayInputs, busy, message) { browser, display, isBusy, uiMessage ->
        val effectiveFolder = browser.folderId?.takeIf { id -> browser.files.any { it.id == id && it.isDirectory } }
        val children = FileTreePolicy.visibleChildren(browser.files, effectiveFolder, display.sort, display.ascending)
        FilesUiState(
            items = children,
            allItems = browser.files,
            breadcrumbs = FileTreePolicy.breadcrumbs(browser.files, effectiveFolder),
            currentFolderId = effectiveFolder,
            viewMode = display.mode,
            sort = display.sort,
            ascending = display.ascending,
            confirmDeleteEnabled = browser.settings.confirmDeleteEnabled,
            isBusy = isBusy,
            message = uiMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilesUiState())

    init {
        viewModelScope.launch {
            runCatching { repository.refreshMetadata() }
                .onFailure { message.value = userMessage(it, FilesMessageKey.REFRESH_FAILED) }
        }
    }

    fun navigateToFolder(id: String) {
        val folder = uiState.value.allItems.firstOrNull { it.id == id && it.isDirectory } ?: return
        currentFolderId.value = folder.id
        savedStateHandle[KEY_FOLDER_ID] = folder.id
    }

    fun navigateToBreadcrumb(id: String?) {
        currentFolderId.value = id
        savedStateHandle[KEY_FOLDER_ID] = id
    }

    fun goUp() {
        val state = uiState.value
        val parentId = state.currentFolderId?.let { id -> state.allItems.firstOrNull { it.id == id }?.parentId }
        navigateToBreadcrumb(parentId)
    }

    fun toggleViewMode() {
        viewMode.value = if (viewMode.value == FileViewMode.LIST) FileViewMode.GRID else FileViewMode.LIST
    }

    fun setSort(value: FileSort) {
        if (sort.value == value) ascending.value = !ascending.value else {
            sort.value = value
            ascending.value = value == FileSort.NAME || value == FileSort.TYPE
        }
    }

    fun importDocuments(uris: List<Uri>) = runOperation(FilesMessageKey.IMPORT_FAILED) {
        val imported = repository.importDocuments(uris, uiState.value.currentFolderId)
        message.value = when (imported.size) {
            0 -> null
            1 -> FilesUiMessage(FilesMessageKey.IMPORTED_ONE, value = imported.first().name)
            else -> FilesUiMessage(FilesMessageKey.IMPORTED_MANY, count = imported.size)
        }
    }

    fun importClipboard(text: String) = runOperation(FilesMessageKey.CLIPBOARD_IMPORT_FAILED) {
        val item = repository.importClipboard(text, uiState.value.currentFolderId)
        message.value = FilesUiMessage(FilesMessageKey.CLIPBOARD_IMPORTED, value = item.name)
    }

    fun createFolder(name: String) = runOperation(FilesMessageKey.CREATE_FOLDER_FAILED) {
        repository.createFolder(uiState.value.currentFolderId, name)
        message.value = FilesUiMessage(FilesMessageKey.FOLDER_CREATED)
    }

    fun rename(item: FileItem, newName: String) = runOperation(FilesMessageKey.RENAME_FAILED) {
        val renamed = repository.rename(item.id, newName)
        message.value = FilesUiMessage(FilesMessageKey.RENAMED, value = renamed.name)
    }

    fun move(item: FileItem, destinationFolderId: String?) = runOperation(FilesMessageKey.MOVE_FAILED) {
        repository.move(item.id, destinationFolderId)
        message.value = FilesUiMessage(FilesMessageKey.MOVED, value = item.name)
    }

    fun trash(item: FileItem) = runOperation(FilesMessageKey.TRASH_FAILED) {
        val wasCurrentFolder = item.id == uiState.value.currentFolderId
        val parentId = item.parentId
        repository.moveToTrash(item.id)
        if (wasCurrentFolder) navigateToBreadcrumb(parentId)
        message.value = FilesUiMessage(FilesMessageKey.TRASHED, value = item.name)
    }

    fun toggleFavorite(item: FileItem) = runOperation(FilesMessageKey.FAVORITE_FAILED) {
        repository.toggleFavorite(item.id)
    }

    fun refresh() = runOperation(FilesMessageKey.REFRESH_FAILED) {
        repository.refreshMetadata()
        message.value = FilesUiMessage(FilesMessageKey.REFRESHED)
    }

    fun clearMessage() {
        message.value = null
    }

    private fun runOperation(fallback: FilesMessageKey, block: suspend () -> Unit) {
        if (!operationMutex.tryLock()) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                block()
            } catch (throwable: Throwable) {
                message.value = userMessage(throwable, fallback)
            } finally {
                busy.value = false
                operationMutex.unlock()
            }
        }
    }

    private fun userMessage(error: Throwable, fallback: FilesMessageKey): FilesUiMessage = when (error) {
        is InvalidFileNameException -> FilesUiMessage(
            when (error.reason) {
                FileNameError.EMPTY -> FilesMessageKey.NAME_EMPTY
                FileNameError.DOT_NAME -> FilesMessageKey.NAME_DOT
                FileNameError.PATH_SEPARATOR -> FilesMessageKey.NAME_SEPARATOR
                FileNameError.DOUBLE_DOT -> FilesMessageKey.NAME_DOUBLE_DOT
                FileNameError.CONTROL_CHARACTER -> FilesMessageKey.NAME_CONTROL
                FileNameError.RESERVED_CHARACTER -> FilesMessageKey.NAME_RESERVED
                FileNameError.TOO_LONG -> FilesMessageKey.NAME_TOO_LONG
            },
        )
        is FileOperationException -> FilesUiMessage(
            key = when (error.reason) {
                FileOperationError.EMPTY_CLIPBOARD -> FilesMessageKey.CLIPBOARD_EMPTY
                FileOperationError.NAME_CONFLICT -> FilesMessageKey.NAME_CONFLICT
                FileOperationError.FILE_NOT_FOUND -> FilesMessageKey.FILE_NOT_FOUND
                FileOperationError.DESTINATION_NOT_FOLDER -> FilesMessageKey.DESTINATION_NOT_FOLDER
                FileOperationError.MOVE_INTO_SELF -> FilesMessageKey.MOVE_INTO_SELF
                FileOperationError.MOVE_INTO_DESCENDANT -> FilesMessageKey.MOVE_INTO_DESCENDANT
                FileOperationError.FOLDER_CANNOT_BE_FAVORITE -> FilesMessageKey.FOLDER_CANNOT_BE_FAVORITE
                FileOperationError.NOT_MANAGED -> FilesMessageKey.NOT_MANAGED
                FileOperationError.FILE_MISSING -> FilesMessageKey.FILE_MISSING
            },
            value = error.value,
        )
        else -> FilesUiMessage(fallback)
    }

    companion object {
        private const val KEY_FOLDER_ID = "files.currentFolderId"
    }
}
