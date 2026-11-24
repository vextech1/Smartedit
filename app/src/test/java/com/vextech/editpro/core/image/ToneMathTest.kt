package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneMathTest {
    @Test fun neutralTableChangesNothing() {
        val t = ToneMath.toneTable(0f, 0f)
        for (v in 0..255) assertEquals(v, t[v])
    }

    @Test fun blackAndWhiteStayPutAtTheExtremes() {
        for (b in listOf(-100f, -40f, 0f, 40f, 100f)) for (c in listOf(-100f, -40f, 0f, 40f, 100f)) {
            val t = ToneMath.toneTable(b, c)
            assertEquals("black at b=$b c=$c", 0, t[0])
            assertEquals("white at b=$b c=$c", 255, t[255])
        }
    }

    @Test fun positiveBrightnessLightensTheMiddle() {
        assertTrue(ToneMath.toneTable(50f, 0f)[128] > 128)
        assertTrue(ToneMath.toneTable(-50f, 0f)[128] < 128)
    }

    @Test fun tableNeverFallsAsTheInputRises() {
        for (b in listOf(-100f, 0f, 100f)) for (c in listOf(-100f, 0f, 100f)) {
            val t = ToneMath.toneTable(b, c)
            for (v in 1..255) assertTrue("b=$b c=$c v=$v", t[v] >= t[v - 1])
        }
    }

    @Test fun contrastPushesDarksDownAndLightsUp() {
        val t = ToneMath.toneTable(0f, 60f)
        assertTrue(t[64] < 64)
        assertTrue(t[192] > 192)
    }

    @Test fun warmAndTintShiftsAreScaledAndLimited() {
        assertEquals(40f, ToneMath.warmShift(100f), 0.001f)
        assertEquals(-40f, ToneMath.warmShift(-300f), 0.001f)
        assertEquals(30f, ToneMath.greenShift(100f), 0.001f)
        assertEquals(0f, ToneMath.greenShift(0f), 0f)
    }

    @Test fun cleanDocumentTurnsPaperWhiteAndInkBlack() {
        val pixels = intArrayOf(0xFFF2F0E8.toInt(), 0xFF202428.toInt(), 0xFF808080.toInt())
        ToneMath.cleanDocument(pixels)
        assertEquals(0xFFFFFFFF.toInt(), pixels[0])
        assertEquals(0xFF000000.toInt(), pixels[1])
        val mid = pixels[2] and 0xFF
        assertTrue("mid grey stays near the middle, was $mid", mid in 120..140)
    }

    @Test fun cleanDocumentKeepsAlphaAndMakesGrey() {
        val pixels = intArrayOf(0x80FF8040.toInt())
        ToneMath.cleanDocument(pixels)
        val p = pixels[0]
        assertEquals(0x80, (p ushr 24) and 0xFF)
        assertEquals((p shr 16) and 0xFF, (p shr 8) and 0xFF)
        assertEquals((p shr 8) and 0xFF, p and 0xFF)
    }
}
