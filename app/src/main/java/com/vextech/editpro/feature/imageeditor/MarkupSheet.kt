package com.vextech.editpro.feature.imageeditor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.image.Mark
import com.vextech.editpro.core.image.MarkForm
import com.vextech.editpro.core.image.MarkMath
import com.vextech.editpro.core.image.MarkTool
import com.vextech.editpro.core.ui.sheet.SheetCheck
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSlider
import com.vextech.editpro.core.ui.sheet.SheetSwatches
import com.vextech.editpro.core.ui.sheet.SheetTextField
import com.vextech.editpro.core.ui.sheet.SheetWarning
import com.vextech.editpro.core.ui.sheet.ToolSheet

private fun hex(argb: Int) = "#%06x".format(argb and 0xFFFFFF)

/** Draw & Text: pen, arrow, box, oval and text in six colours. The marks are drawn on the photo in the editor while this is open. */
@Composable
internal fun MarkupSheet(form: MarkForm, onForm: (MarkForm) -> Unit, onApply: (List<Mark>) -> Unit, onCancel: () -> Unit) {
    val names = listOf(
        stringResource(R.string.mark_color_red), stringResource(R.string.mark_color_amber), stringResource(R.string.mark_color_green),
        stringResource(R.string.mark_color_blue), stringResource(R.string.resize_color_white), stringResource(R.string.resize_color_black),
    )
    ToolSheet(
        title = stringResource(R.string.mark_title),
        onCancel = onCancel,
        onReset = { onForm(form.cleared()) },
        canReset = form.canApply,
        onApply = { if (form.canApply) onApply(form.marks) else onCancel() },
    ) {
        SheetChips(
            MarkTool.entries.map { it.name to toolName(it) },
            form.tool.name,
            onPick = { onForm(form.withTool(MarkTool.valueOf(it))) },
        )
        SheetSwatches(
            MarkMath.COLORS.mapIndexed { i, c -> hex(c) to names[i] },
            hex(form.color),
            onPick = { picked -> onForm(form.copy(color = MarkMath.COLORS.first { hex(it) == picked })) },
        )
        if (form.tool == MarkTool.TEXT) {
            SheetSlider(stringResource(R.string.mark_text_size), form.fontLevel.toFloat(), 1f..10f, { onForm(form.copy(fontLevel = it.toInt())) }, steps = 8)
            SheetTextField(stringResource(R.string.mark_text), form.text, { onForm(form.copy(text = it, needText = false)) }, singleLine = false)
            SheetCheck(stringResource(R.string.mark_text_box), form.textBox, { onForm(form.copy(textBox = it)) })
            if (form.needText) SheetWarning(stringResource(R.string.mark_need_text)) else SheetHint(stringResource(R.string.mark_hint_text))
        } else {
            SheetSlider(stringResource(R.string.mark_thickness), form.level.toFloat(), 1f..10f, { onForm(form.copy(level = it.toInt())) }, steps = 8)
            SheetHint(stringResource(R.string.mark_hint_draw))
        }
        OutlinedButton(onClick = { onForm(form.undoLast()) }, enabled = form.canApply, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mark_undo))
        }
    }
}

@Composable
private fun toolName(t: MarkTool): String = stringResource(
    when (t) {
        MarkTool.PEN -> R.string.mark_pen
        MarkTool.ARROW -> R.string.mark_arrow
        MarkTool.BOX -> R.string.mark_box
        MarkTool.OVAL -> R.string.mark_oval
        MarkTool.TEXT -> R.string.mark_text
    },
)
