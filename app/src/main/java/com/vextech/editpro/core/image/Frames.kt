package com.vextech.editpro.core.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class WallStyle(val base: Int, val grain: Boolean) {
    BLACK(0xFF1A1A1A.toInt(), false), WHITE(0xFFF1F0EC.toInt(), false), OAK(0xFFC9A06A.toInt(), true), WALNUT(0xFF5A3B26.toInt(), true), GOLD(0xFFD9B44A.toInt(), false),
}

/** A frame opening in inches (millimetres for A4 and A3), portrait. [w] of 0 keeps the photo's own shape. */
data class FrameSize(val id: String, val w: Int, val h: Int)

/** The wall a frame hangs on, top to bottom colour. [NONE] has no wall. */
enum class WallBackdrop(val top: Int, val bottom: Int) {
    NONE(0, 0), LIGHT(0xFFECEAE5.toInt(), 0xFFDCD9D2.toInt()), WARM(0xFFE7D8C6.toInt(), 0xFFD6C3AC.toInt()),
    GREY(0xFFB9BCC0.toInt(), 0xFFA4A8AD.toInt()), DARK(0xFF2B2F36.toInt(), 0xFF20242A.toInt()),
}

enum class PhoneStyle(val aspect: Double) { ISLAND(0.462), NOTCH(0.462), PUNCH(0.455), CLASSIC(0.5625) }
enum class PhoneColor(val body: Int) { BLACK(0xFF141416.toInt()), SILVER(0xFFD8D9DC.toInt()), BLUE(0xFF1F2B46.toInt()), TITANIUM(0xFFA9A396.toInt()) }
enum class PhoneBackground { NONE, SOLID, GRADIENT }
enum class FrameKind { WALL, PHONE }

/** A frame the editor can put round the picture. Wall widths are percents of the opening's shorter side. */
sealed interface FrameSpec

data class WallFrameSpec(
    val style: WallStyle = WallStyle.BLACK,
    val size: String = "photo",
    val framePct: Int = 5,
    val matPct: Int = 8,
    val matColor: Int = 0xFFFFFFFF.toInt(),
    val wall: WallBackdrop = WallBackdrop.LIGHT,
    val innerShadow: Boolean = true,
    val fit: FitMode = FitMode.FILL,
) : FrameSpec

data class PhoneFrameSpec(
    val style: PhoneStyle = PhoneStyle.ISLAND,
    val color: PhoneColor = PhoneColor.BLACK,
    val background: PhoneBackground = PhoneBackground.GRADIENT,
    val c1: Int = 0xFF5FE0D6.toInt(),
    val c2: Int = 0xFF8F86FF.toInt(),
    val shadow: Boolean = true,
    val fit: FitMode = FitMode.FILL,
) : FrameSpec

data class WallLayout(val w: Int, val h: Int, val fw: Int, val mw: Int, val px: Int, val py: Int, val pw: Int, val ph: Int, val pad: Int)

data class PhoneLayout(
    val w: Int, val h: Int, val bodyW: Int, val bodyH: Int, val bx: Int, val by: Int,
    val sx: Int, val sy: Int, val sw: Int, val sh: Int, val r: Int, val sr: Int, val pad: Int,
)

/** Photo frames drawn in code, ported from the site's `frames.ts`. This is the layout; [com.vextech.editpro.core.processing.FramePainter] draws it. */
object Frames {
    val SIZES: List<FrameSize> = listOf(
        FrameSize("photo", 0, 0), FrameSize("4x6", 4, 6), FrameSize("5x7", 5, 7), FrameSize("8x10", 8, 10), FrameSize("11x14", 11, 14),
        FrameSize("16x20", 16, 20), FrameSize("a4", 210, 297), FrameSize("a3", 297, 420), FrameSize("square", 8, 8),
    )

    val MATS: List<Int> = listOf(0xFFFFFFFF.toInt(), 0xFFF2EAD8.toInt(), 0xFF1B1B1B.toInt(), 0xFFC9CCD1.toInt())
    val PALETTE: List<Int> = listOf(0xFF5FE0D6.toInt(), 0xFF8F86FF.toInt(), 0xFFFF8FA3.toInt(), 0xFFFFD24A.toInt(), 0xFFFFFFFF.toInt(), 0xFF1F2B46.toInt())

    const val WALL_LONG_MAX = 2400
    const val PHONE_SCREEN_MAX = 1170
    const val PHONE_SCREEN_MIN = 400

