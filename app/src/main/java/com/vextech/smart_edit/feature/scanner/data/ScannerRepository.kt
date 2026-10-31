package com.vextech.smart_edit.feature.scanner.data

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.room.withTransaction
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.database.entity.ScanPageEntity
import com.vextech.smart_edit.core.database.entity.ScanSessionEntity
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.library.OcrPolicies
import com.vextech.smart_edit.core.library.OcrScript
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.model.ResourceType
import com.vextech.smart_edit.core.ocr.OcrService
import com.vextech.smart_edit.core.pdf.PdfEngine
import com.vextech.smart_edit.core.processing.ImageProcessor
import com.vextech.smart_edit.core.repository.SmartEditRepository
import com.vextech.smart_edit.feature.scanner.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ScannerRepository {
    fun observeSessions(): Flow<List<ScanSessionEntity>>
    fun observeSession(id:String): Flow<ScanSessionDraft?>
    fun observeCovers(): Flow<List<com.vextech.smart_edit.core.database.dao.ScanCover>>
    suspend fun rename(sessionId:String, title:String)
    /** Blank [title] auto-names the scan with a timestamp. */
    suspend fun createSession(title:String = ""): String
    /** [crops] (optional, same order as [sources]) pre-crops a page, e.g. from live edge detection; null entries keep the full image. */
    /** [autoFilter] picks Original / Grayscale / B&W per page from its content; turn it off for sources that are already processed (ML Kit scanner). */
    suspend fun addPages(sessionId:String, sources:List<Uri>, crops:List<CropQuad?> = emptyList(), autoFilter:Boolean = true)
    suspend fun applyFilterToPages(sessionId:String, pageIds:Set<String>, filter:ScanFilter)
    /** Clockwise turn (0/90/180/270) that makes the page text upright, 0 when unsure. */
    suspend fun detectOrientation(pageId:String): Int
    /** Flags a fresh capture that looks blurry or too dark. [crop] limits the check to the page area. */
    suspend fun assessQuality(file:File, crop:CropQuad?): PageQuality
    suspend fun deletePage(sessionId:String, pageId:String)
    suspend fun reorder(sessionId:String, from:Int, to:Int)
    suspend fun applyEdit(pageId:String, crop:CropQuad, filter:ScanFilter, rotationDegrees:Int)
    /** Applies [filter] and [rotationDegrees] to every page; [pageId] uses [crop], the others keep their own crop. */
    suspend fun applyEditToAll(sessionId:String, pageId:String, crop:CropQuad, filter:ScanFilter, rotationDegrees:Int)
    /** [script] null = auto: Latin first, then the script matching the detected language, then the others if nothing was found. */
    suspend fun runOcr(pageId:String, script:OcrScript? = null)
    /** Deletes draft sessions with no pages, except [exceptId] and anything touched in the last [graceMs]. Returns the count removed. */
    suspend fun purgeEmptySessions(exceptId:String? = null, graceMs:Long = 0L): Int
    suspend fun exportPdf(sessionId:String): File
    suspend fun discard(sessionId:String)
}

