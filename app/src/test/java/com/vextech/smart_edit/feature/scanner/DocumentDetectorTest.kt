package com.vextech.smart_edit.feature.scanner

import com.google.common.truth.Truth.assertThat
import com.vextech.smart_edit.feature.scanner.domain.*
import org.junit.Test

class DocumentDetectorTest {
    private val page = listOf(.22f to .15f, .80f to .20f, .76f to .88f, .18f to .82f)

    private fun frame(bg: Int, fg: Int, quad: List<Pair<Float, Float>>, w: Int = 160, h: Int = 213): LumaFrame {
        val pts = quad.map { it.first * w to it.second * h }
        val rnd = java.util.Random(7)
        return LumaFrame(w, h, IntArray(w * h) { i ->
            val x = i % w + .5f; val y = i / w + .5f
            var inside = true
            for (k in pts.indices) { val a = pts[k]; val b = pts[(k + 1) % 4]; if ((b.first - a.first) * (y - a.second) - (b.second - a.second) * (x - a.first) < 0f) inside = false }
            ((if (inside) fg else bg) + rnd.nextInt(13) - 6).coerceIn(0, 255)
        })
    }

    @Test fun findsLightPageOnDarkTable() {
        val q = DocumentDetector.detect(frame(40, 215, page))!!.quad
        assertThat(q.topLeft.x).isWithin(.035f).of(.22f); assertThat(q.topLeft.y).isWithin(.035f).of(.15f)
        assertThat(q.bottomRight.x).isWithin(.035f).of(.76f); assertThat(q.bottomRight.y).isWithin(.035f).of(.88f)
    }

    @Test fun findsDarkPageOnLightTable() { assertThat(DocumentDetector.detect(frame(225, 45, page))).isNotNull() }
    @Test fun ignoresEmptyScene() { assertThat(DocumentDetector.detect(frame(120, 120, page))).isNull() }
    @Test fun ignoresFullFrame() { assertThat(DocumentDetector.detect(frame(40, 215, listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)))).isNull() }

    @Test fun rotationKeepsCornerOrder() {
        val q = CropQuad(NormalizedPoint(.1f, .2f), NormalizedPoint(.9f, .2f), NormalizedPoint(.9f, .8f), NormalizedPoint(.1f, .8f))
        val r = QuadGeometry.rotateClockwise(q, 90)
        assertThat(r.topRight.x).isWithin(1e-4f).of(.8f); assertThat(r.topRight.y).isWithin(1e-4f).of(.1f)
    }

    @Test fun autoCaptureFiresOnceThenWaitsForANewPage() {
        val a = CropQuad(NormalizedPoint(.2f, .2f), NormalizedPoint(.8f, .2f), NormalizedPoint(.8f, .8f), NormalizedPoint(.2f, .8f))
        val s = CaptureStabilizer(holdMs = 900)
        var t = 0L; var fired = 0
        repeat(40) { if (s.update(a, t).ready) fired++; t += 50 }
        assertThat(fired).isEqualTo(1)
        repeat(5) { s.update(null, t); t += 50 }
        t += 1500
        repeat(40) { if (s.update(a, t).ready) fired++; t += 50 }
        assertThat(fired).isEqualTo(2)
    }

    private fun stripes(blurPasses: Int): LumaFrame {
        val w = 240; val h = 160
        var cur = IntArray(w * h) { i -> if (((i % w) / 12) % 2 == 0) 235 else 45 }
        repeat(blurPasses) {
            val out = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                var s = 0
                for (dy in -1..1) for (dx in -1..1) s += cur[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
                out[y * w + x] = s / 9
            }
            cur = out
        }
        return LumaFrame(w, h, cur)
    }

    @Test fun sharpPageIsOkAndBlurredPageIsFlagged() {
        assertThat(QualityCheck.assess(stripes(0))).isEqualTo(PageQuality.OK)
        assertThat(QualityCheck.assess(stripes(5))).isEqualTo(PageQuality.BLURRY)
    }
    @Test fun blankAndDarkPages() {
        assertThat(QualityCheck.assess(LumaFrame(100, 100, IntArray(10_000) { 240 }))).isEqualTo(PageQuality.OK)
        assertThat(QualityCheck.assess(LumaFrame(100, 100, IntArray(10_000) { 20 }))).isEqualTo(PageQuality.DARK)
    }
    @Test fun cleanTextPageSuggestsBlackAndWhite() {
        val rnd = java.util.Random(3)
        val px = IntArray(10_000) { if (rnd.nextFloat() < .08f) 0xFF202020.toInt() else 0xFFF2F2F0.toInt() }
        assertThat(FilterAdvisor.suggest(px)).isEqualTo(ScanFilter.BLACK_WHITE)
    }
}
