package com.vextech.editpro.core.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSBoolean
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSObject
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

/** Something worth telling the person about a saved picture. */
enum class ExtractNote { CMYK_JPEG, ALPHA_LOST }

/** One picture found in the PDF. [format] is `jpg`, `png` or `jp2`. */
class ExtractedImage(
    val name: String,
    val page: Int,
    val format: String,
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val note: ExtractNote? = null,
)

class ExtractOutcome(val pages: Int, val truncated: Boolean, val skips: ExtractSkips)

/** A picture saved to disk and listed to the person. */
class ExtractedImageInfo(val file: File, val page: Int, val width: Int, val height: Int, val format: String)

/**
 * What an extraction produced. [listed] are the first pictures as separate files; [zip] holds all of them (only when
 * there is more than one). The PDF itself is not changed.
 */
class ImageExtraction(
    val total: Int,
    val pages: Int,
    val truncated: Boolean,
    val skips: ExtractSkips,
    val listed: List<ExtractedImageInfo>,
    val zip: File?,
    val cmykJpegs: Int,
    val alphaLost: Int,
)

/**
 * Walks a PDF's pages and form XObjects for embedded pictures. A port of the site's `extractImages`.
 * JPEG and JPEG 2000 are copied out byte for byte; Flate/raw pictures are rebuilt as PNG by [PdfImageDecode].
 * This file only reads the PDF's objects; every decision about pixels lives in the pure, tested [PdfImageDecode].
 */
object PdfImageExtractor {
    const val MAX_IMAGES = 500

    private fun n(s: String) = COSName.getPDFName(s)
    private fun get(d: COSDictionary, key: String, abbreviation: String? = null): COSBase? =
        d.getDictionaryObject(n(key)) ?: abbreviation?.let { d.getDictionaryObject(n(it)) }

    private fun num(o: COSBase?): Int? = (o as? COSNumber)?.intValue()
    private fun nameOf(o: COSBase?): String? = (o as? COSName)?.name

    private fun filtersOf(d: COSDictionary): Pair<List<String>, COSDictionary?> {
        val f = get(d, "Filter", "F")
        val names = when (f) {
            is COSName -> listOf(f.name)
            is COSArray -> (0 until f.size()).map { nameOf(f.getObject(it)) ?: "?" }
            else -> emptyList()
        }
        val p = get(d, "DecodeParms", "DP")
        val parms = when (p) {
            is COSArray -> if (p.size() >= 1) p.getObject(0) as? COSDictionary else null
            is COSDictionary -> p
            else -> null
        }
        return names to parms
    }

    private fun rawBytes(s: COSStream): ByteArray = s.createRawInputStream().use { it.readBytes() }

    private fun spaceOf(cs: COSBase?, depth: Int = 0): ColorSpec? {
        nameOf(cs)?.let { name ->
            return when (name) {
                "DeviceGray", "G", "CalGray" -> ColorSpec(1)
                "DeviceRGB", "RGB", "CalRGB" -> ColorSpec(3)
                "DeviceCMYK", "CMYK" -> ColorSpec(4)
                else -> null
            }
        }
        if (cs !is COSArray || cs.size() < 1 || depth > 2) return null
        return when (nameOf(cs.getObject(0))) {
            "CalGray" -> ColorSpec(1)
            "CalRGB" -> ColorSpec(3)
            "ICCBased" -> {
                val s = cs.getObject(1) as? COSStream
                val comps = s?.let { num(get(it, "N")) }
                if (comps == 1 || comps == 3 || comps == 4) ColorSpec(comps) else null
            }
            "Indexed", "I" -> {
                val base = spaceOf(cs.getObject(1), depth + 1)
                val hival = num(cs.getObject(2))
                if (base == null || base.palette != null || hival == null) return null
                val bytes = when (val lookup = cs.getObject(3)) {
                    is COSString -> lookup.bytes
                    is COSStream -> PdfImageDecode.paletteBytes(filtersOf(lookup).first, rawBytes(lookup))
                    else -> null
                } ?: return null
                ColorSpec(1, Palette(bytes, hival, base.comps))
            }
            else -> null                       // Separation, DeviceN, Lab, Pattern...
        }
    }

