package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageTest {
    @Test fun colsAreAsSquareAsPossible() {
        assertEquals(1, Collage.gridCols(1)); assertEquals(2, Collage.gridCols(2)); assertEquals(2, Collage.gridCols(4))
        assertEquals(3, Collage.gridCols(5)); assertEquals(4, Collage.gridCols(12))
    }

    @Test fun gridOfFourIsTwoByTwo() {
        val l = Collage.grid(4, 2, 1.0, 1000.0, 0.0, 0.0)
        assertEquals(1000, l.w); assertEquals(1000, l.h)
        assertEquals(CollageCell(0, 0, 500, 500), l.cells[0]); assertEquals(CollageCell(500, 500, 500, 500), l.cells[3])
    }

    @Test fun gapAndPaddingAreKept() {
        val l = Collage.grid(2, 2, 1.0, 1000.0, 20.0, 20.0)
        assertEquals(20, l.cells[0].x)
        assertEquals(470, l.cells[0].w)
        assertEquals(510, l.cells[1].x)
    }

    @Test fun unfinishedLastRowIsCentred() {
        val l = Collage.grid(3, 2, 1.0, 1000.0, 0.0, 0.0)
        assertEquals(250, l.cells[2].x)
        assertEquals(500, l.cells[2].y)
    }

    @Test fun columnsAreLimitedToThePhotoCount() {
        val l = Collage.grid(2, 6, 1.0, 600.0, 0.0, 0.0)
        assertEquals(300, l.cells[0].w)
    }

    @Test fun rowKeepsEachPhotosShapeAtOneHeight() {
        val l = Collage.row(listOf(2.0, 0.5), 100.0, 10.0, 5.0)
        assertEquals(90, l.cells[0].h); assertEquals(90, l.cells[1].h)
        assertEquals(180, l.cells[0].w); assertEquals(45, l.cells[1].w)
        assertEquals(100, l.h)
        assertEquals(5 + 180 + 10 + 45 + 5, l.w)
    }

    @Test fun columnKeepsEachPhotosShapeAtOneWidth() {
        val l = Collage.column(listOf(2.0, 0.5), 100.0, 10.0, 5.0)
        assertEquals(90, l.cells[0].w); assertEquals(45, l.cells[0].h); assertEquals(180, l.cells[1].h)
        assertEquals(5 + 45 + 10 + 180 + 5, l.h)
    }

    @Test fun scalingScalesEverything() {
        val l = Collage.scale(Collage.grid(4, 2, 1.0, 1000.0, 0.0, 0.0), 0.5)
        assertEquals(500, l.w); assertEquals(250, l.cells[0].w)
    }

    @Test fun limitShrinksATooBigPicture() {
        val l = Collage.limit(CollageLayout(16000, 4000, listOf(CollageCell(0, 0, 16000, 4000))))
        assertTrue(l.w <= Collage.MAX_SIDE && l.w.toLong() * l.h <= Collage.MAX_PIXELS)
    }

    @Test fun limitLeavesAnOkPictureAlone() {
        val l = CollageLayout(2000, 2000, listOf(CollageCell(0, 0, 2000, 2000)))
        assertEquals(l, Collage.limit(l))
    }

    @Test fun formGridUsesAutomaticColumns() {
        val l = CollageForm(gapPct = 0.0).layout(List(4) { 1.0 }, 1000)
        assertEquals(2, l.cells.map { it.x }.distinct().size)
    }

    @Test fun formRowIsScaledToTheWidthAsked() {
        val l = CollageForm(kind = CollageKind.ROW).layout(listOf(1.5, 1.0, 0.75), 2000)
        assertEquals(2000, l.w)
        assertEquals(3, l.cells.size)
    }

    @Test fun formColumnHasTheWidthAsked() = assertEquals(1080, CollageForm(kind = CollageKind.COLUMN).layout(listOf(1.0, 1.5), 1080).w)

    @Test fun wholePhotoOnlyAppliesToTheGrid() {
        assertEquals(FitMode.FIT, CollageForm(fit = FitMode.FIT).fitFor())
        assertEquals(FitMode.FILL, CollageForm(kind = CollageKind.ROW, fit = FitMode.FIT).fitFor())
    }

    @Test fun startsUnchanged() = assertTrue(!CollageForm().changed)

    @Test fun nothingToLayOutIsStillSafe() = assertEquals(0, CollageForm().layout(emptyList(), 1000).cells.size)
}
