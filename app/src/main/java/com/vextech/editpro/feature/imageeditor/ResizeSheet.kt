package com.vextech.editpro.feature.imageeditor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vextech.editpro.R
import com.vextech.editpro.core.image.FitMode
import com.vextech.editpro.core.image.PresetGroup
import com.vextech.editpro.core.image.ResizeForm
import com.vextech.editpro.core.image.ResizeParse
import com.vextech.editpro.core.image.ResizeProblem
import com.vextech.editpro.core.image.ResizeSpec
import com.vextech.editpro.core.image.ResizeUnit
import com.vextech.editpro.core.image.SizeMath
import com.vextech.editpro.core.image.SizePreset
import com.vextech.editpro.core.image.SizePresets
import com.vextech.editpro.core.image.SizeUnit
import com.vextech.editpro.core.ui.sheet.SheetCheck
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSelect
import com.vextech.editpro.core.ui.sheet.SheetSlider
import com.vextech.editpro.core.ui.sheet.SheetSwatches
import com.vextech.editpro.core.ui.sheet.SheetTextField
import com.vextech.editpro.core.ui.sheet.SheetWarning
import com.vextech.editpro.core.ui.sheet.ToolSheet

private const val CUSTOM = "CUSTOM"

@Composable
private fun groupLabel(group: PresetGroup): String = stringResource(
    when (group) {
        PresetGroup.ID -> R.string.resize_group_id
        PresetGroup.FORMS -> R.string.resize_group_forms
        PresetGroup.PRINT -> R.string.resize_group_print
        PresetGroup.SOCIAL -> R.string.resize_group_social
    },
)

/** The name of a size: ID and social sizes have their own names; print and form sizes are named by their numbers. */
@Composable
internal fun presetLabel(p: SizePreset): String {
    val numbers = SizeMath.describe(p.w, p.h, p.unit)
    val kb = if (p.minKB != null && p.maxKB != null) ", ${p.minKB}–${p.maxKB} KB" else ""
    return when (p.id) {
        "us-passport" -> stringResource(R.string.resize_p_us_passport)
        "uk-passport" -> stringResource(R.string.resize_p_uk_passport)
        "schengen" -> stringResource(R.string.resize_p_schengen)
        "canada-passport" -> stringResource(R.string.resize_p_canada)
        "china-visa" -> stringResource(R.string.resize_p_china)
        "australia-passport" -> stringResource(R.string.resize_p_australia)
        "kenya-5x5" -> stringResource(R.string.resize_p_kenya_5x5)
        "kenya-2x2.5" -> stringResource(R.string.resize_p_kenya_2x25)
        "japan-passport" -> stringResource(R.string.resize_p_japan)
        "id-30x40", "id-25x35" -> stringResource(R.string.resize_p_id_photo, numbers)
        "id-20x25" -> stringResource(R.string.resize_p_id_stamp, numbers)
        "form-200x230", "form-35x45" -> stringResource(R.string.resize_p_form_photo, numbers + kb)
        "sign-140x60" -> stringResource(R.string.resize_p_form_sign, numbers + kb)
        "a6", "a5", "a4" -> "${p.id.uppercase()} · $numbers"
        "ig-square" -> stringResource(R.string.resize_p_ig_square)
        "ig-portrait" -> stringResource(R.string.resize_p_ig_portrait)
        "story" -> stringResource(R.string.resize_p_story)
        "yt-thumb" -> stringResource(R.string.resize_p_yt_thumb)
        "profile" -> stringResource(R.string.resize_p_profile)
        "li-banner" -> stringResource(R.string.resize_p_li_banner)
        "fb-cover" -> stringResource(R.string.resize_p_fb_cover)
        "x-header" -> stringResource(R.string.resize_p_x_header)
        else -> numbers
    }
}

@Composable
private fun unitLabel(unit: ResizeUnit): String = stringResource(
    when (unit) {
        ResizeUnit.PX -> R.string.resize_unit_px
        ResizeUnit.MM -> R.string.resize_unit_mm
        ResizeUnit.CM -> R.string.resize_unit_cm
        ResizeUnit.IN -> R.string.resize_unit_in
        ResizeUnit.PERCENT -> R.string.resize_unit_percent
    },
)

/**
 * Resize as a sheet, as on the site: size groups (ID and passport, forms, print, social) or Custom with units and dpi, then how the photo fits.
 * The picture above shows the result live and, when it is cropped to fill, can be dragged to choose what stays in. The tick applies.
 */
