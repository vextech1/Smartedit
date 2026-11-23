package com.vextech.editpro.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangesTest {
    private fun ok(text: String, total: Int = 10) = (PageRanges.parse(text, total) as PageRangeResult.Ok).groups
    private fun problem(text: String, total: Int = 10) = (PageRanges.parse(text, total) as PageRangeResult.Error).problem

    @Test fun emptyAndAllMeanEveryPage() {
        assertEquals(listOf((0..3).toList()), ok("", 4))
        assertEquals(listOf((0..3).toList()), ok(" ALL ", 4))
    }

    @Test fun readsPagesAndRanges() {
        assertEquals(listOf(listOf(0, 1, 2), listOf(4), listOf(6, 7, 8)), ok("1-3, 5, 7-9"))
        assertEquals(listOf(listOf(2)), ok("3"))
        assertEquals(listOf(listOf(0, 1)), ok("1 - 2"))
    }

    @Test fun pagesListIsSortedAndUnique() {
        val r = PageRanges.parse("5, 1-3, 2", 10) as PageRangeResult.Ok
        assertEquals(listOf(0, 1, 2, 4), r.pages)
    }

    @Test fun rejectsBadText() {
        assertEquals(PageRangeProblem.NOT_A_RANGE, problem("a"))
        assertEquals(PageRangeProblem.NOT_A_RANGE, problem("1-"))
        assertEquals(PageRangeProblem.NOT_A_RANGE, problem("1,,2"))
        assertEquals(PageRangeProblem.NOT_A_RANGE, problem("-3"))
    }

    @Test fun rejectsInvalidRanges() {
        assertEquals(PageRangeProblem.INVALID_RANGE, problem("0"))
        assertEquals(PageRangeProblem.INVALID_RANGE, problem("5-3"))
    }

    @Test fun rejectsPastTheEnd() {
        val e = PageRanges.parse("2-11", 10) as PageRangeResult.Error
        assertEquals(PageRangeProblem.PAST_END, e.problem)
        assertEquals("2-11", e.text)
        assertEquals(10, e.total)
        assertEquals(PageRangeProblem.PAST_END, problem("99999999999"))
    }

    @Test fun splitNeedsSomethingTyped() {
        val e = PageRanges.parseForSplit("  ", 5) as PageRangeResult.Error
        assertEquals(PageRangeProblem.EMPTY, e.problem)
        assertTrue(PageRanges.parseForSplit("1-2", 5) is PageRangeResult.Ok)
    }

    @Test fun reversedOrder() {
        assertEquals(listOf(3, 2, 1, 0), PageRanges.reversedOrder(4))
        assertEquals(listOf(0), PageRanges.reversedOrder(1))
        assertEquals(emptyList<Int>(), PageRanges.reversedOrder(0))
    }

    @Test fun doubleSidedOrder() {
        assertEquals(listOf(0, 5, 1, 4, 2, 3), PageRanges.doubleSidedOrder(6, true))
        assertEquals(listOf(0, 3, 1, 4, 2, 5), PageRanges.doubleSidedOrder(6, false))
        assertNull(PageRanges.doubleSidedOrder(5, true))
        assertNull(PageRanges.doubleSidedOrder(0, true))
        assertNull(PageRanges.doubleSidedOrder(1, false))
    }
}
