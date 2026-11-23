package com.vextech.editpro.core.pdf

/** Where an image is drawn on a sheet of paper: bottom-left corner and size, in points. */
data class ImagePlacement(val x: Float, val y: Float, val width: Float, val height: Float)

/** The site's `layout` for A4 and Letter: the image keeps its proportions and sits centred inside the margin. */
object ImagePlacements {
    /** Margin choices of the site's Images to PDF: none, small (24 pt), large (48 pt). */
    const val MARGIN_NONE = 0f
    const val MARGIN_SMALL = 24f
    const val MARGIN_LARGE = 48f

    fun onPaper(pageWidth: Float, pageHeight: Float, margin: Float, imageWidth: Int, imageHeight: Int): ImagePlacement {
        val m = margin.coerceIn(0f, minOf(pageWidth, pageHeight) / 4f)
        val scale = minOf((pageWidth - 2 * m) / imageWidth, (pageHeight - 2 * m) / imageHeight)
        val w = imageWidth * scale
        val h = imageHeight * scale
        return ImagePlacement((pageWidth - w) / 2f, (pageHeight - h) / 2f, w, h)
    }
}
