package com.vextech.editpro.feature.pdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.pdf.PageRangeProblem
import com.vextech.editpro.core.pdf.PageRangeResult
import com.vextech.editpro.core.pdf.PageRanges
import com.vextech.editpro.core.pdf.WatermarkDraft
import com.vextech.editpro.core.pdf.WatermarkKind
import com.vextech.editpro.core.pdf.WatermarkLayout
import com.vextech.editpro.core.pdf.WatermarkPos
import com.vextech.editpro.core.pdf.WatermarkProblem
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSlider
import com.vextech.editpro.core.ui.sheet.SheetSwatches
import com.vextech.editpro.core.ui.sheet.SheetTextField
import com.vextech.editpro.core.ui.sheet.SheetWarning
import com.vextech.editpro.core.ui.sheet.ToolSheet
import com.vextech.editpro.core.ui.sheet.rememberSheetEdits
import kotlin.math.roundToInt

/** Everything the Watermark sheet edits before the tick: the settings, the chosen logo picture and the page box. */
data class WatermarkSheetState(
    val draft: WatermarkDraft = WatermarkDraft(),
    val logo: Uri? = null,
    val pagesText: String = "",
)

/**
 * The Watermark tool as a sheet, with the controls of the site's PDF Watermark sheet: text or logo, nine spots or repeat,
 * size, how visible, turn, edge distance (or spacing for a repeat), and which pages.
 * [onApply] gets the settings, the logo (null for text) and the 0-based pages (null for all).
 */
@Composable
fun WatermarkSheet(pageCount: Int, onCancel: () -> Unit, onApply: (WatermarkDraft, Uri?, Set<Int>?) -> Unit) {
    val edits = rememberSheetEdits(WatermarkSheetState())
    val state = edits.value
    val d = state.draft
    fun setDraft(next: WatermarkDraft) = edits.edit(state.copy(draft = next))
    fun slide(next: WatermarkDraft) = edits.editMerged(state.copy(draft = next))

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) edits.edit(state.copy(logo = uri))
    }

    val range = PageRanges.parse(state.pagesText, pageCount)
    val rangeError = range as? PageRangeResult.Error
    val problem = WatermarkLayout.validate(d, state.logo != null)

    ToolSheet(
        title = stringResource(R.string.pdf_watermark),
        onCancel = onCancel,
        onReset = edits::reset,
        canReset = edits.changed,
        onApply = {
            val pages = if (state.pagesText.isBlank()) null else (range as? PageRangeResult.Ok)?.pages?.toSet()
            onApply(d, if (d.kind == WatermarkKind.LOGO) state.logo else null, pages)
        },
        busy = problem != null || rangeError != null,
        history = edits,
    ) {
        SheetChips(
            listOf(WatermarkKind.TEXT.name to stringResource(R.string.wm_kind_text), WatermarkKind.LOGO.name to stringResource(R.string.wm_kind_logo)),
            d.kind.name,
            { setDraft(d.copy(kind = WatermarkKind.valueOf(it))) },
        )
        if (d.kind == WatermarkKind.TEXT) {
            SheetTextField(stringResource(R.string.pdf_watermark_text), d.text, { slide(d.copy(text = it)) })
            if (problem == WatermarkProblem.UNSUPPORTED_CHAR) SheetWarning(stringResource(R.string.wm_unsupported_text))
            SheetSwatches(
                listOf(
                    "#cc0000" to stringResource(R.string.wm_color_red),
                    "#000000" to stringResource(R.string.wm_color_black),
                    "#6b7280" to stringResource(R.string.wm_color_gray),
                    "#2563eb" to stringResource(R.string.wm_color_blue),
                    "#f59e0b" to stringResource(R.string.wm_color_amber),
                ),
                d.colorHex,
                { setDraft(d.copy(colorHex = it)) },
            )
        } else {
            OutlinedButton(onClick = { logoPicker.launch("image/*") }) {
                Text(stringResource(if (state.logo == null) R.string.wm_choose_logo else R.string.wm_change_logo))
            }
            SheetHint(stringResource(if (state.logo == null) R.string.wm_logo_hint else R.string.wm_logo_chosen))
        }
        SheetChips(positionOptions(), d.pos.name, { setDraft(d.copy(pos = WatermarkPos.valueOf(it))) })
        SheetSlider(stringResource(R.string.wm_size, d.sizePct), d.sizePct.toFloat(), 5f..100f, { slide(d.copy(sizePct = it.roundToInt())) })
        SheetSlider(stringResource(R.string.wm_visible, d.opacityPct), d.opacityPct.toFloat(), 5f..100f, { slide(d.copy(opacityPct = it.roundToInt())) })
        SheetSlider(stringResource(R.string.wm_turn, d.angle), d.angle.toFloat(), -90f..90f, { slide(d.copy(angle = it.roundToInt())) })
        if (d.pos == WatermarkPos.TILE) {
            SheetSlider(stringResource(R.string.wm_spacing, d.gapPct), d.gapPct.toFloat(), 0f..300f, { slide(d.copy(gapPct = (it / 5f).roundToInt() * 5)) })
        } else {
            SheetSlider(stringResource(R.string.wm_edge, d.marginPct), d.marginPct.toFloat(), 0f..15f, { slide(d.copy(marginPct = it.roundToInt())) })
        }
        SheetTextField(stringResource(R.string.wm_pages), state.pagesText, { edits.editMerged(state.copy(pagesText = it)) }, placeholder = stringResource(R.string.wm_pages_hint))
        if (rangeError != null) SheetWarning(rangeMessage(rangeError))
        SheetHint(stringResource(R.string.wm_removable_note))
    }
}

@Composable
private fun positionOptions(): List<Pair<String, String>> = listOf(
    WatermarkPos.TL to R.string.wm_pos_tl, WatermarkPos.TC to R.string.wm_pos_tc, WatermarkPos.TR to R.string.wm_pos_tr,
    WatermarkPos.ML to R.string.wm_pos_ml, WatermarkPos.MC to R.string.wm_pos_mc, WatermarkPos.MR to R.string.wm_pos_mr,
    WatermarkPos.BL to R.string.wm_pos_bl, WatermarkPos.BC to R.string.wm_pos_bc, WatermarkPos.BR to R.string.wm_pos_br,
    WatermarkPos.TILE to R.string.wm_pos_repeat,
).map { (pos, label) -> pos.name to stringResource(label) }

@Composable
private fun rangeMessage(error: PageRangeResult.Error): String = when (error.problem) {
    PageRangeProblem.PAST_END -> stringResource(R.string.wm_range_past_end, error.text, error.total)
    else -> stringResource(R.string.wm_range_invalid, error.text)
}
