package com.vextech.editpro.core.pdf

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.round

/**
 * Minimal DOCX writer: paragraphs, headings, bullets, bold/italic runs, alignment, inline PNG/JPEG pictures and page
 * breaks. A port of the site's `docx-writer.ts`, so both produce the same files. Pure JVM (no Android classes).
 */
sealed interface DocxBlock {
    val pageBreakBefore: Boolean
}

data class DocxParagraph(val paragraph: ParagraphBlock, override val pageBreakBefore: Boolean = false) : DocxBlock

enum class DocxImageFormat(val extension: String) { PNG("png"), JPEG("jpeg") }

class DocxImage(
    val bytes: ByteArray,
    val format: DocxImageFormat,
    val widthPt: Double,
    val heightPt: Double,
    val centred: Boolean = true,
    override val pageBreakBefore: Boolean = false,
) : DocxBlock

object DocxWriter {
    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
        "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
        "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
        "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""
    private val ILLEGAL = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\uFFFE\\uFFFF]")

    /** Remove characters XML 1.0 forbids, then escape the five special ones. */
    fun xmlEscape(s: String): String = s.replace(ILLEGAL, "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun tw(pt: Double) = round(pt * 20).toLong()          // points to twentieths of a point
    private fun emu(pt: Double) = round(pt * 12700).toLong()      // points to EMU
    private fun halfPt(pt: Double) = max(2L, round(pt * 2).toLong())

    private fun runXml(r: Run): String {
        if (r.text.isEmpty()) return ""
        val size = r.size
        val props = (if (r.bold) "<w:b/>" else "") + (if (r.italic) "<w:i/>" else "") +
            (if (size != null && size > 0) "<w:sz w:val=\"${halfPt(size)}\"/><w:szCs w:val=\"${halfPt(size)}\"/>" else "")
        return "<w:r>${if (props.isNotEmpty()) "<w:rPr>$props</w:rPr>" else ""}<w:t xml:space=\"preserve\">${xmlEscape(r.text)}</w:t></w:r>"
    }

    private fun paragraphXml(block: DocxParagraph): String {
        val p = block.paragraph
        val pr = ArrayList<String>()
        if (p.heading != null) pr += "<w:pStyle w:val=\"Heading${p.heading}\"/>"
        if (block.pageBreakBefore) pr += "<w:pageBreakBefore/>"
        if (p.bullet) pr += "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>"
        if (p.indent > 0 && !p.bullet) pr += "<w:ind w:left=\"${tw(p.indent)}\"/>"
        if (p.centred) pr += "<w:jc w:val=\"center\"/>"
        return "<w:p>${if (pr.isNotEmpty()) "<w:pPr>${pr.joinToString("")}</w:pPr>" else ""}${p.runs.joinToString("") { runXml(it) }}</w:p>"
    }

    private fun imageXml(img: DocxImage, rid: String, id: Int): String {
        val cx = emu(img.widthPt)
        val cy = emu(img.heightPt)
        val name = "Picture $id"
        val pr = (if (img.pageBreakBefore) "<w:pageBreakBefore/>" else "") + (if (img.centred) "<w:jc w:val=\"center\"/>" else "")
        return "<w:p>${if (pr.isNotEmpty()) "<w:pPr>$pr</w:pPr>" else ""}<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
            "<wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:docPr id=\"$id\" name=\"$name\"/>" +
            "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic>" +
            "<pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"$name\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"$rid\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
            "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
    }

    private fun stylesXml(font: String, base: Double): String {
        val f = xmlEscape(font)
        fun heading(n: Int, size: Double) =
            "<w:style w:type=\"paragraph\" w:styleId=\"Heading$n\"><w:name w:val=\"heading $n\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>" +
                "<w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"${n - 1}\"/></w:pPr>" +
                "<w:rPr><w:rFonts w:ascii=\"$f\" w:hAnsi=\"$f\" w:cs=\"$f\"/><w:b/><w:bCs/><w:sz w:val=\"${halfPt(size)}\"/><w:szCs w:val=\"${halfPt(size)}\"/></w:rPr></w:style>"
        return "$XML<w:styles $NS><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"$f\" w:hAnsi=\"$f\" w:eastAsia=\"$f\" w:cs=\"$f\"/>" +
            "<w:sz w:val=\"${halfPt(base)}\"/><w:szCs w:val=\"${halfPt(base)}\"/><w:lang w:val=\"en-US\"/></w:rPr></w:rPrDefault>" +
            "<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"259\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
            heading(1, base * 1.9) + heading(2, base * 1.5) + heading(3, base * 1.25) + "</w:styles>"
    }

    private val NUMBERING = "$XML<w:numbering $NS><w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"hybridMultilevel\"/>" +
        "<w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"\u2022\"/><w:lvlJc w:val=\"left\"/>" +
        "<w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\" w:hint=\"default\"/></w:rPr></w:lvl>" +
        "</w:abstractNum><w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num></w:numbering>"

    /** Build the .docx file bytes. [now] is only for the document properties. */
    fun build(
        blocks: List<DocxBlock>,
        widthPt: Double,
        heightPt: Double,
        marginPt: Double = 54.0,
        title: String = "",
        font: String = "Calibri",
        baseSize: Double = 11.0,
        now: Instant = Instant.now(),
    ): ByteArray {
        val files = LinkedHashMap<String, ByteArray>()
        val rels = arrayListOf(
            "<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>",
            "<Relationship Id=\"rIdNumbering\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>",
        )
        var imageCount = 0
        val extensions = HashSet<String>()
        val body = blocks.joinToString("") { b ->
            when (b) {
                is DocxParagraph -> paragraphXml(b)
                is DocxImage -> {
                    imageCount++
                    val ext = b.format.extension
                    val rid = "rIdImg$imageCount"
                    extensions += ext
                    files["word/media/image$imageCount.$ext"] = b.bytes
                    rels += "<Relationship Id=\"$rid\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image$imageCount.$ext\"/>"
                    imageXml(b, rid, imageCount)
                }
            }
        }
        val sect = "<w:sectPr><w:pgSz w:w=\"${tw(widthPt)}\" w:h=\"${tw(heightPt)}\"/>" +
            "<w:pgMar w:top=\"${tw(marginPt)}\" w:right=\"${tw(marginPt)}\" w:bottom=\"${tw(marginPt)}\" w:left=\"${tw(marginPt)}\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/></w:sectPr>"
        // A document must contain at least one paragraph before sectPr.
        val content = body.ifEmpty { "<w:p/>" }

        val types = "$XML<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            (if ("png" in extensions) "<Default Extension=\"png\" ContentType=\"image/png\"/>" else "") +
            (if ("jpeg" in extensions) "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>" else "") +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>" +
            "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
            "</Types>"
        val stamp = now.toString().replace(Regex("\\.\\d+Z$"), "Z")
        val core = "$XML<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
            "<dc:title>${xmlEscape(title)}</dc:title>" +
            "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$stamp</dcterms:created><dcterms:modified xsi:type=\"dcterms:W3CDTF\">$stamp</dcterms:modified></cp:coreProperties>"

        val parts = LinkedHashMap<String, ByteArray>()
        parts["[Content_Types].xml"] = types.toByteArray()
        parts["_rels/.rels"] = ("$XML<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/></Relationships>").toByteArray()
        parts["docProps/core.xml"] = core.toByteArray()
        parts["word/document.xml"] = "$XML<w:document $NS><w:body>$content$sect</w:body></w:document>".toByteArray()
        parts["word/styles.xml"] = stylesXml(font, baseSize).toByteArray()
        parts["word/numbering.xml"] = NUMBERING.toByteArray()
        parts["word/_rels/document.xml.rels"] = "$XML<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">${rels.joinToString("")}</Relationships>".toByteArray()
        parts.putAll(files)

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setLevel(6)
            for ((name, bytes) in parts) {
                zip.putNextEntry(ZipEntry(name).apply { time = 1577836800000L })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}

/** A page picture for a page with no text (a scan). Sizes are in PDF points. */
class PageImage(val bytes: ByteArray, val format: DocxImageFormat, val widthPt: Double, val heightPt: Double)

/** PDF pages to a Word file: text pages as paragraphs, scans optionally as one picture per page. Port of `pdfToDocx`. */
object PdfDocx {
    private const val MARGIN = 54.0

    /** The text analysis of every page, before any scan is rendered. */
    class Analysis(val pages: List<PageInput>, val blocks: List<List<ParagraphBlock>>, val textPages: Set<Int>, val body: Double) {
        /** 0-based pages that have no text and so may need a picture. */
        val scanPages: List<Int> get() = pages.indices.filter { it !in textPages }
    }

    fun analyse(pages: List<PageInput>): Analysis {
        val body = PdfTextLayout.bodySize(pages)
        val blocks = pages.map { PdfTextLayout.pageToBlocks(it, body) }
        val textPages = blocks.indices.filter { i ->
            blocks[i].sumOf { p -> p.runs.sumOf { r -> r.text.count { c -> !c.isWhitespace() } } } >= 3
        }.toSet()
        return Analysis(pages, blocks, textPages, body)
    }

    data class Result(val bytes: ByteArray, val pages: Int, val textPages: Int, val imagePages: Int, val emptyPages: Int)

    /** [images] maps a 0-based scan page to its picture; a missing entry leaves that page blank. */
    fun assemble(analysis: Analysis, images: Map<Int, PageImage>, title: String = ""): Result {
        val first = analysis.pages.firstOrNull() ?: PageInput(595.0, 842.0, emptyList())
        val boxW = max(100.0, first.width - MARGIN * 2)
        val boxH = max(100.0, first.height - MARGIN * 2)
        val out = ArrayList<DocxBlock>()
        var imagePages = 0
        var emptyPages = 0
        analysis.pages.indices.forEach { i ->
            val pageBlocks: List<DocxBlock> = when {
                i in analysis.textPages -> analysis.blocks[i].map { DocxParagraph(it) }
                images[i] != null -> {
                    val img = images.getValue(i)
                    val s = minOf(1.0, boxW / img.widthPt, boxH / img.heightPt)
                    imagePages++
                    listOf(DocxImage(img.bytes, img.format, img.widthPt * s, img.heightPt * s))
                }
                else -> { emptyPages++; emptyList() }
            }
            // Keep page order with a blank paragraph when a page gave nothing.
            val list = pageBlocks.ifEmpty { listOf(DocxParagraph(ParagraphBlock(emptyList()))) }.toMutableList()
            if (i > 0) list[0] = when (val b = list[0]) {
                is DocxParagraph -> b.copy(pageBreakBefore = true)
                is DocxImage -> DocxImage(b.bytes, b.format, b.widthPt, b.heightPt, b.centred, true)
            }
            out += list
        }
        val bytes = DocxWriter.build(
            out, first.width, first.height, MARGIN, title,
            baseSize = minOf(14.0, max(9.0, analysis.body)),
        )
        return Result(bytes, analysis.pages.size, analysis.textPages.size, imagePages, emptyPages)
    }
}
