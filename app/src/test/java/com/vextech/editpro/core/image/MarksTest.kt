package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarksTest {
    private val a = DrawPoint(0.1f, 0.1f)
    private val b = DrawPoint(0.5f, 0.6f)

    @Test fun strokeFollowsThePictureSize() {
        assertEquals(13, MarkMath.strokePx(1000, 4))
        assertEquals(2, MarkMath.strokePx(100, 1))
    }

    @Test fun levelsAreLimited() {
        assertEquals(MarkMath.strokePx(900, 10), MarkMath.strokePx(900, 99))
        assertEquals(MarkMath.strokePx(900, 1), MarkMath.strokePx(900, -3))
    }

    @Test fun fontFollowsThePictureSize() {
        assertEquals(60, MarkMath.fontPx(1000, 5))
        assertEquals(10, MarkMath.fontPx(100, 1))
    }

    @Test fun arrowHeadSitsBehindTheTip() {
        val h = MarkMath.arrowHead(0.0, 0.0, 100.0, 0.0, 5.0)
        assertTrue(h.x1 < 100.0 && h.x2 < 100.0)
        assertEquals(-h.y1, h.y2, 0.0001)
    }

    @Test fun penDraftGrowsThenIsKept() {
        val f = MarkForm().down(a).move(b).move(DrawPoint(0.7f, 0.7f)).up()
        assertNull(f.draft)
        assertEquals(1, f.marks.size)
        assertEquals(3, f.marks[0].points.size)
    }

    @Test fun aShapeKeepsFirstAndLastPoint() {
        val f = MarkForm(tool = MarkTool.BOX).down(a).move(DrawPoint(0.3f, 0.3f)).move(b).up()
        assertEquals(listOf(a, b), f.marks.single().points)
    }

    @Test fun tinyShapeIsDropped() {
        val f = MarkForm(tool = MarkTool.ARROW).down(a).move(DrawPoint(0.101f, 0.1f)).up()
        assertTrue(f.marks.isEmpty())
    }

    @Test fun aDotWithThePenIsKept() = assertEquals(1, MarkForm().down(a).up().marks.size)

    @Test fun draftIsShownWhileDrawing() {
        val f = MarkForm(tool = MarkTool.OVAL).down(a).move(b)
        assertEquals(1, f.shown.size)
        assertFalse(f.canApply)
    }

    @Test fun textGoesWhereYouTap() {
        val f = MarkForm(tool = MarkTool.TEXT, text = "Hello").tap(b)
        val m = f.marks.single()
        assertEquals("Hello", m.text); assertEquals(listOf(b), m.points); assertEquals(5, m.level)
    }

    @Test fun emptyTextAsksForText() {
        val f = MarkForm(tool = MarkTool.TEXT, text = "  ").tap(b)
        assertTrue(f.needText); assertTrue(f.marks.isEmpty())
    }

    @Test fun tapDoesNothingForOtherTools() = assertTrue(MarkForm().tap(b).marks.isEmpty())

    @Test fun undoRemovesTheLastMark() {
        val f = MarkForm().down(a).up().down(b).up().undoLast()
        assertEquals(1, f.marks.size)
        assertTrue(f.canApply)
    }

    @Test fun clearedStartsOver() = assertFalse(MarkForm().down(a).up().cleared().canApply)

    @Test fun changingToolDropsADraft() = assertNull(MarkForm().down(a).withTool(MarkTool.BOX).draft)

    @Test fun pointsStayInsideThePicture() {
        val p = DrawPoint(-0.2f, 1.4f).clamped()
        assertEquals(0f, p.x, 0f); assertEquals(1f, p.y, 0f)
    }
}
