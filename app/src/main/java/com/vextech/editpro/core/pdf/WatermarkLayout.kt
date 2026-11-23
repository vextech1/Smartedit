package com.vextech.editpro.core.pdf

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Where a mark goes: one of nine spots, or repeated over the page. Same spots as the site (`watermark.ts`). */
enum class WatermarkPos(val row: Char, val col: Char) {
    TL('t', 'l'), TC('t', 'c'), TR('t', 'r'),
    ML('m', 'l'), MC('m', 'c'), MR('m', 'r'),
    BL('b', 'l'), BC('b', 'c'), BR('b', 'r'),
    TILE('-', '-'),
}

enum class WatermarkKind { TEXT, LOGO }

/**
 * What the Watermark sheet lets a person set. Same controls as the site's Photo Studio watermark.
 * [angle] is counter-clockwise, in degrees. Percentages are whole numbers.
 */
data class WatermarkDraft(
    val kind: WatermarkKind = WatermarkKind.TEXT,
    val text: String = "DRAFT",
    val colorHex: String = "#cc0000",
    val pos: WatermarkPos = WatermarkPos.MC,
    val sizePct: Int = 60,
    val opacityPct: Int = 30,
    val angle: Int = 45,
    val marginPct: Int = 3,
    val gapPct: Int = 60,
)

enum class WatermarkProblem { EMPTY_TEXT, UNSUPPORTED_CHAR, BAD_COLOR, NO_LOGO, OPACITY, SIZE, ANGLE, MARGIN, GAP }

data class MarkPoint(val x: Double, val y: Double)

/** Raised when a repeat would need more marks on one page than [WatermarkLayout.MAX_TILES_PER_PAGE]. */
class TooManyMarksException : IllegalArgumentException("The watermark is too small to repeat across the page. Make it larger.")

/** Watermark placement maths: no Android, no PDF library. Mirrors the site's `pdf-watermark.ts` and `watermark.ts`. */
object WatermarkLayout {
    const val MAX_TILES_PER_PAGE = 600
    /** A cheap upper bound on the grid before off-page marks are dropped, so a tiny mark cannot build millions of points. */
    private const val MAX_GRID_POINTS = MAX_TILES_PER_PAGE * 40

    val COLORS = listOf("#cc0000", "#000000", "#6b7280", "#2563eb", "#f59e0b")

    /** Null when the draft can be applied. [hasLogo] says whether a logo picture was chosen. */
    fun validate(d: WatermarkDraft, hasLogo: Boolean): WatermarkProblem? {
        if (d.kind == WatermarkKind.TEXT) {
            val t = d.text.trim()
            if (t.isEmpty()) return WatermarkProblem.EMPTY_TEXT
            if (WinAnsi.firstUnsupported(t) != null) return WatermarkProblem.UNSUPPORTED_CHAR
            if (parseHex(d.colorHex) == null) return WatermarkProblem.BAD_COLOR
        } else if (!hasLogo) return WatermarkProblem.NO_LOGO
        if (d.opacityPct !in 5..100) return WatermarkProblem.OPACITY
        if (d.sizePct !in 5..100) return WatermarkProblem.SIZE
        if (d.angle !in -360..360) return WatermarkProblem.ANGLE
        if (d.marginPct !in 0..50) return WatermarkProblem.MARGIN
        if (d.gapPct !in 0..1000) return WatermarkProblem.GAP
        return null
    }

    /** "#cc0000" (the # is optional) as red, green, blue in 0..1, or null when it is not six hex digits. */
    fun parseHex(hex: String): Triple<Float, Float, Float>? {
        val m = Regex("^#?([0-9a-fA-F]{6})$").matchEntire(hex.trim()) ?: return null
        val n = m.groupValues[1].toInt(16)
        return Triple((n shr 16) / 255f, ((n shr 8) and 255) / 255f, (n and 255) / 255f)
    }

    /** Bounding size of a w x h box turned by [deg]. */
    fun footprint(w: Double, h: Double, deg: Double): Pair<Double, Double> {
        val t = Math.toRadians(deg)
        val c = abs(cos(t)); val s = abs(sin(t))
        return (w * c + h * s) to (w * s + h * c)
    }

