package com.vextech.editpro.core.pdf

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfImageDecodeTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun flate(data: ByteArray): ByteArray {
        val d = Deflater(); d.setInput(data); d.finish()
        val out = ByteArrayOutputStream(); val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf)); d.end(); return out.toByteArray()
    }
    private fun rgbSpec(w: Int, h: Int, filters: List<String> = listOf("FlateDecode"), bpc: Int = 8, space: ColorSpec = ColorSpec(3), predictor: Int = 1, colors: Int = 3, columns: Int = w) =
        RawImageSpec(w, h, bpc, space, filters, predictor, colors, 8, columns)

    @Test fun pngEncoderMakesAFileJavaCanReadBack() {
        val data = bytes(255, 0, 0, 0, 255, 0, 0, 0, 255, 10, 20, 30)
        val png = PdfImageDecode.encodePng(2, 2, 3, data)
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(2, img.width); assertEquals(2, img.height)
        assertEquals(0xFF0000, img.getRGB(0, 0) and 0xFFFFFF)
        assertEquals(0x00FF00, img.getRGB(1, 0) and 0xFFFFFF)
        assertEquals(0x0000FF, img.getRGB(0, 1) and 0xFFFFFF)
        assertEquals(0x0A141E, img.getRGB(1, 1) and 0xFFFFFF)
    }

    @Test fun pngEncoderKeepsGrayAndAlpha() {
        val gray = ImageIO.read(ByteArrayInputStream(PdfImageDecode.encodePng(2, 1, 1, bytes(0, 200))))
        assertEquals(200, gray.raster.getSample(1, 0, 0))
        val rgba = ImageIO.read(ByteArrayInputStream(PdfImageDecode.encodePng(1, 1, 4, bytes(10, 20, 30, 128))))
        assertEquals(128, rgba.getRGB(0, 0) ushr 24)
    }

    @Test fun decodesRawAndFlateRgb() {
        val px = bytes(1, 2, 3, 4, 5, 6)
        assertTrue(px.contentEquals(PdfImageDecode.decodeRaw(rgbSpec(2, 1, emptyList()), px)!!.data))
        val flated = PdfImageDecode.decodeRaw(rgbSpec(2, 1), flate(px))!!
        assertEquals(3, flated.channels); assertTrue(px.contentEquals(flated.data))
    }

    @Test fun undoesPngPredictors() {
        // One row each: Sub (1), Up (2) on 1-channel gray, 3 columns.
        val sub = PdfImageDecode.undoPngPredictor(bytes(1, 10, 5, 5), 1, 8, 3)!!
        assertTrue(bytes(10, 15, 20).contentEquals(sub))
        val up = PdfImageDecode.undoPngPredictor(bytes(0, 1, 2, 3, 2, 1, 1, 1), 1, 8, 3)!!
        assertTrue(bytes(1, 2, 3, 2, 3, 4).contentEquals(up))
        assertNull(PdfImageDecode.undoPngPredictor(bytes(9, 1, 2, 3), 1, 8, 3))
        val spec = rgbSpec(3, 1, predictor = 11, colors = 1, space = ColorSpec(1), columns = 3)
        assertTrue(bytes(10, 15, 20).contentEquals(PdfImageDecode.decodeRaw(spec, flate(bytes(1, 10, 5, 5)))!!.data))
    }

    @Test fun undoesTiffPredictor() {
        assertTrue(bytes(10, 15, 20).contentEquals(PdfImageDecode.undoTiffPredictor(bytes(10, 5, 5), 1, 3)))
    }

    @Test fun unpacksLowBitSamples() {
        // 1-bit, 8 wide: 10110000 -> 255,0,255,255,0,0,0,0 ; as palette indexes (raw) they stay 1/0.
        assertTrue(bytes(255, 0, 255, 255, 0, 0, 0, 0).contentEquals(PdfImageDecode.unpackSamples(bytes(0b10110000), 8, 1, 1, 1, false)))
        assertTrue(bytes(1, 0, 1, 1, 0, 0, 0, 0).contentEquals(PdfImageDecode.unpackSamples(bytes(0b10110000), 8, 1, 1, 1, true)))
        // 4-bit gray: 0xF0 -> 255, 0
        assertTrue(bytes(255, 0).contentEquals(PdfImageDecode.unpackSamples(bytes(0xF0), 2, 1, 1, 4, false)))
        // 16-bit keeps the high byte
        assertTrue(bytes(0x12, 0xAB).contentEquals(PdfImageDecode.unpackSamples(bytes(0x12, 0x34, 0xAB, 0xCD), 2, 1, 1, 16, false)))
    }

    @Test fun expandsPalettesAndHonoursHival() {
        val table = bytes(255, 0, 0, 0, 255, 0)            // two RGB entries
        val spec = RawImageSpec(3, 1, 8, ColorSpec(1, Palette(table, 1, 3)), emptyList())
        val pic = PdfImageDecode.decodeRaw(spec, bytes(0, 1, 9))!!           // index 9 clamps to hival 1
        assertEquals(3, pic.channels)
        assertTrue(bytes(255, 0, 0, 0, 255, 0, 0, 255, 0).contentEquals(pic.data))
    }

    @Test fun convertsCmykToRgbAndInvertsDecode() {
        val cmyk = RawImageSpec(2, 1, 8, ColorSpec(4), emptyList())
        val pic = PdfImageDecode.decodeRaw(cmyk, bytes(0, 0, 0, 0, 0, 0, 0, 255))!!      // white, black
        assertEquals(3, pic.channels)
        assertTrue(bytes(255, 255, 255, 0, 0, 0).contentEquals(pic.data))
        val inv = RawImageSpec(2, 1, 8, ColorSpec(1), emptyList(), decodeInverted = true)
        assertTrue(bytes(255, 0).contentEquals(PdfImageDecode.decodeRaw(inv, bytes(0, 255))!!.data))
    }

    @Test fun refusesWhatItCannotDoFaithfully() {
        assertNull(PdfImageDecode.decodeRaw(rgbSpec(1, 1, listOf("LZWDecode")), bytes(1, 2, 3)))
        assertNull(PdfImageDecode.decodeRaw(rgbSpec(1, 1, listOf("FlateDecode", "ASCIIHexDecode")), bytes(1, 2, 3)))
        assertNull(PdfImageDecode.decodeRaw(rgbSpec(1, 1), bytes(1, 2, 3)))                       // not valid Flate
        assertNull(PdfImageDecode.decodeRaw(rgbSpec(1, 1, emptyList(), bpc = 3), bytes(1, 2, 3)))
        assertNull(PdfImageDecode.decodeRaw(rgbSpec(8000, 8000, emptyList()), bytes(1)))          // over the pixel limit
        assertNull(PdfImageDecode.decodeRaw(rgbSpec(1, 1, emptyList(), space = ColorSpec(3)), bytes(1, 2, 3), grayOnly = true))
        assertNull(PdfImageDecode.decodeRaw(rgbSpec(1, 1, predictor = 5), flate(bytes(1, 2, 3))))
    }

    @Test fun addsSoftMaskAsAlphaAndScalesTheMask() {
        val pic = DecodedPicture(2, 2, 1, bytes(10, 20, 30, 40))
        val mask = DecodedPicture(1, 1, 1, bytes(77))
        val out = PdfImageDecode.withAlpha(pic, mask)
        assertEquals(4, out.channels)
        assertTrue(bytes(10, 10, 10, 77, 20, 20, 20, 77, 30, 30, 30, 77, 40, 40, 40, 77).contentEquals(out.data))
        val rgb = PdfImageDecode.withAlpha(DecodedPicture(1, 1, 3, bytes(1, 2, 3)), DecodedPicture(1, 1, 1, bytes(5)))
        assertTrue(bytes(1, 2, 3, 5).contentEquals(rgb.data))
    }

    @Test fun readsJpegDimensionsAndComponents() {
        // SOI, APP0 (length 4), SOF0: precision 8, height 0x0102, width 0x0304, 3 components
        val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x04, 0x00, 0x00, 0xFF, 0xC0, 0x00, 0x11, 0x08, 0x01, 0x02, 0x03, 0x04, 0x03, 0, 0, 0, 0)
        assertEquals(PdfImageDecode.JpegInfo(width = 0x0304, height = 0x0102, comps = 3), PdfImageDecode.jpegInfo(jpeg))
        assertNull(PdfImageDecode.jpegInfo(bytes(1, 2, 3, 4, 5)))
        assertNull(PdfImageDecode.jpegInfo(bytes(0xFF, 0xD8)))
    }

    @Test fun readsPaletteStreams() {
        assertTrue(bytes(1, 2, 3).contentEquals(PdfImageDecode.paletteBytes(emptyList(), bytes(1, 2, 3))!!))
        assertTrue(bytes(1, 2, 3).contentEquals(PdfImageDecode.paletteBytes(listOf("FlateDecode"), flate(bytes(1, 2, 3)))!!))
        assertNull(PdfImageDecode.paletteBytes(listOf("LZWDecode"), bytes(1)))
    }
}
