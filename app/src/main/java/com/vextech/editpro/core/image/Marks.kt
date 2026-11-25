package com.vextech.editpro.core.image

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A point on the picture as fractions of its width and height (0 to 1), so a mark lands in the same place at any size. */
data class DrawPoint(val x: Float, val y: Float)

enum class MarkTool { PEN, ARROW, BOX, OVAL, TEXT }

/**
 * One mark drawn on the picture. [points] is the pen path, the two corners of a box, oval or arrow (start then end), or the start of the text.
 * [level] is the thickness or text size on a scale of 1 to 10.
 */
data class Mark(
    val tool: MarkTool,
    val points: List<DrawPoint>,
    val color: Int,
    val level: Int,
    val text: String = "",
    val textBox: Boolean = false,
)

/** An arrow head corner, in pixels. */
data class ArrowHead(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

/** Sizes and shapes for Draw & Text, ported from the site's `annotate.ts`. */
object MarkMath {
    val COLORS: List<Int> = listOf(0xFFE11D48.toInt(), 0xFFF59E0B.toInt(), 0xFF16A34A.toInt(), 0xFF2563EB.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 10

    /** A drag shorter than this share of the picture is a tap, not a shape. */
    const val TINY = 0.01

    /** Line thickness in pixels on a picture whose shorter side is [minSide]. */
    fun strokePx(minSide: Int, level: Int): Int = max(2, (minSide * level.coerceIn(MIN_LEVEL, MAX_LEVEL) / 300.0).roundToInt())

    /** Text size in pixels on a picture whose shorter side is [minSide]. */
    fun fontPx(minSide: Int, level: Int): Int = max(10, (minSide * level.coerceIn(MIN_LEVEL, MAX_LEVEL) * 1.2 / 100.0).roundToInt())

    /** The two back corners of an arrow head for an arrow ending at (x2, y2), all in pixels. */
    fun arrowHead(x1: Double, y1: Double, x2: Double, y2: Double, size: Double): ArrowHead {
        val a = atan2(y2 - y1, x2 - x1)
        val len = size * 4
        val spread = PI / 7
        return ArrowHead(x2 - len * cos(a - spread), y2 - len * sin(a - spread), x2 - len * cos(a + spread), y2 - len * sin(a + spread))
    }
}

/** What Draw & Text shows and has drawn so far. All changes return a new form. */
data class MarkForm(
    val tool: MarkTool = MarkTool.PEN,
    val color: Int = MarkMath.COLORS.first(),
    val level: Int = 4,
    val fontLevel: Int = 5,
    val text: String = "Text",
    val textBox: Boolean = false,
    val marks: List<Mark> = emptyList(),
    val draft: Mark? = null,
    /** True after a tap with the Text tool while the text box is empty. */
    val needText: Boolean = false,
) {
    val canApply: Boolean get() = marks.isNotEmpty()

    /** Every mark including the one being drawn, for showing. */
    val shown: List<Mark> get() = draft?.let { marks + it } ?: marks

    fun withTool(t: MarkTool) = copy(tool = t, draft = null, needText = false)

    /** A finger goes down at [p]: starts a pen line, box, oval or arrow. Text places on tap instead. */
    fun down(p: DrawPoint): MarkForm = when (tool) {
        MarkTool.TEXT -> this
        MarkTool.PEN -> copy(draft = Mark(MarkTool.PEN, listOf(p), color, level), needText = false)
        else -> copy(draft = Mark(tool, listOf(p, p), color, level), needText = false)
    }

    fun move(p: DrawPoint): MarkForm {
        val d = draft ?: return this
        return copy(draft = if (d.tool == MarkTool.PEN) d.copy(points = d.points + p) else d.copy(points = listOf(d.points.first(), p)))
    }

    /** The finger lifts: keeps the mark, except a shape too small to mean anything. */
    fun up(): MarkForm {
        val d = draft ?: return this
        val tiny = d.tool != MarkTool.PEN && hypot((d.points[1].x - d.points[0].x).toDouble(), (d.points[1].y - d.points[0].y).toDouble()) < MarkMath.TINY
        return copy(draft = null, marks = if (tiny) marks else marks + d)
    }

    /** A tap with the Text tool puts the text at [p]. */
    fun tap(p: DrawPoint): MarkForm {
        if (tool != MarkTool.TEXT) return this
        if (text.isBlank()) return copy(needText = true)
        return copy(marks = marks + Mark(MarkTool.TEXT, listOf(p), color, fontLevel, text, textBox), needText = false)
    }

    fun undoLast(): MarkForm = copy(marks = marks.dropLast(1), draft = null)

    fun cleared(): MarkForm = copy(marks = emptyList(), draft = null, needText = false)
}

/** Keeps a point inside the picture. */
fun DrawPoint.clamped(): DrawPoint = DrawPoint(min(1f, max(0f, x)), min(1f, max(0f, y)))
