package com.vextech.editpro.core.ui.sheet

/**
 * A list of snapshots with a position in it: the step-by-step history behind Undo and Redo for edits a sheet has not applied yet.
 * Pure Kotlin so it is unit tested. [current] is always the snapshot the screen is showing. Same behaviour as the site's `core/timeline.ts`.
 */
class Timeline<T>(
    initial: T,
    private val same: (T, T) -> Boolean = { a, b -> a == b },
    private val max: Int = 100,
) {
    private var steps: List<T> = listOf(initial)
    private var pos = 0

    val current: T get() = steps[pos]
    val canUndo: Boolean get() = pos > 0
    val canRedo: Boolean get() = pos < steps.size - 1
    val size: Int get() = steps.size

    /** Adds a step and drops anything that could have been redone. A snapshot equal to the current one adds nothing (returns false). */
    fun record(next: T): Boolean {
        if (same(current, next)) return false
        var list = steps.subList(0, pos + 1) + next
        if (list.size > max) list = list.subList(list.size - max, list.size)
        steps = list
        pos = steps.size - 1
        return true
    }

    /** Replaces the current step instead of adding one (a drag or a burst of typing is one step). False when nothing changed. */
    fun replaceCurrent(next: T): Boolean {
        if (same(current, next)) return false
        steps = steps.subList(0, pos) + next + steps.subList(pos + 1, steps.size)
        return true
    }

    fun undo(): Boolean {
        if (!canUndo) return false
        pos--
        return true
    }

    fun redo(): Boolean {
        if (!canRedo) return false
        pos++
        return true
    }

    /** Forgets every step and starts again from [initial] (what Reset does: it is not an undo step). */
    fun reset(initial: T) {
        steps = listOf(initial)
        pos = 0
    }
}
