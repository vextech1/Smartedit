package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColourMathTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test fun hexIsLowerCaseSixDigits() = assertEquals("#0a0b0c", ColourMath.hex(0xFF0A0B0C.toInt()))

    @Test fun parsesLongAndShortHex() {
        assertEquals(0xFFAABBCC.toInt(), ColourMath.parseHex("#aabbcc"))
        assertEquals(0xFFAABBCC.toInt(), ColourMath.parseHex("ABC"))
        assertEquals(0xFF112233.toInt(), ColourMath.parseHex(" 112233 "))
    }

    @Test fun badHexIsNull() {
        assertNull(ColourMath.parseHex("#12"))
        assertNull(ColourMath.parseHex("zzzzzz"))
    }

    @Test fun colourIsClamped() = assertEquals(0xFFFF0000.toInt(), ColourMath.color(300, -4, 0))

    @Test fun hslOfPrimaries() {
        assertEquals(Triple(0, 100, 50), ColourMath.hsl(red))
        assertEquals(Triple(120, 100, 50), ColourMath.hsl(0xFF00FF00.toInt()))
        assertEquals(Triple(240, 100, 50), ColourMath.hsl(blue))
    }

    @Test fun hslOfGreysHasNoHueOrSaturation() {
        assertEquals(Triple(0, 0, 100), ColourMath.hsl(0xFFFFFFFF.toInt()))
        assertEquals(Triple(0, 0, 0), ColourMath.hsl(0xFF000000.toInt()))
        assertEquals(Triple(0, 0, 50), ColourMath.hsl(0xFF808080.toInt()))
    }

    @Test fun hueOfMagentaIsNotNegative() = assertEquals(300, ColourMath.hsl(0xFFFF00FF.toInt()).first)

    @Test fun readableTextColour() {
        assertEquals(0xFF000000.toInt(), ColourMath.textOn(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), ColourMath.textOn(0xFF000000.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), ColourMath.textOn(0xFF1F2B46.toInt()))
        assertEquals(0xFF000000.toInt(), ColourMath.textOn(0xFFFFD24A.toInt()))
    }

    @Test fun textForms() {
        assertEquals("rgb(255, 0, 0)", ColourMath.rgbText(red))
        assertEquals("hsl(0, 100%, 50%)", ColourMath.hslText(red))
    }

    @Test fun twoColoursComeBackMostCommonFirst() {
        val px = IntArray(100) { if (it < 70) blue else red }
        assertEquals(listOf(blue, red), ColourMath.extract(px, 6))
    }

    @Test fun oneColourGivesOneEntry() = assertEquals(listOf(red), ColourMath.extract(IntArray(50) { red }, 6))

    @Test fun seeThroughPixelsAreIgnored() {
        val px = IntArray(10) { if (it < 8) 0x00FF0000 else blue }
        assertEquals(listOf(blue), ColourMath.extract(px, 6))
    }

    @Test fun nothingOpaqueGivesNothing() = assertTrue(ColourMath.extract(IntArray(10) { 0 }).isEmpty())

    @Test fun neverMoreThanAskedFor() {
        val px = IntArray(600) { ColourMath.color(it % 256, (it * 7) % 256, (it * 13) % 256) }
        assertTrue(ColourMath.extract(px, 4).size <= 4)
    }

    @Test fun keepsEachColourOnce() {
        val f = PickerForm(current = red).keep()
        assertFalse(f.canKeep)
        assertEquals(1, f.keep().kept.size)
    }

    @Test fun keepIsLimited() {
        var f = PickerForm()
        for (i in 0 until ColourMath.KEEP + 5) f = f.use(ColourMath.color(i, 0, 0)).keep()
        assertEquals(ColourMath.KEEP, f.kept.size)
    }
}
