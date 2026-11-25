package com.vextech.editpro.feature.imageeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.image.FitMode
import com.vextech.editpro.core.image.FrameForm
import com.vextech.editpro.core.image.FrameKind
import com.vextech.editpro.core.image.FrameSpec
import com.vextech.editpro.core.image.Frames
import com.vextech.editpro.core.image.PhoneBackground
import com.vextech.editpro.core.image.PhoneColor
import com.vextech.editpro.core.image.PhoneFrameSpec
import com.vextech.editpro.core.image.PhoneStyle
import com.vextech.editpro.core.image.WallBackdrop
import com.vextech.editpro.core.image.WallFrameSpec
import com.vextech.editpro.core.image.WallStyle
import com.vextech.editpro.core.ui.sheet.SheetCheck
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSelect
import com.vextech.editpro.core.ui.sheet.SheetSlider
import com.vextech.editpro.core.ui.sheet.SheetSwatches
import com.vextech.editpro.core.ui.sheet.ToolSheet

private fun hex(argb: Int) = "#%06x".format(argb and 0xFFFFFF)

/** Frames: a wall frame with a mat, or a generic phone. The picture on the screen shows the result as you change things. */
@Composable
internal fun FrameSheet(form: FrameForm, onForm: (FrameForm) -> Unit, onApply: (FrameSpec) -> Unit, onCancel: () -> Unit) {
    ToolSheet(
        title = stringResource(R.string.frames_title),
        onCancel = onCancel,
        onReset = { onForm(FrameForm()) },
        canReset = form.changed,
        onApply = { onApply(form.spec()) },
    ) {
        SheetChips(
            listOf(FrameKind.WALL.name to stringResource(R.string.frames_wall), FrameKind.PHONE.name to stringResource(R.string.frames_phone)),
            form.kind.name,
            onPick = { onForm(form.copy(kind = FrameKind.valueOf(it))) },
        )
        if (form.kind == FrameKind.WALL) WallControls(form.wall) { onForm(form.copy(wall = it)) } else PhoneControls(form.phone) { onForm(form.copy(phone = it)) }
    }
}

@Composable
private fun fitChips(fit: FitMode, onFit: (FitMode) -> Unit) = SheetChips(
    listOf(FitMode.FILL.name to stringResource(R.string.frames_fit_fill), FitMode.FIT.name to stringResource(R.string.frames_fit_whole)),
    fit.name,
    onPick = { onFit(FitMode.valueOf(it)) },
)

@Composable
private fun WallControls(w: WallFrameSpec, onChange: (WallFrameSpec) -> Unit) {
    SheetChips(WallStyle.entries.map { it.name to wallStyleName(it) }, w.style.name, onPick = { onChange(w.copy(style = WallStyle.valueOf(it))) })
    SheetCheck(stringResource(R.string.frames_inner_shadow), w.innerShadow, { onChange(w.copy(innerShadow = it)) })
    SheetSelect(
        stringResource(R.string.frames_size),
        Frames.SIZES.map { it.id to sizeName(it.id) },
        w.size,
        { onChange(w.copy(size = it)) },
    )
    if (w.size != "photo") fitChips(w.fit) { onChange(w.copy(fit = it)) }
    SheetSlider(stringResource(R.string.frames_frame_width), w.framePct.toFloat(), 2f..12f, { onChange(w.copy(framePct = it.toInt())) }, steps = 9)
    SheetSlider(stringResource(R.string.frames_mat_width), w.matPct.toFloat(), 0f..20f, { onChange(w.copy(matPct = it.toInt())) }, steps = 19)
    val matNames = listOf(R.string.resize_color_white, R.string.resize_color_cream, R.string.resize_color_black, R.string.resize_color_grey).map { stringResource(it) }
    SheetSwatches(Frames.MATS.mapIndexed { i, c -> hex(c) to matNames[i] }, hex(w.matColor), onPick = { p -> onChange(w.copy(matColor = Frames.MATS.first { hex(it) == p })) })
    SheetSelect(
        stringResource(R.string.frames_hang_on),
        WallBackdrop.entries.map { it.name to wallName(it) },
        w.wall.name,
        { onChange(w.copy(wall = WallBackdrop.valueOf(it))) },
    )
}

