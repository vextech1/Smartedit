package com.vextech.smart_edit.feature.converter.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.ProcessingJobEntity
import com.vextech.smart_edit.core.di.IoDispatcher
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.core.pdf.PdfEngine
import com.vextech.smart_edit.core.pdf.PdfRenderFormat
import com.vextech.smart_edit.core.repository.SmartEditRepository
import com.vextech.smart_edit.feature.converter.domain.ConversionJob
import com.vextech.smart_edit.feature.converter.domain.ConversionRequest
import com.vextech.smart_edit.feature.converter.domain.ConversionResult
import com.vextech.smart_edit.feature.converter.domain.ConverterFailure
import com.vextech.smart_edit.feature.converter.domain.ConverterMessageKey
import com.vextech.smart_edit.feature.converter.domain.ConverterOutput
import com.vextech.smart_edit.feature.converter.domain.ConverterPolicies
import com.vextech.smart_edit.feature.converter.domain.ImageTargetFormat
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class AndroidConverterRepository @Inject constructor(
    private val store: DocumentStore,
    private val pdf: PdfEngine,
    private val db: SmartEditDatabase,
    private val appRepository: SmartEditRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ConverterRepository {

    override fun observeJobs(): Flow<List<ConversionJob>> = db.jobDao().observeByKind(JOB_KIND).map { jobs ->
        jobs.map { job ->
            ConversionJob(
                id = job.id,
                sourceFormat = job.sourceFormat,
                targetFormat = job.targetFormat,
                progress = job.progress,
                status = job.status,
                createdAt = job.createdAt,
                completedAt = job.completedAt,
                errorMessage = job.errorMessage,
            )
        }
    }

    override suspend fun convert(
        request: ConversionRequest,
        onProgress: suspend (Float) -> Unit,
    ): ConversionResult = withContext(io) {
        ConverterPolicies.validateRequest(request)
        val jobId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val sourceUris = request.sourceUris()
        val sourceSize = request.sourceSizeBytes()
        val sourceFormat = request.sourceFormatName()
        val targetFormat = request.targetFormatName()
        val staged = mutableListOf<File>()
        val transient = mutableListOf<File>()
        val generated = mutableListOf<File>()
        val registeredIds = mutableListOf<String>()
        var completed = false
        var lastProgress = 0f

        suspend fun update(progress: Float, status: String = STATUS_RUNNING, error: String? = null, outputs: List<File> = emptyList()) {
            val safeProgress = progress.coerceIn(0f, 1f)
            lastProgress = safeProgress
            db.jobDao().upsert(
                ProcessingJobEntity(
                    id = jobId,
                    kind = JOB_KIND,
                    sourceUri = sourceUris.joinToString("|"),
                    outputUri = outputs.firstOrNull()?.toURI()?.toString(),
                    sourceFormat = sourceFormat,
                    targetFormat = targetFormat,
                    sourceSizeBytes = sourceSize,
                    outputSizeBytes = outputs.takeIf { it.isNotEmpty() }?.sumOf(File::length),
                    progress = safeProgress,
                    status = status,
                    createdAt = createdAt,
                    completedAt = if (status == STATUS_COMPLETED || status == STATUS_FAILED || status == STATUS_CANCELLED) System.currentTimeMillis() else null,
                    errorMessage = error,
                )
            )
            onProgress(safeProgress)
        }

        update(0f)
        try {
            val outputs = when (request) {
                is ConversionRequest.ImagesToPdf -> convertImagesToPdf(request, staged, generated) { update(it) }
                is ConversionRequest.ImagesToFormat -> convertImagesToFormat(request, staged, generated) { update(it) }
                is ConversionRequest.PdfToImages -> convertPdfToImages(request, staged, transient, generated) { update(it) }
                is ConversionRequest.PdfToDocx -> convertPdfToDocx(request, staged, generated) { update(it) }
                is ConversionRequest.PdfToText -> convertPdfToText(request, staged, generated) { update(it) }
            }
            currentCoroutineContext().ensureActive()
            val domainOutputs = outputs.map { file ->
                val entity = appRepository.registerFile(file, ResourceType.CONVERSION, mimeFor(file))
                registeredIds += entity.id
                ConverterOutput(file.name, file.toURI().toString(), mimeFor(file), file.length())
            }
            appRepository.recordHistory(
                action = HistoryAction.CONVERTED,
                description = if (outputs.size == 1) "Converted to ${outputs.first().extension.uppercase()}" else "Converted ${outputs.size} files",
                type = ResourceType.CONVERSION,
                resourceId = jobId,
                uri = outputs.firstOrNull()?.toURI()?.toString(),
                displayName = outputs.firstOrNull()?.name,
            )
            update(1f, STATUS_COMPLETED, outputs = outputs)
            completed = true
            ConversionResult(jobId, domainOutputs)
        } catch (cancel: CancellationException) {
            withContext(NonCancellable) { update(lastProgress, STATUS_CANCELLED, error = "Cancelled") }
            throw cancel
        } catch (t: Throwable) {
            update(lastProgress, STATUS_FAILED, error = t.message ?: t::class.java.simpleName)
            if (isPasswordFailure(t)) throw ConverterFailure(ConverterMessageKey.PASSWORD_REQUIRED_OR_INCORRECT, t)
            if (t is IllegalArgumentException && t.message.orEmpty().contains("page", ignoreCase = true)) {
                throw ConverterFailure(ConverterMessageKey.PAGE_RANGE_INVALID, t)
            }
            if (t is ConverterFailure) throw t
            throw ConverterFailure(ConverterMessageKey.CONVERSION_FAILED, t)
        } finally {
            staged.forEach { it.delete() }
            transient.forEach { it.deleteRecursively() }
            if (!completed) {
                generated.forEach { it.deleteRecursively() }
                if (registeredIds.isNotEmpty()) {
                    withContext(NonCancellable) {
                        registeredIds.forEach { id ->
                            db.documentDao().delete(id)
                            db.searchDao().deleteResource(id)
                        }
                    }
                }
            }
        }
    }

    private suspend fun convertImagesToPdf(
        request: ConversionRequest.ImagesToPdf,
        staged: MutableList<File>,
        generated: MutableList<File>,
        progress: suspend (Float) -> Unit,
    ): List<File> {
        val files = request.sources.mapIndexed { index, source ->
            currentCoroutineContext().ensureActive()
            store.stageContent(Uri.parse(source.uri), suffixFor(source.displayName)).also { staged += it }
                .also { progress(0.15f + ((index + 1f) / request.sources.size) * 0.35f) }
        }
        val out = store.uniqueFile(store.convertedDir, "images_${System.currentTimeMillis()}.pdf").also { generated += it }
        val context = currentCoroutineContext()
        pdf.imageFilesToPdf(
            files,
            out,
            ConverterPolicies.clampQuality(request.options.quality),
            shouldContinue = { context.isActive },
        )
        progress(0.9f)
        return listOf(out)
    }

    private suspend fun convertImagesToFormat(
        request: ConversionRequest.ImagesToFormat,
        staged: MutableList<File>,
        generated: MutableList<File>,
        progress: suspend (Float) -> Unit,
    ): List<File> {
        val quality = ConverterPolicies.clampQuality(request.options.quality)
        val outputs = mutableListOf<File>()
        request.sources.forEachIndexed { index, source ->
            currentCoroutineContext().ensureActive()
            val stagedFile = store.stageContent(Uri.parse(source.uri), suffixFor(source.displayName)).also { staged += it }
            val bitmap = decodeOrientedImage(stagedFile, source.displayName)
            try {
                val base = source.displayName.substringBeforeLast('.', source.displayName).ifBlank { "image_${index + 1}" }
                val out = store.uniqueFile(store.convertedDir, "${store.sanitizeName(base)}.${request.format.extension}").also { generated += it }
                out.outputStream().use { stream ->
                    val format = request.format.bitmapFormat()
                    val effectiveQuality = if (request.format.supportsQuality) quality else 100
                    require(bitmap.compress(format, effectiveQuality, stream)) { "Image conversion failed" }
                }
                outputs += out
            } finally {
                bitmap.recycle()
            }
            progress((index + 1f) / request.sources.size * 0.95f)
        }
        return outputs
    }

    private suspend fun convertPdfToImages(
        request: ConversionRequest.PdfToImages,
        staged: MutableList<File>,
        transient: MutableList<File>,
        generated: MutableList<File>,
        progress: suspend (Float) -> Unit,
    ): List<File> {
        val source = store.stageContent(Uri.parse(request.source.uri), ".pdf").also { staged += it }
        val password = request.options.pdfPassword.takeIf(String::isNotBlank)
        val pageCount = pdf.pageCount(source, password)
        val pages = ConverterPolicies.parsePageRange(request.options.pageRange, pageCount)
        val dpi = ConverterPolicies.normalizeDpi(request.options.dpi)
        val quality = ConverterPolicies.clampQuality(request.options.quality)
        val tempDir = store.temporaryDirectory("converter").also { transient += it }
        val rendered = mutableListOf<File>()
        pages.forEachIndexed { outputIndex, pageIndex ->
            currentCoroutineContext().ensureActive()
            val pageName = "page_${(pageIndex + 1).toString().padStart(4, '0')}.${request.format.extension}"
            val target = File(tempDir, pageName)
            when (request.format) {
                ImageTargetFormat.JPEG -> {
                    pdf.renderPageImage(source, pageIndex, target, dpi, PdfRenderFormat.JPEG, quality, password)
                }
                ImageTargetFormat.PNG -> {
                    pdf.renderPageImage(source, pageIndex, target, dpi, PdfRenderFormat.PNG, 100, password)
                }
                ImageTargetFormat.WEBP -> {
                    val losslessIntermediate = File(tempDir, "render_${outputIndex + 1}.png")
                    pdf.renderPageImage(source, pageIndex, losslessIntermediate, dpi, PdfRenderFormat.PNG, 100, password)
                    val bitmap = requireNotNull(BitmapFactory.decodeFile(losslessIntermediate.absolutePath)) { "Unable to render page ${pageIndex + 1}" }
                    try {
                        target.outputStream().use { stream ->
                            require(bitmap.compress(request.format.bitmapFormat(), quality, stream)) { "Page image conversion failed" }
                        }
                    } finally {
                        bitmap.recycle()
                        losslessIntermediate.delete()
                    }
                }
            }
            rendered += target
            progress((outputIndex + 1f) / pages.size * 0.82f)
        }
        currentCoroutineContext().ensureActive()
        val zip = store.uniqueFile(store.convertedDir, "pdf_pages_${System.currentTimeMillis()}_${request.format.extension}.zip").also { generated += it }
        ZipOutputStream(zip.outputStream().buffered()).use { output ->
            rendered.forEachIndexed { index, file ->
                currentCoroutineContext().ensureActive()
                output.putNextEntry(ZipEntry(file.name))
                file.inputStream().buffered().use { it.copyTo(output) }
                output.closeEntry()
                progress(0.82f + ((index + 1f) / rendered.size) * 0.13f)
            }
        }
        return listOf(zip)
    }

    private suspend fun convertPdfToDocx(
        request: ConversionRequest.PdfToDocx,
        staged: MutableList<File>,
        generated: MutableList<File>,
        progress: suspend (Float) -> Unit,
    ): List<File> {
        val source = store.stageContent(Uri.parse(request.source.uri), ".pdf").also { staged += it }
        progress(0.2f)
        val text = pdf.extractText(source, request.options.pdfPassword.takeIf(String::isNotBlank))
        currentCoroutineContext().ensureActive()
        val base = request.source.displayName.substringBeforeLast('.', "document")
        val out = store.uniqueFile(store.convertedDir, "${store.sanitizeName(base)}.docx").also { generated += it }
        SimpleDocxWriter.write(text, out)
        progress(0.92f)
        return listOf(out)
    }

    private suspend fun convertPdfToText(
        request: ConversionRequest.PdfToText,
        staged: MutableList<File>,
        generated: MutableList<File>,
        progress: suspend (Float) -> Unit,
    ): List<File> {
        val source = store.stageContent(Uri.parse(request.source.uri), ".pdf").also { staged += it }
        progress(0.2f)
        val text = pdf.extractText(source, request.options.pdfPassword.takeIf(String::isNotBlank))
        currentCoroutineContext().ensureActive()
        val base = request.source.displayName.substringBeforeLast('.', "document")
        val out = store.uniqueFile(store.convertedDir, "${store.sanitizeName(base)}.txt").also { generated += it }
        out.writeText(text)
        progress(0.92f)
        return listOf(out)
    }

    private fun decodeOrientedImage(file: File, displayName: String): Bitmap {
        val source = requireNotNull(BitmapFactory.decodeFile(file.absolutePath)) { "Unable to decode $displayName" }
        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.preScale(-1f, 1f); matrix.postRotate(270f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.preScale(-1f, 1f); matrix.postRotate(90f) }
            else -> return source
        }
        val corrected = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (corrected !== source) source.recycle()
        return corrected
    }

    private fun ConversionRequest.sourceUris(): List<String> = when (this) {
        is ConversionRequest.ImagesToPdf -> sources.map { it.uri }
        is ConversionRequest.ImagesToFormat -> sources.map { it.uri }
        is ConversionRequest.PdfToImages -> listOf(source.uri)
        is ConversionRequest.PdfToDocx -> listOf(source.uri)
        is ConversionRequest.PdfToText -> listOf(source.uri)
    }

    private fun ConversionRequest.sourceSizeBytes(): Long = when (this) {
        is ConversionRequest.ImagesToPdf -> sources.sumOf { it.sizeBytes }
        is ConversionRequest.ImagesToFormat -> sources.sumOf { it.sizeBytes }
        is ConversionRequest.PdfToImages -> source.sizeBytes
        is ConversionRequest.PdfToDocx -> source.sizeBytes
        is ConversionRequest.PdfToText -> source.sizeBytes
    }

    private fun ConversionRequest.sourceFormatName(): String = when (this) {
        is ConversionRequest.ImagesToPdf, is ConversionRequest.ImagesToFormat -> "image"
        is ConversionRequest.PdfToImages, is ConversionRequest.PdfToDocx, is ConversionRequest.PdfToText -> "pdf"
    }

    private fun ConversionRequest.targetFormatName(): String = when (this) {
        is ConversionRequest.ImagesToPdf -> "pdf"
        is ConversionRequest.ImagesToFormat -> format.extension
        is ConversionRequest.PdfToImages -> "${format.extension}_zip"
        is ConversionRequest.PdfToDocx -> "docx"
        is ConversionRequest.PdfToText -> "txt"
    }

    private fun ImageTargetFormat.bitmapFormat(): Bitmap.CompressFormat = when (this) {
        ImageTargetFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ImageTargetFormat.PNG -> Bitmap.CompressFormat.PNG
        ImageTargetFormat.WEBP -> Bitmap.CompressFormat.WEBP
    }

    private fun suffixFor(name: String): String {
        val ext = name.substringAfterLast('.', "").takeIf { it.length in 1..8 } ?: "img"
        return ".$ext"
    }

    private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "txt" -> "text/plain"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    private fun isPasswordFailure(t: Throwable): Boolean = sequenceOf(t, t.cause)
        .filterNotNull()
        .any { it::class.java.simpleName.contains("Password", ignoreCase = true) || it.message.orEmpty().contains("password", ignoreCase = true) }

    private companion object {
        const val JOB_KIND = "CONVERSION"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
