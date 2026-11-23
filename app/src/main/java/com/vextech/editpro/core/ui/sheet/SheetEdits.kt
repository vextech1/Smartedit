package com.vextech.editpro.core.ui.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** What the editor's top bar needs from an open sheet. */
interface SheetHistory {
    val canUndo: Boolean
    val canRedo: Boolean
    fun undo()
    fun redo()
}

/** The editor owns one. The newest open sheet registers its history here; the top bar's Undo and Redo use it first. */
@Stable
class SheetHistoryRegistry {
    private var handles by mutableStateOf<List<SheetHistory>>(emptyList())
    val active: SheetHistory? get() = handles.lastOrNull()
    fun register(h: SheetHistory): () -> Unit {
        handles = handles + h
        return { handles = handles.filter { it !== h } }
    }
}

val LocalSheetHistoryRegistry = compositionLocalOf<SheetHistoryRegistry?> { null }

/**
 * The unapplied edits of one sheet: the draft the fields show, plus its undo and redo steps.
 * [edit] adds a step. [editMerged] folds a quick run of changes (typing, dragging a slider) into one step.
 * [reset] goes back to the start and is not an undo step.
 */
@Stable
class SheetEdits<T>(
    private val initial: T,
    private val clock: () -> Long = System::currentTimeMillis,
    private val mergeWindowMs: Long = 800,
) : SheetHistory {
    private val timeline = Timeline(initial)
    private var version by mutableIntStateOf(0)
    private var lastMergedAt = Long.MIN_VALUE
    private var lastWasMerged = false

    val value: T get() { version; return timeline.current }
    val changed: Boolean get() { version; return timeline.current != initial }
    override val canUndo: Boolean get() { version; return timeline.canUndo }
    override val canRedo: Boolean get() { version; return timeline.canRedo }

    fun edit(next: T) {
        if (timeline.record(next)) { lastWasMerged = false; version++ }
    }

    fun editMerged(next: T) {
        val now = clock()
        val merge = lastWasMerged && timeline.canUndo && now - lastMergedAt <= mergeWindowMs
        val done = if (merge) timeline.replaceCurrent(next) else timeline.record(next)
        if (done) { lastWasMerged = true; lastMergedAt = now; version++ }
    }

    override fun undo() { if (timeline.undo()) { lastWasMerged = false; version++ } }
    override fun redo() { if (timeline.redo()) { lastWasMerged = false; version++ } }

    fun reset() { timeline.reset(initial); lastWasMerged = false; version++ }
}

@Composable
fun <T> rememberSheetEdits(initial: T): SheetEdits<T> = remember(initial) { SheetEdits(initial) }

/** Registers [history] with the editor's top bar while the calling sheet is in the composition. */
@Composable
fun RegisterSheetHistory(history: SheetHistory) {
    val registry = LocalSheetHistoryRegistry.current
    DisposableEffect(registry, history) {
        val unregister = registry?.register(history)
        onDispose { unregister?.invoke() }
    }
}
