package com.vextech.editpro.core.image

import kotlin.math.max
import kotlin.math.min

/** How a photo lands on the new size: crop to fill, show the whole photo with colour around it, or stretch. */
enum class FitMode { FILL, FIT, STRETCH }

/**
 * Where to read from the photo and where to draw it, in pixels. [slackX] and [slackY] are how far the crop window can still move (source pixels).
 * [scale] is output pixels per source pixel; above 1 means the photo is being enlarged.
 */
data class FitResult(
    val sx: Double, val sy: Double, val sw: Double, val sh: Double,
    val dx: Double, val dy: Double, val dw: Double, val dh: Double,
    val slackX: Double, val slackY: Double,
    val scale: Double,
)

/** Fit, pan and file-size maths for Resize, ported from the site's `resize.ts` so both give the same result. */
object ResizeFit {
    private fun clamp1(v: Double) = max(-1.0, min(1.0, v))

    /** [zoom] is 1 or more; [panX] and [panY] run -1..1 (0 is centred). Only [FitMode.FILL] uses zoom and pan. */
    fun fitRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int, mode: FitMode, zoom: Double = 1.0, panX: Double = 0.0, panY: Double = 0.0): FitResult {
        val sw = srcW.toDouble(); val sh = srcH.toDouble(); val dw = dstW.toDouble(); val dh = dstH.toDouble()
        return when (mode) {
            FitMode.STRETCH -> FitResult(0.0, 0.0, sw, sh, 0.0, 0.0, dw, dh, 0.0, 0.0, max(dw / sw, dh / sh))
            FitMode.FIT -> {
                val s = min(dw / sw, dh / sh)
                val w = sw * s; val h = sh * s
                FitResult(0.0, 0.0, sw, sh, (dw - w) / 2, (dh - h) / 2, w, h, 0.0, 0.0, s)
            }
            FitMode.FILL -> {
                val s = max(dw / sw, dh / sh) * max(1.0, zoom)
                val vw = dw / s; val vh = dh / s
                val slackX = max(0.0, sw - vw); val slackY = max(0.0, sh - vh)
                FitResult(slackX * (0.5 + clamp1(panX) / 2), slackY * (0.5 + clamp1(panY) / 2), vw, vh, 0.0, 0.0, dw, dh, slackX, slackY, s)
            }
        }
    }

    /**
     * New pan (-1..1 on each axis) after dragging the photo by [dx], [dy] screen pixels inside a box [boxW] x [boxH].
     * Dragging the photo right reveals more of its left side.
     */
    fun panBy(startX: Double, startY: Double, dx: Double, dy: Double, boxW: Double, boxH: Double, fit: FitResult): Pair<Double, Double> {
        val sx = dx / max(1.0, boxW) * fit.sw
        val sy = dy / max(1.0, boxH) * fit.sh
        val px = if (fit.slackX > 0) clamp1(startX - sx / (fit.slackX / 2)) else startX
        val py = if (fit.slackY > 0) clamp1(startY - sy / (fit.slackY / 2)) else startY
        return px to py
    }

    /**
     * The highest quality (10..95) whose file still fits in [maxBytes], by halving the range. [sizeAt] gives the file size for a quality.
     * Returns the quality and whether it fits; when even the lowest quality is too big it returns the lowest with false.
     */
    fun bestQuality(maxBytes: Long, lo: Int = 10, hi: Int = 95, steps: Int = 7, sizeAt: (Int) -> Long): Pair<Int, Boolean> {
        if (sizeAt(hi) <= maxBytes) return hi to true
        if (sizeAt(lo) > maxBytes) return lo to false
        var low = lo; var high = hi; var best = lo
        repeat(steps) {
            val mid = (low + high) / 2
            if (mid == low || mid == high) return best to true
            if (sizeAt(mid) <= maxBytes) { best = mid; low = mid } else high = mid
        }
        return best to true
    }
}
