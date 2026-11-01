package com.vextech.smart_edit.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Crop rectangle in normalized (0..1) image coordinates. */
data class CropRect(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    companion object { val Full = CropRect() }
}

private enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, RIGHT, TOP, BOTTOM, MOVE }

/** Where an image with [aspect] (w/h) sits when fitted and centered inside a box of [width] x [height]. */
internal fun fitRect(width: Float, height: Float, aspect: Float): Rect {
    if (width <= 0f || height <= 0f || aspect <= 0f) return Rect(0f, 0f, width, height)
    return if (aspect > width / height) {
        val h = width / aspect
        Rect(0f, (height - h) / 2f, width, (height + h) / 2f)
    } else {
        val w = height * aspect
        Rect((width - w) / 2f, 0f, (width + w) / 2f, height)
    }
}

private fun CropRect.toPixels(fit: Rect) = Rect(
    fit.left + left * fit.width, fit.top + top * fit.height,
    fit.left + right * fit.width, fit.top + bottom * fit.height,
)

private fun pickHandle(p: Offset, r: Rect, slop: Float): Handle? {
    val corners = listOf(
        Handle.TOP_LEFT to r.topLeft, Handle.TOP_RIGHT to r.topRight,
        Handle.BOTTOM_LEFT to r.bottomLeft, Handle.BOTTOM_RIGHT to r.bottomRight,
    )
    corners.minByOrNull { (_, c) -> (p - c).getDistance() }?.let { (h, c) -> if ((p - c).getDistance() <= slop * 1.5f) return h }
    val inX = p.x in (r.left - slop)..(r.right + slop)
    val inY = p.y in (r.top - slop)..(r.bottom + slop)
    return when {
        inY && kotlin.math.abs(p.x - r.left) <= slop -> Handle.LEFT
        inY && kotlin.math.abs(p.x - r.right) <= slop -> Handle.RIGHT
        inX && kotlin.math.abs(p.y - r.top) <= slop -> Handle.TOP
        inX && kotlin.math.abs(p.y - r.bottom) <= slop -> Handle.BOTTOM
        r.contains(p) -> Handle.MOVE
        else -> null
    }
}

private fun CropRect.dragged(h: Handle, dx: Float, dy: Float, min: Float): CropRect = when (h) {
    Handle.TOP_LEFT -> copy(left = (left + dx).coerceIn(0f, right - min), top = (top + dy).coerceIn(0f, bottom - min))
    Handle.TOP_RIGHT -> copy(right = (right + dx).coerceIn(left + min, 1f), top = (top + dy).coerceIn(0f, bottom - min))
    Handle.BOTTOM_LEFT -> copy(left = (left + dx).coerceIn(0f, right - min), bottom = (bottom + dy).coerceIn(top + min, 1f))
    Handle.BOTTOM_RIGHT -> copy(right = (right + dx).coerceIn(left + min, 1f), bottom = (bottom + dy).coerceIn(top + min, 1f))
    Handle.LEFT -> copy(left = (left + dx).coerceIn(0f, right - min))
    Handle.RIGHT -> copy(right = (right + dx).coerceIn(left + min, 1f))
    Handle.TOP -> copy(top = (top + dy).coerceIn(0f, bottom - min))
    Handle.BOTTOM -> copy(bottom = (bottom + dy).coerceIn(top + min, 1f))
    Handle.MOVE -> {
        val w = right - left; val h2 = bottom - top
        val l = (left + dx).coerceIn(0f, 1f - w); val t = (top + dy).coerceIn(0f, 1f - h2)
        CropRect(l, t, l + w, t + h2)
    }
}

/** Largest rectangle with normalized width/height [normRatio] that fits inside this one, kept centered. */
fun CropRect.withAspect(normRatio: Float): CropRect {
    val w = right - left; val h = bottom - top
    val cx = (left + right) / 2f; val cy = (top + bottom) / 2f
    val nw = if (w / h > normRatio) h * normRatio else w
    val nh = nw / normRatio
    return CropRect(cx - nw / 2f, cy - nh / 2f, cx + nw / 2f, cy + nh / 2f)
}

