package com.vextech.smart_edit.feature.pdf.data

import android.net.Uri
import com.vextech.smart_edit.feature.pdf.domain.*
import java.io.File

interface PdfEditorRepository {
    suspend fun open(uri: Uri, password: String? = null): PdfEditorSession
    suspend fun close(sessionId: String, discard: Boolean = true)
    suspend fun session(sessionId: String): PdfEditorSession
    suspend fun pages(sessionId: String): List<PdfPageInfo>
    suspend fun preview(sessionId: String, pageIndex: Int, force: Boolean = false): File
    suspend fun temporaryShareCopy(sessionId: String): File
    suspend fun save(sessionId: String, saveAsName: String? = null): File
    suspend fun undo(sessionId: String): PdfEditorSession
    suspend fun redo(sessionId: String): PdfEditorSession

    suspend fun reorder(sessionId: String, order: List<Int>): PdfEditorSession
    suspend fun deletePages(sessionId: String, pages: List<Int>): PdfEditorSession
    suspend fun duplicatePages(sessionId: String, pages: List<Int>): PdfEditorSession
    suspend fun insertPages(sessionId: String, insertAt: Int, source: Uri, sourcePages: List<Int>, sourcePassword: String? = null): PdfEditorSession
    suspend fun insertBlankPage(sessionId: String, insertAt: Int, widthPoints: Float, heightPoints: Float): PdfEditorSession
    suspend fun rotatePages(sessionId: String, pages: List<Int>, degrees: Int): PdfEditorSession
    suspend fun cropPages(sessionId: String, pages: List<Int>, rect: PdfRect): PdfEditorSession
    suspend fun watermark(sessionId: String, text: String): PdfEditorSession
    suspend fun addPageNumbers(sessionId: String, startNumber: Int = 1): PdfEditorSession
    suspend fun addText(sessionId: String, page: Int, rect: PdfRect, text: String, fontSize: Float): PdfEditorSession
    suspend fun highlight(sessionId: String, page: Int, rect: PdfRect): PdfEditorSession
    suspend fun underline(sessionId: String, page: Int, rect: PdfRect): PdfEditorSession
    suspend fun strikeout(sessionId: String, page: Int, rect: PdfRect): PdfEditorSession
    suspend fun drawInk(sessionId: String, page: Int, points: List<PdfPoint>): PdfEditorSession
    suspend fun drawRectangle(sessionId: String, page: Int, rect: PdfRect): PdfEditorSession
    suspend fun addNote(sessionId: String, page: Int, rect: PdfRect, text: String): PdfEditorSession
    suspend fun addLink(sessionId: String, page: Int, rect: PdfRect, url: String): PdfEditorSession
    suspend fun addImage(sessionId: String, page: Int, rect: PdfRect, image: Uri): PdfEditorSession
    suspend fun redact(sessionId: String, page: Int, rect: PdfRect): PdfEditorSession
    suspend fun annotations(sessionId: String): List<PdfAnnotationInfo>
    suspend fun removeAnnotation(sessionId: String, annotationId: String): PdfEditorSession

    suspend fun extractText(sessionId: String): String
    suspend fun search(sessionId: String, query: String): List<PdfSearchHit>
    suspend fun forms(sessionId: String): List<PdfFormField>
    suspend fun setFormField(sessionId: String, fieldName: String, value: String): PdfEditorSession
    suspend fun flattenForms(sessionId: String): PdfEditorSession
    suspend fun metadata(sessionId: String): PdfDocumentMetadata
    suspend fun updateMetadata(sessionId: String, metadata: PdfDocumentMetadata): PdfEditorSession
    suspend fun bookmarks(sessionId: String): List<PdfBookmark>
    suspend fun addBookmark(sessionId: String, title: String, page: Int): PdfEditorSession
    suspend fun removeBookmark(sessionId: String, bookmarkId: String): PdfEditorSession
    suspend fun security(sessionId: String): PdfSecurityState
    suspend fun protect(sessionId: String, options: PdfSecurityOptions): PdfEditorSession
    suspend fun removeProtection(sessionId: String, password: String): PdfEditorSession

    suspend fun extractToManaged(sessionId: String, pages: List<Int>): File
    suspend fun splitToManaged(sessionId: String): List<File>
    suspend fun mergeToManaged(uris: List<Uri>): File
}