@Singleton
class AndroidScannerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SmartEditDatabase,
    private val store: DocumentStore,
    private val images: ImageProcessor,
    private val pdf: PdfEngine,
    private val ocr: OcrService,
    private val appRepo: SmartEditRepository,
) : ScannerRepository {
    private val io: CoroutineDispatcher = Dispatchers.IO
    override fun observeSessions() = db.scanSessionDao().observeAll()
    override fun observeCovers() = db.scanPageDao().observeCovers()
    override suspend fun rename(sessionId:String, title:String) = withContext(io) {
        val session=requireNotNull(db.scanSessionDao().get(sessionId))
        db.scanSessionDao().upsert(session.copy(title=title.trim().take(60).ifBlank { defaultTitle() },updatedAt=System.currentTimeMillis()))
    }
    override fun observeSession(id:String): Flow<ScanSessionDraft?> = combine(db.scanSessionDao().observe(id), db.scanPageDao().observeForSession(id)) { session,pages ->
        session?.let { s -> ScanSessionDraft(s.id,s.title,ScanStatus.valueOf(s.status),pages.map { it.toDraft() },s.exportedPdfUri) }
    }
    private fun defaultTitle(now:Long = System.currentTimeMillis()) = context.getString(R.string.scanner_default_title, ScannerPolicies.timestampLabel(now))
    override suspend fun createSession(title:String): String = withContext(io) {
        val id=UUID.randomUUID().toString(); val now=System.currentTimeMillis()
        db.scanSessionDao().upsert(ScanSessionEntity(id,title.trim().take(60).ifBlank { defaultTitle(now) },ScanStatus.DRAFT.name,now,now)); id
    }
    override suspend fun purgeEmptySessions(exceptId:String?, graceMs:Long): Int = withContext(io) {
        val ids=db.scanSessionDao().emptyDraftIds(System.currentTimeMillis()-graceMs).filter { it!=exceptId }
        ids.forEach { db.scanSessionDao().delete(it) }; ids.size
    }
    private val addLock = Mutex()   // rapid captures must not compute the same page positions
    override suspend fun addPages(sessionId:String, sources:List<Uri>, crops:List<CropQuad?>, autoFilter:Boolean) = withContext(io) { addLock.withLock {
        require(sources.isNotEmpty())
        val session=requireNotNull(db.scanSessionDao().get(sessionId))
        val existing=db.scanPageDao().forSession(sessionId); val now=System.currentTimeMillis()
        val created=mutableListOf<File>()
        val pages=try {
            val built=sources.mapIndexed { index,uri ->
                val original=store.copyIntoManaged(uri,"scan_${sessionId.take(8)}_${existing.size+index+1}.jpg",store.scansDir); created += original
                images.bakeExifOrientation(original)
                val rendered=store.copyFile(original,store.scansDir,"scan_${sessionId.take(8)}_${existing.size+index+1}_rendered.jpg"); created += rendered
                ScanPageEntity(UUID.randomUUID().toString(),sessionId,existing.size+index,original.toURI().toString(),original.toURI().toString(),rendered.toURI().toString(),createdAt=now,updatedAt=now)
            }
            db.withTransaction { db.scanPageDao().upsertAll(built); db.scanSessionDao().upsert(session.copy(updatedAt=now)) }
            built
        } catch(t:Throwable) { created.forEach(File::delete); throw t }
        // Auto-crop and the suggested filter are bonuses: if either fails the page simply stays as imported.
        pages.forEachIndexed { index,page ->
            val crop=crops.getOrNull(index)
            val filter=if(autoFilter) runCatching { suggestFilter(requireFile(page.originalUri),crop) }.getOrDefault(ScanFilter.ORIGINAL) else ScanFilter.ORIGINAL
            if(crop==null && filter==ScanFilter.ORIGINAL) return@forEachIndexed
            runCatching { db.scanPageDao().upsert(render(page,crop ?: CropQuad.Full,filter,0)) }
        }
    } }
    override suspend fun deletePage(sessionId:String,pageId:String)=withContext(io) {
        val page=requireNotNull(db.scanPageDao().get(pageId)); require(page.sessionId==sessionId)
        db.scanPageDao().delete(pageId); cleanupPageFiles(page)
        renumber(sessionId)
    }
    override suspend fun reorder(sessionId:String,from:Int,to:Int)=withContext(io) {
        val pages=db.scanPageDao().forSession(sessionId); val ids=ScannerPolicies.reorderedIds(pages.map { it.id },from,to)
        val byId=pages.associateBy { it.id }; val now=System.currentTimeMillis()
        db.withTransaction {
            // (sessionId, position) is a unique index, so rows cannot trade places in one pass: park them on free negative slots first.
            db.scanPageDao().upsertAll(pages.mapIndexed { i,p -> p.copy(position=-(i+1)) })
            db.scanPageDao().upsertAll(ids.mapIndexed { i,id -> byId.getValue(id).copy(position=i,updatedAt=now) })
        }
        touch(sessionId)
    }
    override suspend fun applyFilterToPages(sessionId:String,pageIds:Set<String>,filter:ScanFilter)=withContext(io) {
        db.scanPageDao().forSession(sessionId).filter { it.id in pageIds }.forEach { p ->
            val quad=runCatching { CropQuad.decode(p.cropPoints) }.getOrDefault(CropQuad.Full)
            db.scanPageDao().upsert(render(p,quad,filter,p.rotationDegrees))
        }
        touch(sessionId)
    }
    override suspend fun detectOrientation(pageId:String):Int=withContext(io) {
        val page=requireNotNull(db.scanPageDao().get(pageId))
        val bitmap=images.decodeOriented(requireFile(page.originalUri),1000)
        try { ocr.detectOrientation(bitmap) } finally { bitmap.recycle() }
    }
    override suspend fun assessQuality(file:File,crop:CropQuad?):PageQuality=withContext(io) {
        var bitmap=images.decodeOriented(file,480)
        if(crop!=null) { val cropped=PerspectiveCropper.crop(bitmap,crop); if(cropped!==bitmap) bitmap.recycle(); bitmap=cropped }
        try { QualityCheck.assess(lumaOf(bitmap)) } finally { bitmap.recycle() }
    }
    private fun lumaOf(bitmap:Bitmap):LumaFrame {
        val px=IntArray(bitmap.width*bitmap.height); bitmap.getPixels(px,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        return LumaFrame(bitmap.width,bitmap.height,IntArray(px.size) { i -> val p=px[i]; (299*((p shr 16) and 0xFF)+587*((p shr 8) and 0xFF)+114*(p and 0xFF))/1000 })
    }
    /** Starting filter for a new page, judged on a small copy of the (cropped) page. */
    private fun suggestFilter(original:File,crop:CropQuad?):ScanFilter {
        var bitmap=images.decodeOriented(original,200)
        if(crop!=null) { val cropped=PerspectiveCropper.crop(bitmap,crop); if(cropped!==bitmap) bitmap.recycle(); bitmap=cropped }
        return try { val px=IntArray(bitmap.width*bitmap.height); bitmap.getPixels(px,0,bitmap.width,0,0,bitmap.width,bitmap.height); FilterAdvisor.suggest(px) } finally { bitmap.recycle() }
    }
    override suspend fun applyEdit(pageId:String,crop:CropQuad,filter:ScanFilter,rotationDegrees:Int)=withContext(io) {
        val page=requireNotNull(db.scanPageDao().get(pageId))
        db.scanPageDao().upsert(render(page,crop,filter,rotationDegrees)); touch(page.sessionId)
    }
    override suspend fun applyEditToAll(sessionId:String,pageId:String,crop:CropQuad,filter:ScanFilter,rotationDegrees:Int)=withContext(io) {
        db.scanPageDao().forSession(sessionId).forEach { p ->
            val quad=if(p.id==pageId) crop else runCatching { CropQuad.decode(p.cropPoints) }.getOrDefault(CropQuad.Full)
            db.scanPageDao().upsert(render(p,quad,filter,rotationDegrees))   // saved per page so a failure keeps earlier pages consistent
        }
        touch(sessionId)
    }
    /** Re-renders [page] from its original (crop, mirror, rotate, filter) and returns the updated row; the caller persists it. */
    private fun render(page:ScanPageEntity,crop:CropQuad,filter:ScanFilter,rotationDegrees:Int):ScanPageEntity {
        val original=requireFile(page.originalUri)
        var bitmap=images.decode(original)
        val warped=PerspectiveCropper.crop(bitmap,crop); if(warped!==bitmap) bitmap.recycle(); bitmap=warped
        val rot=PageOrientation.rotation(rotationDegrees); val flipped=PageOrientation.flipped(rotationDegrees)
        if(flipped) { val next=images.flipHorizontal(bitmap); bitmap.recycle(); bitmap=next }
        if(rot!=0) { val next=images.rotate(bitmap,rot.toFloat()); bitmap.recycle(); bitmap=next }
        val filtered=when(filter){ ScanFilter.ORIGINAL->bitmap; ScanFilter.GRAYSCALE->images.grayscale(bitmap); ScanFilter.BLACK_WHITE->images.blackAndWhite(bitmap); ScanFilter.ENHANCE->images.adjust(bitmap,8f,1.12f,.85f) }
        if(filtered!==bitmap) bitmap.recycle()
        val out=store.writeBitmap(filtered,store.scansDir,"${page.id}_rendered.jpg",Bitmap.CompressFormat.JPEG,94); filtered.recycle()
        store.asFile(page.renderedUri)?.takeIf { it.absolutePath!=original.absolutePath }?.delete()
        return page.copy(croppedUri=out.toURI().toString(),renderedUri=out.toURI().toString(),cropPoints=crop.encode(),rotationDegrees=PageOrientation.encode(rot,flipped),filter=filter.name,updatedAt=System.currentTimeMillis())
    }
    override suspend fun runOcr(pageId:String,script:OcrScript?)=withContext(io) {
        val page=requireNotNull(db.scanPageDao().get(pageId)); val uri=Uri.parse(page.renderedUri)
        val result=if(script!=null) ocr.recognize(uri,script) else {
            val first=ocr.recognize(uri,OcrScript.LATIN)
            val retry=OcrPolicies.suggestedRetry(OcrScript.LATIN,first.language)
            when {
                retry!=null -> ocr.recognize(uri,retry).takeIf { it.text.isNotBlank() } ?: first
                first.text.isBlank() -> listOf(OcrScript.DEVANAGARI,OcrScript.CHINESE,OcrScript.JAPANESE,OcrScript.KOREAN).firstNotNullOfOrNull { s -> ocr.recognize(uri,s).takeIf { it.text.isNotBlank() } } ?: first
                else -> first
            }
        }
        db.scanPageDao().upsert(page.copy(ocrText=result.text,ocrLanguage=result.language,updatedAt=System.currentTimeMillis())); touch(page.sessionId)
    }
    override suspend fun exportPdf(sessionId:String): File = withContext(io) {
        val session=requireNotNull(db.scanSessionDao().get(sessionId)); val pages=db.scanPageDao().forSession(sessionId); require(ScannerPolicies.canExport(pages.size))
        val bitmaps=pages.map { images.decode(requireFile(it.renderedUri)) }
        try {
            val out=store.uniqueFile(store.scansDir,"${session.title.ifBlank{"Scan"}.replace(Regex("[\\\\/:*?\"<>|]"),"_")}_${System.currentTimeMillis()}.pdf")
            pdf.imagesToPdf(bitmaps,out)
            appRepo.registerFile(out,ResourceType.PDF,"application/pdf")
            appRepo.recordHistory(HistoryAction.SCANNED,"Scanned ${pages.size} page${if(pages.size==1)"" else "s"}",ResourceType.SCAN,sessionId,out.toURI().toString(),out.name)
            val now=System.currentTimeMillis(); db.scanSessionDao().upsert(session.copy(status=ScanStatus.EXPORTED.name,updatedAt=now,exportedPdfUri=out.toURI().toString()))
            db.scanDao().upsert(com.vextech.smart_edit.core.database.entity.ScanEntity(sessionId,pages.first().renderedUri,out.toURI().toString(),pages.mapNotNull{it.ocrText}.joinToString("\n\n").ifBlank{null},pages.size,session.createdAt))
            out
        } finally { bitmaps.forEach(Bitmap::recycle) }
    }
    override suspend fun discard(sessionId:String)=withContext(io) {
        db.scanPageDao().forSession(sessionId).forEach(::cleanupPageFiles); db.withTransaction { db.scanPageDao().deleteForSession(sessionId); db.scanSessionDao().delete(sessionId) }
    }
    private suspend fun renumber(sessionId:String) { val p=db.scanPageDao().forSession(sessionId); db.scanPageDao().upsertAll(p.mapIndexed{i,e->e.copy(position=i)}); touch(sessionId) }
    private suspend fun touch(id:String) { db.scanSessionDao().get(id)?.let { db.scanSessionDao().upsert(it.copy(updatedAt=System.currentTimeMillis())) } }
    private fun requireFile(uri:String)=requireNotNull(store.asFile(uri)) { "Managed scan file required" }.also { require(it.exists()) }
    private fun cleanupPageFiles(p:ScanPageEntity) { setOf(p.originalUri,p.croppedUri,p.renderedUri).mapNotNull(store::asFile).forEach(File::delete) }
    private fun ScanPageEntity.toDraft()=ScanPageDraft(id,position,renderedUri,ScanFilter.valueOf(filter),rotationDegrees,ocrText,ocrLanguage,originalUri,cropPoints,updatedAt)
}

