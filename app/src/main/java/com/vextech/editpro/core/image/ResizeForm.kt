package com.vextech.editpro.core.image

import kotlin.math.max
import kotlin.math.roundToInt

enum class ResizeUnit { PX, MM, CM, IN, PERCENT }

/** What Resize asks the editor to make. [background] is the colour around the photo for [FitMode.FIT], as ARGB. [maxBytes] is set by form presets. */
data class ResizeSpec(
    val width: Int,
    val height: Int,
    val mode: FitMode,
    val zoom: Double,
    val panX: Double,
    val panY: Double,
    val background: Int,
    val maxBytes: Long? = null,
)

enum class ResizeProblem { NOT_A_NUMBER, TOO_SMALL, TOO_BIG }

sealed interface ResizeParse {
    /** [enlarged] is true when the photo grows by more than 5 %, so it may look soft. [needsConfirm] is true until an official size is ticked. */
    data class Ok(val spec: ResizeSpec, val preset: SizePreset?, val enlarged: Boolean, val needsConfirm: Boolean) : ResizeParse
    data class Error(val problem: ResizeProblem, val width: Int, val height: Int) : ResizeParse
}

/**
 * Everything the Resize sheet shows: every box is text, so half-typed numbers stay as they are. [group] null means Custom.
 * Ported from the site's Resize sheet.
 */
data class ResizeForm(
    val group: PresetGroup? = null,
    val presetId: String = "",
    val width: String = "",
    val height: String = "",
    val unit: ResizeUnit = ResizeUnit.PX,
    val dpi: String = "300",
    val lockShape: Boolean = true,
    val mode: FitMode = FitMode.FILL,
    val zoom: Double = 1.0,
    val panX: Double = 0.0,
    val panY: Double = 0.0,
    val background: String = "#ffffff",
    val confirmed: Boolean = false,
) {
    val preset: SizePreset? get() = if (group != null && presetId.isNotEmpty()) SizePresets.byId(presetId) else null

    private fun number(text: String): Double = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: 0.0
    private fun dpiValue(): Int = number(dpi).roundToInt().takeIf { it > 0 } ?: SizeMath.DEFAULT_DPI

    /** Output size in pixels. A preset or a physical unit goes through dpi; a percent is a share of the photo's own size. */
    fun targetPx(srcW: Int, srcH: Int): Pair<Int, Int> {
        val p = preset
        if (p != null) return SizeMath.toPx(p.w, p.unit, dpiValue()) to SizeMath.toPx(p.h, p.unit, dpiValue())
        val w = number(width); val h = number(height)
        return when (unit) {
            ResizeUnit.PERCENT -> max(1, (srcW * w / 100).roundToInt()) to max(1, (srcH * h / 100).roundToInt())
            ResizeUnit.PX -> SizeMath.toPx(w, SizeUnit.PX, dpiValue()) to SizeMath.toPx(h, SizeUnit.PX, dpiValue())
            ResizeUnit.MM -> SizeMath.toPx(w, SizeUnit.MM, dpiValue()) to SizeMath.toPx(h, SizeUnit.MM, dpiValue())
            ResizeUnit.CM -> SizeMath.toPx(w, SizeUnit.CM, dpiValue()) to SizeMath.toPx(h, SizeUnit.CM, dpiValue())
            ResizeUnit.IN -> SizeMath.toPx(w, SizeUnit.IN, dpiValue()) to SizeMath.toPx(h, SizeUnit.IN, dpiValue())
        }
    }

    fun parse(srcW: Int, srcH: Int): ResizeParse {
        if (preset == null && (number(width) == 0.0 || number(height) == 0.0)) return ResizeParse.Error(ResizeProblem.NOT_A_NUMBER, 0, 0)
        val (w, h) = targetPx(srcW, srcH)
        if (w < SizeMath.MIN_OUT_SIDE || h < SizeMath.MIN_OUT_SIDE) return ResizeParse.Error(ResizeProblem.TOO_SMALL, w, h)
        if (!SizeMath.withinLimits(w, h)) return ResizeParse.Error(ResizeProblem.TOO_BIG, w, h)
        val fit = ResizeFit.fitRect(srcW, srcH, w, h, mode, zoom, panX, panY)
        val colour = background.removePrefix("#").toLongOrNull(16)?.let { (0xFF000000L or it).toInt() } ?: 0xFFFFFFFF.toInt()
        val spec = ResizeSpec(w, h, mode, zoom, panX, panY, colour, preset?.maxKB?.let { it * 1024L })
        return ResizeParse.Ok(spec, preset, enlarged = fit.scale > 1.05, needsConfirm = preset?.confirm == true && !confirmed)
    }

    /** Switching to a group picks its first size; switching to Custom keeps the numbers as they are. */
    fun withGroup(next: PresetGroup?): ResizeForm {
        val reset = copy(group = next, confirmed = false, panX = 0.0, panY = 0.0, zoom = 1.0)
        if (next == null) return reset.copy(presetId = "")
        return if (SizePresets.byId(presetId)?.group == next) reset else reset.withPreset(SizePresets.inGroup(next).first().id)
    }

    fun withPreset(id: String): ResizeForm {
        val p = SizePresets.byId(id) ?: return this
        // The site's ID group always crops to fill, so the picture matches the frame the office asks for.
        return copy(presetId = id, confirmed = false, panX = 0.0, panY = 0.0, zoom = 1.0, mode = if (p.group == PresetGroup.ID) FitMode.FILL else mode)
    }

    /** After one side changed with "keep the photo's shape" on, the other side follows. */
    fun withSide(changedWidth: Boolean, srcW: Int, srcH: Int): ResizeForm {
        if (!lockShape) return this
        val ratio = if (unit == ResizeUnit.PERCENT) 1.0 else srcW.toDouble() / srcH
        val (w, h) = SizeMath.linkedSide(changedWidth, max(1.0, number(width)), max(1.0, number(height)), ratio)
        fun show(n: Double) = if (unit == ResizeUnit.PX) n.roundToInt().toString() else trimmed(n)
        return copy(width = show(w), height = show(h))
    }

    /** Changing the unit keeps the same size, written in the new unit. */
    fun withUnit(next: ResizeUnit, srcW: Int, srcH: Int): ResizeForm {
        val d = dpiValue()
        val (pw, ph) = targetPx(srcW, srcH)
        fun conv(px: Int, src: Int): Double = when (next) {
            ResizeUnit.PX -> px.toDouble()
            ResizeUnit.PERCENT -> px.toDouble() / src * 100
            ResizeUnit.MM -> SizeMath.toMm(px.toDouble(), SizeUnit.PX, d)
            ResizeUnit.CM -> SizeMath.toMm(px.toDouble(), SizeUnit.PX, d) / 10
            ResizeUnit.IN -> SizeMath.toMm(px.toDouble(), SizeUnit.PX, d) / SizeMath.MM_PER_IN
        }
        fun show(n: Double) = if (next == ResizeUnit.PX) n.roundToInt().toString() else trimmed(n)
        return copy(unit = next, width = show(conv(pw, srcW)), height = show(conv(ph, srcH)))
    }

    fun changedFrom(start: ResizeForm): Boolean = this != start

    private fun trimmed(n: Double): String {
        val r = (n * 100).roundToInt() / 100.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    companion object {
        /** The sheet's starting point: the photo's own size in pixels. */
        fun start(srcW: Int, srcH: Int) = ResizeForm(width = srcW.toString(), height = srcH.toString())
    }
}
