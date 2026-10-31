package com.vextech.smart_edit.feature.scanner

import androidx.camera.core.ImageProxy
import com.vextech.smart_edit.feature.scanner.domain.LumaFrame

internal object CameraFrames {
    /** Samples the Y (brightness) plane of a YUV frame down to about [targetMax] pixels on the long side. No colour conversion. */
    fun luma(image: ImageProxy, targetMax: Int = 200): LumaFrame {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height
        val step = maxOf(1, maxOf(w, h) / targetMax)
        val dw = w / step
        val dh = h / step
        val out = IntArray(dw * dh)
        for (y in 0 until dh) {
            val row = y * step * rowStride
            for (x in 0 until dw) out[y * dw + x] = buffer.get(row + x * step * pixelStride).toInt() and 0xFF
        }
        return LumaFrame(dw, dh, out)
    }
}
