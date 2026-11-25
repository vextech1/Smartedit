package com.vextech.editpro.core.processing

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.vextech.editpro.core.image.Mark
import com.vextech.editpro.core.image.MarkMath
import com.vextech.editpro.core.image.MarkTool
import kotlin.math.max
import kotlin.math.min

/**
 * Draws Draw & Text marks on a canvas of [width] x [height] pixels. Marks are fractions of the picture and sizes follow its shorter side,
 * so the same marks look the same on the small preview, the on-screen layer and the full-size save.
 */
object MarkPainter {
    fun draw(canvas: Canvas, width: Float, height: Float, marks: List<Mark>) {
        val minSide = min(width, height).toInt().coerceAtLeast(1)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        for (m in marks) {
            paint.color = m.color
            if (m.tool == MarkTool.TEXT) { drawText(canvas, paint, m, width, height, minSide); continue }
            val size = MarkMath.strokePx(minSide, m.level).toFloat()
            paint.strokeWidth = size
            val p = m.points
            val x1 = p.first().x * width; val y1 = p.first().y * height
            val x2 = p.last().x * width; val y2 = p.last().y * height
            when (m.tool) {
                MarkTool.PEN -> if (p.size == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(x1, y1, size / 2, paint)
                } else {
                    paint.style = Paint.Style.STROKE
                    val path = Path().apply { moveTo(x1, y1); p.drop(1).forEach { lineTo(it.x * width, it.y * height) } }
                    canvas.drawPath(path, paint)
                }
                MarkTool.BOX -> { paint.style = Paint.Style.STROKE; canvas.drawRect(RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)), paint) }
                MarkTool.OVAL -> { paint.style = Paint.Style.STROKE; canvas.drawOval(RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)), paint) }
                MarkTool.ARROW -> {
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(x1, y1, x2, y2, paint)
                    val h = MarkMath.arrowHead(x1.toDouble(), y1.toDouble(), x2.toDouble(), y2.toDouble(), size.toDouble())
                    paint.style = Paint.Style.FILL
                    val head = Path().apply { moveTo(x2, y2); lineTo(h.x1.toFloat(), h.y1.toFloat()); lineTo(h.x2.toFloat(), h.y2.toFloat()); close() }
                    canvas.drawPath(head, paint)
                }
                MarkTool.TEXT -> Unit
            }
        }
    }

    private fun drawText(canvas: Canvas, paint: Paint, m: Mark, width: Float, height: Float, minSide: Int) {
        val px = MarkMath.fontPx(minSide, m.level).toFloat()
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = px
        val lines = m.text.split('\n')
        val lineHeight = px * 1.2f
        val x = m.points.first().x * width
        val y = m.points.first().y * height
        if (m.textBox) {
            val w = lines.maxOf { paint.measureText(it) }
            val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt() }
            canvas.drawRect(x - px * 0.25f, y - px * 0.1f, x + w + px * 0.25f, y + lines.size * lineHeight + px * 0.1f, back)
        }
        // The text starts at the top of its line, as on the site.
        val top = -paint.fontMetrics.ascent
        lines.forEachIndexed { i, line -> canvas.drawText(line, x, y + top + i * lineHeight, paint) }
        paint.typeface = Typeface.DEFAULT
    }
}
