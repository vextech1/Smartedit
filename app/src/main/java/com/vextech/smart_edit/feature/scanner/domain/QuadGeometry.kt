package com.vextech.smart_edit.feature.scanner.domain

import kotlin.math.abs
import kotlin.math.hypot

object QuadGeometry {
    /** Share of the unit square covered by [q] (shoelace). */
    fun area(q: CropQuad): Float {
        val p = listOf(q.topLeft, q.topRight, q.bottomRight, q.bottomLeft)
        var s = 0f
        for (i in p.indices) { val a = p[i]; val b = p[(i + 1) % 4]; s += a.x * b.y - b.x * a.y }
        return abs(s) / 2f
    }

    /** Largest distance any corner moved between [a] and [b], in normalized units. */
    fun distance(a: CropQuad, b: CropQuad): Float {
        val pa = listOf(a.topLeft, a.topRight, a.bottomRight, a.bottomLeft)
        val pb = listOf(b.topLeft, b.topRight, b.bottomRight, b.bottomLeft)
        var m = 0f
        for (i in pa.indices) m = maxOf(m, hypot(pa[i].x - pb[i].x, pa[i].y - pb[i].y))
        return m
    }

    fun lerp(from: CropQuad, to: CropQuad, t: Float): CropQuad {
        fun mix(a: NormalizedPoint, b: NormalizedPoint) = NormalizedPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        return CropQuad(mix(from.topLeft, to.topLeft), mix(from.topRight, to.topRight), mix(from.bottomRight, to.bottomRight), mix(from.bottomLeft, to.bottomLeft))
    }

    /** Orders any four corners as top-left, top-right, bottom-right, bottom-left. */
    fun ordered(points: List<NormalizedPoint>): CropQuad {
        require(points.size == 4)
        return CropQuad(
            points.minBy { it.x + it.y }, points.maxBy { it.x - it.y },
            points.maxBy { it.x + it.y }, points.minBy { it.x - it.y },
        )
    }

    /** Rotates the quad as the picture would turn clockwise by [degrees] (0/90/180/270), then re-orders its corners. */
    fun rotateClockwise(q: CropQuad, degrees: Int): CropQuad {
        val turns = ((degrees % 360) + 360) % 360
        if (turns == 0) return q
        fun rot(p: NormalizedPoint) = when (turns) {
            90 -> NormalizedPoint(1f - p.y, p.x)
            180 -> NormalizedPoint(1f - p.x, 1f - p.y)
            270 -> NormalizedPoint(p.y, 1f - p.x)
            else -> p
        }
        return ordered(listOf(q.topLeft, q.topRight, q.bottomRight, q.bottomLeft).map(::rot))
    }
}

/**
 * Decides when to fire the shutter: the page outline must stay put for [holdMs]. After a capture it will not fire again
 * until the page leaves the view or moves by more than [rearmDistance], and never faster than [minIntervalMs].
 */
class CaptureStabilizer(
    private val holdMs: Long = 900,
    private val tolerance: Float = 0.02f,
    private val rearmDistance: Float = 0.10f,
    private val minIntervalMs: Long = 1_200,
    private val lostFramesToRearm: Int = 4,
) {
    data class Status(val progress: Float, val ready: Boolean)

    private var anchor: CropQuad? = null
    private var stableSince = 0L
    private var armed = true
    private var captured: CropQuad? = null
    private var capturedAt = Long.MIN_VALUE / 2
    private var lost = 0

    @Synchronized fun update(quad: CropQuad?, nowMs: Long): Status {
        if (quad == null) {
            anchor = null
            if (!armed && ++lost >= lostFramesToRearm) armed = true
            return Status(0f, false)
        }
        lost = 0
        if (!armed) {
            val ref = captured
            if (ref != null && QuadGeometry.distance(ref, quad) <= rearmDistance) return Status(0f, false)
            armed = true; anchor = null
        }
        if (nowMs - capturedAt < minIntervalMs) { anchor = null; return Status(0f, false) }
        val a = anchor
        if (a == null || QuadGeometry.distance(a, quad) > tolerance) { anchor = quad; stableSince = nowMs }
        val progress = ((nowMs - stableSince).toFloat() / holdMs).coerceIn(0f, 1f)
        if (progress >= 1f) { markCapturedLocked(quad, nowMs); return Status(1f, true) }
        return Status(progress, false)
    }

    /** Call for a manual shutter press too, so auto-capture does not take the same page again. */
    @Synchronized fun markCaptured(quad: CropQuad?, nowMs: Long) = markCapturedLocked(quad, nowMs)

    @Synchronized fun reset() { anchor = null; armed = true; captured = null; lost = 0 }

    private fun markCapturedLocked(quad: CropQuad?, nowMs: Long) {
        captured = quad; capturedAt = nowMs; armed = false; anchor = null; lost = 0
    }
}
