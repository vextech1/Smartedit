package com.vextech.smart_edit.feature.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.vextech.smart_edit.feature.scanner.data.ScannerRepository
import com.vextech.smart_edit.feature.scanner.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ScannerMessage { SESSION_FAILED, IMPORT_FAILED, EDIT_FAILED, OCR_FAILED, EXPORT_FAILED, PAGE_DELETED, EXPORTED }

data class ScannerUiState(
    val session: ScanSessionDraft? = null,
    val recentSessions: List<com.vextech.smart_edit.core.database.entity.ScanSessionEntity> = emptyList(),
    val covers: Map<String, com.vextech.smart_edit.core.database.dao.ScanCover> = emptyMap(),
    val busy: Boolean = false,
    val message: ScannerMessage? = null,
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScannerViewModel @Inject constructor(private val repository: ScannerRepository) : ViewModel() {
    private val activeId=MutableStateFlow<String?>(null)
    private val busy=MutableStateFlow(false)
    private val message=MutableStateFlow<ScannerMessage?>(null)
    private val activeSession=activeId.flatMapLatest { id -> if(id==null) flowOf(null) else repository.observeSession(id) }
    val state: StateFlow<ScannerUiState> = combine(activeSession,repository.observeSessions(),repository.observeCovers(),busy,message) { session,recent,covers,isBusy,msg -> ScannerUiState(session,recent,covers.associateBy { it.sessionId },isBusy,msg) }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),ScannerUiState())

    fun clearMessage(){ message.value=null }
    fun openSession(id:String){ activeId.value=id }
    fun closeSession(){ activeId.value=null }
    fun rename(title:String)=activeId.value?.let { id -> task(ScannerMessage.EDIT_FAILED) { repository.rename(id,title) } }
    fun newSession(onReady:(String)->Unit = {}) = task(ScannerMessage.SESSION_FAILED) { repository.createSession().also { activeId.value=it; onReady(it) } }
    fun importUris(uris:List<Uri>) { ensureSession { id -> task(ScannerMessage.IMPORT_FAILED) { repository.addPages(id,uris) } } }
    fun importScanResult(result:GmsDocumentScanningResult) { importUris(result.pages.orEmpty().map { it.imageUri }) }
    fun deletePage(id:String)=activeId.value?.let { sessionId -> task(ScannerMessage.EDIT_FAILED) { repository.deletePage(sessionId,id); message.value=ScannerMessage.PAGE_DELETED } }
    fun movePage(from:Int,to:Int)=activeId.value?.let { task(ScannerMessage.EDIT_FAILED) { repository.reorder(it,from,to) } }
    fun editPage(id:String,crop:CropQuad,filter:ScanFilter,rotation:Int)=task(ScannerMessage.EDIT_FAILED) { repository.applyEdit(id,crop,filter,rotation) }
    fun ocr(id:String)=task(ScannerMessage.OCR_FAILED) { repository.runOcr(id) }
    fun export()=activeId.value?.let { task(ScannerMessage.EXPORT_FAILED) { repository.exportPdf(it); message.value=ScannerMessage.EXPORTED } }
    fun discard()=activeId.value?.let { id -> task(ScannerMessage.EDIT_FAILED) { repository.discard(id); activeId.value=null } }
    fun error(){ message.value=ScannerMessage.SESSION_FAILED }
    private fun ensureSession(block:(String)->Unit){ activeId.value?.let(block) ?: newSession(block) }
    private fun task(failure:ScannerMessage, block:suspend()->Unit)=viewModelScope.launch { busy.value=true; message.value=null; runCatching { block() }.onFailure { message.value=failure }; busy.value=false }
}
