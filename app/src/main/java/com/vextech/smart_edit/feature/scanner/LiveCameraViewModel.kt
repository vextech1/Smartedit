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

/** ID card mode takes two shots: the front first, then (optionally) the back. */
enum class IdStep { FRONT, BACK }

private class IdSide(val file: File, val crop: CropQuad?)

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
        const val DARK_MEAN = 45f
    }

    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val _mode = MutableStateFlow(ScanMode.DOCUMENT); val mode: StateFlow<ScanMode> = _mode.asStateFlow()
    private val _idStep = MutableStateFlow(IdStep.FRONT); val idStep: StateFlow<IdStep> = _idStep.asStateFlow()
    private var idFront: IdSide? = null
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
        val mode = _mode.value
        // Small subjects (cards, receipts) are detected below the default minimum; whether to crop to them is decided at capture.
        val raw = DocumentDetector.detect(frame, minOf(mode.minArea, 0.12f))?.quad?.let { QuadGeometry.rotateClockwise(it, rotation) }
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
            QuadGeometry.area(quad) < mode.minArea -> CameraHint.MOVE_CLOSER
            status.progress > 0f -> CameraHint.HOLD_STEADY
            else -> CameraHint.ALIGN
        }
        _assist.value = CameraAssist(quad, status.progress, hint, upright)
        if (status.ready) _autoShutter.tryEmit(Unit)
    }

    /** Front camera (or no analysis): no outline, no auto capture. */
    fun clearAssist() { latest = null; smoothed = null; stabilizer.reset(); _assist.value = CameraAssist(aspect = _assist.value.aspect) }

    /** The outline to pre-crop the next capture with, or null to keep the whole image. */
    fun snapshotQuad(): CropQuad? { val mode = _mode.value; return latest?.takeIf { mode.crops && QuadGeometry.area(it) >= mode.minArea } }

    /** Tell the stabilizer a shot was taken (manual or auto) so the same page is not captured twice. */
    fun noteCapture() = stabilizer.markCaptured(latest, SystemClock.elapsedRealtime())

    fun registerCapture(sessionId: String, file: File, quad: CropQuad?) = viewModelScope.launch {
        val mode = _mode.value
        val uri = Uri.fromFile(file)
        // Judge the shot before the temp file goes away; a blurry or dark page offers a one-tap retake.
        val quality = runCatching { scanner.assessQuality(file, quad.takeIf { mode.crops }) }.getOrDefault(PageQuality.OK)
        var keepFile = false
        runCatching {
            when (mode) {
                ScanMode.DOCUMENT -> scanner.addPages(sessionId, listOf(uri), listOf(quad))
                ScanMode.RECEIPT, ScanMode.WHITEBOARD -> scanner.addPages(sessionId, listOf(uri), listOf(quad), autoFilter = false, presetFilter = ScanFilter.CLEAN)
                ScanMode.BOOK -> scanner.addPages(sessionId, listOf(uri), autoFilter = false, presetFilter = ScanFilter.CLEAN, splitSpreads = true)
                ScanMode.ID_CARD -> {
                    val front = idFront
                    if (front == null) { idFront = IdSide(file, quad); _idStep.value = IdStep.BACK; keepFile = true }   // wait for the back
                    else { scanner.addIdCard(sessionId, front.file, front.crop, file, quad); front.file.delete(); idFront = null; _idStep.value = IdStep.FRONT }
                }
            }
        }
            .onSuccess { _message.value = if (keepFile) null else "Capture added"; _retake.value = quality.takeIf { it != PageQuality.OK } }
            .onFailure { _message.value = "Unable to add capture" }
        if (!keepFile) file.delete()
    }

    fun setMode(mode: ScanMode) {
        if (_mode.value == mode) return
        discardFront()
        stabilizer.reset()
        _mode.value = mode
    }

    /** ID card: finish with the front side only. [onDone] runs afterwards (also when there is nothing pending). */
    fun skipBack(sessionId: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        val front = idFront
        if (front != null) {
            runCatching { scanner.addIdCard(sessionId, front.file, front.crop, null, null) }
                .onFailure { _message.value = "Unable to add capture" }
            front.file.delete(); idFront = null; _idStep.value = IdStep.FRONT
        }
        onDone()
    }

    /** Retake prompt: drops the pending ID front if there is one, otherwise the newest page. */
    fun retake(sessionId: String) { if (idFront != null) { discardFront(); _retake.value = null } else deleteLast(sessionId) }

    private fun discardFront() { idFront?.file?.delete(); idFront = null; _idStep.value = IdStep.FRONT }

    override fun onCleared() { idFront?.file?.delete(); super.onCleared() }

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
