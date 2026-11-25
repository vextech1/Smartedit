package com.vextech.editpro.core.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where a photo watermark goes: one of nine spots, or repeated over the whole photo. Same spots as the site (`watermark.ts`). */
enum class MarkSpot(val row: Char, val col: Char) {
    TL('t', 'l'), TC('t', 'c'), TR('t', 'r'),
    ML('m', 'l'), MC('m', 'c'), MR('m', 'r'),
    BL('b', 'l'), BC('b', 'c'), BR('b', 'r'),
    TILE('-', '-'),
}

enum class WatermarkType { TEXT, LOGO }

/** A point in pixels. */
data class PxPoint(val x: Double, val y: Double)

/**
 * What the Watermark sheet sets, and what Edit Image replays. [sizePct] is the mark's width as a share of the photo's width,
 * [angle] is clockwise degrees, [marginPct] is the distance from the edge as a share of the photo's shorter side, [gapPct] is the
 * space between repeats. [atX], [atY] are the mark's centre as a share of the photo when it was dragged to a free spot.
 */
data class WatermarkSpec(
    val type: WatermarkType = WatermarkType.TEXT,
    val text: String = "",
    val color: Int = 0xFFFFFFFF.toInt(),
    val outline: Boolean = true,
    val logoPath: String? = null,
    val logoName: String = "",
    val spot: MarkSpot = MarkSpot.BR,
    val sizePct: Int = 30,
    val opacityPct: Int = 60,
    val angle: Int = 0,
    val marginPct: Int = 3,
    val gapPct: Int = 60,
    val atX: Double? = null,
    val atY: Double? = null,
) {
    val at: PxPoint? get() = if (atX != null && atY != null && spot != MarkSpot.TILE) PxPoint(atX, atY) else null

    /** True when there is something to draw: some words, or a logo. */
    val ready: Boolean get() = if (type == WatermarkType.TEXT) text.isNotBlank() else logoPath != null

    val changed: Boolean get() = this != WatermarkSpec()

    fun withSpot(s: MarkSpot) = copy(spot = s, atX = null, atY = null)
}

/** Watermark placement maths, ported from the site's `watermark.ts`. */
object WatermarkMath {
    const val MIN_SIZE = 5
    const val MAX_SIZE = 100

    val COLORS: List<Int> = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFE11D48.toInt(), 0xFF2563EB.toInt(), 0xFFF59E0B.toInt())

    /** The mark's size: [pct] percent of the photo's width, but never taller than 90 percent of its height. */
    fun markSize(imgW: Int, imgH: Int, aspect: Double, pct: Int): Pair<Double, Double> {
        var w = imgW * pct.coerceIn(1, 100) / 100.0
        var h = w / aspect
        if (h > imgH * 0.9) { h = imgH * 0.9; w = h * aspect }
        return w to h
    }

    /** Top-left corner of a single mark, [marginPct] percent of the photo's shorter side in from the edge. */
    fun placeMark(imgW: Int, imgH: Int, w: Double, h: Double, spot: MarkSpot, marginPct: Int): PxPoint {
        val m = min(imgW, imgH) * max(0, marginPct) / 100.0
        val x = when (spot.col) { 'l' -> m; 'r' -> imgW - w - m; else -> (imgW - w) / 2 }
        val y = when (spot.row) { 't' -> m; 'b' -> imgH - h - m; else -> (imgH - h) / 2 }
        return PxPoint(x, y)
    }

    /** The size (a percent of the photo's width, 5 to 100) after a pinch scaled it by [factor] from [start]. */
    fun pinchSize(start: Int, factor: Double): Int = (start * factor).roundToInt().coerceIn(MIN_SIZE, MAX_SIZE)

    /** Keeps a mark's centre (a 0 to 1 share of the photo) far enough in that the whole mark stays on the photo. */
    fun clampCenter(imgW: Int, imgH: Int, w: Double, h: Double, c: PxPoint): PxPoint {
        val hx = min(0.5, w / 2 / imgW)
        val hy = min(0.5, h / 2 / imgH)
        return PxPoint(c.x.coerceIn(hx, 1 - hx), c.y.coerceIn(hy, 1 - hy))
    }

    /** Top-left corner of a mark dragged to a free spot: [at] is its centre as a share of the photo, kept inside it. */
    fun placeAt(imgW: Int, imgH: Int, w: Double, h: Double, at: PxPoint): PxPoint {
        val c = clampCenter(imgW, imgH, w, h, at)
        return PxPoint(c.x * imgW - w / 2, c.y * imgH - h / 2)
    }

    /** The centre, as a share of the photo, of the mark a spot puts down. Where a drag starts from. */
    fun centerOf(imgW: Int, imgH: Int, w: Double, h: Double, spot: MarkSpot, marginPct: Int): PxPoint {
        val p = placeMark(imgW, imgH, w, h, spot, marginPct)
        return PxPoint((p.x + w / 2) / imgW, (p.y + h / 2) / imgH)
    }

    /** Top-left corners of marks repeated over an area, every other row shifted half a step. */
    fun tilePoints(areaW: Double, areaH: Double, w: Double, h: Double, gapPct: Int): List<PxPoint> {
        val sx = w * (1 + max(0, gapPct) / 100.0)
        val sy = h * (1 + max(0, gapPct) / 100.0)
        if (sx <= 0 || sy <= 0) return emptyList()
        val pts = ArrayList<PxPoint>()
        var row = 0
        var y = 0.0
        while (y < areaH && pts.size < MAX_TILES) {
            var x = if (row % 2 == 1) -sx / 2 else 0.0
            while (x < areaW && pts.size < MAX_TILES) {
                if (x + w > 0) pts.add(PxPoint(x, y))
                x += sx
            }
            row++
            y += sy
        }
        return pts
    }

    /** A cap so a tiny mark cannot build millions of repeats. */
    const val MAX_TILES = 2000

    /** Where a drag of ([dxShare], [dyShare]) of the photo moves a mark that sits at [from]. */
    fun dragged(imgW: Int, imgH: Int, w: Double, h: Double, from: PxPoint, dxShare: Double, dyShare: Double): PxPoint =
        clampCenter(imgW, imgH, w, h, PxPoint(from.x + dxShare, from.y + dyShare))

    /** Outline colour that shows against [color] (black or white, as the site's `textOn`). */
    fun outlineFor(color: Int): Int {
        val r = (color shr 16) and 0xFF; val g = (color shr 8) and 0xFF; val b = color and 0xFF
        fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return if (0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b) > 0.179) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
}
