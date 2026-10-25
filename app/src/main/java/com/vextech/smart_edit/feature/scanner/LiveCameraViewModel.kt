package com.vextech.smart_edit.feature.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.feature.scanner.data.ScannerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LiveCameraViewModel @Inject constructor(private val store:DocumentStore,private val scanner:ScannerRepository):ViewModel(){
    private val _message=MutableStateFlow<String?>(null); val message=_message.asStateFlow()
    fun createOutputFile():File=store.uniqueFile(store.scansDir,"camera_${System.currentTimeMillis()}.jpg")
    fun registerCapture(sessionId:String,file:File)=viewModelScope.launch { runCatching { scanner.addPages(sessionId,listOf(Uri.fromFile(file))); file.delete() }.onSuccess{_message.value="Capture added"}.onFailure{_message.value="Unable to add capture"} }
    fun error(message:String){_message.value=message}
}
