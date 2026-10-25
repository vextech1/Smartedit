package com.vextech.smart_edit.feature.files.domain

enum class FileViewMode { LIST, GRID }
enum class FileSort { NAME, MODIFIED, SIZE, TYPE }

data class FileItem(
    val id: String,
    val name: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val parentId: String?,
    val isDirectory: Boolean,
    val source: String,
    val isFavorite: Boolean,
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
}

data class FileBreadcrumb(
    val id: String?,
    val name: String,
)

enum class FileNameError {
    EMPTY, DOT_NAME, PATH_SEPARATOR, DOUBLE_DOT, CONTROL_CHARACTER, RESERVED_CHARACTER, TOO_LONG
}

class InvalidFileNameException(val reason: FileNameError) : IllegalArgumentException(reason.name)

enum class FileOperationError {
    EMPTY_CLIPBOARD,
    NAME_CONFLICT,
    FILE_NOT_FOUND,
    DESTINATION_NOT_FOLDER,
    MOVE_INTO_SELF,
    MOVE_INTO_DESCENDANT,
    FOLDER_CANNOT_BE_FAVORITE,
    NOT_MANAGED,
    FILE_MISSING,
}

class FileOperationException(
    val reason: FileOperationError,
    val value: String? = null,
) : IllegalArgumentException(reason.name)

object FileNamePolicy {
    private val reserved = Regex("""[<>:"|?*]""")

    fun validateUserName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty()) throw InvalidFileNameException(FileNameError.EMPTY)
        if (name == "." || name == "..") throw InvalidFileNameException(FileNameError.DOT_NAME)
        if ('/' in name || '\\' in name) throw InvalidFileNameException(FileNameError.PATH_SEPARATOR)
        if (".." in name) throw InvalidFileNameException(FileNameError.DOUBLE_DOT)
        if (name.any { it.code < 0x20 }) throw InvalidFileNameException(FileNameError.CONTROL_CHARACTER)
        if (reserved.containsMatchIn(name)) throw InvalidFileNameException(FileNameError.RESERVED_CHARACTER)
        if (name.length > 255) throw InvalidFileNameException(FileNameError.TOO_LONG)
        return name
    }
}

object FileTreePolicy {
    fun breadcrumbs(all: List<FileItem>, currentFolderId: String?): List<FileBreadcrumb> {
        val byId = all.associateBy(FileItem::id)
        val chain = mutableListOf<FileBreadcrumb>()
        val visited = mutableSetOf<String>()
        var cursor = currentFolderId
        while (cursor != null && visited.add(cursor)) {
            val item = byId[cursor] ?: break
            chain += FileBreadcrumb(item.id, item.name)
            cursor = item.parentId
        }
        return buildList {
            add(FileBreadcrumb(null, ""))
            addAll(chain.asReversed())
        }
    }

    fun isDescendant(all: List<FileItem>, candidateId: String, ancestorId: String): Boolean {
        val byId = all.associateBy(FileItem::id)
        val visited = mutableSetOf<String>()
        var cursor: String? = candidateId
        while (cursor != null && visited.add(cursor)) {
            if (cursor == ancestorId) return true
            cursor = byId[cursor]?.parentId
        }
        return false
    }

    fun moveTargets(all: List<FileItem>, movingId: String): List<FileItem> {
        val moving = all.firstOrNull { it.id == movingId } ?: return emptyList()
        return all.asSequence()
            .filter(FileItem::isDirectory)
            .filter { it.id != moving.id }
            .filter { !moving.isDirectory || !isDescendant(all, it.id, moving.id) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    fun visibleChildren(all: List<FileItem>, parentId: String?, sort: FileSort, ascending: Boolean): List<FileItem> {
        val children = all.filter { it.parentId == parentId }
        val base = compareBy<FileItem> { !it.isDirectory }.then(
            when (sort) {
                FileSort.NAME -> compareBy { it.name.lowercase() }
                FileSort.MODIFIED -> compareBy { it.modifiedAt }
                FileSort.SIZE -> compareBy { it.sizeBytes }
                FileSort.TYPE -> compareBy<FileItem> { it.extension }.thenBy { it.name.lowercase() }
            },
        )
        val sorted = children.sortedWith(base)
        if (ascending) return sorted
        // Keep folders before files even when reversing the selected sort direction.
        return sorted.groupBy(FileItem::isDirectory)
            .let { groups -> (groups[true].orEmpty().reversed() + groups[false].orEmpty().reversed()) }
    }
}
