package com.vextech.editpro.core.pdf

/** The left, centre and right text of one band (the header or the footer) of a page. */
data class StampZones(val left: String = "", val center: String = "", val right: String = "") {
    fun all(): List<String> = listOf(left, center, right)
}

enum class StampFont { HELVETICA, TIMES, COURIER }

data class BatesOptions(val prefix: String = "", val start: Int = 1, val digits: Int = 6)

/**
 * Page numbers, headers, footers and Bates numbers: text with {tokens} in up to six spots. Same options as the site (`pdf-stamp.ts`).
 * [marginPt] and [size] are in points; [fromPage] and [toPage] are 1-based and inclusive (null [toPage] means the last page).
 * [date] is `YYYY-MM-DD`, supplied by the caller so this stays pure.
 */
data class StampOptions(
    val header: StampZones,
    val footer: StampZones,
    val font: StampFont = StampFont.HELVETICA,
    val size: Double = 11.0,
    val marginPt: Double = 28.0,
    val gray: Boolean = false,
    val startNumber: Int = 1,
    val fromPage: Int = 1,
    val toPage: Int? = null,
    val bates: BatesOptions = BatesOptions(),
    val fileName: String = "",
    val date: String = "",
)

data class StampPreset(val id: String, val header: StampZones, val footer: StampZones)

enum class StampProblem {
    SIZE, MARGIN, START_NUMBER, FROM_PAGE, TO_PAGE, PAGE_PAST_END, TO_BEFORE_FROM, BATES_DIGITS, BATES_START, NO_TEXT, UNSUPPORTED_CHAR,
}

/** What went wrong and, for the messages that need it, the number ([page], [total]) or the character ([char]) involved. */
data class StampError(val problem: StampProblem, val page: Int = 0, val total: Int = 0, val char: Char? = null)

sealed interface StampParse {
    data class Ok(val options: StampOptions) : StampParse
    data class Error(val error: StampError) : StampParse
}

/** The text of one placed piece, with where its left end and baseline go (screen coordinates: X from the left, Y up from the bottom). */
data class StampItem(val text: String, val x: Double, val y: Double)

data class StampTokens(val n: Int, val total: Int, val date: String, val file: String, val bates: String)

/** Page numbers, headers and footers maths: no Android, no PDF library. Mirrors the site's `pdf-stamp.ts`. */
object StampLayout {
    const val MM = 72.0 / 25.4          // PDF points per millimetre

    /** The ready-made styles, in the order the site lists them. The sheet shows "Custom" when the text matches none of them. */
    val PRESETS = listOf(
        StampPreset("number", StampZones(), StampZones(center = "{n}")),
        StampPreset("page_of", StampZones(), StampZones(center = "Page {n} of {total}")),
        StampPreset("right", StampZones(), StampZones(right = "{n}")),
        StampPreset("top_right", StampZones(right = "{n}"), StampZones()),
        StampPreset("file_and_page", StampZones(center = "{file}"), StampZones(center = "Page {n} of {total}")),
        StampPreset("bates", StampZones(), StampZones(right = "{bates}")),
    )

    private val TOKEN = Regex("\\{(n|total|date|file|bates)\\}", RegexOption.IGNORE_CASE)

    /** Replaces {n} {total} {date} {file} {bates} (any case). Other {words} stay as typed. */
    fun expand(template: String, v: StampTokens): String = TOKEN.replace(template) { m ->
        when (m.groupValues[1].lowercase()) {
            "n" -> v.n.toString()
            "total" -> v.total.toString()
            "date" -> v.date
            "file" -> v.file
            else -> v.bates
        }
    }

    /** A file name with anything the standard fonts cannot print turned into `_`. */
    fun printableFileName(name: String): String = name.map { if (WinAnsi.isSupported(it)) it else '_' }.joinToString("")

    private fun pad(n: Int, digits: Int) = n.toString().padStart(digits, '0')

    /** Checks [o] against a PDF of [total] pages. Null when it can be applied. */
    fun validate(o: StampOptions, total: Int): StampError? {
        if (!(o.size >= 4.0 && o.size <= 72.0)) return StampError(StampProblem.SIZE)
        if (!o.marginPt.isFinite() || o.marginPt < 0) return StampError(StampProblem.MARGIN)
        if (o.startNumber !in 0..999_999) return StampError(StampProblem.START_NUMBER)
        if (o.fromPage < 1) return StampError(StampProblem.FROM_PAGE)
        if (o.fromPage > total) return StampError(StampProblem.PAGE_PAST_END, o.fromPage, total)
        val to = o.toPage
        if (to != null) {
            if (to < o.fromPage) return StampError(StampProblem.TO_BEFORE_FROM)
            if (to > total) return StampError(StampProblem.PAGE_PAST_END, to, total)
        }
        if (o.bates.digits !in 1..12) return StampError(StampProblem.BATES_DIGITS)
        if (o.bates.start < 0) return StampError(StampProblem.BATES_START)
        val zones = o.header.all() + o.footer.all()
        if (zones.none { it.isNotBlank() }) return StampError(StampProblem.NO_TEXT)
        for (t in zones + o.bates.prefix) {
            val bad = WinAnsi.firstUnsupported(t)
            if (bad != null) return StampError(StampProblem.UNSUPPORTED_CHAR, char = bad)
        }
        return null
    }

