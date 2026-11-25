package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoCropTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun picture(w: Int, h: Int, bg: Int, fill: Int, x0: Int, y0: Int, x1: Int, y1: Int) =
        IntArray(w * h) { i -> val x = i % w; val y = i / w; if (x in x0 until x1 && y in y0 until y1) fill else bg }

    @Test fun findsADarkBoxOnWhite() {
        val px = picture(100, 100, white, black, 30, 20, 70, 80)
        assertEquals(PixelBox(28, 18, 44, 64), AutoCrop.detect(px, 100, 100))
    }

    @Test fun findsAWhitePageOnADarkSurface() {
        val px = picture(100, 100, black, white, 25, 25, 75, 85)
        val b = AutoCrop.detect(px, 100, 100)!!
        assertEquals(23, b.x); assertEquals(23, b.y)
    }

    @Test fun aPlainPictureHasNothingToCrop() = assertNull(AutoCrop.detect(IntArray(10000) { white }, 100, 100))

    @Test fun contentFillingTheWholePictureGivesNull() {
        val px = picture(100, 100, white, black, 2, 2, 98, 98)
        assertNull(AutoCrop.detect(px, 100, 100))
    }

    @Test fun aTinySpeckIsIgnored() {
        val px = picture(100, 100, white, black, 48, 48, 52, 52)
        assertNull(AutoCrop.detect(px, 100, 100))
    }

    @Test fun tooSmallAPictureGivesNull() = assertNull(AutoCrop.detect(IntArray(100) { white }, 10, 10))

    @Test fun seeThroughPixelsCountAsWhite() {
        val clear = 0x00000000
        val px = picture(100, 100, clear, black, 30, 20, 70, 80)
        assertEquals(PixelBox(28, 18, 44, 64), AutoCrop.detect(px, 100, 100))
    }

    @Test fun aShortArrayGivesNull() = assertNull(AutoCrop.detect(IntArray(10), 100, 100))

    @Test fun detectSizeKeepsTheShape() {
        assertEquals(240 to 180, AutoCrop.detectSize(4000, 3000))
        assertEquals(100 to 50, AutoCrop.detectSize(100, 50))
    }
}
