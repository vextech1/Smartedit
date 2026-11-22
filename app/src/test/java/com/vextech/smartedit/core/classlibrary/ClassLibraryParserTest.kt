package com.vextech.smartedit.core.classlibrary

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClassLibraryParserTest {
    private fun entry(vararg over: Pair<String, Any?>): Map<String, Any?> =
        mapOf<String, Any?>(
            "name" to "week_1-intro.pdf", "title" to "Week 1 Intro", "size" to 1234L, "ext" to "pdf",
            "section" to "s1", "cls" to "c1", "subject" to "x1", "category" to "notes", "added" to "2026-01-02", "parts" to 1L,
        ) + over

    private fun parse(vararg over: Pair<String, Any?>): List<CloudFile> =
        ClassLibraryParser.parseIndex(mapOf("files" to mapOf("f0000000001" to entry(*over))))

    @Test fun `a good entry is read`() {
        val file = parse().single()
        assertThat(file.id).isEqualTo("f0000000001")
        assertThat(file.title).isEqualTo("Week 1 Intro")
        assertThat(file.category).isEqualTo(LibraryCategory.NOTES)
        assertThat(file.size).isEqualTo(1234L)
        assertThat(file.parts).isEqualTo(1)
        assertThat(file.label).isNull()
    }

    @Test fun `the title comes from the file name when missing`() {
        assertThat(parse("title" to null).single().title).isEqualTo("Week 1 Intro")
        assertThat(ClassLibraryParser.titleFromFilename("past_paper-2020.PDF")).isEqualTo("Past Paper 2020")
    }

    @Test fun `bad ids and unsafe names are dropped`() {
        val files = mapOf("../evil" to entry(), "F123" to entry(), "f0000000002" to entry("name" to "a/b.pdf"), "f0000000003" to entry("name" to "a#b.pdf"))
        assertThat(ClassLibraryParser.parseIndex(mapOf("files" to files))).isEmpty()
    }

    @Test fun `unknown types categories and sizes are dropped`() {
        assertThat(parse("ext" to "exe")).isEmpty()
        assertThat(parse("category" to "secret")).isEmpty()
        assertThat(parse("size" to -1L)).isEmpty()
        assertThat(parse("parts" to 0L)).isEmpty()
        assertThat(parse("parts" to 1.5)).isEmpty()
        assertThat(parse("parts" to (ClassLibraryParser.MAX_PARTS + 2).toLong())).isEmpty()
        assertThat(parse("subject" to 7L)).isEmpty()
    }

    @Test fun `an admin label is kept only with the other category and is cleaned`() {
        val other = parse("category" to "other", "label" to "  Lab   manuals ").single()
        assertThat(other.label).isEqualTo("Lab manuals")
        assertThat(parse("category" to "notes", "label" to "Sneaky").single().label).isNull()
        assertThat(parse("category" to "other", "label" to 5L).single().label).isNull()
        assertThat(ClassLibraryParser.cleanLabel("x".repeat(100))).hasLength(ClassLibraryParser.MAX_LABEL)
    }

    @Test fun `numbers from Firestore may be whole doubles`() {
        val file = parse("size" to 2048.0, "parts" to 2.0).single()
        assertThat(file.size).isEqualTo(2048L)
        assertThat(file.parts).isEqualTo(2)
    }

    @Test fun `the shard count falls back to one`() {
        assertThat(ClassLibraryParser.parseShardCount(mapOf("shards" to 3L))).isEqualTo(3)
        assertThat(ClassLibraryParser.parseShardCount(mapOf("shards" to 99L))).isEqualTo(1)
        assertThat(ClassLibraryParser.parseShardCount(mapOf("shards" to "3"))).isEqualTo(1)
        assertThat(ClassLibraryParser.parseShardCount(null)).isEqualTo(1)
        assertThat(ClassLibraryParser.shardDocName(0)).isEqualTo("index")
        assertThat(ClassLibraryParser.shardDocName(2)).isEqualTo("index-2")
    }

    @Test fun `the catalog is read and bad entries skipped`() {
        val data = mapOf(
            "sections" to listOf(
                mapOf(
                    "id" to "f0000000001", "name" to "Secondary",
                    "classes" to listOf(
                        mapOf("id" to "f0000000002", "name" to "Form 1", "subjects" to listOf(mapOf("id" to "f0000000003", "name" to "Maths"), mapOf("id" to "bad", "name" to "Nope"))),
                        mapOf("id" to "f0000000004", "name" to "a/b"),
                    ),
                ),
                mapOf("id" to "nope", "name" to "Skipped"),
            ),
        )
        val sections = ClassLibraryParser.parseStructure(data)
        assertThat(sections).hasSize(1)
        assertThat(sections[0].classes.map { it.name }).containsExactly("Form 1")
        assertThat(sections[0].classes[0].subjects.map { it.name }).containsExactly("Maths")
    }

    @Test fun `pieces are numbered and joined in order`() {
        assertThat(ClassLibraryParser.partId(0)).isEqualTo("0000")
        assertThat(ClassLibraryParser.partId(12)).isEqualTo("0012")
        assertThat(ClassLibraryParser.joinChunks(listOf(byteArrayOf(1, 2), byteArrayOf(3), byteArrayOf()))).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test fun `file types have a mime type`() {
        assertThat(ClassLibraryParser.mimeOf("pdf")).isEqualTo("application/pdf")
        assertThat(ClassLibraryParser.mimeOf("zip")).isEqualTo("application/zip")
        assertThat(ClassLibraryParser.mimeOf("weird")).isEqualTo("application/octet-stream")
    }
}
