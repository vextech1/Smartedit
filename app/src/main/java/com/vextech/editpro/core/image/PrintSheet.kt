package com.vextech.editpro.core.image

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A paper size for the photo sheet, in millimetres. */
data class PaperSheet(val id: String, val wMm: Double, val hMm: Double)

/** Where the photos go on a page: [cols] x [rows] cells starting at ([x0], [y0]) from the top left, all in millimetres. */
data class SheetLayout(val cols: Int, val rows: Int, val pageW: Double, val pageH: Double, val x0: Double, val y0: Double, val landscape: Boolean) {
    val count: Int get() = cols * rows
}

/** Tiles a photo onto a print sheet, ported from the site's `print-sheet.ts`. */
object PrintSheets {
    const val GAP_MM = 3.0
    const val MARGIN_MM = 5.0
    private const val IN = SizeMath.MM_PER_IN

    val papers: List<PaperSheet> = listOf(
        PaperSheet("4x6", 4 * IN, 6 * IN), PaperSheet("5x7", 5 * IN, 7 * IN), PaperSheet("a4", 210.0, 297.0), PaperSheet("letter", 8.5 * IN, 11 * IN),
    )

    /** Copies offered besides "fill the page" (0). */
    val copyChoices: List<Int> = listOf(0, 1, 2, 4, 6, 8, 9, 12)

    private fun fit(avail: Double, cell: Double, gap: Double): Int = if (cell > avail || cell <= 0) 0 else floor((avail + gap) / (cell + gap)).toInt()

    /** How many cells of [cellW] x [cellH] fit on a page, centred, with [gap] between them and [margin] round the edge. */
    fun layout(pageW: Double, pageH: Double, cellW: Double, cellH: Double, gap: Double = GAP_MM, margin: Double = MARGIN_MM): SheetLayout {
        val cols = fit(pageW - 2 * margin, cellW, gap)
        val rows = fit(pageH - 2 * margin, cellH, gap)
        val usedW = cols * cellW + max(0, cols - 1) * gap
        val usedH = rows * cellH + max(0, rows - 1) * gap
        return SheetLayout(cols, rows, pageW, pageH, (pageW - usedW) / 2, (pageH - usedH) / 2, pageW > pageH)
    }

    /** The better of portrait and sideways for this paper. */
    fun best(paper: PaperSheet, cellW: Double, cellH: Double, gap: Double = GAP_MM, margin: Double = MARGIN_MM): SheetLayout {
        val portrait = layout(paper.wMm, paper.hMm, cellW, cellH, gap, margin)
        val sideways = layout(paper.hMm, paper.wMm, cellW, cellH, gap, margin)
        return if (sideways.count > portrait.count) sideways else portrait
    }

    /** How many photos to print: 0 copies means fill the page; never more than fit. */
    fun photosOn(layout: SheetLayout, copies: Int): Int = if (copies > 0) min(copies, layout.count) else layout.count

    /** Photo sizes the sheet offers: the ID, passport and print sizes that have a real-world size. */
    val sizes: List<SizePreset> = SizePresets.all.filter { (it.group == PresetGroup.ID || it.group == PresetGroup.PRINT) && it.unit != SizeUnit.PX }

    fun sizeMm(p: SizePreset): Pair<Double, Double> = SizeMath.toMm(p.w, p.unit, SizeMath.DEFAULT_DPI) to SizeMath.toMm(p.h, p.unit, SizeMath.DEFAULT_DPI)
}

/** What the Photo sheet shows. [sizeId] empty means the typed [widthMm] x [heightMm]. */
data class PrintSheetForm(
    val paperId: String = "4x6",
    val sizeId: String = "us-passport",
    val widthMm: String = "35",
    val heightMm: String = "45",
    val copies: Int = 0,
    val guides: Boolean = true,
) {
    val paper: PaperSheet get() = PrintSheets.papers.firstOrNull { it.id == paperId } ?: PrintSheets.papers.first()

    private fun number(t: String): Double = t.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 0.0

    /** The size of one photo in millimetres, or null when the typed size is not a number. */
    fun cellMm(): Pair<Double, Double>? {
        PrintSheets.sizes.firstOrNull { it.id == sizeId }?.let { return PrintSheets.sizeMm(it) }
        val w = number(widthMm); val h = number(heightMm)
        return if (w > 0 && h > 0) w to h else null
    }

    /** The page layout for this paper and size, or null when no size is set. A layout with a count of 0 means the photo is too big. */
    fun layout(): SheetLayout? = cellMm()?.let { (w, h) -> PrintSheets.best(paper, w, h) }

    fun photos(): Int = layout()?.let { PrintSheets.photosOn(it, copies) } ?: 0

    /** True when a sheet can be made. */
    val ready: Boolean get() = photos() > 0
}
