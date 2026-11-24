package com.vextech.editpro.core.pdf

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pixel work for "Extract images": rebuild raw PDF picture data as PNG. A port of the site's `pdf-images.ts`, so both
 * give the same files. Pure Kotlin (no PDF library, no Android), so it is unit-tested on the JVM.
 * JPEG and JPEG 2000 pictures are copied byte for byte elsewhere; this file handles Flate/raw pixels: palettes, CMYK,
 * 1/2/4/16-bit samples, PNG and TIFF predictors, and soft-mask transparency. Fax (CCITT/JBIG2), LZW and exotic colour
 * spaces are reported as unsupported rather than guessed at.
 */

/** A decoded picture: 8-bit interleaved pixels, 1 channel = gray, 3 = RGB, 4 = RGBA. */
class DecodedPicture(val width: Int, val height: Int, val channels: Int, val data: ByteArray)

/** An Indexed colour table. [baseComps] is the component count of the table's own colour space (1, 3 or 4). */
class Palette(val bytes: ByteArray, val hival: Int, val baseComps: Int)

/** A colour space reduced to what extraction needs: component count (1, 3 or 4), or a palette (then comps is 1). */
class ColorSpec(val comps: Int, val palette: Palette? = null)

/** Everything read from a picture's dictionary that decoding depends on. Filters are the names in order, e.g. [FlateDecode]. */
class RawImageSpec(
    val width: Int,
    val height: Int,
    val bitsPerComponent: Int,
    val space: ColorSpec,
    val filters: List<String>,
    val predictor: Int = 1,
    val predictorColors: Int = 1,
    val predictorBits: Int = 8,
    val predictorColumns: Int = 1,
    /** `/Decode [1 0]`: the samples are inverted. */
    val decodeInverted: Boolean = false,
)

/** Counts of what an extraction left out. */
data class ExtractSkips(val tiny: Int = 0, val unsupported: Int = 0, val masks: Int = 0)

object PdfImageDecode {
    /** Largest picture (in pixels) rebuilt in memory. The site allows 100 million; a phone's memory does not. */
    const val MAX_PIXELS = 25_000_000L

    private fun Byte.u() = toInt() and 0xFF

