package com.vextech.smart_edit.feature.converter.domain

enum class ConverterSourceKind { IMAGES, PDF }

enum class ImageTargetFormat(
    val extension: String,
    val mimeType: String,
    val supportsQuality: Boolean,
) {
    JPEG("jpg", "image/jpeg", true),
    PNG("png", "image/png", false),
    WEBP("webp", "image/webp", true),
}

enum class ConverterTarget {
    PDF,
    JPEG,
    PNG,
    WEBP,
    PDF_IMAGES_ZIP,
    DOCX,
    TEXT,
}

data class ConverterSource(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

data class ConverterOptions(
    val quality: Int = 92,
    val dpi: Int = 144,
    val pageRange: String = "all",
    val pdfPassword: String = "",
)

data class ConverterOutput(
    val name: String,
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long,
)

data class ConversionResult(
    val jobId: String,
    val outputs: List<ConverterOutput>,
)

data class ConversionJob(
    val id: String,
    val sourceFormat: String,
    val targetFormat: String,
    val progress: Float,
    val status: String,
    val createdAt: Long,
    val completedAt: Long?,
    val errorMessage: String?,
)

enum class ConverterMessageKey {
    SOURCE_REQUIRED,
    IMAGE_SOURCE_REQUIRED,
    PDF_SOURCE_REQUIRED,
    PAGE_RANGE_INVALID,
    PAGE_RANGE_EMPTY,
    PASSWORD_REQUIRED_OR_INCORRECT,
    CONVERSION_FAILED,
    CONVERSION_CANCELLED,
    CONVERSION_COMPLETE,
}

class ConverterFailure(
    val key: ConverterMessageKey,
    cause: Throwable? = null,
) : Exception(key.name, cause)

sealed interface ConversionRequest {
    val options: ConverterOptions

    data class ImagesToPdf(
        val sources: List<ConverterSource>,
        override val options: ConverterOptions,
    ) : ConversionRequest

    data class ImagesToFormat(
        val sources: List<ConverterSource>,
        val format: ImageTargetFormat,
        override val options: ConverterOptions,
    ) : ConversionRequest

    data class PdfToImages(
        val source: ConverterSource,
        val format: ImageTargetFormat,
        override val options: ConverterOptions,
    ) : ConversionRequest

    data class PdfToDocx(
        val source: ConverterSource,
        override val options: ConverterOptions,
    ) : ConversionRequest

    data class PdfToText(
        val source: ConverterSource,
        override val options: ConverterOptions,
    ) : ConversionRequest
}

object ConverterPolicies {
    fun clampQuality(value: Int): Int = value.coerceIn(10, 100)

    fun normalizeDpi(value: Int): Int = value.coerceIn(72, 300)

    fun targetForImages(format: ImageTargetFormat): ConverterTarget = when (format) {
        ImageTargetFormat.JPEG -> ConverterTarget.JPEG
        ImageTargetFormat.PNG -> ConverterTarget.PNG
        ImageTargetFormat.WEBP -> ConverterTarget.WEBP
    }

    /** Parses a 1-based page expression such as `all`, `1-3,5,7-8` into zero-based indices. */
    fun parsePageRange(raw: String, pageCount: Int): List<Int> {
        require(pageCount > 0) { "Page count must be positive" }
        val text = raw.trim().lowercase()
        if (text.isBlank() || text == "all" || text == "*") return (0 until pageCount).toList()

        val pages = linkedSetOf<Int>()
        text.split(',').map(String::trim).filter(String::isNotEmpty).forEach { token ->
            if ('-' in token) {
                val parts = token.split('-', limit = 2)
                require(parts.size == 2) { "Invalid page range" }
                val start = parts[0].trim().toIntOrNull() ?: throw IllegalArgumentException("Invalid page range")
                val end = parts[1].trim().toIntOrNull() ?: throw IllegalArgumentException("Invalid page range")
                require(start >= 1 && end >= 1 && start <= end) { "Invalid page range" }
                require(end <= pageCount) { "Page range exceeds document" }
                for (page in start..end) pages += page - 1
            } else {
                val page = token.toIntOrNull() ?: throw IllegalArgumentException("Invalid page range")
                require(page in 1..pageCount) { "Page range exceeds document" }
                pages += page - 1
            }
        }
        require(pages.isNotEmpty()) { "No pages selected" }
        return pages.toList().sorted()
    }

    fun validateRequest(request: ConversionRequest) {
        when (request) {
            is ConversionRequest.ImagesToPdf -> { require(request.sources.isNotEmpty()) { "Select at least one image" }; require(request.sources.all { it.mimeType.startsWith("image/") || it.displayName.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif") }) { "Select image files" } }
            is ConversionRequest.ImagesToFormat -> { require(request.sources.isNotEmpty()) { "Select at least one image" }; require(request.sources.all { it.mimeType.startsWith("image/") || it.displayName.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif") }) { "Select image files" } }
            is ConversionRequest.PdfToImages -> require(request.source.mimeType == "application/pdf" || request.source.displayName.endsWith(".pdf", true)) { "Select a PDF" }
            is ConversionRequest.PdfToDocx -> require(request.source.mimeType == "application/pdf" || request.source.displayName.endsWith(".pdf", true)) { "Select a PDF" }
            is ConversionRequest.PdfToText -> require(request.source.mimeType == "application/pdf" || request.source.displayName.endsWith(".pdf", true)) { "Select a PDF" }
        }
    }
}
