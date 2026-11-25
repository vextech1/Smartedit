package com.vextech.editpro.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramesTest {
    @Test fun shadeLightensAndDarkens() {
        assertEquals(0xFFFFFFFF.toInt(), Frames.shade(0xFFFFFFFF.toInt(), 0.5))
        assertEquals(0xFF000000.toInt(), Frames.shade(0xFF000000.toInt(), -0.5))
        assertEquals(0xFF808080.toInt(), Frames.shade(0xFF000000.toInt(), 0.5))
        assertEquals(0xFF646464.toInt(), Frames.shade(0xFFC8C8C8.toInt(), -0.5))
    }

    @Test fun openingKeepsThePhotosShapeByDefault() = assertEquals(1.5, Frames.openingRatio("photo", 3000, 2000), 0.0001)

    @Test fun landscapePhotoTurnsAPortraitSizeSideways() = assertEquals(1.5, Frames.openingRatio("4x6", 3000, 2000), 0.0001)

    @Test fun portraitPhotoKeepsAPortraitSize() = assertEquals(4.0 / 6, Frames.openingRatio("4x6", 2000, 3000), 0.0001)

    @Test fun squareSizeStaysSquare() = assertEquals(1.0, Frames.openingRatio("square", 3000, 2000), 0.0001)

    @Test fun unknownSizeUsesThePhotoShape() = assertEquals(2.0, Frames.openingRatio("zzz", 400, 200), 0.0001)

    @Test fun wallLayoutAddsFrameAndMatAroundThePicture() {
        val l = Frames.wallLayout(1000, 500, "photo", 5, 8, WallBackdrop.NONE, 1000.0)
        // Picture 1000 x 500; the shorter side is 500, so frame = 25 and mat = 40.
        assertEquals(25, l.fw); assertEquals(40, l.mw)
        assertEquals(1000 + 2 * 65, l.w); assertEquals(500 + 2 * 65, l.h)
        assertEquals(0, l.pad); assertEquals(65, l.px); assertEquals(65, l.py)
    }

    @Test fun wallAddsMarginAroundTheFrame() {
        val l = Frames.wallLayout(1000, 500, "photo", 5, 8, WallBackdrop.LIGHT, 1000.0)
        assertEquals(136, l.pad)
        assertEquals(l.pad + l.fw + l.mw, l.px)
    }

    @Test fun noMatMeansNoMatWidth() = assertEquals(0, Frames.wallLayout(1000, 500, "photo", 5, 0, WallBackdrop.NONE, 1000.0).mw)

    @Test fun wallLongIsCapped() {
        assertEquals(2400.0, Frames.wallLong(4000, 3000), 0.0)
        assertEquals(800.0, Frames.wallLong(800, 600), 0.0)
    }

    @Test fun phoneScreenWidthIsLimited() {
        assertEquals(1170, Frames.phoneScreenWidth(4000))
        assertEquals(400, Frames.phoneScreenWidth(100))
        assertEquals(900, Frames.phoneScreenWidth(900))
    }

    @Test fun phoneBodyHoldsTheScreen() {
        val l = Frames.phoneLayout(PhoneStyle.ISLAND, 1000, true)
        assertEquals(1000, l.sw)
        assertEquals(2165, l.sh)
        assertTrue(l.bodyW > l.sw && l.bodyH > l.sh)
        assertEquals(l.pad + 32, l.sx)
    }

    @Test fun classicPhoneHasBiggerTopAndBottom() {
        val modern = Frames.phoneLayout(PhoneStyle.ISLAND, 1000, false)
        val classic = Frames.phoneLayout(PhoneStyle.CLASSIC, 1000, false)
        assertTrue(classic.sy - classic.by > modern.sy - modern.by)
    }

    @Test fun backgroundGivesMoreRoomRoundThePhone() =
        assertTrue(Frames.phoneLayout(PhoneStyle.NOTCH, 800, true).pad > Frames.phoneLayout(PhoneStyle.NOTCH, 800, false).pad)

    @Test fun outputSizeMatchesTheLayout() {
        val spec = WallFrameSpec(wall = WallBackdrop.NONE)
        val l = Frames.wallLayout(1000, 500, "photo", 5, 8, WallBackdrop.NONE, 1000.0)
        assertEquals(l.w to l.h, Frames.outputSize(spec, 1000, 500))
        val phone = PhoneFrameSpec()
        val p = Frames.phoneLayout(phone.style, 900, true)
        assertEquals(p.w to p.h, Frames.outputSize(phone, 900, 1600))
    }

    @Test fun onlyAPhoneWithNoBackgroundIsTransparent() {
        assertTrue(Frames.isTransparent(PhoneFrameSpec(background = PhoneBackground.NONE)))
        assertFalse(Frames.isTransparent(PhoneFrameSpec()))
        assertFalse(Frames.isTransparent(WallFrameSpec(wall = WallBackdrop.NONE)))
    }

    @Test fun formStartsAsABlackWallFrame() {
        val f = FrameForm()
        assertFalse(f.changed)
        assertEquals(WallStyle.BLACK, (f.spec() as WallFrameSpec).style)
    }

    @Test fun formSwitchesKind() {
        val f = FrameForm(kind = FrameKind.PHONE)
        assertTrue(f.spec() is PhoneFrameSpec); assertTrue(f.changed)
    }

    @Test fun everySizeHasABothDimensionsOrKeepsThePhoto() {
        Frames.SIZES.forEach { assertTrue(it.id, (it.w == 0 && it.h == 0) || (it.w > 0 && it.h > 0)) }
    }
}
