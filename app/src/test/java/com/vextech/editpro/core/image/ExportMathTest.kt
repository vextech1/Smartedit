package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportMathTest {
    @Test fun limitSideShrinksTheLongSideAndKeepsTheShape() {
        assertEquals(2048 to 1024, ExportMath.limitSide(4096, 2048, 2048))
        assertEquals(683 to 1280, ExportMath.limitSide(1024, 1920, 1280))
    }

    @Test fun limitSideNeverEnlarges() {
        assertEquals(800 to 600, ExportMath.limitSide(800, 600, 2048))
        assertEquals(800 to 600, ExportMath.limitSide(800, 600, 800))
    }

    @Test fun zeroKeepsTheSize() = assertEquals(3000 to 2000, ExportMath.limitSide(3000, 2000, 0))

    @Test fun neverBelowOnePixel() = assertEquals(800 to 1, ExportMath.limitSide(100000, 1, 800))

    @Test fun transparencyRulesOutJpeg() {
        assertFalse(ExportFormat.JPEG in ExportMath.formatsFor(hasAlpha = true))
        assertTrue(ExportFormat.PNG in ExportMath.formatsFor(hasAlpha = true))
        assertEquals(3, ExportMath.formatsFor(hasAlpha = false).size)
        assertEquals(ExportFormat.PNG, ExportMath.startFormat(true))
        assertEquals(ExportFormat.JPEG, ExportMath.startFormat(false))
    }

    @Test fun fileNameKeepsTheBaseAndUsesTheFormatExtension() {
        assertEquals("holiday_edited.webp", ExportMath.fileName("holiday.jpg", ExportFormat.WEBP))
        assertEquals("image_edited.png", ExportMath.fileName("  ", ExportFormat.PNG))
        assertEquals("a.b_edited.jpg", ExportMath.fileName("a.b.png", ExportFormat.JPEG))
    }

    @Test fun scaleBytesGrowsWithPixelsButNeverShrinks() {
        assertEquals(400_000L, ExportMath.scaleBytes(100_000, 1_000_000, 4_000_000))
        assertEquals(100_000L, ExportMath.scaleBytes(100_000, 1_000_000, 500_000))
        assertEquals(5L, ExportMath.scaleBytes(5, 0, 100))
    }

    @Test fun sampleIsCappedAtTheGivenSide() {
        assertEquals(1600 to 800, ExportMath.sampleSize(4000, 2000, 1600))
        assertEquals(1000 to 500, ExportMath.sampleSize(1000, 500, 1600))
    }
}