/** Resizing while keeping normalized width/height equal to [r]. Corners anchor the opposite corner; edges resize about the center. */
private fun CropRect.draggedLocked(handle: Handle, dx: Float, dy: Float, r: Float, min: Float): CropRect {
    val loW = maxOf(min, min * r)
    val loH = maxOf(min, min / r)
    return when (handle) {
        Handle.TOP_LEFT, Handle.TOP_RIGHT, Handle.BOTTOM_LEFT, Handle.BOTTOM_RIGHT -> {
            val leftSide = handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_LEFT
            val topSide = handle == Handle.TOP_LEFT || handle == Handle.TOP_RIGHT
            val ax = if (leftSide) right else left
            val ay = if (topSide) bottom else top
            val px = (if (leftSide) left else right) + dx
            val py = (if (topSide) top else bottom) + dy
            val maxW = minOf(if (leftSide) ax else 1f - ax, (if (topSide) ay else 1f - ay) * r)
            val w = maxOf(kotlin.math.abs(px - ax), kotlin.math.abs(py - ay) * r).coerceAtLeast(loW).coerceAtMost(maxW)
            val h = w / r
            CropRect(if (leftSide) ax - w else ax, if (topSide) ay - h else ay, if (leftSide) ax else ax + w, if (topSide) ay else ay + h)
        }
        Handle.LEFT, Handle.RIGHT -> {
            val cy = (top + bottom) / 2f
            val fromLeft = handle == Handle.LEFT
            val newW = if (fromLeft) right - (left + dx) else (right + dx) - left
            val maxW = minOf(if (fromLeft) right else 1f - left, r, 2f * minOf(cy, 1f - cy) * r)
            val w = newW.coerceAtLeast(loW).coerceAtMost(maxW)
            val h = w / r
            CropRect(if (fromLeft) right - w else left, cy - h / 2f, if (fromLeft) right else left + w, cy + h / 2f)
        }
        Handle.TOP, Handle.BOTTOM -> {
            val cx = (left + right) / 2f
            val fromTop = handle == Handle.TOP
            val newH = if (fromTop) bottom - (top + dy) else (bottom + dy) - top
            val maxH = minOf(if (fromTop) bottom else 1f - top, 1f / r, 2f * minOf(cx, 1f - cx) / r)
            val h = newH.coerceAtLeast(loH).coerceAtMost(maxH)
            val w = h * r
            CropRect(cx - w / 2f, if (fromTop) bottom - h else top, cx + w / 2f, if (fromTop) bottom else top + h)
        }
        Handle.MOVE -> dragged(handle, dx, dy, min)
    }
}

/**
 * Crop frame: dimmed outside, rule-of-thirds grid, L-shaped corner brackets and a bar on each edge midpoint.
 * Must be laid out at the exact same size as the image it sits on (image drawn with ContentScale.Fit).
 */
@Composable
fun CropOverlay(
    crop: CropRect,
    imageAspect: Float,
    onCropChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
    minSize: Float = 0.1f,
    /** Locked crop width/height in original image pixels, or null for free-form. */
    aspectLock: Float? = null,
) {
    val current by rememberUpdatedState(crop)
    val onChange by rememberUpdatedState(onCropChange)
    val lock by rememberUpdatedState(aspectLock)
    var active by remember { mutableStateOf<Handle?>(null) }
    Canvas(
        modifier.pointerInput(imageAspect) {
            val slop = 28.dp.toPx()
            detectDragGestures(
                onDragStart = { start ->
                    val fit = fitRect(size.width.toFloat(), size.height.toFloat(), imageAspect)
                    active = pickHandle(start, current.toPixels(fit), slop)
                },
                onDragEnd = { active = null },
                onDragCancel = { active = null },
            ) { change, drag ->
                val handle = active ?: return@detectDragGestures
                change.consume()
                val fit = fitRect(size.width.toFloat(), size.height.toFloat(), imageAspect)
                val ndx = drag.x / fit.width; val ndy = drag.y / fit.height
                val locked = lock
                onChange(if (locked != null) current.draggedLocked(handle, ndx, ndy, locked / imageAspect, minSize) else current.dragged(handle, ndx, ndy, minSize))
            }
        },
    ) {
        val fit = fitRect(size.width, size.height, imageAspect)
        val r = crop.toPixels(fit)
        val dim = Color.Black.copy(alpha = 0.55f)
        drawRect(dim, Offset(fit.left, fit.top), Size(fit.width, r.top - fit.top))
        drawRect(dim, Offset(fit.left, r.bottom), Size(fit.width, fit.bottom - r.bottom))
        drawRect(dim, Offset(fit.left, r.top), Size(r.left - fit.left, r.height))
        drawRect(dim, Offset(r.right, r.top), Size(fit.right - r.right, r.height))
        drawRect(Color.White.copy(alpha = 0.7f), r.topLeft, r.size, style = Stroke(1.dp.toPx()))

        val thin = 1.dp.toPx(); val grid = Color.White.copy(alpha = 0.5f)
        for (i in 1..2) {
            val x = r.left + r.width * i / 3f; val y = r.top + r.height * i / 3f
            drawLine(grid, Offset(x, r.top), Offset(x, r.bottom), thin)
            drawLine(grid, Offset(r.left, y), Offset(r.right, y), thin)
        }

        val sw = 3.dp.toPx(); val len = minOf(26.dp.toPx(), r.width / 3f, r.height / 3f); val bar = minOf(24.dp.toPx(), r.width / 4f, r.height / 4f)
        fun seg(a: Offset, b: Offset) = drawLine(Color.White, a, b, sw, StrokeCap.Square)
        seg(r.topLeft, r.topLeft + Offset(len, 0f)); seg(r.topLeft, r.topLeft + Offset(0f, len))
        seg(r.topRight, r.topRight - Offset(len, 0f)); seg(r.topRight, r.topRight + Offset(0f, len))
        seg(r.bottomLeft, r.bottomLeft + Offset(len, 0f)); seg(r.bottomLeft, r.bottomLeft - Offset(0f, len))
        seg(r.bottomRight, r.bottomRight - Offset(len, 0f)); seg(r.bottomRight, r.bottomRight - Offset(0f, len))
        val cx = r.center.x; val cy = r.center.y
        seg(Offset(cx - bar, r.top), Offset(cx + bar, r.top)); seg(Offset(cx - bar, r.bottom), Offset(cx + bar, r.bottom))
        seg(Offset(r.left, cy - bar), Offset(r.left, cy + bar)); seg(Offset(r.right, cy - bar), Offset(r.right, cy + bar))
    }
}

