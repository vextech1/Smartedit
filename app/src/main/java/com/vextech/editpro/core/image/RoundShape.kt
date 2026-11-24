package com.vextech.editpro.core.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A picture shape the Round sheet can crop to. [ratio] is width / height; 0 keeps the photo's own shape. */
data class PictureRatio(val id: String, val ratio: Double)

/** One of the three ready-made shapes: a picture shape and a corner roundness (0 to 50 percent of the shorter side). */
data class RoundPreset(val id: String, val ratioId: String, val radiusPct: Int)

/** What Round asks the editor to make: crop to [ratio] (zoomed and panned as set), then cut the corners to [radiusPct]. */
data class RoundSpec(val ratio: Double, val radiusPct: Int, val zoom: Double, val panX: Double, val panY: Double)

/** Crop shapes and rounded corners, ported from the site's `shape.ts` and Round sheet. */
object RoundShapes {
    const val ORIGINAL = "original"
    const val MAX_RADIUS = 50
    const val CIRCLE = "circle"

    val ratios: List<PictureRatio> = listOf(
        PictureRatio(ORIGINAL, 0.0), PictureRatio("1:1", 1.0), PictureRatio("4:5", 4.0 / 5), PictureRatio("3:4", 3.0 / 4),
        PictureRatio("9:16", 9.0 / 16), PictureRatio("3:2", 3.0 / 2), PictureRatio("16:9", 16.0 / 9),
    )

    val presets: List<RoundPreset> = listOf(
        RoundPreset(CIRCLE, "1:1", 50), RoundPreset("rounded", "1:1", 18), RoundPreset("soft", ORIGINAL, 10),
    )

    fun ratioOf(id: String): Double = ratios.firstOrNull { it.id == id }?.ratio ?: 0.0

    /** The largest area of the photo with this shape (ratio 0 is the photo's own shape), no larger than [maxSide] on its long side. */
    fun outputSize(srcW: Int, srcH: Int, ratio: Double, maxSide: Int): Pair<Int, Int> {
        var w = srcW.toDouble(); var h = srcH.toDouble()
        if (ratio > 0) {
            if (srcW.toDouble() / srcH > ratio) w = srcH * ratio else h = srcW / ratio
        }
        val k = min(1.0, maxSide.toDouble() / max(w, h))
        return max(1, (w * k).roundToInt()) to max(1, (h * k).roundToInt())
    }

    /** Corner radius in pixels for a 0 to 50 percent setting. 50 percent on a square is a circle. */
    fun radiusFor(w: Int, h: Int, pct: Int): Double = min(w, h) * pct.coerceIn(0, MAX_RADIUS) / 100.0
}

/** Everything the Round sheet shows. Starts as a circle, as on the site. */
data class RoundForm(
    val ratioId: String = "1:1",
    val radius: Int = 50,
    val zoom: Double = 1.0,
    val panX: Double = 0.0,
    val panY: Double = 0.0,
) {
    /** The ready-made shape these settings match, or null when they are custom. */
    val presetId: String? get() = RoundShapes.presets.firstOrNull { it.ratioId == ratioId && it.radiusPct == radius }?.id

    fun withPreset(id: String): RoundForm {
        val p = RoundShapes.presets.firstOrNull { it.id == id } ?: return this
        return copy(ratioId = p.ratioId, radius = p.radiusPct, panX = 0.0, panY = 0.0)
    }

    fun withRatio(id: String): RoundForm = copy(ratioId = id, panX = 0.0, panY = 0.0)

    fun spec(): RoundSpec = RoundSpec(RoundShapes.ratioOf(ratioId), radius.coerceIn(0, RoundShapes.MAX_RADIUS), zoom.coerceAtLeast(1.0), panX, panY)

    /** The size the result will have for a photo [srcW] x [srcH]. */
    fun outputSize(srcW: Int, srcH: Int): Pair<Int, Int> = RoundShapes.outputSize(srcW, srcH, RoundShapes.ratioOf(ratioId), max(srcW, srcH))

    val changed: Boolean get() = this != RoundForm()
}
