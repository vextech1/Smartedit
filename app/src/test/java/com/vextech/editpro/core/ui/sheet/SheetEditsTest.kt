package com.vextech.editpro.core.ui.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetEditsTest {
    private var now = 1_000L
    private fun edits(initial: String = "a") = SheetEdits(initial, clock = { now }, mergeWindowMs = 800)

    @Test fun editsAreOneStepEach() {
        val e = edits()
        e.edit("b"); e.edit("c")
        assertTrue(e.changed)
        e.undo(); assertEquals("b", e.value)
        e.undo(); assertEquals("a", e.value)
        assertFalse(e.canUndo)
        e.redo(); assertEquals("b", e.value)
    }

    @Test fun quickMergedEditsAreOneStep() {
        val e = edits()
        e.editMerged("t"); now += 100
        e.editMerged("ty"); now += 100
        e.editMerged("typ")
        assertEquals("typ", e.value)
        e.undo()
        assertEquals("a", e.value)
        assertFalse(e.canUndo)
    }

    @Test fun mergedEditsAfterAPauseStartANewStep() {
        val e = edits()
        e.editMerged("t"); now += 100
        e.editMerged("ty"); now += 2_000
        e.editMerged("typo")
        e.undo()
        assertEquals("ty", e.value)
    }

    @Test fun aPlainEditEndsAMergeRun() {
        val e = edits()
        e.editMerged("x"); now += 10
        e.edit("y"); now += 10
        e.editMerged("z")
        e.undo()
        assertEquals("y", e.value)
    }

    @Test fun resetGoesBackAndIsNotAStep() {
        val e = edits()
        e.edit("b"); e.edit("c")
        e.reset()
        assertEquals("a", e.value)
        assertFalse(e.changed)
        assertFalse(e.canUndo)
        assertFalse(e.canRedo)
    }
}
