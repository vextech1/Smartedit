package com.vextech.smart_edit.feature.scanner

import com.vextech.smart_edit.core.ui.CropOverlay
import com.vextech.smart_edit.core.ui.CropRect
import com.vextech.smart_edit.core.ui.FullCorners
import com.vextech.smart_edit.core.ui.QuadCropOverlay
import com.vextech.smart_edit.core.ui.withAspect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ui.ImageScrim
import com.vextech.smart_edit.feature.scanner.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image

/** Crop aspect presets; [ratio] is width/height as the user sees it (after rotation). */
internal enum class CropAspect(val ratio: Float?) {
    FREE(null), ORIGINAL(null), SQUARE(1f), R4_3(4f / 3f), R3_4(3f / 4f), A4(1f / 1.4142f);
}

@Composable
private fun cropAspectLabel(a: CropAspect) = when (a) {
    CropAspect.FREE -> stringResource(R.string.scanner_aspect_free)
    CropAspect.ORIGINAL -> stringResource(R.string.scanner_aspect_original)
    CropAspect.SQUARE -> "1:1"
    CropAspect.R4_3 -> "4:3"
    CropAspect.R3_4 -> "3:4"
    CropAspect.A4 -> stringResource(R.string.scanner_aspect_a4)
}

/** Aspect ratio of the crop in ORIGINAL image pixels, or null when free. Crop is applied before rotation. */
private fun aspectLockFor(a: CropAspect, rotation: Int, imageAspect: Float): Float? {
    if (a == CropAspect.FREE) return null
    if (a == CropAspect.ORIGINAL) return imageAspect
    val displayed = a.ratio ?: return null
    return if (rotation % 180 != 0) 1f / displayed else displayed
}

/**
 * Full-screen page editor: draggable crop frame over the original image, live filter preview, rotate and reset.
 * Crop is applied to the original first, then rotation, matching [AndroidScannerRepository.applyEdit].
 */
