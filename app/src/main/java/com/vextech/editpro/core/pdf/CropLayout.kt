package com.vextech.editpro.core.pdf

/** A box in a page's own (unrotated) space, in points. */
data class PageBox(val x: Double, val y: Double, val width: Double, val height: Double)

/** Margins to trim, in points, measured on the page as it looks on screen. */
data class Margins(val top: Double, val right: Double, val bottom: Double, val left: Double)

enum class Paper(val width: Double, val height: Double) {
    A3(841.89, 1190.55), A4(595.28, 841.89), A5(419.53, 595.28), LETTER(612.0, 792.0), LEGAL(612.0, 1008.0),
}

enum class SheetOrientation { AUTO, PORTRAIT, LANDSCAPE }

enum class CropMode { TRIM, RESIZE }

/** Where a page lands on a sheet: scale and offset of the visible page, in sheet points. Mirrors the site's `fitOnSheet`. */
data class SheetFit(val scale: Double, val offsetX: Double, val offsetY: Double, val width: Double, val height: Double)

/** What the Crop sheet asks the engine to do. [pages] are 0-based; null means every page. */
sealed interface CropOptions {
    val pages: List<Int>?

    data class Trim(val margins: Margins, override val pages: List<Int>?) : CropOptions
    data class Resize(val paper: Paper, val orientation: SheetOrientation, val marginPoints: Double, override val pages: List<Int>?) : CropOptions
}

/** The page's crop box is too small for the margins asked, so nothing would be left. */
class CropTooLargeException(val pageIndex: Int) : IllegalArgumentException("The margins are larger than page ${pageIndex + 1}")

/** Page geometry for trimming and fitting to paper. Mirrors `cropBoxFor`, `fitOnSheet` and `originFor` in the site's `pdf-tools.ts`. */
object CropMath {
    const val POINTS_PER_MM = 72.0 / 25.4
    const val MIN_SIDE = 12.0

    private fun norm(rotation: Int): Int = (((Math.round(rotation / 90.0).toInt() * 90) % 360) + 360) % 360

    /**
     * Turns margins measured on the page as displayed into an unrotated crop box. A page with /Rotate 90 is shown turned
     * clockwise, so what you see as its top edge is the unrotated left edge, and so on.
     */
    fun cropBoxFor(box: PageBox, rotation: Int, m: Margins): PageBox {
        val left: Double; val top: Double; val right: Double; val bottom: Double
        when (norm(rotation)) {
            0 -> { left = m.left; top = m.top; right = m.right; bottom = m.bottom }
            90 -> { left = m.top; top = m.right; right = m.bottom; bottom = m.left }
            180 -> { bottom = m.top; left = m.right; top = m.bottom; right = m.left }
            else -> { right = m.top; bottom = m.right; left = m.bottom; top = m.left }
        }
        return PageBox(box.x + left, box.y + bottom, box.width - left - right, box.height - top - bottom)
    }

    /** True when trimming [m] from [box] leaves a box big enough to see. */
    fun leavesRoom(box: PageBox, rotation: Int, m: Margins): Boolean {
        val b = cropBoxFor(box, rotation, m)
        return b.width >= MIN_SIDE && b.height >= MIN_SIDE
    }

    /** The visible size (after rotation) of a page whose unrotated box is [box]. */
    fun visualSize(box: PageBox, rotation: Int): Pair<Double, Double> =
        if (norm(rotation) % 180 == 90) box.height to box.width else box.width to box.height

    /** Sheet width and height for a page of visual size [vw] x [vh]. [SheetOrientation.AUTO] matches the page's shape. */
    fun sheetSize(paper: Paper, orientation: SheetOrientation, vw: Double, vh: Double): Pair<Double, Double> {
        val landscape = when (orientation) {
            SheetOrientation.AUTO -> vw > vh
            SheetOrientation.LANDSCAPE -> true
            SheetOrientation.PORTRAIT -> false
        }
        return if (landscape) paper.height to paper.width else paper.width to paper.height
    }

    /** Fits the visible page inside the sheet's margin and centres it. */
    fun fitOnSheet(vw: Double, vh: Double, sheetW: Double, sheetH: Double, margin: Double): SheetFit {
        val s = minOf((sheetW - 2 * margin) / vw, (sheetH - 2 * margin) / vh)
        val dw = vw * s
        val dh = vh * s
        return SheetFit(s, (sheetW - dw) / 2.0, (sheetH - dh) / 2.0, dw, dh)
    }

    /** The margin is too large for the paper when almost nothing would be left to draw on. */
    fun marginFitsPaper(paper: Paper, marginPoints: Double): Boolean = marginPoints * 2 < minOf(paper.width, paper.height) - MIN_SIDE
}

enum class CropProblem { MARGIN_NUMBER, PAPER_MARGIN_NUMBER, PAPER_MARGIN_TOO_LARGE, PAGES_NOT_A_RANGE, PAGES_INVALID, PAGES_PAST_END }

sealed interface CropParse {
    data class Ok(val options: CropOptions) : CropParse
    data class Error(val problem: CropProblem, val text: String = "", val total: Int = 0) : CropParse
}

/** What the Crop sheet shows: every box is text, so half-typed numbers stay as they are. Margins are in millimetres. */
data class CropForm(
    val mode: CropMode = CropMode.TRIM,
    val top: String = "0",
    val right: String = "0",
    val bottom: String = "0",
    val left: String = "0",
    val paper: Paper = Paper.A4,
    val orientation: SheetOrientation = SheetOrientation.AUTO,
    val margin: String = "5",
    /** Pages as typed, like `1-3, 5`. Blank means every page. */
    val pages: String = "",
) {
    fun parse(pageCount: Int): CropParse {
        val selection = when (val r = PageRanges.parse(pages, pageCount)) {
            is PageRangeResult.Error -> return CropParse.Error(
                when (r.problem) {
                    PageRangeProblem.PAST_END -> CropProblem.PAGES_PAST_END
                    PageRangeProblem.INVALID_RANGE -> CropProblem.PAGES_INVALID
                    else -> CropProblem.PAGES_NOT_A_RANGE
                },
                r.text, r.total,
            )
            is PageRangeResult.Ok -> if (pages.isBlank()) null else r.pages
        }
        return when (mode) {
            CropMode.TRIM -> {
                val t = mm(top) ?: return CropParse.Error(CropProblem.MARGIN_NUMBER, top)
                val r = mm(right) ?: return CropParse.Error(CropProblem.MARGIN_NUMBER, right)
                val b = mm(bottom) ?: return CropParse.Error(CropProblem.MARGIN_NUMBER, bottom)
                val l = mm(left) ?: return CropParse.Error(CropProblem.MARGIN_NUMBER, left)
                CropParse.Ok(CropOptions.Trim(Margins(t, r, b, l), selection))
            }
            CropMode.RESIZE -> {
                val m = mm(margin) ?: return CropParse.Error(CropProblem.PAPER_MARGIN_NUMBER, margin)
                if (!CropMath.marginFitsPaper(paper, m)) return CropParse.Error(CropProblem.PAPER_MARGIN_TOO_LARGE)
                CropParse.Ok(CropOptions.Resize(paper, orientation, m, selection))
            }
        }
    }

    /** Blank counts as zero; anything that is not a number of zero or more is refused. Result in points. */
    private fun mm(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return 0.0
        val v = t.toDoubleOrNull() ?: return null
        return if (v.isFinite() && v >= 0) v * CropMath.POINTS_PER_MM else null
    }
}