@Composable
private fun PhoneControls(p: PhoneFrameSpec, onChange: (PhoneFrameSpec) -> Unit) {
    SheetChips(PhoneStyle.entries.map { it.name to phoneStyleName(it) }, p.style.name, onPick = { onChange(p.copy(style = PhoneStyle.valueOf(it))) })
    SheetChips(PhoneColor.entries.map { it.name to phoneColorName(it) }, p.color.name, onPick = { onChange(p.copy(color = PhoneColor.valueOf(it))) })
    SheetCheck(stringResource(R.string.frames_phone_shadow), p.shadow, { onChange(p.copy(shadow = it)) })
    SheetChips(
        PhoneBackground.entries.map { it.name to backgroundName(it) },
        p.background.name,
        onPick = { onChange(p.copy(background = PhoneBackground.valueOf(it))) },
    )
    val names = listOf(R.string.frames_c_teal, R.string.frames_c_violet, R.string.frames_c_pink, R.string.frames_c_yellow, R.string.resize_color_white, R.string.frames_c_navy).map { stringResource(it) }
    val palette = Frames.PALETTE.mapIndexed { i, c -> hex(c) to names[i] }
    if (p.background != PhoneBackground.NONE) {
        SheetHint(stringResource(if (p.background == PhoneBackground.GRADIENT) R.string.frames_first_colour else R.string.frames_colour))
        SheetSwatches(palette, hex(p.c1), onPick = { s -> onChange(p.copy(c1 = Frames.PALETTE.first { hex(it) == s })) })
    }
    if (p.background == PhoneBackground.GRADIENT) {
        SheetHint(stringResource(R.string.frames_second_colour))
        SheetSwatches(palette, hex(p.c2), onPick = { s -> onChange(p.copy(c2 = Frames.PALETTE.first { hex(it) == s })) })
    }
    fitChips(p.fit) { onChange(p.copy(fit = it)) }
    SheetHint(stringResource(R.string.frames_generic_note))
}

@Composable
private fun wallStyleName(s: WallStyle) = stringResource(
    when (s) {
        WallStyle.BLACK -> R.string.resize_color_black
        WallStyle.WHITE -> R.string.resize_color_white
        WallStyle.OAK -> R.string.frames_oak
        WallStyle.WALNUT -> R.string.frames_walnut
        WallStyle.GOLD -> R.string.frames_gold
    },
)

@Composable
private fun sizeName(id: String): String = when (id) {
    "photo" -> stringResource(R.string.frames_size_photo)
    "square" -> stringResource(R.string.frames_size_square)
    "a4" -> "A4"
    "a3" -> "A3"
    else -> id.replace("x", " × ") + " in"
}

@Composable
private fun wallName(w: WallBackdrop) = stringResource(
    when (w) {
        WallBackdrop.NONE -> R.string.frames_wall_none
        WallBackdrop.LIGHT -> R.string.frames_wall_light
        WallBackdrop.WARM -> R.string.frames_wall_warm
        WallBackdrop.GREY -> R.string.frames_wall_grey
        WallBackdrop.DARK -> R.string.frames_wall_dark
    },
)

@Composable
private fun phoneStyleName(s: PhoneStyle) = stringResource(
    when (s) {
        PhoneStyle.ISLAND -> R.string.frames_ph_island
        PhoneStyle.NOTCH -> R.string.frames_ph_notch
        PhoneStyle.PUNCH -> R.string.frames_ph_punch
        PhoneStyle.CLASSIC -> R.string.frames_ph_classic
    },
)

@Composable
private fun phoneColorName(c: PhoneColor) = stringResource(
    when (c) {
        PhoneColor.BLACK -> R.string.resize_color_black
        PhoneColor.SILVER -> R.string.frames_silver
        PhoneColor.BLUE -> R.string.frames_midnight
        PhoneColor.TITANIUM -> R.string.frames_titanium
    },
)

@Composable
private fun backgroundName(b: PhoneBackground) = stringResource(
    when (b) {
        PhoneBackground.NONE -> R.string.frames_bg_none
        PhoneBackground.SOLID -> R.string.frames_bg_solid
        PhoneBackground.GRADIENT -> R.string.frames_bg_gradient
    },
)
