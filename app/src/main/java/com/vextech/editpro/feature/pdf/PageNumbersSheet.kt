package com.vextech.editpro.feature.pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.pdf.StampFont
import com.vextech.editpro.core.pdf.StampForm
import com.vextech.editpro.core.pdf.StampOptions
import com.vextech.editpro.core.pdf.StampParse
import com.vextech.editpro.core.pdf.StampProblem
import com.vextech.editpro.core.pdf.StampZones
import com.vextech.editpro.core.ui.sheet.SheetCheck
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetTextField
import com.vextech.editpro.core.ui.sheet.SheetWarning
import com.vextech.editpro.core.ui.sheet.ToolSheet
import com.vextech.editpro.core.ui.sheet.rememberSheetEdits

/**
 * Page numbers, headers, footers and Bates numbers as a sheet, with the site's options: ready-made styles or your own text for
 * the six spots, which pages, first number, font, size, margin in mm, gray, and Bates prefix, start and digits.
 * [fileName] (without ".pdf") and [date] (`YYYY-MM-DD`) fill the {file} and {date} tokens.
 */
@Composable
fun PageNumbersSheet(pageCount: Int, fileName: String, date: String, onCancel: () -> Unit, onApply: (StampOptions) -> Unit) {
    val edits = rememberSheetEdits(StampForm())
    val form = edits.value
    val parsed = form.parse(pageCount, fileName, date)
    val error = (parsed as? StampParse.Error)?.error

    fun typed(next: StampForm) = edits.editMerged(next.copy(presetId = "custom"))
    fun field(next: StampForm) = edits.editMerged(next)

    ToolSheet(
        title = stringResource(R.string.pdf_page_numbers),
        onCancel = onCancel,
        onReset = edits::reset,
        canReset = edits.changed,
        onApply = { (parsed as? StampParse.Ok)?.let { onApply(it.options) } },
        busy = parsed !is StampParse.Ok,
        history = edits,
    ) {
        SheetChips(styleOptions(), form.presetId, { id -> edits.edit(form.withPreset(id)) })

        SheetHint(stringResource(R.string.pn_header))
        SheetTextField(stringResource(R.string.pn_left), form.header.left, { typed(form.copy(header = form.header.copy(left = it))) })
        SheetTextField(stringResource(R.string.pn_centre), form.header.center, { typed(form.copy(header = form.header.copy(center = it))) })
        SheetTextField(stringResource(R.string.pn_right), form.header.right, { typed(form.copy(header = form.header.copy(right = it))) })

        SheetHint(stringResource(R.string.pn_footer))
        SheetTextField(stringResource(R.string.pn_left), form.footer.left, { typed(form.copy(footer = form.footer.copy(left = it))) })
        SheetTextField(stringResource(R.string.pn_centre), form.footer.center, { typed(form.copy(footer = form.footer.copy(center = it))) })
        SheetTextField(stringResource(R.string.pn_right), form.footer.right, { typed(form.copy(footer = form.footer.copy(right = it))) })
        SheetHint(stringResource(R.string.pn_tokens_hint))

        SheetTextField(stringResource(R.string.pn_from), form.from, { field(form.copy(from = it)) }, numeric = true)
        SheetTextField(stringResource(R.string.pn_to), form.to, { field(form.copy(to = it)) }, numeric = true)
        SheetTextField(stringResource(R.string.pn_start), form.start, { field(form.copy(start = it)) }, numeric = true)

        SheetChips(
            listOf(
                StampFont.HELVETICA.name to stringResource(R.string.pn_font_sans),
                StampFont.TIMES.name to stringResource(R.string.pn_font_serif),
                StampFont.COURIER.name to stringResource(R.string.pn_font_mono),
            ),
            form.font.name,
            { edits.edit(form.copy(font = StampFont.valueOf(it))) },
        )
        SheetTextField(stringResource(R.string.pn_size), form.size, { field(form.copy(size = it)) }, numeric = true)
        SheetTextField(stringResource(R.string.pn_margin), form.marginMm, { field(form.copy(marginMm = it)) }, numeric = true)
        SheetCheck(stringResource(R.string.pn_gray), form.gray, { edits.edit(form.copy(gray = it)) })

        if (usesBates(form.header) || usesBates(form.footer)) {
            SheetTextField(stringResource(R.string.pn_bates_prefix), form.batesPrefix, { field(form.copy(batesPrefix = it)) })
            SheetTextField(stringResource(R.string.pn_bates_start), form.batesStart, { field(form.copy(batesStart = it)) }, numeric = true)
            SheetTextField(stringResource(R.string.pn_bates_digits), form.batesDigits, { field(form.copy(batesDigits = it)) }, numeric = true)
        }

        if (error != null) SheetWarning(errorMessage(error.problem, error.page, error.total, error.char))
        SheetHint(stringResource(R.string.pn_western_note))
    }
}

private fun usesBates(z: StampZones): Boolean = z.all().any { it.contains("{bates}", ignoreCase = true) }

@Composable
private fun styleOptions(): List<Pair<String, String>> = listOf(
    "number" to R.string.pn_style_number,
    "page_of" to R.string.pn_style_page_of,
    "right" to R.string.pn_style_right,
    "top_right" to R.string.pn_style_top_right,
    "file_and_page" to R.string.pn_style_file_and_page,
    "bates" to R.string.pn_style_bates,
    "custom" to R.string.pn_style_custom,
).map { (id, label) -> id to stringResource(label) }

@Composable
private fun errorMessage(problem: StampProblem, page: Int, total: Int, char: Char?): String = when (problem) {
    StampProblem.SIZE -> stringResource(R.string.pn_err_size)
    StampProblem.MARGIN -> stringResource(R.string.pn_err_margin)
    StampProblem.START_NUMBER -> stringResource(R.string.pn_err_start)
    StampProblem.FROM_PAGE -> stringResource(R.string.pn_err_from)
    StampProblem.TO_PAGE -> stringResource(R.string.pn_err_to)
    StampProblem.PAGE_PAST_END -> stringResource(R.string.pn_err_past_end, page, total)
    StampProblem.TO_BEFORE_FROM -> stringResource(R.string.pn_err_to_before)
    StampProblem.BATES_DIGITS -> stringResource(R.string.pn_err_digits)
    StampProblem.BATES_START -> stringResource(R.string.pn_err_bates_start)
    StampProblem.NO_TEXT -> stringResource(R.string.pn_err_no_text)
    StampProblem.UNSUPPORTED_CHAR -> stringResource(R.string.pn_err_char, char?.toString().orEmpty())
}
