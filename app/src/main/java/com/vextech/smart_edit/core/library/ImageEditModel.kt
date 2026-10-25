package com.vextech.smart_edit.core.library

data class IntRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** Crop rectangle as fractions of the image (0..1). Always at least [MIN_SIZE] wide and tall. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    fun toPixels(width: Int, height: Int): IntRect {
        require(width > 0 && height > 0) { "Image size must be positive" }
        val l = Math.round(width * left).coerceIn(0, width - 1)
        val t = Math.round(height * top).coerceIn(0, height - 1)
        val r = Math.round(width * right).coerceIn(l + 1, width)
        val b = Math.round(height * bottom).coerceIn(t + 1, height)
        return IntRect(l, t, r - l, b - t)
    }

    companion object {
        const val MIN_SIZE = 0.05f
        val FULL = CropRect(0f, 0f, 1f, 1f)

        /** Clamps to the image. If edges cross, the left/top edge stays and the right/bottom edge is pushed out. */
        fun of(left: Float, top: Float, right: Float, bottom: Float): CropRect {
            val l = left.coerceIn(0f, 1f - MIN_SIZE)
            val t = top.coerceIn(0f, 1f - MIN_SIZE)
            val r = right.coerceIn(l + MIN_SIZE, 1f)
            val b = bottom.coerceIn(t + MIN_SIZE, 1f)
            return CropRect(l, t, r, b)
        }
    }
}

data class AdjustParams(val brightness: Float = 0f, val contrast: Float = 1f, val saturation: Float = 1f) {
    val isIdentity: Boolean get() = brightness == 0f && contrast == 1f && saturation == 1f

    fun coerced(): AdjustParams = AdjustParams(
        brightness.coerceIn(BRIGHTNESS_RANGE),
        contrast.coerceIn(CONTRAST_RANGE),
        saturation.coerceIn(SATURATION_RANGE),
    )

    companion object {
        val BRIGHTNESS_RANGE = -100f..100f
        val CONTRAST_RANGE = 0.5f..2f
        val SATURATION_RANGE = 0f..2f
    }
}

enum class ImageFilter { NONE, GRAYSCALE, BLACK_WHITE }

sealed interface EditOp {
    data class Rotate(val quarterTurns: Int) : EditOp
    data class Crop(val rect: CropRect) : EditOp
    data class Adjust(val params: AdjustParams) : EditOp
    data class Filter(val filter: ImageFilter) : EditOp
    data object RemoveBackground : EditOp
}

/**
 * Immutable undo/redo history of edits. Operations are applied in order to the original image.
 * Pushing a new edit clears redo. Consecutive rotations add up, consecutive adjustments and filters
 * replace each other, and edits that change nothing are ignored.
 */
class EditStack private constructor(val done: List<EditOp>, private val undone: List<EditOp>) {
    constructor() : this(emptyList(), emptyList())

    val canUndo: Boolean get() = done.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()
    val isEmpty: Boolean get() = done.isEmpty()

    fun push(op: EditOp): EditStack {
        val last = done.lastOrNull()
        val next: List<EditOp> = when (op) {
            is EditOp.Rotate -> {
                val turns = ((op.quarterTurns % 4) + 4) % 4
                when {
                    turns == 0 -> return this
                    last is EditOp.Rotate -> {
                        val total = (last.quarterTurns + turns) % 4
                        if (total == 0) done.dropLast(1) else done.dropLast(1) + EditOp.Rotate(total)
                    }
                    else -> done + EditOp.Rotate(turns)
                }
            }
            is EditOp.Crop -> if (op.rect.isFull) return this else done + op
            is EditOp.Adjust -> {
                val params = op.params.coerced()
                when {
                    params.isIdentity && last is EditOp.Adjust -> done.dropLast(1)
                    params.isIdentity -> return this
                    last is EditOp.Adjust -> done.dropLast(1) + EditOp.Adjust(params)
                    else -> done + EditOp.Adjust(params)
                }
            }
            is EditOp.Filter -> when {
                op.filter == ImageFilter.NONE && last is EditOp.Filter -> done.dropLast(1)
                op.filter == ImageFilter.NONE -> return this
                last is EditOp.Filter -> if (last.filter == op.filter) return this else done.dropLast(1) + op
                else -> done + op
            }
            EditOp.RemoveBackground -> if (last == EditOp.RemoveBackground) return this else done + op
        }
        return EditStack(next, emptyList())
    }

    fun undo(): EditStack = if (done.isEmpty()) this else EditStack(done.dropLast(1), undone + done.last())

    fun redo(): EditStack = if (undone.isEmpty()) this else EditStack(done + undone.last(), undone.dropLast(1))

    /** The active adjustment, or the identity. Used to seed sliders. */
    fun currentAdjust(): AdjustParams = (done.lastOrNull { it is EditOp.Adjust } as? EditOp.Adjust)?.params ?: AdjustParams()

    fun currentFilter(): ImageFilter = (done.lastOrNull { it is EditOp.Filter } as? EditOp.Filter)?.filter ?: ImageFilter.NONE
}

object EditPolicies {
    /** Pixel size after the given rotation. */
    fun rotatedSize(width: Int, height: Int, quarterTurns: Int): Pair<Int, Int> =
        if (((quarterTurns % 4) + 4) % 4 % 2 == 1) height to width else width to height

    /** Final size of the image after all operations, without touching pixels. */
    fun outputSize(width: Int, height: Int, ops: List<EditOp>): Pair<Int, Int> {
        var w = width
        var h = height
        for (op in ops) when (op) {
            is EditOp.Rotate -> rotatedSize(w, h, op.quarterTurns).let { w = it.first; h = it.second }
            is EditOp.Crop -> op.rect.toPixels(w, h).let { w = it.width; h = it.height }
            else -> Unit
        }
        return w to h
    }

    /** Background removal produces transparency, so the result must be PNG. */
    fun outputFormat(sourceHasAlpha: Boolean, ops: List<EditOp>): ImageOutput =
        if (sourceHasAlpha || ops.any { it == EditOp.RemoveBackground }) ImageOutput.PNG else ImageOutput.JPEG

    fun outputName(originalName: String, output: ImageOutput): String {
        val base = originalName.substringBeforeLast('.', originalName).trim().ifBlank { "image" }
        return "${base}_edited.${output.extension}"
    }
}
