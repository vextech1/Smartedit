package com.vextech.editpro.core.image

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pixel maths for Adjust, ported from the site's `image-edit.ts` so both give the same look. No Android types, so it runs in unit tests.
 */
object ToneMath {
    /**
     * Brightness and contrast as one 256-entry lookup table that keeps pure black and pure white where they are, so lifting the light
     * or adding punch never throws pixels away. Both inputs run -100..100. Brightness bends the middle tones with a gamma (positive is lighter);
     * contrast is a smooth S-curve (negative flattens).
     */
    fun toneTable(brightness: Float, contrast: Float): IntArray {
        val g = 2.0.pow(-brightness.coerceIn(-100f, 100f) / 50.0)
        val a = contrast.coerceIn(-100f, 100f) / 100.0
        return IntArray(256) { v ->
            val x = (v / 255.0).pow(g)
            (255.0 * (x - a * sin(2 * PI * x) / (2 * PI))).roundToInt().coerceIn(0, 255)
        }
    }

    /** Warm (positive) adds red and removes blue; cool does the reverse. [temperature] runs -100..100. Returns the red/blue shift in 0..255 levels. */
    fun warmShift(temperature: Float): Float = temperature.coerceIn(-100f, 100f) * 0.4f

    /** Tint (positive is magenta) removes green. [tint] runs -100..100. Returns the green shift in 0..255 levels. */
    fun greenShift(tint: Float): Float = tint.coerceIn(-100f, 100f) * 0.3f

    /**
     * "Clean document": grey, then a hard contrast stretch so paper goes to white and ink to black, as on the site.
     * Works on packed ARGB ints in place; the alpha channel is kept.
     */
    fun cleanDocument(pixels: IntArray, contrast: Float = DOCUMENT_CONTRAST) {
        val c = contrast.coerceIn(-100f, 100f) * 2.55f
        val k = (259f * (c + 255f)) / (255f * (259f - c))
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val v = (k * (lum - 128f) + 128f).roundToInt().coerceIn(0, 255)
            pixels[i] = (p and 0xFF000000.toInt()) or (v shl 16) or (v shl 8) or v
        }
    }

    const val DOCUMENT_CONTRAST = 45f
}
