package com.vextech.smart_edit.feature.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.vextech.smart_edit.core.pdf.PdfBoxEngine
import com.vextech.smart_edit.feature.pdf.domain.PdfRect
import com.vextech.smart_edit.feature.pdf.domain.PdfSecurityOptions
import java.io.File
import java.util.concurrent.CancellationException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfBoxEngineTest {
    private lateinit var context: Context
    private lateinit var engine: PdfBoxEngine
    private lateinit var dir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        engine = PdfBoxEngine(context)
        dir = File(context.cacheDir, "pdf-engine-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun structuralEdits_preserveUsableDocumentAndOrdering() {
        val source = samplePdf("source.pdf", 3)
        val reordered = file("reordered.pdf")
        engine.reorder(source, listOf(2, 0, 1), reordered)
        assertThat(engine.pageCount(reordered)).isEqualTo(3)
        assertThat(engine.search(reordered, "PAGE_3").single().pageIndex).isEqualTo(0)

        val deleted = file("deleted.pdf")
        engine.deletePages(reordered, listOf(1), deleted)
        assertThat(engine.pageCount(deleted)).isEqualTo(2)

        val duplicated = file("duplicated.pdf")
        engine.duplicatePages(deleted, listOf(0), duplicated)
        assertThat(engine.pageCount(duplicated)).isEqualTo(3)

        val rotated = file("rotated.pdf")
        engine.rotatePages(duplicated, listOf(0), 90, rotated)
        assertThat(engine.pageInfo(rotated).first().rotation).isEqualTo(90)

        val cropped = file("cropped.pdf")
        val before = engine.pageInfo(rotated).first()
        engine.cropPages(rotated, listOf(0), PdfRect(.1f, .1f, .9f, .9f), cropped)
        val after = engine.pageInfo(cropped).first()
        assertThat(after.widthPoints).isLessThan(before.widthPoints)
        assertThat(after.heightPoints).isLessThan(before.heightPoints)
    }

    @Test
    fun blankPagesPageNumbersAndLinks_roundTrip() {
        val source = samplePdf("organize.pdf", 2)
        val blankInserted = file("blank-inserted.pdf")
        val first = engine.pageInfo(source).first()
        engine.insertBlankPage(source, 1, first.widthPoints, first.heightPoints, blankInserted)
        assertThat(engine.pageCount(blankInserted)).isEqualTo(3)
        assertThat(engine.search(blankInserted, "PAGE_2").single().pageIndex).isEqualTo(2)

        val numbered = file("numbered.pdf")
        engine.addPageNumbers(blankInserted, 101, numbered)
        assertThat(engine.search(numbered, "101")).isNotEmpty()

        val linked = file("linked.pdf")
        engine.addLink(numbered, 0, PdfRect(.1f, .35f, .8f, .45f), "https://example.com", linked)
        val link = engine.annotations(linked).single { it.type == "Link" }
        assertThat(link.contents).isEqualTo("https://example.com")

        val noLink = file("no-link.pdf")
        engine.removeAnnotation(linked, link.id, noLink)
        assertThat(engine.annotations(noLink).none { it.type == "Link" }).isTrue()
    }

    @Test
    fun textMarkupAnnotations_areDiscoverableAndRemovable() {
        val source = samplePdf("annotations.pdf", 1)
        val highlighted = file("highlighted.pdf")
        val underlined = file("underlined.pdf")
        val struck = file("struck.pdf")
        val noted = file("noted.pdf")
        val rect = PdfRect(.1f, .1f, .7f, .2f)

        engine.highlight(source, 0, rect, highlighted)
        engine.underline(highlighted, 0, rect, underlined)
        engine.strikeout(underlined, 0, rect, struck)
        engine.addNote(struck, 0, PdfRect(.75f, .1f, .9f, .25f), "Review this", noted)

        val annotations = engine.annotations(noted)
        assertThat(annotations.map { it.type }).containsAtLeast("Highlight", "Underline", "StrikeOut", "Text")

        val removed = file("removed.pdf")
        engine.removeAnnotation(noted, annotations.first().id, removed)
        assertThat(engine.annotations(removed)).hasSize(annotations.size - 1)
    }

    @Test
    fun searchBookmarksAndMetadata_roundTrip() {
        val source = samplePdf("intelligence.pdf", 2)
        assertThat(engine.search(source, "PAGE_2").single().pageIndex).isEqualTo(1)
        assertThat(engine.metadata(source).title).isEqualTo("Smart Edit fixture")

        val bookmarked = file("bookmarked.pdf")
        engine.addBookmark(source, "Second page", 1, bookmarked)
        val bookmark = engine.bookmarks(bookmarked).single()
        assertThat(bookmark.title).isEqualTo("Second page")
        assertThat(bookmark.pageIndex).isEqualTo(1)

        val noBookmark = file("no-bookmark.pdf")
        engine.removeBookmark(bookmarked, bookmark.id, noBookmark)
        assertThat(engine.bookmarks(noBookmark)).isEmpty()
    }

    @Test
    fun passwordProtection_exposesPermissionsAndCanBeRemovedByOwner() {
        val source = samplePdf("plain.pdf", 1)
        val protected = file("protected.pdf")
        engine.protect(
            source,
            PdfSecurityOptions(
                userPassword = "reader",
                ownerPassword = "owner-secret",
                allowPrint = false,
                allowCopy = false,
                allowModify = false,
                allowAssemble = true,
                allowAnnotations = true,
                allowFillForms = true,
            ),
            protected,
        )

        assertThat(runCatching { engine.pageCount(protected) }.isFailure).isTrue()
        val userSecurity = engine.security(protected, "reader")
        assertThat(userSecurity.encrypted).isTrue()
        assertThat(userSecurity.canModify).isFalse()
        assertThat(userSecurity.canAssemble).isTrue()
        assertThat(userSecurity.canAnnotate).isTrue()
        assertThat(userSecurity.isOwner).isFalse()
        assertThat(engine.security(protected, "owner-secret").isOwner).isTrue()

        val unprotected = file("unprotected.pdf")
        engine.removeProtection(protected, unprotected, "owner-secret")
        assertThat(engine.pageCount(unprotected)).isEqualTo(1)
        assertThat(engine.security(unprotected).encrypted).isFalse()
    }

    @Test
    fun formFields_canBeFilledAndFlattened() {
        val source = formPdf("form.pdf")
        val fields = engine.formFields(source)
        assertThat(fields).hasSize(1)
        assertThat(fields.single().name).isEqualTo("customer")
        assertThat(fields.single().value).isEqualTo("Initial")

        val filled = file("filled.pdf")
        engine.setFormField(source, "customer", "Updated", filled)
        assertThat(engine.formFields(filled).single().value).isEqualTo("Updated")

        val flattened = file("flattened.pdf")
        engine.flattenForms(filled, flattened)
        assertThat(engine.formFields(flattened)).isEmpty()
        assertThat(engine.pageCount(flattened)).isEqualTo(1)
    }

    @Test
    fun secureRedaction_rejectsEncryptedPdfUntilProtectionIsRemoved() {
        val source = samplePdf("redact-protected-source.pdf", 1)
        val protected = file("redact-protected.pdf")
        engine.protect(
            source,
            PdfSecurityOptions(
                userPassword = "reader",
                ownerPassword = "owner-secret",
                allowPrint = true,
                allowCopy = true,
                allowModify = true,
                allowAssemble = true,
                allowAnnotations = true,
                allowFillForms = true,
            ),
            protected,
        )

        val attempted = file("should-not-exist.pdf")
        assertThat(
            runCatching {
                engine.redact(protected, 0, PdfRect(.05f, .05f, .95f, .3f), attempted, "owner-secret")
            }.isFailure
        ).isTrue()
    }

    @Test
    fun secureRedaction_removesUnderlyingSearchableTextFromAffectedPage() {
        val source = samplePdf("redact.pdf", 1)
        assertThat(engine.search(source, "PAGE_1")).isNotEmpty()

        val redacted = file("redacted.pdf")
        engine.redact(source, 0, PdfRect(.05f, .05f, .95f, .3f), redacted)

        assertThat(engine.pageCount(redacted)).isEqualTo(1)
        assertThat(engine.search(redacted, "PAGE_1")).isEmpty()
    }

    @Test
    fun mergeSplitAndExtract_produceValidOutputs() {
        val first = samplePdf("first.pdf", 2)
        val second = samplePdf("second.pdf", 1)
        val merged = file("merged.pdf")
        engine.merge(listOf(first, second), merged)
        assertThat(engine.pageCount(merged)).isEqualTo(3)

        val extracted = file("extracted.pdf")
        engine.extractPages(merged, listOf(2, 0), extracted)
        assertThat(engine.pageCount(extracted)).isEqualTo(2)

        val splitDir = File(dir, "split")
        val parts = engine.split(merged, splitDir)
        assertThat(parts).hasSize(3)
        parts.forEach { assertThat(engine.pageCount(it)).isEqualTo(1) }
    }

    @Test
    fun rasterCompress_reportsProgressAndWritesNothingWhenCancelled() {
        val source = samplePdf("raster-progress.pdf", 3)
        val reported = mutableListOf<Float>()
        val output = file("raster-progress-out.pdf")
        engine.rasterCompress(source, output, 72, 60, onProgress = { reported += it })
        assertThat(engine.pageCount(output)).isEqualTo(3)
        assertThat(reported.first()).isEqualTo(0f)
        assertThat(reported.last()).isEqualTo(1f)
        assertThat(reported).isInOrder()

        val cancelled = file("raster-cancelled.pdf")
        val failure = runCatching {
            engine.rasterCompress(source, cancelled, 72, 60, onProgress = { if (it > 0f) throw CancellationException("stop") })
        }.exceptionOrNull()
        assertThat(failure).isInstanceOf(CancellationException::class.java)
        assertThat(cancelled.exists()).isFalse()
    }

    @Test
    fun rasterCompress_keepsTheShapeOfRotatedPages() {
        val source = file("rotated-source.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle(200f, 100f)).apply { rotation = 90 })
            document.save(source)
        }
        val output = file("rotated-raster.pdf")
        engine.rasterCompress(source, output, 72, 60)

        val page = engine.pageInfo(output).single()
        assertThat(page.widthPoints).isWithin(0.5f).of(100f)
        assertThat(page.heightPoints).isWithin(0.5f).of(200f)
        assertThat(page.rotation).isEqualTo(0)
    }

    @Test
    fun compression_keepsProtectedPdfProtectedAndNeedsThePassword() {
        val source = samplePdf("compress-plain.pdf", 2)
        val protected = file("compress-protected.pdf")
        engine.protect(source, PdfSecurityOptions(userPassword = "reader", ownerPassword = "owner-secret"), protected)

        val withoutPassword = runCatching { engine.optimizeImages(protected, file("no-password.pdf"), 1200, 60) }.exceptionOrNull()
        assertThat(withoutPassword).isInstanceOf(InvalidPasswordException::class.java)
        val wrongPassword = runCatching { engine.rasterCompress(protected, file("wrong-password.pdf"), 72, 60, password = "nope") }.exceptionOrNull()
        assertThat(wrongPassword).isInstanceOf(InvalidPasswordException::class.java)

        val optimized = file("optimized-protected.pdf")
        engine.optimizeImages(protected, optimized, 1200, 60, password = "reader")
        assertThat(runCatching { engine.pageCount(optimized) }.isFailure).isTrue()
        assertThat(engine.pageCount(optimized, "reader")).isEqualTo(2)

        val raster = file("raster-protected.pdf")
        engine.rasterCompress(protected, raster, 72, 60, password = "reader")
        assertThat(runCatching { engine.pageCount(raster) }.isFailure).isTrue()
        assertThat(engine.pageCount(raster, "reader")).isEqualTo(2)
    }

    private fun formPdf(name: String): File {
        val output = file(name)
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.LETTER))
            val form = PDAcroForm(document).apply { needAppearances = true }
            document.documentCatalog.acroForm = form
            val field = PDTextField(form).apply {
                partialName = "customer"
                value = "Initial"
            }
            form.fields = listOf(field)
            document.save(output)
        }
        return output
    }

    private fun samplePdf(name: String, pages: Int): File {
        val output = file(name)
        PDDocument().use { document ->
            document.documentInformation.title = "Smart Edit fixture"
            repeat(pages) { index ->
                val page = PDPage(PDRectangle.LETTER)
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 18f)
                    stream.newLineAtOffset(72f, 700f)
                    stream.showText("PAGE_${index + 1} searchable content")
                    stream.endText()
                }
            }
            document.save(output)
        }
        return output
    }

    private fun file(name: String): File = File(dir, name)
}
