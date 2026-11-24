package com.vextech.editpro.feature.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.pdf.CropForm
import com.vextech.editpro.core.pdf.CropMode
import com.vextech.editpro.core.pdf.CropOptions
import com.vextech.editpro.core.pdf.CropParse
import com.vextech.editpro.core.pdf.CropProblem
import com.vextech.editpro.core.pdf.Paper
import com.vextech.editpro.core.pdf.SheetOrientation
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetTextField
import com.vextech.editpro.core.ui.sheet.SheetWarning
import com.vextech.editpro.core.ui.sheet.ToolSheet
import com.vextech.editpro.core.ui.sheet.rememberSheetEdits
import com.vextech.editpro.feature.pdf.domain.PdfPageInfo

/**
 * The site's Crop PDF as a sheet: trim margins (millimetres, as the page looks) or fit pages to A3, A4, A5, Letter or Legal,
 * for every page or the pages typed like `1-3, 5`. Edits stay unapplied until the tick; Reset puts the start values back.
 */
@Composable
fun CropSheet(
    pages: List<PdfPageInfo>,
    onPreview: (CropForm?) -> Unit,
    onCancel: () -> Unit,
    onApply: (CropOptions) -> Unit,
) {
    val edits = rememberSheetEdits(CropForm())
    val form = edits.value
    val parsed = form.parse(pages.size, pages)
    LaunchedEffect(form) { onPreview(form) }          // the page behind the sheet shades what Trim would hide
    DisposableEffect(Unit) { onDispose { onPreview(null) } }
    val error = parsed as? CropParse.Error

    ToolSheet(
        title = stringResource(R.string.crop_title),
        onCancel = onCancel,
        onReset = edits::reset,
        canReset = edits.changed,
        onApply = { (parsed as? CropParse.Ok)?.let { onApply(it.options) } },
        busy = parsed !is CropParse.Ok,
        history = edits,
    ) {
        SheetChips(
            listOf(CropMode.TRIM.name to stringResource(R.string.crop_mode_trim), CropMode.RESIZE.name to stringResource(R.string.crop_mode_resize)),
            form.mode.name,
            { edits.edit(form.copy(mode = CropMode.valueOf(it))) },
        )
        if (form.mode == CropMode.TRIM) {
            SheetTextField(stringResource(R.string.crop_top), form.top, { edits.editMerged(form.copy(top = it)) }, numeric = true)
            SheetTextField(stringResource(R.string.crop_bottom), form.bottom, { edits.editMerged(form.copy(bottom = it)) }, numeric = true)
            SheetTextField(stringResource(R.string.crop_left), form.left, { edits.editMerged(form.copy(left = it)) }, numeric = true)
            SheetTextField(stringResource(R.string.crop_right), form.right, { edits.editMerged(form.copy(right = it)) }, numeric = true)
            SheetHint(stringResource(R.string.crop_trim_note))
        } else {
            SheetChips(
                Paper.entries.map { it.name to paperName(it) },
                form.paper.name,
                { edits.edit(form.copy(paper = Paper.valueOf(it))) },
            )
            SheetChips(
                listOf(
                    SheetOrientation.AUTO.name to stringResource(R.string.crop_orient_auto),
                    SheetOrientation.PORTRAIT.name to stringResource(R.string.crop_orient_portrait),
                    SheetOrientation.LANDSCAPE.name to stringResource(R.string.crop_orient_landscape),
                ),
                form.orientation.name,
                { edits.edit(form.copy(orientation = SheetOrientation.valueOf(it))) },
            )
            SheetTextField(stringResource(R.string.crop_margin), form.margin, { edits.editMerged(form.copy(margin = it)) }, numeric = true)
            SheetHint(stringResource(R.string.crop_resize_note))
        }
        SheetTextField(stringResource(R.string.crop_pages), form.pages, { edits.editMerged(form.copy(pages = it)) }, placeholder = "1-3, 5")
        if (error != null) SheetWarning(problemMessage(error))
    }
}

@Composable
private fun paperName(paper: Paper): String = when (paper) {
    Paper.A3 -> "A3"
    Paper.A4 -> "A4"
    Paper.A5 -> "A5"
    Paper.LETTER -> stringResource(R.string.crop_paper_letter)
    Paper.LEGAL -> stringResource(R.string.crop_paper_legal)
}

@Composable
private fun problemMessage(e: CropParse.Error): String = when (e.problem) {
    CropProblem.MARGIN_NUMBER -> stringResource(R.string.crop_err_margin)
    CropProblem.PAPER_MARGIN_NUMBER -> stringResource(R.string.crop_err_margin)
    CropProblem.PAPER_MARGIN_TOO_LARGE -> stringResource(R.string.crop_err_paper_margin)
    CropProblem.PAGES_NOT_A_RANGE -> stringResource(R.string.crop_err_pages, e.text)
    CropProblem.PAGES_INVALID -> stringResource(R.string.crop_err_pages_invalid, e.text)
    CropProblem.PAGES_PAST_END -> stringResource(R.string.crop_err_pages_past_end, e.total)
    CropProblem.TRIM_TOO_LARGE_FOR_PAGE -> stringResource(R.string.crop_err_trim_page, e.page)
}
