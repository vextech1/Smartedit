package com.vextech.smart_edit.feature.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.analytics.FirebaseAnalytics
import com.vextech.smart_edit.core.analytics.AnalyticsService
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.pdf.PdfBoxEngine
import com.vextech.smart_edit.core.repository.SmartEditRepository
import com.vextech.smart_edit.feature.converter.data.AndroidConverterRepository
import com.vextech.smart_edit.feature.converter.domain.ConversionRequest
import com.vextech.smart_edit.feature.converter.domain.ConverterOptions
import com.vextech.smart_edit.feature.converter.domain.ConverterSource
import com.vextech.smart_edit.feature.converter.domain.ImageTargetFormat
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidConverterRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: SmartEditDatabase
    private lateinit var store: DocumentStore
    private lateinit var repository: AndroidConverterRepository
    private lateinit var pdf: PdfBoxEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, SmartEditDatabase::class.java).build()
        store = DocumentStore(context)
        store.root.deleteRecursively()
        pdf = PdfBoxEngine(context)
        val appRepository = SmartEditRepository(
            database,
            store,
            AnalyticsService(FirebaseAnalytics.getInstance(context)),
        )
        repository = AndroidConverterRepository(
            store = store,
            pdf = pdf,
            db = database,
            appRepository = appRepository,
            io = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        database.close()
        store.root.deleteRecursively()
        File(context.cacheDir, "converter-test").deleteRecursively()
    }

    @Test
    fun imageBatchToPdf_createsManagedPdfAndCompletedJob() = runBlocking {
        val images = listOf(imageSource("first.png", Color.RED), imageSource("second.png", Color.BLUE))

        val result = repository.convert(
            ConversionRequest.ImagesToPdf(images, ConverterOptions(quality = 85))
        )

        assertThat(result.outputs).hasSize(1)
        val output = result.outputs.single()
        assertThat(output.mimeType).isEqualTo("application/pdf")
        val file = File(requireNotNull(android.net.Uri.parse(output.uri).path))
        assertThat(file.exists()).isTrue()
        assertThat(pdf.pageCount(file)).isEqualTo(2)
        assertThat(database.documentDao().getByUri(output.uri)).isNotNull()
        assertThat(database.jobDao().snapshot().single().status).isEqualTo("COMPLETED")
    }

    @Test
    fun batchImageFormatConversion_createsOneOutputPerSource() = runBlocking {
        val images = listOf(imageSource("a.png", Color.GREEN), imageSource("b.png", Color.YELLOW))

        val result = repository.convert(
            ConversionRequest.ImagesToFormat(images, ImageTargetFormat.JPEG, ConverterOptions(quality = 70))
        )

        assertThat(result.outputs).hasSize(2)
        assertThat(result.outputs.map { it.mimeType }).containsExactly("image/jpeg", "image/jpeg")
        assertThat(database.documentDao().activeSnapshot().map { it.displayName }).containsAtLeastElementsIn(result.outputs.map { it.name })
    }

    @Test
    fun pdfToImages_honorsSelectedPageRangeAndCreatesZip() = runBlocking {
        val source = pdfSource(pageCount = 3)

        val result = repository.convert(
            ConversionRequest.PdfToImages(
                source = source,
                format = ImageTargetFormat.PNG,
                options = ConverterOptions(pageRange = "2-3", dpi = 96),
            )
        )

        val zipFile = File(requireNotNull(android.net.Uri.parse(result.outputs.single().uri).path))
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            assertThat(entries).containsExactly("page_0002.png", "page_0003.png").inOrder()
        }
    }

    @Test
    fun pdfToTextAndDocx_createReadableOutputs() = runBlocking {
        val source = pdfSource(pageCount = 1)

        val text = repository.convert(ConversionRequest.PdfToText(source, ConverterOptions()))
        val textFile = File(requireNotNull(android.net.Uri.parse(text.outputs.single().uri).path))
        assertThat(textFile.exists()).isTrue()

        val docx = repository.convert(ConversionRequest.PdfToDocx(source, ConverterOptions()))
        val docxFile = File(requireNotNull(android.net.Uri.parse(docx.outputs.single().uri).path))
        assertThat(docxFile.exists()).isTrue()
        ZipFile(docxFile).use { zip ->
            assertThat(zip.getEntry("word/document.xml")).isNotNull()
            assertThat(zip.getEntry("[Content_Types].xml")).isNotNull()
        }
    }

    @Test
    fun invalidPdfPageRange_marksJobFailed() = runBlocking {
        val source = pdfSource(pageCount = 2)

        val result = runCatching {
            repository.convert(
                ConversionRequest.PdfToImages(
                    source,
                    ImageTargetFormat.JPEG,
                    ConverterOptions(pageRange = "3"),
                )
            )
        }

        assertThat(result.isFailure).isTrue()
        assertThat(database.jobDao().snapshot().single().status).isEqualTo("FAILED")
    }

    private fun imageSource(name: String, color: Int): ConverterSource {
        val dir = File(context.cacheDir, "converter-test").apply { mkdirs() }
        val file = File(dir, name)
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return ConverterSource(uri.toString(), name, "image/png", file.length())
    }

    private fun pdfSource(pageCount: Int): ConverterSource {
        val imageFiles = (1..pageCount).map { index ->
            val source = imageSource("page-$index.png", if (index % 2 == 0) Color.CYAN else Color.MAGENTA)
            File(requireNotNull(FileProviderUriPathResolver.path(context, source.uri)))
        }
        val pdfFile = File(context.cacheDir, "converter-test/source-$pageCount.pdf")
        pdf.imageFilesToPdf(imageFiles, pdfFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        return ConverterSource(uri.toString(), pdfFile.name, "application/pdf", pdfFile.length())
    }
}

/** Resolves FileProvider test URIs by copying their bytes to a temporary file path. */
private object FileProviderUriPathResolver {
    fun path(context: Context, uriString: String): String? {
        val uri = android.net.Uri.parse(uriString)
        val out = File(context.cacheDir, "converter-test/resolved-${System.nanoTime()}.png")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use(input::copyTo) } ?: return null
        return out.absolutePath
    }
}
