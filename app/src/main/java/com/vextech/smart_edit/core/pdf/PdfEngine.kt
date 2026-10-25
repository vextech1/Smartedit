package com.vextech.smart_edit.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix as PdfBoxMatrix
import com.vextech.smart_edit.core.library.CompressionPolicies
import com.vextech.smart_edit.core.util.CorePolicies
import com.vextech.smart_edit.feature.pdf.domain.PdfAnnotationInfo
import com.vextech.smart_edit.feature.pdf.domain.PdfBookmark
import com.vextech.smart_edit.feature.pdf.domain.PdfDocumentMetadata
import com.vextech.smart_edit.feature.pdf.domain.PdfEditorPolicies
import com.vextech.smart_edit.feature.pdf.domain.PdfFormField
import com.vextech.smart_edit.feature.pdf.domain.PdfPageInfo
import com.vextech.smart_edit.feature.pdf.domain.PdfPoint
import com.vextech.smart_edit.feature.pdf.domain.PdfRect
import com.vextech.smart_edit.feature.pdf.domain.PdfSearchHit
import com.vextech.smart_edit.feature.pdf.domain.PdfSecurityOptions
import com.vextech.smart_edit.feature.pdf.domain.PdfSecurityState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class PdfRenderFormat { JPEG, PNG }

interface PdfEngine {
    fun pageCount(file: File, password: String? = null): Int
    fun pageInfo(file: File, password: String? = null): List<PdfPageInfo>
    fun renderPage(input: File, pageIndex: Int, output: File, dpi: Int = 120, password: String? = null) =
        renderPageImage(input, pageIndex, output, dpi, PdfRenderFormat.JPEG, 88, password)
    fun renderPageImage(
        input: File,
        pageIndex: Int,
        output: File,
        dpi: Int = 120,
        format: PdfRenderFormat = PdfRenderFormat.JPEG,
        quality: Int = 88,
        password: String? = null,
    )
    fun renderPages(input: File, outputDir: File, dpi: Int = 144, quality: Int = 90, password: String? = null): List<File>
    fun merge(inputs: List<File>, output: File)
    fun split(input: File, outputDir: File, password: String? = null): List<File>
    fun reorder(input: File, pageOrder: List<Int>, output: File, password: String? = null)
    fun extractPages(input: File, pages: List<Int>, output: File, password: String? = null)
    fun deletePages(input: File, pages: List<Int>, output: File, password: String? = null)
    fun duplicatePages(input: File, pages: List<Int>, output: File, password: String? = null)
    fun insertPages(input: File, insertAt: Int, source: File, sourcePages: List<Int>, output: File, password: String? = null, sourcePassword: String? = null)
    fun insertBlankPage(input: File, insertAt: Int, widthPoints: Float, heightPoints: Float, output: File, password: String? = null)
    fun rotatePages(input: File, pages: List<Int>, degrees: Int, output: File, password: String? = null)
    fun cropPages(input: File, pages: List<Int>, rect: PdfRect, output: File, password: String? = null)
    fun watermark(input: File, text: String, output: File, password: String? = null)
    fun addPageNumbers(input: File, startNumber: Int, output: File, password: String? = null)
    fun addText(input: File, pageIndex: Int, rect: PdfRect, text: String, fontSize: Float, output: File, password: String? = null)
    fun highlight(input: File, pageIndex: Int, rect: PdfRect, output: File, password: String? = null)
    fun underline(input: File, pageIndex: Int, rect: PdfRect, output: File, password: String? = null)
    fun strikeout(input: File, pageIndex: Int, rect: PdfRect, output: File, password: String? = null)
    fun drawInk(input: File, pageIndex: Int, points: List<PdfPoint>, output: File, password: String? = null)
    fun drawRectangle(input: File, pageIndex: Int, rect: PdfRect, output: File, password: String? = null)
    fun addNote(input: File, pageIndex: Int, rect: PdfRect, text: String, output: File, password: String? = null)
    fun addLink(input: File, pageIndex: Int, rect: PdfRect, url: String, output: File, password: String? = null)
    fun addImage(input: File, pageIndex: Int, rect: PdfRect, bitmap: Bitmap, output: File, password: String? = null)
    fun redact(input: File, pageIndex: Int, rect: PdfRect, output: File, password: String? = null)
    fun annotations(input: File, password: String? = null): List<PdfAnnotationInfo>
    fun removeAnnotation(input: File, annotationId: String, output: File, password: String? = null)
    fun extractText(input: File, password: String? = null): String
    fun search(input: File, query: String, password: String? = null): List<PdfSearchHit>
    fun formFields(input: File, password: String? = null): List<PdfFormField>
    fun setFormField(input: File, fieldName: String, value: String, output: File, password: String? = null)
    fun flattenForms(input: File, output: File, password: String? = null)
    fun metadata(input: File, password: String? = null): PdfDocumentMetadata
    fun updateMetadata(input: File, metadata: PdfDocumentMetadata, output: File, password: String? = null)
    fun bookmarks(input: File, password: String? = null): List<PdfBookmark>
    fun addBookmark(input: File, title: String, pageIndex: Int, output: File, password: String? = null)
    fun removeBookmark(input: File, bookmarkId: String, output: File, password: String? = null)
    fun security(input: File, password: String? = null): PdfSecurityState
    fun protect(input: File, options: PdfSecurityOptions, output: File, currentPassword: String? = null)
    fun removeProtection(input: File, output: File, password: String)
    fun imagesToPdf(images: List<Bitmap>, output: File, jpegQuality: Int = 92)
    fun imageFilesToPdf(
        images: List<File>,
        output: File,
        jpegQuality: Int = 92,
        shouldContinue: () -> Boolean = { true },
        onImageProcessed: (processed: Int, total: Int) -> Unit = { _, _ -> },
    )
    fun rasterCompress(input: File, output: File, dpi: Int, jpegQuality: Int, password: String? = null)

