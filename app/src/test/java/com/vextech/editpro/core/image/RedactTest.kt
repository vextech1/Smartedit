package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun box(l: Float, t: Float, r: Float, b: Float) = HideBox(l, t, r, b, HideKind.BLACK, 5)

    @Test fun rectIsRoundedAndKeptInside() {
        val r = Redact.rectFor(box(-0.2f, 0.25f, 0.5f, 1.4f), 100, 200)!!
        assertEquals(PixelRect(0, 50, 50, 150), r)
    }

    @Test fun rectWorksWhenDraggedBackwards() = assertEquals(PixelRect(10, 20, 30, 40), Redact.rectFor(box(0.4f, 0.3f, 0.1f, 0.1f), 100, 200))

    @Test fun nothingLeftGivesNull() = assertNull(Redact.rectFor(box(0.5f, 0.5f, 0.5f, 0.9f), 100, 100))

    @Test fun strengthScalesWithThePicture() {
        assertEquals(60, Redact.strengthPx(1500, 6, HideKind.PIXELATE))
        assertEquals(60, Redact.strengthPx(1000, 6, HideKind.BLUR))
        assertEquals(4, Redact.strengthPx(50, 1, HideKind.PIXELATE))
        assertEquals(3, Redact.strengthPx(50, 1, HideKind.BLUR))
        assertEquals(0, Redact.strengthPx(1000, 9, HideKind.BLACK))
    }

    @Test fun strengthIsLimited() = assertEquals(Redact.strengthPx(1000, 10, HideKind.BLUR), Redact.strengthPx(1000, 50, HideKind.BLUR))

    @Test fun blackKeepsNothing() {
        val px = IntArray(16) { rgb(it * 10, 200, 50) }
        Redact.apply(px, 4, 4, HideKind.BLACK, 5, 100)
        assertTrue(px.all { it == 0xFF000000.toInt() })
    }

    @Test fun pixelateMakesEachBlockOneColour() {
        val px = IntArray(16) { rgb(if (it % 4 < 2) 0 else 200, 0, 0) }
        Redact.pixelate(px, 4, 4, 2)
        assertEquals(px[0], px[1]); assertEquals(px[0], px[4]); assertEquals(px[0], px[5])
        assertEquals(rgb(0, 0, 0), px[0]); assertEquals(rgb(200, 0, 0), px[2])
    }

    @Test fun pixelateAveragesTheBlock() {
        val px = intArrayOf(rgb(0, 0, 0), rgb(100, 0, 0), rgb(0, 0, 0), rgb(100, 0, 0))
        Redact.pixelate(px, 2, 2, 2)
        assertEquals(rgb(50, 0, 0), px[3])
    }

    @Test fun pixelateHandlesEdgesThatDoNotDivide() {
        val px = IntArray(15) { rgb(it, 0, 0) }
        Redact.pixelate(px, 5, 3, 2)
        assertEquals(px[4], px[9])
    }

    @Test fun blurKeepsAFlatColourFlat() {
        val px = IntArray(36) { rgb(80, 120, 160) }
        Redact.blur(px, 6, 6, 2)
        assertTrue(px.all { it == rgb(80, 120, 160) })
    }

    @Test fun blurSpreadsABrightDot() {
        val px = IntArray(81) { rgb(0, 0, 0) }
        px[40] = rgb(255, 255, 255)
        Redact.blur(px, 9, 9, 1)
        assertTrue((px[40] shr 16 and 0xFF) < 255)
        assertNotEquals(rgb(0, 0, 0), px[41])
    }

    @Test fun blurLeavesOnlyOpaquePixels() {
        val px = IntArray(25) { rgb(it * 9, 0, 0) }
        Redact.blur(px, 5, 5, 1)
        assertTrue(px.all { (it ushr 24) == 0xFF })
    }

    private val a = DrawPoint(0.1f, 0.1f)

    @Test fun dragMakesABox() {
        val f = HideForm().down(a).move(DrawPoint(0.5f, 0.4f)).up()
        val b = f.boxes.single()
        assertEquals(0.1f, b.left, 0f); assertEquals(0.5f, b.right, 0f); assertEquals(0.4f, b.bottom, 0f)
        assertFalse(f.dragging)
    }

    @Test fun dragBackwardsStillMakesABox() {
        val b = HideForm().down(DrawPoint(0.6f, 0.6f)).move(a).up().boxes.single()
        assertTrue(b.left < b.right && b.top < b.bottom)
    }

    @Test fun strayTapIsIgnored() = assertTrue(HideForm().down(a).move(DrawPoint(0.105f, 0.4f)).up().boxes.isEmpty())

    @Test fun boxRemembersTheWayChosenWhenDrawn() {
        val f = HideForm(kind = HideKind.BLUR, strength = 3).down(a).move(DrawPoint(0.5f, 0.5f)).up()
        assertEquals(HideKind.BLUR, f.boxes.single().kind); assertEquals(3, f.boxes.single().strength)
    }

    @Test fun undoAndClear() {
        val f = HideForm().down(a).move(DrawPoint(0.5f, 0.5f)).up().down(a).move(DrawPoint(0.9f, 0.9f)).up()
        assertEquals(1, f.undoLast().boxes.size)
        assertFalse(f.cleared().canApply)
    }
}
