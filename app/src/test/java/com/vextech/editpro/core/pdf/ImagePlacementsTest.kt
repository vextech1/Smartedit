package com.vextech.editpro.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePlacementsTest {
    private val a4w = 595.28f
    private val a4h = 841.89f

    @Test fun noMarginFillsTheWidthOfAPortraitPageForAWideImage() {
        val p = ImagePlacements.onPaper(a4w, a4h, ImagePlacements.MARGIN_NONE, 2000, 1000)
        assertEquals(a4w, p.width, 0.01f)
        assertEquals(a4w / 2f, p.height, 0.01f)
        assertEquals(0f, p.x, 0.01f)
    }

    @Test fun marginShrinksAndCentresTheImage() {
        val p = ImagePlacements.onPaper(a4w, a4h, ImagePlacements.MARGIN_LARGE, 1000, 1000)
        assertEquals(a4w - 96f, p.width, 0.01f)
        assertEquals(p.width, p.height, 0.01f)
        assertEquals(48f, p.x, 0.01f)
        assertEquals((a4h - p.height) / 2f, p.y, 0.01f)
    }

    @Test fun largerMarginMeansSmallerImage() {
        val small = ImagePlacements.onPaper(a4w, a4h, ImagePlacements.MARGIN_SMALL, 1200, 1600)
        val large = ImagePlacements.onPaper(a4w, a4h, ImagePlacements.MARGIN_LARGE, 1200, 1600)
        assertTrue(large.width < small.width)
        assertTrue(large.height < small.height)
    }

    @Test fun absurdMarginIsCappedSoTheImageStaysVisible() {
        val p = ImagePlacements.onPaper(a4w, a4h, 10_000f, 1000, 1000)
        assertTrue(p.width > 0f && p.height > 0f)
    }

    @Test fun imageKeepsItsProportions() {
        val p = ImagePlacements.onPaper(612f, 792f, ImagePlacements.MARGIN_SMALL, 3000, 1500)
        assertEquals(2f, p.width / p.height, 0.001f)
    }
}
