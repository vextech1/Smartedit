package com.vextech.smart_edit.core.library

/** Tool actions offered from a file's menu in the Files tab. */
enum class FileAction { EDIT_PDF, EDIT_IMAGE, EXTRACT_TEXT, CONVERT, COMPRESS }

enum class FileKind { PDF, IMAGE, OTHER }

object FileActionPolicies {
    /** Image formats every tool in the app can decode. */
    val supportedImageExtensions = setOf("jpg", "jpeg", "png", "webp")

    fun kindOf(name: String, mimeType: String): FileKind {
        val extension = name.substringAfterLast('.', "").lowercase()
        val mime = mimeType.lowercase()
        return when {
            extension == "pdf" || mime == "application/pdf" -> FileKind.PDF
            extension in supportedImageExtensions -> FileKind.IMAGE
            mime == "image/jpeg" || mime == "image/png" || mime == "image/webp" -> FileKind.IMAGE
            else -> FileKind.OTHER
        }
    }

    /** Ordered actions for the file menu. Folders and unsupported types get none. */
    fun actionsFor(name: String, mimeType: String, isDirectory: Boolean): List<FileAction> {
        if (isDirectory) return emptyList()
        return when (kindOf(name, mimeType)) {
            FileKind.PDF -> listOf(FileAction.EDIT_PDF, FileAction.CONVERT, FileAction.COMPRESS)
            FileKind.IMAGE -> listOf(FileAction.EDIT_IMAGE, FileAction.EXTRACT_TEXT, FileAction.CONVERT, FileAction.COMPRESS)
            FileKind.OTHER -> emptyList()
        }
    }
}
