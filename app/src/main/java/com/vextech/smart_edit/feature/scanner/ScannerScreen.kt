package com.vextech.smart_edit.feature.scanner

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.documentscanner.*
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.feature.scanner.domain.*

@Composable
fun ScannerScreen(onBack:()->Unit,onLiveCamera:(String)->Unit,vm:ScannerViewModel=hiltViewModel()) {
    val state by vm.state.collectAsState(); val context=LocalContext.current; val activity=context as Activity
    var edit by remember { mutableStateOf<ScanPageDraft?>(null) }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if(it.isNotEmpty()) vm.importUris(it) }
    val scanLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result -> if(result.resultCode==Activity.RESULT_OK) GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.let(vm::importScanResult) }
    val scanner=remember { GmsDocumentScanning.getClient(GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(50).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()) }
    fun autoScan(){ scanner.getStartScanIntent(activity).addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }.addOnFailureListener { vm.error() } }
    val msg=state.message?.let { scannerMessage(it) }
    LaunchedEffect(msg){ if(msg!=null) { kotlinx.coroutines.delay(2500); vm.clearMessage() } }
    FeatureScaffold(stringResource(R.string.scanner_title),onBack,actions={
        IconButton(onClick={ vm.newSession(onLiveCamera) },enabled=!state.busy){ Icon(Icons.Rounded.PhotoCamera,stringResource(R.string.scanner_live_camera)) }
        IconButton(onClick={ gallery.launch(arrayOf("image/*")) },enabled=!state.busy){ Icon(Icons.Rounded.AddPhotoAlternate,stringResource(R.string.scanner_import)) }
        IconButton(onClick=::autoScan,enabled=!state.busy){ Icon(Icons.Rounded.DocumentScanner,stringResource(R.string.scanner_auto)) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            msg?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
            val session=state.session
            if(session==null) {
                Text(stringResource(R.string.scanner_intro),style=MaterialTheme.typography.bodyLarge)
                Button(onClick={vm.newSession()},enabled=!state.busy){ Text(stringResource(R.string.scanner_new_session)) }
                state.recentSessions.take(8).forEach { recent -> TextButton(onClick={vm.openSession(recent.id)}) { Text("${recent.title} • ${recent.status}") } }
            } else {
                Text(session.title,style=MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.scanner_pages_count,session.pages.size))
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(session.pages,key={_,p->p.id}) { index,page ->
                        ElevatedCard { Column(Modifier.width(180.dp).padding(10.dp)) {
                            Text(stringResource(R.string.scanner_page_number,index+1),style=MaterialTheme.typography.titleMedium)
                            Text(page.filter.name.replace('_',' '),style=MaterialTheme.typography.bodySmall)
                            page.ocrText?.takeIf(String::isNotBlank)?.let { Text(it.take(90),maxLines=3) }
                            Row { IconButton(onClick={ if(index>0) vm.movePage(index,index-1) },enabled=index>0){Icon(Icons.Rounded.ArrowBack,"Move left")}; IconButton(onClick={ if(index<session.pages.lastIndex) vm.movePage(index,index+1) },enabled=index<session.pages.lastIndex){Icon(Icons.Rounded.ArrowForward,"Move right")} }
                            Row { TextButton(onClick={edit=page}){Text(stringResource(R.string.scanner_edit))}; TextButton(onClick={vm.ocr(page.id)}){Text("OCR")}; IconButton(onClick={vm.deletePage(page.id)}){Icon(Icons.Rounded.Delete,stringResource(R.string.scanner_delete_page))} }
                        } }
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick=vm::export,enabled=session.pages.isNotEmpty()&&!state.busy){ Icon(Icons.Rounded.PictureAsPdf,null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.scanner_export_pdf)) }
                    OutlinedButton(onClick=vm::discard,enabled=!state.busy){ Text(stringResource(R.string.scanner_discard)) }
                }
                session.exportedPdfUri?.let { Text(stringResource(R.string.scanner_pdf_ready)) }
            }
        }
    }
    edit?.let { page -> ScanEditDialog(page,{edit=null}) { crop,filter,rotation -> vm.editPage(page.id,crop,filter,rotation); edit=null } }
}

@Composable private fun ScanEditDialog(page:ScanPageDraft,onDismiss:()->Unit,onApply:(CropQuad,ScanFilter,Int)->Unit){
    var left by remember{mutableFloatStateOf(0f)}; var top by remember{mutableFloatStateOf(0f)}; var right by remember{mutableFloatStateOf(1f)}; var bottom by remember{mutableFloatStateOf(1f)}
    var filter by remember{mutableStateOf(page.filter)}; var rotation by remember{mutableIntStateOf(page.rotationDegrees)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.scanner_edit_page))},text={ Column {
        Text(stringResource(R.string.scanner_crop_left)); Slider(left,{left=it},valueRange=0f..0.8f)
        Text(stringResource(R.string.scanner_crop_top)); Slider(top,{top=it},valueRange=0f..0.8f)
        Text(stringResource(R.string.scanner_crop_right)); Slider(right,{right=it},valueRange=0.2f..1f)
        Text(stringResource(R.string.scanner_crop_bottom)); Slider(bottom,{bottom=it},valueRange=0.2f..1f)
        SingleChoiceSegmentedButtonRow { ScanFilter.entries.forEachIndexed { i,f -> SegmentedButton(selected=filter==f,onClick={filter=f},shape=SegmentedButtonDefaults.itemShape(i,ScanFilter.entries.size)){Text(f.name.replace('_',' '))} } }
        TextButton(onClick={rotation=(rotation+90)%360}){Text(stringResource(R.string.scanner_rotate,rotation))}
    } },confirmButton={Button(enabled=left<right&&top<bottom,onClick={onApply(CropQuad(NormalizedPoint(left,top),NormalizedPoint(right,top),NormalizedPoint(right,bottom),NormalizedPoint(left,bottom)),filter,rotation)}){Text(stringResource(R.string.scanner_apply))}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(android.R.string.cancel))}})
}

@Composable private fun scannerMessage(m:ScannerMessage)=stringResource(when(m){ScannerMessage.SESSION_FAILED->R.string.scanner_error_session;ScannerMessage.IMPORT_FAILED->R.string.scanner_error_import;ScannerMessage.EDIT_FAILED->R.string.scanner_error_edit;ScannerMessage.OCR_FAILED->R.string.scanner_error_ocr;ScannerMessage.EXPORT_FAILED->R.string.scanner_error_export;ScannerMessage.PAGE_DELETED->R.string.scanner_page_deleted;ScannerMessage.EXPORTED->R.string.scanner_exported})
