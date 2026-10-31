package com.vextech.smart_edit.feature.scanner

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.vextech.smart_edit.R
import com.vextech.smart_edit.feature.scanner.domain.ScanFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Decodes a managed scan file, down-sampled so its longest side is roughly [maxDim] or more. */
internal fun decodeScaled(uri: String, maxDim: Int): ImageBitmap? {
    val path = Uri.parse(uri).path ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

@Composable
fun ScanThumbnail(
    uri: String,
    modifier: Modifier = Modifier,
    version: Any? = null,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null,
    maxDim: Int = 512,
) {
    val bitmap by produceState<ImageBitmap?>(null, uri, version) { value = withContext(Dispatchers.IO) { decodeScaled(uri, maxDim) } }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = contentScale, colorFilter = colorFilter) }
    }
}

/** Live preview approximation of the filters that [AndroidScannerRepository] bakes into the page on apply. */
internal fun scanFilterPreview(filter: ScanFilter): ColorFilter? = when (filter) {
    ScanFilter.ORIGINAL -> null
    ScanFilter.GRAYSCALE -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    ScanFilter.BLACK_WHITE -> {
        val c = 2.2f; val t = (-0.5f * c + 0.5f) * 255f
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            c * .213f, c * .715f, c * .072f, 0f, t,
            c * .213f, c * .715f, c * .072f, 0f, t,
            c * .213f, c * .715f, c * .072f, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
    ScanFilter.ENHANCE -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        1.12f, 0f, 0f, 0f, 8f,
        0f, 1.12f, 0f, 0f, 8f,
        0f, 0f, 1.12f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f,
    )))
    // The real filter divides by the local paper brightness; a contrast lift is the closest cheap preview.
    ScanFilter.CLEAN -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        1.3f, 0f, 0f, 0f, 14f,
        0f, 1.3f, 0f, 0f, 14f,
        0f, 0f, 1.3f, 0f, 14f,
        0f, 0f, 0f, 1f, 0f,
    )))
}

@Composable
internal fun scanFilterLabel(filter: ScanFilter) = stringResource(
    when (filter) {
        ScanFilter.ORIGINAL -> R.string.scanner_filter_original
        ScanFilter.GRAYSCALE -> R.string.scanner_filter_grayscale
        ScanFilter.BLACK_WHITE -> R.string.scanner_filter_black_white
        ScanFilter.ENHANCE -> R.string.scanner_filter_enhance
        ScanFilter.CLEAN -> R.string.scanner_filter_clean
    }
)
