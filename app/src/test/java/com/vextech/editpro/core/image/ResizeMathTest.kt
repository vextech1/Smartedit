package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResizeMathTest {
    @Test fun physicalUnitsGoThroughDpi() {
        assertEquals(600, SizeMath.toPx(2.0, SizeUnit.IN, 300))
        assertEquals(413, SizeMath.toPx(35.0, SizeUnit.MM, 300))
        assertEquals(591, SizeMath.toPx(5.0, SizeUnit.CM, 300))
        assertEquals(200, SizeMath.toPx(200.0, SizeUnit.PX, 300))
        assertEquals(1, SizeMath.toPx(0.0, SizeUnit.PX, 300))
    }

    @Test fun toMmConvertsEveryUnit() {
        assertEquals(25.4, SizeMath.toMm(300.0, SizeUnit.PX, 300), 0.0001)
        assertEquals(50.0, SizeMath.toMm(5.0, SizeUnit.CM, 300), 0.0001)
        assertEquals(50.8, SizeMath.toMm(2.0, SizeUnit.IN, 300), 0.0001)
    }

    @Test fun limitsRefuseTinyAndHugePictures() {
        assertTrue(SizeMath.withinLimits(200, 230))
        assertFalse(SizeMath.withinLimits(15, 200))
        assertFalse(SizeMath.withinLimits(8001, 100))
        assertFalse(SizeMath.withinLimits(7000, 7000))
        assertTrue(SizeMath.withinLimits(8000, 5000))
    }

    @Test fun linkedSideKeepsTheShape() {
        assertEquals(1000.0 to 750.0, SizeMath.linkedSide(true, 1000.0, 1.0, 4.0 / 3))
        assertEquals(1200.0 to 900.0, SizeMath.linkedSide(false, 1.0, 900.0, 4.0 / 3))
    }

    @Test fun describeTrimsNumbers() {
        assertEquals("35 × 45 mm", SizeMath.describe(35.0, 45.0, SizeUnit.MM))
        assertEquals("2.5 × 3.5 in", SizeMath.describe(2.5, 3.5, SizeUnit.IN))
        assertEquals("1080 × 1080 px", SizeMath.describe(1080.0, 1080.0, SizeUnit.PX))
    }

    @Test fun everyPresetIdIsUniqueAndEveryGroupHasSizes() {
        assertEquals(SizePresets.all.size, SizePresets.all.map { it.id }.toSet().size)
        PresetGroup.entries.forEach { assertTrue(SizePresets.inGroup(it).isNotEmpty()) }
        assertEquals(31, SizePresets.all.size)
    }

    @Test fun officialSizesNeedConfirmation() {
        assertTrue(SizePresets.byId("us-passport")!!.confirm)
        assertTrue(SizePresets.byId("form-200x230")!!.confirm)
        assertFalse(SizePresets.byId("p-4x6")!!.confirm)
        assertFalse(SizePresets.byId("ig-square")!!.confirm)
    }

    @Test fun formPresetsCarryTheirFileSizeLimits() {
        val p = SizePresets.byId("form-200x230")!!
        assertEquals(20, p.minKB); assertEquals(50, p.maxKB)
        assertNull(SizePresets.byId("form-35x45")!!.maxKB)
    }

    @Test fun fillCropsToTheShapeAndCentres() {
        val r = ResizeFit.fitRect(4000, 3000, 200, 200, FitMode.FILL)
        assertEquals(3000.0, r.sw, 0.001); assertEquals(3000.0, r.sh, 0.001)
        assertEquals(500.0, r.sx, 0.001); assertEquals(0.0, r.sy, 0.001)
        assertEquals(1000.0, r.slackX, 0.001)
        assertEquals(200.0 / 3000, r.scale, 0.0001)
    }

    @Test fun panMovesTheWindowAcrossTheSlack() {
        val left = ResizeFit.fitRect(4000, 3000, 200, 200, FitMode.FILL, panX = -1.0)
        val right = ResizeFit.fitRect(4000, 3000, 200, 200, FitMode.FILL, panX = 1.0)
        assertEquals(0.0, left.sx, 0.001)
        assertEquals(1000.0, right.sx, 0.001)
    }

    @Test fun zoomShrinksTheWindow() {
        val r = ResizeFit.fitRect(4000, 3000, 200, 200, FitMode.FILL, zoom = 2.0)
        assertEquals(1500.0, r.sw, 0.001)
    }

    @Test fun fitShowsTheWholePhotoCentred() {
        val r = ResizeFit.fitRect(4000, 2000, 400, 400, FitMode.FIT)
        assertEquals(400.0, r.dw, 0.001); assertEquals(200.0, r.dh, 0.001)
        assertEquals(0.0, r.dx, 0.001); assertEquals(100.0, r.dy, 0.001)
        assertEquals(4000.0, r.sw, 0.001)
    }

    @Test fun stretchFillsTheFrame() {
        val r = ResizeFit.fitRect(4000, 2000, 400, 400, FitMode.STRETCH)
        assertEquals(400.0, r.dw, 0.001); assertEquals(400.0, r.dh, 0.001)
    }

    @Test fun draggingThePhotoRightRevealsItsLeftSide() {
        val fit = ResizeFit.fitRect(4000, 3000, 200, 200, FitMode.FILL)
        val (x, _) = ResizeFit.panBy(0.0, 0.0, 50.0, 0.0, 200.0, 200.0, fit)
        assertTrue("pan went left, was $x", x < 0)
    }

    @Test fun panDoesNothingWithoutSlack() {
        val fit = ResizeFit.fitRect(1000, 1000, 200, 200, FitMode.FILL)
        assertEquals(0.3 to 0.0, ResizeFit.panBy(0.3, 0.0, 99.0, 99.0, 200.0, 200.0, fit))
    }

    @Test fun bestQualityFindsTheHighestThatFits() {
        val size = { q: Int -> q * 1000L }
        assertEquals(95 to true, ResizeFit.bestQuality(200_000, sizeAt = size))
        val (q, fits) = ResizeFit.bestQuality(50_000, sizeAt = size)
        assertTrue(fits); assertTrue("quality $q", q in 45..50)
        assertEquals(10 to false, ResizeFit.bestQuality(5_000, sizeAt = size))
    }
}
