package com.vextech.smart_edit.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.vextech.smart_edit.core.library.FileAction
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.feature.favorites.FavoritesScreen
import com.vextech.smart_edit.feature.files.FilesScreen
import com.vextech.smart_edit.feature.notes.NotesScreen

/**
 * Files tab: Files, Notes and Favorites share one tab through a segmented control.
 * Each segment is a full screen that renders [LibrarySegments] as its header, so the
 * three view models live as long as this tab's navigation entry.
 */
@Composable
fun FilesTabScreen(
    share: ShareService,
    onOpenNote: (String?) -> Unit,
    onSearch: () -> Unit,
    onTrash: () -> Unit,
    onTool: (FileAction, String) -> Unit,
) {
    var segment by rememberSaveable { mutableStateOf(LibrarySegment.FILES) }
    val header: @Composable () -> Unit = { LibrarySegments(segment) { segment = it } }
    when (segment) {
        LibrarySegment.FILES -> FilesScreen(
            onBack = null, share = share, header = header, onSearch = onSearch, onTrash = onTrash, onToolAction = onTool,
        )
        LibrarySegment.NOTES -> NotesScreen(
            onBack = null, onNew = { onOpenNote(null) }, onOpen = { onOpenNote(it) }, onTrash = onTrash,
            header = header, onSearch = onSearch,
        )
        LibrarySegment.FAVORITES -> FavoritesScreen(
            onBack = null, onOpenNote = { onOpenNote(it) }, share = share, header = header, onSearch = onSearch,
        )
    }
}
