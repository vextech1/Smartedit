package com.vextech.editpro.core.compression

import android.graphics.Bitmap
import android.net.Uri
import com.vextech.editpro.core.database.EditProDatabase
import com.vextech.editpro.core.database.entity.ProcessingJobEntity
import com.vextech.editpro.core.di.IoDispatcher
import com.vextech.editpro.core.files.DocumentStore
import com.vextech.editpro.core.library.CompressionKind
import com.vextech.editpro.core.library.CompressionPolicies
import com.vextech.editpro.core.library.CompressionPreset
import com.vextech.editpro.core.library.CompressionVerdict
import com.vextech.editpro.core.library.ImageOutput
import com.vextech.editpro.core.library.PdfCompressionMode
import com.vextech.editpro.core.library.PdfPasswordIssue
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.vextech.editpro.core.model.HistoryAction
import com.vextech.editpro.core.model.ResourceType
import com.vextech.editpro.core.pdf.PdfEngine
import com.vextech.editpro.core.processing.ImageProcessor
import com.vextech.editpro.core.repository.EditProRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

/** The PDF is protected. [issue] says whether no password was given or the given one was wrong. */
class PdfPasswordException(val issue: PdfPasswordIssue) : IllegalArgumentException("PDF password ${issue.name.lowercase()}")

@Singleton
class CompressionService @Inject constructor(
    private val store: DocumentStore,
    private val pdf: PdfEngine,
    private val db: EditProDatabase,
    private val repository: EditProRepository,
    private val processor: ImageProcessor,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Compresses a PDF or an image. [onProgress] gets 0..1 when the work can report it.
     * [password] opens a protected PDF; the compressed copy stays protected with it. It is never stored.
     * Partial files are always removed on failure or cancellation.
     * @throws PdfPasswordException when a PDF needs a password or the one given is wrong.
     */
    suspend fun compress(
        uri: Uri,
        displayName: String,
        mimeType: String,
        preset: CompressionPreset,
        pdfMode: PdfCompressionMode,
        password: String? = null,
        grayscale: Boolean = false,
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
        var sourceBytes = 0L
        try {
            val result = withContext(io) {
                val input = store.stageContent(uri, if (kind == CompressionKind.PDF) ".pdf" else ".img").also { staged = it }
                val originalBytes = input.length().also { sourceBytes = it }
                val job = currentCoroutineContext()[Job]
                when (kind) {
                    CompressionKind.PDF -> compressPdf(input, displayName, originalBytes, preset, pdfMode, password, grayscale, { job?.ensureActive(); onProgress(it) }) { produced = it }
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
                db.jobDao().upsert(jobRow(jobId, uri, null, sourceFormat, "compressed", sourceBytes, null, 0f, "CANCELLED", startedAt, System.currentTimeMillis(), null))
            }
            throw cancelled
        } catch (error: Exception) {
            produced?.delete()
            val failure = if (error is InvalidPasswordException) PdfPasswordException(CompressionPolicies.passwordIssue(password)) else error
            db.jobDao().upsert(jobRow(jobId, uri, null, sourceFormat, "compressed", sourceBytes, null, 0f, "FAILED", startedAt, System.currentTimeMillis(), failure.message))
            throw failure
        } finally {
            staged?.delete()
        }
    }

    /**
     * Puts the kept results into one ZIP, so many files can be saved or shared at once. Entry names are made unique.
     * The ZIP lives next to the results; the caller removes it when it is no longer needed. A partial file is removed on failure.
     */
    suspend fun bundle(outputs: List<File>): File = withContext(io) {
        val zip = store.uniqueFile(store.compressedDir, "compressed-files.zip")
        try {
            val names = CompressionPolicies.zipEntryNames(outputs.map { it.name })
            ZipOutputStream(zip.outputStream().buffered()).use { out ->
                outputs.forEachIndexed { index, file ->
                    ensureActive()
                    out.putNextEntry(ZipEntry(names[index]))
                    file.inputStream().buffered().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            zip
        } catch (error: Throwable) {
            zip.delete()
            throw error
        }
    }

    private fun compressPdf(
        input: File,
        displayName: String,
        originalBytes: Long,
        preset: CompressionPreset,
        mode: PdfCompressionMode,
        password: String?,
        grayscale: Boolean,
        onProgress: (Float) -> Unit,
        onOutput: (File) -> Unit,
    ): CompressionResult {
        val target = store.uniqueFile(store.compressedDir, CompressionPolicies.outputName(displayName, "pdf"))
        onOutput(target)
        var optimized: Int? = null
        // A black-and-white copy turns every page into a picture, so it always uses the raster path.
        when (CompressionPolicies.effectivePdfMode(mode, grayscale)) {
            PdfCompressionMode.STANDARD -> {
                val params = CompressionPolicies.embeddedImageParams(preset)
                optimized = pdf.optimizeImages(input, target, params.maxDimension, params.jpegQuality, onProgress, password).imagesReplaced
            }
            PdfCompressionMode.MAXIMUM -> {
                val raster = CompressionPolicies.rasterParams(preset)
                pdf.rasterCompress(input, target, raster.dpi, raster.jpegQuality, password, onProgress, grayscale)
            }
        }
        // A grayscale copy is wanted even when it is not smaller (the site does the same).
        return finish(displayName, originalBytes, target, "application/pdf", optimized, keepLarger = CompressionPolicies.keepsLargerResult(grayscale))
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
            val output = CompressionPolicies.imageOutput(processor.hasTransparentPixels(bitmap))
            val format = if (output == ImageOutput.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val target = store.writeBitmap(bitmap, store.compressedDir, CompressionPolicies.outputName(displayName, output.extension), format, params.jpegQuality)
            onOutput(target)
            return finish(displayName, originalBytes, target, output.mimeType, null)
        } finally {
            bitmap.recycle()
        }
    }

    /** Keeps the compressed copy only when it is not larger than the original. */
    private fun finish(name: String, originalBytes: Long, target: File, mime: String, optimized: Int?, keepLarger: Boolean = false): CompressionResult {
        val compressedBytes = target.length()
        val verdict = CompressionPolicies.verdict(originalBytes, compressedBytes)
        if (verdict == CompressionVerdict.LARGER && !keepLarger) {
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
