package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnTest {
    @Test fun noneChangesNothing() = assertFalse(Turn.NONE.isTurned)

    @Test fun quarterTurnsWrapBothWays() {
        assertEquals(1, Turn.NONE.turnBy(1).turns)
        assertEquals(3, Turn.NONE.turnBy(-1).turns)
        assertEquals(0, Turn.NONE.turnBy(4).turns)
        assertEquals(2, Turn.NONE.turnBy(1).turnBy(1).turns)
    }

    @Test fun fourTurnsLeaveThePictureAsItWas() = assertFalse(Turn.NONE.turnBy(1).turnBy(1).turnBy(1).turnBy(1).isTurned)

    @Test fun aFlipCountsAsChange() {
        assertTrue(Turn.NONE.flipOnScreen(true).isTurned)
        assertFalse(Turn.NONE.flipOnScreen(true).flipOnScreen(true).isTurned)
    }

    @Test fun flipsOnTheUprightPhotoUseTheirOwnAxis() {
        assertTrue(Turn.NONE.flipOnScreen(horizontal = true).flipH)
        assertTrue(Turn.NONE.flipOnScreen(horizontal = false).flipV)
    }

    @Test fun afterAQuarterTurnTheMirrorGoesOnTheOtherPhotoAxis() {
        val turned = Turn.NONE.turnBy(1)
        assertTrue("left-right on screen is the photo's vertical axis", turned.flipOnScreen(horizontal = true).flipV)
        assertTrue("top-bottom on screen is the photo's horizontal axis", turned.flipOnScreen(horizontal = false).flipH)
    }

    @Test fun afterAHalfTurnTheAxesAreTheSameAgain() {
        val turned = Turn.NONE.turnBy(2)
        assertTrue(turned.flipOnScreen(horizontal = true).flipH)
    }

    @Test fun aQuarterTurnSwapsWidthAndHeight() {
        assertEquals(300 to 400, Turn.NONE.turnBy(1).turnedSize(400, 300))
        assertEquals(400 to 300, Turn.NONE.turnBy(2).turnedSize(400, 300))
        assertEquals(300 to 400, Turn.NONE.turnBy(3).turnedSize(400, 300))
    }
}
