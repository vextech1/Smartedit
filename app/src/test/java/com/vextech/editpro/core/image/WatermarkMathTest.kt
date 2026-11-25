package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermarkMathTest {
    @Test fun markIsAShareOfTheWidth() {
        val (w, h) = WatermarkMath.markSize(1000, 800, 4.0, 30)
        assertEquals(300.0, w, 0.001); assertEquals(75.0, h, 0.001)
    }

    @Test fun tallMarkIsLimitedToNinetyPercentOfTheHeight() {
        val (w, h) = WatermarkMath.markSize(1000, 500, 0.5, 100)
        assertEquals(450.0, h, 0.001); assertEquals(225.0, w, 0.001)
    }

    @Test fun bottomRightSitsInByTheMargin() {
        val p = WatermarkMath.placeMark(1000, 800, 200.0, 50.0, MarkSpot.BR, 5)
        assertEquals(1000 - 200 - 40.0, p.x, 0.001); assertEquals(800 - 50 - 40.0, p.y, 0.001)
    }

    @Test fun centreIsCentred() {
        val p = WatermarkMath.placeMark(1000, 800, 200.0, 100.0, MarkSpot.MC, 3)
        assertEquals(400.0, p.x, 0.001); assertEquals(350.0, p.y, 0.001)
    }

    @Test fun topLeftUsesTheShorterSideForTheMargin() {
        val p = WatermarkMath.placeMark(1000, 500, 10.0, 10.0, MarkSpot.TL, 10)
        assertEquals(50.0, p.x, 0.001); assertEquals(50.0, p.y, 0.001)
    }

    @Test fun pinchIsLimited() {
        assertEquals(100, WatermarkMath.pinchSize(60, 5.0))
        assertEquals(5, WatermarkMath.pinchSize(30, 0.01))
        assertEquals(45, WatermarkMath.pinchSize(30, 1.5))
    }

    @Test fun centreStaysFarEnoughInForTheWholeMark() {
        val c = WatermarkMath.clampCenter(1000, 1000, 400.0, 200.0, PxPoint(0.0, 1.0))
        assertEquals(0.2, c.x, 0.0001); assertEquals(0.9, c.y, 0.0001)
    }

    @Test fun aMarkBiggerThanThePhotoStaysCentred() {
        val c = WatermarkMath.clampCenter(100, 100, 400.0, 400.0, PxPoint(0.1, 0.9))
        assertEquals(0.5, c.x, 0.0001); assertEquals(0.5, c.y, 0.0001)
    }

    @Test fun draggedSpotIsPlacedByItsCentre() {
        val p = WatermarkMath.placeAt(1000, 1000, 200.0, 100.0, PxPoint(0.5, 0.5))
        assertEquals(400.0, p.x, 0.001); assertEquals(450.0, p.y, 0.001)
    }

    @Test fun centreOfASpotRoundTripsThroughPlaceAt() {
        val w = 200.0; val h = 80.0
        val c = WatermarkMath.centerOf(1000, 800, w, h, MarkSpot.BR, 4)
        val a = WatermarkMath.placeAt(1000, 800, w, h, c)
        val b = WatermarkMath.placeMark(1000, 800, w, h, MarkSpot.BR, 4)
        assertEquals(b.x, a.x, 0.001); assertEquals(b.y, a.y, 0.001)
    }

    @Test fun dragMovesFromWhereItWasAndStaysOnThePhoto() {
        val p = WatermarkMath.dragged(1000, 1000, 200.0, 200.0, PxPoint(0.5, 0.5), 0.9, -0.2)
        assertEquals(0.9, p.x, 0.0001); assertEquals(0.3, p.y, 0.0001)
    }

    @Test fun tilesStartAtTheCornerAndShiftEveryOtherRow() {
        val t = WatermarkMath.tilePoints(100.0, 100.0, 40.0, 20.0, 0)
        assertEquals(PxPoint(0.0, 0.0), t.first())
        assertTrue(t.any { it.y == 20.0 && it.x == -20.0 })
        assertTrue(t.none { it.x + 40.0 <= 0 })
    }

    @Test fun gapSpreadsTheTiles() {
        assertTrue(WatermarkMath.tilePoints(1000.0, 1000.0, 50.0, 50.0, 200).size < WatermarkMath.tilePoints(1000.0, 1000.0, 50.0, 50.0, 0).size)
    }

    @Test fun aTinyMarkCannotBuildMillionsOfTiles() = assertTrue(WatermarkMath.tilePoints(10000.0, 10000.0, 1.0, 1.0, 0).size <= WatermarkMath.MAX_TILES)

    @Test fun zeroSizeMakesNoTiles() = assertTrue(WatermarkMath.tilePoints(100.0, 100.0, 0.0, 0.0, 0).isEmpty())

    @Test fun lightTextGetsADarkOutlineAndTheReverse() {
        assertEquals(0xFF000000.toInt(), WatermarkMath.outlineFor(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), WatermarkMath.outlineFor(0xFF000000.toInt()))
    }

    @Test fun nothingIsReadyUntilThereAreWordsOrALogo() {
        assertFalse(WatermarkSpec().ready)
        assertFalse(WatermarkSpec(text = "  ").ready)
        assertTrue(WatermarkSpec(text = "© Jane").ready)
        assertFalse(WatermarkSpec(type = WatermarkType.LOGO).ready)
        assertTrue(WatermarkSpec(type = WatermarkType.LOGO, logoPath = "/x").ready)
    }

    @Test fun choosingASpotForgetsTheDraggedPlace() {
        val s = WatermarkSpec(atX = 0.3, atY = 0.4).withSpot(MarkSpot.TL)
        assertNull(s.at)
    }

    @Test fun repeatIgnoresADraggedPlace() = assertNull(WatermarkSpec(spot = MarkSpot.TILE, atX = 0.3, atY = 0.4).at)

    @Test fun startsUnchanged() = assertFalse(WatermarkSpec().changed)
}
