package com.vextech.smart_edit.core.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.sharing.ShareService
import kotlinx.coroutines.launch

/** Input: suggested file name to MIME type. Output: the chosen document Uri, or null if cancelled. */
private class CreateTypedDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second.ifBlank { "application/octet-stream" })
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

class SaveToDeviceAction internal constructor(private val start: (String, String, String) -> Unit) {
    /** [managedUri] must point inside Smart Edit managed storage. */
    fun save(managedUri: String, displayName: String, mimeType: String) = start(managedUri, displayName, mimeType)
}

private data class PendingSave(val managedUri: String, val displayName: String, val mimeType: String)

/**
 * Lets the user pick where to save a managed file using the system file picker.
 * No storage permission is needed. Outcome is reported through [onMessage].
 */
@Composable
fun rememberSaveToDevice(share: ShareService, onMessage: (UiText) -> Unit): SaveToDeviceAction {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingSave?>(null) }
    val launcher = rememberLauncherForActivityResult(CreateTypedDocument()) { destination ->
        val request = pending
        pending = null
        if (destination != null && request != null) {
            scope.launch {
                val saved = runCatching { share.saveManagedTo(request.managedUri, destination) }.isSuccess
                onMessage(UiText.res(if (saved) R.string.common_saved_to_device else R.string.common_save_failed))
            }
        }
    }
    return remember(launcher) {
        SaveToDeviceAction { uri, name, mime ->
            pending = PendingSave(uri, name, mime)
            launcher.launch(name to mime)
        }
    }
}
