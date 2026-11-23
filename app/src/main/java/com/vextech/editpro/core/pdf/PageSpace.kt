package com.vextech.editpro.core.pdf

/** A point in a page's own (unrotated) space, as offsets from the lower-left corner of its visible box. */
data class PagePoint(val u: Double, val v: Double)

/**
 * Page geometry shared by the tools that draw on top of a page (watermark, page numbers).
 * Placement is worked out as the page looks on screen (X from the left, Y up from the bottom, in points) and then
 * mapped back through the page's rotation, so what is drawn comes out upright. Mirrors `screenToPage` in the site's `pdf-stamp.ts`.
 */
object PageSpace {
    /** Width and height of the page as it looks on screen: the box turned sideways when the page is rotated 90 or 270 degrees. */
    fun visualSize(boxWidth: Double, boxHeight: Double, rotation: Int): Pair<Double, Double> =
        if (normalize(rotation) % 180 == 90) boxHeight to boxWidth else boxWidth to boxHeight

    fun screenToPage(boxWidth: Double, boxHeight: Double, rotation: Int, x: Double, y: Double): PagePoint =
        when (normalize(rotation)) {
            0 -> PagePoint(x, y)
            90 -> PagePoint(boxWidth - y, x)
            180 -> PagePoint(boxWidth - x, boxHeight - y)
            else -> PagePoint(y, boxHeight - x)
        }

    private fun normalize(rotation: Int): Int = (((Math.round(rotation / 90.0).toInt() * 90) % 360) + 360) % 360
}

/** The characters the PDF standard fonts can print (Windows-1252): Latin-1 plus a few extras in the 0x80 to 0x9F block. */
object WinAnsi {
    private const val EXTRA = "\u20AC\u201A\u0192\u201E\u2026\u2020\u2021\u02C6\u2030\u0160\u2039\u0152\u017D\u2018\u2019\u201C\u201D\u2022\u2013\u2014\u02DC\u2122\u0161\u203A\u0153\u017E\u0178"

    fun isSupported(c: Char): Boolean = c.code in 0x20..0x7E || c.code in 0xA0..0xFF || EXTRA.indexOf(c) >= 0

    /** The first character that cannot be printed, or null when all can. */
    fun firstUnsupported(text: String): Char? = text.firstOrNull { !isSupported(it) }
}
