package com.vextech.editpro.core.image

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One photo's place in a collage, in output pixels. */
data class CollageCell(val x: Int, val y: Int, val w: Int, val h: Int)

data class CollageLayout(val w: Int, val h: Int, val cells: List<CollageCell>)

enum class CollageKind { GRID, ROW, COLUMN }

/** A box shape for the Grid, as width over height. */
data class CellShape(val id: String, val ratio: Double)

/** Layouts for combining photos into one picture, ported from the site's `collage.ts`. No Android here. */
object Collage {
    const val MAX_PHOTOS = 12

    /** Same ceilings as the photo editor, so a collage can always be opened and edited there. */
    const val MAX_SIDE = 8000
    const val MAX_PIXELS = 40_000_000L

    val SHAPES: List<CellShape> = listOf(
        CellShape("1:1", 1.0), CellShape("4:3", 4.0 / 3), CellShape("3:4", 3.0 / 4),
        CellShape("3:2", 3.0 / 2), CellShape("16:9", 16.0 / 9), CellShape("9:16", 9.0 / 16),
    )

    val WIDTHS: List<Int> = listOf(1080, 2000, 3000)

    val BACKGROUNDS: List<Int> = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFF2EAD8.toInt(), 0xFFC9CCD1.toInt(), 0xFF1F2B46.toInt())

    private fun r(v: Double): Int = v.roundToInt()

    /** A sensible number of columns for [n] photos: as square a grid as possible. */
    fun gridCols(n: Int): Int = max(1, ceil(sqrt(n.toDouble())).toInt())

    /** Equal cells in rows. An unfinished last row is centred. */
    fun grid(n: Int, cols: Int, aspect: Double, outW: Double, gap: Double, pad: Double): CollageLayout {
        val c = max(1, min(cols, if (n == 0) 1 else n))
        val rows = max(1, ceil(n.toDouble() / c).toInt())
        val cw = (outW - 2 * pad - (c - 1) * gap) / c
        val ch = cw / aspect
        val cells = (0 until n).map { i ->
            val row = i / c
            val inRow = if (row == rows - 1) n - row * c else c
            val shift = (c - inRow) * (cw + gap) / 2
            CollageCell(r(pad + shift + (i % c) * (cw + gap)), r(pad + row * (ch + gap)), r(cw), r(ch))
        }
        return CollageLayout(r(outW), r(2 * pad + rows * ch + (rows - 1) * gap), cells)
    }

    /** One row, every photo the same height, whole photos. [aspects] are width over height. */
    fun row(aspects: List<Double>, outH: Double, gap: Double, pad: Double): CollageLayout {
        val h = outH - 2 * pad
        var x = pad
        val cells = aspects.map { a -> CollageCell(r(x), r(pad), r(a * h), r(h)).also { x += a * h + gap } }
        return CollageLayout(r(x - gap + pad), r(outH), cells)
    }

    /** One column, every photo the same width, whole photos. */
    fun column(aspects: List<Double>, outW: Double, gap: Double, pad: Double): CollageLayout {
        val w = outW - 2 * pad
        var y = pad
        val cells = aspects.map { a -> CollageCell(r(pad), r(y), r(w), r(w / a)).also { y += w / a + gap } }
        return CollageLayout(r(outW), r(y - gap + pad), cells)
    }

    /** The same layout with every number multiplied by [k]. */
    fun scale(l: CollageLayout, k: Double): CollageLayout = CollageLayout(
        max(1, r(l.w * k)), max(1, r(l.h * k)),
        l.cells.map { CollageCell(r(it.x * k), r(it.y * k), max(1, r(it.w * k)), max(1, r(it.h * k))) },
    )

    /** Shrinks a layout so neither side passes [max] and the whole picture stays under [maxPixels]. */
    fun limit(l: CollageLayout, max: Int = MAX_SIDE, maxPixels: Long = MAX_PIXELS): CollageLayout {
        val k = min(1.0, min(max.toDouble() / max(l.w, l.h), sqrt(maxPixels.toDouble() / (l.w.toDouble() * l.h)) * 0.9999))
        return if (k == 1.0) l else scale(l, k)
    }
}

/** What the Collage sheet shows. [cols] of 0 means automatic. [gapPct] is the space between photos as a share of the width. */
data class CollageForm(
    val kind: CollageKind = CollageKind.GRID,
    val cols: Int = 0,
    val shape: Double = 1.0,
    val fit: FitMode = FitMode.FILL,
    val gapPct: Double = 1.5,
    val background: Int = 0xFFFFFFFF.toInt(),
    val outWidth: Int = 2000,
) {
    val changed: Boolean get() = this != CollageForm()

    /** Where each of the photos with these [aspects] (width over height) goes when the collage is [width] pixels wide. */
    fun layout(aspects: List<Double>, width: Int): CollageLayout {
        val n = aspects.size
        val gap = width * gapPct / 100
        val raw = when (kind) {
            CollageKind.GRID -> Collage.grid(n, if (cols == 0) Collage.gridCols(n) else cols, shape, width.toDouble(), gap, gap)
            CollageKind.COLUMN -> Collage.column(aspects, width.toDouble(), gap, gap)
            CollageKind.ROW -> {
                // Built at a fixed height, then scaled to the width asked for.
                val spacing = 1000 * gapPct / 100 * 1.5
                val l = Collage.row(aspects, 1000.0, spacing, spacing)
                Collage.scale(l, width.toDouble() / l.w)
            }
        }
        return Collage.limit(raw)
    }

    /** How a photo is fitted in its cell: only the Grid can show the whole photo; Row and Column cells already have the photo's shape. */
    fun fitFor(): FitMode = if (kind == CollageKind.GRID) fit else FitMode.FILL
}
