package com.vextech.editpro.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WatermarkLayoutTest {
    private val eps = 1e-6

    @Test fun defaultsAreValidForText() {
        assertNull(WatermarkLayout.validate(WatermarkDraft(), hasLogo = false))
    }

    @Test fun rejectsBadDrafts() {
        fun p(d: WatermarkDraft, logo: Boolean = false) = WatermarkLayout.validate(d, logo)
        assertEquals(WatermarkProblem.EMPTY_TEXT, p(WatermarkDraft(text = "   ")))
        assertEquals(WatermarkProblem.UNSUPPORTED_CHAR, p(WatermarkDraft(text = "Привет")))
        assertEquals(WatermarkProblem.BAD_COLOR, p(WatermarkDraft(colorHex = "red")))
        assertEquals(WatermarkProblem.NO_LOGO, p(WatermarkDraft(kind = WatermarkKind.LOGO)))
        assertNull(p(WatermarkDraft(kind = WatermarkKind.LOGO), logo = true))
        assertEquals(WatermarkProblem.OPACITY, p(WatermarkDraft(opacityPct = 4)))
        assertEquals(WatermarkProblem.SIZE, p(WatermarkDraft(sizePct = 101)))
        assertEquals(WatermarkProblem.MARGIN, p(WatermarkDraft(marginPct = 51)))
        assertEquals(WatermarkProblem.GAP, p(WatermarkDraft(gapPct = 1001)))
    }

    @Test fun westernTextWithQuotesAndEuroIsAccepted() {
        assertNull(WatermarkLayout.validate(WatermarkDraft(text = "Café “draft” €5"), false))
    }

    @Test fun parsesHexColours() {
        val (r, g, b) = WatermarkLayout.parseHex("#cc0000")!!
        assertEquals(0.8f, r, 0.001f); assertEquals(0f, g, 0f); assertEquals(0f, b, 0f)
        assertNotNull(WatermarkLayout.parseHex("2563eb"))
        assertNull(WatermarkLayout.parseHex("#12345"))
        assertNull(WatermarkLayout.parseHex("#gggggg"))
    }

    @Test fun footprintOfTurnedBox() {
        val (fw, fh) = WatermarkLayout.footprint(200.0, 100.0, 90.0)
        assertEquals(100.0, fw, eps); assertEquals(200.0, fh, eps)
        val (w0, h0) = WatermarkLayout.footprint(200.0, 100.0, 0.0)
        assertEquals(200.0, w0, eps); assertEquals(100.0, h0, eps)
    }

    @Test fun cornerForUnturnedBoxIsPlainOffset() {
        val c = WatermarkLayout.cornerFor(300.0, 400.0, 100.0, 40.0, 0.0)
        assertEquals(250.0, c.x, eps); assertEquals(380.0, c.y, eps)
    }

    @Test fun cornerForTurnedBoxStaysOnTheSameCentre() {
        // The box centre is the corner plus half the box turned by the angle.
        val deg = 45.0
        val c = WatermarkLayout.cornerFor(300.0, 400.0, 100.0, 40.0, deg)
        val t = Math.toRadians(deg)
        val cx = c.x + 50 * Math.cos(t) - 20 * Math.sin(t)
        val cy = c.y + 50 * Math.sin(t) + 20 * Math.cos(t)
        assertEquals(300.0, cx, eps); assertEquals(400.0, cy, eps)
    }

    @Test fun singleMarkSpotsFollowTheMargin() {
        // 600 x 800 page, 100 x 20 mark, margin 3% of the short side (600) = 18.
        val tl = WatermarkLayout.placeMark(600.0, 800.0, 100.0, 20.0, WatermarkPos.TL, 3.0)
        assertEquals(18.0, tl.x, eps); assertEquals(18.0, tl.y, eps)
        val br = WatermarkLayout.placeMark(600.0, 800.0, 100.0, 20.0, WatermarkPos.BR, 3.0)
        assertEquals(482.0, br.x, eps); assertEquals(762.0, br.y, eps)
        val mc = WatermarkLayout.placeMark(600.0, 800.0, 100.0, 20.0, WatermarkPos.MC, 3.0)
        assertEquals(250.0, mc.x, eps); assertEquals(390.0, mc.y, eps)
    }

    @Test fun centreOfASingleMarkIsInPageUpCoordinates() {
        val c = WatermarkLayout.markCentres(600.0, 800.0, 100.0, 20.0, 0.0, WatermarkPos.TL, 3.0, 60.0).single()
        assertEquals(68.0, c.x, eps)          // 18 + 50
        assertEquals(800.0 - 28.0, c.y, eps)  // top edge is Y = 800, mark centre is 28 below it
        val mid = WatermarkLayout.markCentres(600.0, 800.0, 100.0, 20.0, 45.0, WatermarkPos.MC, 3.0, 60.0).single()
        assertEquals(300.0, mid.x, eps); assertEquals(400.0, mid.y, eps)
    }

    @Test fun tilesStepByMarkSizePlusGapAndStaggerEveryOtherRow() {
        val pts = WatermarkLayout.tilePoints(400.0, 200.0, 100.0, 50.0, 100.0)   // step 200 x 100
        assertEquals(listOf(0.0, 200.0), pts.filter { it.y == 0.0 }.map { it.x })
        // Row 1 is shifted left by half a step (-100). A mark that would end exactly at the area's left edge is dropped, so it starts at 100.
        assertEquals(listOf(100.0, 300.0), pts.filter { it.y == 100.0 }.map { it.x })
    }

    @Test fun repeatCoversThePageAndKeepsOnlyMarksNearIt() {
        val c = WatermarkLayout.markCentres(595.0, 842.0, 150.0, 40.0, 45.0, WatermarkPos.TILE, 3.0, 60.0)
        assertTrue(c.size in 10..WatermarkLayout.MAX_TILES_PER_PAGE)
        val (fw, fh) = WatermarkLayout.footprint(150.0, 40.0, 45.0)
        assertTrue(c.all { it.x >= -fw / 2 - eps && it.x <= 595 + fw / 2 + eps && it.y >= -fh / 2 - eps && it.y <= 842 + fh / 2 + eps })
    }

    @Test fun tinyRepeatIsRefused() {
        try {
            WatermarkLayout.markCentres(595.0, 842.0, 4.0, 3.0, 0.0, WatermarkPos.TILE, 3.0, 0.0)
            fail("expected TooManyMarksException")
        } catch (_: TooManyMarksException) { }
    }

    @Test fun markSizeNeverExceedsNinetyPercentOfPageHeight() {
        val (w, h) = WatermarkLayout.markSize(600.0, 800.0, 0.5, 100)      // twice as tall as wide: 1200 tall at full width
        assertEquals(720.0, h, eps); assertEquals(360.0, w, eps)
        val (w2, h2) = WatermarkLayout.markSize(600.0, 800.0, 5.0, 60)
        assertEquals(360.0, w2, eps); assertEquals(72.0, h2, eps)
    }

    @Test fun screenToPageFollowsPageRotation() {
        // Box 600 wide, 800 high before rotation.
        fun at(rot: Int) = PageSpace.screenToPage(600.0, 800.0, rot, 10.0, 20.0)
        assertEquals(PagePoint(10.0, 20.0), at(0))
        assertEquals(PagePoint(580.0, 10.0), at(90))
        assertEquals(PagePoint(590.0, 780.0), at(180))
        assertEquals(PagePoint(20.0, 790.0), at(270))
        assertEquals(PagePoint(10.0, 20.0), at(360))
    }

    @Test fun visualSizeSwapsOnSidewaysPages() {
        assertEquals(600.0 to 800.0, PageSpace.visualSize(600.0, 800.0, 0))
        assertEquals(800.0 to 600.0, PageSpace.visualSize(600.0, 800.0, 90))
        assertEquals(800.0 to 600.0, PageSpace.visualSize(600.0, 800.0, 270))
        assertEquals(600.0 to 800.0, PageSpace.visualSize(600.0, 800.0, 180))
    }

    @Test fun winAnsiKnowsWesternLettersAndNotOthers() {
        assertNull(WinAnsi.firstUnsupported("Hello, Müller – 5 € “ok”"))
        assertEquals('Ж', WinAnsi.firstUnsupported("abcЖ"))
        assertEquals('学', WinAnsi.firstUnsupported("学"))
    }

    @Test fun textLayoutFitsTheRequestedWidthAndCentresOnThePage() {
        // Text 5 units wide at size 1, 60% of a 600-wide page: size = 360 / 5 = 72, so the mark is 360 wide and 72 * 0.72 tall.
        val l = WatermarkLayout.layout(600.0, 800.0, WatermarkDraft(sizePct = 60, pos = WatermarkPos.MC, angle = 0), 5.0, 1.0)
        assertEquals(72.0, l.size, eps); assertEquals(360.0, l.w, eps); assertEquals(72.0 * 0.72, l.h, eps)
        assertEquals(300.0, l.centres.single().x, eps); assertEquals(400.0, l.centres.single().y, eps)
    }

    @Test fun textSizeIsLimitedTo600Points() {
        val l = WatermarkLayout.layout(2000.0, 3000.0, WatermarkDraft(sizePct = 100), 0.5, 1.0)
        assertEquals(600.0, l.size, eps)
    }

    @Test fun logoLayoutKeepsAspectAndNeverExceedsPageHeight() {
        val wide = WatermarkLayout.layout(600.0, 800.0, WatermarkDraft(kind = WatermarkKind.LOGO, sizePct = 50), 1.0, 2.0)
        assertEquals(300.0, wide.w, eps); assertEquals(150.0, wide.h, eps)
        val tall = WatermarkLayout.layout(600.0, 800.0, WatermarkDraft(kind = WatermarkKind.LOGO, sizePct = 100), 1.0, 0.5)
        assertEquals(720.0, tall.h, eps); assertEquals(360.0, tall.w, eps)
    }

    @Test fun layoutRefusesATinyRepeat() {
        try {
            WatermarkLayout.layout(595.0, 842.0, WatermarkDraft(pos = WatermarkPos.TILE, sizePct = 5, gapPct = 0), 40.0, 1.0)
            fail("expected TooManyMarksException")
        } catch (_: TooManyMarksException) { }
    }
}