    // ---- PNG encoder ---------------------------------------------------------------------------------------------

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArray(12 + data.size)
        fun putInt(at: Int, v: Long) { for (i in 0..3) out[at + i] = (v shr (24 - 8 * i)).toByte() }
        putInt(0, data.size.toLong())
        for (i in 0..3) out[4 + i] = type[i].code.toByte()
        System.arraycopy(data, 0, out, 8, data.size)
        val crc = CRC32().apply { update(out, 4, 4 + data.size) }
        putInt(8 + data.size, crc.value)
        return out
    }

    /** 8-bit PNG from interleaved pixels: 1 channel = gray, 3 = RGB, 4 = RGBA. */
    fun encodePng(width: Int, height: Int, channels: Int, data: ByteArray): ByteArray {
        require(channels == 1 || channels == 3 || channels == 4)
        val stride = width * channels
        val raw = ByteArray((stride + 1) * height)                       // filter byte stays 0
        for (y in 0 until height) System.arraycopy(data, y * stride, raw, y * (stride + 1) + 1, stride)
        val ihdr = ByteArray(13)
        fun putInt(at: Int, v: Int) { for (i in 0..3) ihdr[at + i] = (v shr (24 - 8 * i)).toByte() }
        putInt(0, width); putInt(4, height)
        ihdr[8] = 8
        ihdr[9] = (if (channels == 1) 0 else if (channels == 3) 2 else 6).toByte()
        val deflater = Deflater(6)
        val compressed = ByteArrayOutputStream()
        deflater.setInput(raw); deflater.finish()
        val buf = ByteArray(65536)
        while (!deflater.finished()) compressed.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        out.write(chunk("IHDR", ihdr)); out.write(chunk("IDAT", compressed.toByteArray())); out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    // ---- pixel decoding ------------------------------------------------------------------------------------------

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a); val pb = abs(p - b); val pc = abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    /** Undo the PNG predictors (Predictor 10-15) that PDF producers apply before Flate. Null when a row is corrupt. */
    fun undoPngPredictor(data: ByteArray, colors: Int, bpc: Int, columns: Int): ByteArray? {
        val bpp = max(1, ceil(colors * bpc / 8.0).toInt())
        val rowLen = ceil(colors.toLong() * bpc * columns / 8.0).toInt()
        if (rowLen <= 0) return null
        val rows = data.size / (rowLen + 1)
        val out = ByteArray(rows * rowLen)
        for (r in 0 until rows) {
            val ft = data[r * (rowLen + 1)].u()
            val src = r * (rowLen + 1) + 1
            val o = r * rowLen
            if (ft > 4) return null
            for (i in 0 until rowLen) {
                val x = data[src + i].u()
                val a = if (i >= bpp) out[o + i - bpp].u() else 0
                val b = if (r > 0) out[o - rowLen + i].u() else 0
                val c = if (r > 0 && i >= bpp) out[o - rowLen + i - bpp].u() else 0
                val v = when (ft) { 0 -> x; 1 -> x + a; 2 -> x + b; 3 -> x + ((a + b) shr 1); else -> x + paeth(a, b, c) }
                out[o + i] = (v and 255).toByte()
            }
        }
        return out
    }

    /** TIFF predictor 2, 8-bit samples only. */
    fun undoTiffPredictor(data: ByteArray, colors: Int, columns: Int): ByteArray {
        val rowLen = colors * columns
        val out = data.copyOf()
        if (rowLen <= 0) return out
        var r = 0
        while (r + rowLen <= out.size) {
            for (i in colors until rowLen) out[r + i] = ((out[r + i].u() + out[r + i - colors].u()) and 255).toByte()
            r += rowLen
        }
        return out
    }

    /** Samples of 1/2/4/8/16 bits become one byte each. [raw] keeps palette indexes as they are instead of scaling to 0-255. */
    fun unpackSamples(data: ByteArray, w: Int, h: Int, comps: Int, bpc: Int, raw: Boolean): ByteArray {
        val stride = ceil(w.toLong() * comps * bpc / 8.0).toInt()
        val n = w * comps
        val out = ByteArray(n * h)
        val maxValue = (1 shl min(bpc, 8)) - 1
        fun at(i: Int) = if (i < data.size) data[i].u() else 0
        for (y in 0 until h) for (i in 0 until n) {
            var v: Int
            if (bpc == 8) v = at(y * stride + i)
            else if (bpc == 16) v = at(y * stride + i * 2)
            else {
                val bit = i * bpc
                v = (at(y * stride + (bit shr 3)) shr (8 - bpc - (bit and 7))) and maxValue
                if (!raw) v = (v * 255.0 / maxValue).roundToInt()
            }
            out[y * n + i] = v.toByte()
        }
        return out
    }

    private fun cmykToRgb(c: Int, m: Int, y: Int, k: Int, out: ByteArray, o: Int) {
        val kk = 1 - k / 255.0
        out[o] = (255 * (1 - c / 255.0) * kk).roundToInt().toByte()
        out[o + 1] = (255 * (1 - m / 255.0) * kk).roundToInt().toByte()
        out[o + 2] = (255 * (1 - y / 255.0) * kk).roundToInt().toByte()
    }

    /** Dimensions and component count from a JPEG's start-of-frame marker, or null when it isn't a JPEG. */
    data class JpegInfo(val width: Int, val height: Int, val comps: Int)

    fun jpegInfo(b: ByteArray): JpegInfo? {
        if (b.size < 4 || b[0].u() != 0xFF || b[1].u() != 0xD8) return null
        var i = 2
        while (i + 9 < b.size) {
            if (b[i].u() != 0xFF) { i++; continue }
            val m = b[i + 1].u()
            if (m == 0xFF) { i++; continue }
            if (m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) {
                return JpegInfo(width = (b[i + 7].u() shl 8) or b[i + 8].u(), height = (b[i + 5].u() shl 8) or b[i + 6].u(), comps = b[i + 9].u())
            }
            if (m == 0xD8 || m in 0xD0..0xD7 || m == 0x01) { i += 2; continue }
            i += 2 + ((b[i + 2].u() shl 8) or b[i + 3].u())
        }
        return null
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(65536)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null   // truncated or needs a dictionary
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } catch (_: Exception) { null } finally { inflater.end() }
    }

    /** A Indexed colour table stored as a stream: raw, or a single Flate filter. Anything else can't be read. */
    fun paletteBytes(filters: List<String>, contents: ByteArray): ByteArray? = when {
        filters.isEmpty() -> contents
        filters.size == 1 && filters[0] == "FlateDecode" -> inflate(contents)
        else -> null
    }

    /** Decode a raw-pixel picture to 8-bit pixels, or null when it can't be done faithfully. [grayOnly] is for soft masks. */
    fun decodeRaw(spec: RawImageSpec, contents: ByteArray, grayOnly: Boolean = false): DecodedPicture? {
        val w = spec.width
        val h = spec.height
        val bpc = spec.bitsPerComponent
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS || bpc !in listOf(1, 2, 4, 8, 16)) return null
        val sp = spec.space
        if (grayOnly && (sp.comps != 1 || sp.palette != null)) return null
        var data: ByteArray
        if (spec.filters.isEmpty()) data = contents
        else if (spec.filters.size == 1 && (spec.filters[0] == "FlateDecode" || spec.filters[0] == "Fl")) {
            data = inflate(contents) ?: return null
            if (spec.predictor >= 10) data = undoPngPredictor(data, spec.predictorColors, spec.predictorBits, spec.predictorColumns) ?: return null
            else if (spec.predictor == 2) { if (spec.predictorBits != 8) return null; data = undoTiffPredictor(data, spec.predictorColors, spec.predictorColumns) }
            else if (spec.predictor != 1) return null
        } else return null

        val comps = sp.comps
        val px = unpackSamples(data, w, h, comps, bpc, sp.palette != null)
        if (spec.decodeInverted && sp.palette == null && comps != 4) for (i in px.indices) px[i] = (255 - px[i].u()).toByte()
        val palette = sp.palette
        if (palette != null) {
            val ch = if (palette.baseComps == 1) 1 else 3
            val out = ByteArray(w * h * ch)
            fun at(i: Int) = if (i < palette.bytes.size) palette.bytes[i].u() else 0
            for (i in 0 until w * h) {
                val e = min(px[i].u(), palette.hival) * palette.baseComps
                val o = i * ch
                when (palette.baseComps) {
                    1 -> out[o] = at(e).toByte()
                    3 -> { out[o] = at(e).toByte(); out[o + 1] = at(e + 1).toByte(); out[o + 2] = at(e + 2).toByte() }
                    else -> cmykToRgb(at(e), at(e + 1), at(e + 2), at(e + 3), out, o)
                }
            }
            return DecodedPicture(w, h, ch, out)
        }
        if (comps == 4) {
            val out = ByteArray(w * h * 3)
            for (i in 0 until w * h) cmykToRgb(px[i * 4].u(), px[i * 4 + 1].u(), px[i * 4 + 2].u(), px[i * 4 + 3].u(), out, i * 3)
            return DecodedPicture(w, h, 3, out)
        }
        return DecodedPicture(w, h, comps, px)
    }

    /** Add [alpha] (a gray soft mask, any size) to a gray or RGB picture, scaling the mask by nearest neighbour. Result is RGBA. */
    fun withAlpha(pic: DecodedPicture, alpha: DecodedPicture): DecodedPicture {
        val w = pic.width
        val h = pic.height
        val rgba = ByteArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val o = i * 4
            val ax = min(alpha.width - 1, (x.toLong() * alpha.width / w).toInt())
            val ay = min(alpha.height - 1, (y.toLong() * alpha.height / h).toInt())
            if (pic.channels == 1) { rgba[o] = pic.data[i]; rgba[o + 1] = pic.data[i]; rgba[o + 2] = pic.data[i] }
            else { rgba[o] = pic.data[i * 3]; rgba[o + 1] = pic.data[i * 3 + 1]; rgba[o + 2] = pic.data[i * 3 + 2] }
            rgba[o + 3] = alpha.data[ay * alpha.width + ax]
        }
        return DecodedPicture(w, h, 4, rgba)
    }
}
