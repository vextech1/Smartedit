package com.vextech.editpro.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditStackFlipTest {
    @Test fun aFlipIsRecorded() {
        val stack = EditStack().push(EditOp.Flip(horizontal = true))
        assertEquals(listOf<EditOp>(EditOp.Flip(true)), stack.done)
    }

    @Test fun flippingTheSameWayTwiceCancels() {
        val stack = EditStack().push(EditOp.Flip(true)).push(EditOp.Flip(true))
        assertTrue(stack.isEmpty)
    }

    @Test fun flippingBothWaysKeepsBoth() {
        val stack = EditStack().push(EditOp.Flip(true)).push(EditOp.Flip(false))
        assertEquals(2, stack.done.size)
    }

    @Test fun aFlipCanBeUndoneAndRedone() {
        val stack = EditStack().push(EditOp.Flip(false))
        assertTrue(stack.undo().isEmpty)
        assertEquals(stack.done, stack.undo().redo().done)
    }

    @Test fun flipDoesNotChangeTheOutputSize() {
        assertEquals(400 to 300, EditPolicies.outputSize(400, 300, listOf(EditOp.Flip(true), EditOp.Flip(false))))
    }
}
