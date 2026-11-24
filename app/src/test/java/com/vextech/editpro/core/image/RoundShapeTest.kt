package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundShapeTest {
    @Test fun originalKeepsThePhotosShape() = assertEquals(4000 to 3000, RoundShapes.outputSize(4000, 3000, 0.0, 4000))

    @Test fun squareTakesTheShorterSide() = assertEquals(3000 to 3000, RoundShapes.outputSize(4000, 3000, 1.0, 4000))

    @Test fun portraitCropsTheWidthOfALandscapePhoto() {
        val (w, h) = RoundShapes.outputSize(4000, 3000, 4.0 / 5, 4000)
        assertEquals(3000, h); assertEquals(2400, w)
    }

    @Test fun wideShapeCropsTheHeightOfASquarePhoto() {
        val (w, h) = RoundShapes.outputSize(3000, 3000, 16.0 / 9, 3000)
        assertEquals(3000, w); assertEquals(1688, h)
    }

    @Test fun neverLargerThanMaxSide() = assertEquals(1000 to 750, RoundShapes.outputSize(4000, 3000, 0.0, 1000))

    @Test fun radiusIsAShareOfTheShorterSide() {
        assertEquals(1500.0, RoundShapes.radiusFor(3000, 3000, 50), 0.001)
        assertEquals(300.0, RoundShapes.radiusFor(4000, 3000, 10), 0.001)
        assertEquals(0.0, RoundShapes.radiusFor(100, 100, 0), 0.001)
    }

    @Test fun radiusIsLimitedToACircle() = assertEquals(1500.0, RoundShapes.radiusFor(3000, 3000, 90), 0.001)

    @Test fun startsAsACircle() {
        val f = RoundForm()
        assertEquals("circle", f.presetId)
        assertFalse(f.changed)
        assertEquals(1.0, f.spec().ratio, 0.0)
        assertEquals(50, f.spec().radiusPct)
    }

    @Test fun presetsSetTheirShapeAndRoundness() {
        val rounded = RoundForm().withPreset("rounded")
        assertEquals("1:1", rounded.ratioId); assertEquals(18, rounded.radius)
        val soft = RoundForm().withPreset("soft")
        assertEquals(RoundShapes.ORIGINAL, soft.ratioId); assertEquals(10, soft.radius)
        assertEquals(0.0, soft.spec().ratio, 0.0)
    }

    @Test fun customSettingsMatchNoPreset() {
        assertNull(RoundForm(radius = 30).presetId)
        assertEquals("circle", RoundForm(radius = 30).copy(radius = 50).presetId)
    }

    @Test fun changingThePictureShapeResetsThePan() {
        val f = RoundForm(panX = 0.5, panY = -0.3).withRatio("3:2")
        assertEquals(0.0, f.panX, 0.0); assertEquals(0.0, f.panY, 0.0)
        assertTrue(f.changed)
    }

    @Test fun zoomNeverGoesBelowOne() = assertEquals(1.0, RoundForm(zoom = 0.2).spec().zoom, 0.0)

    @Test fun formSizeFollowsTheShape() {
        assertEquals(3000 to 3000, RoundForm().outputSize(4000, 3000))
        assertEquals(4000 to 3000, RoundForm(ratioId = "original").outputSize(4000, 3000))
    }

    @Test fun everyPresetUsesAKnownRatio() {
        RoundShapes.presets.forEach { p -> assertTrue(p.id, RoundShapes.ratios.any { it.id == p.ratioId }) }
    }
}
