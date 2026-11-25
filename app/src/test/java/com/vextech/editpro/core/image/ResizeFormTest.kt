package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResizeFormTest {
    private val start = ResizeForm.start(4000, 3000)

    private fun ok(form: ResizeForm) = form.parse(4000, 3000) as ResizeParse.Ok
    private fun err(form: ResizeForm) = form.parse(4000, 3000) as ResizeParse.Error

    @Test fun startsAtThePhotosOwnSize() {
        val r = ok(start)
        assertEquals(4000, r.spec.width); assertEquals(3000, r.spec.height)
        assertFalse(r.enlarged); assertFalse(r.needsConfirm)
    }

    @Test fun customPixelsAreUsedAsTyped() {
        val r = ok(start.copy(width = "800", height = "600"))
        assertEquals(800, r.spec.width); assertEquals(600, r.spec.height)
    }

    @Test fun commaDecimalsAreAccepted() {
        val r = ok(start.copy(unit = ResizeUnit.CM, width = "5,0", height = "5,0"))
        assertEquals(591, r.spec.width)
    }

    @Test fun percentIsAShareOfThePhoto() {
        val r = ok(start.copy(unit = ResizeUnit.PERCENT, width = "50", height = "50"))
        assertEquals(2000, r.spec.width); assertEquals(1500, r.spec.height)
    }

    @Test fun blankOrWrongNumbersAreRefused() {
        assertEquals(ResizeProblem.NOT_A_NUMBER, err(start.copy(width = "")).problem)
        assertEquals(ResizeProblem.NOT_A_NUMBER, err(start.copy(height = "abc")).problem)
        assertEquals(ResizeProblem.NOT_A_NUMBER, err(start.copy(width = "0")).problem)
    }

    @Test fun tooSmallAndTooBigAreRefused() {
        assertEquals(ResizeProblem.TOO_SMALL, err(start.copy(width = "10", height = "10")).problem)
        assertEquals(ResizeProblem.TOO_BIG, err(start.copy(width = "9000", height = "100")).problem)
        assertEquals(ResizeProblem.TOO_BIG, err(start.copy(width = "7000", height = "7000")).problem)
    }

    @Test fun makingThePhotoLargerIsFlagged() {
        assertTrue(ok(start.copy(width = "6000", height = "4500")).enlarged)
        assertFalse(ok(start.copy(width = "4100", height = "3075")).enlarged)
    }

    @Test fun anIdPresetUses300DpiAndNeedsTheTick() {
        val f = start.withGroup(PresetGroup.ID).withPreset("us-passport")
        val r = ok(f)
        assertEquals(600, r.spec.width); assertEquals(600, r.spec.height)
        assertTrue(r.needsConfirm)
        assertFalse(ok(f.copy(confirmed = true)).needsConfirm)
    }

    @Test fun aFormPresetKeepsTheFileUnderItsLimit() {
        val r = ok(start.withGroup(PresetGroup.FORMS).withPreset("form-200x230"))
        assertEquals(200, r.spec.width); assertEquals(230, r.spec.height)
        assertEquals(50 * 1024L, r.spec.maxBytes)
    }

    @Test fun printAndSocialSizesNeedNoTick() {
        assertFalse(ok(start.withGroup(PresetGroup.PRINT).withPreset("p-4x6")).needsConfirm)
        val social = ok(start.withGroup(PresetGroup.SOCIAL).withPreset("ig-square"))
        assertEquals(1080, social.spec.width); assertNull(social.spec.maxBytes)
    }

    @Test fun choosingAGroupPicksItsFirstSizeAndCustomClearsIt() {
        val g = start.withGroup(PresetGroup.PRINT)
        assertEquals("wallet", g.presetId)
        assertEquals("", g.withGroup(null).presetId)
    }

    @Test fun idSizesAlwaysCropToFill() {
        val f = start.copy(mode = FitMode.FIT).withGroup(PresetGroup.ID)
        assertEquals(FitMode.FILL, f.mode)
    }

    @Test fun keepShapeFollowsTheOtherSide() {
        val f = start.copy(width = "2000").withSide(changedWidth = true, 4000, 3000)
        assertEquals("1500", f.height)
        val g = start.copy(height = "600").withSide(changedWidth = false, 4000, 3000)
        assertEquals("800", g.width)
    }

    @Test fun keepShapeOffLeavesTheOtherSide() {
        val f = start.copy(lockShape = false, width = "2000").withSide(true, 4000, 3000)
        assertEquals("3000", f.height)
    }

    @Test fun changingUnitKeepsTheSameSize() {
        val f = start.copy(width = "600", height = "300").withUnit(ResizeUnit.IN, 4000, 3000)
        assertEquals("2", f.width); assertEquals("1", f.height)
        val back = f.withUnit(ResizeUnit.PX, 4000, 3000)
        assertEquals("600", back.width); assertEquals("300", back.height)
    }

    @Test fun backgroundColourIsParsed() {
        assertEquals(0xFF000000.toInt(), ok(start.copy(background = "#000000")).spec.background)
        assertEquals(0xFFF2EAD8.toInt(), ok(start.copy(background = "#f2ead8")).spec.background)
    }
}
