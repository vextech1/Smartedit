package com.vextech.editpro.core.classlibrary

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClassLibraryFiltersTest {
    private fun file(
        id: String,
        title: String,
        category: LibraryCategory = LibraryCategory.NOTES,
        label: String? = null,
        added: String = "2026-01-01",
        subject: String? = "sub",
        cls: String = "c1",
    ) = CloudFile(id, "$title.pdf", title, 10L, "pdf", "s1", cls, subject, category, label, added, 1, 0)

    @Test fun `chips count what is there with built in names first and custom labels after`() {
        val files = listOf(
            file("a", "A", LibraryCategory.OTHER, "Lab manuals"),
            file("b", "B", LibraryCategory.NOTES),
            file("c", "C", LibraryCategory.OTHER, "lab MANUALS"),
            file("d", "D", LibraryCategory.PAST_PAPERS),
            file("e", "E", LibraryCategory.OTHER),
        )
        val chips = ClassLibraryFilters.chips(files)
        assertThat(chips.map { it.key }).containsExactly("notes", "past-papers", "other:lab manuals", "other").inOrder()
        assertThat(chips.first { it.key == "other:lab manuals" }.count).isEqualTo(2)
    }

    @Test fun `the scope is a class and optionally a subject`() {
        val files = listOf(file("a", "A", subject = "x"), file("b", "B", subject = "y"), file("c", "C", cls = "c2"))
        assertThat(ClassLibraryFilters.inScope(files, "s1", "c1", null).map { it.id }).containsExactly("a", "b")
        assertThat(ClassLibraryFilters.inScope(files, "s1", "c1", "x").map { it.id }).containsExactly("a")
    }

    @Test fun `search needs every word and ignores capitals`() {
        val files = listOf(file("a", "Algebra Week 1"), file("b", "Geometry Week 1"))
        assertThat(ClassLibraryFilters.filter(files, ClassLibraryFilters.ALL, "week").map { it.id }).containsExactly("a", "b")
        assertThat(ClassLibraryFilters.filter(files, ClassLibraryFilters.ALL, "ALGEBRA 1").map { it.id }).containsExactly("a")
        assertThat(ClassLibraryFilters.filter(files, ClassLibraryFilters.ALL, "nothing")).isEmpty()
    }

    @Test fun `a category chip keeps only its files`() {
        val files = listOf(file("a", "A", LibraryCategory.NOTES), file("b", "B", LibraryCategory.OTHER, "Lab manuals"))
        assertThat(ClassLibraryFilters.filter(files, "other:lab manuals", "").map { it.id }).containsExactly("b")
        assertThat(ClassLibraryFilters.filter(files, "notes", "").map { it.id }).containsExactly("a")
    }

    @Test fun `newest first and numbers in order`() {
        val files = listOf(
            file("a", "Week 10", added = "2026-01-01"),
            file("b", "Week 2", added = "2026-01-01"),
            file("c", "Old", added = "2025-12-01"),
            file("d", "New", added = "2026-02-01"),
        )
        assertThat(ClassLibraryFilters.sorted(files).map { it.id }).containsExactly("d", "b", "a", "c").inOrder()
    }
}
