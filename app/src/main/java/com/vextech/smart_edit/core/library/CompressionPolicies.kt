package com.vextech.smart_edit.core.library

enum class CompressionPreset(val quality: Float) { HIGH(0.9f), BALANCED(0.7f), SMALLER(0.4f) }

/** STANDARD keeps text selectable and the page structure. MAXIMUM turns every page into an image. */
enum class PdfCompressionMode { STANDARD, MAXIMUM }

enum class CompressionKind { PDF, IMAGE, UNSUPPORTED }

enum class CompressionVerdict { SMALLER, UNCHANGED, LARGER }

data class ImageParams(val jpegQuality: Int, val maxDimension: Int)

data class RasterParams(val dpi: Int, val jpegQuality: Int)

enum class ImageOutput(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
}

object CompressionPolicies {
    const val MIN_SAVING_PERCENT = 2
    private const val MIN_EMBEDDED_PIXELS = 40_000
    private const val MIN_EMBEDDED_BYTES = 20 * 1024L

    fun kindOf(name: String, mimeType: String): CompressionKind = when (FileActionPolicies.kindOf(name, mimeType)) {
        FileKind.PDF -> CompressionKind.PDF
        FileKind.IMAGE -> CompressionKind.IMAGE
        FileKind.OTHER -> CompressionKind.UNSUPPORTED
    }

    fun imageParams(preset: CompressionPreset): ImageParams = when (preset) {
        CompressionPreset.HIGH -> ImageParams(90, 4096)
        CompressionPreset.BALANCED -> ImageParams(75, 2560)
        CompressionPreset.SMALLER -> ImageParams(50, 1600)
    }

    /** Settings for pictures inside a PDF in STANDARD mode. */
    fun embeddedImageParams(preset: CompressionPreset): ImageParams = when (preset) {
        CompressionPreset.HIGH -> ImageParams(85, 3000)
        CompressionPreset.BALANCED -> ImageParams(70, 1800)
        CompressionPreset.SMALLER -> ImageParams(45, 1200)
    }

    /** Settings for MAXIMUM mode page rendering. */
    fun rasterParams(preset: CompressionPreset): RasterParams = when (preset) {
        CompressionPreset.HIGH -> RasterParams(150, 85)
        CompressionPreset.BALANCED -> RasterParams(110, 70)
        CompressionPreset.SMALLER -> RasterParams(72, 50)
    }

    /** Keeps the aspect ratio, never enlarges, never returns a side below 1. */
    fun scaledSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "Image size must be positive" }
        val longest = maxOf(width, height)
        if (maxDimension <= 0 || longest <= maxDimension) return width to height
        val factor = maxDimension.toDouble() / longest
        return maxOf(1, Math.round(width * factor).toInt()) to maxOf(1, Math.round(height * factor).toInt())
    }

    /** Whole percent saved. Negative when the result is larger. Clamped to -100..100. */
    fun percentSaved(original: Long, compressed: Long): Int {
        if (original <= 0L) return 0
        val saved = (original - compressed) * 100.0 / original
        return Math.floor(saved).toInt().coerceIn(-100, 100)
    }

    fun verdict(original: Long, compressed: Long): CompressionVerdict = when {
        compressed > original -> CompressionVerdict.LARGER
        percentSaved(original, compressed) >= MIN_SAVING_PERCENT -> CompressionVerdict.SMALLER
        else -> CompressionVerdict.UNCHANGED
    }

    /** Transparent images stay PNG (JPEG would turn transparency black). Everything else becomes JPEG. */
    fun imageOutput(hasAlpha: Boolean): ImageOutput = if (hasAlpha) ImageOutput.PNG else ImageOutput.JPEG

    fun outputName(originalName: String, extension: String): String {
        val base = originalName.substringBeforeLast('.', originalName).trim().ifBlank { "file" }
        return "${base}_compressed.$extension"
    }

    /** Pictures with transparency masks, tiny pictures and already small streams are left alone. */
    fun shouldSkipEmbeddedImage(width: Int, height: Int, originalBytes: Long, hasSoftMask: Boolean, isStencil: Boolean): Boolean =
        hasSoftMask || isStencil || width.toLong() * height < MIN_EMBEDDED_PIXELS || originalBytes < MIN_EMBEDDED_BYTES
}
