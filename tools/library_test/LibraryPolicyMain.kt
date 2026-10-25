import com.vextech.smart_edit.core.library.*
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.core.model.SearchHit
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private var checks = 0
private fun check(name: String, ok: Boolean) { checks++; if (!ok) throw AssertionError("FAILED: $name") }
private fun <T> eq(name: String, actual: T, expected: T) { checks++; if (actual != expected) throw AssertionError("FAILED: $name\n  expected: $expected\n  actual:   $actual") }

private fun note(id: String, title: String = "", content: String = "", tags: List<String> = emptyList(), updated: Long = 0, pinned: Boolean = false) =
    Note(id, title, content, tags, 0, updated, isPinned = pinned)

private fun entry(id: String, action: HistoryAction?, ts: Long, desc: String = "d", name: String? = null, uri: String? = null) =
    HistoryEntry(id, action, action?.name ?: "UNKNOWN", desc, null, null, uri, name, ts)

fun main() {
    // ---- tags
    eq("tags basic", NotePolicies.parseTags("work, #ideas ,  home  "), listOf("work", "ideas", "home"))
    eq("tags dedupe ignoring case keeps first", NotePolicies.parseTags("Work, work, WORK, Home"), listOf("Work", "Home"))
    eq("tags newline separators", NotePolicies.parseTags("a\nb\r\nc"), listOf("a", "b", "c"))
    eq("tags collapse inner space", NotePolicies.parseTags("big   idea"), listOf("big idea"))
    eq("tags blank dropped", NotePolicies.parseTags(" , ,, #, "), emptyList())
    eq("tags max count", NotePolicies.parseTags((1..30).joinToString(",") { "t$it" }).size, NotePolicies.MAX_TAGS)
    eq("tags max length", NotePolicies.parseTags("x".repeat(100)).single().length, NotePolicies.MAX_TAG_LENGTH)
    eq("tags encode/decode roundtrip", NotePolicies.decodeTags(NotePolicies.encodeTags(listOf("a", "b c", "A"))), listOf("a", "b c"))
    check("encoded tags never contain extra comma", !NotePolicies.encodeTags(listOf("a,b")).contains(",,"))
    eq("decode ignores empties", NotePolicies.decodeTags("a,,b, ,c"), listOf("a", "b", "c"))
    eq("tags for editing", NotePolicies.tagsForEditing(listOf("a", "b")), "a, b")

    // ---- notes
    check("empty note detected", NotePolicies.isEmptyNote("  ", "\n", " , "))
    check("tag-only note is not empty", !NotePolicies.isEmptyNote("", "", "x"))
    eq("title trimmed and capped", NotePolicies.normalizeTitle("  " + "a".repeat(300)).length, NotePolicies.MAX_TITLE_LENGTH)
    val notes = listOf(
        note("1", "Alpha", "first body", listOf("work"), updated = 10),
        note("2", "Beta", "second body", listOf("home", "work"), updated = 30),
        note("3", "Gamma", "pinned one", listOf("home"), updated = 5, pinned = true),
        note("4", "Delta", "tie", emptyList(), updated = 30),
    )
    eq("sort pinned first then newest then id", NotePolicies.sort(notes).map { it.id }, listOf("3", "2", "4", "1"))
    eq("filter by tag ignoring case", NotePolicies.filter(notes, "", "WORK").map { it.id }.toSet(), setOf("1", "2"))
    eq("filter by words all must match", NotePolicies.filter(notes, "second body", null).map { it.id }, listOf("2"))
    eq("filter word in tag", NotePolicies.filter(notes, "home", null).map { it.id }.toSet(), setOf("2", "3"))
    eq("filter query and tag", NotePolicies.filter(notes, "body", "home").map { it.id }, listOf("2"))
    eq("blank filter returns all", NotePolicies.filter(notes, "   ", null).size, 4)
    eq("all tags by usage then alpha", NotePolicies.allTags(notes), listOf("home", "work"))
    eq("all tags keeps first spelling", NotePolicies.allTags(listOf(note("a", tags = listOf("Zed")), note("b", tags = listOf("zed")))), listOf("Zed"))
    eq("preview collapses whitespace", NotePolicies.preview("a \n\n b   c"), "a b c")
    check("preview ellipsis", NotePolicies.preview("x".repeat(200), 50).endsWith("…"))
    check("preview short untouched", NotePolicies.preview("short") == "short")

    // ---- trash
    val day = TrashPolicies.MILLIS_PER_DAY
    eq("auto delete at", TrashPolicies.autoDeleteAt(1000, 30), 1000 + 30 * day)
    eq("retention floor is 1 day", TrashPolicies.autoDeleteAt(0, 0), day)
    eq("days left rounds up", TrashPolicies.daysLeft(0, day + 1), 2)
    eq("days left exact day", TrashPolicies.daysLeft(0, day), 1)
    eq("days left expired is zero", TrashPolicies.daysLeft(100, 50), 0)
    eq("days left null stays null", TrashPolicies.daysLeft(0, null), null)
    check("expired at boundary", TrashPolicies.isExpired(50, 50))
    check("not expired before", !TrashPolicies.isExpired(49, 50))
    check("null never expires", !TrashPolicies.isExpired(Long.MAX_VALUE, null))
    val trashSorted = TrashPolicies.sortNewestFirst(listOf(
        TrashItem("a", TrashKind.FILE, "a", 1, 10, 5), TrashItem("b", TrashKind.NOTE, "b", null, 20, 5), TrashItem("c", TrashKind.FILE, "c", 1, 20, 5)))
    eq("trash sorted newest then id", trashSorted.map { it.id }, listOf("b", "c", "a"))

    // ---- favorites
    val refs = listOf(
        FavoriteRef("f1", "DOCUMENT", "d1", "stale name", "file:/old", 100),
        FavoriteRef("f2", "DOCUMENT", "gone", "x", "file:/x", 300),
        FavoriteRef("f3", "NOTE", "n1", "old title", null, 200),
        FavoriteRef("f4", "NOTE", "deleted", "t", null, 400),
        FavoriteRef("f5", "BOGUS", "z", "kept", "file:/z", 50),
    )
    val resolved = FavoritePolicies.resolve(refs,
        mapOf("d1" to LiveDocument("fresh.pdf", "file:/new", "application/pdf", 42)),
        mapOf("n1" to LiveNote("Live title")))
    eq("favorites drop missing and sort newest", resolved.map { it.id }, listOf("f3", "f1", "f5"))
    eq("favorite doc uses live name/uri", resolved.first { it.id == "f1" }.displayName to resolved.first { it.id == "f1" }.uri, "fresh.pdf" to "file:/new")
    eq("favorite note uses live title", resolved.first { it.id == "f3" }.displayName, "Live title")
    check("favorite note flagged", resolved.first { it.id == "f3" }.isNote)
    eq("unknown type kept with stored name", resolved.first { it.id == "f5" }.displayName, "kept")

    // ---- history
    val zone = ZoneOffset.UTC
    val today = LocalDate.of(2026, 10, 24)
    fun ts(date: LocalDate, hour: Int) = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    val hist = listOf(
        entry("h1", HistoryAction.CREATED, ts(today, 9)),
        entry("h2", HistoryAction.CONVERTED, ts(today, 15)),
        entry("h3", HistoryAction.DELETED, ts(today.minusDays(1), 8), uri = "file:/x"),
        entry("h4", HistoryAction.EDITED, ts(today.minusDays(5), 8), uri = "file:/y"),
        entry("h5", null, ts(today.minusDays(5), 20)),
    )
    val groups = HistoryPolicies.groupByDay(hist, zone, today)
    eq("group buckets", groups.map { it.bucket }, listOf(DayBucket.TODAY, DayBucket.YESTERDAY, DayBucket.OTHER))
    eq("group newest first inside day", groups[0].entries.map { it.id }, listOf("h2", "h1"))
    eq("other bucket holds both", groups[2].entries.map { it.id }, listOf("h5", "h4"))
    eq("other bucket date", groups[2].date, today.minusDays(5))
    eq("filter processed", HistoryPolicies.filter(hist, HistoryFilter.PROCESSED).map { it.id }, listOf("h2"))
    eq("filter created/edited", HistoryPolicies.filter(hist, HistoryFilter.CREATED_EDITED).map { it.id }.toSet(), setOf("h1", "h4"))
    eq("filter all keeps unknown action", HistoryPolicies.filter(hist, HistoryFilter.ALL).size, 5)
    eq("unknown action parses null", HistoryPolicies.parseAction("NOPE"), null)
    check("deleted entry cannot open", !HistoryPolicies.canOpen(hist[2]))
    check("edited entry with uri can open", HistoryPolicies.canOpen(hist[3]))
    check("entry without uri cannot open", !HistoryPolicies.canOpen(hist[0]))
    // day grouping respects timezone: 23:30 UTC on the 23rd is already the 24th in UTC+2
    val late = listOf(entry("t", HistoryAction.CREATED, today.minusDays(1).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()))
    eq("timezone shifts day", HistoryPolicies.groupByDay(late, ZoneId.of("Europe/Berlin"), today).single().bucket, DayBucket.TODAY)

    // ---- export
    eq("normalize formats", listOf("CSV", " json ", "txt", "text", "pdf", "weird").map { HistoryExportFormatter.normalize(it) },
        listOf(HistoryExportFormat.CSV, HistoryExportFormat.JSON, HistoryExportFormat.TXT, HistoryExportFormat.TXT, HistoryExportFormat.PDF, HistoryExportFormat.PDF))
    eq("formula neutralized", HistoryExportFormatter.neutralizeFormula("=SUM(A1)"), "'=SUM(A1)")
    eq("normal text untouched", HistoryExportFormatter.neutralizeFormula("hello"), "hello")
    val risky = listOf(entry("r", HistoryAction.CREATED, 1, desc = "say \"hi\"\n=2+2", name = "+cmd"))
    val csv = HistoryExportFormatter.csv(risky)
    check("csv header", csv.startsWith("timestamp,action,description,name\n"))
    check("csv doubles quotes", csv.contains("\"say \"\"hi\"\""))
    check("csv name formula neutralized", csv.contains("\"'+cmd\""))
    val json = HistoryExportFormatter.json(risky)
    check("json escapes newline and quotes", json.contains("say \\\"hi\\\"\\n=2+2"))
    check("json null for missing uri", json.contains("\"uri\": null"))
    eq("json empty list", HistoryExportFormatter.json(emptyList()), "[]\n")
    check("json control char escaped", HistoryExportFormatter.jsonString("a\u0001b").contains("\\u0001"))
    eq("text line format", HistoryExportFormatter.textLines(listOf(entry("x", HistoryAction.CREATED, 0, desc = "Made it")), ZoneOffset.UTC).single(), "1970-01-01 00:00  CREATED  Made it")
    val wrapped = HistoryExportFormatter.wrap(listOf("word ".repeat(30).trim()), 40)
    check("wrap respects max", wrapped.all { it.length <= 42 })
    check("wrap keeps all words", wrapped.joinToString(" ").split(Regex("\\s+")).count { it == "word" } == 30)
    eq("wrap short line unchanged", HistoryExportFormatter.wrap(listOf("short"), 40), listOf("short"))
    eq("wrap hard-cuts long word", HistoryExportFormatter.wrap(listOf("x".repeat(25)), 10).first().length, 10)

    // ---- search
    eq("terms strip punctuation", SearchPolicies.terms("  hello, wor!ld  "), listOf("hello", "world"))
    eq("terms empty for symbols", SearchPolicies.terms(" !!! "), emptyList())
    val hits = listOf(
        SearchHit("1", ResourceType.NOTE, "Meeting notes", "", null),
        SearchHit("2", ResourceType.PDF, "invoice.pdf", "", "file:/i"),
        SearchHit("3", ResourceType.DOCUMENT, "Meeting.docx", "", "file:/m"),
    )
    eq("filter notes", SearchPolicies.applyFilter(hits, SearchFilter.NOTES).map { it.resourceId }, listOf("1"))
    eq("filter files", SearchPolicies.applyFilter(hits, SearchFilter.FILES).map { it.resourceId }, listOf("2", "3"))
    eq("filter all", SearchPolicies.applyFilter(hits, SearchFilter.ALL).size, 3)
    eq("rank title matches first", SearchPolicies.rank(listOf(hits[1], hits[0]), listOf("meeting")).map { it.resourceId }, listOf("1", "2"))
    val body = "Lorem ipsum ".repeat(30) + "the QUARTERLY report is due " + "dolor sit ".repeat(30)
    val snip = SearchPolicies.snippet(body, listOf("quarterly"), 100)
    check("snippet contains match", snip.lowercase().contains("quarterly"))
    check("snippet bounded", snip.length <= 106)
    check("snippet has leading ellipsis", snip.startsWith("…"))
    eq("short body unchanged", SearchPolicies.snippet("a  short\nbody", listOf("short")), "a short body")
    eq("highlight word starts only", SearchPolicies.highlightRanges("Cat concat category", listOf("cat")), listOf(0..2, 11..13))
    eq("highlight merges overlapping terms", SearchPolicies.highlightRanges("report reports", listOf("rep", "report")), listOf(0..5, 7..12))
    eq("highlight none for empty terms", SearchPolicies.highlightRanges("abc", emptyList()), emptyList())

    println("Library policy tests: PASS ($checks checks)")
}
