package com.vextech.editpro.feature.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vextech.editpro.core.pdf.CropForm
import com.vextech.editpro.core.pdf.CropMath
import com.vextech.editpro.core.pdf.CropMode
import com.vextech.editpro.core.pdf.CropOptions
import com.vextech.editpro.core.pdf.CropParse
import com.vextech.editpro.core.pdf.PageBox
import com.vextech.editpro.feature.pdf.domain.PdfPageInfo

/**
 * Shades the part of the page that the Crop sheet's trim margins would hide and outlines what stays, as the site's preview does.
 * Shows only in Trim mode, on a page the page box includes, while the margins are valid. Fill the page's on-screen shape.
 */
@Composable
fun CropPreviewLayer(form: CropForm, pageIndex: Int, pageCount: Int, pages: List<PdfPageInfo>, modifier: Modifier = Modifier) {
    if (form.mode != CropMode.TRIM) return
    val page = pages.getOrNull(pageIndex) ?: return
    val trim = (form.parse(pageCount, pages) as? CropParse.Ok)?.options as? CropOptions.Trim ?: return
    if (trim.pages != null && pageIndex !in trim.pages) return
    val m = trim.margins
    Canvas(modifier) {
        val (vw, vh) = CropMath.visualSize(PageBox(0.0, 0.0, page.widthPoints.toDouble(), page.heightPoints.toDouble()), page.rotation)
        if (vw <= 0.0 || vh <= 0.0) return@Canvas
        val k = size.width / vw.toFloat()
        val left = (m.left * k).toFloat().coerceIn(0f, size.width)
        val right = size.width - (m.right * k).toFloat().coerceIn(0f, size.width)
        val top = (m.top * k).toFloat().coerceIn(0f, size.height)
        val bottom = size.height - (m.bottom * k).toFloat().coerceIn(0f, size.height)
        val dim = Color(0x80000000)
        drawRect(dim, Offset(0f, 0f), Size(size.width, top))                                   // top band
        drawRect(dim, Offset(0f, bottom), Size(size.width, size.height - bottom))              // bottom band
        drawRect(dim, Offset(0f, top), Size(left, (bottom - top).coerceAtLeast(0f)))           // left band between them
        drawRect(dim, Offset(right, top), Size(size.width - right, (bottom - top).coerceAtLeast(0f)))
        drawRect(
            Color(0xFFFFD43B), Offset(left, top),
            Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
            style = Stroke(width = 3f),
        )
    }
}
