package com.vextech.editpro.core.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class HideKind { BLACK, PIXELATE, BLUR }

/** A box to hide, as fractions of the picture (0 to 1), so it covers the same spot at any size. [strength] is 1 to 10. */
data class HideBox(val left: Float, val top: Float, val right: Float, val bottom: Float, val kind: HideKind, val strength: Int)

data class PixelRect(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Covers parts of a picture, ported from the site's `redact.ts`. These work on the box's own pixels (ARGB ints, as from `Bitmap.getPixels`),
 * change them in place, and keep nothing of what was underneath a black box.
 */
object Redact {
    const val MIN_STRENGTH = 1
    const val MAX_STRENGTH = 10

    /** The box in whole pixels, kept inside a [width] x [height] picture; null when nothing is left. */
    fun rectFor(box: HideBox, width: Int, height: Int): PixelRect? {
        val x1 = max(0, (min(box.left, box.right) * width).roundToInt())
        val y1 = max(0, (min(box.top, box.bottom) * height).roundToInt())
        val x2 = min(width, (max(box.left, box.right) * width).roundToInt())
        val y2 = min(height, (max(box.top, box.bottom) * height).roundToInt())
        return if (x2 > x1 && y2 > y1) PixelRect(x1, y1, x2 - x1, y2 - y1) else null
    }

    /** Strength 1 to 10 as pixels, scaled to the picture so a setting looks the same on a small photo and a big one. Black has none. */
    fun strengthPx(minSide: Int, strength: Int, kind: HideKind): Int {
        val s = strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH)
        return when (kind) {
            HideKind.BLACK -> 0
            HideKind.PIXELATE -> max(4, (minSide * s / 150.0).roundToInt())
            HideKind.BLUR -> max(3, (minSide * s / 100.0).roundToInt())
        }
    }

    /** One solid colour (black by default). */
    fun fill(px: IntArray, color: Int = 0xFF000000.toInt()) = px.fill(color)

    /** Replaces each [block] x [block] square with its average colour. */
    fun pixelate(px: IntArray, w: Int, h: Int, block: Int) {
        val b = max(1, block)
        var by = 0
        while (by < h) {
            val y2 = min(by + b, h)
            var bx = 0
            while (bx < w) {
                val x2 = min(bx + b, w)
                var r = 0L; var g = 0L; var bl = 0L
                val n = (x2 - bx) * (y2 - by)
                for (y in by until y2) for (x in bx until x2) {
                    val c = px[y * w + x]
                    r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bl += c and 0xFF
                }
                val avg = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
                for (y in by until y2) for (x in bx until x2) px[y * w + x] = avg
                bx += b
            }
            by += b
        }
    }

    /**
     * Box blur with [passes] passes each way. Edges repeat, so nothing from outside the box leaks in. A small radius can be partly undone
     * by software, so pixelate or a black box is the way to hide anything private.
     */
    fun blur(px: IntArray, w: Int, h: Int, radius: Int, passes: Int = 3) {
        val rad = max(1, radius)
        repeat(passes) {
            blurLines(px, w, h, rad, horizontal = true)
            blurLines(px, w, h, rad, horizontal = false)
        }
        for (i in px.indices) px[i] = px[i] or (0xFF shl 24)
    }

    private fun blurLines(px: IntArray, w: Int, h: Int, rad: Int, horizontal: Boolean) {
        val n = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val step = if (horizontal) 1 else w
        val lineStep = if (horizontal) w else 1
        val div = 2 * rad + 1
        val line = IntArray(n)
        for (l in 0 until lines) for (shift in intArrayOf(16, 8, 0)) {
            val base = l * lineStep
            for (i in 0 until n) line[i] = (px[base + i * step] shr shift) and 0xFF
            var sum = 0
            for (k in -rad..rad) sum += line[k.coerceIn(0, n - 1)]
            for (i in 0 until n) {
                val v = (sum + div / 2) / div
                val idx = base + i * step
                px[idx] = (px[idx] and (0xFF shl shift).inv()) or (v shl shift)
                sum += line[min(n - 1, i + rad + 1)] - line[max(0, i - rad)]
            }
        }
    }

    /** Hides [px] (the box's pixels, [w] x [h]) as [kind] says, on a picture whose shorter side is [minSide]. */
    fun apply(px: IntArray, w: Int, h: Int, kind: HideKind, strength: Int, minSide: Int) {
        when (kind) {
            HideKind.BLACK -> fill(px)
            HideKind.PIXELATE -> pixelate(px, w, h, strengthPx(minSide, strength, kind))
            HideKind.BLUR -> blur(px, w, h, strengthPx(minSide, strength, kind))
        }
    }
}

/** What Blur or Hide shows: the way to hide, its strength, the boxes drawn so far and the one being dragged. */
data class HideForm(
    val kind: HideKind = HideKind.BLACK,
    val strength: Int = 6,
    val boxes: List<HideBox> = emptyList(),
    val start: DrawPoint? = null,
    val end: DrawPoint? = null,
) {
    val canApply: Boolean get() = boxes.isNotEmpty()
    val dragging: Boolean get() = start != null && end != null

    fun down(p: DrawPoint) = copy(start = p, end = p)
    fun move(p: DrawPoint) = if (start == null) this else copy(end = p)

    /** The finger lifts: keeps the box unless it is a stray tap (under 1 percent of the picture either way). */
    fun up(): HideForm {
        val s = start ?: return this
        val e = end ?: return copy(start = null, end = null)
        val big = kotlin.math.abs(e.x - s.x) > MIN_SIDE && kotlin.math.abs(e.y - s.y) > MIN_SIDE
        val box = HideBox(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y), kind, strength)
        return copy(boxes = if (big) boxes + box else boxes, start = null, end = null)
    }

    fun undoLast() = copy(boxes = boxes.dropLast(1))
    fun cleared() = copy(boxes = emptyList(), start = null, end = null)

    private companion object { const val MIN_SIDE = 0.01f }
}
