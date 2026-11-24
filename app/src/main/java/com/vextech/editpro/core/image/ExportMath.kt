package com.vextech.editpro.core.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The formats the Export sheet offers, as on the site: JPG, PNG and WebP. */
enum class ExportFormat(val extension: String, val mimeType: String, val lossy: Boolean) {
    JPEG("jpg", "image/jpeg", true),
    PNG("png", "image/png", false),
    WEBP("webp", "image/webp", true),
}

/** What the Export sheet asks for. [longestSide] 0 means keep the size; [quality] is 10 to 100 and ignored for PNG. */
data class ExportRequest(val format: ExportFormat, val quality: Int, val longestSide: Int)

data class ExportEstimate(val width: Int, val height: Int, val bytes: Long)

object ExportMath {
    /** The longest-side choices: keep the size, or shrink to one of these. */
    val SIDES: List<Int> = listOf(0, 2048, 1280, 800)

    const val DEFAULT_QUALITY = 90
    const val MIN_QUALITY = 10

    /** JPEG has no see-through pixels, so a picture with transparency is offered PNG and WebP only. */
    fun formatsFor(hasAlpha: Boolean): List<ExportFormat> =
        if (hasAlpha) listOf(ExportFormat.PNG, ExportFormat.WEBP) else ExportFormat.entries

    /** The format the sheet opens on: PNG when the picture has transparency, otherwise JPG. */
    fun startFormat(hasAlpha: Boolean): ExportFormat = if (hasAlpha) ExportFormat.PNG else ExportFormat.JPEG

    /** Size after limiting the longest side to [side] (0 keeps it). Never enlarges, never below 1 px. */
    fun limitSide(width: Int, height: Int, side: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (side <= 0 || longest <= side || longest <= 0) return width to height
        val k = side.toDouble() / longest
        return max(1, (width * k).roundToInt()) to max(1, (height * k).roundToInt())
    }

    /** Scale a sample file size to the pixels the real picture will have, assuming size grows with pixel count. Never below the sample. */
    fun scaleBytes(sampleBytes: Long, samplePixels: Long, targetPixels: Long): Long {
        if (samplePixels <= 0L) return sampleBytes
        val k = max(1.0, targetPixels.toDouble() / samplePixels)
        return (sampleBytes * k).toLong()
    }

    /** `photo.jpg` and `JPEG` give `photo_edited.jpg`. */
    fun fileName(originalName: String, format: ExportFormat): String {
        val base = originalName.substringBeforeLast('.', originalName).trim().ifBlank { "image" }
        return "${base}_edited.${format.extension}"
    }

    /** Pixel size of the sample to encode for an estimate: the final size, but not larger than [maxSide] on its long side. */
    fun sampleSize(finalWidth: Int, finalHeight: Int, maxSide: Int): Pair<Int, Int> {
        val longest = max(finalWidth, finalHeight)
        if (longest <= maxSide) return finalWidth to finalHeight
        return limitSide(finalWidth, finalHeight, min(maxSide, longest))
    }
}
