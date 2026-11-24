package com.vextech.editpro.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropLayoutTest {
    private val box = PageBox(0.0, 0.0, 600.0, 800.0)
    private val m = Margins(top = 10.0, right = 20.0, bottom = 30.0, left = 40.0)

    @Test fun trimOnAnUnrotatedPage() {
        val b = CropMath.cropBoxFor(box, 0, m)
        assertEquals(PageBox(40.0, 30.0, 540.0, 760.0), b)
    }

    @Test fun trimFollowsPageRotation() {
        // Rotated 90: the visible top edge is the unrotated left edge, the visible right edge is the unrotated top edge.
        assertEquals(PageBox(10.0, 40.0, 560.0, 740.0), CropMath.cropBoxFor(box, 90, m))
        assertEquals(PageBox(20.0, 10.0, 540.0, 760.0), CropMath.cropBoxFor(box, 180, m))
        assertEquals(PageBox(30.0, 20.0, 560.0, 740.0), CropMath.cropBoxFor(box, 270, m))
        assertEquals(CropMath.cropBoxFor(box, 0, m), CropMath.cropBoxFor(box, 360, m))
        assertEquals(CropMath.cropBoxFor(box, 270, m), CropMath.cropBoxFor(box, -90, m))
    }

    @Test fun trimKeepsTheBoxOrigin() {
        val b = CropMath.cropBoxFor(PageBox(100.0, 50.0, 600.0, 800.0), 0, Margins(0.0, 0.0, 0.0, 10.0))
        assertEquals(110.0, b.x, 0.0001)
        assertEquals(50.0, b.y, 0.0001)
    }

    @Test fun refusesATrimThatLeavesNothing() {
        assertTrue(CropMath.leavesRoom(box, 0, m))
        assertFalse(CropMath.leavesRoom(box, 0, Margins(0.0, 300.0, 0.0, 300.0)))
        assertFalse(CropMath.leavesRoom(box, 0, Margins(400.0, 0.0, 395.0, 0.0)))
    }

    @Test fun visualSizeSwapsForSidewaysPages() {
        assertEquals(600.0 to 800.0, CropMath.visualSize(box, 0))
        assertEquals(800.0 to 600.0, CropMath.visualSize(box, 90))
        assertEquals(600.0 to 800.0, CropMath.visualSize(box, 180))
    }

    @Test fun sheetOrientation() {
        assertEquals(595.28 to 841.89, CropMath.sheetSize(Paper.A4, SheetOrientation.AUTO, 600.0, 800.0))
        assertEquals(841.89 to 595.28, CropMath.sheetSize(Paper.A4, SheetOrientation.AUTO, 800.0, 600.0))
        assertEquals(841.89 to 595.28, CropMath.sheetSize(Paper.A4, SheetOrientation.LANDSCAPE, 600.0, 800.0))
        assertEquals(595.28 to 841.89, CropMath.sheetSize(Paper.A4, SheetOrientation.PORTRAIT, 800.0, 600.0))
    }

    @Test fun fitCentresAndKeepsProportions() {
        val f = CropMath.fitOnSheet(300.0, 400.0, 600.0, 900.0, 0.0)
        assertEquals(2.0, f.scale, 0.0001)
        assertEquals(600.0, f.width, 0.0001)
        assertEquals(800.0, f.height, 0.0001)
        assertEquals(0.0, f.offsetX, 0.0001)
        assertEquals(50.0, f.offsetY, 0.0001)
    }

    @Test fun fitRespectsTheMargin() {
        val f = CropMath.fitOnSheet(600.0, 800.0, 600.0, 800.0, 50.0)
        assertEquals(500.0 / 600.0, f.scale, 0.0001)
        assertEquals(50.0, f.offsetX, 0.0001)
        assertEquals((800.0 - 800.0 * 500.0 / 600.0) / 2.0, f.offsetY, 0.0001)
    }

    @Test fun marginMustLeaveRoomOnThePaper() {
        assertTrue(CropMath.marginFitsPaper(Paper.A4, 14.17))
        assertFalse(CropMath.marginFitsPaper(Paper.A4, 300.0))
    }

    @Test fun trimFormReadsMillimetres() {
        val r = CropForm(top = "10", left = "2,5").parse(5) as CropParse.Ok
        val t = r.options as CropOptions.Trim
        assertEquals(10 * CropMath.POINTS_PER_MM, t.margins.top, 0.0001)
        assertEquals(2.5 * CropMath.POINTS_PER_MM, t.margins.left, 0.0001)
        assertEquals(0.0, t.margins.right, 0.0)
        assertNull(t.pages)
    }

    @Test fun blankMarginCountsAsZeroButTextIsRefused() {
        assertTrue(CropForm(top = "").parse(3) is CropParse.Ok)
        assertEquals(CropProblem.MARGIN_NUMBER, (CropForm(top = "abc").parse(3) as CropParse.Error).problem)
        assertEquals(CropProblem.MARGIN_NUMBER, (CropForm(left = "-1").parse(3) as CropParse.Error).problem)
    }

    @Test fun pagesAreChecked() {
        val ok = CropForm(pages = "2-3, 1").parse(5) as CropParse.Ok
        assertEquals(listOf(0, 1, 2), ok.options.pages)
        assertEquals(CropProblem.PAGES_PAST_END, (CropForm(pages = "9").parse(5) as CropParse.Error).problem)
        assertEquals(CropProblem.PAGES_NOT_A_RANGE, (CropForm(pages = "x").parse(5) as CropParse.Error).problem)
        assertEquals(CropProblem.PAGES_INVALID, (CropForm(pages = "4-2").parse(5) as CropParse.Error).problem)
    }

    @Test fun resizeFormChecksTheMargin() {
        val ok = CropForm(mode = CropMode.RESIZE, paper = Paper.LETTER, margin = "5").parse(2) as CropParse.Ok
        val r = ok.options as CropOptions.Resize
        assertEquals(Paper.LETTER, r.paper)
        assertEquals(5 * CropMath.POINTS_PER_MM, r.marginPoints, 0.0001)
        assertEquals(CropProblem.PAPER_MARGIN_TOO_LARGE, (CropForm(mode = CropMode.RESIZE, margin = "500").parse(2) as CropParse.Error).problem)
        assertEquals(CropProblem.PAPER_MARGIN_NUMBER, (CropForm(mode = CropMode.RESIZE, margin = "x").parse(2) as CropParse.Error).problem)
    }

    @Test fun resizeIgnoresTrimBoxesEvenIfInvalid() {
        assertTrue(CropForm(mode = CropMode.RESIZE, top = "abc").parse(2) is CropParse.Ok)
    }
}
