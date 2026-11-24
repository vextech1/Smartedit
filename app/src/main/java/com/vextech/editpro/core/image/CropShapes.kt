package com.vextech.editpro.core.image

/**
 * Crop shapes the Edit Image crop sheet offers: the site's list (Free, Original, 1:1, 4:5, 3:2, 16:9, 9:16) plus the app's own 4:3 and 3:4.
 * [ratio] is width divided by height; null means free. [original] means the picture's own shape, which depends on the photo.
 */
enum class CropShape(val ratio: Float?, val original: Boolean = false) {
    FREE(null),
    ORIGINAL(null, original = true),
    SQUARE(1f),
    R4_5(4f / 5f),
    R3_2(3f / 2f),
    R4_3(4f / 3f),
    R3_4(3f / 4f),
    R16_9(16f / 9f),
    R9_16(9f / 16f);

    /** The width / height this shape asks for on a picture whose own shape is [imageAspect]; null for Free. */
    fun ratioFor(imageAspect: Float): Float? = if (original) imageAspect.takeIf { it > 0f } else ratio

    /** The label that is a plain ratio such as "4:5", or null for Free and Original, which use words. */
    val ratioLabel: String?
        get() = when (this) {
            FREE, ORIGINAL -> null
            SQUARE -> "1:1"
            R4_5 -> "4:5"
            R3_2 -> "3:2"
            R4_3 -> "4:3"
            R3_4 -> "3:4"
            R16_9 -> "16:9"
            R9_16 -> "9:16"
        }
}
