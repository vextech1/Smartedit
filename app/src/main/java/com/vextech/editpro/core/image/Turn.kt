package com.vextech.editpro.core.image

/**
 * The picture as the Rotate and Flip sheets see it, ported from the site's `Turn`: the photo is mirrored first,
 * then turned clockwise by [turns] quarter turns. Unapplied until the tick.
 */
data class Turn(val turns: Int = 0, val flipH: Boolean = false, val flipV: Boolean = false) {
    /** True when applying this would change the picture. */
    val isTurned: Boolean get() = ((turns % 4) + 4) % 4 != 0 || flipH || flipV

    /** Quarter turns clockwise; use a negative number for anticlockwise. Kept within 0 to 3. */
    fun turnBy(quarters: Int): Turn = copy(turns = (((turns + quarters) % 4) + 4) % 4)

    /**
     * Mirror what is on screen, left to right or top to bottom. After a quarter turn the picture's own sides have swapped places,
     * so the mirror has to go on the other axis of the photo, or the Flip buttons would seem to do the opposite.
     */
    fun flipOnScreen(horizontal: Boolean): Turn {
        val sideways = turns % 2 != 0
        val onPhotoH = if (sideways) !horizontal else horizontal
        return if (onPhotoH) copy(flipH = !flipH) else copy(flipV = !flipV)
    }

    /** Size of a [width] x [height] picture after the turns: a quarter turn swaps width and height. */
    fun turnedSize(width: Int, height: Int): Pair<Int, Int> = if (turns % 2 != 0) height to width else width to height

    companion object {
        val NONE = Turn()
    }
}
