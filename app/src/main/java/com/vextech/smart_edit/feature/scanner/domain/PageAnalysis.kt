package com.vextech.smart_edit.feature.scanner.domain

/** Picks a starting filter for a new page. The user can always change it in the editor. Thresholds are heuristics to tune on real scans. */
object FilterAdvisor {
    private const val MEAN_SATURATION_COLOUR = 0.18f
    private const val VIVID_SATURATION = 0.40f
    private const val VIVID_SHARE_COLOUR = 0.03f

    /** [argb] are the pixels of the (already cropped) page, downscaled to about 200 px. */
    fun suggest(argb: IntArray): ScanFilter {
        if (argb.size < 64) return ScanFilter.ORIGINAL
        var satSum = 0f; var satCount = 0; var vivid = 0; var bright = 0; var dark = 0
        for (p in argb) {
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val max = maxOf(r, maxOf(g, b)); val min = minOf(r, minOf(g, b))
            val luma = (299 * r + 587 * g + 114 * b) / 1000
            if (luma > 170) bright++
            if (luma < 90) dark++
            if (max > 40) {
                val s = (max - min).toFloat() / max
                satSum += s; satCount++
                if (s > VIVID_SATURATION && max > 60) vivid++
            }
        }
        val n = argb.size.toFloat()
        val meanSat = if (satCount == 0) 0f else satSum / satCount
        val brightShare = bright / n; val darkShare = dark / n
        return when {
            meanSat > MEAN_SATURATION_COLOUR || vivid / n > VIVID_SHARE_COLOUR -> ScanFilter.ORIGINAL   // colour matters: photos, IDs, forms
            brightShare >= 0.55f && darkShare in 0.01f..0.30f -> ScanFilter.BLACK_WHITE                 // clean paper with ink
            else -> ScanFilter.GRAYSCALE
        }
    }
}

enum class PageQuality { OK, BLURRY, DARK }

/** Flags a captured page that is probably worth retaking. Blur is judged by edge strength (variance of the Laplacian). */
object QualityCheck {
    const val DARK_MEAN = 50f
    /** Typical sharp document photos at ~480 px score in the hundreds to thousands; below 100 is the usual "blurry" cut-off. Tune on real captures. */
    const val BLUR_VARIANCE = 100f
    const val BLANK_VARIANCE = 2f      // below this there are no edges at all (blank page): cannot judge, treat as fine

    fun assess(frame: LumaFrame): PageQuality {
        if (frame.mean() < DARK_MEAN) return PageQuality.DARK
        val v = laplacianVariance(frame)
        return if (v >= BLANK_VARIANCE && v < BLUR_VARIANCE) PageQuality.BLURRY else PageQuality.OK
    }

    fun laplacianVariance(f: LumaFrame): Float {
        val w = f.width; val h = f.height
        if (w < 3 || h < 3) return 0f
        var sum = 0.0; var sumSq = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val l = 4 * f.data[i] - f.data[i - 1] - f.data[i + 1] - f.data[i - w] - f.data[i + w]
            sum += l; sumSq += l.toDouble() * l; n++
        }
        val mean = sum / n
        return (sumSq / n - mean * mean).toFloat()
    }
}