    /** Lower-left corner (screen coordinates, Y up) of a w x h box centred on (cx, cy) and turned by [deg] about that centre. */
    fun cornerFor(cx: Double, cy: Double, w: Double, h: Double, deg: Double): MarkPoint {
        val t = Math.toRadians(deg)
        val c = cos(t); val s = sin(t)
        return MarkPoint(cx - (w / 2) * c + (h / 2) * s, cy - (w / 2) * s - (h / 2) * c)
    }

    /** Top-left corner (Y down) for a single mark, [marginPct] % of the page's short side in from the edge. */
    fun placeMark(areaW: Double, areaH: Double, w: Double, h: Double, pos: WatermarkPos, marginPct: Double): MarkPoint {
        require(pos != WatermarkPos.TILE)
        val m = min(areaW, areaH) * max(0.0, marginPct) / 100.0
        val x = when (pos.col) { 'l' -> m; 'r' -> areaW - w - m; else -> (areaW - w) / 2 }
        val y = when (pos.row) { 't' -> m; 'b' -> areaH - h - m; else -> (areaH - h) / 2 }
        return MarkPoint(x, y)
    }

    /** Top-left corners of marks repeated over an area, every other row shifted half a step. */
    fun tilePoints(areaW: Double, areaH: Double, w: Double, h: Double, gapPct: Double): List<MarkPoint> {
        require(w > 0 && h > 0)
        val grow = 1 + max(0.0, gapPct) / 100.0
        val sx = w * grow; val sy = h * grow
        val rows = kotlin.math.ceil(areaH / sy).toLong() + 1
        val cols = kotlin.math.ceil(areaW / sx).toLong() + 2
        if (rows * cols > MAX_GRID_POINTS) throw TooManyMarksException()
        val out = ArrayList<MarkPoint>()
        var row = 0
        var y = 0.0
        while (y < areaH) {
            var x = if (row % 2 != 0) -sx / 2 else 0.0
            while (x < areaW) {
                if (x + w > 0) out += MarkPoint(x, y)
                x += sx
            }
            row++
            y += sy
        }
        return out
    }

    /**
     * Centres (X from the left, Y up from the bottom) for the marks on a page that looks [vw] x [vh] on screen: one mark in a
     * spot, [marginPct] % of the short side in from the edge, or a staggered repeat turned about the page centre.
     * [angle] is counter-clockwise.
     */
    fun markCentres(vw: Double, vh: Double, w: Double, h: Double, angle: Double, pos: WatermarkPos, marginPct: Double, gapPct: Double): List<MarkPoint> {
        if (pos != WatermarkPos.TILE) {
            val p = placeMark(vw, vh, w, h, pos, marginPct)
            return listOf(MarkPoint(p.x + w / 2, vh - (p.y + h / 2)))
        }
        val d = hypot(vw, vh)
        val t = Math.toRadians(-angle)
        val c = cos(t); val s = sin(t)
        val (fw, fh) = footprint(w, h, angle)
        val out = ArrayList<MarkPoint>()
        for (q in tilePoints(d, d, w, h, gapPct)) {
            val dx = q.x + w / 2 - d / 2
            val dy = q.y + h / 2 - d / 2
            val x = vw / 2 + dx * c - dy * s
            val y = vh / 2 + dx * s + dy * c
            if (x < -fw / 2 || x > vw + fw / 2 || y < -fh / 2 || y > vh + fh / 2) continue   // wholly off the page
            out += MarkPoint(x, vh - y)
        }
        if (out.size > MAX_TILES_PER_PAGE) throw TooManyMarksException()
        return out
    }

    /** One mark's size on a page, in points: text sized to [sizePct] of the page width, or a picture of the given aspect. Never taller than 90 % of the page. */
    fun markSize(vw: Double, vh: Double, aspect: Double, sizePct: Int): Pair<Double, Double> {
        var w = vw * sizePct / 100.0
        var h = w / aspect
        if (h > vh * 0.9) { h = vh * 0.9; w = h * aspect }
        return w to h
    }
}
