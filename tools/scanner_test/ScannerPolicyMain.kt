import com.vextech.smart_edit.feature.scanner.domain.*
import com.vextech.smart_edit.core.library.OcrPolicies
fun main(){
    check(ScannerPolicies.reorderedIds(listOf("a","b","c"),0,2)==listOf("b","c","a"))
    check(ScannerPolicies.reorderedIds(listOf("a","b","c"),2,0)==listOf("c","a","b"))
    check(runCatching { ScannerPolicies.reorderedIds(listOf("a"),1,0) }.isFailure)
    check(ScannerPolicies.normalizeRotation(-90)==270)
    check(ScannerPolicies.normalizeRotation(450)==90)
    check(!ScannerPolicies.canExport(0) && ScannerPolicies.canExport(1))
    val q=CropQuad(NormalizedPoint(.1f,.2f),NormalizedPoint(.9f,.2f),NormalizedPoint(.8f,.9f),NormalizedPoint(.2f,.8f))
    check(CropQuad.decode(q.encode())==q)
    check(runCatching { NormalizedPoint(-.1f,0f) }.isFailure)
    check(ScannerPolicies.timestampLabel(1_793_446_560_000L, java.util.Locale.ENGLISH, java.util.TimeZone.getTimeZone("UTC"))=="31 Oct 11:36")
    check(ScannerPolicies.withoutPending(listOf("a","b","c"),setOf("b")){ it }==listOf("a","c") && ScannerPolicies.withoutPending(listOf("a","b","c"),setOf("a","c")){ it }==listOf("b"))
    check(ScannerPolicies.withoutPending(listOf("a","b"),emptySet()){ it }==listOf("a","b"))
    detectorTests(); geometryTests(); stabilizerTests(); analysisTests()
    println("Scanner production-policy tests: PASS")
}

// ── Phase 1: detector + stabilizer ──
private fun synthetic(w: Int, h: Int, bg: Int, fg: Int, quad: List<Pair<Float, Float>>, noise: Int = 0): LumaFrame {
    val rnd = java.util.Random(7)
    val pts = quad.map { it.first * w to it.second * h }
    val data = IntArray(w * h) { i ->
        val x = (i % w) + 0.5f; val y = (i / w) + 0.5f
        var inside = true
        for (k in pts.indices) { val a = pts[k]; val b = pts[(k + 1) % 4]
            if ((b.first - a.first) * (y - a.second) - (b.second - a.second) * (x - a.first) < 0f) inside = false }
        // dark "text" specks inside the page
        val text = inside && ((i % w) % 9 == 0) && ((i / w) % 5 < 2)
        val base = if (text) bg + 20 else if (inside) fg else bg
        (base + if (noise > 0) rnd.nextInt(noise * 2 + 1) - noise else 0).coerceIn(0, 255)
    }
    return LumaFrame(w, h, data)
}
private fun near(a: NormalizedPoint, x: Float, y: Float, tol: Float = 0.035f) = kotlin.math.abs(a.x - x) <= tol && kotlin.math.abs(a.y - y) <= tol

fun detectorTests() {
    val page = listOf(.22f to .15f, .80f to .20f, .76f to .88f, .18f to .82f)
    val light = DocumentDetector.detect(synthetic(160, 213, 40, 215, page, noise = 6))
    check(light != null) { "light page not found" }
    check(near(light.quad.topLeft, .22f, .15f) && near(light.quad.topRight, .80f, .20f) && near(light.quad.bottomRight, .76f, .88f) && near(light.quad.bottomLeft, .18f, .82f)) { "light corners off: ${light.quad}" }
    val dark = DocumentDetector.detect(synthetic(160, 213, 225, 45, page, noise = 6))
    check(dark != null && near(dark.quad.topLeft, .22f, .15f) && near(dark.quad.bottomRight, .76f, .88f)) { "dark page not found: $dark" }
    check(DocumentDetector.detect(synthetic(160, 213, 120, 120, page, noise = 25)) == null) { "found a page in noise" }
    check(DocumentDetector.detect(synthetic(160, 213, 40, 215, listOf(.45f to .45f, .52f to .45f, .52f to .52f, .45f to .52f))) == null) { "tiny blob accepted" }
    check(DocumentDetector.detect(synthetic(160, 213, 40, 215, listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f))) == null) { "full frame accepted" }
}

fun geometryTests() {
    val q = CropQuad(NormalizedPoint(.1f, .2f), NormalizedPoint(.9f, .2f), NormalizedPoint(.9f, .8f), NormalizedPoint(.1f, .8f))
    check(kotlin.math.abs(QuadGeometry.area(q) - .48f) < 1e-4f)
    val r = QuadGeometry.rotateClockwise(q, 90)
    // 90° clockwise: old top-left (.1,.2) -> (.8,.1) and becomes the top-right corner
    check(near(r.topRight, .8f, .1f, 1e-4f) && near(r.topLeft, .2f, .1f, 1e-4f) && near(r.bottomRight, .8f, .9f, 1e-4f)) { "rotate90 wrong: $r" }
    check(QuadGeometry.distance(QuadGeometry.rotateClockwise(QuadGeometry.rotateClockwise(q, 90), 270), q) < 1e-5f)
    check(QuadGeometry.distance(q, q) == 0f)
}

