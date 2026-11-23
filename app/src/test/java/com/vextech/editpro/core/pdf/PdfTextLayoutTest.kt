package com.vextech.editpro.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the site's `tests/pdf-docx.test.ts` layout cases and adds the Markdown/text writer. */
class PdfTextLayoutTest {
    private fun item(s: String, x: Double, y: Double, size: Double = 11.0, bold: Boolean = false, italic: Boolean = false) =
        TextItem(s, x, y, s.length * size * 0.5, size, bold, italic)

    private fun page(vararg items: TextItem) = PageInput(595.0, 842.0, items.toList())
    private fun text(b: ParagraphBlock) = b.runs.joinToString("") { it.text }
    private fun blocks(vararg items: TextItem) = PdfTextLayout.pageToBlocks(page(*items), 11.0)

    @Test fun ordersLinesAndAddsSpacesForGaps() {
        val lines = PdfTextLayout.buildLines(listOf(item("world", 100.0, 700.0), item("Hello", 60.0, 700.0), item("Second", 60.0, 680.0)))
        assertEquals(listOf("Hello world", "Second"), lines.map { it.text })
    }

    @Test fun doesNotDoubleSpaces() {
        val lines = PdfTextLayout.buildLines(listOf(item("Hello", 60.0, 700.0), item(" ", 90.0, 700.0), item("world", 95.0, 700.0)))
        assertEquals("Hello world", lines[0].text)
    }

    @Test fun joinsWrappedLinesAndSplitsOnBigGap() {
        val b = blocks(item("This is a long sentence that wraps", 60.0, 700.0), item("onto the next line.", 60.0, 686.0), item("A new paragraph starts here.", 60.0, 650.0))
        assertEquals(listOf("This is a long sentence that wraps onto the next line.", "A new paragraph starts here."), b.map(::text))
    }

    @Test fun removesHyphenOfSplitWordButKeepsRealHyphen() {
        assertEquals("The international team won", text(blocks(item("The inter-", 60.0, 700.0), item("national team won", 60.0, 686.0))[0]))
        assertEquals("Smith- Jones agreed", text(blocks(item("Smith-", 60.0, 700.0), item("Jones agreed", 60.0, 686.0))[0]))
    }

    @Test fun largerTextBecomesHeadingBySizeRatio() {
        val b = blocks(item("Big Title", 60.0, 760.0, 24.0), item("Section", 60.0, 720.0, 15.0), item("Body text goes here.", 60.0, 690.0))
        assertEquals(listOf(1, 2, null), b.map { it.heading })
        assertTrue(b[0].runs.all { !it.bold && it.size == null })
    }

    @Test fun shortAllBoldLineIsHeadingButBoldProseIsNot() {
        val b = blocks(item("Overview", 60.0, 760.0, bold = true), item("Bold sentence that ends with a full stop.", 60.0, 700.0, bold = true))
        assertEquals(3, b[0].heading)
        assertNull(b[1].heading)
    }

    @Test fun detectsBulletsAndKeepsWrappedTextTogether() {
        val b = blocks(item("\u2022 First point that is long", 60.0, 700.0), item("and wraps here", 74.0, 686.0), item("\u2022 Second point", 60.0, 672.0))
        assertEquals(listOf(true to "First point that is long and wraps here", true to "Second point"), b.map { it.bullet to text(it) })
    }

    @Test fun keepsBoldAndItalicRuns() {
        val b = blocks(item("Plain ", 60.0, 700.0), item("bold", 90.0, 700.0, bold = true), item(" end", 112.0, 700.0))
        assertEquals(listOf("Plain " to false, "bold" to true, " end" to false), b[0].runs.map { it.text to it.bold })
    }

    @Test fun dropsLonePageNumberAtBottomOnly() {
        val b = blocks(item("Real text here", 60.0, 700.0), item("3", 295.0, 30.0), item("42", 60.0, 400.0))
        assertEquals(listOf("Real text here", "42"), b.map(::text))
    }

    @Test fun picksMostCommonSizeAsBody() {
        assertEquals(12.0, PdfTextLayout.bodySize(listOf(page(item("aaaaaaaaaa", 0.0, 0.0, 10.0), item("bbbbbbbbbbbbbbbbbbbb", 0.0, 20.0, 12.0), item("big", 0.0, 40.0, 30.0)))), 0.0)
        assertEquals(11.0, PdfTextLayout.bodySize(listOf(page())), 0.0)
        assertTrue(blocks().isEmpty())
    }

    @Test fun markdownEscapesAndGuardsParagraphStarts() {
        assertEquals("a\\*b\\_c\\[d\\]\\<e\\>\\\\f\\`g", PdfTextLayout.mdEscape("a*b_c[d]<e>\\f`g"))
        val b = blocks(item("1. not a list item", 60.0, 700.0))
        assertEquals("1\\. not a list item", PdfTextLayout.blockToText(b[0], TextFormat.MD))
    }

    @Test fun markdownKeepsHeadingsBulletsAndEmphasis() {
        val p = page(
            item("Quarterly Report", 150.0, 780.0, 26.0, bold = true),
            item("Note: ", 54.0, 660.0, bold = true), item("figures are unaudited.", 84.0, 660.0, italic = true),
            item("\u2022 North America up 12%", 54.0, 630.0), item("\u2022 Europe up 8%", 54.0, 616.0),
        )
        val md = PdfTextLayout.toText(listOf(p), TextFormat.MD, false).text
        assertEquals("# Quarterly Report\n\n**Note:** *figures are unaudited.*\n\n- North America up 12%\n- Europe up 8%\n", md)
        val txt = PdfTextLayout.toText(listOf(p), TextFormat.TXT, false).text
        assertTrue(txt.contains("\u2022 Europe up 8%"))
        assertFalse(txt.contains("**"))
    }

    @Test fun pageMarkersAndEmptyPages() {
        val one = page(item("First page text", 60.0, 700.0))
        val two = page(item("Second page text", 60.0, 700.0))
        val r = PdfTextLayout.toText(listOf(one, page(), two), TextFormat.TXT, true)
        // As on the site, an empty page in the middle still gets its marker.
        assertEquals("----- Page 1 -----\n\nFirst page text\n\n----- Page 2 -----\n\n----- Page 3 -----\n\nSecond page text\n", r.text)
        assertEquals(2, r.textPages); assertEquals(1, r.emptyPages)
        assertEquals("", PdfTextLayout.toText(listOf(page()), TextFormat.MD, true).text)
        val md = PdfTextLayout.toText(listOf(one, two), TextFormat.MD, true).text
        assertEquals("*Page 1*\n\nFirst page text\n\n---\n\n*Page 2*\n\nSecond page text\n", md)
    }
}
