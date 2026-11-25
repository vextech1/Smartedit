package com.vextech.editpro.core.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A box in pixels. */
data class PixelBox(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Finds the box round what stands out from the background of a picture, ported from the site's `auto-crop.ts`. The background is taken to be
 * the colour along the picture's border, so it suits a page with white margins, a scan with a dark rim, or a paper or object on a plain surface.
 * It looks for a straight box only: it does not find the corners of a tilted sheet.
 */
object AutoCrop {
    /** The longest side, in pixels, the picture is shrunk to before looking for edges. Small is quick and ignores speckle. */
    const val DETECT_SIDE = 240

    /**
     * The box round the content of [argb] (w x h pixels), or null when nothing clear stands out, or when the box would be the whole
     * picture anyway, so the caller keeps the full picture selected.
     */
    fun detect(argb: IntArray, w: Int, h: Int): PixelBox? {
        if (w < 16 || h < 16 || argb.size < w * h) return null
        val ring = max(1, (min(w, h) * 0.02).roundToInt())
        fun onRing(x: Int, y: Int) = x < ring || y < ring || x >= w - ring || y >= h - ring
        // See-through pixels count as white.
        fun channels(c: Int): IntArray {
            val a = ((c ushr 24) and 0xFF) / 255.0
            val k = 255 * (1 - a)
            return intArrayOf(
                (((c shr 16) and 0xFF) * a + k).roundToInt(), (((c shr 8) and 0xFF) * a + k).roundToInt(), ((c and 0xFF) * a + k).roundToInt(),
            )
        }
        val edge = Array(3) { ArrayList<Int>() }
        for (y in 0 until h) for (x in 0 until w) {
            if (!onRing(x, y)) continue
            val c = channels(argb[y * w + x])
            for (k in 0..2) edge[k].add(c[k])
        }
        val bg = IntArray(3) { k -> edge[k].sorted().let { it[it.size shr 1] } }

        val diff = IntArray(w * h)
        val onEdge = ArrayList<Int>()
        for (y in 0 until h) for (x in 0 until w) {
            val c = channels(argb[y * w + x])
            val d = max(abs(c[0] - bg[0]), max(abs(c[1] - bg[1]), abs(c[2] - bg[2])))
            diff[y * w + x] = d
            if (onRing(x, y)) onEdge.add(d)
        }
        onEdge.sort()
        val threshold = max(32, onEdge[(onEdge.size * 0.95).toInt()] + 16)

        val rows = IntArray(h)
        val cols = IntArray(w)
        for (y in 0 until h) for (x in 0 until w) if (diff[y * w + x] > threshold) { rows[y]++; cols[x]++ }
        val needRow = max(2, (w * 0.015).roundToInt())
        val needCol = max(2, (h * 0.015).roundToInt())
        fun first(n: IntArray, need: Int): Int { for (i in 0 until n.size - 1) if (n[i] >= need && n[i + 1] >= need) return i; return -1 }
        fun last(n: IntArray, need: Int): Int { for (i in n.size - 1 downTo 1) if (n[i] >= need && n[i - 1] >= need) return i; return -1 }
        val top = first(rows, needRow); val bottom = last(rows, needRow)
        val left = first(cols, needCol); val right = last(cols, needCol)
        if (top < 0 || bottom <= top || left < 0 || right <= left) return null

        val pad = max(1, (min(w, h) * 0.02).roundToInt())
        val x0 = max(0, left - pad); val y0 = max(0, top - pad)
        val x1 = min(w, right + 1 + pad); val y1 = min(h, bottom + 1 + pad)
        val box = PixelBox(x0, y0, x1 - x0, y1 - y0)
        if (box.w.toLong() * box.h < w.toLong() * h * 0.04) return null
        if (box.w >= w * 0.98 && box.h >= h * 0.98) return null
        return box
    }

    /** The size to shrink a [w] x [h] picture to before [detect]. */
    fun detectSize(w: Int, h: Int): Pair<Int, Int> {
        val k = min(1.0, DETECT_SIDE.toDouble() / max(w, h))
        return max(1, (w * k).roundToInt()) to max(1, (h * k).roundToInt())
    }
}