    private fun specOf(d: COSDictionary): RawImageSpec? {
        val w = num(get(d, "Width", "W")) ?: return null
        val h = num(get(d, "Height", "H")) ?: return null
        val space = spaceOf(get(d, "ColorSpace", "CS")) ?: return null
        val (filters, parms) = filtersOf(d)
        val decode = get(d, "Decode", "D") as? COSArray
        val inverted = decode != null && decode.size() >= 2 &&
            (decode.getObject(0) as? COSNumber)?.floatValue() == 1f && (decode.getObject(1) as? COSNumber)?.floatValue() == 0f
        return RawImageSpec(
            width = w, height = h, bitsPerComponent = num(get(d, "BitsPerComponent", "BPC")) ?: 8, space = space, filters = filters,
            predictor = parms?.let { num(get(it, "Predictor")) } ?: 1,
            predictorColors = parms?.let { num(get(it, "Colors")) } ?: 1,
            predictorBits = parms?.let { num(get(it, "BitsPerComponent")) } ?: 8,
            predictorColumns = parms?.let { num(get(it, "Columns")) } ?: 1,
            decodeInverted = inverted,
        )
    }

    private fun decodeStream(s: COSStream, grayOnly: Boolean = false): DecodedPicture? {
        val spec = specOf(s) ?: return null
        return PdfImageDecode.decodeRaw(spec, rawBytes(s), grayOnly)
    }

    /**
     * Find every picture and hand each to [sink] as it is made, so memory holds one picture at a time.
     * Pictures under [minSize] pixels on a side are counted as tiny; stencil masks and unreadable kinds are counted too.
     */
    fun extract(doc: PDDocument, baseName: String, minSize: Int, maxImages: Int = MAX_IMAGES, sink: (ExtractedImage) -> Unit): ExtractOutcome {
        val seen = HashSet<String>()
        var tiny = 0
        var unsupported = 0
        var masks = 0
        var truncated = false
        var count = 0

        fun handleImage(ref: String?, s: COSStream, page: Int, perPage: IntArray) {
            if (ref != null && !seen.add(ref)) return
            val w = num(get(s, "Width"))
            val h = num(get(s, "Height"))
            if (w == null || h == null || w <= 0 || h <= 0) { unsupported++; return }
            if ((get(s, "ImageMask") as? COSBoolean)?.value == true) { masks++; return }
            if (w < minSize || h < minSize) { tiny++; return }
            if (count >= maxImages) { truncated = true; return }
            val filters = filtersOf(s).first
            val format: String
            val bytes: ByteArray
            var note: ExtractNote? = null
            if (filters.size == 1 && (filters[0] == "DCTDecode" || filters[0] == "DCT")) {
                val contents = rawBytes(s)
                val info = PdfImageDecode.jpegInfo(contents)
                if (info == null) { unsupported++; return }
                format = "jpg"; bytes = contents
                if (info.comps == 4) note = ExtractNote.CMYK_JPEG
            } else if (filters.size == 1 && filters[0] == "JPXDecode") {
                format = "jp2"; bytes = rawBytes(s)
            } else {
                var pic = decodeStream(s)
                if (pic == null) { unsupported++; return }
                val mask = get(s, "SMask") as? COSStream
                if (mask != null && (pic.channels == 1 || pic.channels == 3)) {
                    val alpha = decodeStream(mask, grayOnly = true)
                    if (alpha != null) pic = PdfImageDecode.withAlpha(pic, alpha) else note = ExtractNote.ALPHA_LOST
                }
                format = "png"; bytes = PdfImageDecode.encodePng(pic.width, pic.height, pic.channels, pic.data)
            }
            perPage[0]++
            count++
            sink(ExtractedImage("$baseName-page$page-image${perPage[0]}.$format", page, format, bytes, w, h, note))
        }

        fun walk(res: COSDictionary?, page: Int, perPage: IntArray, depth: Int) {
            val xo = res?.let { get(it, "XObject") } as? COSDictionary ?: return
            for (entry in xo.entrySet()) {
                val raw = entry.value
                val obj = if (raw is COSObject) raw.getObject() else raw
                val stream = obj as? COSStream ?: continue
                val ref = (raw as? COSObject)?.let { "${it.objectNumber} ${it.generationNumber}" }
                try {
                    when (nameOf(get(stream, "Subtype"))) {
                        "Image" -> handleImage(ref, stream, page, perPage)
                        "Form" -> if (depth < 4) {
                            if (ref != null && !seen.add("form:$ref:$page")) continue
                            walk(get(stream, "Resources") as? COSDictionary, page, perPage, depth + 1)
                        }
                    }
                } catch (_: Exception) {
                    unsupported++                       // a damaged picture is counted, not fatal
                } catch (_: OutOfMemoryError) {
                    unsupported++                       // too big to rebuild on this device
                }
            }
        }

        val pages = doc.numberOfPages
        for (i in 0 until pages) walk(doc.getPage(i).resources?.cosObject, i + 1, intArrayOf(0), 0)
        return ExtractOutcome(pages, truncated, ExtractSkips(tiny, unsupported, masks))
    }
}
