package com.vextech.smart_edit.feature.scanner.domain

/** Grayscale frame: one 0..255 value per pixel, row-major. Pure Kotlin so it can be unit-tested off-device. */
class LumaFrame(val width: Int, val height: Int, val data: IntArray) {
    init { require(width > 0 && height > 0 && data.size == width * height) { "Bad frame size" } }
    fun mean(): Float { var sum = 0L; for (v in data) sum += v; return sum.toFloat() / data.size }
}

/** [areaRatio] is the quad's share of the frame (0..1). */
data class Detection(val quad: CropQuad, val areaRatio: Float)

/**
 * Finds the page in a frame without OpenCV: blur, Otsu threshold, largest light (or dark) blob that is not the
 * background, then its four extreme corners. Works for a page clearly lighter or darker than what is behind it and
 * tilted less than 45 degrees. Returns null when nothing page-like is found, so callers must always have a manual path.
 */
object DocumentDetector {
    private const val MIN_AREA = 0.12f
    private const val MAX_AREA = 0.92f
    private const val MIN_FILL = 0.65f
    private const val MIN_SIDE = 0.10f

    /** [minArea] is the smallest accepted share of the frame (smaller subjects such as ID cards and receipts need a lower value). */
    fun detect(frame: LumaFrame, minArea: Float = MIN_AREA): Detection? {
        val w = frame.width; val h = frame.height
        if (w < 24 || h < 24) return null
        val smooth = blur(frame.data, w, h)
        val threshold = otsu(smooth)
        return listOf(true, false)
            .mapNotNull { lightBlob -> candidate(smooth, w, h, threshold, lightBlob, minArea) }
            .maxByOrNull { it.second }?.first
    }

    private fun candidate(px: IntArray, w: Int, h: Int, t: Int, light: Boolean, minArea: Float): Pair<Detection, Float>? {
        val n = w * h
        val seen = BooleanArray(n)
        val queue = IntArray(n)
        var best: Pair<Detection, Float>? = null
        for (start in 0 until n) {
            if (seen[start] || (px[start] >= t) != light) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            var area = 0; var sides = 0
            var tl = start; var br = start; var tr = start; var bl = start
            var minSum = Int.MAX_VALUE; var maxSum = Int.MIN_VALUE; var maxDiff = Int.MIN_VALUE; var minDiff = Int.MAX_VALUE
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w; area++
                val s = x + y; val d = x - y
                if (s < minSum) { minSum = s; tl = i }
                if (s > maxSum) { maxSum = s; br = i }
                if (d > maxDiff) { maxDiff = d; tr = i }
                if (d < minDiff) { minDiff = d; bl = i }
                if (x == 0) sides = sides or 1
                if (x == w - 1) sides = sides or 2
                if (y == 0) sides = sides or 4
                if (y == h - 1) sides = sides or 8
                if (x > 0) { val j = i - 1; if (!seen[j] && (px[j] >= t) == light) { seen[j] = true; queue[tail++] = j } }
                if (x < w - 1) { val j = i + 1; if (!seen[j] && (px[j] >= t) == light) { seen[j] = true; queue[tail++] = j } }
                if (y > 0) { val j = i - w; if (!seen[j] && (px[j] >= t) == light) { seen[j] = true; queue[tail++] = j } }
                if (y < h - 1) { val j = i + w; if (!seen[j] && (px[j] >= t) == light) { seen[j] = true; queue[tail++] = j } }
            }
            val areaRatio = area.toFloat() / n
            if (areaRatio < minArea || areaRatio > MAX_AREA) continue
            if (Integer.bitCount(sides) >= 3) continue            // touches most edges: that is the background
            fun pt(i: Int) = floatArrayOf((i % w + 0.5f) / w, (i / w + 0.5f) / h)
            val p = listOf(pt(tl), pt(tr), pt(br), pt(bl))
            val quadArea = shoelace(p, w.toFloat(), h.toFloat())
            if (quadArea <= 0f) continue
            val fill = (area.toFloat() / quadArea).coerceAtMost(1f)
            if (fill < MIN_FILL || !convex(p) || !longEnough(p, w, h)) continue
            val quad = CropQuad(
                NormalizedPoint(p[0][0].coerceIn(0f, 1f), p[0][1].coerceIn(0f, 1f)),
                NormalizedPoint(p[1][0].coerceIn(0f, 1f), p[1][1].coerceIn(0f, 1f)),
                NormalizedPoint(p[2][0].coerceIn(0f, 1f), p[2][1].coerceIn(0f, 1f)),
                NormalizedPoint(p[3][0].coerceIn(0f, 1f), p[3][1].coerceIn(0f, 1f)),
            )
            val score = areaRatio * fill
            if (best == null || score > best.second) best = Detection(quad, QuadGeometry.area(quad)) to score
        }
        return best
    }

    /** Shoelace area in pixel units (points are normalized, so scale them first). */
    private fun shoelace(p: List<FloatArray>, w: Float, h: Float): Float {
        var s = 0f
        for (i in p.indices) { val a = p[i]; val b = p[(i + 1) % p.size]; s += (a[0] * w) * (b[1] * h) - (b[0] * w) * (a[1] * h) }
        return kotlin.math.abs(s) / 2f
    }

    private fun convex(p: List<FloatArray>): Boolean {
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % 4]; val c = p[(i + 2) % 4]
            if ((b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0]) <= 0f) return false
        }
        return true
    }

    private fun longEnough(p: List<FloatArray>, w: Int, h: Int): Boolean {
        val min = MIN_SIDE * minOf(w, h)
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % 4]
            if (kotlin.math.hypot((a[0] - b[0]) * w, (a[1] - b[1]) * h) < min) return false
        }
        return true
    }

    private fun blur(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0; var count = 0
            for (dy in -1..1) { val yy = y + dy; if (yy < 0 || yy >= h) continue
                for (dx in -1..1) { val xx = x + dx; if (xx < 0 || xx >= w) continue; sum += src[yy * w + xx]; count++ } }
            out[y * w + x] = sum / count
        }
        return out
    }

    private fun otsu(px: IntArray): Int {
        val hist = IntArray(256); for (v in px) hist[v.coerceIn(0, 255)]++
        val total = px.size; var sumAll = 0L; for (i in 0..255) sumAll += i.toLong() * hist[i]
        var sumB = 0L; var wB = 0; var best = 0f; var threshold = 128
        for (i in 0..255) {
            wB += hist[i]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += i.toLong() * hist[i]
            val mB = sumB.toFloat() / wB; val mF = (sumAll - sumB).toFloat() / wF
            val between = wB.toFloat() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; threshold = i + 1 }
        }
        return threshold
    }
}
