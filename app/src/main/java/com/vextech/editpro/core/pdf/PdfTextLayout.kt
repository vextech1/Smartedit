package com.vextech.editpro.core.pdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Rebuilds lines, paragraphs, headings and bullets from positioned text, and writes plain text or Markdown.
 * A port of the EditPro site's `pdf-docx.ts` layout analysis and `pdf-markdown.ts`, so both give the same result.
 * Pure Kotlin: no PDF library, so it is unit-tested on the JVM.
 */

/** One positioned piece of text, in PDF points (origin bottom-left). */
data class TextItem(
    val str: String,
    val x: Double,
    val y: Double,
    val w: Double,
    val size: Double,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

data class PageInput(val width: Double, val height: Double, val items: List<TextItem>)

data class Run(var text: String, var bold: Boolean = false, var italic: Boolean = false, var size: Double? = null)

enum class TextFormat { TXT, MD }

data class ParagraphBlock(
    val runs: List<Run>,
    val heading: Int? = null,
    val bullet: Boolean = false,
    val centred: Boolean = false,
    val indent: Double = 0.0,
)

data class TextLine(val runs: List<Run>, val text: String, val x: Double, val right: Double, val y: Double, val size: Double, val bold: Boolean)

object PdfTextLayout {
    private val BULLET = Regex("^[\\u2022\\u25AA\\u25E6\\u25CF\\u25CB\\u25A0\\u25A1\\u2023\\u2219\\u00B7\\uF0B7\\uF0A7\\u2013\\u2014-]\\s+")
    private val PAGE_NUMBER = Regex("^(page\\s*)?\\d{1,4}(\\s*(of|/)\\s*\\d{1,4})?$", RegexOption.IGNORE_CASE)
    private val SENTENCE_END = Regex("[.!?:;\\u201D\")\\]]$")
    private val HYPHEN_BEFORE = Regex("[A-Za-z\\u00C0-\\u024F]-$")
    private val LOWER_START = Regex("^[a-z\\u00DF-\\u00FF]")
    private val EDGE = Regex("^(\\s*)([\\s\\S]*?)(\\s*)$")

    private fun halfPoint(size: Double) = round(size * 2) / 2
    private fun visible(s: String) = s.count { !it.isWhitespace() }

    fun isBoldFont(name: String) = Regex("bold|black|heavy|semibold|demi", RegexOption.IGNORE_CASE).containsMatchIn(name)
    fun isItalicFont(name: String) = Regex("italic|oblique", RegexOption.IGNORE_CASE).containsMatchIn(name)

    /** Group items into reading-order lines and merge neighbouring items into styled runs. */
    fun buildLines(items: List<TextItem>): List<TextLine> {
        val live = items.filter { it.str.isNotEmpty() && it.x.isFinite() && it.y.isFinite() }
            .sortedWith(compareByDescending<TextItem> { it.y }.thenBy { it.x })
        val groups = ArrayList<MutableList<TextItem>>()
        for (it in live) {
            val g = groups.lastOrNull()
            if (g != null) {
                val ref = g[0]
                val tol = 0.45 * max(max(it.size, ref.size), 1.0)
                if (abs(ref.y - it.y) <= tol) { g += it; continue }
            }
            groups += mutableListOf(it)
        }
        val lines = ArrayList<TextLine>()
        for (g in groups) {
            g.sortBy { it.x }
            val runs = ArrayList<Run>()
            val sizeChars = LinkedHashMap<Double, Int>()
            var prevRight = Double.NEGATIVE_INFINITY
            var prev: TextItem? = null
            var boldChars = 0
            var chars = 0
            val text = StringBuilder()
            for (it in g) {
                var s = it.str
                val gap = it.x - prevRight
                val needsSpace = prev != null && gap > 0.18 * it.size && !(text.isNotEmpty() && text.last().isWhitespace()) && !s.first().isWhitespace()
                if (needsSpace) s = " $s"
                val last = runs.lastOrNull()
                if (last != null && last.bold == it.bold && last.italic == it.italic) last.text += s
                else runs += Run(s, it.bold, it.italic)
                text.append(s)
                val vis = visible(it.str)
                chars += vis
                if (it.bold) boldChars += vis
                val key = halfPoint(it.size)
                sizeChars[key] = (sizeChars[key] ?: 0) + vis
                prevRight = it.x + it.w
                prev = it
            }
            if (text.isBlank()) continue
            runs.first().text = runs.first().text.trimStart()
            runs.last().text = runs.last().text.trimEnd()
            val cleaned = runs.filter { it.text.isNotEmpty() }
            var size = 0.0
            var best = -1
            for ((k, v) in sizeChars) if (v > best) { best = v; size = k }
            lines += TextLine(
                runs = cleaned, text = cleaned.joinToString("") { it.text }, x = g[0].x, right = prevRight,
                y = g[0].y, size = size, bold = chars > 0 && boldChars.toDouble() / chars > 0.9,
            )
        }
        return lines
    }

    /** Most common body text size (by characters) across all pages. */
    fun bodySize(pages: List<PageInput>): Double {
        val m = LinkedHashMap<Double, Int>()
        for (p in pages) for (it in p.items) {
            val n = visible(it.str)
            if (n == 0) continue
            val k = halfPoint(it.size)
            m[k] = (m[k] ?: 0) + n
        }
        var size = 11.0
        var best = -1
        for ((k, v) in m) if (v > best) { best = v; size = k }
        return size
    }

    private fun joinRuns(into: MutableList<Run>, more: List<Run>, dehyphenate: Boolean) {
        val last = into.lastOrNull()
        if (dehyphenate && last != null && HYPHEN_BEFORE.containsMatchIn(last.text) && LOWER_START.containsMatchIn(more[0].text)) {
            last.text = last.text.dropLast(1)
        } else if (last != null && !last.text.last().isWhitespace() && !more[0].text.first().isWhitespace()) {
            last.text += " "
        }
        for (r in more) {
            val l = into.lastOrNull()
            if (l != null && l.bold == r.bold && l.italic == r.italic && l.size == r.size) l.text += r.text else into += r.copy()
        }
    }

    private class Current(var runs: MutableList<Run>, val lines: MutableList<TextLine>, val bullet: Boolean)

    /** Turn one page into paragraph blocks. [body] is the document's body text size. */
    fun pageToBlocks(page: PageInput, body: Double): List<ParagraphBlock> {
        val lines = buildLines(page.items).filterNot { it.y < page.height * 0.07 && PAGE_NUMBER.matches(it.text.trim()) }
        if (lines.isEmpty()) return emptyList()
        val textLeft = lines.minOf { it.x }
        val textRight = lines.maxOf { it.right }
        val colWidth = max(1.0, textRight - textLeft)
        val blocks = ArrayList<ParagraphBlock>()
        var cur: Current? = null

        fun flush() {
            val c = cur ?: return
            val first = c.lines[0]
            val n = c.lines.size
            val total = c.runs.sumOf { it.text.length }
            val ratio = first.size / body
            var heading: Int? = null
            if (!c.bullet && n <= 3 && total <= 200) {
                heading = when {
                    ratio >= 1.6 -> 1
                    ratio >= 1.3 -> 2
                    ratio >= 1.12 -> 3
                    c.lines.all { it.bold } && n == 1 && total <= 90 && !SENTENCE_END.containsMatchIn(first.text.trim()) -> 3
                    else -> null
                }
            }
            val center = (first.x + first.right) / 2
            val pageMid = page.width / 2
            val centred = n <= 2 && abs(center - pageMid) < page.width * 0.04 && first.x > page.width * 0.12
            val runs = c.runs.map { it.copy() }
            if (heading != null) {
                for (r in runs) { r.bold = false; r.size = null }
            } else if (abs(first.size - body) >= 1 && c.lines.all { abs(it.size - first.size) < 0.6 }) {
                for (r in runs) r.size = first.size
            }
            val indent = if (!c.bullet && !centred && first.x - textLeft > body * 1.5) min(first.x - textLeft, colWidth / 2) else 0.0
            blocks += ParagraphBlock(runs, heading, c.bullet, centred, indent)
            cur = null
        }

        for (l in lines) {
            val isBullet = BULLET.containsMatchIn(l.text)
            var runs: List<Run> = l.runs
            if (isBullet) {
                val copy = l.runs.map { it.copy() }.toMutableList()
                copy[0].text = copy[0].text.replace(BULLET, "")
                runs = if (copy[0].text.isEmpty() && copy.size > 1) copy.drop(1) else copy
            }
            val c = cur
            if (c != null) {
                val prev = c.lines.last()
                val gap = prev.y - l.y
                val sameSize = abs(prev.size - l.size) <= max(0.6, prev.size * 0.1)
                val dx = abs(l.x - prev.x)
                val prevShort = prev.right < textLeft + colWidth * 0.72 && SENTENCE_END.containsMatchIn(prev.text.trim())
                val prevHeading = prev.size / body >= 1.12 && !c.bullet
                val continues = !isBullet && sameSize && !prevShort && !prevHeading && gap > 0 &&
                    gap <= 1.65 * max(prev.size, l.size) && (dx <= body * 3.2 || c.bullet)
                if (continues) { joinRuns(c.runs, runs, true); c.lines += l; continue }
                flush()
            }
            cur = Current(runs.map { it.copy() }.toMutableList(), mutableListOf(l), isBullet)
        }
        flush()
        return blocks
    }

    // ---- plain text and Markdown ----------------------------------------------------------------------------------

    /** Escape characters Markdown would treat as formatting, so the text reads the same when rendered. */
    fun mdEscape(s: String): String = Regex("[\\\\`*_\\[\\]<>]").replace(s) { "\\" + it.value }

    /** Escape a paragraph-opening marker (`# `, `- `, `1. `) that would turn plain text into a heading or list. */
    private fun guardStart(s: String): String = s
        .replace(Regex("^(#{1,6})(\\s)")) { "\\" + it.groupValues[1] + it.groupValues[2] }
        .replace(Regex("^([-+])(\\s)")) { "\\" + it.groupValues[1] + it.groupValues[2] }
        .replace(Regex("^(\\d+)([.)])(\\s)")) { it.groupValues[1] + "\\" + it.groupValues[2] + it.groupValues[3] }

    private fun mdRun(r: Run): String {
        val m = EDGE.matchEntire(r.text) ?: return r.text
        if (m.groupValues[2].isEmpty()) return r.text
        val core = mdEscape(m.groupValues[2])
        val mark = if (r.bold && r.italic) "***" else if (r.bold) "**" else if (r.italic) "*" else ""
        return "${m.groupValues[1]}$mark$core$mark${m.groupValues[3]}"
    }

    private val SPACES = Regex("[ \\t]+")

    fun blockToText(b: ParagraphBlock, fmt: TextFormat): String {
        if (fmt == TextFormat.TXT) {
            val t = b.runs.joinToString("") { it.text }.replace(SPACES, " ").trim()
            return if (b.bullet) "\u2022 $t" else t
        }
        val inline = b.runs.joinToString("") { mdRun(it) }.replace(SPACES, " ").trim()
        if (b.heading != null) return "${"#".repeat(b.heading)} $inline"
        return if (b.bullet) "- $inline" else guardStart(inline)
    }

    /** Join paragraphs: a blank line between them, but consecutive bullets stay together as one list. */
    fun blocksToText(pages: List<List<ParagraphBlock>>, fmt: TextFormat, pageMarkers: Boolean): String {
        val out = ArrayList<String>()
        pages.forEachIndexed { i, blocks ->
            if (pageMarkers && i > 0) out += if (fmt == TextFormat.MD) "---\n\n*Page ${i + 1}*" else "----- Page ${i + 1} -----"
            else if (pageMarkers && i == 0 && blocks.isNotEmpty()) out += if (fmt == TextFormat.MD) "*Page 1*" else "----- Page 1 -----"
            var prevBullet = false
            var buf = ""
            for (b in blocks) {
                val t = blockToText(b, fmt)
                if (t.isEmpty()) continue
                if (b.bullet && prevBullet) buf += "\n$t"
                else { if (buf.isNotEmpty()) out += buf; buf = t }
                prevBullet = b.bullet
            }
            if (buf.isNotEmpty()) out += buf
        }
        return out.joinToString("\n\n") + if (out.isNotEmpty()) "\n" else ""
    }

    data class TextResult(val text: String, val pages: Int, val textPages: Int, val emptyPages: Int)

    /** Whole-document conversion from positioned pages. Pages without a text layer give no text. */
    fun toText(pages: List<PageInput>, fmt: TextFormat, pageMarkers: Boolean): TextResult {
        val body = bodySize(pages)
        val blocks = pages.map { pageToBlocks(it, body) }
        val textPages = blocks.count { bs -> bs.any { b -> b.runs.any { it.text.isNotBlank() } } }
        return TextResult(if (textPages > 0) blocksToText(blocks, fmt, pageMarkers) else "", pages.size, textPages, pages.size - textPages)
    }
}
