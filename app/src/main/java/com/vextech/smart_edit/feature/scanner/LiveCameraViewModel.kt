package com.vextech.smart_edit.feature.scanner

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.feature.scanner.data.ScannerRepository
import com.vextech.smart_edit.feature.scanner.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class CameraHint { ALIGN, MOVE_CLOSER, HOLD_STEADY, TOO_DARK }

/** What the viewfinder overlay shows. [quad] is in upright preview coordinates (0..1); [aspect] is preview width / height. */
data class CameraAssist(
    val quad: CropQuad? = null,
    val progress: Float = 0f,
    val hint: CameraHint = CameraHint.ALIGN,
    val aspect: Float = 9f / 16f,
)

@HiltViewModel
class LiveCameraViewModel @Inject constructor(private val store: DocumentStore, private val scanner: ScannerRepository) : ViewModel() {
    private companion object {
        const val MIN_CROP_AREA = 0.20f      // below this the outline is probably not a whole page; keep the full image
        const val CLOSE_ENOUGH_AREA = 0.25f
        const val DARK_MEAN = 45f
    }

    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val _retake = MutableStateFlow<PageQuality?>(null); val retake: StateFlow<PageQuality?> = _retake.asStateFlow()
    private val _exporting = MutableStateFlow(false); val exporting: StateFlow<Boolean> = _exporting.asStateFlow()
    private val _assist = MutableStateFlow(CameraAssist()); val assist: StateFlow<CameraAssist> = _assist.asStateFlow()
    private val _autoShutter = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Emits when the page has been held steady long enough and the shutter should fire. */
    val autoShutter: SharedFlow<Unit> = _autoShutter.asSharedFlow()

    private val stabilizer = CaptureStabilizer()
    @Volatile private var autoEnabled = true
    @Volatile private var latest: CropQuad? = null
    private var smoothed: CropQuad? = null   // touched from the analysis thread only

    fun createOutputFile(): File = store.uniqueFile(store.scansDir, "camera_${System.currentTimeMillis()}.jpg")

    fun setAuto(enabled: Boolean) { autoEnabled = enabled; stabilizer.reset() }

    /** Called on the analysis thread for every frame. [rotation] is the clockwise turn that makes the frame upright. */
    fun analyze(frame: LumaFrame, rotation: Int) {
        val upright = if (rotation % 180 == 0) frame.width.toFloat() / frame.height else frame.height.toFloat() / frame.width
        val raw = DocumentDetector.detect(frame)?.quad?.let { QuadGeometry.rotateClockwise(it, rotation) }
        val prev = smoothed
        val quad = when {
            raw == null -> null
            prev == null || QuadGeometry.distance(prev, raw) > 0.15f -> raw
            else -> QuadGeometry.lerp(prev, raw, 0.5f)      // calm the jitter, but jump when the page really moves
        }
        smoothed = quad; latest = quad
        val status = if (autoEnabled) stabilizer.update(quad, SystemClock.elapsedRealtime()) else CaptureStabilizer.Status(0f, false)
        val hint = when {
            frame.mean() < DARK_MEAN -> CameraHint.TOO_DARK
            quad == null -> CameraHint.ALIGN
            QuadGeometry.area(quad) < CLOSE_ENOUGH_AREA -> CameraHint.MOVE_CLOSER
            status.progress > 0f -> CameraHint.HOLD_STEADY
            else -> CameraHint.ALIGN
        }
        _assist.value = CameraAssist(quad, status.progress, hint, upright)
        if (status.ready) _autoShutter.tryEmit(Unit)
    }

    /** Front camera (or no analysis): no outline, no auto capture. */
    fun clearAssist() { latest = null; smoothed = null; stabilizer.reset(); _assist.value = CameraAssist(aspect = _assist.value.aspect) }

    /** The outline to pre-crop the next capture with, or null to keep the whole image. */
    fun snapshotQuad(): CropQuad? = latest?.takeIf { QuadGeometry.area(it) >= MIN_CROP_AREA }

    /** Tell the stabilizer a shot was taken (manual or auto) so the same page is not captured twice. */
    fun noteCapture() = stabilizer.markCaptured(latest, SystemClock.elapsedRealtime())

    fun registerCapture(sessionId: String, file: File, quad: CropQuad?) = viewModelScope.launch {
        // Judge the shot before the temp file goes away; a blurry or dark page offers a one-tap retake.
        val quality = runCatching { scanner.assessQuality(file, quad) }.getOrDefault(PageQuality.OK)
        runCatching { scanner.addPages(sessionId, listOf(Uri.fromFile(file)), listOf(quad)) }
            .onSuccess { _message.value = "Capture added"; _retake.value = quality.takeIf { it != PageQuality.OK } }
            .onFailure { _message.value = "Unable to add capture" }
        file.delete()
    }

    /** Removes the most recent page of the session (undo for a bad shot). */
    fun dismissRetake() { _retake.value = null }

    fun deleteLast(sessionId: String) = viewModelScope.launch {
        _retake.value = null
        runCatching { scanner.observeSession(sessionId).first()?.pages?.lastOrNull()?.let { scanner.deletePage(sessionId, it.id) } }
            .onFailure { _message.value = "Unable to remove page" }
    }

    /** Exports the session as one PDF; [onResult] gets the file, or null if it failed. */
    fun exportPdf(sessionId: String, onResult: (File?) -> Unit) = viewModelScope.launch {
        _exporting.value = true
        val file = runCatching { scanner.exportPdf(sessionId) }.getOrNull()
        _exporting.value = false
        onResult(file)
    }

    fun error(message: String) { _message.value = message }
    fun clearMessage() { _message.value = null }
    fun observeSession(sessionId: String) = scanner.observeSession(sessionId)
}
