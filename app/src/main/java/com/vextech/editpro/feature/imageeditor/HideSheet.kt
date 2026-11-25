package com.vextech.editpro.feature.imageeditor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.image.HideBox
import com.vextech.editpro.core.image.HideForm
import com.vextech.editpro.core.image.HideKind
import com.vextech.editpro.core.image.Redact
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSlider
import com.vextech.editpro.core.ui.sheet.SheetWarning
import com.vextech.editpro.core.ui.sheet.ToolSheet

/** Blur or Hide: drag boxes over faces, plates or addresses and cover them with black, pixels or blur. */
@Composable
internal fun HideSheet(form: HideForm, onForm: (HideForm) -> Unit, onApply: (List<HideBox>) -> Unit, onCancel: () -> Unit) {
    ToolSheet(
        title = stringResource(R.string.hide_title),
        onCancel = onCancel,
        onReset = { onForm(form.cleared()) },
        canReset = form.canApply,
        onApply = { if (form.canApply) onApply(form.boxes) else onCancel() },
    ) {
        SheetChips(
            HideKind.entries.map { it.name to kindName(it) },
            form.kind.name,
            onPick = { onForm(form.copy(kind = HideKind.valueOf(it))) },
        )
        if (form.kind != HideKind.BLACK) {
            SheetSlider(stringResource(R.string.hide_strength), form.strength.toFloat(), Redact.MIN_STRENGTH.toFloat()..Redact.MAX_STRENGTH.toFloat(), { onForm(form.copy(strength = it.toInt())) }, steps = 8)
        }
        SheetHint(stringResource(R.string.hide_hint))
        if (form.boxes.isNotEmpty()) SheetHint(stringResource(R.string.hide_count, form.boxes.size))
        SheetWarning(stringResource(R.string.hide_history_warning))
        OutlinedButton(onClick = { onForm(form.undoLast()) }, enabled = form.canApply, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hide_undo))
        }
    }
}

@Composable
private fun kindName(k: HideKind): String = stringResource(
    when (k) {
        HideKind.BLACK -> R.string.hide_black
        HideKind.PIXELATE -> R.string.hide_pixelate
        HideKind.BLUR -> R.string.hide_blur
    },
)
