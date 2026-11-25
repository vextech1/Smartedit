package com.vextech.editpro.core.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Colour maths for the Colour Picker, ported from the site's `palette.ts`. Colours are ARGB ints. No Android here. */
object ColourMath {
    const val KEEP = 24

    fun rgb(color: Int): Triple<Int, Int, Int> = Triple((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)

    fun color(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** "#rrggbb", lower case. */
    fun hex(color: Int): String = "#%06x".format(color and 0xFFFFFF)

    /** "#abc", "abc", "#aabbcc" or "aabbcc" as a colour, or null when it is not a colour. */
    fun parseHex(text: String): Int? {
        val m = Regex("^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$").matchEntire(text.trim()) ?: return null
        val digits = m.groupValues[1].let { if (it.length == 3) it.map { c -> "$c$c" }.joinToString("") else it }
        return 0xFF000000.toInt() or digits.toInt(16)
    }

    /** Hue 0 to 360, saturation and lightness 0 to 100. */
    fun hsl(color: Int): Triple<Int, Int, Int> {
        val (r, g, b) = rgb(color)
        val rf = r / 255.0; val gf = g / 255.0; val bf = b / 255.0
        val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf))
        val d = mx - mn
        val l = (mx + mn) / 2
        if (d == 0.0) return Triple(0, 0, (l * 100).roundToInt())
        val s = d / (1 - abs(2 * l - 1))
        val h = when (mx) { rf -> ((gf - bf) / d) % 6; gf -> (bf - rf) / d + 2; else -> (rf - gf) / d + 4 }
        return Triple((((h * 60) + 360) % 360).roundToInt(), (s * 100).roundToInt(), (l * 100).roundToInt())
    }

    /** Black or white, whichever is easier to read on [color]. */
    fun textOn(color: Int): Int {
        fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        val (r, g, b) = rgb(color)
        return if (0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b) > 0.179) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    /** "rgb(r, g, b)". */
    fun rgbText(color: Int): String = rgb(color).let { (r, g, b) -> "rgb($r, $g, $b)" }

    /** "hsl(h, s%, l%)". */
    fun hslText(color: Int): String = hsl(color).let { (h, s, l) -> "hsl($h, $s%, $l%)" }

    private class Range(val channel: Int, val span: Int)

    private fun range(box: List<IntArray>): Range {
        val lo = intArrayOf(255, 255, 255); val hi = intArrayOf(0, 0, 0)
        for (p in box) for (c in 0..2) { if (p[c] < lo[c]) lo[c] = p[c]; if (p[c] > hi[c]) hi[c] = p[c] }
        val r = IntArray(3) { hi[it] - lo[it] }
        val span = r.max()
        return Range(r.indexOf(span), span)
    }

    /**
     * The main colours of a picture ([pixels] are ARGB), most common first. Median cut on a sample. Fewer than [count] come back when the
     * picture has fewer distinct colours. Mostly see-through pixels are left out.
     */
    fun extract(pixels: IntArray, count: Int = 6, sample: Int = 20000): List<Int> {
        val step = max(1, pixels.size / sample)
        val px = ArrayList<IntArray>()
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            if (((c ushr 24) and 0xFF) >= 128) px.add(intArrayOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF))
            i += step
        }
        if (px.isEmpty()) return emptyList()
        var boxes: List<List<IntArray>> = listOf(px)
        while (boxes.size < count) {
            var best = -1; var bestN = 0
            boxes.forEachIndexed { idx, b -> if (b.size > bestN && range(b).span > 0) { best = idx; bestN = b.size } }
            if (best < 0) break
            val ch = range(boxes[best]).channel
            val sorted = boxes[best].sortedBy { it[ch] }
            // Cut at the value change nearest the middle, so a group of identical colours is never torn in two.
            var mid = sorted.size shr 1
            for (d in sorted.indices) {
                val lo = mid - d; val hi = mid + d
                if (lo > 0 && sorted[lo][ch] != sorted[lo - 1][ch]) { mid = lo; break }
                if (hi < sorted.size && hi > 0 && sorted[hi][ch] != sorted[hi - 1][ch]) { mid = hi; break }
            }
            val next = ArrayList<List<IntArray>>(boxes)
            next.removeAt(best)
            next.add(best, sorted.subList(mid, sorted.size))
            next.add(best, sorted.subList(0, mid))
            boxes = next.filter { it.isNotEmpty() }
        }
        return boxes.sortedByDescending { it.size }
            .map { b ->
                val n = b.size
                color((b.sumOf { it[0] }.toDouble() / n).roundToInt(), (b.sumOf { it[1] }.toDouble() / n).roundToInt(), (b.sumOf { it[2] }.toDouble() / n).roundToInt())
            }
            .distinct()
    }
}

/** What the Colour Picker shows: the colour read, where on the photo it was read, and the colours kept. */
data class PickerForm(
    val current: Int = 0xFF000000.toInt(),
    val at: DrawPoint? = null,
    val kept: List<Int> = emptyList(),
) {
    val canKeep: Boolean get() = current !in kept && kept.size < ColourMath.KEEP
    fun keep(): PickerForm = if (canKeep) copy(kept = kept + current) else this
    fun use(color: Int): PickerForm = copy(current = color)
}
