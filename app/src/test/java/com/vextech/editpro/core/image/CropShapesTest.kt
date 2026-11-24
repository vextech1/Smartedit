package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropShapesTest {
    @Test fun freeHasNoRatio() = assertNull(CropShape.FREE.ratioFor(1.5f))

    @Test fun originalUsesThePhotosOwnShape() {
        assertEquals(1.5f, CropShape.ORIGINAL.ratioFor(1.5f)!!, 0.0001f)
        assertEquals(0.75f, CropShape.ORIGINAL.ratioFor(0.75f)!!, 0.0001f)
    }

    @Test fun originalIgnoresAnInvalidShape() = assertNull(CropShape.ORIGINAL.ratioFor(0f))

    @Test fun fixedShapesMatchTheSiteList() {
        assertEquals(1f, CropShape.SQUARE.ratioFor(2f)!!, 0f)
        assertEquals(0.8f, CropShape.R4_5.ratioFor(2f)!!, 0.0001f)
        assertEquals(1.5f, CropShape.R3_2.ratioFor(2f)!!, 0.0001f)
        assertEquals(16f / 9f, CropShape.R16_9.ratioFor(2f)!!, 0.0001f)
        assertEquals(9f / 16f, CropShape.R9_16.ratioFor(2f)!!, 0.0001f)
    }

    @Test fun appShapesAreKept() {
        assertEquals(4f / 3f, CropShape.R4_3.ratioFor(1f)!!, 0.0001f)
        assertEquals(3f / 4f, CropShape.R3_4.ratioFor(1f)!!, 0.0001f)
    }

    @Test fun onlyFreeAndOriginalUseWords() {
        assertNull(CropShape.FREE.ratioLabel)
        assertNull(CropShape.ORIGINAL.ratioLabel)
        CropShape.entries.filter { it != CropShape.FREE && it != CropShape.ORIGINAL }.forEach { assert(it.ratioLabel != null) }
    }
}
