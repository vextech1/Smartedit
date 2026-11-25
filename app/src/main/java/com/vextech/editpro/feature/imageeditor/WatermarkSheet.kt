package com.vextech.editpro.feature.imageeditor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.image.MarkSpot
import com.vextech.editpro.core.image.WatermarkMath
import com.vextech.editpro.core.image.WatermarkSpec
import com.vextech.editpro.core.image.WatermarkType
import com.vextech.editpro.core.ui.sheet.SheetCheck
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSlider
import com.vextech.editpro.core.ui.sheet.SheetSwatches
import com.vextech.editpro.core.ui.sheet.SheetTextField
import com.vextech.editpro.core.ui.sheet.ToolSheet

private fun hex(argb: Int) = "#%06x".format(argb and 0xFFFFFF)

/** Watermark: words or a logo, in one of nine spots or repeated; drag the mark on the photo and pinch to resize it. */
@Composable
internal fun WatermarkSheet(
    spec: WatermarkSpec,
    logoName: String?,
    onSpec: (WatermarkSpec) -> Unit,
    onPickLogo: () -> Unit,
    onApply: (WatermarkSpec) -> Unit,
    onCancel: () -> Unit,
) {
    val names = listOf(
        stringResource(R.string.resize_color_white), stringResource(R.string.resize_color_black), stringResource(R.string.mark_color_red),
        stringResource(R.string.mark_color_blue), stringResource(R.string.mark_color_amber),
    )
    val tile = spec.spot == MarkSpot.TILE
    ToolSheet(
        title = stringResource(R.string.wm_title),
        onCancel = onCancel,
        onReset = { onSpec(WatermarkSpec()) },
        canReset = spec.changed || logoName != null,
        onApply = { onApply(spec) },
        busy = !spec.ready,
    ) {
        SheetChips(
            listOf(WatermarkType.TEXT.name to stringResource(R.string.wm_type_text), WatermarkType.LOGO.name to stringResource(R.string.wm_type_logo)),
            spec.type.name,
            onPick = { onSpec(spec.copy(type = WatermarkType.valueOf(it))) },
        )
        if (spec.type == WatermarkType.TEXT) {
            SheetTextField(
                stringResource(R.string.wm_text_label), spec.text, { onSpec(spec.copy(text = it)) },
                placeholder = stringResource(R.string.wm_text_placeholder), singleLine = false,
            )
            if (!spec.ready) SheetHint(stringResource(R.string.wm_need_text))
            SheetSwatches(
                WatermarkMath.COLORS.mapIndexed { i, c -> hex(c) to names[i] },
                hex(spec.color),
                onPick = { picked -> onSpec(spec.copy(color = WatermarkMath.COLORS.first { hex(it) == picked })) },
            )
            SheetCheck(stringResource(R.string.wm_outline), spec.outline, { onSpec(spec.copy(outline = it)) })
        } else {
            OutlinedButton(onClick = onPickLogo, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (spec.logoPath != null) R.string.wm_change_logo else R.string.wm_choose_logo))
            }
            if (logoName != null && spec.logoPath != null) SheetHint(logoName) else SheetHint(stringResource(R.string.wm_logo_hint))
        }
        SheetChips(
            MarkSpot.entries.map { it.name to spotName(it) },
            if (spec.at != null) "" else spec.spot.name,
            onPick = { onSpec(spec.withSpot(MarkSpot.valueOf(it))) },
        )
        if (spec.ready) SheetHint(stringResource(if (tile) R.string.wm_hint_repeat else R.string.wm_hint_drag))
        SheetSlider(stringResource(R.string.wm_size, spec.sizePct), spec.sizePct.toFloat(), WatermarkMath.MIN_SIZE.toFloat()..WatermarkMath.MAX_SIZE.toFloat(), { onSpec(spec.copy(sizePct = it.toInt())) })
        SheetSlider(stringResource(R.string.wm_visible, spec.opacityPct), spec.opacityPct.toFloat(), 5f..100f, { onSpec(spec.copy(opacityPct = it.toInt())) })
        SheetSlider(stringResource(R.string.wm_turn, spec.angle), spec.angle.toFloat(), -90f..90f, { onSpec(spec.copy(angle = it.toInt())) })
        if (tile) SheetSlider(stringResource(R.string.wm_spacing, spec.gapPct), spec.gapPct.toFloat(), 0f..300f, { onSpec(spec.copy(gapPct = (it / 5).toInt() * 5)) })
        else if (spec.at == null) SheetSlider(stringResource(R.string.wm_edge, spec.marginPct), spec.marginPct.toFloat(), 0f..15f, { onSpec(spec.copy(marginPct = it.toInt())) })
    }
}

@Composable
private fun spotName(s: MarkSpot): String = stringResource(
    when (s) {
        MarkSpot.TL -> R.string.wm_top_left
        MarkSpot.TC -> R.string.wm_top_centre
        MarkSpot.TR -> R.string.wm_top_right
        MarkSpot.ML -> R.string.wm_middle_left
        MarkSpot.MC -> R.string.wm_centre
        MarkSpot.MR -> R.string.wm_middle_right
        MarkSpot.BL -> R.string.wm_bottom_left
        MarkSpot.BC -> R.string.wm_bottom_centre
        MarkSpot.BR -> R.string.wm_bottom_right
        MarkSpot.TILE -> R.string.wm_repeat
    },
)