fun stabilizerTests() {
    val a = CropQuad(NormalizedPoint(.2f, .2f), NormalizedPoint(.8f, .2f), NormalizedPoint(.8f, .8f), NormalizedPoint(.2f, .8f))
    val moved = QuadGeometry.lerp(a, CropQuad(NormalizedPoint(.35f, .3f), NormalizedPoint(.95f, .3f), NormalizedPoint(.95f, .9f), NormalizedPoint(.35f, .9f)), 1f)
    val s = CaptureStabilizer(holdMs = 900)
    var t = 0L; var fired = 0
    repeat(40) { if (s.update(a, t).ready) fired++; t += 50 }           // 2 s of a steady page
    check(fired == 1) { "should fire exactly once, fired $fired" }
    check(!s.update(a, t).ready)                                         // same page still there: stays quiet
    repeat(5) { s.update(null, t); t += 50 }                             // page leaves view: re-arm
    t += 1500
    var again = 0; repeat(40) { if (s.update(a, t).ready) again++; t += 50 }
    check(again == 1) { "should re-arm after the page left, fired $again" }
    val s2 = CaptureStabilizer(holdMs = 900); var t2 = 0L; var f2 = 0
    repeat(60) { val q = if (it % 2 == 0) a else moved; if (s2.update(q, t2).ready) f2++; t2 += 50 }
    check(f2 == 0) { "a shaking page must not fire" }
    val s3 = CaptureStabilizer(); s3.markCaptured(a, 0L)
    check(!s3.update(a, 5_000L).ready)                                    // manual capture disarms auto
}

// ── Phase 2: filter advice, quality, orientation ──
private fun argbPage(w: Int, h: Int, paper: Int, ink: Int, inkShare: Float): IntArray {
    val rnd = java.util.Random(3)
    return IntArray(w * h) { if (rnd.nextFloat() < inkShare) ink else paper }
}
private fun lumaStripes(w: Int, h: Int, period: Int, soft: Boolean): LumaFrame {
    val raw = IntArray(w * h) { i -> if (((i % w) / period) % 2 == 0) 235 else 45 }
    if (!soft) return LumaFrame(w, h, raw)
    var cur = raw
    repeat(5) { val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) { var s = 0; var c = 0
            for (dy in -1..1) for (dx in -1..1) { val xx = (x + dx).coerceIn(0, w - 1); val yy = (y + dy).coerceIn(0, h - 1); s += cur[yy * w + xx]; c++ }
            out[y * w + x] = s / c }
        cur = out }
    return LumaFrame(w, h, cur)
}

fun analysisTests() {
    val white = 0xFFF2F2F0.toInt(); val black = 0xFF202020.toInt()
    check(FilterAdvisor.suggest(argbPage(100, 100, white, black, .08f)) == ScanFilter.BLACK_WHITE) { "clean text page should be B&W" }
    check(FilterAdvisor.suggest(argbPage(100, 100, 0xFFFFE0A0.toInt(), 0xFF3050C0.toInt(), .2f)) == ScanFilter.ORIGINAL) { "colourful page should stay original" }
    check(FilterAdvisor.suggest(argbPage(100, 100, 0xFF9A9A9A.toInt(), 0xFF303030.toInt(), .1f)) == ScanFilter.GRAYSCALE) { "grey shadowed page should be grayscale" }
    check(FilterAdvisor.suggest(IntArray(10)) == ScanFilter.ORIGINAL)

    check(QualityCheck.assess(lumaStripes(240, 160, 12, false)) == PageQuality.OK) { "sharp stripes should be OK" }
    check(QualityCheck.assess(lumaStripes(240, 160, 12, true)) == PageQuality.BLURRY) { "blurred stripes should be BLURRY" }
    check(QualityCheck.assess(LumaFrame(200, 120, IntArray(200 * 120) { 240 })) == PageQuality.OK) { "blank page is not blurry" }
    check(QualityCheck.assess(LumaFrame(200, 120, IntArray(200 * 120) { 20 })) == PageQuality.DARK)

    check(OcrPolicies.pageRotation(intArrayOf(120, 3, 0, 5)) == 0)
    check(OcrPolicies.pageRotation(intArrayOf(4, 150, 2, 0)) == 90)
    check(OcrPolicies.pageRotation(intArrayOf(10, 0, 160, 0)) == 180)
    check(OcrPolicies.pageRotation(intArrayOf(100, 110, 0, 0)) == 0) { "no clear winner keeps current rotation" }
    check(OcrPolicies.pageRotation(intArrayOf(0, 0, 0, 12)) == 0) { "too little text to decide" }
}
