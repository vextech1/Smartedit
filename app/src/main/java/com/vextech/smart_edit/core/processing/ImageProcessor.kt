package com.vextech.smart_edit.core.processing

import android.graphics.*
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class ImageProcessor @Inject constructor() {
    fun decode(file: File): Bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath)) { "Unsupported image: ${file.name}" }

    fun rotate(source: Bitmap, degrees: Float): Bitmap = Bitmap.createBitmap(
        source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degrees) }, true
    )

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
        val rotation = when (runCatching { androidx.exifinterface.media.ExifInterface(file).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val oriented = if (rotation == 0f) decoded else {
            rotate(decoded, rotation).also { if (it !== decoded) decoded.recycle() }
        }
        val (width, height) = com.vextech.smart_edit.core.library.CompressionPolicies.scaledSize(oriented.width, oriented.height, maxDimension)
        if (width == oriented.width && height == oriented.height) return oriented
        return Bitmap.createScaledBitmap(oriented, width, height, true).also { if (it !== oriented) oriented.recycle() }
    }
}
