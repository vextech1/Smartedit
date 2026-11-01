package com.vextech.smart_edit.feature.scanner.domain

/**
 * Capture presets. [minArea] is the smallest share of the frame the outline must cover to count as the subject
 * (also the "move closer" threshold); [crops] says whether the outline pre-crops the shot.
 */
enum class ScanMode(val minArea: Float, val crops: Boolean) {
    DOCUMENT(0.20f, true),
    ID_CARD(0.10f, true),       // cards are small in the frame
    RECEIPT(0.08f, true),       // long and narrow
    WHITEBOARD(0.20f, true),
    BOOK(0.30f, false),         // the whole spread is kept and split in two
}

enum class CutoutMode { SIGNATURE, PHOTO }

/** Where card pictures go on an A4 page at 300 dpi. Cards are printed at their real size (ID-1: 85.6 x 54 mm). */
object IdCardLayout {
    const val PAGE_W = 2480
    const val PAGE_H = 3508
    const val CARD_W = 1011
    const val CARD_H = 638
    private const val GAP = 150

    data class Slot(val x: Int, val y: Int, val w: Int, val h: Int)

    /** One slot for a single side, two stacked slots for front and back; the block is centred on the page. */
    fun slots(sides: Int): List<Slot> {
        require(sides in 1..2)
        val total = sides * CARD_H + (sides - 1) * GAP
        val top = (PAGE_H - total) / 2
        val x = (PAGE_W - CARD_W) / 2
        return List(sides) { Slot(x, top + it * (CARD_H + GAP), CARD_W, CARD_H) }
    }

    /** Largest rectangle with the picture's aspect ratio that fits [slot], centred in it. */
    fun fit(slot: Slot, pictureW: Int, pictureH: Int): Slot {
        require(pictureW > 0 && pictureH > 0)
        val scale = minOf(slot.w.toFloat() / pictureW, slot.h.toFloat() / pictureH)
        val w = (pictureW * scale).toInt().coerceAtLeast(1); val h = (pictureH * scale).toInt().coerceAtLeast(1)
        return Slot(slot.x + (slot.w - w) / 2, slot.y + (slot.h - h) / 2, w, h)
    }

    /** A card photographed upright (taller than wide) is turned to landscape. */
    fun needsTurn(w: Int, h: Int) = h > w
}

/** Finds where to cut a photographed book spread into two pages. */
object SpreadSplitter {
    private const val MIN_DEPTH = 12f      // the gutter must be at least this much darker than its surroundings

    /** Fraction of the width (0..1) of the dark gutter line near the middle, or 0.5 when there is no clear one. */
    fun gutter(frame: LumaFrame): Float {
        val w = frame.width; val h = frame.height
        if (w < 20 || h < 8) return 0.5f
        val y0 = h / 10; val y1 = h - h / 10
        val col = FloatArray(w) { x -> var s = 0L; for (y in y0 until y1) s += frame.data[y * w + x]; s.toFloat() / (y1 - y0) }
        val lo = (w * 0.38f).toInt(); val hi = (w * 0.62f).toInt()
        var best = lo; var bestVal = Float.MAX_VALUE
        for (x in lo..hi) {
            val v = (col[x - 1] + col[x] + col[x + 1]) / 3f
            if (v < bestVal) { bestVal = v; best = x }
        }
        val side = (w * 0.08f).toInt().coerceAtLeast(2)
        val around = (col[(best - 3 * side).coerceAtLeast(0)] + col[(best + 3 * side).coerceAtMost(w - 1)]) / 2f
        return if (around - bestVal >= MIN_DEPTH) (best + 0.5f) / w else 0.5f
    }
}
