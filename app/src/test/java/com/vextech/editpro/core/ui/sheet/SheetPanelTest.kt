package com.vextech.editpro.core.ui.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetPanelTest {
    @Test fun phoneInPortraitKeepsTheBottomSheet() {
        assertFalse(SheetPanel.useSidePanel(360, 800))
        assertFalse(SheetPanel.useSidePanel(411, 914))
    }

    @Test fun phoneInLandscapeGetsTheSidePanel() {
        assertTrue(SheetPanel.useSidePanel(800, 360))
        assertTrue(SheetPanel.useSidePanel(640, 360))
    }

    @Test fun veryShortLandscapeStaysBottomWhenTooNarrow() {
        assertFalse(SheetPanel.useSidePanel(540, 300))     // under 560 dp wide: a panel would squeeze the page too much
    }

    @Test fun tabletsGetThePanelInEitherOrientation() {
        assertTrue(SheetPanel.useSidePanel(1280, 800))
        assertTrue(SheetPanel.useSidePanel(900, 1400))     // 840 dp or wider, even in portrait
        assertFalse(SheetPanel.useSidePanel(800, 1280))    // a small tablet in portrait: the bottom sheet has room
    }

    @Test fun panelIsAtMost360AndAtMost45PercentOfTheWidth() {
        assertEquals(360, SheetPanel.panelWidthDp(1280))
        assertEquals(360, SheetPanel.panelWidthDp(800))
        assertEquals(288, SheetPanel.panelWidthDp(640))
        assertEquals(252, SheetPanel.panelWidthDp(560))
    }
}
