package com.vextech.smart_edit.core.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageProcessorTest {
    private val processor = ImageProcessor()
    private lateinit var dir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dir = File(context.cacheDir, "image-processor-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Source picture is 64x32: top-left red, top-right green, bottom-left blue, bottom-right yellow. */
    private fun quadrantJpeg(orientation: Int): File {
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until 32) for (x in 0 until 64) {
            bitmap.setPixel(x, y, when {
                y < 16 && x < 32 -> Color.RED
                y < 16 -> Color.GREEN
                x < 32 -> Color.BLUE
                else -> Color.YELLOW
            })
        }
        val file = File(dir, "orientation-$orientation.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private fun assertCorners(orientation: Int, topLeft: Int, topRight: Int, bottomLeft: Int, bottomRight: Int, sideways: Boolean) {
        val result = processor.decodeOriented(quadrantJpeg(orientation), 4096)
        assertThat(result.width).isEqualTo(if (sideways) 32 else 64)
        assertThat(result.height).isEqualTo(if (sideways) 64 else 32)
        val left = result.width / 4
        val right = result.width * 3 / 4
        val top = result.height / 4
        val bottom = result.height * 3 / 4
        assertThat(isClose(result.getPixel(left, top), topLeft)).isTrue()
        assertThat(isClose(result.getPixel(right, top), topRight)).isTrue()
        assertThat(isClose(result.getPixel(left, bottom), bottomLeft)).isTrue()
        assertThat(isClose(result.getPixel(right, bottom), bottomRight)).isTrue()
        result.recycle()
    }

    private fun isClose(actual: Int, expected: Int): Boolean =
        abs(Color.red(actual) - Color.red(expected)) < 60 &&
            abs(Color.green(actual) - Color.green(expected)) < 60 &&
            abs(Color.blue(actual) - Color.blue(expected)) < 60

    @Test fun normal_isUnchanged() = assertCorners(1, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, sideways = false)
    @Test fun flipHorizontal() = assertCorners(2, Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE, sideways = false)
    @Test fun rotate180() = assertCorners(3, Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED, sideways = false)
    @Test fun flipVertical() = assertCorners(4, Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN, sideways = false)
    @Test fun transpose() = assertCorners(5, Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, sideways = true)
    @Test fun rotate90() = assertCorners(6, Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN, sideways = true)
    @Test fun transverse() = assertCorners(7, Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED, sideways = true)
    @Test fun rotate270() = assertCorners(8, Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE, sideways = true)

    @Test fun decodeOriented_neverEnlargesAndScalesDownToTheLimit() {
        val file = quadrantJpeg(1)
        val small = processor.decodeOriented(file, 16)
        assertThat(maxOf(small.width, small.height)).isAtMost(16)
        small.recycle()
        val full = processor.decodeOriented(file, 4096)
        assertThat(full.width).isEqualTo(64)
        full.recycle()
    }

    @Test fun hasTransparentPixels_ignoresAnOpaqueAlphaChannel() {
        val opaque = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        assertThat(opaque.hasAlpha()).isTrue()
        assertThat(processor.hasTransparentPixels(opaque)).isFalse()

        val seeThrough = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            setPixel(7, 7, Color.TRANSPARENT)
        }
        assertThat(processor.hasTransparentPixels(seeThrough)).isTrue()

        val noAlpha = Bitmap.createBitmap(8, 8, Bitmap.Config.RGB_565)
        assertThat(processor.hasTransparentPixels(noAlpha)).isFalse()
    }
}
