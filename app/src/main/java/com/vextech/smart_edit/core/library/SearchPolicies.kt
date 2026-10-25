package com.vextech.smart_edit.core.library

import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.core.model.SearchHit

enum class SearchFilter { ALL, FILES, NOTES }

object SearchPolicies {
    /** Same normalization the FTS query uses, so highlighting matches what was found. */
    fun terms(raw: String): List<String> = raw.trim()
        .split(Regex("\\s+"))
        .map { it.replace(Regex("[^\\p{L}\\p{N}_-]"), "") }
        .filter { it.isNotBlank() }

    fun applyFilter(hits: List<SearchHit>, filter: SearchFilter): List<SearchHit> = when (filter) {
        SearchFilter.ALL -> hits
        SearchFilter.NOTES -> hits.filter { it.type == ResourceType.NOTE }
        SearchFilter.FILES -> hits.filter { it.type != ResourceType.NOTE }
    }

    /** Hits with a title match first, then the rest, each keeping their original order. */
    fun rank(hits: List<SearchHit>, terms: List<String>): List<SearchHit> {
        if (terms.isEmpty()) return hits
        val lower = terms.map(String::lowercase)
        val (titleHits, others) = hits.partition { hit -> lower.all { hit.title.lowercase().contains(it) } }
        return titleHits + others
    }

    /** A window of [body] around the first match, trimmed to word edges with ellipses. */
    fun snippet(body: String, terms: List<String>, window: Int = 160): String {
        val flat = body.replace(Regex("\\s+"), " ").trim()
        if (flat.length <= window) return flat
        val lower = flat.lowercase()
        val first = terms.map { lower.indexOf(it.lowercase()) }.filter { it >= 0 }.minOrNull() ?: 0
        var start = (first - window / 4).coerceAtLeast(0)
        var end = (start + window).coerceAtMost(flat.length)
        start = (end - window).coerceAtLeast(0)
        if (start > 0) flat.indexOf(' ', start).takeIf { it in 0..(start + 20) }?.let { start = it + 1 }
        if (end < flat.length) flat.lastIndexOf(' ', end).takeIf { it > end - 20 && it > start }?.let { end = it }
        return (if (start > 0) "…" else "") + flat.substring(start, end).trim() + (if (end < flat.length) "…" else "")
    }

    /**
     * Ranges to highlight: every occurrence of a term that starts a word (the search matches prefixes).
     * Overlapping or touching ranges are merged.
     */
    fun highlightRanges(text: String, terms: List<String>): List<IntRange> {
        if (terms.isEmpty() || text.isEmpty()) return emptyList()
        val lower = text.lowercase()
        val raw = ArrayList<IntRange>()
        for (term in terms.map(String::lowercase).filter(String::isNotEmpty)) {
            var from = 0
            while (true) {
                val at = lower.indexOf(term, from)
                if (at < 0) break
                val startsWord = at == 0 || !lower[at - 1].isLetterOrDigit()
                if (startsWord) raw += at until (at + term.length)
                from = at + 1
            }
        }
        if (raw.isEmpty()) return emptyList()
        val sorted = raw.sortedBy { it.first }
        val merged = ArrayList<IntRange>()
        var current = sorted.first()
        for (r in sorted.drop(1)) {
            if (r.first <= current.last + 1) current = current.first..maxOf(current.last, r.last)
            else { merged += current; current = r }
        }
        merged += current
        return merged
    }
}
