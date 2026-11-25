package com.vextech.editpro.feature.imageeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.image.RoundForm
import com.vextech.editpro.core.image.RoundShapes
import com.vextech.editpro.core.image.RoundSpec
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSlider
import com.vextech.editpro.core.ui.sheet.ToolSheet

private const val CUSTOM = "CUSTOM"

/** Round: pick a ready-made shape or set the picture shape and corner roundness yourself; drag the photo to choose what stays in. */
@Composable
internal fun RoundSheet(form: RoundForm, onForm: (RoundForm) -> Unit, onApply: (RoundSpec) -> Unit, onCancel: () -> Unit) {
    val start = RoundForm()
    ToolSheet(
        title = stringResource(R.string.round_title),
        onCancel = onCancel,
        onReset = { onForm(start) },
        canReset = form.changed,
        onApply = { onApply(form.spec()) },
    ) {
        SheetChips(
            RoundShapes.presets.map { it.id to presetName(it.id) } + (CUSTOM to stringResource(R.string.resize_custom)),
            form.presetId ?: CUSTOM,
            onPick = { id -> if (id != CUSTOM) onForm(form.withPreset(id)) },
        )
        SheetSlider(stringResource(R.string.round_roundness), form.radius.toFloat(), 0f..RoundShapes.MAX_RADIUS.toFloat(), { onForm(form.copy(radius = it.toInt())) })
        SheetHint(stringResource(R.string.round_shape))
        SheetChips(
            RoundShapes.ratios.map { it.id to if (it.id == RoundShapes.ORIGINAL) stringResource(R.string.round_original) else it.id },
            form.ratioId,
            onPick = { onForm(form.withRatio(it)) },
        )
        SheetSlider(stringResource(R.string.resize_zoom), form.zoom.toFloat(), 1f..4f, { onForm(form.copy(zoom = it.toDouble())) })
        SheetHint(stringResource(R.string.resize_drag_hint))
    }
}

@Composable
private fun presetName(id: String): String = stringResource(
    when (id) {
        RoundShapes.CIRCLE -> R.string.round_circle
        "rounded" -> R.string.round_rounded
        else -> R.string.round_soft
    },
)
