package com.vextech.smart_edit.feature.files

import com.google.common.truth.Truth.assertThat
import com.vextech.smart_edit.feature.files.data.ManagedFileOperator
import com.vextech.smart_edit.feature.files.domain.FileItem
import com.vextech.smart_edit.feature.files.domain.FileNamePolicy
import com.vextech.smart_edit.feature.files.domain.FileSort
import com.vextech.smart_edit.feature.files.domain.FileTreePolicy
import java.nio.file.Files
import org.junit.Test

class FilePoliciesTest {
    @Test
    fun fileNamePolicy_acceptsTrimmedSafeName() {
        assertThat(FileNamePolicy.validateUserName("  Report 2026.pdf  ")).isEqualTo("Report 2026.pdf")
    }

    @Test
    fun fileNamePolicy_rejectsTraversalSeparatorsControlsAndReservedCharacters() {
        listOf("", ".", "..", "../secret", "a/b", "a\\b", "my..file", "bad:name", "line\nbreak")
            .forEach { name -> assertThat(runCatching { FileNamePolicy.validateUserName(name) }.isFailure).isTrue() }
    }

    @Test
    fun treePolicy_buildsBreadcrumbsAndExcludesDescendantMoveTargets() {
        val items = listOf(
            item("a", "Alpha", directory = true),
            item("b", "Beta", parentId = "a", directory = true),
            item("c", "Gamma", parentId = "b", directory = true),
        )
        assertThat(FileTreePolicy.breadcrumbs(items, "c").map { it.name })
            .containsExactly("", "Alpha", "Beta", "Gamma").inOrder()
        assertThat(FileTreePolicy.moveTargets(items, "a").map { it.id }).doesNotContain("b")
        assertThat(FileTreePolicy.moveTargets(items, "a").map { it.id }).doesNotContain("c")
    }

    @Test
    fun visibleChildren_keepsFoldersBeforeFilesForBothSortDirections() {
        val items = listOf(
            item("f1", "z.txt", size = 10),
            item("d1", "Zoo", directory = true),
            item("f2", "a.txt", size = 20),
            item("d2", "Alpha", directory = true),
        )
        assertThat(FileTreePolicy.visibleChildren(items, null, FileSort.NAME, true).map { it.name })
            .containsExactly("Alpha", "Zoo", "a.txt", "z.txt").inOrder()
        assertThat(FileTreePolicy.visibleChildren(items, null, FileSort.NAME, false).map { it.name })
            .containsExactly("Zoo", "Alpha", "z.txt", "a.txt").inOrder()
    }

    @Test
    fun managedFileOperator_movesRollsBackAndProtectsCollisions() {
        val root = Files.createTempDirectory("smartedit-file-policy-test").toFile()
        try {
            val source = root.resolve("source.txt").apply { writeText("source") }
            val target = root.resolve("nested/target.txt")
            ManagedFileOperator.relocate(source, target)
            assertThat(source.exists()).isFalse()
            assertThat(target.readText()).isEqualTo("source")

            ManagedFileOperator.rollback(target, source)
            assertThat(source.readText()).isEqualTo("source")
            assertThat(target.exists()).isFalse()

            val occupied = root.resolve("occupied.txt").apply { writeText("occupied") }
            assertThat(runCatching { ManagedFileOperator.relocate(source, occupied) }.isFailure).isTrue()
            assertThat(source.readText()).isEqualTo("source")
            assertThat(occupied.readText()).isEqualTo("occupied")
        } finally {
            root.deleteRecursively()
        }
    }

    private fun item(
        id: String,
        name: String,
        parentId: String? = null,
        directory: Boolean = false,
        size: Long = 0,
    ) = FileItem(
        id = id,
        name = name,
        uri = "file:///$name",
        mimeType = if (directory) "inode/directory" else "text/plain",
        sizeBytes = size,
        createdAt = 0,
        modifiedAt = 0,
        parentId = parentId,
        isDirectory = directory,
        source = "managed",
        isFavorite = false,
    )
}