@Composable
fun ResizeSheet(
    sourceWidth: Int,
    sourceHeight: Int,
    form: ResizeForm,
    onForm: (ResizeForm) -> Unit,
    onApply: (ResizeSpec) -> Unit,
    onCancel: () -> Unit,
) {
    val start = ResizeForm.start(sourceWidth, sourceHeight)
    val parsed = form.parse(sourceWidth, sourceHeight)
    val ok = parsed as? ResizeParse.Ok
    val preset = form.preset

    ToolSheet(
        title = stringResource(R.string.resize_title),
        onCancel = onCancel,
        onReset = { onForm(start) },
        canReset = form.changedFrom(start),
        onApply = { ok?.let { onApply(it.spec) } },
        busy = ok == null || ok.needsConfirm,
    ) {
        SheetChips(
            PresetGroup.entries.map { it.name to groupLabel(it) } + (CUSTOM to stringResource(R.string.resize_custom)),
            form.group?.name ?: CUSTOM,
            onPick = { id -> onForm(form.withGroup(if (id == CUSTOM) null else PresetGroup.valueOf(id))) },
        )
        if (form.group != null) {
            SheetChips(
                SizePresets.inGroup(form.group).map { it.id to presetLabel(it) },
                form.presetId,
                onPick = { onForm(form.withPreset(it)) },
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetTextField(stringResource(R.string.resize_width), form.width, { onForm(form.copy(width = it).withSide(true, sourceWidth, sourceHeight)) }, Modifier.weight(1f), numeric = true)
                SheetTextField(stringResource(R.string.resize_height), form.height, { onForm(form.copy(height = it).withSide(false, sourceWidth, sourceHeight)) }, Modifier.weight(1f), numeric = true)
            }
            SheetSelect(
                stringResource(R.string.resize_unit),
                ResizeUnit.entries.map { it.name to unitLabel(it) },
                form.unit.name,
                { onForm(form.withUnit(ResizeUnit.valueOf(it), sourceWidth, sourceHeight)) },
            )
            if (form.unit != ResizeUnit.PX && form.unit != ResizeUnit.PERCENT) {
                SheetTextField(stringResource(R.string.resize_dpi), form.dpi, { onForm(form.copy(dpi = it)) }, numeric = true)
            }
            SheetCheck(stringResource(R.string.resize_keep_shape), form.lockShape, { onForm(form.copy(lockShape = it)) })
        }

        ok?.let { SheetHint(sizeLine(it, preset)) }
        if (preset?.group == PresetGroup.ID || preset?.group == PresetGroup.FORMS) SheetHint(stringResource(R.string.resize_note_official))
        preset?.maxKB?.let { kb ->
            SheetHint(stringResource(R.string.resize_note_kb, kb) + (preset.minKB?.let { " " + stringResource(R.string.resize_note_min_kb, it) } ?: ""))
        }
        (parsed as? ResizeParse.Error)?.let { SheetWarning(problemText(it)) }
        if (ok?.enlarged == true) SheetWarning(stringResource(R.string.resize_enlarged))

        SheetChips(
            listOf(
                FitMode.FILL.name to stringResource(R.string.resize_fit_fill),
                FitMode.FIT.name to stringResource(R.string.resize_fit_fit),
                FitMode.STRETCH.name to stringResource(R.string.resize_fit_stretch),
            ),
            form.mode.name,
            onPick = { onForm(form.copy(mode = FitMode.valueOf(it), panX = 0.0, panY = 0.0)) },
        )
        when (form.mode) {
            FitMode.FILL -> {
                SheetSlider(stringResource(R.string.resize_zoom), form.zoom.toFloat(), 1f..4f, { onForm(form.copy(zoom = it.toDouble())) })
                SheetHint(stringResource(R.string.resize_drag_hint))
            }
            FitMode.FIT -> {
                SheetHint(stringResource(R.string.resize_bg))
                SheetSwatches(
                    listOf(
                        "#ffffff" to stringResource(R.string.resize_color_white), "#000000" to stringResource(R.string.resize_color_black),
                        "#f2ead8" to stringResource(R.string.resize_color_cream), "#c9ccd1" to stringResource(R.string.resize_color_grey),
                    ),
                    form.background, { onForm(form.copy(background = it)) },
                )
            }
            FitMode.STRETCH -> Unit
        }
        if (preset?.confirm == true) SheetCheck(stringResource(R.string.resize_confirm), form.confirmed, { onForm(form.copy(confirmed = it)) })
    }
}

@Composable
private fun sizeLine(ok: ResizeParse.Ok, preset: SizePreset?): String {
    val px = stringResource(R.string.resize_size_px, ok.spec.width, ok.spec.height)
    return if (preset != null && preset.unit != SizeUnit.PX) "$px · ${SizeMath.describe(preset.w, preset.h, preset.unit)}" else px
}

@Composable
private fun problemText(e: ResizeParse.Error): String = when (e.problem) {
    ResizeProblem.NOT_A_NUMBER -> stringResource(R.string.resize_err_number)
    ResizeProblem.TOO_SMALL -> stringResource(R.string.resize_err_small, SizeMath.MIN_OUT_SIDE)
    ResizeProblem.TOO_BIG -> stringResource(R.string.resize_err_big, SizeMath.MAX_OUT_SIDE)
}
