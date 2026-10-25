package com.vextech.smart_edit.core.compression

import android.graphics.Bitmap
import android.net.Uri
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.ProcessingJobEntity
import com.vextech.smart_edit.core.di.IoDispatcher
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.library.CompressionKind
import com.vextech.smart_edit.core.library.CompressionPolicies
import com.vextech.smart_edit.core.library.CompressionPreset
import com.vextech.smart_edit.core.library.CompressionVerdict
import com.vextech.smart_edit.core.library.ImageOutput
import com.vextech.smart_edit.core.library.PdfCompressionMode
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.core.pdf.PdfEngine
import com.vextech.smart_edit.core.processing.ImageProcessor
import com.vextech.smart_edit.core.repository.SmartEditRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of compressing one file. [output] is null when the compressed copy was not smaller:
 * nothing is kept and the original stays as it is.
 */
data class CompressionResult(
    val sourceName: String,
    val originalBytes: Long,
    val compressedBytes: Long,
    val verdict: CompressionVerdict,
    val output: File?,
    val outputMimeType: String?,
    val optimizedImages: Int? = null,
)

class UnsupportedCompressionInputException(val name: String) : IllegalArgumentException("Unsupported file: $name")

@Singleton
class CompressionService @Inject constructor(
    private val store: DocumentStore,
    private val pdf: PdfEngine,
    private val db: SmartEditDatabase,
    private val repository: SmartEditRepository,
    private val processor: ImageProcessor,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Compresses a PDF or an image. [onProgress] gets 0..1 when the work can report it.
     * Partial files are always removed on failure or cancellation.
     */
    suspend fun compress(
        uri: Uri,
        displayName: String,
        mimeType: String,
        preset: CompressionPreset,
        pdfMode: PdfCompressionMode,
        onProgress: (Float) -> Unit = {},
    ): CompressionResult {
        val kind = CompressionPolicies.kindOf(displayName, mimeType)
        if (kind == CompressionKind.UNSUPPORTED) throw UnsupportedCompressionInputException(displayName)
        val jobId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val sourceFormat = if (kind == CompressionKind.PDF) "pdf" else "image"
        db.jobDao().upsert(jobRow(jobId, uri, null, sourceFormat, "compressed", 0, null, 0f, "RUNNING", startedAt, null, null))

        var staged: File? = null
        var produced: File? = null
        try {
            val result = withContext(io) {
                val input = store.stageContent(uri, if (kind == CompressionKind.PDF) ".pdf" else ".img").also { staged = it }
                val originalBytes = input.length()
                val job = currentCoroutineContext()[Job]
                when (kind) {
                    CompressionKind.PDF -> compressPdf(input, displayName, originalBytes, preset, pdfMode, { job?.ensureActive(); onProgress(it) }) { produced = it }
                    else -> compressImage(input, displayName, originalBytes, preset) { produced = it }
                }
            }
            val output = result.output
            if (output != null) {
                repository.registerFile(output, ResourceType.COMPRESSION, requireNotNull(result.outputMimeType))
                repository.recordHistory(
                    HistoryAction.COMPRESSED,
                    "Compressed ${output.name} (saved ${CompressionPolicies.percentSaved(result.originalBytes, result.compressedBytes)}%)",
                    ResourceType.COMPRESSION, jobId, output.toURI().toString(), output.name,
                )
            }
            db.jobDao().upsert(
                jobRow(jobId, uri, output?.toURI()?.toString(), sourceFormat, output?.extension ?: sourceFormat,
                    result.originalBytes, output?.let { result.compressedBytes }, 1f, "COMPLETED", startedAt, System.currentTimeMillis(), null)
            )
            return result
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                produced?.delete()
                db.jobDao().upsert(jobRow(jobId, uri, null, sourceFormat, "compressed", 0, null, 0f, "CANCELLED", startedAt, System.currentTimeMillis(), null))
            }
            throw cancelled
        } catch (error: Exception) {
            produced?.delete()
            db.jobDao().upsert(jobRow(jobId, uri, null, sourceFormat, "compressed", 0, null, 0f, "FAILED", startedAt, System.currentTimeMillis(), error.message))
            throw error
        } finally {
            staged?.delete()
        }
    }

    private fun compressPdf(
        input: File,
        displayName: String,
        originalBytes: Long,
        preset: CompressionPreset,
        mode: PdfCompressionMode,
        onProgress: (Float) -> Unit,
        onOutput: (File) -> Unit,
    ): CompressionResult {
        val target = store.uniqueFile(store.compressedDir, CompressionPolicies.outputName(displayName, "pdf"))
        onOutput(target)
        var optimized: Int? = null
        when (mode) {
            PdfCompressionMode.STANDARD -> {
                val params = CompressionPolicies.embeddedImageParams(preset)
                optimized = pdf.optimizeImages(input, target, params.maxDimension, params.jpegQuality, onProgress).imagesReplaced
            }
            PdfCompressionMode.MAXIMUM -> {
                val raster = CompressionPolicies.rasterParams(preset)
                pdf.rasterCompress(input, target, raster.dpi, raster.jpegQuality)
            }
        }
        return finish(displayName, originalBytes, target, "application/pdf", optimized)
    }

    private fun compressImage(
        input: File,
        displayName: String,
        originalBytes: Long,
        preset: CompressionPreset,
        onOutput: (File) -> Unit,
    ): CompressionResult {
        val params = CompressionPolicies.imageParams(preset)
        val bitmap = processor.decodeOriented(input, params.maxDimension)
        try {
            val output = CompressionPolicies.imageOutput(bitmap.hasAlpha())
            val format = if (output == ImageOutput.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val target = store.writeBitmap(bitmap, store.compressedDir, CompressionPolicies.outputName(displayName, output.extension), format, params.jpegQuality)
            onOutput(target)
            return finish(displayName, originalBytes, target, output.mimeType, null)
        } finally {
            bitmap.recycle()
        }
    }

    /** Keeps the compressed copy only when it is not larger than the original. */
    private fun finish(name: String, originalBytes: Long, target: File, mime: String, optimized: Int?): CompressionResult {
        val compressedBytes = target.length()
        val verdict = CompressionPolicies.verdict(originalBytes, compressedBytes)
        if (verdict == CompressionVerdict.LARGER) {
            target.delete()
            return CompressionResult(name, originalBytes, compressedBytes, verdict, null, null, optimized)
        }
        return CompressionResult(name, originalBytes, compressedBytes, verdict, target, mime, optimized)
    }

    private fun jobRow(
        id: String, source: Uri, outputUri: String?, sourceFormat: String, targetFormat: String,
        sourceSize: Long, outputSize: Long?, progress: Float, status: String, createdAt: Long, completedAt: Long?, error: String?,
    ) = ProcessingJobEntity(id, "COMPRESSION", source.toString(), outputUri, sourceFormat, targetFormat, sourceSize, outputSize, progress, status, createdAt, completedAt, error)
}