object PerspectiveCropper {
    fun crop(source: Bitmap, quad: CropQuad): Bitmap {
        if (quad == CropQuad.Full) return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        val pts=listOf(quad.topLeft,quad.topRight,quad.bottomRight,quad.bottomLeft)
        fun dist(a:NormalizedPoint,b:NormalizedPoint)=kotlin.math.hypot(((a.x-b.x)*source.width).toDouble(),((a.y-b.y)*source.height).toDouble()).toFloat()
        val width=maxOf(dist(pts[0],pts[1]),dist(pts[3],pts[2])).toInt().coerceAtLeast(1)
        val height=maxOf(dist(pts[0],pts[3]),dist(pts[1],pts[2])).toInt().coerceAtLeast(1)
        val src=floatArrayOf(pts[0].x*source.width,pts[0].y*source.height,pts[1].x*source.width,pts[1].y*source.height,pts[2].x*source.width,pts[2].y*source.height,pts[3].x*source.width,pts[3].y*source.height)
        val dst=floatArrayOf(0f,0f,width.toFloat(),0f,width.toFloat(),height.toFloat(),0f,height.toFloat())
        val matrix=Matrix(); require(matrix.setPolyToPoly(src,0,dst,0,4)) { "Invalid crop corners" }
        return Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also { Canvas(it).drawBitmap(source,matrix,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
    }
}
