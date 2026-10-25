package com.vextech.smart_edit.feature.pdf.data

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

class WorkingCopyHistoryTest {
    @Test fun undoRedoAndSaveBoundary() {
        val root = Files.createTempDirectory("pdf-history").toFile()
        try {
            val working = File(root, "working.pdf").apply { writeText("v1") }
            val history = WorkingCopyHistory(File(root, "history"), maxDepth = 3)
            history.checkpoint(working); working.writeText("v2")
            history.checkpoint(working); working.writeText("v3")
            assertThat(history.undo(working)).isTrue(); assertThat(working.readText()).isEqualTo("v2")
            assertThat(history.undo(working)).isTrue(); assertThat(working.readText()).isEqualTo("v1")
            assertThat(history.redo(working)).isTrue(); assertThat(working.readText()).isEqualTo("v2")
            history.markSaved()
            assertThat(history.canUndo).isFalse(); assertThat(history.canRedo).isFalse()
        } finally { root.deleteRecursively() }
    }

    @Test fun failedMutationCanDropCheckpoint() {
        val root = Files.createTempDirectory("pdf-history-rollback").toFile()
        try {
            val working = File(root, "working.pdf").apply { writeText("base") }
            val history = WorkingCopyHistory(File(root, "history"))
            history.checkpoint(working); history.rollbackCheckpoint()
            assertThat(history.canUndo).isFalse(); assertThat(working.readText()).isEqualTo("base")
        } finally { root.deleteRecursively() }
    }
}
