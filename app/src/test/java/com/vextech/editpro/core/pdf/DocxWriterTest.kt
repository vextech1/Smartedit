package com.vextech.editpro.core.pdf

import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the docx writer and `pdfToDocx` cases of the site's `tests/pdf-docx.test.ts`. */
class DocxWriterTest {
    private val png = byteArrayOf(1, 2, 3, 4, 5)
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) { val e = z.nextEntry ?: break; files[e.name] = z.readBytes() }
        }
        return files
    }

    private fun para(text: String, heading: Int? = null, bullet: Boolean = false, centred: Boolean = false, bold: Boolean = false, italic: Boolean = false) =
        DocxParagraph(ParagraphBlock(listOf(Run(text, bold, italic)), heading, bullet, centred))

    @Test fun escapesXmlAndStripsControlCharacters() {
        assertEquals("a &amp; b &lt; c &gt; &quot;d&quot; ", DocxWriter.xmlEscape("a & b < c > \"d\" \u0001\u000B"))
    }

    @Test fun writesRequiredPartsWithContentTypesFirst() {
        val files = unzip(DocxWriter.build(listOf(para("Hi")), 595.0, 842.0, now = now))
        for (n in listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml", "word/numbering.xml", "word/_rels/document.xml.rels", "docProps/core.xml"))
            assertTrue(n, files[n] != null)
        assertEquals("[Content_Types].xml", files.keys.first())
    }

    @Test fun writesHeadingsBulletsEmphasisAlignmentBreaksAndImages() {
        val bytes = DocxWriter.build(
            listOf(
                para("Title", heading = 1, centred = true),
                DocxParagraph(ParagraphBlock(listOf(Run("bold", bold = true), Run(" it", italic = true))), pageBreakBefore = true),
                para("item", bullet = true),
                DocxImage(png, DocxImageFormat.PNG, 100.0, 50.0, centred = false),
            ),
            595.0, 842.0, title = "T & T", now = now,
        )
        val files = unzip(bytes)
        val doc = String(files.getValue("word/document.xml"))
        assertTrue(doc.contains("w:pStyle w:val=\"Heading1\""))
        assertTrue(doc.contains("<w:jc w:val=\"center\"/>"))
        assertTrue(doc.contains("<w:b/>")); assertTrue(doc.contains("<w:i/>"))
        assertTrue(doc.contains("<w:pageBreakBefore/>"))
        assertTrue(doc.contains("<w:numPr>"))
        assertTrue(doc.contains("r:embed=\"rIdImg1\""))
        assertTrue(files.getValue("word/media/image1.png").contentEquals(png))
        assertTrue(String(files.getValue("[Content_Types].xml")).contains("image/png"))
        assertTrue(String(files.getValue("docProps/core.xml")).contains("T &amp; T"))
    }

    @Test fun everyXmlPartIsWellFormed() {
        val bytes = DocxWriter.build(
            listOf(para("A < B & \"C\"", heading = 2), para("item", bullet = true), DocxImage(png, DocxImageFormat.JPEG, 10.0, 10.0)),
            595.0, 842.0, title = "x & y", now = now,
        )
        for ((name, data) in unzip(bytes)) {
            if (!name.endsWith(".xml") && !name.endsWith(".rels")) continue
            javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(ByteArrayInputStream(data))
        }
    }

    @Test fun emptyDocumentStillHasAParagraph() {
        assertTrue(String(unzip(DocxWriter.build(emptyList(), 595.0, 842.0, now = now)).getValue("word/document.xml")).contains("<w:p/>"))
    }

    private fun item(s: String, x: Double, y: Double, size: Double = 11.0) = TextItem(s, x, y, s.length * size * 0.5, size)

    @Test fun textPagesBecomeParagraphsAndScansBecomePicturesWhenKept() {
        val text = PageInput(595.0, 842.0, listOf(item("Some real text on page one.", 60.0, 700.0)))
        val scan = PageInput(595.0, 842.0, emptyList())
        val analysis = PdfDocx.analyse(listOf(text, scan, scan))
        assertEquals(setOf(0), analysis.textPages)
        assertEquals(listOf(1, 2), analysis.scanPages)

        val kept = PdfDocx.assemble(analysis, mapOf(1 to PageImage(png, DocxImageFormat.JPEG, 595.0, 842.0)), "Doc")
        assertEquals(1, kept.textPages); assertEquals(1, kept.imagePages); assertEquals(1, kept.emptyPages)
        val files = unzip(kept.bytes)
        val doc = String(files.getValue("word/document.xml"))
        assertTrue(doc.contains("Some real text on page one."))
        assertTrue(files.containsKey("word/media/image1.jpeg"))
        assertEquals(2, Regex("<w:pageBreakBefore/>").findAll(doc).count())
        // A full-page picture is scaled down to fit inside the margins.
        val ext = Regex("<wp:extent cx=\"(\\d+)\" cy=\"(\\d+)\"/>").find(doc)!!
        assertTrue(ext.groupValues[1].toLong() <= (595 - 108) * 12700L + 1)
        assertTrue(ext.groupValues[2].toLong() <= (842 - 108) * 12700L + 1)

        val dropped = PdfDocx.assemble(analysis, emptyMap())
        assertEquals(0, dropped.imagePages); assertEquals(2, dropped.emptyPages)
        assertTrue(!unzip(dropped.bytes).containsKey("word/media/image1.jpeg"))
    }
}
