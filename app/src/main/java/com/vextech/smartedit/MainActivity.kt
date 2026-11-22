package com.vextech.smartedit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.vextech.smartedit.core.files.IncomingFileReader
import com.vextech.smartedit.core.library.IncomingFile
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vextech.smartedit.core.ads.UnityAdsService
import com.vextech.smartedit.core.model.AppSettings
import com.vextech.smartedit.core.preferences.SettingsRepository
import com.vextech.smartedit.core.sharing.ShareService
import com.vextech.smartedit.ui.navigation.SmartEditNavigation
import com.vextech.smartedit.ui.theme.SmartEditTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var shareService: ShareService
    @Inject lateinit var adsService: UnityAdsService
    @Inject lateinit var incomingReader: IncomingFileReader

    /** Files that arrived through Share or Open with, waiting for the shell to open them. */
    private val incoming = mutableStateOf<List<IncomingFile>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A rotation or theme change re-delivers the same intent; only a fresh launch should open its files.
        if (savedInstanceState == null) handleIncoming(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            LaunchedEffect(settings.language) {
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                if (current != settings.language) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(settings.language))
                }
            }
            SmartEditTheme(settings.theme) {
                SmartEditNavigation(
                    shareService = shareService,
                    adsService = adsService,
                    incoming = incoming.value,
                    onIncomingHandled = { incoming.value = emptyList() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        val uris = intent?.let(::sharedUris).orEmpty()
        if (uris.isEmpty()) return
        lifecycleScope.launch { incoming.value = incomingReader.importShared(uris) }
    }

    /** The files in a Share (one or several) or Open with request. */
    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri)
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)
        else -> emptyList()
    }
}