@Composable
fun ScanPageEditor(page: ScanPageDraft, onDismiss: () -> Unit, onApply: (CropQuad, ScanFilter, Int) -> Unit) {
    BackHandler(onBack = onDismiss)
    val initialQuad = remember(page.id) { runCatching { CropQuad.decode(page.cropPoints) }.getOrDefault(CropQuad.Full) }
    val initialCorners = remember(page.id) { listOf(initialQuad.topLeft, initialQuad.topRight, initialQuad.bottomRight, initialQuad.bottomLeft).map { Offset(it.x, it.y) } }
    // A page saved with a non-rectangular crop reopens directly in perspective mode.
    val wasPerspective = remember(page.id) {
        val (tl, tr, br, bl) = initialCorners
        kotlin.math.abs(tl.x - bl.x) > 1e-4f || kotlin.math.abs(tr.x - br.x) > 1e-4f || kotlin.math.abs(tl.y - tr.y) > 1e-4f || kotlin.math.abs(bl.y - br.y) > 1e-4f
    }
    var crop by remember(page.id) {
        mutableStateOf(CropRect(initialCorners.minOf { it.x }, initialCorners.minOf { it.y }, initialCorners.maxOf { it.x }, initialCorners.maxOf { it.y }))
    }
    var corners by remember(page.id) { mutableStateOf<List<Offset>?>(if (wasPerspective) initialCorners else null) }
    var filter by remember(page.id) { mutableStateOf(page.filter) }
    var rotation by remember(page.id) { mutableIntStateOf(PageOrientation.rotation(page.rotationDegrees)) }
    var flipped by remember(page.id) { mutableStateOf(PageOrientation.flipped(page.rotationDegrees)) }
    var aspect by remember(page.id) { mutableStateOf(CropAspect.FREE) }
    val source = page.originalUri.ifBlank { page.renderedUri }
    val bitmap by produceState<ImageBitmap?>(null, source) { value = withContext(Dispatchers.IO) { decodeScaled(source, 2048) } }

    fun setAspect(newAspect: CropAspect, newRotation: Int) {
        aspect = newAspect; rotation = newRotation
        val bmp = bitmap ?: return
        val imageAspect = bmp.width.toFloat() / bmp.height
        aspectLockFor(newAspect, newRotation, imageAspect)?.let { crop = crop.withAspect(it / imageAspect) }
    }

    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            Text(
                stringResource(R.string.scanner_editor_page, page.position + 1),
                Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(
                enabled = bitmap != null && crop.left < crop.right && crop.top < crop.bottom,
                onClick = {
                    fun pt(o: Offset) = NormalizedPoint(o.x.coerceIn(0f, 1f), o.y.coerceIn(0f, 1f))
                    val c = corners
                    val quad = if (c != null) CropQuad(pt(c[0]), pt(c[1]), pt(c[2]), pt(c[3])) else CropQuad(
                        NormalizedPoint(crop.left, crop.top), NormalizedPoint(crop.right, crop.top),
                        NormalizedPoint(crop.right, crop.bottom), NormalizedPoint(crop.left, crop.bottom),
                    )
                    onApply(quad, filter, PageOrientation.encode(rotation, flipped))
                },
            ) { Text(stringResource(R.string.scanner_apply)) }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val bmp = bitmap
            if (bmp != null) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val quarterTurn = rotation % 180 != 0
                    val fit = if (quarterTurn) minOf(maxWidth / maxHeight, maxHeight / maxWidth) else 1f
                    Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation.toFloat(); scaleX = if (flipped) -fit else fit; scaleY = fit }) {
                        Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = scanFilterPreview(filter))
                        val activeCorners = corners
                        if (activeCorners != null) {
                            QuadCropOverlay(activeCorners, bmp.width.toFloat() / bmp.height, { corners = it }, Modifier.fillMaxSize(), accent = MaterialTheme.colorScheme.primary)
                        } else {
                            CropOverlay(crop, bmp.width.toFloat() / bmp.height, { crop = it }, Modifier.fillMaxSize(), aspectLock = aspectLockFor(aspect, rotation, bmp.width.toFloat() / bmp.height))
                        }
                    }
                }
            } else CircularProgressIndicator(Modifier.align(Alignment.Center))

            Surface(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                shape = CircleShape, color = ImageScrim,
            ) {
                Row(Modifier.padding(horizontal = 4.dp)) {
                    // Mirror left-right as seen on screen. Edits run flip-then-rotate, so with the image turned
                    // 90/270 a visual mirror is equivalent to toggling the flip and negating the rotation.
                    IconButton(onClick = { setAspect(aspect, ScannerPolicies.normalizeRotation(-rotation)); flipped = !flipped }) {
                        Icon(Icons.Rounded.Flip, stringResource(R.string.scanner_flip), tint = if (flipped) MaterialTheme.colorScheme.primary else Color.White)
                    }
                    IconButton(onClick = { setAspect(aspect, ScannerPolicies.normalizeRotation(rotation + 90)) }) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, stringResource(R.string.scanner_rotate, rotation), tint = Color.White)
                    }
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(enabled = bitmap != null && corners == null, onClick = { menu = true }) {
                            Icon(
                                Icons.Rounded.AspectRatio, stringResource(R.string.scanner_aspect),
                                tint = if (aspect == CropAspect.FREE) Color.White else MaterialTheme.colorScheme.primary,
                            )
                        }
                        DropdownMenu(menu, { menu = false }) {
                            CropAspect.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(cropAspectLabel(option)) },
                                    trailingIcon = { if (option == aspect) Icon(Icons.Rounded.Check, null) },
                                    onClick = { menu = false; setAspect(option, rotation) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        val c = corners
                        if (c == null) {
                            aspect = CropAspect.FREE
                            corners = listOf(Offset(crop.left, crop.top), Offset(crop.right, crop.top), Offset(crop.right, crop.bottom), Offset(crop.left, crop.bottom))
                        } else {
                            crop = CropRect(c.minOf { it.x }, c.minOf { it.y }, c.maxOf { it.x }, c.maxOf { it.y })
                            corners = null
                        }
                    }) {
                        Icon(
                            Icons.Rounded.Transform, stringResource(R.string.scanner_perspective),
                            tint = if (corners != null) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    IconButton(onClick = { crop = CropRect.Full; aspect = CropAspect.FREE; if (corners != null) corners = FullCorners }) {
                        Icon(Icons.Rounded.RestartAlt, stringResource(R.string.scanner_reset_crop), tint = Color.White)
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(ScanFilter.entries.toList(), key = { it.name }) { f ->
                    val selected = f == filter
                    Column(Modifier.clickable { filter = f }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScanThumbnail(
                            source, Modifier.size(64.dp, 80.dp).clip(RoundedCornerShape(10.dp))
                                .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                            version = page.updatedAt, colorFilter = scanFilterPreview(f), maxDim = 256,
                        )
                        Text(
                            scanFilterLabel(f), style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