// ───────────────────────── Perspective (free four-corner) crop ─────────────────────────

/** Corners are normalized and ordered top-left, top-right, bottom-right, bottom-left (clockwise on screen). */
internal val FullCorners = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))

private fun isValidQuad(c: List<Offset>): Boolean {
    for (i in 0..3) {
        val a = c[i]; val b = c[(i + 1) % 4]; val d = c[(i + 2) % 4]
        if ((b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x) <= 1e-4f) return false // not convex / wrong winding
    }
    var area = 0f
    for (i in 0..3) { val a = c[i]; val b = c[(i + 1) % 4]; area += a.x * b.y - b.x * a.y }
    return area / 2f > 0.02f
}

private fun pointInQuad(p: Offset, q: List<Offset>): Boolean =
    (0..3).all { i -> val a = q[i]; val b = q[(i + 1) % 4]; (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x) >= 0f }

/**
 * Free-form crop for perspective correction: drag each corner anywhere, or drag inside to move the whole quad.
 * Must be laid out at the same size as the image it sits on (ContentScale.Fit), like [CropOverlay].
 */
@Composable
fun QuadCropOverlay(
    corners: List<Offset>,
    imageAspect: Float,
    onCornersChange: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color.White,
) {
    val current by rememberUpdatedState(corners)
    val onChange by rememberUpdatedState(onCornersChange)
    var active by remember { mutableIntStateOf(-1) } // 0..3 corner, 4 move whole quad, -1 none
    Canvas(
        modifier.pointerInput(imageAspect) {
            val slop = 36.dp.toPx()
            detectDragGestures(
                onDragStart = { start ->
                    val fit = fitRect(size.width.toFloat(), size.height.toFloat(), imageAspect)
                    val px = current.map { Offset(fit.left + it.x * fit.width, fit.top + it.y * fit.height) }
                    val nearest = px.indices.minByOrNull { (px[it] - start).getDistance() } ?: 0
                    active = when {
                        (px[nearest] - start).getDistance() <= slop -> nearest
                        pointInQuad(start, px) -> 4
                        else -> -1
                    }
                },
                onDragEnd = { active = -1 },
                onDragCancel = { active = -1 },
            ) { change, drag ->
                if (active < 0) return@detectDragGestures
                change.consume()
                val fit = fitRect(size.width.toFloat(), size.height.toFloat(), imageAspect)
                val d = Offset(drag.x / fit.width, drag.y / fit.height)
                val c = current
                val next = if (active == 4) {
                    val dx = d.x.coerceIn(-c.minOf { it.x }, 1f - c.maxOf { it.x })
                    val dy = d.y.coerceIn(-c.minOf { it.y }, 1f - c.maxOf { it.y })
                    c.map { Offset(it.x + dx, it.y + dy) }
                } else {
                    c.mapIndexed { i, p -> if (i == active) Offset((p.x + d.x).coerceIn(0f, 1f), (p.y + d.y).coerceIn(0f, 1f)) else p }
                }
                if (isValidQuad(next)) onChange(next)
            }
        },
    ) {
        val fit = fitRect(size.width, size.height, imageAspect)
        val pts = corners.map { Offset(fit.left + it.x * fit.width, fit.top + it.y * fit.height) }
        val quad = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1..3) lineTo(pts[i].x, pts[i].y)
            close()
        }
        val outside = Path().apply { fillType = PathFillType.EvenOdd; addRect(fit); addPath(quad) }
        drawPath(outside, Color.Black.copy(alpha = 0.55f))
        drawPath(quad, Color.White.copy(alpha = 0.85f), style = Stroke(1.5.dp.toPx()))

        val thin = 1.dp.toPx(); val grid = Color.White.copy(alpha = 0.5f)
        for (i in 1..2) {
            val t = i / 3f
            fun lerp(a: Offset, b: Offset) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            drawLine(grid, lerp(pts[0], pts[1]), lerp(pts[3], pts[2]), thin)
            drawLine(grid, lerp(pts[0], pts[3]), lerp(pts[1], pts[2]), thin)
        }
        pts.forEach { p ->
            drawCircle(Color.White, 11.dp.toPx(), p)
            drawCircle(accent, 11.dp.toPx(), p, style = Stroke(3.dp.toPx()))
        }
    }
}