    /** Lightens ([amt] above 0) or darkens (below 0) a colour; [amt] is -1 to 1. Alpha is kept. */
    fun shade(color: Int, amt: Double): Int {
        fun f(v: Int): Int = (if (amt >= 0) v + (255 - v) * amt else v * (1 + amt)).roundToInt().coerceIn(0, 255)
        val r = f((color shr 16) and 0xFF); val g = f((color shr 8) and 0xFF); val b = f(color and 0xFF)
        return (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    /** Opening shape (width over height) for a frame size, turned to match the photo's orientation. */
    fun openingRatio(size: String, photoW: Int, photoH: Int): Double {
        val f = SIZES.firstOrNull { it.id == size }
        if (f == null || f.w == 0) return photoW.toDouble() / photoH
        val portrait = f.w.toDouble() / f.h
        return if (photoW >= photoH) 1 / portrait else portrait
    }

    /** Geometry for a wall frame whose picture area has its long side at [long] pixels. */
    fun wallLayout(photoW: Int, photoH: Int, size: String, framePct: Int, matPct: Int, wall: WallBackdrop, long: Double): WallLayout {
        val ratio = openingRatio(size, photoW, photoH)
        val ow = if (ratio >= 1) long else long * ratio
        val oh = if (ratio >= 1) long / ratio else long
        val short = min(ow, oh)
        val fw = (short * framePct / 100).roundToInt()
        val mw = (short * matPct / 100).roundToInt()
        val pw = ow.roundToInt(); val ph = oh.roundToInt()
        val w = pw + 2 * (fw + mw); val h = ph + 2 * (fw + mw)
        val pad = if (wall == WallBackdrop.NONE) 0 else (max(w, h) * 0.12).roundToInt()
        return WallLayout(w, h, fw, mw, pad + fw + mw, pad + fw + mw, pw, ph, pad)
    }

    /** The long side the picture area gets for a photo [photoW] x [photoH]. */
    fun wallLong(photoW: Int, photoH: Int): Double = min(WALL_LONG_MAX, max(photoW, photoH)).toDouble()

    /** Width of the phone's screen for a photo [photoW] wide. */
    fun phoneScreenWidth(photoW: Int): Int = min(PHONE_SCREEN_MAX, max(PHONE_SCREEN_MIN, photoW))

    /** Geometry for a phone drawn with a screen [screenW] pixels wide. */
    fun phoneLayout(style: PhoneStyle, screenW: Int, withBackground: Boolean): PhoneLayout {
        val classic = style == PhoneStyle.CLASSIC
        val side = (screenW * (if (classic) 0.06 else 0.032)).roundToInt()
        val top = (screenW * (if (classic) 0.17 else 0.032)).roundToInt()
        val bottom = (screenW * (if (classic) 0.19 else 0.032)).roundToInt()
        val sw = screenW
        val sh = (screenW / style.aspect).roundToInt()
        val bodyW = sw + 2 * side
        val bodyH = sh + top + bottom
        val r = (bodyW * (if (classic) 0.12 else 0.15)).roundToInt()
        val sr = if (classic) (screenW * 0.02).roundToInt() else max(4, r - side)
        val pad = (bodyW * (if (withBackground) 0.14 else 0.04)).roundToInt()
        return PhoneLayout(bodyW + 2 * pad, bodyH + 2 * pad, bodyW, bodyH, pad, pad, pad + side, pad + top, sw, sh, r, sr, pad)
    }

    /** The size of the framed picture for a photo [photoW] x [photoH], without drawing anything. */
    fun outputSize(spec: FrameSpec, photoW: Int, photoH: Int): Pair<Int, Int> = when (spec) {
        is WallFrameSpec -> wallLayout(photoW, photoH, spec.size, spec.framePct, spec.matPct, spec.wall, wallLong(photoW, photoH)).let { (it.w + 2 * it.pad) to (it.h + 2 * it.pad) }
        is PhoneFrameSpec -> phoneLayout(spec.style, phoneScreenWidth(photoW), spec.background != PhoneBackground.NONE).let { it.w to it.h }
    }

    /** True when the framed picture has see-through areas, which JPG would turn black. */
    fun isTransparent(spec: FrameSpec): Boolean = spec is PhoneFrameSpec && spec.background == PhoneBackground.NONE
}

/** What the Frames sheet shows: which kind, and the settings for each. */
data class FrameForm(
    val kind: FrameKind = FrameKind.WALL,
    val wall: WallFrameSpec = WallFrameSpec(),
    val phone: PhoneFrameSpec = PhoneFrameSpec(),
) {
    fun spec(): FrameSpec = if (kind == FrameKind.WALL) wall else phone
    val changed: Boolean get() = this != FrameForm()
    val transparent: Boolean get() = Frames.isTransparent(spec())
}
