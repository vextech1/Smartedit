package com.vextech.editpro.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.vextech.editpro.core.image.FitMode
import com.vextech.editpro.core.image.FrameSpec
import com.vextech.editpro.core.image.Frames
import com.vextech.editpro.core.image.PhoneBackground
import com.vextech.editpro.core.image.PhoneColor
import com.vextech.editpro.core.image.PhoneFrameSpec
import com.vextech.editpro.core.image.PhoneStyle
import com.vextech.editpro.core.image.ResizeFit
import com.vextech.editpro.core.image.WallBackdrop
import com.vextech.editpro.core.image.WallFrameSpec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws wall frames and generic phone frames round a picture. The layout comes from [Frames]; this port follows the site's `frames.ts`. */
object FramePainter {
    fun draw(source: Bitmap, spec: FrameSpec): Bitmap = when (spec) {
        is WallFrameSpec -> wall(source, spec)
        is PhoneFrameSpec -> phone(source, spec)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color; strokeWidth = width }

    private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): Path {
        val k = max(0f, min(r, min(w / 2, h / 2)))
        return Path().apply { addRoundRect(RectF(x, y, x + w, y + h), k, k, Path.Direction.CW) }
    }

    /** Draws [src] into the box, filling or fitting it, on [bg]. */
    private fun cover(canvas: Canvas, src: Bitmap, x: Float, y: Float, w: Float, h: Float, mode: FitMode, bg: Int) {
        canvas.drawRect(x, y, x + w, y + h, fill(bg))
        val r = ResizeFit.fitRect(src.width, src.height, max(1, w.roundToInt()), max(1, h.roundToInt()), mode)
        val from = Rect(
            r.sx.roundToInt().coerceIn(0, src.width - 1), r.sy.roundToInt().coerceIn(0, src.height - 1),
            (r.sx + r.sw).roundToInt().coerceIn(1, src.width), (r.sy + r.sh).roundToInt().coerceIn(1, src.height),
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, from, RectF(x + r.dx.toFloat(), y + r.dy.toFloat(), x + (r.dx + r.dw).toFloat(), y + (r.dy + r.dh).toFloat()), paint)
    }

    // ---------- wall frames ----------

    private fun wall(src: Bitmap, o: WallFrameSpec): Bitmap {
        val l = Frames.wallLayout(src.width, src.height, o.size, o.framePct, o.matPct, o.wall, Frames.wallLong(src.width, src.height))
        val cw = l.w + 2 * l.pad; val ch = l.h + 2 * l.pad
        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val hasWall = o.wall != WallBackdrop.NONE
        if (hasWall) {
            c.drawRect(0f, 0f, cw.toFloat(), ch.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, ch.toFloat(), o.wall.top, o.wall.bottom, Shader.TileMode.CLAMP) })
        }
        val x = l.pad.toFloat(); val y = l.pad.toFloat(); val w = l.w.toFloat(); val h = l.h.toFloat(); val fw = l.fw.toFloat(); val mw = l.mw
        val base = o.style.base
        if (hasWall) { // soft shadow the frame casts on the wall
            val p = fill(base).apply { setShadowLayer(max(w, h) * 0.035f, 0f, max(w, h) * 0.012f, 0x61000000) }
            c.drawRect(x, y, x + w, y + h, p)
        }
        // moulding: four mitred strips, lit from the top left
        val strips = listOf(
            0.18 to floatArrayOf(0f, 0f, w, 0f, w - fw, fw, fw, fw),
            0.06 to floatArrayOf(0f, 0f, fw, fw, fw, h - fw, 0f, h),
            -0.16 to floatArrayOf(w, 0f, w, h, w - fw, h - fw, w - fw, fw),
            -0.3 to floatArrayOf(0f, h, fw, h - fw, w - fw, h - fw, w, h),
        )
        for ((amt, pts) in strips) {
            val path = Path().apply {
                moveTo(x + pts[0], y + pts[1])
                for (i in 2 until pts.size step 2) lineTo(x + pts[i], y + pts[i + 1])
                close()
            }
            c.drawPath(path, fill(Frames.shade(base, amt)))
        }
        val ring = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(x, y, x + w, y + h, Path.Direction.CW)
            addRect(x + fw, y + fw, x + w - fw, y + h - fw, Path.Direction.CW)
        }
        if (o.style == com.vextech.editpro.core.image.WallStyle.GOLD) { // brushed-metal sheen
            c.save(); c.clipPath(ring)
            val g = Paint().apply { shader = LinearGradient(x, y, x + w, y + h, intArrayOf(0x47FFFFFF, 0x1F000000, 0x33FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) }
            c.drawRect(x, y, x + w, y + h, g)
            c.restore()
        }
        if (o.style.grain) { // wood grain: faint streaks along the moulding
            c.save(); c.clipPath(ring)
            val n = (max(w, h) / 3).toInt()
            for (i in 0 until n) {
                val t = ((i * 37) % 100) / 100f
                val alpha = ((0.035 + (i % 5) * 0.008) * 255).roundToInt()
                val streak = stroke(if (i % 2 == 1) Color.argb(alpha, 0, 0, 0) else Color.argb(alpha, 255, 255, 255), (1 + (i % 3)).toFloat())
                c.drawLine(x, y + t * h, x + w, y + t * h + (i % 7) - 3, streak)
                c.drawLine(x + t * w, y, x + t * w + (i % 7) - 3, y + h, streak)
            }
            c.restore()
        }
        c.drawRect(x + fw, y + fw, x + w - fw, y + h - fw, stroke(0x59000000, max(1f, fw * 0.04f))) // thin inner lip

        val px = l.px.toFloat(); val py = l.py.toFloat(); val pw = l.pw.toFloat(); val ph = l.ph.toFloat()
        if (mw > 0) c.drawRect(x + fw, y + fw, x + w - fw, y + h - fw, fill(o.matColor))
        cover(c, src, px, py, pw, ph, o.fit, o.matColor)
        if (mw > 0) c.drawRect(px, py, px + pw, py + ph, stroke(0x40000000, max(1f, min(mw * 0.05f, 3f)))) // bevel on the mat's inner edge
        if (o.innerShadow) {
            c.save(); c.clipRect(px, py, px + pw, py + ph)
            val d = max(4f, min(pw, ph) * 0.035f)
            val edges = listOf(
                floatArrayOf(px, 0f, px + d, 0f), floatArrayOf(0f, py, 0f, py + d),
                floatArrayOf(px + pw, 0f, px + pw - d, 0f), floatArrayOf(0f, py + ph, 0f, py + ph - d),
            )
            for (e in edges) {
                val g = Paint().apply { shader = LinearGradient(e[0], e[1], e[2], e[3], 0x47000000, 0x00000000, Shader.TileMode.CLAMP) }
                c.drawRect(px, py, px + pw, py + ph, g)
            }
            c.restore()
        }
        return out
    }

    // ---------- phone frames ----------

    private fun phone(src: Bitmap, o: PhoneFrameSpec): Bitmap {
        val withBg = o.background != PhoneBackground.NONE
        val l = Frames.phoneLayout(o.style, Frames.phoneScreenWidth(src.width), withBg)
        val out = Bitmap.createBitmap(l.w, l.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        when (o.background) {
            PhoneBackground.SOLID -> c.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), fill(o.c1))
            PhoneBackground.GRADIENT -> c.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, l.w.toFloat(), l.h.toFloat(), o.c1, o.c2, Shader.TileMode.CLAMP) })
            PhoneBackground.NONE -> Unit
        }
        val body = o.color.body
        val u = l.bodyW / 100f // 1% of the phone's width
        val bx = l.bx.toFloat(); val by = l.by.toFloat(); val bw = l.bodyW.toFloat(); val bh = l.bodyH.toFloat()

        // side buttons sit just outside the body
        val btnPaint = fill(Frames.shade(body, -0.25))
        fun btn(x: Float, yy: Float, h: Float) = c.drawPath(roundRect(x, by + yy * u, u * 0.9f, h * u, u * 0.4f), btnPaint)
        btn(bx - u * 0.7f, 28f, 6f); btn(bx - u * 0.7f, 40f, 10f); btn(bx - u * 0.7f, 53f, 10f); btn(bx + bw - u * 0.2f, 44f, 16f)

        val bodyPath = roundRect(bx, by, bw, bh, l.r.toFloat())
        val bodyPaint = fill(body)
        if (o.shadow) { bodyPaint.setShadowLayer(u * 5, 0f, u * 2, 0x66000000) }
        c.drawPath(bodyPath, bodyPaint)
        c.drawPath(bodyPath, stroke(Frames.shade(body, if (o.color == PhoneColor.SILVER) -0.2 else 0.25), max(1f, u * 0.35f)))

        // screen with the picture
        val sx = l.sx.toFloat(); val sy = l.sy.toFloat(); val sw = l.sw.toFloat(); val sh = l.sh.toFloat()
        c.save()
        c.clipPath(roundRect(sx, sy, sw, sh, l.sr.toFloat()))
        cover(c, src, sx, sy, sw, sh, o.fit, Color.BLACK)
        c.drawRect(sx, sy, sx + sw, sy + sh, Paint().apply { shader = LinearGradient(sx, sy, sx + sw, sy + sh, intArrayOf(0x1AFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.35f), Shader.TileMode.CLAMP) })
        c.restore()

        // camera cut-out and details
        val black = fill(Color.BLACK)
        val cx = sx + sw / 2
        when (o.style) {
            PhoneStyle.ISLAND -> c.drawPath(roundRect(cx - sw * 0.14f, sy + sw * 0.03f, sw * 0.28f, sw * 0.08f, sw * 0.04f), black)
            PhoneStyle.NOTCH -> {
                val bottom = sy + sw * 0.08f
                val path = Path().apply {
                    moveTo(cx - sw * 0.22f, sy); lineTo(cx + sw * 0.22f, sy)
                    lineTo(cx + sw * 0.22f, sy + sw * 0.03f)
                    quadTo(cx + sw * 0.22f, bottom, cx + sw * 0.17f, bottom)
                    lineTo(cx - sw * 0.17f, bottom)
                    quadTo(cx - sw * 0.22f, bottom, cx - sw * 0.22f, sy + sw * 0.03f)
                    close()
                }
                c.drawPath(path, black)
            }
            PhoneStyle.PUNCH -> c.drawCircle(cx, sy + sw * 0.055f, sw * 0.02f, black)
            PhoneStyle.CLASSIC -> {
                c.drawPath(roundRect(cx - sw * 0.1f, by + (sy - by) * 0.55f, sw * 0.2f, sw * 0.014f, sw * 0.007f), fill(Frames.shade(body, 0.2)))
                c.drawCircle(cx, sy + sh + (by + bh - sy - sh) / 2, sw * 0.05f, stroke(Frames.shade(body, 0.3), max(1f, u * 0.5f)))
            }
        }
        return out
    }
}
