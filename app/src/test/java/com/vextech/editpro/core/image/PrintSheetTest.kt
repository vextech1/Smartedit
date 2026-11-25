package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintSheetTest {
    private val fourBySix = PrintSheets.papers.first { it.id == "4x6" }
    private val a4 = PrintSheets.papers.first { it.id == "a4" }

    @Test fun sixSmallPassportPhotosFitFourBySix() {
        val l = PrintSheets.best(fourBySix, 35.0, 45.0)
        assertEquals(6, l.count)
    }

    @Test fun layoutIsCentred() {
        val l = PrintSheets.layout(210.0, 297.0, 50.0, 50.0)
        val usedW = l.cols * 50.0 + (l.cols - 1) * PrintSheets.GAP_MM
        assertEquals((210.0 - usedW) / 2, l.x0, 0.0001)
    }

    @Test fun picksSidewaysWhenItFitsMore() {
        val l = PrintSheets.best(a4, 200.0, 90.0)
        assertTrue(l.count >= 3)
    }

    @Test fun tooBigPhotoFitsNone() = assertEquals(0, PrintSheets.best(fourBySix, 200.0, 200.0).count)

    @Test fun zeroSizeFitsNone() = assertEquals(0, PrintSheets.layout(210.0, 297.0, 0.0, 10.0).count)

    @Test fun copiesZeroFillsThePage() {
        val l = PrintSheets.best(a4, 35.0, 45.0)
        assertEquals(l.count, PrintSheets.photosOn(l, 0))
    }

    @Test fun copiesNeverExceedWhatFits() {
        val l = PrintSheets.best(fourBySix, 35.0, 45.0)
        assertEquals(l.count, PrintSheets.photosOn(l, 99))
        assertEquals(2, PrintSheets.photosOn(l, 2))
    }

    @Test fun offersOnlyPhysicalSizes() {
        assertTrue(PrintSheets.sizes.isNotEmpty())
        assertTrue(PrintSheets.sizes.none { it.unit == SizeUnit.PX })
        assertTrue(PrintSheets.sizes.none { it.group == PresetGroup.FORMS || it.group == PresetGroup.SOCIAL })
    }

    @Test fun inchSizesConvertToMillimetres() {
        val us = PrintSheets.sizeMm(SizePresets.byId("us-passport")!!)
        assertEquals(50.8, us.first, 0.001); assertEquals(50.8, us.second, 0.001)
    }

    @Test fun formStartsReadyWithASize() {
        val f = PrintSheetForm()
        assertNotNull(f.layout()); assertTrue(f.ready)
    }

    @Test fun typedSizeIsUsedWhenNoPresetIsPicked() {
        val f = PrintSheetForm(sizeId = "", widthMm = "35", heightMm = "45")
        assertEquals(35.0 to 45.0, f.cellMm())
    }

    @Test fun commaDecimalsAreRead() = assertEquals(37.5, PrintSheetForm(sizeId = "", widthMm = "37,5", heightMm = "45").cellMm()!!.first, 0.0)

    @Test fun badTypedSizeMakesNoSheet() {
        val f = PrintSheetForm(sizeId = "", widthMm = "abc", heightMm = "45")
        assertNull(f.cellMm()); assertFalse(f.ready)
    }

    @Test fun oversizeMakesNoSheet() = assertFalse(PrintSheetForm(sizeId = "", widthMm = "500", heightMm = "500").ready)

    @Test fun unknownPaperFallsBackToTheFirst() = assertEquals("4x6", PrintSheetForm(paperId = "zzz").paper.id)
}
