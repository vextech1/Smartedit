package com.vextech.smart_edit.feature.pdf.data

import java.io.File
import java.util.ArrayDeque

class WorkingCopyHistory(
    private val directory: File,
    private val maxDepth: Int = 12,
) {
    private val undo = ArrayDeque<File>()
    private val redo = ArrayDeque<File>()
    init { directory.mkdirs() }

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun checkpoint(working: File) {
        require(working.isFile)
        undo.addLast(snapshot(working, "undo"))
        while (undo.size > maxDepth) undo.removeFirst().delete()
        clearDeque(redo)
    }

    fun rollbackCheckpoint() {
        undo.pollLast()?.delete()
    }

    fun undo(working: File): Boolean {
        val previous = undo.pollLast() ?: return false
        redo.addLast(snapshot(working, "redo"))
        previous.copyTo(working, overwrite = true)
        previous.delete()
        return true
    }

    fun redo(working: File): Boolean {
        val next = redo.pollLast() ?: return false
        undo.addLast(snapshot(working, "undo"))
        next.copyTo(working, overwrite = true)
        next.delete()
        return true
    }

    fun markSaved() {
        clearDeque(undo)
        clearDeque(redo)
    }

    fun clear() {
        markSaved()
        directory.deleteRecursively()
    }

    private fun snapshot(source: File, prefix: String): File = File(directory, "${prefix}_${System.nanoTime()}.pdf").also { source.copyTo(it, overwrite = true) }
    private fun clearDeque(deque: ArrayDeque<File>) { while (deque.isNotEmpty()) deque.removeFirst().delete() }
}
