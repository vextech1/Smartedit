package com.vextech.editpro.core.pdf

/** Why a page-range box could not be read. The screen turns these into text in the user's language. */
enum class PageRangeProblem { NOT_A_RANGE, INVALID_RANGE, PAST_END, EMPTY }

sealed interface PageRangeResult {
    /** Groups of 0-based page indices, one list per typed part ("1-3, 5" is two groups). */
    data class Ok(val groups: List<List<Int>>) : PageRangeResult {
        /** Every page in every group, once each, in ascending order. */
        val pages: List<Int> get() = groups.flatten().distinct().sorted()
    }

    /** [text] is the part that failed; [total] is the page count, for "past the end" messages. */
    data class Error(val problem: PageRangeProblem, val text: String, val total: Int) : PageRangeResult
}

/** Reads "1-3, 5, 7-9" the way the EditPro site does (`parseRanges`, `parseSplitRanges`). */
object PageRanges {
    private val PART = Regex("^(\\d+)(?:\\s*-\\s*(\\d+))?$")

    /** Empty or "all" means every page, as one group. */
    fun parse(text: String, total: Int): PageRangeResult {
        val t = text.trim().lowercase()
        if (t.isEmpty() || t == "all") return PageRangeResult.Ok(listOf((0 until total).toList()))
        return parseParts(t, total)
    }

    /** For Split: nothing typed means nothing to split. */
    fun parseForSplit(text: String, total: Int): PageRangeResult {
        if (text.isBlank()) return PageRangeResult.Error(PageRangeProblem.EMPTY, "", total)
        return parse(text, total)
    }

    private fun parseParts(t: String, total: Int): PageRangeResult {
        val groups = ArrayList<List<Int>>()
        for (raw in t.split(',')) {
            val part = raw.trim()
            val m = PART.matchEntire(part) ?: return PageRangeResult.Error(PageRangeProblem.NOT_A_RANGE, part, total)
            val a = m.groupValues[1].toLongOrNull() ?: return PageRangeResult.Error(PageRangeProblem.INVALID_RANGE, part, total)
            val b = m.groupValues[2].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: a
            if (a < 1 || b < a) return PageRangeResult.Error(PageRangeProblem.INVALID_RANGE, part, total)
            if (b > total) return PageRangeResult.Error(PageRangeProblem.PAST_END, part, total)
            groups += (a.toInt() - 1..b.toInt() - 1).toList()
        }
        return PageRangeResult.Ok(groups)
    }

    /**
     * Page order for a double-sided stack scanned in two passes: fronts 1..N first, then the backs, either reversed (the usual
     * case, N..1) or in order. Returns the 0-based source index for each output position, or null for an odd or tiny page count.
     */
    fun doubleSidedOrder(total: Int, backsReversed: Boolean): List<Int>? {
        if (total < 2 || total % 2 != 0) return null
        val n = total / 2
        val order = ArrayList<Int>(total)
        for (j in 0 until n) {
            order += j
            order += if (backsReversed) total - 1 - j else n + j
        }
        return order
    }
}
