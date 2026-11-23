package com.vextech.editpro.core.ui.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {
    @Test fun startsWithInitialAndNothingToUndo() {
        val t = Timeline("a")
        assertEquals("a", t.current)
        assertFalse(t.canUndo)
        assertFalse(t.canRedo)
    }

    @Test fun recordUndoRedo() {
        val t = Timeline("a")
        assertTrue(t.record("b"))
        assertTrue(t.record("c"))
        assertEquals("c", t.current)
        assertTrue(t.undo()); assertEquals("b", t.current)
        assertTrue(t.canRedo)
        assertTrue(t.redo()); assertEquals("c", t.current)
        assertFalse(t.redo())
    }

    @Test fun equalSnapshotAddsNothing() {
        val t = Timeline("a")
        assertFalse(t.record("a"))
        assertEquals(1, t.size)
    }

    @Test fun recordingAfterUndoDropsRedo() {
        val t = Timeline("a")
        t.record("b"); t.record("c"); t.undo()
        t.record("x")
        assertEquals("x", t.current)
        assertFalse(t.canRedo)
        assertEquals(3, t.size)
    }

    @Test fun replaceCurrentKeepsOneStep() {
        val t = Timeline("a")
        t.record("b")
        assertTrue(t.replaceCurrent("c"))
        assertEquals(2, t.size)
        t.undo()
        assertEquals("a", t.current)
    }

    @Test fun historyIsCapped() {
        val t = Timeline(0, max = 5)
        for (i in 1..20) t.record(i)
        assertEquals(5, t.size)
        assertEquals(20, t.current)
        repeat(10) { t.undo() }
        assertEquals(16, t.current)
    }

    @Test fun resetStartsOver() {
        val t = Timeline("a")
        t.record("b"); t.record("c")
        t.reset("a")
        assertEquals("a", t.current)
        assertFalse(t.canUndo)
        assertEquals(1, t.size)
    }
}
