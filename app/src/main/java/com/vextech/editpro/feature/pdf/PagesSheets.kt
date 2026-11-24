package com.vextech.editpro.feature.pdf

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.pdf.PageRangeProblem
import com.vextech.editpro.core.pdf.PageRangeResult
import com.vextech.editpro.core.pdf.PageRanges
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSelect
import com.vextech.editpro.core.ui.sheet.SheetTextField
import com.vextech.editpro.core.ui.sheet.SheetWarning
import com.vextech.editpro.core.ui.sheet.ToolSheet

/** The message for a mistake in typed page ranges. */
@StringRes
internal fun rangeProblemMessage(problem: PageRangeProblem): Int = when (problem) {
    PageRangeProblem.EMPTY -> R.string.pdf_range_empty
    PageRangeProblem.NOT_A_RANGE -> R.string.pdf_range_not_a_range
    PageRangeProblem.INVALID_RANGE -> R.string.pdf_range_invalid
    PageRangeProblem.PAST_END -> R.string.pdf_range_past_end
}

/**
 * Split into one PDF per page, or one per typed range (`1-3, 5, 7-9`); several parts also come as one ZIP.
 * The ranges are checked as they are typed, and the tick does nothing until they are valid, so no input is lost.
 */
@Composable
fun SplitSheet(
    pageCount: Int,
    busy: Boolean,
    onCancel: () -> Unit,
    onSplit: (ranges: String?) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(MODE_EVERY) }
    var ranges by rememberSaveable { mutableStateOf("") }
    val byRanges = mode == MODE_RANGES
    val problem = if (byRanges) (PageRanges.parseForSplit(ranges, pageCount) as? PageRangeResult.Error) else null

    ToolSheet(
        title = stringResource(R.string.pdf_split),
        onCancel = onCancel,
        onReset = { mode = MODE_EVERY; ranges = "" },
        canReset = byRanges || ranges.isNotEmpty(),
        onApply = {
            if (!byRanges) { onSplit(null); onCancel() }
            else if (problem == null) { onSplit(ranges); onCancel() }
        },
        busy = busy,
    ) {
        SheetHint(stringResource(R.string.pdf_split_summary, pageCount))
        SheetChips(
            options = listOf(MODE_EVERY to stringResource(R.string.pdf_split_every), MODE_RANGES to stringResource(R.string.pdf_split_ranges)),
            selectedId = mode,
            onPick = { mode = it },
        )
        if (byRanges) {
            SheetTextField(
                label = stringResource(R.string.pdf_split_pages_hint),
                value = ranges,
                onChange = { ranges = it },
            )
            if (problem != null) {
                val text = stringResource(rangeProblemMessage(problem.problem))
                // Blank is just the starting state, so it is a hint rather than a warning.
                if (problem.problem == PageRangeProblem.EMPTY) SheetHint(text) else SheetWarning(text)
            }
        }
    }
}

/** What "Arrange" does to the whole document. */
enum class ArrangeOp(@StringRes val label: Int, val needsEvenPages: Boolean = false) {
    REVERSE(R.string.pdf_reverse_order),
    ROTATE_RIGHT(R.string.pdf_rotate_all_right),
    ROTATE_LEFT(R.string.pdf_rotate_all_left),
    DOUBLE_SIDED_REVERSED(R.string.pdf_double_sided_reversed, needsEvenPages = true),
    DOUBLE_SIDED_IN_ORDER(R.string.pdf_double_sided_normal, needsEvenPages = true),
}

/** Whole-document page order and rotation: reverse, rotate all, fix a two-pass double-sided scan. */
@Composable
fun ArrangeSheet(
    pageCount: Int,
    busy: Boolean,
    onCancel: () -> Unit,
    onApply: (ArrangeOp) -> Unit,
) {
    var picked by rememberSaveable { mutableStateOf(ArrangeOp.REVERSE.name) }
    val op = ArrangeOp.valueOf(picked)
    val oddPages = op.needsEvenPages && pageCount % 2 != 0

    ToolSheet(
        title = stringResource(R.string.pdf_arrange),
        onCancel = onCancel,
        onReset = { picked = ArrangeOp.REVERSE.name },
        canReset = op != ArrangeOp.REVERSE,
        onApply = { if (!oddPages) { onApply(op); onCancel() } },
        busy = busy,
    ) {
        SheetSelect(
            label = stringResource(R.string.pdf_arrange),
            options = ArrangeOp.entries.map { it.name to stringResource(it.label) },
            selectedId = picked,
            onSelect = { picked = it },
        )
        if (oddPages) SheetWarning(stringResource(R.string.pdf_double_sided_needs_even))
    }
}
