package com.vextech.editpro.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StampLayoutTest {
    private val eps = 1e-9
    private fun opts(
        header: StampZones = StampZones(), footer: StampZones = StampZones(center = "{n}"),
        from: Int = 1, to: Int? = null, start: Int = 1,
    ) = StampOptions(header, footer, fromPage = from, toPage = to, startNumber = start, fileName = "report", date = "2026-11-24")
    private fun width(t: String) = t.length * 5.0          // every character 5 points wide

    @Test fun expandsAllTokensInAnyCase() {
        val v = StampTokens(n = 3, total = 10, date = "2026-11-24", file = "report", bates = "CASE-000007")
        assertEquals("Page 3 of 10", StampLayout.expand("Page {n} of {TOTAL}", v))
        assertEquals("report 2026-11-24 CASE-000007", StampLayout.expand("{file} {Date} {bates}", v))
        assertEquals("{page}", StampLayout.expand("{page}", v))
    }

    @Test fun validatesTheSameLimitsAsTheSite() {
        fun p(o: StampOptions, total: Int = 10) = StampLayout.validate(o, total)?.problem
        assertNull(p(opts()))
        assertEquals(StampProblem.SIZE, p(opts().copy(size = 3.0)))
        assertEquals(StampProblem.SIZE, p(opts().copy(size = 73.0)))
        assertEquals(StampProblem.MARGIN, p(opts().copy(marginPt = -1.0)))
        assertEquals(StampProblem.START_NUMBER, p(opts(start = 1_000_000)))
        assertEquals(StampProblem.FROM_PAGE, p(opts(from = 0)))
        assertEquals(StampProblem.PAGE_PAST_END, p(opts(from = 11)))
        assertEquals(StampProblem.TO_BEFORE_FROM, p(opts(from = 5, to = 4)))
        assertEquals(StampProblem.PAGE_PAST_END, p(opts(to = 11)))
        assertEquals(StampProblem.BATES_DIGITS, p(opts().copy(bates = BatesOptions(digits = 13))))
        assertEquals(StampProblem.BATES_START, p(opts().copy(bates = BatesOptions(start = -1))))
        assertEquals(StampProblem.NO_TEXT, p(opts(footer = StampZones(center = "  "))))
    }

    @Test fun pastTheEndNamesThePageAndTheTotal() {
        val e = StampLayout.validate(opts(from = 12), 10)!!
        assertEquals(12, e.page); assertEquals(10, e.total)
    }

    @Test fun refusesLettersTheFontCannotPrint() {
        val e = StampLayout.validate(opts(footer = StampZones(center = "Страница {n}")), 10)!!
        assertEquals(StampProblem.UNSUPPORTED_CHAR, e.problem); assertEquals('С', e.char)
        assertEquals(StampProblem.UNSUPPORTED_CHAR, StampLayout.validate(opts().copy(bates = BatesOptions(prefix = "№")), 10)!!.problem)
    }

    @Test fun footerZonesSitAtTheMarginAndAlign() {
        val o = opts(footer = StampZones("L", "C", "R")).copy(marginPt = 20.0)
        val items = StampLayout.items(o, 0, 5, 600.0, 800.0, ::width)
        assertEquals(listOf("L", "C", "R"), items.map { it.text })
        assertEquals(20.0, items[0].x, eps)                        // left: at the margin
        assertEquals((600.0 - 5.0) / 2, items[1].x, eps)           // centre
        assertEquals(600.0 - 20.0 - 5.0, items[2].x, eps)          // right: ends at the margin
        assertTrue(items.all { it.y == 20.0 })
    }

    @Test fun headerBaselineIsBelowTheTopMargin() {
        val o = opts(header = StampZones(center = "H"), footer = StampZones()).copy(marginPt = 28.0, size = 10.0)
        val item = StampLayout.items(o, 0, 5, 600.0, 800.0, ::width).single()
        assertEquals(800.0 - 28.0 - 10.0 * 0.72, item.y, eps)
    }

    @Test fun numbersCountFromTheFirstStampedPage() {
        // Pages 3 to 5 of 10, first number 7: pages show 7, 8, 9 and {total} is 9.
        val o = opts(footer = StampZones(center = "{n}/{total}"), from = 3, to = 5, start = 7)
        fun text(page: Int) = StampLayout.items(o, page, 10, 600.0, 800.0, ::width).single().text
        assertEquals("7/9", text(2)); assertEquals("8/9", text(3)); assertEquals("9/9", text(4))
        assertEquals(2..4, StampLayout.pageSpan(o, 10))
    }

    @Test fun batesNumbersArePaddedAndPrefixed() {
        val o = opts(footer = StampZones(right = "{bates}")).copy(bates = BatesOptions("CASE-", 41, 6))
        assertEquals("CASE-000041", StampLayout.items(o, 0, 3, 600.0, 800.0, ::width).single().text)
        assertEquals("CASE-000043", StampLayout.items(o, 2, 3, 600.0, 800.0, ::width).single().text)
    }

    @Test fun fileTokenReplacesUnprintableCharactersAndDateIsPassedThrough() {
        val o = opts(footer = StampZones(center = "{file} {date}")).copy(fileName = "отчёт 2026")
        assertEquals("_____ 2026 2026-11-24", StampLayout.items(o, 0, 1, 600.0, 800.0, ::width).single().text)
    }

    @Test fun emptyZonesProduceNothing() {
        assertTrue(StampLayout.items(opts(footer = StampZones(center = "{unknown}", left = "")).copy(), 0, 1, 600.0, 800.0, ::width).size == 1)
        assertTrue(StampLayout.items(opts(footer = StampZones(center = " ")), 0, 1, 600.0, 800.0, ::width).isEmpty())
    }

    @Test fun presetsMatchTheSite() {
        assertEquals(listOf("number", "page_of", "right", "top_right", "file_and_page", "bates"), StampLayout.PRESETS.map { it.id })
        val f = StampForm().withPreset("file_and_page")
        assertEquals("{file}", f.header.center); assertEquals("Page {n} of {total}", f.footer.center)
        assertEquals("custom", StampForm().withPreset("nope").presetId)
    }

    @Test fun formReadsTypedValuesAndConvertsMillimetres() {
        val ok = StampForm(size = "12", marginMm = "25.4").parse(10, "report", "2026-11-24") as StampParse.Ok
        assertEquals(12.0, ok.options.size, eps); assertEquals(72.0, ok.options.marginPt, 1e-6)
        assertNull(ok.options.toPage)
        val ranged = StampForm(from = "2", to = "4").parse(10, "r", "d") as StampParse.Ok
        assertEquals(2, ranged.options.fromPage); assertEquals(4, ranged.options.toPage)
    }

    @Test fun formReportsTheFirstProblem() {
        fun p(f: StampForm, total: Int = 10) = (f.parse(total, "r", "d") as StampParse.Error).error.problem
        assertEquals(StampProblem.SIZE, p(StampForm(size = "")))
        assertEquals(StampProblem.MARGIN, p(StampForm(marginMm = "x")))
        assertEquals(StampProblem.START_NUMBER, p(StampForm(start = "-")))
        assertEquals(StampProblem.FROM_PAGE, p(StampForm(from = "")))
        assertEquals(StampProblem.TO_PAGE, p(StampForm(to = "x")))
        assertEquals(StampProblem.PAGE_PAST_END, p(StampForm(from = "11")))
        assertEquals(StampProblem.NO_TEXT, p(StampForm(footer = StampZones())))
        assertNotNull(StampForm().parse(1, "r", "d") as? StampParse.Ok)
    }
}
