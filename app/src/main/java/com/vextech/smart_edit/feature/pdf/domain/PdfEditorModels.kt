package com.vextech.smart_edit.feature.pdf.domain

data class PdfRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top)
    }
}

data class PdfPoint(val x: Float, val y: Float) {
    init { require(x in 0f..1f && y in 0f..1f) }
}

data class PdfPageInfo(
    val index: Int,
    val widthPoints: Float,
    val heightPoints: Float,
    val rotation: Int,
)

data class PdfDocumentMetadata(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
)

data class PdfBookmark(
    val id: String,
    val title: String,
    val pageIndex: Int,
)

data class PdfSearchHit(
    val pageIndex: Int,
    val snippet: String,
)

data class PdfFormField(
    val name: String,
    val value: String,
    val type: String,
    val readOnly: Boolean,
)

data class PdfAnnotationInfo(
    val id: String,
    val pageIndex: Int,
    val type: String,
    val contents: String,
)

data class PdfSecurityState(
    val encrypted: Boolean,
    val canPrint: Boolean = true,
    val canCopy: Boolean = true,
    val canModify: Boolean = true,
    val canAnnotate: Boolean = true,
    val canFillForms: Boolean = true,
    val canAssemble: Boolean = true,
    val isOwner: Boolean = true,
)

data class PdfSecurityOptions(
    val userPassword: String,
    val ownerPassword: String,
    val allowPrint: Boolean = true,
    val allowCopy: Boolean = true,
    val allowModify: Boolean = true,
    val allowAssemble: Boolean = true,
    val allowAnnotations: Boolean = true,
    val allowFillForms: Boolean = true,
)

enum class PdfEditorMessageKey {
    OPEN_FAILED,
    PASSWORD_REQUIRED,
    WRONG_PASSWORD,
    SAVE_FAILED,
    SAVED,
    SHARE_FAILED,
    OPERATION_FAILED,
    INVALID_PAGE_SELECTION,
    SEARCH_EMPTY,
    REDACTION_WARNING,
    REDACTION_REQUIRES_UNPROTECTED,
    SECURITY_APPLIED,
    SECURITY_REMOVED,
    PRINT_NOT_ALLOWED,
}

data class PdfEditorSession(
    val id: String,
    val sourceName: String,
    val pageCount: Int,
    val currentPage: Int = 0,
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val encrypted: Boolean = false,
)

object PdfEditorPolicies {
    fun normalizeRotation(value: Int): Int {
        val normalized = ((value % 360) + 360) % 360
        require(normalized % 90 == 0) { "Rotation must be a multiple of 90" }
        return normalized
    }

    fun validatePageSelection(pageCount: Int, pages: Collection<Int>, allowEmpty: Boolean = false): List<Int> {
        require(pageCount > 0) { "Document has no pages" }
        if (!allowEmpty) require(pages.isNotEmpty()) { "Select at least one page" }
        val distinct = pages.distinct()
        require(distinct.size == pages.size) { "Duplicate page selection" }
        require(distinct.all { it in 0 until pageCount }) { "Page selection is out of range" }
        return distinct.sorted()
    }

    fun validateReorder(pageCount: Int, order: List<Int>): List<Int> {
        require(order.size == pageCount) { "Every page must appear exactly once" }
        require(order.toSet().size == pageCount) { "Every page must appear exactly once" }
        require(order.all { it in 0 until pageCount }) { "Page order is out of range" }
        return order
    }

    fun visualPointToUnrotated(point: PdfPoint, rotation: Int): PdfPoint = when (normalizeRotation(rotation)) {
        0 -> point
        90 -> PdfPoint(point.y, 1f - point.x)
        180 -> PdfPoint(1f - point.x, 1f - point.y)
        270 -> PdfPoint(1f - point.y, point.x)
        else -> error("Unsupported page rotation")
    }

    fun visualRectToUnrotated(rect: PdfRect, rotation: Int): PdfRect {
        val points = listOf(
            PdfPoint(rect.left, rect.top),
            PdfPoint(rect.right, rect.top),
            PdfPoint(rect.right, rect.bottom),
            PdfPoint(rect.left, rect.bottom),
        ).map { visualPointToUnrotated(it, rotation) }
        return PdfRect(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y },
        )
    }

    fun validateExternalLink(value: String): String {
        val url = value.trim()
        require(url.length in 4..2048) { "Link is invalid" }
        val lower = url.lowercase()
        require(lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:")) {
            "Only HTTP, HTTPS and mail links are supported"
        }
        return url
    }

    fun validateSecurity(options: PdfSecurityOptions) {
        require(options.ownerPassword.length >= 4) { "Owner password must contain at least 4 characters" }
        require(options.userPassword != options.ownerPassword || options.userPassword.isEmpty()) {
            "User and owner passwords must differ"
        }
    }

    fun normalizedRect(x1: Float, y1: Float, x2: Float, y2: Float): PdfRect {
        val left = minOf(x1, x2).coerceIn(0f, 1f)
        val right = maxOf(x1, x2).coerceIn(0f, 1f)
        val top = minOf(y1, y2).coerceIn(0f, 1f)
        val bottom = maxOf(y1, y2).coerceIn(0f, 1f)
        require(right - left >= 0.005f && bottom - top >= 0.005f) { "Selection is too small" }
        return PdfRect(left, top, right, bottom)
    }
}
