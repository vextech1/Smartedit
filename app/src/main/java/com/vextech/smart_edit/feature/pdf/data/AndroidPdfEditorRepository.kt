package com.vextech.smart_edit.feature.pdf.data

import android.graphics.BitmapFactory
import android.net.Uri
import com.vextech.smart_edit.core.di.IoDispatcher
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.files.UriMetadataReader
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.core.pdf.PdfEngine
import com.vextech.smart_edit.core.repository.SmartEditRepository
import com.vextech.smart_edit.feature.pdf.domain.*
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class AndroidPdfEditorRepository @Inject constructor(
    private val store: DocumentStore,
    private val metadataReader: UriMetadataReader,
    private val engine: PdfEngine,
    private val appRepository: SmartEditRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PdfEditorRepository {

    private enum class EditPermission { MODIFY, ASSEMBLE, ANNOTATE, FORM }

    private data class Holder(
        val id: String,
        val dir: File,
        val working: File,
        var sourceFile: File?,
        var sourceName: String,
        var password: String?,
        val history: WorkingCopyHistory,
        var dirty: Boolean = false,
    )

    private val sessions = LinkedHashMap<String, Holder>()

    override suspend fun open(uri: Uri, password: String?): PdfEditorSession = withContext(io) {
        val id = UUID.randomUUID().toString()
        val dir = File(store.root, "pdf_sessions/$id").apply { mkdirs() }
        val working = File(dir, "working.pdf")
        val metadata = runCatching { metadataReader.read(uri) }.getOrNull()
        val managed = managedFile(uri)
        try {
            if (managed != null) managed.copyTo(working, overwrite = true)
            else {
                val staged = store.stageContent(uri, ".pdf")
                try { staged.copyTo(working, overwrite = true) } finally { staged.delete() }
            }
            engine.pageCount(working, password)
            val holder = Holder(
                id = id,
                dir = dir,
                working = working,
                sourceFile = managed,
                sourceName = metadata?.name?.takeIf { it.isNotBlank() } ?: managed?.name ?: "document.pdf",
                password = password,
                history = WorkingCopyHistory(File(dir, "history")),
            )
            sessions[id] = holder
            state(holder)
        } catch (t: Throwable) {
            dir.deleteRecursively()
            throw t
        }
    }

    override suspend fun close(sessionId: String, discard: Boolean) = withContext(io) {
        sessions.remove(sessionId)?.let { holder ->
            if (discard || !holder.dirty) holder.dir.deleteRecursively()
        }
    }

    override suspend fun session(sessionId: String): PdfEditorSession = withContext(io) { state(requireHolder(sessionId)) }

    override suspend fun pages(sessionId: String): List<PdfPageInfo> = withContext(io) {
        val h = requireHolder(sessionId)
        engine.pageInfo(h.working, h.password)
    }

    override suspend fun preview(sessionId: String, pageIndex: Int, force: Boolean): File = withContext(io) {
        val h = requireHolder(sessionId)
        val dir = File(h.dir, "previews").apply { mkdirs() }
        val out = File(dir, "page_${pageIndex}.jpg")
        if (force || !out.isFile || out.lastModified() < h.working.lastModified()) {
            engine.renderPage(h.working, pageIndex, out, 110, h.password)
        }
        out
    }

    override suspend fun temporaryShareCopy(sessionId: String): File = withContext(io) {
        val h = requireHolder(sessionId)
        store.temporaryFile("${h.sourceName.substringBeforeLast('.', h.sourceName)}_preview.pdf").also { h.working.copyTo(it, overwrite = true) }
    }

    override suspend fun save(sessionId: String, saveAsName: String?): File = withContext(io) {
        val h = requireHolder(sessionId)
        val requested = saveAsName?.trim()?.takeIf { it.isNotEmpty() }
        val target = when {
            requested != null -> store.uniqueFile(store.documentsDir, ensurePdfName(requested))
            h.sourceFile != null -> h.sourceFile!!
            else -> store.uniqueFile(store.documentsDir, ensurePdfName(h.sourceName))
        }
        if (target != h.working) {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
            try {
                h.working.copyTo(temp, overwrite = true)
                if (target.exists() && !target.delete()) error("Unable to replace ${target.name}")
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            } finally { temp.delete() }
        }
        val entity = appRepository.registerFile(target, ResourceType.PDF, "application/pdf")
        appRepository.recordHistory(HistoryAction.EDITED, "Saved PDF ${target.name}", ResourceType.PDF, entity.id, entity.uri, entity.displayName)
        h.sourceFile = target
        h.sourceName = target.name
        h.dirty = false
        h.history.markSaved()
        state(h)
        target
    }

    override suspend fun undo(sessionId: String): PdfEditorSession = withContext(io) {
        val h = requireHolder(sessionId)
        if (h.history.undo(h.working)) {
            h.dirty = true
            invalidatePreviews(h)
        }
        state(h)
    }

    override suspend fun redo(sessionId: String): PdfEditorSession = withContext(io) {
        val h = requireHolder(sessionId)
        if (h.history.redo(h.working)) {
            h.dirty = true
            invalidatePreviews(h)
        }
        state(h)
    }

    override suspend fun reorder(sessionId: String, order: List<Int>) = mutate(sessionId, EditPermission.ASSEMBLE) { h, out -> engine.reorder(h.working, order, out, h.password) }
    override suspend fun deletePages(sessionId: String, pages: List<Int>) = mutate(sessionId, EditPermission.ASSEMBLE) { h, out -> engine.deletePages(h.working, pages, out, h.password) }
    override suspend fun duplicatePages(sessionId: String, pages: List<Int>) = mutate(sessionId, EditPermission.ASSEMBLE) { h, out -> engine.duplicatePages(h.working, pages, out, h.password) }

    override suspend fun insertPages(sessionId: String, insertAt: Int, source: Uri, sourcePages: List<Int>, sourcePassword: String?): PdfEditorSession = withContext(io) {
        val staged = stageUri(source)
        try {
            val selection = if (sourcePages.isEmpty()) (0 until engine.pageCount(staged, sourcePassword)).toList() else sourcePages
            mutateOnIo(sessionId, EditPermission.ASSEMBLE) { h, out -> engine.insertPages(h.working, insertAt, staged, selection, out, h.password, sourcePassword) }
        }
        finally { staged.delete() }
    }

    override suspend fun insertBlankPage(sessionId: String, insertAt: Int, widthPoints: Float, heightPoints: Float) =
        mutate(sessionId, EditPermission.ASSEMBLE) { h, out -> engine.insertBlankPage(h.working, insertAt, widthPoints, heightPoints, out, h.password) }

    override suspend fun rotatePages(sessionId: String, pages: List<Int>, degrees: Int) = mutate(sessionId, EditPermission.ASSEMBLE) { h, out -> engine.rotatePages(h.working, pages, degrees, out, h.password) }
    override suspend fun cropPages(sessionId: String, pages: List<Int>, rect: PdfRect) = mutate(sessionId) { h, out -> engine.cropPages(h.working, pages, rect, out, h.password) }
    override suspend fun watermark(sessionId: String, text: String) = mutate(sessionId) { h, out -> engine.watermark(h.working, text, out, h.password) }
    override suspend fun addPageNumbers(sessionId: String, startNumber: Int) = mutate(sessionId) { h, out -> engine.addPageNumbers(h.working, startNumber, out, h.password) }
    override suspend fun addText(sessionId: String, page: Int, rect: PdfRect, text: String, fontSize: Float) = mutate(sessionId) { h, out -> engine.addText(h.working, page, rect, text, fontSize, out, h.password) }
    override suspend fun highlight(sessionId: String, page: Int, rect: PdfRect) = mutate(sessionId, EditPermission.ANNOTATE) { h, out -> engine.highlight(h.working, page, rect, out, h.password) }
    override suspend fun underline(sessionId: String, page: Int, rect: PdfRect) = mutate(sessionId, EditPermission.ANNOTATE) { h, out -> engine.underline(h.working, page, rect, out, h.password) }
    override suspend fun strikeout(sessionId: String, page: Int, rect: PdfRect) = mutate(sessionId, EditPermission.ANNOTATE) { h, out -> engine.strikeout(h.working, page, rect, out, h.password) }
    override suspend fun drawInk(sessionId: String, page: Int, points: List<PdfPoint>) = mutate(sessionId) { h, out -> engine.drawInk(h.working, page, points, out, h.password) }
    override suspend fun drawRectangle(sessionId: String, page: Int, rect: PdfRect) = mutate(sessionId) { h, out -> engine.drawRectangle(h.working, page, rect, out, h.password) }
    override suspend fun addNote(sessionId: String, page: Int, rect: PdfRect, text: String) = mutate(sessionId, EditPermission.ANNOTATE) { h, out -> engine.addNote(h.working, page, rect, text, out, h.password) }

    override suspend fun addLink(sessionId: String, page: Int, rect: PdfRect, url: String) = mutate(sessionId, EditPermission.ANNOTATE) { h, out -> engine.addLink(h.working, page, rect, url, out, h.password) }

    override suspend fun addImage(sessionId: String, page: Int, rect: PdfRect, image: Uri): PdfEditorSession = withContext(io) {
        val staged = store.stageContent(image, ".img")
        try {
            val bitmap = requireNotNull(BitmapFactory.decodeFile(staged.absolutePath)) { "Unable to decode image" }
            try { mutateOnIo(sessionId) { h, out -> engine.addImage(h.working, page, rect, bitmap, out, h.password) } }
            finally { bitmap.recycle() }
        } finally { staged.delete() }
    }

    override suspend fun redact(sessionId: String, page: Int, rect: PdfRect) = mutate(sessionId) { h, out -> engine.redact(h.working, page, rect, out, h.password) }
    override suspend fun annotations(sessionId: String): List<PdfAnnotationInfo> = withContext(io) { requireHolder(sessionId).let { engine.annotations(it.working, it.password) } }
    override suspend fun removeAnnotation(sessionId: String, annotationId: String) = mutate(sessionId, EditPermission.ANNOTATE) { h, out -> engine.removeAnnotation(h.working, annotationId, out, h.password) }

    override suspend fun extractText(sessionId: String): String = withContext(io) { requireHolder(sessionId).let { engine.extractText(it.working, it.password) } }
    override suspend fun search(sessionId: String, query: String): List<PdfSearchHit> = withContext(io) { requireHolder(sessionId).let { engine.search(it.working, query, it.password) } }
    override suspend fun forms(sessionId: String): List<PdfFormField> = withContext(io) { requireHolder(sessionId).let { engine.formFields(it.working, it.password) } }
    override suspend fun setFormField(sessionId: String, fieldName: String, value: String) = mutate(sessionId, EditPermission.FORM) { h, out -> engine.setFormField(h.working, fieldName, value, out, h.password) }
    override suspend fun flattenForms(sessionId: String) = mutate(sessionId) { h, out -> engine.flattenForms(h.working, out, h.password) }
    override suspend fun metadata(sessionId: String): PdfDocumentMetadata = withContext(io) { requireHolder(sessionId).let { engine.metadata(it.working, it.password) } }
    override suspend fun updateMetadata(sessionId: String, metadata: PdfDocumentMetadata) = mutate(sessionId) { h, out -> engine.updateMetadata(h.working, metadata, out, h.password) }
    override suspend fun bookmarks(sessionId: String): List<PdfBookmark> = withContext(io) { requireHolder(sessionId).let { engine.bookmarks(it.working, it.password) } }
    override suspend fun addBookmark(sessionId: String, title: String, page: Int) = mutate(sessionId) { h, out -> engine.addBookmark(h.working, title, page, out, h.password) }
    override suspend fun removeBookmark(sessionId: String, bookmarkId: String) = mutate(sessionId) { h, out -> engine.removeBookmark(h.working, bookmarkId, out, h.password) }
    override suspend fun security(sessionId: String): PdfSecurityState = withContext(io) { requireHolder(sessionId).let { engine.security(it.working, it.password) } }

    override suspend fun protect(sessionId: String, options: PdfSecurityOptions): PdfEditorSession = withContext(io) {
        val h = requireHolder(sessionId)
        PdfEditorPolicies.validateSecurity(options)
        require(engine.security(h.working, h.password).isOwner) { "Owner permission is required to change PDF security" }
        mutateSecurity(h, options.ownerPassword) { out -> engine.protect(h.working, options, out, h.password) }
    }

    override suspend fun removeProtection(sessionId: String, password: String): PdfEditorSession = withContext(io) {
        val h = requireHolder(sessionId)
        mutateSecurity(h, null) { out -> engine.removeProtection(h.working, out, password) }
    }

    override suspend fun extractToManaged(sessionId: String, pages: List<Int>): File = withContext(io) {
        val h = requireHolder(sessionId)
        val out = store.uniqueFile(store.documentsDir, "${h.sourceName.substringBeforeLast('.')}_extracted.pdf")
        engine.extractPages(h.working, pages, out, h.password)
        registerOutput(out, "Extracted PDF pages")
        out
    }

    override suspend fun splitToManaged(sessionId: String): List<File> = withContext(io) {
        val h = requireHolder(sessionId)
        val dir = File(store.documentsDir, "${h.sourceName.substringBeforeLast('.')}_split_${System.currentTimeMillis()}").apply { mkdirs() }
        val outputs = engine.split(h.working, dir, h.password)
        for (file in outputs) registerOutput(file, "Split PDF page")
        outputs
    }

    override suspend fun mergeToManaged(uris: List<Uri>): File = withContext(io) {
        require(uris.size >= 2)
        val staged = uris.map(::stageUri)
        try {
            val out = store.uniqueFile(store.documentsDir, "merged_${System.currentTimeMillis()}.pdf")
            engine.merge(staged, out)
            registerOutput(out, "Merged ${uris.size} PDFs")
            out
        } finally { staged.forEach(File::delete) }
    }

    private suspend fun mutate(
        sessionId: String,
        permission: EditPermission = EditPermission.MODIFY,
        block: (Holder, File) -> Unit,
    ): PdfEditorSession = withContext(io) { mutateOnIo(sessionId, permission, block) }

    private fun mutateOnIo(
        sessionId: String,
        permission: EditPermission = EditPermission.MODIFY,
        block: (Holder, File) -> Unit,
    ): PdfEditorSession {
        val h = requireHolder(sessionId)
        val security = engine.security(h.working, h.password)
        val permitted = when (permission) {
            EditPermission.MODIFY -> security.canModify
            EditPermission.ASSEMBLE -> security.canModify || security.canAssemble
            EditPermission.ANNOTATE -> security.canModify || security.canAnnotate
            EditPermission.FORM -> security.canModify || security.canAnnotate || security.canFillForms
        }
        require(permitted) { "This PDF does not permit that edit with the supplied password" }
        h.history.checkpoint(h.working)
        val out = File(h.dir, "mutation_${System.nanoTime()}.pdf")
        try {
            block(h, out)
            require(out.isFile && out.length() > 0) { "PDF operation produced no output" }
            out.copyTo(h.working, overwrite = true)
            h.dirty = true
            invalidatePreviews(h)
            return state(h)
        } catch (t: Throwable) {
            h.history.rollbackCheckpoint()
            throw t
        } finally { out.delete() }
    }


    private fun mutateSecurity(h: Holder, passwordAfter: String?, block: (File) -> Unit): PdfEditorSession {
        h.history.checkpoint(h.working)
        val out = File(h.dir, "security_${System.nanoTime()}.pdf")
        val oldPassword = h.password
        try {
            block(out)
            require(out.isFile && out.length() > 0) { "PDF security operation produced no output" }
            out.copyTo(h.working, overwrite = true)
            h.password = passwordAfter
            h.dirty = true
            invalidatePreviews(h)
            return state(h)
        } catch (t: Throwable) {
            h.password = oldPassword
            h.history.rollbackCheckpoint()
            throw t
        } finally { out.delete() }
    }

    private fun state(h: Holder): PdfEditorSession {
        val count = engine.pageCount(h.working, h.password)
        val encrypted = runCatching { engine.security(h.working, h.password).encrypted }.getOrDefault(false)
        return PdfEditorSession(h.id, h.sourceName, count, dirty = h.dirty, canUndo = h.history.canUndo, canRedo = h.history.canRedo, encrypted = encrypted)
    }

    private fun requireHolder(id: String): Holder = requireNotNull(sessions[id]) { "PDF edit session expired" }

    private fun invalidatePreviews(h: Holder) { File(h.dir, "previews").deleteRecursively() }

    private fun stageUri(uri: Uri): File {
        val file = managedFile(uri)
        return if (file != null) File(store.temporaryFile(file.name).absolutePath).also { file.copyTo(it, overwrite = true) }
        else store.stageContent(uri, ".pdf")
    }

    private fun managedFile(uri: Uri): File? {
        if (uri.scheme != null && uri.scheme != "file") return null
        val path = uri.path ?: return null
        val file = File(path).canonicalFile
        val root = store.root.canonicalFile
        if (!(file.path == root.path || file.path.startsWith(root.path + File.separator))) return null
        return file.takeIf { it.isFile }
    }

    private suspend fun registerOutput(file: File, description: String) {
        val entity = appRepository.registerFile(file, ResourceType.PDF, "application/pdf")
        appRepository.recordHistory(HistoryAction.EDITED, description, ResourceType.PDF, entity.id, entity.uri, entity.displayName)
    }

    private fun ensurePdfName(name: String): String = if (name.endsWith(".pdf", true)) name else "$name.pdf"
}
