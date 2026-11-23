package com.vextech.editpro.feature.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vextech.editpro.core.pdf.PageRangeResult
import com.vextech.editpro.core.pdf.PageRanges
import com.vextech.editpro.core.pdf.PageSpace
import com.vextech.editpro.core.pdf.WatermarkKind
import com.vextech.editpro.core.pdf.WatermarkLayout
import com.vextech.editpro.feature.pdf.domain.PdfPageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Width of [text] at text size 1, in Android's bold sans font. The PDF itself uses Helvetica Bold, which is very close. */
internal fun approxTextUnit(text: String): Double {
    val paint = Paint().apply { typeface = Typeface.DEFAULT_BOLD; textSize = 100f }
    return (paint.measureText(text.trim()) / 100f).toDouble().coerceAtLeast(0.01)
}

/** Whether the sheet's page box (blank means every page) includes [pageIndex]. A box that cannot be read counts as no. */
internal fun watermarkAppliesTo(state: WatermarkSheetState, pageIndex: Int, pageCount: Int): Boolean {
    if (state.pagesText.isBlank()) return true
    val range = PageRanges.parse(state.pagesText, pageCount)
    return range is PageRangeResult.Ok && pageIndex in range.pages
}

/**
 * Draws the watermark being edited on top of the page, scaled to fill this layer (which must have the page's on-screen shape).
 * It uses the same layout code as the file writer, so what shows here is where the mark will land.
 */
@Composable
fun WatermarkPreviewLayer(state: WatermarkSheetState, pageIndex: Int, pageCount: Int, page: PdfPageInfo, modifier: Modifier = Modifier) {
    val d = state.draft
    val context = LocalContext.current
    val logo by produceState<ImageBitmap?>(initialValue = null, state.logo, d.kind) {
        val uri = state.logo
        value = if (d.kind == WatermarkKind.LOGO && uri != null) withContext(Dispatchers.IO) { runCatching { decodePreviewLogo(context, uri) }.getOrNull() } else null
    }
    if (!watermarkAppliesTo(state, pageIndex, pageCount)) return
    if (WatermarkLayout.validate(d, state.logo != null) != null) return
    if (d.kind == WatermarkKind.LOGO && logo == null) return

    Canvas(modifier) {
        val (vw, vh) = PageSpace.visualSize(page.widthPoints.toDouble(), page.heightPoints.toDouble(), page.rotation)
        if (vw <= 0.0 || vh <= 0.0) return@Canvas
        val scale = size.width / vw.toFloat()
        val isText = d.kind == WatermarkKind.TEXT
        val image = logo
        val unit = if (isText) approxTextUnit(d.text) else 1.0
        val aspect = if (image != null) image.width.toDouble() / image.height else 1.0
        val mark = runCatching { WatermarkLayout.layout(vw, vh, d, unit, aspect) }.getOrNull() ?: return@Canvas
        val alpha = d.opacityPct / 100f
        val rgb = WatermarkLayout.parseHex(d.colorHex)
        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textSize = (mark.size * scale).toFloat().coerceAtLeast(1f)
            color = android.graphics.Color.argb(
                (alpha * 255).toInt(),
                ((rgb?.first ?: 0f) * 255).toInt(), ((rgb?.second ?: 0f) * 255).toInt(), ((rgb?.third ?: 0f) * 255).toInt(),
            )
        }
        val w = (mark.w * scale).toFloat()
        val h = (mark.h * scale).toFloat()
        for (c in mark.centres) {
            val px = (c.x * scale).toFloat()
            val py = ((vh - c.y) * scale).toFloat()
            rotate(-d.angle.toFloat(), Offset(px, py)) {   // the turn is counter-clockwise, Compose's is clockwise
                if (isText) {
                    drawContext.canvas.nativeCanvas.drawText(d.text.trim(), px - w / 2f, py + h / 2f, paint)
                } else if (image != null) {
                    drawImage(
                        image,
                        dstOffset = IntOffset((px - w / 2f).toInt(), (py - h / 2f).toInt()),
                        dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

/** A small copy of the chosen logo for the preview: no more than about 600 pixels on its long side. */
private fun decodePreviewLogo(context: Context, uri: Uri): ImageBitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 600) sample *= 2
    val bitmap: Bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    return bitmap.asImageBitmap()
}
