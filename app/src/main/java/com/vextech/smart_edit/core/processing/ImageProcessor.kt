package com.vextech.smart_edit.core.processing

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class ImageProcessor @Inject constructor() {
    fun decode(file: File): Bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath)) { "Unsupported image: ${file.name}" }

    fun rotate(source: Bitmap, degrees: Float): Bitmap = Bitmap.createBitmap(
        source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degrees) }, true
    )

    fun flipHorizontal(source: Bitmap): Bitmap = Bitmap.createBitmap(
        source, 0, 0, source.width, source.height, Matrix().apply { postScale(-1f, 1f) }, true
    )

    /**
     * Rewrites a JPEG so its pixels are upright and the EXIF orientation no longer matters. The scanner decodes with
     * BitmapFactory (which ignores EXIF), so without this camera and gallery photos can end up sideways and any crop
     * computed on the upright preview would land in the wrong place. Returns true when the file was rewritten.
     */
    fun bakeExifOrientation(file: File, quality: Int = 95): Boolean {
        val orientation = runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            else -> return false
        }
        val source = decode(file)
        val upright = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (upright !== source) source.recycle()
        try { java.io.FileOutputStream(file).use { require(upright.compress(Bitmap.CompressFormat.JPEG, quality, it)) { "Bitmap compression failed" } } }
        finally { upright.recycle() }
        return true
    }

    /**
     * Removes shadows and uneven lighting: estimates the paper colour on a coarse grid (mean of the brightest quarter of
     * each cell), divides every pixel by it, and darkens the mid-tones a little so ink stays crisp. Works per colour
     * channel, so coloured marker on a whiteboard keeps its colour. Streams rows to keep memory near one extra bitmap.
     */
    fun flattenBackground(source: Bitmap, gamma: Float = 1.4f): Bitmap {
        val w = source.width; val h = source.height
        val cell = max(16, max(w, h) / 40)
        val gw = (w + cell - 1) / cell; val gh = (h + cell - 1) / cell
        var grid = Array(3) { FloatArray(gw * gh) }
        val strip = IntArray(w * cell)
        fun lum(p: Int) = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        for (gy in 0 until gh) {
            val y0 = gy * cell; val rows = minOf(cell, h - y0)
            source.getPixels(strip, 0, w, 0, y0, w, rows)
            for (gx in 0 until gw) {
                val x0 = gx * cell; val cols = minOf(cell, w - x0)
                val hist = IntArray(256); var n = 0
                var y = 0
                while (y < rows) { var x = 0; while (x < cols) { hist[lum(strip[y * w + x0 + x])]++; n++; x += 2 }; y += 2 }
                var cut = 255; var acc = 0
                while (cut > 0) { acc += hist[cut]; if (acc >= n / 4) break; cut-- }
                var r = 0L; var g = 0L; var b = 0L; var count = 0
                y = 0
                while (y < rows) { var x = 0; while (x < cols) { val p = strip[y * w + x0 + x]; if (lum(p) >= cut) { r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF; count++ }; x += 2 }; y += 2 }
                val i = gy * gw + gx; val c = max(count, 1)
                grid[0][i] = r.toFloat() / c; grid[1][i] = g.toFloat() / c; grid[2][i] = b.toFloat() / c
            }
        }
        repeat(2) { // soften cell edges
            grid = Array(3) { ch -> FloatArray(gw * gh) { i ->
                val gx = i % gw; val gy = i / gw; var sum = 0f; var cnt = 0
                for (dy in -1..1) for (dx in -1..1) { val xx = gx + dx; val yy = gy + dy; if (xx in 0 until gw && yy in 0 until gh) { sum += grid[ch][yy * gw + xx]; cnt++ } }
                sum / cnt
            } }
        }
        val lut = IntArray(1101) { i -> (Math.pow(minOf(1.0, i / 1000.0 * 1.04), gamma.toDouble()) * 255.0).toInt().coerceIn(0, 255) }
        val x0s = IntArray(w); val x1s = IntArray(w); val txs = FloatArray(w)
        for (x in 0 until w) {
            val fx = ((x + 0.5f) / cell - 0.5f).coerceIn(0f, (gw - 1).toFloat())
            x0s[x] = fx.toInt(); x1s[x] = minOf(x0s[x] + 1, gw - 1); txs[x] = fx - x0s[x]
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w); val bgRow = Array(3) { FloatArray(gw) }
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / cell - 0.5f).coerceIn(0f, (gh - 1).toFloat())
            val ya = fy.toInt(); val yb = minOf(ya + 1, gh - 1); val ty = fy - ya
            for (ch in 0..2) for (gx in 0 until gw) bgRow[ch][gx] = grid[ch][ya * gw + gx] * (1 - ty) + grid[ch][yb * gw + gx] * ty
            source.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = row[x]; val a = x0s[x]; val b = x1s[x]; val t = txs[x]
                val bgR = max(bgRow[0][a] * (1 - t) + bgRow[0][b] * t, 1f)
                val bgG = max(bgRow[1][a] * (1 - t) + bgRow[1][b] * t, 1f)
                val bgB = max(bgRow[2][a] * (1 - t) + bgRow[2][b] * t, 1f)
                val r = lut[((p shr 16 and 0xFF) / bgR * 1000f).toInt().coerceIn(0, 1100)]
                val g = lut[((p shr 8 and 0xFF) / bgG * 1000f).toInt().coerceIn(0, 1100)]
                val bl = lut[((p and 0xFF) / bgB * 1000f).toInt().coerceIn(0, 1100)]
                row[x] = (p and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
            }
            out.setPixels(row, 0, w, 0, y, w, 1)
        }
        return out
    }

    /** Crops away fully transparent margins (plus a little padding), e.g. after [removePlainBackground]. */
    fun trimTransparent(source: Bitmap, padFraction: Float = 0.04f): Bitmap {
        val w = source.width; val h = source.height
        val px = IntArray(w * h); source.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) { val base = y * w
            for (x in 0 until w) if ((px[base + x] ushr 24) > 8) { if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y } }
        if (maxX < 0) return source
        val pad = (max(maxX - minX, maxY - minY) * padFraction).toInt()
        val l = (minX - pad).coerceAtLeast(0); val t = (minY - pad).coerceAtLeast(0)
        val r = (maxX + pad).coerceAtMost(w - 1); val b = (maxY + pad).coerceAtMost(h - 1)
        return Bitmap.createBitmap(source, l, t, r - l + 1, b - t + 1)
    }

    fun crop(source: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val l = (source.width * left.coerceIn(0f, 1f)).toInt()
        val t = (source.height * top.coerceIn(0f, 1f)).toInt()
        val r = (source.width * right.coerceIn(0f, 1f)).toInt().coerceAtLeast(l + 1)
        val b = (source.height * bottom.coerceIn(0f, 1f)).toInt().coerceAtLeast(t + 1)
        return Bitmap.createBitmap(source, l, t, min(source.width - l, r - l), min(source.height - t, b - t))
    }

    fun adjust(source: Bitmap, brightness: Float, contrast: Float, saturation: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val saturationMatrix = ColorMatrix().apply { setSaturation(saturation.coerceIn(0f, 2f)) }
        val c = contrast.coerceIn(.2f, 2f)
        val b = brightness.coerceIn(-255f, 255f)
        val contrastMatrix = ColorMatrix(floatArrayOf(
            c,0f,0f,0f,b, 0f,c,0f,0f,b, 0f,0f,c,0f,b, 0f,0f,0f,1f,0f
        ))
        saturationMatrix.postConcat(contrastMatrix)
        Canvas(result).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(saturationMatrix) })
        return result
    }

    fun grayscale(source: Bitmap): Bitmap = adjust(source, 0f, 1f, 0f)

    fun blackAndWhite(source: Bitmap, threshold: Int = 145): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(out.width * out.height)
        out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = (Color.red(p) * .299 + Color.green(p) * .587 + Color.blue(p) * .114).toInt()
            val v = if (lum >= threshold) 255 else 0
            pixels[i] = Color.argb(Color.alpha(p), v, v, v)
        }
        out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        return out
    }

    fun removePlainBackground(source: Bitmap, tolerance: Int = 40): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width; val h = out.height
        val pixels = IntArray(w * h); out.getPixels(pixels, 0, w, 0, 0, w, h)
        val corners = intArrayOf(pixels[0], pixels[w - 1], pixels[(h - 1) * w], pixels[h * w - 1])
        val bgR = corners.sumOf { Color.red(it) } / 4
        val bgG = corners.sumOf { Color.green(it) } / 4
        val bgB = corners.sumOf { Color.blue(it) } / 4
        fun close(p: Int): Boolean = max(max(kotlin.math.abs(Color.red(p)-bgR), kotlin.math.abs(Color.green(p)-bgG)), kotlin.math.abs(Color.blue(p)-bgB)) <= tolerance
        val queue = IntArray(w * h); var head = 0; var tail = 0
        val visited = BooleanArray(w * h)
        fun push(i: Int) { if (i in pixels.indices && !visited[i] && close(pixels[i])) { visited[i]=true; queue[tail++]=i } }
        for (x in 0 until w) { push(x); push((h-1)*w+x) }
        for (y in 0 until h) { push(y*w); push(y*w+w-1) }
        while (head < tail) {
            val i = queue[head++]; pixels[i] = pixels[i] and 0x00FFFFFF
            val x=i%w; val y=i/w
            if (x>0) push(i-1); if(x<w-1) push(i+1); if(y>0) push(i-w); if(y<h-1) push(i+w)
        }
        out.setPixels(pixels,0,w,0,0,w,h); return out
    }

    /** True only when some pixel is actually see-through. A PNG can carry an alpha channel that is fully opaque. */
    fun hasTransparentPixels(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val width = bitmap.width
        val row = IntArray(width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            for (pixel in row) if ((pixel ushr 24) != 0xFF) return true
        }
        return false
    }

    /**
     * Decodes close to [maxDimension] (power-of-two sampling keeps memory low), applies camera orientation,
     * then fine-scales so the longest side is at most [maxDimension]. Never enlarges.
     */
    fun decodeOriented(file: File, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image: ${file.name}" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val decoded = requireNotNull(
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ) { "Unsupported image: ${file.name}" }
        val exifOrientation = runCatching {
            androidx.exifinterface.media.ExifInterface(file).getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
        val fix = com.vextech.smart_edit.core.library.CompressionPolicies.orientationFix(exifOrientation)
        val oriented = if (fix.isIdentity) decoded else {
            val matrix = Matrix().apply {
                if (fix.flipHorizontal) preScale(-1f, 1f)
                if (fix.rotationDegrees != 0f) postRotate(fix.rotationDegrees)
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
        }
        val (width, height) = com.vextech.smart_edit.core.library.CompressionPolicies.scaledSize(oriented.width, oriented.height, maxDimension)
        if (width == oriented.width && height == oriented.height) return oriented
        return Bitmap.createScaledBitmap(oriented, width, height, true).also { if (it !== oriented) oriented.recycle() }
    }
}