    /** Zero-based index of the first and last page stamped. */
    fun pageSpan(o: StampOptions, total: Int): IntRange = (o.fromPage - 1)..((o.toPage ?: total) - 1)

    /** The text pieces for page [pageIndex] (0-based) of a PDF with [total] pages, placed on a page that looks [vw] x [vh] on screen. */
    fun items(o: StampOptions, pageIndex: Int, total: Int, vw: Double, vh: Double, measure: (String) -> Double): List<StampItem> {
        val span = pageSpan(o, total)
        val k = pageIndex - span.first
        val count = span.last - span.first + 1
        val tokens = StampTokens(
            n = o.startNumber + k,
            total = o.startNumber + count - 1,
            date = o.date,
            file = printableFileName(o.fileName),
            bates = o.bates.prefix + pad(o.bates.start + k, o.bates.digits),
        )
        val out = ArrayList<StampItem>()
        val bands = listOf(o.header to vh - o.marginPt - o.size * 0.72, o.footer to o.marginPt)
        for ((zones, y) in bands) {
            listOf(0 to zones.left, 1 to zones.center, 2 to zones.right).forEach { (align, template) ->
                val text = expand(template, tokens).trim()
                if (text.isEmpty()) return@forEach
                val w = measure(text)
                val x = when (align) { 0 -> o.marginPt; 1 -> (vw - w) / 2; else -> vw - o.marginPt - w }
                out += StampItem(text, x, y)
            }
        }
        return out
    }
}

/**
 * What the Page numbers sheet edits: every number kept as typed text so a box can be empty or half-typed.
 * [presetId] is the ready-made style last picked, or "custom" once the text was changed by hand.
 */
data class StampForm(
    val presetId: String = "number",
    val header: StampZones = StampZones(),
    val footer: StampZones = StampZones(center = "{n}"),
    val font: StampFont = StampFont.HELVETICA,
    val size: String = "11",
    val marginMm: String = "10",
    val gray: Boolean = false,
    val start: String = "1",
    val from: String = "1",
    val to: String = "",
    val batesPrefix: String = "",
    val batesStart: String = "1",
    val batesDigits: String = "6",
) {
    /** Switches to a ready-made style and shows its text in the six boxes. */
    fun withPreset(id: String): StampForm {
        val p = StampLayout.PRESETS.firstOrNull { it.id == id } ?: return copy(presetId = "custom")
        return copy(presetId = id, header = p.header, footer = p.footer)
    }

    /** Reads the typed values. [fileName] is the name without ".pdf"; [date] is `YYYY-MM-DD`. */
    fun parse(total: Int, fileName: String, date: String): StampParse {
        fun int(text: String): Int? = text.trim().toIntOrNull()
        val sizeValue = size.trim().toDoubleOrNull() ?: return err(StampProblem.SIZE)
        val marginValue = marginMm.trim().toDoubleOrNull() ?: return err(StampProblem.MARGIN)
        val startValue = int(start) ?: return err(StampProblem.START_NUMBER)
        val fromValue = int(from) ?: return err(StampProblem.FROM_PAGE)
        val toValue = if (to.isBlank()) null else int(to) ?: return err(StampProblem.TO_PAGE)
        val digits = int(batesDigits) ?: return err(StampProblem.BATES_DIGITS)
        val batesFirst = int(batesStart) ?: return err(StampProblem.BATES_START)
        val options = StampOptions(
            header = header, footer = footer, font = font, size = sizeValue, marginPt = marginValue * StampLayout.MM, gray = gray,
            startNumber = startValue, fromPage = fromValue, toPage = toValue,
            bates = BatesOptions(batesPrefix, batesFirst, digits), fileName = fileName, date = date,
        )
        val problem = StampLayout.validate(options, total)
        return if (problem != null) StampParse.Error(problem) else StampParse.Ok(options)
    }

    private fun err(p: StampProblem) = StampParse.Error(StampError(p))
}
