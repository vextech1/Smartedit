package com.vextech.editpro.feature.imageeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.image.PrintSheetForm
import com.vextech.editpro.core.image.PrintSheets
import com.vextech.editpro.core.image.SizeMath
import com.vextech.editpro.core.ui.sheet.SheetCheck
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSelect
import com.vextech.editpro.core.ui.sheet.SheetTextField
import com.vextech.editpro.core.ui.sheet.SheetWarning
import com.vextech.editpro.core.ui.sheet.ToolSheet

private const val CUSTOM = "CUSTOM"

/** Photo sheet: repeats the picture on a page of paper as a one-page PDF to print, with optional cutting lines. */
@Composable
internal fun PrintSheetSheet(form: PrintSheetForm, onForm: (PrintSheetForm) -> Unit, onMake: (PrintSheetForm) -> Unit, onCancel: () -> Unit) {
    val layout = form.layout()
    ToolSheet(
        title = stringResource(R.string.print_title),
        onCancel = onCancel,
        onReset = { onForm(PrintSheetForm()) },
        canReset = form != PrintSheetForm(),
        onApply = { onMake(form) },
        busy = !form.ready,
    ) {
        SheetSelect(
            stringResource(R.string.print_paper),
            PrintSheets.papers.map { it.id to paperName(it.id) },
            form.paperId,
            { onForm(form.copy(paperId = it)) },
        )
        SheetSelect(
            stringResource(R.string.print_photo_size),
            PrintSheets.sizes.map { it.id to presetLabel(it) } + (CUSTOM to stringResource(R.string.resize_custom)),
            form.sizeId.ifEmpty { CUSTOM },
            { onForm(if (it == CUSTOM) form.copy(sizeId = "") else form.copy(sizeId = it)) },
        )
        if (form.sizeId.isEmpty()) {
            SheetTextField(stringResource(R.string.print_width_mm), form.widthMm, { onForm(form.copy(widthMm = it)) }, numeric = true)
            SheetTextField(stringResource(R.string.print_height_mm), form.heightMm, { onForm(form.copy(heightMm = it)) }, numeric = true)
        }
        SheetSelect(
            stringResource(R.string.print_copies),
            PrintSheets.copyChoices.map { it.toString() to if (it == 0) stringResource(R.string.print_fill) else it.toString() },
            form.copies.toString(),
            { onForm(form.copy(copies = it.toInt())) },
        )
        SheetCheck(stringResource(R.string.print_guides), form.guides, { onForm(form.copy(guides = it)) })
        when {
            layout == null -> SheetWarning(stringResource(R.string.print_bad_size))
            layout.count == 0 -> SheetWarning(stringResource(R.string.print_too_big))
            else -> SheetHint(
                stringResource(if (layout.landscape) R.string.print_fits_sideways else R.string.print_fits, layout.count),
            )
        }
    }
}

@Composable
private fun paperName(id: String): String = when (id) {
    "4x6" -> "4 × 6 in"
    "5x7" -> "5 × 7 in"
    "a4" -> "A4"
    else -> stringResource(R.string.print_letter)
}
