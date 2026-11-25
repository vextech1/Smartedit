package com.vextech.editpro.core.image

import kotlin.math.max
import kotlin.math.roundToInt

enum class SizeUnit { PX, MM, CM, IN }

enum class PresetGroup { ID, FORMS, PRINT, SOCIAL }

/**
 * A named output size for Resize, ported from the site's `presets.ts`. [confirm] marks official-document sizes, where the person must tick
 * that they checked the size. [minKB] and [maxKB] are the file-size limits many forms ask for.
 */
data class SizePreset(
    val id: String,
    val group: PresetGroup,
    val w: Double,
    val h: Double,
    val unit: SizeUnit,
    val minKB: Int? = null,
    val maxKB: Int? = null,
    val confirm: Boolean = false,
)

object SizePresets {
    private fun id(key: String, w: Double, h: Double, unit: SizeUnit) = SizePreset(key, PresetGroup.ID, w, h, unit, confirm = true)
    private fun form(key: String, w: Double, h: Double, unit: SizeUnit, minKB: Int? = null, maxKB: Int? = null) =
        SizePreset(key, PresetGroup.FORMS, w, h, unit, minKB, maxKB, confirm = true)
    private fun print(key: String, w: Double, h: Double, unit: SizeUnit) = SizePreset(key, PresetGroup.PRINT, w, h, unit)
    private fun social(key: String, w: Int, h: Int) = SizePreset(key, PresetGroup.SOCIAL, w.toDouble(), h.toDouble(), SizeUnit.PX)

    val all: List<SizePreset> = listOf(
        id("us-passport", 2.0, 2.0, SizeUnit.IN),
        id("uk-passport", 35.0, 45.0, SizeUnit.MM),
        id("schengen", 35.0, 45.0, SizeUnit.MM),
        id("canada-passport", 50.0, 70.0, SizeUnit.MM),
        id("china-visa", 33.0, 48.0, SizeUnit.MM),
        id("australia-passport", 35.0, 45.0, SizeUnit.MM),
        id("kenya-5x5", 5.0, 5.0, SizeUnit.CM),
        id("kenya-2x2.5", 2.0, 2.5, SizeUnit.IN),
        id("japan-passport", 35.0, 45.0, SizeUnit.MM),
        id("id-30x40", 30.0, 40.0, SizeUnit.MM),
        id("id-25x35", 25.0, 35.0, SizeUnit.MM),
        id("id-20x25", 20.0, 25.0, SizeUnit.MM),
        form("form-200x230", 200.0, 230.0, SizeUnit.PX, minKB = 20, maxKB = 50),
        form("sign-140x60", 140.0, 60.0, SizeUnit.PX, minKB = 10, maxKB = 20),
        form("form-35x45", 35.0, 45.0, SizeUnit.MM),
        print("wallet", 2.5, 3.5, SizeUnit.IN),
        print("p-3.5x5", 3.5, 5.0, SizeUnit.IN),
        print("p-4x6", 4.0, 6.0, SizeUnit.IN),
        print("p-5x7", 5.0, 7.0, SizeUnit.IN),
        print("p-8x10", 8.0, 10.0, SizeUnit.IN),
        print("a6", 105.0, 148.0, SizeUnit.MM),
        print("a5", 148.0, 210.0, SizeUnit.MM),
        print("a4", 210.0, 297.0, SizeUnit.MM),
        social("ig-square", 1080, 1080),
        social("ig-portrait", 1080, 1350),
        social("story", 1080, 1920),
        social("yt-thumb", 1280, 720),
        social("profile", 500, 500),
        social("li-banner", 1584, 396),
        social("fb-cover", 820, 312),
        social("x-header", 1500, 500),
    )

    fun byId(id: String): SizePreset? = all.firstOrNull { it.id == id }
    fun inGroup(group: PresetGroup): List<SizePreset> = all.filter { it.group == group }
}

/** Units, pixel sizes and limits for Resize, ported from the site's `presets.ts` and `photo-ops.ts`. */
object SizeMath {
    const val MM_PER_IN = 25.4
    const val DEFAULT_DPI = 300

    /** The largest picture the editor will make, so a typo (say 40000 px) cannot freeze the phone. */
    const val MAX_OUT_SIDE = 8000
    const val MAX_OUT_PIXELS = 40_000_000L

    /** The smallest side, so a 0 or a stray digit cannot quietly make a 1 x 1 picture. */
    const val MIN_OUT_SIDE = 16

    private fun perInch(unit: SizeUnit): Double = when (unit) {
        SizeUnit.PX -> 0.0
        SizeUnit.IN -> 1.0
        SizeUnit.MM -> 1.0 / MM_PER_IN
        SizeUnit.CM -> 1.0 / 2.54
    }

    /** A length in [unit] as pixels at [dpi]. Pixels pass through unchanged. Never below 1. */
    fun toPx(value: Double, unit: SizeUnit, dpi: Int): Int =
        max(1, (if (unit == SizeUnit.PX) value else value * perInch(unit) * dpi).roundToInt())

    /** A length in [unit] as millimetres. Pixels need a [dpi] to mean anything physical. */
    fun toMm(value: Double, unit: SizeUnit, dpi: Int): Double = when (unit) {
        SizeUnit.PX -> value / dpi * MM_PER_IN
        SizeUnit.MM -> value
        SizeUnit.CM -> value * 10
        SizeUnit.IN -> value * MM_PER_IN
    }

    fun withinLimits(w: Int, h: Int): Boolean =
        w >= MIN_OUT_SIDE && h >= MIN_OUT_SIDE && w <= MAX_OUT_SIDE && h <= MAX_OUT_SIDE && w.toLong() * h <= MAX_OUT_PIXELS

    /** With "keep shape" on, the other side that matches [ratio] (width / height) after one side changed. */
    fun linkedSide(changedWidth: Boolean, w: Double, h: Double, ratio: Double): Pair<Double, Double> {
        val r = if (ratio > 0) ratio else 1.0
        fun round(n: Double) = max(1.0, (n * 100).roundToInt() / 100.0)
        return if (changedWidth) w to round(w / r) else round(h * r) to h
    }

    private fun trim(n: Double): String = ((n * 100).roundToInt() / 100.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }

    /** "35 × 45 mm", "2 × 2 in" or "1080 × 1080 px". */
    fun describe(w: Double, h: Double, unit: SizeUnit): String =
        "${trim(w)} × ${trim(h)} ${when (unit) { SizeUnit.PX -> "px"; SizeUnit.MM -> "mm"; SizeUnit.CM -> "cm"; SizeUnit.IN -> "in" }}"
}
