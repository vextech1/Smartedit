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
    /**
     * [presetFilter] (e.g. CLEAN for receipts) overrides [autoFilter]. [splitSpreads] cuts every source in two at the book gutter,
     * giving two pages per photo; crops are ignored then.
     */
    suspend fun addPages(sessionId:String, sources:List<Uri>, crops:List<CropQuad?> = emptyList(), autoFilter:Boolean = true, presetFilter:ScanFilter? = null, splitSpreads:Boolean = false)
    /** Puts the card photos (front, optional back) on one A4 page at real card size. The caller deletes the source files. */
    suspend fun addIdCard(sessionId:String, front:File, frontCrop:CropQuad?, back:File?, backCrop:CropQuad?)
    /** Writes the page as a transparent PNG (plain background removed, margins trimmed) and returns the file. */
    suspend fun extractCutout(pageId:String, mode:CutoutMode): File
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
    override suspend fun addPages(sessionId:String, sources:List<Uri>, crops:List<CropQuad?>, autoFilter:Boolean, presetFilter:ScanFilter?, splitSpreads:Boolean) = withContext(io) { addLock.withLock {
        require(sources.isNotEmpty())
        val session=requireNotNull(db.scanSessionDao().get(sessionId))
        val existing=db.scanPageDao().forSession(sessionId); val now=System.currentTimeMillis()
        val created=mutableListOf<File>()
        val pages=mutableListOf<ScanPageEntity>(); val pageCrops=mutableListOf<CropQuad?>()
        try {
            var position=existing.size
            sources.forEachIndexed { index,uri ->
                val original=store.copyIntoManaged(uri,"scan_${sessionId.take(8)}_${position+1}.jpg",store.scansDir); created += original
                images.bakeExifOrientation(original)
                val files=if(splitSpreads) splitSpread(original,created) else listOf(original)
                files.forEach { file ->
                    val rendered=store.copyFile(file,store.scansDir,"${file.nameWithoutExtension}_rendered.jpg"); created += rendered
                    pages += ScanPageEntity(UUID.randomUUID().toString(),sessionId,position++,file.toURI().toString(),file.toURI().toString(),rendered.toURI().toString(),createdAt=now,updatedAt=now)
                    pageCrops += if(splitSpreads) null else crops.getOrNull(index)
                }
            }
            db.withTransaction { db.scanPageDao().upsertAll(pages); db.scanSessionDao().upsert(session.copy(updatedAt=now)) }
        } catch(t:Throwable) { created.forEach(File::delete); throw t }
        // Auto-crop and the filter are bonuses: if rendering fails the page simply stays as imported.
        pages.forEachIndexed { index,page ->
            val crop=pageCrops[index]
            val filter=presetFilter ?: if(autoFilter) runCatching { suggestFilter(requireFile(page.originalUri),crop) }.getOrDefault(ScanFilter.ORIGINAL) else ScanFilter.ORIGINAL
            if(crop==null && filter==ScanFilter.ORIGINAL) return@forEachIndexed
            runCatching { db.scanPageDao().upsert(render(page,crop ?: CropQuad.Full,filter,0)) }
        }
    } }
    /** Cuts a photographed spread at its gutter into left and right files; the combined file is removed. */
    private fun splitSpread(original:File,created:MutableList<File>):List<File> {
        val small=images.decodeOriented(original,240); val cut=try { SpreadSplitter.gutter(lumaOf(small)) } finally { small.recycle() }
        val full=images.decode(original)
        try {
            val x=(full.width*cut).toInt().coerceIn(1,full.width-1)
            val left=Bitmap.createBitmap(full,0,0,x,full.height); val right=Bitmap.createBitmap(full,x,0,full.width-x,full.height)
            try {
                val l=store.writeBitmap(left,store.scansDir,"${original.nameWithoutExtension}_L.jpg",Bitmap.CompressFormat.JPEG,95); created += l
                val r=store.writeBitmap(right,store.scansDir,"${original.nameWithoutExtension}_R.jpg",Bitmap.CompressFormat.JPEG,95); created += r
                created.remove(original); original.delete()
                return listOf(l,r)
            } finally { left.recycle(); right.recycle() }
        } finally { full.recycle() }
    }
    override suspend fun addIdCard(sessionId:String,front:File,frontCrop:CropQuad?,back:File?,backCrop:CropQuad?) = withContext(io) { addLock.withLock {
        val session=requireNotNull(db.scanSessionDao().get(sessionId))
        val existing=db.scanPageDao().forSession(sessionId); val now=System.currentTimeMillis()
        val created=mutableListOf<File>()
        try {
            val sides=listOfNotNull(front to frontCrop, back?.let { it to backCrop }).map { (file,crop) -> cardBitmap(file,crop) }
            val slots=IdCardLayout.slots(sides.size)
            val sheet=Bitmap.createBitmap(IdCardLayout.PAGE_W,IdCardLayout.PAGE_H,Bitmap.Config.ARGB_8888)
            try {
                val canvas=Canvas(sheet); canvas.drawColor(Color.WHITE)
                val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val border=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=3f; color=0xFFBDBDBD.toInt() }
                sides.forEachIndexed { i,bitmap ->
                    val r=IdCardLayout.fit(slots[i],bitmap.width,bitmap.height); val dst=Rect(r.x,r.y,r.x+r.w,r.y+r.h)
                    canvas.drawBitmap(bitmap,null,dst,paint); canvas.drawRect(dst,border); bitmap.recycle()
                }
                val original=store.writeBitmap(sheet,store.scansDir,"id_${sessionId.take(8)}_${existing.size+1}.jpg",Bitmap.CompressFormat.JPEG,95); created += original
                val rendered=store.copyFile(original,store.scansDir,"${original.nameWithoutExtension}_rendered.jpg"); created += rendered
                val page=ScanPageEntity(UUID.randomUUID().toString(),sessionId,existing.size,original.toURI().toString(),original.toURI().toString(),rendered.toURI().toString(),createdAt=now,updatedAt=now)
                db.withTransaction { db.scanPageDao().upsert(page); db.scanSessionDao().upsert(session.copy(updatedAt=now)) }
            } finally { sheet.recycle() }
        } catch(t:Throwable) { created.forEach(File::delete); throw t }
    } }
    /** One card side, upright, perspective-corrected and turned to landscape. */
    private fun cardBitmap(file:File,crop:CropQuad?):Bitmap {
        var bitmap=images.decodeOriented(file,1800)
        if(crop!=null) { val cropped=PerspectiveCropper.crop(bitmap,crop); if(cropped!==bitmap) bitmap.recycle(); bitmap=cropped }
        if(IdCardLayout.needsTurn(bitmap.width,bitmap.height)) { val turned=images.rotate(bitmap,90f); bitmap.recycle(); bitmap=turned }
        return bitmap
    }
    override suspend fun extractCutout(pageId:String,mode:CutoutMode):File=withContext(io) {
        val page=requireNotNull(db.scanPageDao().get(pageId))
        var bitmap=images.decodeOriented(requireFile(page.renderedUri),2000)   // capped: the background flood fill needs several int arrays
        try {
            if(mode==CutoutMode.SIGNATURE) { val clean=images.flattenBackground(bitmap); bitmap.recycle(); bitmap=clean }
            val cut=images.removePlainBackground(bitmap,if(mode==CutoutMode.SIGNATURE) 60 else 40)
            val trimmed=images.trimTransparent(cut); if(trimmed!==cut) cut.recycle()
            try {
                val name=(if(mode==CutoutMode.SIGNATURE) "signature" else "cutout")+"_${System.currentTimeMillis()}.png"
                val out=store.writeBitmap(trimmed,store.scansDir,name,Bitmap.CompressFormat.PNG,100)
                appRepo.registerFile(out,ResourceType.IMAGE,"image/png")
                appRepo.recordHistory(HistoryAction.SCANNED,context.getString(if(mode==CutoutMode.SIGNATURE) R.string.scanner_history_signature else R.string.scanner_history_cutout),ResourceType.IMAGE,pageId,out.toURI().toString(),out.name)
                out
            } finally { trimmed.recycle() }
        } finally { bitmap.recycle() }
    }
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
        val filtered=when(filter){ ScanFilter.ORIGINAL->bitmap; ScanFilter.GRAYSCALE->images.grayscale(bitmap); ScanFilter.BLACK_WHITE->images.blackAndWhite(bitmap); ScanFilter.ENHANCE->images.adjust(bitmap,8f,1.12f,.85f); ScanFilter.CLEAN->images.flattenBackground(bitmap) }
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
            appRepo.recordHistory(HistoryAction.SCANNED,context.resources.getQuantityString(R.plurals.scanner_history_scanned,pages.size,pages.size),ResourceType.SCAN,sessionId,out.toURI().toString(),out.name)
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