    /**
     * Shrinks a PDF without rendering pages: embedded pictures larger than [maxDimension] are scaled down and
     * re-encoded as JPEG at [jpegQuality] when that is smaller. Text, fonts and structure are untouched.
     * [onProgress] receives 0..1 after each page and may throw to cancel.
     */
    fun optimizeImages(input: File, output: File, maxDimension: Int, jpegQuality: Int, onProgress: (Float) -> Unit = {}): PdfOptimizeStats =
        throw UnsupportedOperationException("optimizeImages is not supported by this engine")
}

data class PdfOptimizeStats(val imagesFound: Int, val imagesReplaced: Int)

@Singleton
class PdfBoxEngine @Inject constructor(@ApplicationContext context: Context) : PdfEngine {
    init { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context) }

    private fun load(file: File, password: String?): PDDocument = PDDocument.load(file, password.orEmpty())

    override fun pageCount(file: File, password: String?): Int = load(file, password).use { it.numberOfPages }

    override fun pageInfo(file: File, password: String?): List<PdfPageInfo> = load(file, password).use { doc ->
        (0 until doc.numberOfPages).map { index ->
            val page = doc.getPage(index)
            val box = page.cropBox
            PdfPageInfo(index, box.width, box.height, PdfEditorPolicies.normalizeRotation(page.rotation))
        }
    }

    override fun renderPage(input: File, pageIndex: Int, output: File, dpi: Int, password: String?) {
        renderPageImage(input, pageIndex, output, dpi, PdfRenderFormat.JPEG, 88, password)
    }

    override fun renderPageImage(
        input: File,
        pageIndex: Int,
        output: File,
        dpi: Int,
        format: PdfRenderFormat,
        quality: Int,
        password: String?,
    ) {
        load(input, password).use { doc ->
            require(pageIndex in 0 until doc.numberOfPages)
            val imageType = if (format == PdfRenderFormat.PNG) ImageType.ARGB else ImageType.RGB
            val bitmap = PDFRenderer(doc).renderImageWithDPI(pageIndex, dpi.toFloat(), imageType)
            output.parentFile?.mkdirs()
            output.outputStream().use { stream ->
                val compression = if (format == PdfRenderFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val effectiveQuality = if (format == PdfRenderFormat.PNG) 100 else quality.coerceIn(10, 100)
                require(bitmap.compress(compression, effectiveQuality, stream)) { "Page rendering failed" }
            }
            bitmap.recycle()
        }
    }

    override fun merge(inputs: List<File>, output: File) {
        require(inputs.size >= 2) { "At least two PDFs are required" }
        output.parentFile?.mkdirs()
        PDFMergerUtility().apply {
            destinationFileName = output.absolutePath
            inputs.forEach(::addSource)
            mergeDocuments(null)
        }
    }

    override fun split(input: File, outputDir: File, password: String?): List<File> = load(input, password).use { doc ->
        outputDir.mkdirs()
        Splitter().split(doc).mapIndexed { i, part ->
            File(outputDir, "${input.nameWithoutExtension}_page_${i + 1}.pdf").also { out -> part.use { it.save(out) } }
        }
    }

    override fun reorder(input: File, pageOrder: List<Int>, output: File, password: String?) {
        load(input, password).use { doc ->
            CorePolicies.validatePageOrder(doc.numberOfPages, pageOrder)
            val pages = (0 until doc.numberOfPages).map(doc::getPage)
            pages.forEach { doc.pages.remove(it) }
            pageOrder.forEach { doc.addPage(pages[it]) }
            doc.save(output)
        }
    }

    override fun extractPages(input: File, pages: List<Int>, output: File, password: String?) {
        load(input, password).use { source ->
            val selection = PdfEditorPolicies.validatePageSelection(source.numberOfPages, pages)
            PDDocument().use { target ->
                target.documentInformation = copyInfo(source.documentInformation)
                selection.forEach { target.importPage(source.getPage(it)) }
                target.save(output)
            }
        }
    }

    override fun deletePages(input: File, pages: List<Int>, output: File, password: String?) {
        load(input, password).use { doc ->
            val selection = PdfEditorPolicies.validatePageSelection(doc.numberOfPages, pages)
            require(selection.size < doc.numberOfPages) { "A PDF must keep at least one page" }
            selection.sortedDescending().forEach { doc.pages.remove(it) }
            doc.save(output)
        }
    }

    override fun duplicatePages(input: File, pages: List<Int>, output: File, password: String?) {
        load(input, password).use { doc ->
            val selection = PdfEditorPolicies.validatePageSelection(doc.numberOfPages, pages)
            val originals = selection.map(doc::getPage)
            originals.forEach { doc.importPage(it) }
            doc.save(output)
        }
    }

    override fun insertPages(input: File, insertAt: Int, source: File, sourcePages: List<Int>, output: File, password: String?, sourcePassword: String?) {
        load(input, password).use { target ->
            require(insertAt in 0..target.numberOfPages)
            load(source, sourcePassword).use { incoming ->
                val selection = PdfEditorPolicies.validatePageSelection(incoming.numberOfPages, sourcePages)
                val originalPages = (0 until target.numberOfPages).map(target::getPage)
                val imported = selection.map { target.importPage(incoming.getPage(it)) }
                val all = buildList {
                    addAll(originalPages.take(insertAt))
                    addAll(imported)
                    addAll(originalPages.drop(insertAt))
                }
                (0 until target.numberOfPages).map(target::getPage).forEach { target.pages.remove(it) }
                all.forEach(target::addPage)
                target.save(output)
            }
        }
    }

    override fun insertBlankPage(input: File, insertAt: Int, widthPoints: Float, heightPoints: Float, output: File, password: String?) {
        require(widthPoints > 0f && heightPoints > 0f)
        load(input, password).use { doc ->
            require(insertAt in 0..doc.numberOfPages)
            val blank = PDPage(PDRectangle(widthPoints, heightPoints))
            if (insertAt == doc.numberOfPages) doc.addPage(blank)
            else doc.pages.insertBefore(blank, doc.getPage(insertAt))
            doc.save(output)
        }
    }

    override fun rotatePages(input: File, pages: List<Int>, degrees: Int, output: File, password: String?) {
        load(input, password).use { doc ->
            val selection = PdfEditorPolicies.validatePageSelection(doc.numberOfPages, pages)
            val delta = PdfEditorPolicies.normalizeRotation(degrees)
            selection.forEach { index ->
                val page = doc.getPage(index)
                page.rotation = PdfEditorPolicies.normalizeRotation(page.rotation + delta)
            }
            doc.save(output)
        }
    }

    override fun cropPages(input: File, pages: List<Int>, rect: PdfRect, output: File, password: String?) {
        load(input, password).use { doc ->
            PdfEditorPolicies.validatePageSelection(doc.numberOfPages, pages).forEach { index ->
                val page = doc.getPage(index)
                page.cropBox = toPageRect(page, rect)
            }
            doc.save(output)
        }
    }

    override fun watermark(input: File, text: String, output: File, password: String?) {
        require(text.isNotBlank())
        load(input, password).use { doc ->
            doc.pages.forEach { page ->
                val box = page.cropBox
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.setNonStrokingColor(135, 135, 135)
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 42f)
                    stream.setTextMatrix(PdfBoxMatrix.getRotateInstance(Math.toRadians(35.0), box.width * .15f, box.height * .45f))
                    stream.showText(safeText(text, 80))
                    stream.endText()
                }
            }
            doc.save(output)
        }
    }

    override fun addPageNumbers(input: File, startNumber: Int, output: File, password: String?) {
        require(startNumber >= 0)
        load(input, password).use { doc ->
            repeat(doc.numberOfPages) { index ->
                val page = doc.getPage(index)
                val label = (startNumber + index).toString()
                val font = PDType1Font.HELVETICA
                val size = 10f
                val textWidth = font.getStringWidth(label) / 1000f * size
                val box = page.cropBox
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.beginText()
                    stream.setNonStrokingColor(70, 70, 70)
                    stream.setFont(font, size)
                    stream.newLineAtOffset(box.lowerLeftX + (box.width - textWidth) / 2f, box.lowerLeftY + 18f)
                    stream.showText(label)
                    stream.endText()
                }
            }
            doc.save(output)
        }
    }

    override fun addText(input: File, pageIndex: Int, rect: PdfRect, text: String, fontSize: Float, output: File, password: String?) {
        require(text.isNotBlank())
        require(fontSize in 6f..96f)
        load(input, password).use { doc ->
            val page = doc.getPage(pageIndex)
            val r = toPageRect(page, rect)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.beginText()
                stream.setNonStrokingColor(20, 20, 20)
                stream.setFont(PDType1Font.HELVETICA, fontSize)
                stream.newLineAtOffset(r.lowerLeftX, r.upperRightY - fontSize)
                text.lineSequence().take(12).forEachIndexed { i, line ->
                    if (i > 0) stream.newLineAtOffset(0f, -(fontSize * 1.25f))
                    stream.showText(safeText(line, 180))
                }
                stream.endText()
            }
            doc.save(output)
        }
    }

    override fun highlight(input: File, pageIndex: Int, rect: PdfRect, output: File, password: String?) {
        load(input, password).use { doc ->
            val page = doc.getPage(pageIndex)
            val r = toPageRect(page, rect)
            addTextMarkup(doc, page, r, PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT, "Highlight", floatArrayOf(1f, .92f, .18f))
            doc.save(output)
        }
    }

    override fun underline(input: File, pageIndex: Int, rect: PdfRect, output: File, password: String?) =
        textMarkup(input, pageIndex, rect, PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE, "Underline", floatArrayOf(.12f, .35f, .9f), output, password)

    override fun strikeout(input: File, pageIndex: Int, rect: PdfRect, output: File, password: String?) =
        textMarkup(input, pageIndex, rect, PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT, "Strikeout", floatArrayOf(.9f, .18f, .18f), output, password)

    private fun textMarkup(
        input: File,
        pageIndex: Int,
        rect: PdfRect,
        subtype: String,
        label: String,
        color: FloatArray,
        output: File,
        password: String?,
    ) {
        load(input, password).use { doc ->
            val page = doc.getPage(pageIndex)
            addTextMarkup(doc, page, toPageRect(page, rect), subtype, label, color)
            doc.save(output)
        }
    }

    private fun addTextMarkup(
        document: PDDocument,
        page: PDPage,
        rect: PDRectangle,
        subtype: String,
        label: String,
        rgb: FloatArray,
    ) {
        val annotation = PDAnnotationTextMarkup(subtype).apply {
            rectangle = rect
            color = PDColor(rgb, PDDeviceRGB.INSTANCE)
            quadPoints = floatArrayOf(
                rect.lowerLeftX, rect.upperRightY,
                rect.upperRightX, rect.upperRightY,
                rect.lowerLeftX, rect.lowerLeftY,
                rect.upperRightX, rect.lowerLeftY,
            )
            contents = label
        }
        page.annotations.add(annotation)
        annotation.constructAppearances(document)
    }

    override fun drawInk(input: File, pageIndex: Int, points: List<PdfPoint>, output: File, password: String?) {
        require(points.size >= 2)
        load(input, password).use { doc ->
            val page = doc.getPage(pageIndex)
            val box = page.cropBox
            fun map(p: PdfPoint): Pair<Float, Float> {
                val mapped = PdfEditorPolicies.visualPointToUnrotated(p, page.rotation)
                return Pair(box.lowerLeftX + mapped.x * box.width, box.lowerLeftY + (1f - mapped.y) * box.height)
            }
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.setStrokingColor(210, 32, 32)
                stream.setLineWidth(2.2f)
                val first = map(points.first())
                stream.moveTo(first.first, first.second)
                points.drop(1).forEach { p -> val q = map(p); stream.lineTo(q.first, q.second) }
                stream.stroke()
            }
            doc.save(output)
        }
    }

    override fun drawRectangle(input: File, pageIndex: Int, rect: PdfRect, output: File, password: String?) {
        load(input, password).use { doc ->
            val page = doc.getPage(pageIndex)
            val r = toPageRect(page, rect)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.setStrokingColor(34, 88, 220)
                stream.setLineWidth(2f)
                stream.addRect(r.lowerLeftX, r.lowerLeftY, r.width, r.height)
                stream.stroke()
            }
            doc.save(output)
        }
    }

    override fun addNote(input: File, pageIndex: Int, rect: PdfRect, text: String, output: File, password: String?) {
        require(text.isNotBlank())
        load(input, password).use { doc ->
            val page = doc.getPage(pageIndex)
            page.annotations.add(PDAnnotationText().apply {
                rectangle = toPageRect(page, rect)
                contents = text.take(1000)
                name = PDAnnotationText.NAME_COMMENT
                setOpen(false)
            })
            doc.save(output)
        }
    }

    override fun addLink(input: File, pageIndex: Int, rect: PdfRect, url: String, output: File, password: String?) {
        val safeUrl = PdfEditorPolicies.validateExternalLink(url)
        load(input, password).use { doc ->
            val page = doc.getPage(pageIndex)
            val pageRect = toPageRect(page, rect)
            val link = PDAnnotationLink().apply {
                rectangle = pageRect
                action = PDActionURI().apply { uri = safeUrl }
                highlightMode = PDAnnotationLink.HIGHLIGHT_MODE_INVERT
                contents = safeUrl
            }
            page.annotations.add(link)
            link.constructAppearances(doc)
            doc.save(output)
        }
    }

    override fun addImage(input: File, pageIndex: Int, rect: PdfRect, bitmap: Bitmap, output: File, password: String?) {
        load(input, password).use { doc ->
            val page = doc.getPage(pageIndex)
            val r = toPageRect(page, rect)
            val image = if (bitmap.hasAlpha()) LosslessFactory.createFromImage(doc, bitmap) else JPEGFactory.createFromImage(doc, bitmap, .92f)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.drawImage(image, r.lowerLeftX, r.lowerLeftY, r.width, r.height)
            }
            doc.save(output)
        }
    }

    override fun redact(input: File, pageIndex: Int, rect: PdfRect, output: File, password: String?) {
        load(input, password).use { source ->
            require(pageIndex in 0 until source.numberOfPages)
            require(!source.isEncrypted) { "Remove PDF security before secure redaction" }

            val savedBookmarks = bookmarksFromDocument(source)
            // Flatten forms so imported pages keep the visible values without dangling AcroForm references.
            source.documentCatalog.acroForm?.flatten()
            val renderer = PDFRenderer(source)
            val selectedPage = source.getPage(pageIndex)
            val selectedBox = selectedPage.cropBox
            val selectedRotation = PdfEditorPolicies.normalizeRotation(selectedPage.rotation)
            val redactedBitmap = renderer.renderImageWithDPI(pageIndex, 180f, ImageType.RGB)
            try {
                val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
                Canvas(redactedBitmap).drawRect(
                    rect.left * redactedBitmap.width,
                    rect.top * redactedBitmap.height,
                    rect.right * redactedBitmap.width,
                    rect.bottom * redactedBitmap.height,
                    paint,
                )

                PDDocument().use { target ->
                    target.documentInformation = copyInfo(source.documentInformation)
                    repeat(source.numberOfPages) { index ->
                        if (index != pageIndex) {
                            target.importPage(source.getPage(index))
                        } else {
                            val visualWidth = if (selectedRotation == 90 || selectedRotation == 270) selectedBox.height else selectedBox.width
                            val visualHeight = if (selectedRotation == 90 || selectedRotation == 270) selectedBox.width else selectedBox.height
                            val page = PDPage(PDRectangle(visualWidth, visualHeight))
                            target.addPage(page)
                            val image = JPEGFactory.createFromImage(target, redactedBitmap, .92f)
                            PDPageContentStream(target, page).use { stream ->
                                stream.drawImage(image, 0f, 0f, visualWidth, visualHeight)
                            }
                        }
                    }
                    restoreBookmarks(target, savedBookmarks)
                    target.save(output)
                }
            } finally {
                redactedBitmap.recycle()
            }
        }
    }

    override fun annotations(input: File, password: String?): List<PdfAnnotationInfo> = load(input, password).use { doc ->
        buildList {
            repeat(doc.numberOfPages) { pageIndex ->
                doc.getPage(pageIndex).annotations.forEachIndexed { annotationIndex, annotation ->
                    add(PdfAnnotationInfo("$pageIndex:$annotationIndex", pageIndex, annotation.subtype ?: "Annotation", annotation.contents.orEmpty()))
                }
            }
        }
    }

    override fun removeAnnotation(input: File, annotationId: String, output: File, password: String?) {
        val parts = annotationId.split(':')
        require(parts.size == 2)
        val pageIndex = parts[0].toInt()
        val annotationIndex = parts[1].toInt()
        load(input, password).use { doc ->
            val list = doc.getPage(pageIndex).annotations
            require(annotationIndex in list.indices)
            list.removeAt(annotationIndex)
            doc.save(output)
        }
    }

    override fun extractText(input: File, password: String?): String = load(input, password).use { PDFTextStripper().getText(it) }

    override fun search(input: File, query: String, password: String?): List<PdfSearchHit> {
        require(query.isNotBlank())
        val needle = query.trim()
        return load(input, password).use { doc ->
            buildList {
                repeat(doc.numberOfPages) { pageIndex ->
                    val stripper = PDFTextStripper().apply { startPage = pageIndex + 1; endPage = pageIndex + 1 }
                    val text = stripper.getText(doc).replace(Regex("\\s+"), " ").trim()
                    var from = 0
                    while (true) {
                        val at = text.indexOf(needle, from, ignoreCase = true)
                        if (at < 0) break
                        val start = (at - 55).coerceAtLeast(0)
                        val end = (at + needle.length + 90).coerceAtMost(text.length)
                        add(PdfSearchHit(pageIndex, text.substring(start, end)))
                        from = at + needle.length
                    }
                }
            }
        }
    }

    override fun formFields(input: File, password: String?): List<PdfFormField> = load(input, password).use { doc ->
        val form = doc.documentCatalog.acroForm ?: return@use emptyList()
        form.fieldTree.map { field -> PdfFormField(field.fullyQualifiedName, field.valueAsString.orEmpty(), field.javaClass.simpleName, field.isReadOnly) }.toList()
    }

    override fun setFormField(input: File, fieldName: String, value: String, output: File, password: String?) {
        load(input, password).use { doc ->
            val form = requireNotNull(doc.documentCatalog.acroForm) { "PDF has no form" }
            val field = requireNotNull(form.getField(fieldName)) { "Form field no longer exists" }
            require(!field.isReadOnly) { "Form field is read-only" }
            field.setValue(value)
            doc.save(output)
        }
    }

    override fun flattenForms(input: File, output: File, password: String?) {
        load(input, password).use { doc ->
            doc.documentCatalog.acroForm?.flatten()
            doc.save(output)
        }
    }

    override fun metadata(input: File, password: String?): PdfDocumentMetadata = load(input, password).use { doc ->
        doc.documentInformation.let { PdfDocumentMetadata(it.title.orEmpty(), it.author.orEmpty(), it.subject.orEmpty(), it.keywords.orEmpty(), it.creator.orEmpty(), it.producer.orEmpty()) }
    }

    override fun updateMetadata(input: File, metadata: PdfDocumentMetadata, output: File, password: String?) {
        load(input, password).use { doc ->
            doc.documentInformation.apply {
                title = metadata.title; author = metadata.author; subject = metadata.subject; keywords = metadata.keywords; creator = metadata.creator; producer = metadata.producer
            }
            doc.save(output)
        }
    }

    override fun bookmarks(input: File, password: String?): List<PdfBookmark> = load(input, password).use(::bookmarksFromDocument)

    private fun bookmarksFromDocument(doc: PDDocument): List<PdfBookmark> {
        val outline = doc.documentCatalog.documentOutline ?: return emptyList()
        val result = mutableListOf<PdfBookmark>()
        var counter = 0
        fun visit(first: PDOutlineItem?) {
            var item = first
            while (item != null) {
                val page = runCatching { item.findDestinationPage(doc) }.getOrNull()
                val pageIndex = if (page == null) -1 else (0 until doc.numberOfPages).firstOrNull { doc.getPage(it).cosObject == page.cosObject } ?: -1
                result += PdfBookmark((counter++).toString(), item.title.orEmpty(), pageIndex)
                visit(item.firstChild)
                item = item.nextSibling
            }
        }
        visit(outline.firstChild)
        return result
    }

    private fun restoreBookmarks(doc: PDDocument, bookmarks: List<PdfBookmark>) {
        val valid = bookmarks.filter { it.pageIndex in 0 until doc.numberOfPages && it.title.isNotBlank() }
        if (valid.isEmpty()) return
        val outline = PDDocumentOutline()
        doc.documentCatalog.documentOutline = outline
        valid.forEach { bookmark ->
            outline.addLast(PDOutlineItem().apply {
                title = bookmark.title.take(160)
                setDestination(doc.getPage(bookmark.pageIndex))
            })
        }
        outline.openNode()
    }

    override fun addBookmark(input: File, title: String, pageIndex: Int, output: File, password: String?) {
        require(title.isNotBlank())
        load(input, password).use { doc ->
            require(pageIndex in 0 until doc.numberOfPages)
            val outline = doc.documentCatalog.documentOutline ?: PDDocumentOutline().also { doc.documentCatalog.documentOutline = it }
            outline.addLast(PDOutlineItem().apply { this.title = title.take(160); setDestination(doc.getPage(pageIndex)) })
            outline.openNode()
            doc.save(output)
        }
    }

    override fun removeBookmark(input: File, bookmarkId: String, output: File, password: String?) {
        val wanted = bookmarkId.toInt()
        load(input, password).use { doc ->
            val outline = requireNotNull(doc.documentCatalog.documentOutline) { "PDF has no bookmarks" }
            var counter = 0
            var found: PDOutlineItem? = null
            fun visit(first: PDOutlineItem?) {
                var item = first
                while (item != null && found == null) {
                    if (counter++ == wanted) { found = item; return }
                    visit(item.firstChild)
                    item = item.nextSibling
                }
            }
            visit(outline.firstChild)
            val target = requireNotNull(found) { "Bookmark no longer exists" }
            unlinkOutlineItem(target)
            doc.save(output)
        }
    }

    private fun unlinkOutlineItem(item: PDOutlineItem) {
        val node = item.cosObject
        val parent = node.getDictionaryObject(COSName.PARENT) as? COSDictionary
        val previous = node.getDictionaryObject(COSName.PREV) as? COSDictionary
        val next = node.getDictionaryObject(COSName.NEXT) as? COSDictionary
        if (previous != null) {
            if (next != null) previous.setItem(COSName.NEXT, next) else previous.removeItem(COSName.NEXT)
        } else if (parent != null) {
            if (next != null) parent.setItem(COSName.FIRST, next) else parent.removeItem(COSName.FIRST)
        }
        if (next != null) {
            if (previous != null) next.setItem(COSName.PREV, previous) else next.removeItem(COSName.PREV)
        } else if (parent != null) {
            if (previous != null) parent.setItem(COSName.LAST, previous) else parent.removeItem(COSName.LAST)
        }
        node.removeItem(COSName.PREV)
        node.removeItem(COSName.NEXT)
        node.removeItem(COSName.PARENT)
        // /Count is optional. Removing stale counts is safer than leaving an invalid outline count.
        var ancestor = parent
        while (ancestor != null) {
            ancestor.removeItem(COSName.COUNT)
            ancestor = ancestor.getDictionaryObject(COSName.PARENT) as? COSDictionary
        }
    }

    override fun security(input: File, password: String?): PdfSecurityState = load(input, password).use { doc ->
        val p = doc.currentAccessPermission
        PdfSecurityState(
            encrypted = doc.isEncrypted,
            canPrint = p.canPrint(),
            canCopy = p.canExtractContent(),
            canModify = p.canModify(),
            canAnnotate = p.canModifyAnnotations(),
            canFillForms = p.canFillInForm(),
            canAssemble = p.canAssembleDocument(),
            isOwner = p.isOwnerPermission,
        )
    }

    override fun protect(input: File, options: PdfSecurityOptions, output: File, currentPassword: String?) {
        PdfEditorPolicies.validateSecurity(options)
        load(input, currentPassword).use { doc ->
            val permission = AccessPermission().apply {
                setCanPrint(options.allowPrint)
                setCanExtractContent(options.allowCopy)
                setCanModify(options.allowModify)
                setCanAssembleDocument(options.allowAssemble)
                setCanModifyAnnotations(options.allowAnnotations)
                setCanFillInForm(options.allowFillForms)
            }
            val policy = StandardProtectionPolicy(options.ownerPassword, options.userPassword, permission).apply {
                setEncryptionKeyLength(128)
                setPreferAES(true)
            }
            doc.protect(policy)
            doc.save(output)
        }
    }

    override fun removeProtection(input: File, output: File, password: String) {
        require(password.isNotBlank())
        load(input, password).use { doc ->
            doc.setAllSecurityToBeRemoved(true)
            doc.save(output)
        }
    }

    override fun renderPages(input: File, outputDir: File, dpi: Int, quality: Int, password: String?): List<File> = load(input, password).use { doc ->
        outputDir.mkdirs()
        val renderer = PDFRenderer(doc)
        (0 until doc.numberOfPages).map { index ->
            val bitmap = renderer.renderImageWithDPI(index, dpi.toFloat(), ImageType.ARGB)
            File(outputDir, "${input.nameWithoutExtension}_${index + 1}.jpg").also { file ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                bitmap.recycle()
            }
        }
    }

    override fun imagesToPdf(images: List<Bitmap>, output: File, jpegQuality: Int) {
        require(images.isNotEmpty())
        PDDocument().use { doc ->
            images.forEach { bitmap ->
                val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                doc.addPage(page)
                val image = if (bitmap.hasAlpha()) LosslessFactory.createFromImage(doc, bitmap) else JPEGFactory.createFromImage(doc, bitmap, jpegQuality / 100f)
                PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
            }
            doc.save(output)
        }
    }

    override fun imageFilesToPdf(
        images: List<File>,
        output: File,
        jpegQuality: Int,
        shouldContinue: () -> Boolean,
        onImageProcessed: (processed: Int, total: Int) -> Unit,
    ) {
        require(images.isNotEmpty())
        PDDocument().use { doc ->
            images.forEachIndexed { index, file ->
                if (!shouldContinue()) throw java.util.concurrent.CancellationException("Cancelled")
                val bitmap = decodeOrientedImage(file)
                try {
                    val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                    doc.addPage(page)
                    val image = if (bitmap.hasAlpha()) LosslessFactory.createFromImage(doc, bitmap)
                    else JPEGFactory.createFromImage(doc, bitmap, jpegQuality.coerceIn(10, 100) / 100f)
                    PDPageContentStream(doc, page).use { stream ->
                        stream.drawImage(image, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                    }
                } finally {
                    bitmap.recycle()
                }
                onImageProcessed(index + 1, images.size)
            }
            if (!shouldContinue()) throw java.util.concurrent.CancellationException("Cancelled")
            output.parentFile?.mkdirs()
            doc.save(output)
        }
    }

    override fun rasterCompress(input: File, output: File, dpi: Int, jpegQuality: Int, password: String?) {
        load(input, password).use { source ->
            val renderer = PDFRenderer(source)
            PDDocument().use { target ->
                target.documentInformation = copyInfo(source.documentInformation)
                repeat(source.numberOfPages) { index ->
                    val srcPage = source.getPage(index)
                    val bitmap = renderer.renderImageWithDPI(index, dpi.toFloat(), ImageType.RGB)
                    val page = PDPage(srcPage.mediaBox)
                    target.addPage(page)
                    val image = JPEGFactory.createFromImage(target, bitmap, jpegQuality / 100f)
                    PDPageContentStream(target, page).use { it.drawImage(image, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
                    bitmap.recycle()
                }
                target.save(output)
            }
        }
    }

    override fun optimizeImages(input: File, output: File, maxDimension: Int, jpegQuality: Int, onProgress: (Float) -> Unit): PdfOptimizeStats {
        var found = 0
        var replaced = 0
        load(input, null).use { doc ->
            val visitedResources = java.util.IdentityHashMap<COSDictionary, Boolean>()
            val replacements = java.util.IdentityHashMap<Any, PDImageXObject?>()
            val total = doc.numberOfPages.coerceAtLeast(1)
            for (index in 0 until doc.numberOfPages) {
                val counts = optimizeResources(doc, doc.getPage(index).resources, maxDimension, jpegQuality, visitedResources, replacements)
                found += counts.first
                replaced += counts.second
                onProgress((index + 1f) / total)
            }
            doc.save(output)
        }
        return PdfOptimizeStats(found, replaced)
    }

    private fun optimizeResources(
        doc: PDDocument,
        resources: PDResources?,
        maxDimension: Int,
        jpegQuality: Int,
        visited: java.util.IdentityHashMap<COSDictionary, Boolean>,
        replacements: java.util.IdentityHashMap<Any, PDImageXObject?>,
    ): Pair<Int, Int> {
        if (resources == null || visited.put(resources.cosObject, true) != null) return 0 to 0
        var found = 0
        var replaced = 0
        for (name in resources.xObjectNames.toList()) {
            val xObject = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
            when (xObject) {
                is PDImageXObject -> {
                    found++
                    val key = xObject.cosObject
                    val replacement = if (replacements.containsKey(key)) {
                        replacements[key]
                    } else {
                        recompressImage(doc, xObject, maxDimension, jpegQuality).also { replacements[key] = it }
                    }
                    if (replacement != null) {
                        resources.put(name, replacement)
                        replaced++
                    }
                }
                is PDFormXObject -> {
                    val inner = optimizeResources(doc, xObject.resources, maxDimension, jpegQuality, visited, replacements)
                    found += inner.first
                    replaced += inner.second
                }
                else -> Unit
            }
        }
        return found to replaced
    }

    /** Returns a smaller JPEG version of [image], or null when the picture should stay as it is. */
    private fun recompressImage(doc: PDDocument, image: PDImageXObject, maxDimension: Int, jpegQuality: Int): PDImageXObject? = runCatching {
        val originalBytes = image.cosObject.length
        val hasMask = image.softMask != null || image.cosObject.containsKey(COSName.MASK)
        if (CompressionPolicies.shouldSkipEmbeddedImage(image.width, image.height, originalBytes, hasMask, image.isStencil)) return@runCatching null
        val bitmap = image.image ?: return@runCatching null
        val (width, height) = CompressionPolicies.scaledSize(bitmap.width, bitmap.height, maxDimension)
        val scaled = if (width != bitmap.width || height != bitmap.height) Bitmap.createScaledBitmap(bitmap, width, height, true) else bitmap
        val candidate = JPEGFactory.createFromImage(doc, scaled, jpegQuality / 100f)
        if (scaled !== bitmap) scaled.recycle()
        candidate.takeIf { it.cosObject.length in 1 until originalBytes }
    }.getOrNull()

    private fun decodeOrientedImage(file: File): Bitmap {
        val source = requireNotNull(BitmapFactory.decodeFile(file.absolutePath)) { "Unable to decode ${file.name}" }
        val orientation = runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = AndroidMatrix()
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

    private fun toPageRect(page: PDPage, rect: PdfRect): PDRectangle {
        val mapped = PdfEditorPolicies.visualRectToUnrotated(rect, page.rotation)
        val box = page.cropBox
        val x = box.lowerLeftX + mapped.left * box.width
        val y = box.lowerLeftY + (1f - mapped.bottom) * box.height
        return PDRectangle(x, y, (mapped.right - mapped.left) * box.width, (mapped.bottom - mapped.top) * box.height)
    }

    private fun safeText(text: String, limit: Int): String = text.take(limit).map { ch -> if (ch.code in 32..255) ch else '?' }.joinToString("")

    private fun copyInfo(info: PDDocumentInformation) = PDDocumentInformation().apply {
        title = info.title; author = info.author; subject = info.subject; keywords = info.keywords; creator = info.creator; producer = info.producer
    }
}
